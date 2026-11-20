# Manage · Android Kotlin 组件库

[ads](ads/README.md) 提供开屏、插屏、横幅和原生广告。将 `ads/` 复制到目标项目根目录，在宿主 `settings.gradle.kts` 的 `include(":app")` 后加 `apply(from = "ads/ads.gradle")`。组件负责引入 SDK 和聚合适配器，并将配置中的 AdMob App ID 合入 Manifest。业务统一调用 `AdsManager`，广告 ID 和启动策略在 [ads.json](ads/conf/ads.json) 中调整。

[sample](sample/src/main/java/dev/manage/sample/SampleActivity.kt) 演示获取用户同意、等待开屏、进入主页和展示广告。

在仓库根目录运行组件单元测试并构建示例：

```powershell
.\gradlew.bat :sample:testDebugUnitTest :sample:assembleRelease
```
