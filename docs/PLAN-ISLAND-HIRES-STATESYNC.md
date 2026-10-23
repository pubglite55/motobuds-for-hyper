# 方案：官方超级岛回归 + 音量增强/Hi-Res 回归 + 连接后状态检测

> 状态：方案稿（未实施）
> 分支：`hyperos4`
> 结论先行：三件事共享同一个前提——**连接后必须先把状态查全，UI 才能可信**。建议实施顺序 **C → B → A**。

---

## 一、现状诊断（基于代码，不是猜测）

### 1. 官方超级岛：功能被"摘掉"，但代码还在

| 证据 | 位置 |
|---|---|
| 选项列表只剩 2 项：`NONE`、`MODULE` | `SettingsPage.kt`：`islandModeValues = listOf(NONE, MODULE)` |
| 文案其实还在：`island_mode_official`（官方 / Official） | `values/strings.xml`、`values-zh-rCN/strings.xml` |
| 模块内岛只在 MODULE 模式生效 | `MiBluetoothToastHook.kt`：`if (islandMode() != ISLAND_MODE_MODULE) return` |
| **官方岛改写逻辑已存在但成了死代码** | `BluetoothUpstreamHeadsetHook.patchHeadsetWearIslandBundle()`：`if (islandMode() != ISLAND_MODE_OFFICIAL) return` —— 永远进不去 |
| 默认值仍写 OFFICIAL | `SettingsPage.kt` 参数默认 `mutableStateOf(ISLAND_MODE_OFFICIAL)`，与可选项不一致 |

即：**只差"恢复选项 + 接上触发路径 + 失败降级"**，不是从零做。

### 2. 音量增强 / Hi-Res：整条链路都在，只缺详情页控件

| 层 | 状态 |
|---|---|
| 协议 | `SET_VOLUME_BOOST_STATE 0x314`、`SET_HI_RES_MODE 0x30D`、通知 0x315/0x311 都在；解析已修（0x1D/0x1E、[支持,状态]） |
| 控制器 | `setVolumeBoost()`、`setHiResMode()`、互斥联动（开 Hi-Res 自动关游戏模式，反之亦然）都在 |
| 广播 | `ACTION_VOLUME_BOOST_SET`/`ACTION_PODS_VOLUME_BOOST_CHANGED`、`ACTION_HI_RES_MODE_SET`/`..._CHANGED` 都在 |
| MainUI/MainTabs | `displayVolumeBoost`、`displayHiResMode` 一路传到 `PodDetailPage` |
| **PodDetailPage** | **只有 gameMode / spatialAudio / EQ 三个控件，volumeBoost 与 hiResMode 参数收到却没用** |
| 字符串 | `volume_boost`、`volume_boost_summary`、`hi_res_mode`、`hi_res_mode_summary` 都在 |

结论：**只是 UI 漏渲染**（很可能是某次重写详情页时丢的），补两个 `SwitchPreference` + 能力门控即可。

### 3. 连接后状态检测：查得不全、没有超时/重试/完成事件

`sendStatusQueryPackets()` 目前发 17 条查询（型号、能力、名称、电量、ANC、EQ、游戏模式、音量增强、双连接…），但：

- **缺**：Hi-Res 读（0x30C，`Enums` 里连 `QUERY_HI_RES` 都没定义）、空间音频读（0x30A 有包但没发）、入耳（0x402）、LE Audio（0x410）
- **没有**：超时、重试、"同步完成"事件；没回包就静默停在旧值
- **多进程**：`MiLinkServiceHook` / `BluetoothUpstreamHeadsetHook` 用跨进程 prefs 缓存状态，连接瞬间可能读到上一轮的旧值
- UI 没有"同步中/未检测到"的表达，用户看到的是上一个状态

---

## 二、方案 A：官方超级岛回归

### 设计

```
islandMode = NONE       → 不显示岛
islandMode = MODULE     → 模块建的 Focus 通知（现状）
islandMode = OFFICIAL   → 优先改写系统 headset_wear_notification；
                          失败 → 降级到模块 Focus 通知（并 Toast 提示一次）
```

