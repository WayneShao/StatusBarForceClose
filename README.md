# StatusBar Force Close

一个只做一件事的现代 LSPosed 模块：双击状态栏，强制停止当前前台应用，并以动态 Toast 显示实际被停止的应用名称。

当前版本面向 Android 16/17、libxposed API 102 和已 root 设备。APK 没有桌面图标、Activity、设置界面、Receiver 或 Provider；仅声明一个供 SystemUI 显式绑定的桥接 Service，静态作用域只有 `com.android.systemui`。

## 项目缘起

我以前很依赖 CustoMIUIzer 的“双击状态栏关闭当前应用”功能。升级到 HyperOS 4 后，原功能因 SystemUI Hook 点变化而失效；它又是我日常使用频率很高的操作，所以单独做了这个模块。

我日常双持 Xiaomi 17 Ultra for Leica 和 OnePlus 13T，分别运行 HyperOS 4 和 ColorOS 16。`0.2.0` 已在两部手机上使用同一个 APK 完成实机验证。项目保持单一职责，不依附综合模块的其他功能，便于独立更新、停用和卸载。

## 工作方式

模块将厂商相关的触摸入口与通用的强制停止后端分开：

1. HyperOS 4 使用 `PhoneStatusBarView.dispatchTouchEvent(MotionEvent)` 观察触摸事件。
2. ColorOS 16 Hook `PhoneStatusBarView.onFinishInflate()`，为状态栏附加不消费事件的 `OnTouchListener`。
3. 两种入口共用系统双击时间、位移阈值和同一个前台任务解析流程。
4. 从真实前台任务读取 `packageName`、应用 label 和 Android `userId`，不把双开应用固定按用户 `0` 处理。
5. SystemUI 显式绑定模块的 `ForceStopBridgeService`；Service 只接受调用 UID 对应包名包含 `com.android.systemui` 的请求。
6. 模块进程通过 libsu 6.0.0 启动 RootService；root 进程取得系统 ActivityManager Binder，并执行 `forceStopPackage(packageName, userId)`。
7. RootService 不可用时才尝试 SystemUI 自身的 Binder 回退。跨厂商稳定能力以 root 路径为准。
8. 成功后显示 `已强制关闭<应用名>`，应用名来自实际目标，不写死具体软件。

当前固定保护 Android 核心、SystemUI、MiuiHome 和当前输入法。系统设置与本模块自身不在保护名单中。找不到真实任务用户 ID、Hook 结构不匹配或 root/Binder 均失败时，本次操作会停止，不猜测目标。

本模块不修改、重签、替换或重新打包任何厂商 APK，也不修改系统分区文件。

## 已验证环境

| 设备 | 系统 | Android | SystemUI Hook | 强制停止结果 |
| --- | --- | --- | --- | --- |
| Xiaomi 17 Ultra for Leica (`nezha` / `25128PNA1C`) | HyperOS `OS4.0.0.10.XPACNXM` | Android 17 / SDK 37 | `MIUI_DISPATCH` | libsu RootService，root 进程 `uid=0` |
| OnePlus 13T (`PKX110`) | ColorOS 16，`PKX110_16.0.10.500(CN01)` | Android 16 / SDK 36 | `OPLUS_INFLATE_LISTENER` | libsu RootService |

两台设备均使用 LSPosed 2.1.1-it 和 libxposed API 102。验证包括状态栏事件捕获、前台微信 `user=0` 解析、RootService 成功结果、动态 Toast 和 SystemUI 稳定性。用户 ID `999` 及其他有效用户的传递由单元测试覆盖，但本次 `0.2.0` 定版不把它描述为新增的双机实测结果。

以上是精确构建上的实机结果，不代表所有厂商 SystemUI 已自动兼容。未知类结构会 fail closed，不安装模糊 Hook。

## 安装与启用

要求：

- Android 16 / SDK 36 或更高版本；
- 支持 libxposed API 102 的 LSPosed 实现；
- 设备已 root，并向 `StatusBar Force Close` 授予 root 权限；
- LSPosed 可以正常注入 `com.android.systemui`。

安装步骤：

1. 安装签名 APK。
2. 向本模块授予 root 权限。
3. 在 LSPosed 中启用模块，保持作用域为“系统界面” (`com.android.systemui`)。
4. 重启 SystemUI 或重启设备，使 Hook 生效。
5. 打开一个普通应用，双击状态栏验证强制停止和动态 Toast。

