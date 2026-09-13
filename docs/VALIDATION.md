# 验证结果

验证日期：2026-09-14，版本 1.0.1，Windows 11，JDK 21，Android SDK 36。

最终执行：

```powershell
.\scripts\build-local.ps1 -Tasks ':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug'
```

结果：`BUILD SUCCESSFUL`。

| 检查 | 结果 |
| --- | --- |
| Debug APK | 编译成功 |
| 敲击算法 | 12 个 JVM 测试通过 |
| PCM 合成和节拍时钟 | 4 个 JVM 测试通过 |
| Compose 界面 | 2 个 Robolectric 测试通过 |
| Android Lint | 0 错误；8 条非阻断建议（依赖更新和资源配置等） |
| 设备测试 APK | 1.0.0 时已编译；本轮未执行设备测试 |
| APK 签名 | apksigner 校验通过，v2 签名 |

共 18 个本机测试通过，无失败或跳过。敲击测试包括多个目标 BPM 下的时间戳抖动、意外重复输入、漏拍、持续变速、历史范围和 10,000 次随机输入；新增超过两分钟、280 个连续间隔的滚动窗口测试，确认不停止输出且旧速度被最近窗口替代。音频时钟测试渲染两分钟、137 BPM、三等分的 PCM，逐个主拍核对期限误差不超过约一个采样帧。

UI 测试验证 98.4 的内部估算只显示为 98，点击应用后使用同一个整数；同时覆盖 ±5 操作、设置入口、小屏可滚动性、底部按钮可达性，以及快捷 TAP 在按下时记录一次、抬起不重复记录。

## 已检查的界面渲染

截图直接来自应用 Compose 界面在 Robolectric API 34 原生图形模式下的渲染，使用模拟测速数据，不是真机截图。

- [393 × 852 dp 主界面](screenshots/phone-home.png)
- [测速结果区域](screenshots/phone-tap.png)
- [320 × 640 dp、150% 字体、12 拍](screenshots/small-phone.png)

已根据渲染结果修复小屏节奏设置挤换行，并在底部增加始终可达的快捷 TAP。

## 尚需实际设备验证

尚未在真机或 Android 模拟器上启动应用。实际扬声器输出、Bluetooth 延迟、锁屏持续播放、通知权限与音频焦点打断仍需按 [真机验收清单](DEVICE_CHECKLIST.md) 验证；Robolectric 不验证这些硬件和系统服务行为。

APK 是用于安装测试的 Debug 版本，不是应用商店发布版本。
