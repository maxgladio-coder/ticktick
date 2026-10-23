# 律动 · TickTick

一个适合手机操作的原生 Android 节拍器。Kotlin + Jetpack Compose，Android 8.0（API 26）及以上，无广告、无需登录、无需麦克风权限。节拍与测速离线可用，仅手动检查更新时联网。

## 功能

- 30–300 BPM；滑动、±1 / ±5 按钮和直接输入。开始／停止与快捷 TAP 固定在底部。
- 1–12 拍的小节；以四分或八分音符为一拍。
- 每拍 1、2、3、4 等分；可关闭首拍重音。
- 木鱼／电子音色、应用音量、前台节拍触感。
- 前台服务支持锁屏与后台播放，通知栏可停止；音频焦点丢失、通话打断或耳机拔出时停止，不擅自恢复。
- 自动保存节拍设置；重新打开应用时不自动开始播放。
- 独立敲击测速区，显示整数 BPM、历史波动和稳定度；估算结果四舍五入后显示，应用时使用同一个整数。
- 设置中的“检查更新”读取 GitHub 最新正式 Release，显示版本与更新说明，通过浏览器下载 APK 后确认安装。

## 构建和运行

使用支持 AGP 8.13 的 Android Studio，打开本目录，安装 Android SDK Platform 36 和 Build Tools 35.0.0，然后同步项目并点击 Run。选择 Android 8.0 以上真机或模拟器。

也可在 PowerShell 中运行（需要 JDK 17 或 21）：

```powershell
$env:JAVA_HOME = '你的 JDK 路径'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

在 `local.properties` 设置你的 `sdk.dir`，或设置 `ANDROID_HOME`。`local.properties` 与本机下载工具均已加入 `.gitignore`。

Gradle 原始 APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。本机构建脚本同时生成带版本号的发布安装包：`app/build/outputs/publish/ticktick-<versionName>.apk`；也可在构建后运行 `scripts/package-apk.ps1` 生成。GitHub Release 上传带版本号的安装包。

GitHub 更新的发布步骤、版本标签和签名要求见 [发布说明](docs/RELEASING.md)。

设备 UI 测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

本机专用构建脚本 `scripts/build-local.ps1` 会优先使用 `JAVA_HOME`，然后检测 Android Studio / PyCharm 附带的 JDK，使用项目内 SDK 与缓存。此脚本不会自动下载或安装工具。

## 敲击算法

核心位于 `app/src/main/java/com/maxgladio/ticktick/core/TapTempoEstimator.kt`，与 Android 无耦合。

1. 在触摸按下时读取单调时钟，避免系统时间调整和手指抬起延迟。
2. 每次敲击都滚动计算最近最多 40 次敲击（39 个间隔），不限制这段历史的总时长；达到上限后旧数据自动移出，慢速和快速均可积累完整 40 次窗口。连续敲击没有次数或总时长限制。小于 150 ms 的重复输入不移动时间锚点。
3. 用间隔中位数和 MAD（中位绝对偏差）估计中心与波动。明显离群间隔不参与均值，其余间隔按 Huber 型权重降权。
4. 近期权重为 `exp(-ageMs / 7000)`，再与稳健权重相乘。BPM 为 `60000 / 加权平均间隔`，不会直接平均 BPM。
5. 接近两倍或三倍中位间隔的孤立漏拍先归一化，不重复投票。
6. 历史足够时，连续三个一致、且与旧节奏差异至少 18% 的间隔触发新速度窗口，包括有意的半速／倍速敲击。更小的变化由滑动窗口逐渐跟随。
7. 至少三个有效间隔才显示结果；停顿超过 4 秒后，下一次敲击开启新一轮。最后估算值保留供应用。

稳定度综合有效样本数、间隔变异和有效比例，表示敲击一致性，不是概率或绝对精度保证。一般连续敲击 8–12 次比四次更可靠。密集漏拍和有意半速存在固有歧义，程序根据连续敲击的一致性判断。

## 音频与节奏约定

`ClickSynthesizer` 按 48 kHz PCM 样本安排节拍，以浮点帧期限累计间隔，再落到实际采样帧上，避免每拍取整产生长期漂移。音频专用线程通过 `AudioTrack.WRITE_BLOCKING` 持续输出；界面亮拍根据播放帧位置延后通知，不承担声音调度。

BPM 始终表示每分钟主拍数。比如 6/8 是每小节六个八分音符主拍，不自动解释成两个附点四分音符大拍。细分是对当前主拍均分。速度、拍号与细分变化在下一主拍边界应用。

Bluetooth 和不同设备音频链路可能产生固定延迟；采样时钟避免的是应用逐拍调度漂移，不能消除设备输出延迟。触感仅在应用前台提供，并遵循系统触感开关。

## 验证

JVM 测试覆盖抖动、多速度收敛、误触、漏拍、半速／倍速切换、历史上限、超时、任意输入及 PCM 时间线、静音、重音和拍号变化。设备测试验证开始按钮和显式应用测速结果。

真机验收项目见 `docs/DEVICE_CHECKLIST.md`。构建与测试的实际结果见 `docs/VALIDATION.md`。

## 依赖说明

- AGP 8.13.2 / Gradle 8.13 / Kotlin 2.2.21
- Compose BOM 2025.12.01 / Material 3
- Activity 1.12.2 / Lifecycle 2.10.0

版本兼容依据：[Android Gradle Plugin 8.13 文档](https://developer.android.com/build/releases/agp-8-13-0-release-notes)。前台播放服务配置依据：[Android 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#media)。