### 步骤

1. **恢复选项**：`SettingsPage` 的 `islandModeValues/Options` 加回 OFFICIAL（文案已有），默认值由 `ConfigManager` 决定（建议继续默认 MODULE，避免升级后行为突变）。
2. **接上触发路径**（`MiBluetoothToastHook`）：
   - `updatepodsnotification`：按 `islandMode()` 分支；OFFICIAL 时调用已有 `patchHeadsetWearIslandBundle` 路径（通过 `BluetoothUpstreamHeadsetHook` 的静态入口或直接 hook 的数据流）
   - `sendstrongtoast`：MODULE 走 `FocusIslandUtil`；OFFICIAL 走"改写官方通知"，拿不到官方通知时降级 `FocusIslandUtil`
   - `cancelpodsnotification`：MODULE 取消模块通知；OFFICIAL 不主动杀系统通知（交给系统），只清理标记
3. **降级与幂等**：`patchHeadsetWearIslandBundle` 返回布尔；false 时设 `officialIslandUnavailable = true`，本次连接内不再尝试，并 Toast「官方超级岛不可用，已切换为模块岛」。
4. **焦点通知开关联动**：`focusNotification=false` 时两种岛都不显示（统一守卫）。
5. **验收**：三种模式切换即时生效；官方模式在真机上显示电量；不可用时自动降级且仅提示一次。

### 风险

- 官方岛依赖 `headset_wear_notification` 的 bundle 结构（`param`/`island_param` JSON）与 HyperOS 版本强相关；不同版本字段可能变。
- 上游 2.x 还有一条"自建 strong toast + 系统 mp4 动画"的实现（`showOfficialConnectToast`），依赖 `com.xiaomi.bluetooth` 的 raw 资源；本分支没有这些 mp4，**不采用**，只做"改写系统通知 + 模块岛兜底"。
- 必须保留一键回退：设置里选"模块"即可。

---

## 三、方案 B：音量增强 / Hi-Res 回归

### 步骤

1. `PodDetailPage` 补两个控件（放在游戏模式同一张卡）：
   ```kotlin
   SwitchPreference(title = R.string.volume_boost, summary = R.string.volume_boost_summary,
       checked = volumeBoost, onCheckedChange = onVolumeBoostChange)
   SwitchPreference(title = R.string.hi_res_mode, summary = R.string.hi_res_mode_summary,
       checked = hiResMode, onCheckedChange = onHiResModeChange)
   ```
2. **能力门控**（新增，复用 `DeviceModelRegistry` 功能位）：
   - `DeviceCapabilities` 增加 `hiResSupported`（feature 109）、`volumeBoostSupported`（feature 113）
   - 不支持时控件置灰并显示"当前机型不支持"，仍保留设置里的强制开关
   - 已知差异：`XT2441-1`（Buds+）**没有**音量增强，`XT2443-1` 两个都有
3. **Hi-Res 特殊处理**：
   - 开启会主动断连重协商 LDAC → 开启后 3 秒内暂停其他命令，断线重连后走方案 C 的重新同步
   - 与游戏模式互斥逻辑已实现，UI 上加一行提示
4. **验收**：两个开关出现且可切换；切换后 0x315/0x311 通知能回写 UI；不支持机型显示灰态。

---

## 四、方案 C：连接后状态检测（核心）

### 数据模型

```kotlin
enum class FeatureState { UNKNOWN, SYNCING, ON, OFF, UNSUPPORTED }

data class FeatureSyncEntry(
    val feature: Feature,
    val query: ByteArray,
    val expectedOpcodes: Set<Int>,
    val timeoutMs: Long = 1500,
)
```

### 查询矩阵（连接后串行发送，间隔 50ms）

