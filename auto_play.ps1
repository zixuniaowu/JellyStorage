# ============================================================
# 全自动通关测试：战士·全章节
# ============================================================
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$shotDir = "C:\Users\zixun\AppData\Local\Temp\opencode\e2e_full"
New-Item -ItemType Directory -Force -Path $shotDir | Out-Null
$report = "$shotDir\report.txt"

function Log($msg) {
    $ts = Get-Date -Format "HH:mm:ss"
    "[$ts] $msg" | Tee-Object -FilePath $report -Append
}
function Tap($x, $y) { & $adb shell input tap $x $y }
function Swipe($x1, $y1, $x2, $y2, $dur) { & $adb shell input swipe $x1 $y1 $x2 $y2 $dur }
function Shot($name) { & $adb exec-out screencap -p > "$shotDir\$name.png" }
function GetScreen {
    $line = & $adb logcat -d -s JellyScreen:D 2>$null | Select-String "JellyScreen" | Select-Object -Last 1
    if ($line -match ": (\w+)") { return $Matches[1] }
    return "UNKNOWN"
}
function CheckCrash {
    $err = & $adb logcat -d -s AndroidRuntime:E 2>$null | Select-String "FATAL" | Select-Object -Last 1
    if ($err) { Log "💥 CRASH detected!"; return $true }
    return $false
}
function WaitScreen($target, $timeoutMs) {
    $deadline = [Diagnostics.Stopwatch]::StartNew()
    while ($deadline.Elapsed.TotalMilliseconds -lt $timeoutMs) {
        if ((GetScreen) -eq $target) { Log "✅ $target"; return $true }
        Start-Sleep -Milliseconds 400
    }
    Log "⏱ timeout waiting for $target (current: $(GetScreen))"
    return $false
}

# ============================================================
# Phase 0: 重置
# ============================================================
Log "=== 全自动通关测试开始 ==="
& $adb shell pm clear $PKG 2>$null | Out-Null
Log "App data cleared"
Start-Sleep 1
& $adb logcat -c
& $adb shell am start -W -n com.jellystorage/.MainActivity 2>$null | Out-Null
Start-Sleep 4
if (CheckCrash) { Log "FATAL: crash on launch"; exit 1 }

# ============================================================
# Phase 1: 注册 + 创建战士
# ============================================================
Log "--- Phase 1: 创建角色 ---"
$screen = GetScreen
Log "Screen: $screen"

if ($screen -eq "LOGIN") {
    # 一键登录
    Tap 700 500; Start-Sleep 2
    $screen = GetScreen
}

if ($screen -eq "CREATE_CHAR") {
    # 选战士（第一个职业按钮）
    Tap 700 900; Start-Sleep 1
    # 点创建
    Tap 500 1350; Start-Sleep 2
    $screen = GetScreen
    Log "After create: $screen"
}

# 跳过 HOW_TO
if ($screen -eq "HOW_TO") {
    Tap 1500 1350; Start-Sleep 2
    $screen = GetScreen
}

Log "Phase 1 done, screen: $screen"
Shot "phase1_character"

# ============================================================
# Phase 2: 主循环（自动跑图 + 战斗）
# ============================================================
Log "--- Phase 2: 主循环开始 ---"
$maxActions = 300  # 最多 300 次操作
$battleCount = 0
$deathCount = 0
$chapterReached = 0
$levelReached = 1
$goldTotal = 0
$equipFound = @()
$shrineCount = 0
$sw = [Diagnostics.Stopwatch]::StartNew()

