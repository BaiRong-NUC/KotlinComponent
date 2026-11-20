# Manage · Android Kotlin 组件库

`ads/` 提供开屏、插屏、横幅和原生广告。业务统一调用 `dev.manage.ads.AdsManager`。`sample/` 是可构建的最小宿主，包含 UMP 同意流程、启动页、插屏按钮、横幅容器和原生容器。

## 接入一个 App

1. 在宿主的 `settings.gradle.kts` 加入本地组合构建和聚合依赖仓库。下面的相对路径适用于 `D:\Android\PDF` 下的产品。

   ```kotlin
   val manageDir = file("../../Manage")
   includeBuild(manageDir)
   apply(from = manageDir.resolve("gradle/ads-repositories.settings.gradle"))
   ```

   宿主常规仓库需要 `google()` 和 `mavenCentral()`。本工程使用 AGP 9.2.1、Gradle 9.4.1、compileSdk 36.1 和 minSdk 24。

2. 在宿主的 `app/build.gradle.kts` 应用配置生成脚本并引入模块。

   ```kotlin
   apply(from = gradle.includedBuild("Manage").projectDir.resolve("ads/ads-config.gradle"))

   dependencies {
       implementation("dev.manage:ads:1.0.0")
   }
   ```

3. 将 [ads.json 示例](sample/src/main/ads.json) 复制到 `app/src/main/ads.json`，换成宿主自己的 AdMob App ID 和广告单元 ID。构建会生成宿主 namespace 下的 `AppAdsConfig`，同时填充合并 Manifest 中的 `com.google.android.gms.ads.APPLICATION_ID`。已接入 AdMob 的产品迁移时，应移除宿主 Manifest 中重复的旧 ID 声明。

4. 在主线程注册管理器。`install` 只保存配置和回调。

   ```kotlin
   AdsManager.install(this, AppAdsConfig.value)
   ```

5. 在宿主取得本次请求资格后，从启动页面的 `lifecycleScope` 调用。`runStartup` 根据配置等待广告，并在需要时尝试展示开屏。全屏展示需要处于 `RESUMED` 状态且实现 `LifecycleOwner` 的 Activity。

   ```kotlin
   lifecycleScope.launch {
       val startup = AdsManager.runStartup(this@MainActivity, consent.canRequestAds())
       navigateToHome()
   }
   ```

   `startup.gate` 给出放行原因，`startup.waitMs` 记录等待时长，`startup.presentation` 给出开屏展示结果。宿主负责隐私弹窗与导航。用户在设置中修改授权时，调用 `AdsManager.setRequestPermission(consent.canRequestAds())` 更新资格。撤销后组件关闭视图绑定、释放预加载并忽略旧加载回调。

## 调整启动策略

`ads.json` 是构建输入。Gradle 生成 Kotlin 配置，运行时直接读取生成值。每个变体选择一份完整文件，优先级从高到低为 `src/<variant>`、`src/<buildType>`、`src/<combinedFlavor>`、各 flavor、`src/main`。例如 `src/debug/ads.json` 可以覆盖调试包的全部 ID 和策略。改动在重新构建后生效。

| 字段 | 含义 |
| --- | --- |
| `enabled` | 广告总开关。关闭后，启动直接放行，组件跳过 SDK 初始化和广告请求。 |
| `admobAppId` | 应用级 ID，形式为 `ca-app-pub-…~…`。 |
| `placements` | 以业务位置命名。每项的 `unitId` 为 `ca-app-pub-…/…`，`format` 为 `app_open`、`interstitial`、`native` 或 `banner`。 |
| `startup.preloadStages` | 从上往下执行。同阶段并行。阶段内请求全部返回或达到 `timeoutMs` 后启动下一阶段。已发出的请求继续完成。横幅在绑定时按容器宽度请求。 |
| `enterHome.waitFor` | `none` 立即放行，`sdk` 等聚合初始化回调，`any` 等任一指定广告位就绪，`all` 等全部指定广告位就绪。 |
| `enterHome.placements` | `any` 和 `all` 的等待名单，取自预加载阶段。`none` 和 `sdk` 可以省略该字段。 |
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

这份策略中，首页原生先成功时可以马上进入主页；只有开屏也已就绪才会展示它。进入主页后其余预加载阶段继续执行。示例默认预算是 4000 ms，实际值应结合设备测量调整。对于 `none`，建议省略 `showBeforeHome` 以直接进入主页。

## 调用四类广告

| 操作 | 调用方式 |
| --- | --- |
| 预加载开屏、插屏或原生 | `AdsManager.preload("editor_exit")` |
| 展示已缓存的开屏 | `AdsManager.showAppOpen(activity, "startup_open")` |
| 展示已缓存的插屏 | `AdsManager.showInterstitial(activity, "editor_exit")` |
| 绑定横幅 | `AdsManager.bindBanner(container, "home_banner", viewLifecycleOwner)` |
| 绑定默认原生卡片 | `AdsManager.bindNative(container, "home_native", viewLifecycleOwner)` |

预加载和全屏展示为挂起函数，主线程可以继续绘制。全屏展示在关闭、失败或当前无法展示时返回 `AdResult`，业务可在下一行继续。缓存按广告位复用，开屏与插屏共用一个全屏展示位置。

横幅和原生绑定在主线程执行。Fragment 传 `viewLifecycleOwner`，Activity 传自身。同一个容器重新绑定会释放前一个绑定。列表项回收或 Compose `DisposableEffect` 销毁时调用返回句柄的 `close()`。自定义原生卡片可传入 `renderer = { context, nativeAd -> ... }`，返回设置好广告标识、素材及对应资产视图的 `NativeAdView`，组件负责注册和销毁广告。

## 聚合与耗时

SDK 和适配器使用 PDF 项目的组合。Google Mobile Ads `25.5.0`，AppLovin `13.6.4.2`，Meta `6.22.0.0`，Mintegral `17.1.81.1`，Pangle `8.3.0.4.0`。在每个产品的 AdMob 后台配置对应的广告源和广告单元。`preloadStages` 控制业务广告位顺序。

`install` 接受可选的 `onPaid` 和 `onTiming`。收益事件包含广告位、单元 ID、微单位金额、币种、精度和可用的广告源。耗时事件包括 SDK 初始化、单广告位加载、展示流程和广告实际可见时间。结合 `StartupResult.waitMs` 可比较不同启动策略。宿主可自行选择统计服务。

## 本地验证

```powershell
.\gradlew.bat :ads:testDebugUnitTest :ads:assembleRelease :sample:assembleRelease
```

库产物位于 `ads/build/outputs/aar/ads-release.aar`，示例 APK 位于 `sample/build/outputs/apk/release/`。组合构建会传递 SDK 与适配器依赖。示例配置使用 Google 示例 ID。策略和缓存单元测试使用模拟请求与虚拟时钟。
