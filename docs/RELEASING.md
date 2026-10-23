# GitHub 更新发布

应用手动请求 `https://api.github.com/repos/maxgladio-coder/ticktick/releases/latest`，无需令牌。仓库必须公开，并至少有一个正式 Release；只有推送源码或 Git 提交不会触发应用更新。

每次发布：

1. 递增 `app/build.gradle.kts` 的 `versionCode`，设置 `versionName` 为三段数字，例如 `1.1.0`。
2. 执行 `scripts/build-local.ps1` 编译与测试。沿用最初安装包的签名密钥；密钥不进入 Git，也不上传到 Release。当前本地 Debug 签名由 `.android/debug.keystore` 保存，应另行安全备份。其他电脑重新生成的 Debug 密钥无法覆盖安装旧包。
3. 将对应源码提交、推送，创建与 APK 版本一致的标签，例如 `v1.1.0`。
4. 在 GitHub Releases 创建正式版本，填写更新说明，上传唯一的通用 APK，设为最新版本。不要标记为 draft 或 prerelease。
5. 用未登录浏览器检查 Releases 和 APK 均可访问；旧版应用点击“检查更新”应提示升级，新版应显示“已是最新版本”。首次使用更新功能需要手动安装 1.1.0。

版本按数字比较，不按字符串排序。不支持预发布标签或带构建元数据的标签。没有唯一 APK 时引导用户查看 Release；404、限流和网络错误会明确显示失败，不当作已是最新版。下载由浏览器完成，应用不后台安装，也不申请安装包管理权限。Release 的 APK 必须保持相同 applicationId、签名和递增的 versionCode。

## 1.1.0 发布说明

- 敲击测速的滚动缓冲增至最多 40 次敲击，继续显示整数 BPM。
- 新增设置内 GitHub 更新检查、更新说明与 APK 下载入口。
- 节拍器和测速仍可离线使用。
