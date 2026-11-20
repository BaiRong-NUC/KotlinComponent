# ads · Android 广告组件

宿主直接编译 `ads/` 下的 Kotlin 源文件，业务统一通过 [AdsManager.kt](AdsManager.kt) 调用开屏、插屏、横幅和原生广告。修改默认广告 ID 和启动策略只需编辑 [ads.json](conf/ads.json)。

| 文件 | 职责 |
| --- | --- |
| `AdsManager.kt` | 安装、授权、启动等待，以及四类广告的业务入口。 |
| `data/AdsConfig.kt` | 定义广告位、启动策略、展示结果和收益事件。 |
| `conf/ads.json` | 默认广告位、广告单元 ID 和启动策略。 |
| `runtime/` | 按广告位分发请求、监听 SDK 收益、读取广告源并组装 `PaidEvent`，同时负责缓存、预加载和全屏展示互斥。 |
| [`net/AdMobRequests.kt`](net/AdMobRequests.kt) | 调用 AdMob SDK 发起四类广告的加载请求。 |
| [sample/gradle/ads.gradle](../sample/gradle/ads.gradle) | 宿主可复用的接入脚本，声明 SDK 和聚合依赖，按变体从 `ads.json` 生成配置和 Manifest 中的 AdMob App ID。 |

## 接入一个 App

将 `ads/` 复制到目标项目根目录，与 `app/` 同级。将仓库中的 `sample/gradle/ads.gradle` 复制到目标项目的 `gradle/ads.gradle`。

1. 在宿主 `settings.gradle.kts` 的 `include(":app")` 之后加一行。

   ```kotlin
   apply(from = "gradle/ads.gradle")
   ```

   默认接入 `:app`。如果应用模块叫 `:mobile`，在 `apply` 前加 `extra["manageAdsAppPath"] = ":mobile"`。脚本只把广告源码和依赖加入选定的应用模块。验证环境为 AndroidX、AGP 9.2.1、Gradle 9.4.1、compileSdk 36.1 和 minSdk 24。

2. 打开 `ads/conf/ads.json`，将示例 App ID 和广告单元 ID 换成当前产品的值。构建会把这个 App ID 自动合入 Manifest。迁移已有 AdMob 的项目时，请移除宿主原先写死的 `com.google.android.gms.ads.APPLICATION_ID`，将 `ads.json` 作为配置来源。

3. 在宿主 `Application.onCreate()` 调用 `super.onCreate()` 后注册管理器。`install` 使用当前构建生成的配置。宿主可通过 `AdsManager.isEnabled` 读取广告开关。

   ```kotlin
   AdsManager.install(this)
   ```

4. 在宿主取得本次请求资格后，从启动页面的 `lifecycleScope` 调用。`runStartup` 根据配置等待广告，并在需要时尝试展示开屏。全屏展示需要处于 `RESUMED` 状态且实现 `LifecycleOwner` 的 Activity。

   ```kotlin
   lifecycleScope.launch {
       val startup = AdsManager.runStartup(this@MainActivity, consent.canRequestAds())
       navigateToHome()
   }
   ```

   `startup.gate` 给出放行原因，`startup.waitMs` 记录等待时长，`startup.presentation` 给出开屏展示结果。宿主负责隐私弹窗与导航。用户在设置中修改授权时，调用 `AdsManager.setRequestPermission(consent.canRequestAds())` 更新资格。撤销后组件取消横幅和原生广告的使用协程、释放预加载并忽略旧加载回调。

## 调整启动策略

`ads/conf/ads.json` 使用标准 JSON。Gradle 在构建时校验字段、广告 ID 和广告位引用，并生成 Kotlin 配置。App 启动时直接读取生成值。修改配置后重新构建即可。

需要区分调试包或多个产品时，可在宿主 `app/src/` 下放一份完整的 `ads.json`。按当前 variant、build type、组合 flavor、各 flavor、`main` 的顺序查找，最后使用 `ads/conf/ads.json`。例如只新增 `app/src/debug/ads.json`，release 仍使用组件内的默认文件。

