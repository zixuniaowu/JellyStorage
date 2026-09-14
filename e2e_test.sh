#!/bin/bash
# ============================================================
# E2E 自主测试：全自动通关第一关
# 用法: bash e2e_test.sh
# 前提: 真机已连接 adb，游戏已安装 debug 包
# ============================================================
set -e

ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -f "$ADB" ] || ADB="adb"
PKG="com.jellystorage"
ACT="$PKG/.MainActivity"
LOG="e2e_log.txt"
SHOT_DIR="e2e_shots"
mkdir -p "$SHOT_DIR"

# ---------- 工具函数 ----------
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }
shot() { $ADB exec-out screencap -p > "$SHOT_DIR/$1.png" && log "📸 $1.png ($(wc -c < "$SHOT_DIR/$1.png") bytes)"; }
tap() { $ADB shell input tap "$1" "$2"; }
swipe() { $ADB shell input swipe "$1" "$2" "$3" "$4" "$5"; }
wait_screen() {
    local target="$1" timeout="$2" found=false
    for i in $(seq 1 $((timeout / 500))); do
        if $ADB logcat -d -s JellyScreen:D 2>/dev/null | grep -q "$target"; then
            found=true; break
        fi
        sleep 0.5
    done
    if [ "$found" = true ]; then log "✅ 到达 $target"; else log "❌ 未到达 $target (等待 ${timeout}ms)"; fi
    $found
}
clear_logcat() { $ADB logcat -c; }
check_crash() {
    if $ADB logcat -d -s AndroidRuntime:E 2>/dev/null | grep -q "FATAL"; then
        log "💥 检测到崩溃!"
        $ADB logcat -d -s AndroidRuntime:E | tail -20 >> "$LOG"
        return 1
    fi
    return 0
}

# ---------- 开始 ----------
log "========== E2E 开始 =========="
$ADB devices | grep -q "device" || { log "❌ 无设备连接"; exit 1; }

# 1. 冷启动
log "--- 冷启动 ---"
$ADB shell am force-stop $PKG
sleep 1
clear_logcat
$ADB shell am start -W -n $ACT
wait_screen "TITLE" 8000
shot "01_title"
check_crash || exit 1

# 2. 创建角色（如果需要）
log "--- 尝试创建角色 ---"
# 先检查是否在 TITLE（已有角色）或 CREATE_CHAR
if $ADB logcat -d -s JellyScreen:D | grep -q "CREATE_CHAR"; then
    log "在创建角色页"
    tap 1000 400   # 点名字输入区域
    sleep 1
    tap 700 800    # 选职业（战士）
    sleep 1
    tap 500 1300   # 确认创建
    sleep 2
fi
wait_screen "TITLE" 5000 || true
shot "02_after_create"

# 3. 开始新冒险
log "--- 开始新冒险 ---"
clear_logcat
# TITLE 界面的"开始冒险/继续冒险"按钮区域
tap 2100 450
sleep 3
# 可能有确认弹窗
tap 2000 950   # 确认弹窗的确认按钮位置
sleep 2
# 检查到达了 MAP
wait_screen "MAP" 10000 || { log "未到达 MAP，尝试跳过剧情"; tap 2300 1250; sleep 3; }
shot "03_map"
check_crash || exit 1

# 4. 选路进入战斗
log "--- 进入第一个战斗节点 ---"
clear_logcat
# 点击地图上第一个可用节点（下方路线按钮区域）
tap 1496 1300
sleep 2
# 可能有剧情，跳过
if $ADB logcat -d -s JellyScreen:D | tail -5 | grep -q "STORY"; then
    log "跳过剧情..."
    for i in 1 2 3; do tap 2300 1250; sleep 1.5; done
fi
wait_screen "ARENA" 15000
shot "04_arena_enter"
check_crash || exit 1

# 5. 战斗模拟：持续攻击 + 移动摇杆
log "--- 战斗模拟开始 ---"
BATTLE_START=$(date +%s)
MAX_BATTLE_SEC=90
frame=0
while true; do
    NOW=$(date +%s)
    ELAPSED=$((NOW - BATTLE_START))
    if [ $ELAPSED -ge $MAX_BATTLE_SEC ]; then
        log "⏱ 战斗超时 ${MAX_BATTLE_SEC}s"
        break
    fi
    # 检查是否还在 ARENA
    SCREEN=$($ADB logcat -d -s JellyScreen:D | grep "JellyScreen" | tail -1 | sed 's/.*: //')
    if [ "$SCREEN" != "ARENA" ]; then
        log "✅ 离开战斗（当前: $SCREEN, 用时 ${ELAPSED}s）"
        break
    fi
    # 交替攻击和移动
    FRAME_TYPE=$((frame % 4))
    case $FRAME_TYPE in
        0) tap 2632 1093 ;;  # 攻击键
        1) swipe 600 950 1000 850 200 ;;  # 摇杆右上
        2) tap 2632 1093 ;;  # 攻击键
        3) swipe 1000 850 700 950 200 ;;  # 摇杆左下
    esac
    frame=$((frame + 1))
    sleep 0.3
    # 每 10 帧截一次图
    if [ $((frame % 10)) -eq 0 ]; then
        shot "05_battle_f$frame"
        check_crash || { log "💥 战斗中崩溃!"; break; }
    fi
done
shot "06_battle_end"
log "战斗共 $frame 帧输入"

# 6. 结果检查
log "--- 结果检查 ---"
CURRENT=$($ADB logcat -d -s JellyScreen:D | grep "JellyScreen" | tail -1 | sed 's/.*: //')
log "最终屏幕: $CURRENT"
case "$CURRENT" in
    MAP|LEVEL_UP|STORY|CORE_INK|STAGE_CLEAR)
        log "✅ 战斗胜利！进入 $CURRENT" ;;
    RESULT)
        log "💀 战斗失败（RESULT）" ;;
    *)
        log "❓ 未知状态: $CURRENT" ;;
esac
shot "07_result"

# 7. 帧率数据
log "--- 帧率数据 ---"
$ADB logcat -d -s JellyPerf:D 2>/dev/null | grep "frame avg" | tail -10 | while read line; do
    log "$line"
done

# 8. 崩溃最终检查
if check_crash; then
    log "✅ 全程无崩溃"
else
    log "💥 有崩溃"
fi

log "========== E2E 结束 =========="
echo ""
echo "📸 截图目录: $SHOT_DIR/"
echo "📋 详细日志: $LOG"
