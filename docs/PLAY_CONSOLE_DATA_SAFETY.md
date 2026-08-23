# Google Play Data safety 填写底稿（AdMob 25.4.0）

此文件是控制台填写底稿，不是法律意见。发布前应以实际构建、AdMob 配置和 Google 最新披露页重新核对。

## 应用自身

- 本机档案、角色、进度、设置和崩溃摘要只保存在应用私有目录。
- 工程没有开发者后端，也没有把这些本机数据传给开发者。
- 已关闭 Android 云备份和设备间迁移。

## Google Mobile Ads SDK 默认处理

在 Play Console Data safety 中核对并披露下列数据类型及用途：

| 数据 | 可能用途 |
|---|---|
| IP 地址／大致位置 | 广告、分析、防范欺诈 |
| 应用互动（启动、点击、广告视频观看） | 广告、分析 |
| 诊断信息（启动时间、卡顿、能耗等） | 分析、应用功能、防范欺诈 |
| 设备或其他标识符（广告 ID、App Set ID 等） | 广告、分析、防范欺诈 |

- Google 声明上述数据通过 TLS 加密传输。
- 是否属于“收集”或“共享”、是否可选、是否用于个性化广告，应按最终 AdMob 与 UMP 配置回答。
- 若后续增加 Crashlytics、Analytics、登录服务器或内购，必须重新更新本表、隐私政策和 Play Console 声明。

参考：https://developers.google.com/admob/android/privacy/play-data-disclosure