| 功能 | 查询 | 响应 | 备注 |
|---|---|---|---|
| 型号/能力 | 0x000 / 0x001 | 0x000 / 0x001 | 决定哪些控件可见 |
| 电量 | 0x005 | 0x005 / 0x009 | |
| ANC | 0x200 | 0x200 / 0x204 | |
| 游戏模式 | 0x30E | 0x30E / 0x312 | 通知是 0x19/0x1A |
| 音量增强 | 0x313 | 0x313 / 0x315 | 通知是 0x1D/0x1E |
| **Hi-Res（新增）** | **0x30C** | 0x30C / 0x311 | 响应 2 字节 [支持,状态] |
| 空间音频 | 0x30A | 0x30A / 0x310 | 有无 feature 106 |
| EQ 预设 | 0x302 | 0x302 / 0x308 | |
| 双设备连接 | 0x406 | 0x406 / 0x40F | |
| 入耳检测 | 0x402 | 0x402 / 0x404 | |
| 在盒/佩戴 | 0x40C | 0x40C | |
| LE Audio | 0x410 | 0x410 | 另由 LeAudioConnectionHook 判 ACL/CIS |

### 流程

```
连接成功 (STATE_CONNECTED)
   └─ delay(300ms)
      └─ for each entry (50ms 间隔):
           send(query); pending[opcode] = now + timeout
      └─ 每个响应到达 → pending.remove(opcode) → changeUI*(...)
      └─ watchdog: 超时项重试 1 次（+300ms），仍失败 → 标记 UNSUPPORTED/UNKNOWN
      └─ 全部结束（最多 ~4s）→ 广播 ACTION_PODS_STATE_SYNCED
                            {missing=[...], model=..., features=[...]}
```

### 多进程一致性

- `ACTION_PODS_STATE_SYNCED` 广播给 `com.milink.service` / `com.xiaomi.bluetooth` / `com.android.settings`
- `MiLinkServiceHook` / `BluetoothUpstreamHeadsetHook` 收到后：**丢弃跨进程 prefs 里的旧状态**，重新请求 `ACTION_REFRESH_STATUS`，等真实数据再渲染
- 各 hook 的 `currentBattery/currentAnc` 缓存加"是否为本连接世代"标记（epoch 号），避免旧值穿透

### UI

- 详情页顶部：同步中显示细条 "正在检测耳机状态…"；完成后隐藏
- 未响应的功能：控件灰显 + "未检测到"
- 设置页加"重新检测"按钮（发 `ACTION_REFRESH_STATUS`）

### 验收

- 连接后 ≤2s 内三个开关状态与耳机一致（以日志 opcode 回包为准）
- 断连重连后自动重新检测，不残留上一轮状态
- 某功能不回包时，UI 明确显示"未检测到"，不假装可用

---

## 五、实施顺序与工作量

| 阶段 | 内容 | 文件数 | 预估 |
|---|---|---|---|
| C | 状态检测框架 + 补齐查询 + 多进程刷新 | 5-6 | ~1 天 |
| B | 两个开关 + 能力门控 + Hi-Res 断连处理 | 3-4 | 2-3 小时 |
| A | 官方岛选项 + 触发路径 + 降级 | 4-5 | 半天（真机调试另算） |

先做 C 的原因：B 的开关状态、A 的电量显示都依赖"连接后状态可得且可信"；否则只是把按钮画出来，状态还是旧的。

## 六、风险与回滚

| 风险 | 应对 |
|---|---|
| 官方岛私有接口随 HyperOS 版本变化 | 自动降级模块岛 + 一次性提示；保留"模块"选项 |
| Hi-Res 切换断连导致命令时序错乱 | 切换后 3s 命令静默 + 重连后强制重新同步 |
| 状态查询串行导致连接初期拥塞 | 50ms 间隔 + 1.5s 超时 + 单次重试，总时长上限 4s |
| 旧缓存穿透 | 引入连接世代 epoch，广播里带 epoch，旧 epoch 数据丢弃 |

## 七、需要你确认的点

1. 官方岛默认值：继续默认 **模块**（保守）还是默认 **官方**？
2. Hi-Res/音量增强：不支持机型是否允许"强制打开"（现设置页有 capability override 体系，可复用）？
3. 状态检测失败时：UI 显示"未检测到"即可，还是要弹 Toast 提示？
