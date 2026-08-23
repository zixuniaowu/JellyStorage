# 果冻勇者 — Google Play 上架检查清单（v1.11.0）

面向 Google Play / 应用商店上架。代码侧已做的基线 + **你必须在控制台完成** 的事项。

## 已在工程内处理（至 v1.11.0）

| 项 | 状态 |
|---|---|
| 战斗中尺寸变化不再重置整场 | ✅ |
| 新冒险 / 删角色 / 放弃本局 / 进下一章二次确认 | ✅ |
| 一键登录不覆盖已有本机账号 | ✅ |
| 金币购买统一校验，不足不扣款 | ✅ |
| 装备栏展示全部已拥有装 | ✅ |
| 出口误触自动进章修复 | ✅ |
| 广告药水本局 ≤2 | ✅ |
| 事件/磨刀金币不足有提示 | ✅ |
| allowBackup=false（防备份拖走密码） | ✅ |
| versionName/versionCode 对齐 1.11.0 / 210 | ✅ |
| 自适应启动图标 | ✅ |
| 操作说明与现流程一致 | ✅ |
| 安全区 padding（刘海/手势条） | ✅ 1.5.4 |
| 战斗音效（内置 wav + Tone 兜底）+ 设置开关 | ✅ 1.5.4 |
| 商店购买后刷新货架 | ✅ 1.5.4 |
| 装备栏上下拖滚动 | ✅ 1.5.4 |
| 本地崩溃落盘 CrashReporter | ✅ 1.5.4 |
| target / compile SDK 36（Android 16） | ✅ 1.8.0 |
| AGP 8.9.1（API 36 最低兼容版本） | ✅ 1.8.0 |
| Kotlin / Compose 编译器 2.3.20 | ✅ 1.8.0 |
| Google Mobile Ads 25.4.0 + UMP 4.0.0 | ✅ 1.8.0 |
| Android 12+ 数据提取规则，禁止备份与设备迁移 | ✅ 1.8.0 |
| Android 8–9 震动 API 崩溃修复 | ✅ 1.8.0 |
| Android 13+ 单色主题图标 | ✅ 1.8.0 |
| UMP 同意状态刷新后才初始化/请求广告 | ✅ 1.8.0 |
| 设置页隐私政策和广告隐私选项入口 | ✅ 1.8.0 |
| 正式签名、AdMob ID、隐私 URL 外部注入与校验 | ✅ 1.8.0 |
| 战斗房随机试炼（疾书/连墨/灵涌/守砚）与额外奖励 | ✅ 1.9.0 |
| 新增墨泉、无人锻炉两类随机抉择事件 | ✅ 1.9.0 |
| 9 件高阶装备透明手绘图标，接入商店/换装/图鉴 | ✅ 1.9.0 |
| 全职业技能起手印记、链雷轨迹、技能/必杀分级震动音效 | ✅ 1.9.0 |
| 五章 Boss 独立身份、10 种专属招式、阶段轮换与 0.8–1.1 秒地面预警 | ✅ 1.9.1 |
| 幽火治疗、哥布林鼓舞、甲虫护卫三类战术职责及可读徽记/支援轨迹 | ✅ 1.9.1 |
| Boss 后职业核心墨印三选一、五阶成长、9 种技能机制改造及存档恢复 | ✅ 1.10.0 |
| 套装集齐觉醒演出、装备双图展示、分级震动及真实特效冷却 HUD | ✅ 1.10.1 |
| 640 dpi 真机首页/地图/图鉴排版、沉浸式全屏、图鉴已获得装备优先展示 | ✅ 1.10.2 |
| 中文/日文运行时切换、系统日语首次默认、语言偏好持久化 | ✅ 1.11.0 |
| 登录/标题/设置语言入口及剧情、职业、装备、Boss、试炼日文词库 | ✅ 1.11.0 |
| 日文应用名、商店文案与双语隐私政策模板 | ✅ 1.11.0 |

## 你必须完成（否则无法上架 / 无收益）

1. **确认永久应用 ID**
   - 当前为 `com.jellystorage`；首次上传后不能更换，请上传前最终确认。

2. **正式签名**
   - 生成 upload keystore。
   - 复制 `release.properties.example` 为 `release.properties` 并填写签名项。
   - keystore、密码和填好的 `release.properties` 均不得提交 Git。

3. **AdMob 正式 ID**（普通构建仍使用测试广告）
   - 在 `release.properties` 填应用、插页式和激励广告单元 ID。
   - 在 AdMob「隐私权和消息」创建欧洲法规消息。
   - 不要直接修改源码中的 Google 测试 ID。

4. **隐私政策 URL**
   - 以 `docs/PRIVACY_POLICY_TEMPLATE.md` 为底稿，补开发者名称、邮箱和正式日期。
   - 发布为公开 HTTPS 页面，将 URL 填入 `release.properties` 和 Play Console。

5. **Data safety / 广告标识符**
   - 按 `docs/PLAY_CONSOLE_DATA_SAFETY.md` 逐项核对并填写。
   - 应用含广告，Play Console 必须声明「包含广告」。

6. **商店素材**
   - 512 图标、功能图、手机横屏截图 ≥2、简短与完整说明
   - 在「翻译管理」添加 `日本語 (ja-JP)`，粘贴 `docs/PLAY_STORE_JA.md` 的标题、短说明和完整说明
   - 当前官方字段上限：应用名 30 字符、短说明 80 字符、完整说明 4000 字符
   - 内容分级问卷

7. **生成受校验的 AAB**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean testDebugUnitTest lintRelease bundleRelease -PplayStoreRelease=true
```

只有带 `-PplayStoreRelease=true` 的命令才会强制校验正式广告、隐私政策、keystore 和签名密码。
成功后上传 `app/build/outputs/bundle/release/app-release.aab`。

8. **测试轨道**
   - 先发内部测试并完成主要流程、旋转/折叠屏、断网和广告失败测试。
   - 2023-11-13 后创建的个人开发者账号，正式发布前需要至少 12 名测试者连续加入封闭测试 14 天，然后申请生产环境权限。

9. **日本地区补充核对**
   - 如果以后改为付费应用或加入应用内购买，核对日本《特定商取引法》要求的经营者名称、地址和电话展示。
   - 当前免费广告模式仍须完成隐私政策、Data safety、「包含广告」和 UMP 隐私选项。

官方参考：

- https://support.google.com/googleplay/android-developer/answer/9859152
- https://support.google.com/googleplay/android-developer/answer/9844778?hl=ja
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/6223646

## 产品诚实说明（评分相关）

- **账号是本机存档**，不是云账号；文案勿写「云同步 / 跨设备登录」
- 横屏动作 Roguelite，适合手机横握；平板可再优化安全区
- 上架后优先看：崩溃率、误触清档投诉、广告频率投诉

## 建议下一迭代（冲 4.5★）

- Crashlytics 或 Play Vitals 监控
- 将“本地账号/密码”重命名为“本机档案/口令”，避免用户误解为云账号
- 平板、折叠屏和自由窗口模式专项布局测试
