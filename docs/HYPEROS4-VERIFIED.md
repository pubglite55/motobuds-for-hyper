# HyperOS 4 适配与真机验证记录

设备：Xiaomi 13 Pro（`nuwa` / 2210132C）
系统：**Android 17 (API 37) / HyperOS OS4.0 / CP2A.260605.016**
框架：KernelSU + **LSPosed 2.2.1 (7912), API 102**

## 已验证通过（真机）

| 项 | 结果 |
|---|---|
| 模块加载 | `com.android.bluetooth` / `com.xiaomi.bluetooth` 进程内正常，无 hook 安装失败 |
| libxposed API 102 | hook 稳定 id 注册成功，热重载所需的 `onHotReloading` 就位 |
| RFCOMM 连接 | 连续多轮连接 `moto buds` 成功（UUID `fc9d9fe0-…`），偶发失败会自动重试 |
| 协议收发 | 电量、ANC、游戏模式、音量增强、双连、EQ、Hi-Res(0x30C) 均能读到真实值 |
| 详情页 | 电量 L/R、降噪四档、游戏模式、EQ 预设渲染正常 |
| 导航（miuix-nav 0.9.4） | 首页 → 耳机详情 → 设置 → 主题页 → 返回，全部正常 |
| 官方超级岛 | `islandMode=1`（官方）已生效并可选择 |
| 焦点通知 | 开关存在且默认开启 |

## 本轮修复的真机问题

| 问题 | 现象 | 修复 |
|---|---|---|
| **Hi-Res 状态不准** | 详情页开关恒为关，与实际不符 | 补 `QUERY_HI_RES`(0x30C) 进状态查询；连接时从 prefs 恢复；切换后持久化（原先 `saveLastState` 恒写 false） |
| **A17 电池元数据报错** | 每次电量更新抛 `NoSuchFieldException: mAdapterService` → 改成 `NoSuchMethodException: setBatteryLevel`，日志被刷爆 | 改为按"3 参数 + 名字含 BatteryLevel"匹配，按参数类型填参；首次失败后关闭该推送，不再刷日志 |
| **BLE 连不上** | 一直回退 RFCOMM | 已知问题：系统 GATT 被占用/ROM 限制，RFCOMM 是正常工作路径，暂不处理 |
| **官方岛视觉** | 需人工确认 | 留待观察通知栏 |

## 本轮功能调整

| 调整 | 说明 |
|---|---|
| 移除 LE Audio 模块 | OPPO 私有 AT 协议对 Moto Buds 无用（−1196 行） |
| 移除 Hi-Res / 音量增强 UI | 按需求撤掉详情页开关与能力强制项；协议常量保留在 `Cmd` 中便于重接 |
| 官方超级岛设为默认 | `islandMode` 默认 `ISLAND_MODE_OFFICIAL`，设置页恢复三选项 |
| 导航迁移到 miuix-nav | 去掉 `androidx.navigation3`，统一 Miuix 0.9.4 |

## 当前已知未做

1. **连接后状态同步框架（方案 C）**：目前查询序列是"发了就算"，没有超时/重试/完成事件；个别功能不回包时 UI 会停在旧值。
2. **设置页「耳机能力」文案**：仍显示"自适应模式：自动检测 / 空间音频：自动检测 / 空间音效：自动检测"，但能力已由 `DeviceModelRegistry` 按机型判定，文案可以更准确。
3. **BLE GATT 路径**：一直回退 RFCOMM，未深挖（不影响功能）。
4. **发布**：仍用 debug 签名；release 需要 `motobuds.jks` 与 `KEYSTORE_*` secrets。
