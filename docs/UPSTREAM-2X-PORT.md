# 上游 2.x 架构移植记录

目标：把 `1812z/OppoPods` 2.2.0 的新架构按模块移植到本 MotoBuds 定制分支，而不是整体 merge
（整体 merge 有 18 个冲突，且上游删除了本分支仍在用的类）。

## 已移植

### 1. LE Audio 兼容链路（2.x 的核心新架构）

| 文件 | 作用 |
|---|---|
| `config/LeAudioConfig.kt` | LE Audio 配置模型（总开关、设备白名单、AT 协议参数、连接策略、GATT 保持、上下文等 14 项） |
| `hook/leaudio/OppoLeAudioHook.kt` | 厂商 AT 握手（VDID / VDSF / OESF / VDSP），挂在 `com.android.bluetooth` 进程 |
| `hook/leaudio/LeAudioConnectionHook.kt` | 500 行事件驱动的连接/稳定性路径：修复 A2DP/HFP 策略、LE 优先尝试、直连握手、经典回连、双耳 GATT 持有、CSIP 组补齐、上下文修复 |
| `ui/pages/LeAudioSettingsPage.kt` | 设置页：总开关、设备白名单、延迟/连接/稳定性/协议四组高级开关 |
| `ui/dialogs/LeAudioDevicesDialog.kt` | 配对设备多选（MAC 白名单） |

入口：**设置 → OPPO LE Audio**。默认总开关关闭、白名单为空，需要用户手动选择耳机后才接管。

适配点：
- 用于本分支的 `com.xiuxiu391.motobuds.R`（上游用 `moe.chenxy.oppopods.R`）
- `AppConfig` 里新增 `leAudio` 字段（与原有的 capability override 字段并存），
  `ConfigManager.updateLeAudio()` 走同一份 `config_json` 与远端 prefs 同步
- 两个 hook 都补了 `onHotReloading()` 清理（API 102 热重载要求）
- `HookEntry` 在 `com.android.bluetooth` 分支最先加载 `OppoLeAudioHook`

### 2. 焦点通知总开关

- `AppConfig.focusNotification`（默认开）+ `PREF_KEY_FOCUS_NOTIFICATION` 直读兼容
- `MiBluetoothToastHook`：关闭时 `onHook` 直接跳过；运行中关闭则撤掉已显示通知
- 设置页「超级岛」卡片内新增开关，切换后向 `com.android.bluetooth` / `com.xiaomi.bluetooth` 广播配置变更

### 3. 机型能力判定（上一轮已移植并保留）

`0x0000` 解析 `XT####-#` → `DeviceModelRegistry` 功能表 → 驱动自适应/空间音频等能力。

## 未移植（有意保留本分支现状）

| 上游 2.x 内容 | 不移植原因 |
|---|---|
| 官方超级岛修复 | 本分支 v1.3.1 已移除"官方超级岛"选项，只保留模块内超级岛 |
| EqualizerPage / 10 段 EQ / 预设管理 | 按你的要求已删除均衡器功能 |
| Miuix Nav 迁移 | 本分支已用 Navigation3 + 独立 Screen，迁移收益低 |
| ConfigManager 结构重构（milinkCardFeatures / autoGameMode 进 AppConfig） | 会破坏本分支的 capability override 体系，改为增量加字段 |
| `gradle/gradle-daemon-jvm.properties` | 上游声明 toolchain 21，与本项目 Java 22 冲突 |

## 构建验证

```text
BUILD SUCCESSFUL (assembleDebug, JDK 22 / compileSdk 37)
```

## 真机验证清单（LE Audio）

1. 设置 → OPPO LE Audio → 打开总开关 → 选择目标设备并保存（白名单）
2. 重启蓝牙 + 重开耳机盒
3. 看 LSPosed 日志 `OppoPods-LEAudio`：AT 握手（VDID/VDSF/OESF/VDSP）是否被应答
4. 确认经典 A2DP/HFP 仍可用（关键：很多 LE 方案会误杀 HFP，导致 AT 进不来）
5. 双耳是否都在 LE 音频；单耳断连时看 GATT holder 是否重新挂上
6. 关闭总开关后是否恢复经典路径
