# PDF 广告公共库

`pdf-ads` 是一个可独立构建的 Android Library。首版抽取三个 PDF 产品中重复的 AdMob 请求和 Adjust 收益对象构造，以及 Viewer、Toolkit 共用的全屏展示状态。代码位于 `src/main/java/com/pdf/shared/ads/`。

| 文件 | 可替换的产品代码 | 宿主继续负责 |
| --- | --- | --- |
| `AdMobRequests.kt` | Reader、Viewer、Toolkit 的开屏、插屏、横幅和原生请求，以及 Viewer 的激励请求 | 同意流程、开关、SDK 初始化、广告位 ID、监听回调、缓存和 UI |
| `AdMobRevenue.kt` | 三个 `AdRevenueReporter` 中创建 `AdjustAdRevenue` 的部分 | 在 paid 回调中校验参数、选择来源、记录 Firebase 事件及向 Adjust 提交 |
| `FullScreenAdSession.kt` | Viewer、Toolkit 的同名类。按对象身份持有展示中的广告，迟到的关闭回调无法清掉下一个广告 | 在主线程调用 `start` 和 `finish` |
| `AppOpenAdCooldown.kt` | Viewer、Toolkit 的同名类。冷却起点由 `recordShown()` 设置 | 传入产品自己的时长和单调时钟 |

Reader 的 `FullScreenPolicy` 在开屏和插屏之间共享冷却，继续由 Reader 管理。原生广告的 `options` 参数默认留空，与 Reader 一致。Viewer 和 Toolkit 接入时传入原有的 `NativeAdOptions`。

## Review 后接入

在产品的 `settings.gradle.kts` 末尾引用当前本地目录：

```kotlin
includeBuild("../../Manage/pdf-ads")
```

在产品的 `app/build.gradle.kts` 的 `dependencies` 中添加：

```kotlin
implementation("com.pdf.shared:pdf-ads:0.1.0")
```

例如在 Viewer 的 `InterstitialAdManager` 中，保留原有广告开关、同意校验和 `callback`，将调用 SDK 的那一行替换为：

```kotlin
AdMobRequests.loadInterstitial(activity, AdsConfig.INTERSTITIAL_UNIT_ID, callback)
```

导入 `com.pdf.shared.ads.AdMobRequests`。横幅调用接收已经配置好尺寸、广告位和监听器的 `AdView`。所有加载入口都直接调用 SDK，应放在产品现有的主线程、同意校验和初始化成功分支内。收益入口只构造对象，在原 `AdRevenueReporter` 的构造函数处替换即可。

## 本地验证

在本目录运行 `.\gradlew.bat assembleRelease testDebugUnitTest`。AAR 位于 `build/outputs/aar/pdf-ads-release.aar`。单元测试覆盖全屏对象身份、冷却边界和收益字段。此命令无需启动产品或请求广告。