| 字段 | 含义 |
| --- | --- |
| `enabled` | 广告总开关。关闭后，启动直接放行，组件跳过 SDK 初始化和广告请求。 |
| `admobAppId` | 应用级 ID，形式为 `ca-app-pub-…~…`。 |
| `placements` | 以业务位置命名。每项的 `unitId` 为 `ca-app-pub-…/…`，`format` 为 `app_open`、`interstitial`、`native` 或 `banner`。 |
| `startup.preloadStages` | 从上往下执行。同阶段并行。阶段内请求全部返回或达到 `timeoutMs` 后启动下一阶段。已发出的请求继续完成。横幅在绑定时按容器宽度请求。 |
| `enterHome.waitFor` | `none` 立即放行，`sdk` 等聚合初始化回调，`any` 等任一指定广告位就绪，`all` 等全部指定广告位就绪。 |
| `enterHome.placements` | `any` 和 `all` 的等待名单，取自预加载阶段。`none` 和 `sdk` 应省略该字段或填空数组。 |
| `enterHome.maxWaitMs` | 从允许请求后的首次启动准备开始计时，覆盖 SDK 初始化和广告加载。条件已确定无法满足时提前放行。 |
| `enterHome.showBeforeHome` | 可选的开屏位。放行时该广告已准备好且 Activity 可展示，就等待广告关闭再返回。超时后到达的广告留在缓存，供业务后续显式使用。 |

例如开屏和首页原生一起预加载，想实验不同的首页等待条件，只改 `enterHome` 即可。

```json
{
  "waitFor": "any",
  "placements": ["startup_open", "home_native"],
  "maxWaitMs": 3000,
  "showBeforeHome": "startup_open"
}
```

这份策略最多等待 3 秒。首页原生先就绪时可以直接进入主页。若开屏已经就绪，则展示开屏并在关闭后返回。随组件提供的默认配置只等 `startup_open`，最多等待 4 秒。要直接进入主页，可配置 `waitFor` 为 `none` 并省略 `placements` 和 `showBeforeHome`。

## 调用四类广告

| 操作 | 调用方式 |
| --- | --- |
| 预加载开屏、插屏或原生 | `AdsManager.preload("editor_exit")` |
| 展示已缓存的开屏 | `AdsManager.showAppOpen(activity, "startup_open")` |
| 展示已缓存的插屏 | `AdsManager.showInterstitial(activity, "editor_exit")` |
| 取得横幅视图 | `AdsManager.withBannerAd(context, "home_banner", widthDp) { adView -> … }` |
| 取得原生广告 | `AdsManager.withNativeAd("home_native") { nativeAd -> … }` |

预加载和全屏展示为挂起函数，主线程可以继续绘制。全屏展示在关闭、失败或当前无法展示时返回 `AdResult`，业务可在下一行继续。缓存按广告位复用，开屏与插屏共用一个全屏展示位置。

`withNativeAd` 优先取预加载的 `NativeAd`，没有缓存时会先加载。`withBannerAd` 根据传入的可用宽度（dp）创建 `AdView` 并发起请求，可用 `onLoadResult` 接收横幅加载结果。两个接口都在主线程将广告交给 `use` 代码块，代码块的执行时间就是广告的使用期限。

宿主在页面的 `lifecycleScope` 中调用接口，在 `use` 中挂载视图并用 `awaitCancellation()` 保持展示。页面销毁、调用任务取消或广告请求资格撤销时，宿主在 `finally` 里移除视图，组件随后销毁广告对象。同一容器重新绑定前应取消旧任务。原生卡片布局、素材注册和横幅暂停与恢复的完整示例见 [sample 中的 AdBinding.kt](../sample/src/main/java/dev/manage/sample/AdBinding.kt)。

## 聚合与耗时

SDK 和适配器使用 PDF 项目的组合。Google Mobile Ads `25.5.0`，AppLovin `13.6.4.2`，Meta `6.22.0.0`，Mintegral `17.1.81.1`，Pangle `8.3.0.4.0`。在每个产品的 AdMob 后台配置对应的广告源和广告单元。`preloadStages` 控制业务广告位顺序。

`install` 接受可选的 `onPaid` 和 `onTiming`。收益事件包含广告位、单元 ID、微单位金额、币种、精度和可用的广告源。耗时事件包括 SDK 初始化、单广告位加载、展示流程和广告实际可见时间。结合 `StartupResult.waitMs` 可比较不同启动策略。宿主可自行选择统计服务。

