# 视界有度 · Vision Minder

使用 Kotlin、Jetpack Compose 和 Material 3 开发的 Android 短视频使用干预工具，通过观看目标、定时提醒、长按确认和延迟切换，帮助用户管理观看节奏。

当前为实验性版本，仅支持抖音（`com.ss.android.ugc.aweme`），与抖音官方无关联。应用包名为 `com.example.test`，最低支持 Android 7.0（API 24）。

## 功能

| 模式 | 参数 | 行为 |
| --- | --- | --- |
| 目标模式 | 5–60 分钟 | 显示进度环，达到目标后改变颜色；支持拖动并保存位置，透明度为 20%–100%。 |
| 提醒模式 | 每 3–30 分钟 | 显示提醒，等待 3 秒后选择继续或结束；继续重新计时，结束返回桌面。 |
| 动作模式 | 长按 1–5 秒，可设置触发频率 | 在抖音首页推荐页拦截视频区域滑动，长按完成并松手后执行一次切换。 |
| 慢速模式 | 等待 1–8 秒，可设置触发频率 | 滑动后倒计时，再执行一次切换；若手指仍按住，松手后切换。 |

- 提供模式预览、参数设置、应用安装状态和无障碍服务状态检查。
- 切出目标应用或锁屏暂停计时，返回继续本轮进度；暂停干预后重新开启开始新一轮。
- 按本地日期持久化今日使用时长、提醒次数和通过提醒主动结束的次数，跨午夜分账。
- 统计仅覆盖干预开启时的目标应用前台时间，不计提醒浮层停留时间，不代表系统全部使用历史。
- 动作和慢速模式在评论、搜索等页面及无法识别的页面自动放行；离开推荐页、锁屏、暂停或修改设置会取消待执行切换。慢速模式等待期间连续滑动不会排队。

## 安装与使用

1. 按下文构建并安装 `app/build/outputs/apk/debug/app-debug.apk`。
2. 安装抖音，在「应用管理」中将其加入干预。
3. 阅读服务用途说明，前往系统无障碍设置，手动开启「视界有度」。
4. 选择模式、设置参数，点击「开启干预」，再打开抖音。
5. 可随时在应用中暂停干预，或在系统设置中关闭无障碍服务。

提醒浮层不会直接暂停视频或音频。目标模式达到目标后不会强制退出应用。动作和慢速模式仅在能识别的首页推荐页生效。

## 权限与数据

应用使用无障碍服务识别前台应用，并通过 `TYPE_ACCESSIBILITY_OVERLAY` 显示浮层，无需普通悬浮窗权限。动作和慢速模式还会读取页面节点的类型、边界、文字和内容描述来识别推荐页，并通过无障碍手势接口执行切换。

设置、按日汇总的时长与提醒统计保存在本机 `SharedPreferences` 中。应用不保存页面文字、视频内容或输入信息，当前清单未声明网络权限。Android 系统备份已开启，数据可能随系统备份或设备迁移转移，具体取决于设备及系统设置。

## 本地构建

准备 JDK 17 或更高版本、Android SDK，以及支持项目 Android Gradle Plugin 的 Android Studio。项目使用 AGP 9.3.3、Gradle 9.5.0、Kotlin Compose 插件 2.2.10，编译 SDK 为 API 36.1，目标 SDK 为 API 36。Java 源码兼容级别为 11，与运行 Gradle 所用 JDK 不同。

克隆仓库，用 Android Studio 打开并同步依赖。通过 SDK 管理器安装对应 SDK，在 `local.properties` 中配置本机 `sdk.dir`（该文件不提交）。首次构建需要网络下载依赖。

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

macOS / Linux：

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。连接专用测试设备或启动模拟器后运行设备测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

测试运行器可能安装或卸载应用，请使用专用测试设备。发布版本需自行配置签名，不要提交签名密钥、密码或本机配置。

## 项目结构

主要源码位于 `app/src/main/java/com/example/test/`：

| 文件 | 用途 |
| --- | --- |
| `MainActivity.kt` / `TingkeApp.kt` | 应用入口、Compose 界面和设置 |
| `TargetAccessibilityService.kt` | 前台识别、计时、目标和提醒浮层 |
| `DouyinFeedRegion.kt` | 抖音推荐页识别 |
| `ActionIntervention.kt` | 动作和慢速模式的触摸与手势处理 |
| `UsageTracker.kt` | 按日持久化统计 |
| `ReminderTiming.kt` / `SlowTiming.kt` / `SwipeGate.kt` | 提醒、倒计时与滑动门控逻辑 |

`app/src/test` 包含提醒、慢速计时和滑动门控单元测试；`app/src/androidTest` 包含统计持久化和跨日分账的设备测试。

## 兼容性与贡献

四种模式均已接入真实服务。动作和慢速模式依赖抖音页面结构，不同抖音版本、Android 版本及厂商系统可能导致识别或手势执行失败，仍需在真实设备上验证兼容性。

欢迎通过 [GitHub Issues](https://github.com/bin-art-666/android-compose-starter/issues) 提交问题，或通过 Pull Request 贡献代码。问题报告请包含 Android 版本、设备型号、抖音版本、干预模式及复现步骤，避免上传个人信息。

## 许可

项目采用 [MIT License](LICENSE)。第三方依赖仍遵循各自的许可。
