# HyperOS 4（Android 17）适配记录

分支：`hyperos4`，基线 `main`（6f15257）。
本文记录本分支做了哪些 HyperOS 4 / Android 17（A17）适配、依据是什么、以及上游 OppoPods 合并评估结论。

## 1. 依据：HyperOS 4 上到底变了什么

| 现象 | 证据来源 |
|---|---|
| `MiuiBluetoothNotification` 在 A17 上有两个构造函数，运行时按 `mibt_memory_trim` 二选一：`(Context, Looper)` 与 `(Looper, BluetoothHeadsetService)` | Leaf-lsgtky/OppoPods `e01d2aa`（Release 1.2.3 with A17 notification compatibility） |
| `wireless_headset` 状态栏图标由 `com.xiaomi.bluetooth` 进程内的 `HeadsetIconShowManager`（`MiuiNearbyServiceV2` 托管，混淆名 `com.android.bluetooth.ble.app.i`）在连接约 500 ms 后隐藏，不经过 `com.android.bluetooth` | 同仓库 `ba0eb14`（HyperOS headset icon guard） |
| A17 可能不暴露 `BluetoothDevice.name`，需要回退 `alias` | 同上 `HeadsetStateDispatcher.isOppoPod` |
| libxposed **API 102** 支持热重载：hook 需要稳定 id，模块要实现 `onHotReloading` / `onHotReloaded`，`module.prop` 声明 `targetApiVersion=102` + `autoHotReload=true` | 同仓库 `HookContext` / `HookEntry` / `module.prop` |
| 设置页在 HyperOS 上出现"旧版 MiuiHeadsetActivity"与"新版数据驱动页（`BluetoothDeviceDetailsFragment` + `IDeviceSettingsConfigProviderService`）"两条并存路径 | 同仓库 `docs/Settings_Headset_Detail_Page_Analysis.md` |

## 2. 本分支的改动

### 2.1 libxposed API 102 + 热重载（`2167434`）
- `gradle/libs.versions.toml`：`libxposedApi` 101.0.1 → **102.0.0**。
- `META-INF/xposed/module.prop`：`targetApiVersion=102`、`autoHotReload=true`（`minApiVersion` 保持 101，API 101 框架下行为不变）。
- `HookContext`：每个 hook 记录 `HookHandle`；API ≥ 102 时用 `"<phase>:<method signature>"` 作为稳定 id；新增 `onHotReloading()`、`hookIds()`、`reloadRemotePrefs()`。
- `HookEntry`：实现 `onHotReloading`（释放目标进程资源 → `detach()`）与 `onHotReloaded`（按旧 handle 的 ClassLoader 重新装载，`unhook` 不再使用的 id）。
- 各 hook 补齐 `onHotReloading`：注销广播、清空回调缓存（`HeadsetStateDispatcher` / `MiBluetoothToastHook` / `MiLinkServiceHook` / `BluetoothUpstreamHeadsetHook`）。

### 2.2 A17 通知构造函数兼容（`2167434`）
`MiBluetoothToastHook` 不再用 `findConstructorByParamCount(..., 2)` 猜构造函数，而是显式匹配 `(Context, Looper)` 与 `(Looper, BluetoothHeadsetService)`，两者都失败时才回退旧逻辑；同时用标志位保证通知广播接收器只注册一次（原来每次构造通知都会重复注册，是泄漏）。

### 2.3 状态栏耳机图标守卫（`49a0b96`）
新增 `hook/XiaomiBluetoothHeadsetIconGuard.kt`，在 `com.xiaomi.bluetooth` 进程 hook `StatusBarManager.setIconVisibility(String, boolean)` 的所有重载，吞掉 `wireless_headset=false`，避免模块点亮的图标被系统再次隐藏。连接状态用"连接广播 + `getConnectedDevices` 实时查询"双信号判定。开关 key：`show_headset_status_bar_icon`（默认 true）。

### 2.4 设备名回退（`2167434`）
`HeadsetStateDispatcher.isOppoPod` 在 `device.name` 为空时回退 `device.alias`。

## 3. 构建验证

```powershell
$env:JAVA_HOME="C:\Users\L1872\tools\jdk22"; $env:ANDROID_HOME="C:\Android"
$env:JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.nonProxyHosts=localhost|127.0.0.1"
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

结果：**BUILD SUCCESSFUL**（38 tasks），APK ≈ 40.8 MB；APK 内 `META-INF/xposed/module.prop` 已确认 `targetApiVersion=102`。
备注：仓库只有 POSIX `gradlew`，Windows 下需用上面的 `java -classpath ...` 方式（或在副本里补 `gradlew.bat`）；`local.properties` 需要写 `sdk.dir=C\:\\Android`。

## 4. 上游合并评估（OppoPods）

已添加远端：`upstream` = 1812z/OppoPods（master，2.2.0）、`leaf` = Leaf-lsgtky/OppoPods（dev，1.4.0）。
与 `upstream/master` 的 merge-base = `f9c6260`，本地 **领先 39 个提交 / 落后 16 个提交**。

直接 `git merge upstream/master` 的结果：**18 个文件冲突**，且包含两处"上游删除、本地依赖"：

| 文件 | 情况 |
|---|---|
| `pods/GameModeImplementation.kt` | 上游删除，本地 `RfcommController`/`MainUI`/`Packets` 仍在用 |
| `ui/pages/DeviceCapabilitiesPage.kt` | 上游删除，本地导航仍指向该页面 |
| `Packets.kt` / `RfcommController.kt` / `DeviceCapabilities.kt` / `ConfigManager.kt` | 双方都大改（本地重写过） |
| `MiBluetoothToastHook.kt` / `MiLinkServiceHook.kt` / `MainUI.kt` / `SettingsPage.kt` / `PodDetailPage.kt` / `AboutPage.kt` / `OppoPodsAction.kt` / `strings.xml`×2 / `libs.versions.toml` / `app/build.gradle.kts` / `README*` | 内容冲突 |

上游 2.x 还引入了 LE Audio（`LeAudioConnectionHook` 504 行、`LeAudioConfig`、`LeAudioSettingsPage`）、`DeviceModelRegistry`、`EqualizerPage`（396 行）、Miuix nav 迁移、LC3 开关等新结构。

**结论**：这不是"合并"，而是"把 2.x 的新架构移植到 MotoBuds 定制分支上"，需要按模块分批做、每批都要能编译+真机验证。建议顺序：
1. 先合 `DeviceModelRegistry` + 能力识别修复（自带单测）；
2. 再合 `EqualizerPage`（UI 独立，依赖少）；
3. 然后 LE Audio（涉及 `HeadsetStateDispatcher` / `ConfigManager` / 新页面，冲突最大）；
4. 最后处理 `MainUI`/`MainTabs` 的导航与设置页重组。

## 5. 待真机验证（HyperOS 4）

- [ ] 热重载：更新模块后 LSPosed 日志应出现 `Hot reload package=... process=...`，且不再需要重启作用域。
- [ ] 状态栏图标：连接 Moto Buds 后图标持续显示（不再闪现后消失）。
- [ ] 通知：`com.xiaomi.bluetooth` 里 `MiuiBluetoothNotification` 的两个构造函数至少命中一个（看 `MotoBuds-Notification` 日志）。
- [ ] 超级岛 / 融合设备中心 / 设置页降噪控制是否仍可用；若设置页走新版数据驱动路径（`BluetoothDeviceDetailsFragment`），旧 `SettingsHeadsetHook` 路线需切换到 provider 方案（见上游分析文档）。