for ($action = 0; $action -lt $maxActions; $action++) {
    $screen = GetScreen
    $elapsed = [int]$sw.Elapsed.TotalMinutes
    
    # 崩溃检查（每 10 次操作）
    if ($action % 10 -eq 0) {
        if (CheckCrash) { Log "FATAL: crash at action $action"; break }
    }
    
    switch ($screen) {
        "TITLE" {
            # 点"开始冒险"或"继续冒险"
            Tap 2100 500; Start-Sleep 2
            # 可能出现确认弹窗 → 确认
            Tap 2000 950; Start-Sleep 2
        }
        "CHAR_SELECT" {
            # 选第一个角色
            Tap 700 400; Start-Sleep 2
        }
        "CREATE_CHAR" {
            Tap 700 900; Start-Sleep 1  # 选战士
            Tap 500 1350; Start-Sleep 2  # 创建
        }
        "HOW_TO" {
            Tap 1500 1350; Start-Sleep 2  # 点"知道了"
        }
        "STORY" {
            # 快速跳过剧情
            Tap 2300 1250; Start-Sleep -Milliseconds 800
        }
        "MAP" {
            # 在地图上：点下一个可用节点（下方路线按钮区域）
            Tap 1496 1300; Start-Sleep 1.5
            # 可能触发确认
            Tap 2000 950; Start-Sleep 1
        }
        "ARENA" {
            # 战斗：攻击 + 移动 + 技能
            $battleCount++
            $subframe = $action % 6
            switch ($subframe) {
                0 { Tap 2632 1093 }  # 攻击
                1 { Swipe 600 950 1100 850 150 }  # 移动
                2 { Tap 2632 1093 }  # 攻击
                3 { Tap 2450 1050 }  # 技能1（如果有）
                4 { Tap 2632 1093 }  # 攻击
                5 { Swipe 1100 850 500 1000 150 }  # 移动
            }
            Start-Sleep -Milliseconds 400
        }
        "EVENT" {
            # 事件：选第一个选项
            Tap 1496 800; Start-Sleep 1.5
        }
        "SHOP" {
            # 商店：买第一个武器（如果有金）
            Tap 800 700; Start-Sleep 1
            # 退出商店
            Tap 2700 1350; Start-Sleep 1.5
        }
        "GEAR" {
            # 装备：点第一个装备穿上
            Tap 500 400; Start-Sleep 1
            # 退出
            Tap 2700 1350; Start-Sleep 1.5
        }
        "LEVEL_UP" {
            # 升级：选第一个天赋
            Tap 1496 550; Start-Sleep 1.5
        }
        "CORE_INK" {
            # 核心墨印：选第一个
            Tap 1496 500; Start-Sleep 1.5
        }
        "STAGE_CLEAR" {
            # 章节完成：轻触继续
            Tap 1496 720; Start-Sleep 2
            $chapterReached++
            Log "🏆 章节完成！当前章节: $chapterReached"
        }
        "RESULT" {
            # 结算：轻触继续
            Tap 1496 900; Start-Sleep 2
            $deathCount++
            Log "💀 结算画面（第 $($deathCount) 次）"
        }
        "CODEX" {
            Tap 2700 1350; Start-Sleep 1.5  # 返回
        }
        "TRIALS" {
            Tap 1496 1300; Start-Sleep 1.5  # 返回
        }
        "SETTINGS" {
            Tap 2700 1350; Start-Sleep 1.5  # 返回
        }
        default {
            # 未知屏幕：点中央尝试继续
            Tap 1496 720; Start-Sleep 1
        }
    }
    
    # 每 20 次操作截图 + 记录
    if ($action % 20 -eq 0 -and $action -gt 0) {
        Shot "auto_a$action"
        $curScreen = GetScreen
        Log "📊 action=$action screen=$curScreen elapsed=$($elapsed)min"
        # 从日志提取等级和击杀
        $perfLine = & $adb logcat -d -s JellyPerf:D 2>$null | Select-String "frame avg" | Select-Object -Last 1
        if ($perfLine) { Log "  perf: $($perfLine.Line.Trim().Substring(0, [Math]::Min(80, $perfLine.Line.Trim().Length)))" }
    }
    
    # 检查是否到达 RESULT（通关或全灭）
    if ($screen -eq "RESULT") {
        $deathCount++
        if ($deathCount -ge 3) {
            Log "💀 死亡 3 次，停止测试"
            break
        }
    }
}

$sw.Stop()
$totalMin = [math]::Round($sw.Elapsed.TotalMinutes, 1)

# ============================================================
# Phase 3: 收集数据
# ============================================================
Log "=== 数据收集 ==="
Shot "final_state"

# 从存档读取最终状态
$saveData = & $adb shell run-as com.jellystorage cat shared_prefs/jelly_adventure_v1.xml 2>$null
$kills = if ($saveData -match 'life_kills" value="(\d+)') { $Matches[1] } else { "?" }
$gold = if ($saveData -match 'life_gold" value="(\d+)') { $Matches[1] } else { "?" }
$wins = if ($saveData -match 'wins" value="(\d+)') { $Matches[1] } else { "?" }
$runs = if ($saveData -match 'runs" value="(\d+)') { $Matches[1] } else { "?" }
$weapons = if ($saveData -match 'disc_wpn">([^<]+)') { ($Matches[1] -split ",").Count } else { 0 }

Log "生涯击杀: $kills"
Log "生涯金币: $gold"
Log "通关次数: $wins"
Log "出征次数: $runs"
Log "已发现武器: $weapons 种"

# 帧率统计
$perfLines = & $adb logcat -d -s JellyPerf:D 2>$null | Select-String "frame avg" | ForEach-Object {
    if ($_ -match "avg=([\d.]+)ms") { [float]$Matches[1] }
}
if ($perfLines) {
    $avgFps = [math]::Round(1000 / ($perfLines | Measure-Object -Average -Minimum -Maximum | ForEach-Object { $_.Average }), 0)
    $minMs = [math]::Round(($perfLines | Measure-Object -Minimum).Minimum, 1)
    $maxMs = [math]::Round(($perfLines | Measure-Object -Maximum).Maximum, 1)
    Log "帧率: 平均 $([math]::Round(1000/($perfLines | Measure-Object -Average).Average, 0))fps (min ${minMs}ms max ${maxMs}ms, 共 $($perfLines.Count) 采样)"
}

# ============================================================
# 报告
# ============================================================
Log ""
Log "========== 通关报告 =========="
Log "总用时: ${totalMin} 分钟"
Log "总操作数: $action"
Log "战斗次数: $battleCount"
Log "死亡次数: $deathCount"
Log "到达章节: $chapterReached"
Log "生涯击杀: $kills | 生涯金: $gold | 通关: $wins | 出征: $runs"
Log "装备发现: $weapons 种武器"
Log ""
Log "截图保存在: $shotDir"
Log "========== 测试结束 =========="

Shot "final_report"
