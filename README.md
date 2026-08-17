# StatusBar Force Close

一个只做一件事的现代 LSPosed 模块：双击状态栏，强制停止当前前台应用。

当前版本面向 HyperOS 4、Android 17（SDK 37）和 libxposed API 102。APK 没有桌面图标、Activity、设置界面、Service、Receiver 或 Provider，静态作用域仅包含 `com.android.systemui`。

## 项目缘起

我以前很依赖 CustoMIUIzer 的“双击状态栏关闭当前应用”功能。升级到 HyperOS 4 后，原功能因 SystemUI Hook 点变化而失效；它又是我日常使用频率很高的操作，所以单独做了这个模块。

项目刻意保持单一职责，不依附原模块的其他功能。这样即使 CustoMIUIzer 或其他综合模块以后逐步适配 HyperOS 4，我仍可独立决定是否更新、停用或卸载本模块，避免功能重复或版本耦合。

## 工作方式

1. 在 `com.android.systemui` 中 Hook 当前系统的 `PhoneStatusBarView.dispatchTouchEvent(MotionEvent)`。
2. 仅观察触摸事件，不改变 SystemUI 原有的触摸返回值。
3. 识别满足系统双击时间和位移阈值的两次点击。
4. 查询当前用户的前台任务，排除 SystemUI、桌面、系统设置、系统选择器和本模块自身。
5. 优先执行 `su -c am force-stop --user USER_ID PACKAGE`。
6. 如果 SystemUI 的 SELinux 域不能执行 `su`，回退到 SystemUI 已持有权限的 ActivityManager Binder 调用。
7. 成功后显示 `已强制关闭<应用名>`，应用名来自被停止应用的实际 label，不写死具体应用。

本模块不修改、重签、替换或重新打包任何小米 APK，也不修改系统分区文件。

## 已验证环境

| 项目 | 已验证值 |
| --- | --- |
| 设备 | Xiaomi `nezha` / `25128PNA1C` |
| 系统 | HyperOS `OS4.0.0.10.XPACNXM` |
| Android | Android 17 / SDK 37 |
| Hook 框架 | LSPosed 2.1.1-it，libxposed API 102 |
| 作用域 | `com.android.systemui` |
| 模块版本 | `0.1.0` (`versionCode 1`) |

实机首次验证中，SystemUI 受 SELinux 限制而不能直接执行 `su`，模块按设计回退到 Binder，成功停止应用商店并显示动态 Toast，SystemUI 没有崩溃或重启。这个结果验证的是当前设备与系统版本，不代表其他 HyperOS 4 构建已自动兼容。

## 安装与启用

要求：

- Android 17 / SDK 37；
- 支持 libxposed API 102 的 LSPosed 实现；
- 已获得 root，并允许所用 Hook 框架正常注入 SystemUI。

安装步骤：

1. 安装签名 APK。
2. 在 LSPosed 中启用模块。
3. 保持静态作用域为“系统界面” (`com.android.systemui`)。
4. 重启 SystemUI 或重启设备，使首次 Hook 生效。
5. 打开一个普通应用，双击状态栏验证强制停止和动态 Toast。

不同签名的 APK 不能覆盖安装。早期 debug APK 使用 Android debug 证书；切换到正式签名版本时需要先卸载 debug 版，后续正式版本会持续使用本项目自己的独立签名。

正式签名证书 SHA-256：

```text
41722a4f43310dcd3695da5505416dea825fef37c4e1ea8913543fc938131d90
```

## Debug 日志

所有结构化诊断均受编译期常量 `BuildConfig.DIAGNOSTICS_ENABLED` 控制：

- debug：`true`，同时写入 LSPosed 模块日志和 logcat；
- release：`false`，R8 会移除不可达的诊断分支。

查看 debug 日志：

```powershell
adb -s SERIAL logcat -v threadtime -s StatusBarForceClose
```

主要事件包括 `module_loaded`、`hook_installed`、`double_tap_detected`、`force_stop_start`、`su_result`、`binder_result`、`force_stop_result` 和 `toast_shown`。提交问题时请提供从 `double_tap_detected` 到 `request_released` 的完整同一次 `requestId` 日志，并删除不相关的个人信息。

## 本地构建

```powershell
$env:JAVA_HOME='JAVA_HOME'
$env:ANDROID_HOME='ANDROID_SDK_ROOT'
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

构建签名 release 时额外提供以下环境变量：

```powershell
$env:ANDROID_KEYSTORE_PATH='KEYSTORE_PATH'
$env:ANDROID_KEY_ALIAS='KEY_ALIAS'
$env:ANDROID_KEYSTORE_PASSWORD='KEYSTORE_PASSWORD'
$env:ANDROID_KEY_PASSWORD='KEY_PASSWORD'
./gradlew.bat clean testDebugUnitTest lintDebug assembleRelease --no-daemon
```

密钥和密码不得写入 Gradle 文件、Git 历史或 Actions 日志。

## CI 与发布

- `.github/workflows/ci.yml`：对 `main` 的 push 和 pull request 执行单元测试、lint 和 debug 构建，并上传短期 debug artifact。
- `.github/workflows/release.yml`：手动触发时构建、签名、验证并上传 Actions artifact，不创建 GitHub Release；推送格式为 `versionCode-versionName`（例如 `1-0.1.0`）的 tag 时才发布正式 Release。
- 发布工作流验证 APK 签名、包名、版本、SDK 级别、无组件清单、API102 元数据、唯一静态作用域和入口类，并生成 `SHA256SUMS`。

仓库需要配置四个 Actions Secret：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

## 项目结构

```text
StatusBarForceClose/
|-- .github/
|   |-- ISSUE_TEMPLATE/bug_report.yml
|   `-- workflows/
|       |-- ci.yml
|       `-- release.yml
|-- app/
|   |-- proguard-rules.pro
|   `-- src/
|       |-- main/
|       |   |-- java/com/wayne/statusbarforceclose/
|       |   |-- resources/META-INF/xposed/
|       |   `-- AndroidManifest.xml
|       `-- test/java/com/wayne/statusbarforceclose/
|-- docs/
|   |-- design.md
|   |-- device-evidence.md
|   |-- implementation-plan.md
|   `-- release-validation.md
|-- LICENSE
|-- SCOPE
|-- SIGNING_CERT_SHA256
|-- SOURCE_URL
`-- SUMMARY
```

## 兼容性边界

- 第一版只针对上述实机环境，不为尚未验证的旧 Android 或其他 HyperOS 4 构建宣称兼容。
- 当前 Hook 点依赖 HyperOS 4 SystemUI 的类层级；类或方法不存在时模块记录失败并停止，不尝试模糊匹配未知实现。
- 模块只处理当前用户的前台普通应用，不跨用户猜测目标。
- Root 路径是否可用取决于设备 SELinux 策略；Binder 回退是当前实机上的实际成功路径。
- 双击发生在通知下拉、锁屏或受保护系统目标上时，模块可能选择不执行，以避免关闭关键系统界面。

## License

[MIT](LICENSE)