不同签名的 APK 通常不能覆盖安装。早期 Debug APK 使用 Android Debug 证书；切换到正式签名版本时通常需要先卸载 Debug 版。后续正式版本持续使用本项目自己的独立证书，可以直接覆盖升级。

正式签名证书 SHA-256：

```text
41722a4f43310dcd3695da5505416dea825fef37c4e1ea8913543fc938131d90
```

## Debug 日志

结构化诊断受编译期常量 `BuildConfig.DIAGNOSTICS_ENABLED` 控制：

- Debug：`true`，写入 LSPosed 模块日志和 logcat；
- Release：`false`，R8 移除不可达的诊断分支。

查看 Debug 日志：

```powershell
adb -s SERIAL logcat -v threadtime -s StatusBarForceClose
```

主要事件包括：

- Hook：`module_loaded`、`hook_strategy_selected`、`hook_installed`、`touch_listener_attached`；
- 手势与目标：`double_tap_detected`、`target_resolved`、`force_stop_start`；
- 模块桥接：`root_bridge_bind_start`、`bridge_request_received`、`libsu_root_connected`；
- root 执行：`root_service_bound`、`root_force_stop_start`、`root_force_stop_result`；
- 最终结果：`force_stop_result`、`toast_shown`、`request_released`。

提交问题时请提供同一个 `requestId` 从 `double_tap_detected` 到 `request_released` 的完整日志，并删除不相关的个人信息。

## 本地构建

```powershell
$env:JAVA_HOME='JAVA_HOME'
$env:ANDROID_HOME='ANDROID_SDK_ROOT'
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleRelease --no-daemon
```

构建签名 Release 时额外提供：

```powershell
$env:ANDROID_KEYSTORE_PATH='KEYSTORE_PATH'
$env:ANDROID_KEY_ALIAS='KEY_ALIAS'
$env:ANDROID_KEYSTORE_PASSWORD='KEYSTORE_PASSWORD'
$env:ANDROID_KEY_PASSWORD='KEY_PASSWORD'
./gradlew.bat clean testDebugUnitTest lintDebug assembleRelease --no-daemon
```

密钥和密码不得写入 Gradle 文件、Git 历史或 Actions 日志。

## CI 与发布

- `.github/workflows/ci.yml`：对 `main` 的 push 和 pull request 执行单元测试、lint 和 Debug 构建。
- `.github/workflows/release.yml`：手动触发时生成保留诊断的签名测试包；推送 `versionCode-versionName` tag 时生成关闭诊断的正式 Release。
- 发布工作流验证签名证书、包名、版本、SDK、唯一桥接 Service、零 Android 权限、API102 元数据、静态作用域、入口类、R8 日志剥离和 SHA-256。

签名材料仅保存在 GitHub Actions Secrets 中。

## 项目结构

```text
StatusBarForceClose/
|-- .github/
|   |-- release-notes/
|   `-- workflows/
|-- app/
|   `-- src/
|       |-- main/
|       |   |-- aidl/com/wayne/statusbarforceclose/
|       |   |-- java/com/wayne/statusbarforceclose/
|       |   |-- resources/META-INF/xposed/
|       |   `-- AndroidManifest.xml
|       `-- test/java/com/wayne/statusbarforceclose/
|-- docs/
|-- LICENSE
|-- SCOPE
|-- SIGNING_CERT_SHA256
|-- SOURCE_URL
`-- SUMMARY
```

## 兼容性边界

- 当前 APK 为 `minSdk 36`、`targetSdk 37`；更早 Android 版本不在本次发布范围内。
- root 强停后端与厂商无关，但状态栏 Hook 入口仍依赖各厂商 SystemUI 结构。
- 当前只为已验证的 HyperOS 4 和 ColorOS 16 结构安装 Hook；其他结构会记录 `UNSUPPORTED` 并停止。
- 模块保留 Binder 回退用于兼容与诊断，但 ColorOS 16 的实机结果证明其 SystemUI 没有强停权限，跨厂商使用必须授予模块 root。
- 模块传递真实 Android 用户 ID；读取失败时不回退到用户 `0`。
- release 构建不保留 Debug 诊断字符串，需要排障时使用对应提交的 Debug 或签名测试 artifact。

## License

[MIT](LICENSE)
