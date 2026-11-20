# Manage · Android Kotlin 组件库

[ads](ads/README.md) 提供四类广告的分配、展示和收益回调，[ads/net](ads/net/AdMobRequests.kt) 封装 AdMob SDK 加载请求。业务统一调用 `AdsManager`，广告 ID 和启动策略在 [ads.json](ads/conf/ads.json) 中调整。宿主可复用 [sample 的广告接入脚本](sample/gradle/ads.gradle) 来引入 SDK、编译广告源码并将 AdMob App ID 合入 Manifest。

[sample](sample/src/main/java/dev/manage/sample/SampleActivity.kt) 演示获取用户同意、等待开屏、进入主页和展示广告。

[tool/http](tool/http/JsonHttpClient.kt) 提供可单独接入的 JSON POST 接口，适用于自有埋点服务器等 HTTP 端点。将 `tool/http/` 按原路径复制到目标项目根目录，在宿主的 `android` 块中添加 `sourceSets.getByName("main").kotlin.srcDir(rootProject.file("tool/http"))`，引入 Kotlin 协程并在 Manifest 声明 `android.permission.INTERNET`。调用 `JsonHttpClient.postJson(url, json, headers)` 可以获取 HTTP 状态码，连接失败或超时会抛出 `IOException`。Firebase Analytics 的 Android SDK 埋点使用 `FirebaseAnalytics.logEvent()`，由 SDK 负责上传。

在 Android Studio 中打开 `sample/` 即可运行示例。从仓库根目录执行以下命令可运行单元测试并构建 Release 包。

```powershell
cd sample
.\gradlew.bat testDebugUnitTest assembleRelease
```
