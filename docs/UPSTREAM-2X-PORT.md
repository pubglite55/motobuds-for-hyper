# 上游 2.x 架构移植记录

目标：把 `1812z/OppoPods` 2.2.0 的新架构按模块移植到本 MotoBuds 定制分支，而不是整体 merge
（整体 merge 有 18 个冲突，且上游删除了本分支仍在用的类）。

## 当前保留的 2.x 成果

| 模块 | 内容 |
|---|---|
| **焦点通知总开关** | `AppConfig.focusNotification`（默认开）；`MiBluetoothToastHook` 关闭时跳过安装、运行中关闭会撤掉已显示通知；设置页开关 |
| **机型能力判定** | `0x0000` 响应解析 `XT####-#` → `DeviceModelRegistry`（移植自 Motorola `buds_features.json`）→ 驱动自适应/空间音频/Hi-Res/音量增强能力 |
| **Hi-Res / 音量增强能力位** | 功能位 109 / 113；不支持的机型可在「设置 → 设备能力」用强制开关打开 |

## 已移植后移除：LE Audio 兼容链路

曾移植 `LeAudioConfig`、`hook/leaudio/OppoLeAudioHook`（OPPO 厂商 AT 握手：VDID/VDSF/OESF/VDSP，
vendor id 1946）、`LeAudioConnectionHook`（A2DP/HFP 策略修复、LE 优先、GATT holder、CSIP 组补齐）、
`LeAudioSettingsPage`、`LeAudioDevicesDialog` 与 42 条文案。

**移除原因**：
1. 核心是 OPPO 私有 AT 协议，Moto Buds 使用自家的 "HE" SPP 协议，不会发这些指令；
2. 协议里的 `0x410 LE_AUDIO_STATE_CHANGED` / `QUERY_LE_AUDIO` 从未被实际查询，Moto Buds 是否走 LE Audio 未验证；
3. 默认关闭 + 白名单为空时完全惰性，却会在设置页造成"给 Moto Buds 加了功能"的误导；
4. 1100 行不可真机验证的 hook 维护成本高。

**移除范围**：`config/LeAudioConfig.kt`、`hook/leaudio/*`、`ui/pages/LeAudioSettingsPage.kt`、
`ui/dialogs/LeAudioDevicesDialog.kt`、`HookEntry` 加载、`ConfigManager.leAudio`/`updateLeAudio`、
设置页入口、3 个语言包的全部 `le_audio_*` 文案。

## 官方超级岛：恢复并设为默认

- `AppConfig.islandMode` 默认值改回 `ISLAND_MODE_OFFICIAL`
- 设置页恢复「不显示 / 官方 / 模块」三项
- 官方模式走已有实现 `BluetoothUpstreamHeadsetHook.patchHeadsetWearIslandBundle()`
  （改写系统 `headset_wear_notification` 的 `param`/`island_param` 电量文本，再放行）
- 不采用上游"自建 strong toast + 系统 mp4"方案：本分支没有 `com.xiaomi.bluetooth` 的 raw 资源

## Hi-Res / 音量增强：详情页补齐

- `PodDetailPage` 新增两个 `SwitchPreference`（参数此前一路传到页面却没有渲染）
- `DeviceCapabilities` 新增 `hiResSupported` / `volumeBoostSupported`（尊重 capability override）
- 「设置 → 设备能力」新增两个「自动 / 强制关闭 / 强制开启」下拉，不支持机型可强制打开
- Hi-Res 与游戏模式互斥联动保持不变

## 未移植（有意保留本分支现状）

| 上游 2.x 内容 | 不移植原因 |
|---|---|
| 官方超级岛 strong toast 自建方案 | 缺系统 raw 资源，改为改写系统通知 |
| EqualizerPage / 10 段 EQ | 产品决定删除均衡器 |
| Miuix Nav 迁移 | 本分支已用 Navigation3 + 独立 Screen |
| ConfigManager 结构重构（milinkCardFeatures 等） | 会破坏本分支 capability override 体系，改为增量加字段 |
| `gradle-daemon-jvm.properties`（toolchain 21） | 与本项目 Java 22 冲突 |

## 构建验证

```text
BUILD SUCCESSFUL (assembleDebug, JDK 22 / compileSdk 37)
```
