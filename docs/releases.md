# 版本与 APK 发布约定

## Obtainium 更新

1. 在 Obtainium 添加应用，来源填 `https://github.com/Mlevngr/mote`。
2. 在 GitHub 来源的额外选项中，将 **Filter APKs by Regular Expression / 按正则表达式过滤 APK** 设为 **`^Mote\.apk$`**。
3. 使用正式发布（不启用预发布）；不使用“发布日期作为版本”，也不需要禁用版本检测。正式标签 `v0.8.2` 对应 APK 内 `0.8.2`。
4. 添加后可由 Obtainium 下载、安装和检查更新。Android 仍可能要求授予安装未知应用权限或确认安装，不保证无人值守安装。

固定下载入口：[最新正式版 Mote.apk](https://github.com/Mlevngr/mote/releases/latest/download/Mote.apk)。
两个插件是不同应用：过滤分别为 `^Mote-AI-Organizer\.apk$` 和 `^Mote-Local-Organizer\.apk$`；主程序过滤不会误选插件。插件 APK 的版本独立于主程序发布标签，若用 Obtainium 跟踪插件需单独关闭版本检测、按发布标签跟踪，或者手动下载；主程序不要关闭版本检测。

依据：[Obtainium 官方来源说明](https://wiki.obtainium.imranr.dev/sources/)、[版本跟踪说明](https://wiki.obtainium.imranr.dev/app_tracking/)。

## 版本规则

- `version.properties` 是主程序 `versionName` / `versionCode` 的唯一来源。
- 正式版使用 SemVer `主.次.修订`：此次 `0.8.2`；修复发布递增修订号，新增兼容功能递增次版本，重大不兼容变更递增主版本。
- 预发布使用 `0.9.0-beta.1` 或 `0.9.0-rc.1`；正式标签必须严格等于 `v${versionName}`。不再使用 APK 内版本不变而只给标签增加 `-rN` 的方式。
- 每一次公开发布（包括预发布）都必须提高 `versionCode`，此次从 56 提高到 58。它独立于 SemVer，不根据提交数、时间或 CI 重跑自动生成。
- 不覆盖已发布标签或替换同版本 APK。修复构建需要新版本、新 code、新标签；失败且未发布的 CI 可用同标签重跑。
- 插件保持各自独立的版本；只有插件代码变化时才在其 Gradle 配置中递增，不把主程序版本当插件版本。

Android 用 `versionCode` 判断安装先后顺序，`versionName` 是用户可见版本。参见 [Android 官方版本说明](https://developer.android.com/studio/publish/versioning)。

## 发布步骤（维护者）

1. 修改代码和测试，更新 `version.properties`，新增 `docs/releases/<versionName>.md`。
2. 运行版本校验（从旧式 r25 迁移也支持）：
   ```sh
   python3 -m unittest discover -s scripts/tests -v
   python3 scripts/release.py check --tag v0.8.2 --previous-tag v0.8.0-r25
   ./gradlew testDebugUnitTest lintDebug assembleRelease
   ```
3. 提交并推送主分支；CI 先在带 KVM 的 API 35 模拟器执行设备 smoke。主分支校验通过后再创建并推送对应标签；标签构建重复同样的检查，通过后才发布。CI 获取完整历史，对照所有其他版本标签验证版本递增、拒绝覆盖已有 Release、检查 tag 匹配、发布说明存在，运行所有模块的测试/lint，构建正式 APK。
4. 仅从干净、已提交且对应标签指向 HEAD 的检出目录运行 `python3 scripts/release.py package --tag v0.8.2`。它校验 APK 清单/构建元数据、包名、versionName/code 和签名，并输出到**必须为空**的 `build/release/`。本地重复打包请使用 `--output build/release-<unique-name>`，不覆盖旧输出。
5. 产物统一为 `Mote.apk`、两个 `Mote-*-Organizer.apk`、`SHA256SUMS`、`release.json`。主程序为单一通用 APK；不混入 debug/unsigned 或 ABI 分包。
6. CI 全部成功后创建正式 GitHub Release（含 `-beta.N`/`-rc.N` 的标签才为 prerelease）。检查 Release 可见、下载 APK 的元数据、SHA-256 和签名。下载后可用 `sha256sum -c SHA256SUMS` 校验全部 APK。

`release.json` 记录主程序包名、版本、提交、签名证书指纹和每个 APK 的 SHA-256。CI 还会归档同一批文件；GitHub Release 仅上传校验后的目录，不直接上传未经校验的构建输出。

## 签名与安全边界

为了覆盖更新，保持既有 `com.mlevngr.inknote` 包名与证书指纹：

```
59bd95a37b9916c7dc073caf54474e5fed5ea112270da22f7df14c33692ed12d
```

此证书由 `signing/certificate.sha256` 锁定；更换签名会被打包检查拒绝。**现有开发密钥及密码已在仓库公开**，只能用于当前开源测试分发，任何人都可能用该密钥签名；不能声称具有生产发布安全性。Obtainium 能提供更新便利，但不能消除公开密钥风险。

本次不擅自更换证书，避免破坏覆盖安装。未来生产密钥应以独立迁移方案处理兼容性，并存入 CI Secrets；迁移前先备份笔记，不要求用户直接卸载当前应用。

## 设备端回归

主分支推送和发布标签均执行设备 smoke；新主分支提交会取消过时的验证，发布标签构建则不会被新提交取消。日志归档为 Actions 的 `device-evidence-*`。无需新增测试依赖，设备 smoke 使用 Android 平台 Instrumentation，覆盖真实 Activity 与触摸事件分发、双指转单指、长笔记上下导航、连续 20 次勾选刷新、阅读位置进入编辑及过期焦点取消。

**只在模拟器或专用测试设备上执行**（会在应用私有笔记库创建测试笔记）：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 scripts/device-smoke.sh --skip-build
```

这是无 AndroidX runner 依赖的专用 smoke runner，不声称替代所有 UI 测试。仍需人工检查不同输入法、长按选文与选择手柄、图片/PDF 高度变化、键盘遮挡和 4/6 倍操作；纯数学单元测试不能作为设备体验通过的证据。
