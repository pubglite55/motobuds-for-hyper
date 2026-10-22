# 耳机工具箱（EQ / 贴合测试 / 查找耳机 / 机型能力）

入口：模块 App → 设置 → **耳机工具箱**（`ui/pages/PodToolsPage.kt`）。

## 1. 10 段自定义 EQ

| 项 | 值 |
|---|---|
| 读 | `0x0305`（无载荷） |
| 写 | `0x0306`，载荷固定 **173 字节** |
| 布局 | `0x3F` + 前级增益(f32 LE) + 后级增益(f32 LE) + 段数(i32 LE=10) + 10×{类型 i32=0, 增益 f32(-3..3dB), 频率 f32, Q f32=0.75} |
| 频率 | 32 / 64 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz |
| 实现 | `pods/Packets.kt` 的 `MotoBudsCustomEq`（构建 + 解析）与 `MotoBudsCommands.customEq/queryCustomEq` |

UI 用 10 个滑杆（±3 dB），"应用"下发 0x0306 并在 400ms 后读回 0x0305 确认；"从耳机读取"只读；"重置"清零并下发。

## 2. 贴合测试（Fit Test）

| 项 | 值 |
|---|---|
| 开始 | `0x0400` 载荷 `[0x01]` |
| 停止 | `0x0400` 载荷 `[0x00]` |
| 结果 | `0x0401` 载荷 `[左, 右]`，`0x01`=贴合良好，其它=建议调整 |

结果通过 `ACTION_PODS_FIT_TEST_RESULT` 广播回 UI，页面显示左右耳结论。

## 3. 查找耳机（Find My Device / Ring）

| 项 | 值 |
|---|---|
| 下发 | `0x0405` 载荷 `[左, 右]`（1=响铃，0=停止） |
| 状态 | `0x040E` 载荷 `[左, 右]` |

页面两个开关分别控制左右耳。

## 4. 机型能力（model_id + 功能表）

固件在 `0x0000`（Profile Version / Firmware Info）响应里带回 ASCII 型号（`XT####-#`）。`RfcommController.parseProfileVersion()` 用 `XT[0-9]{4}-[0-9]` 提取，并查 `pods/DeviceModelRegistry.kt` 的功能表（移植自 Motorola 的 `buds_features.json`）：

| 型号 | 代号 | 市场名 | 关键能力 |
|---|---|---|---|
| XT2443-1 | guitar | Moto Buds | ANC(104)、自适应(105)、Hi-Res(109)、游戏模式(110)、低音增强(112)、音量增强(113)、入耳(116)、贴合(117)、双连(120)、响铃(121) |
| XT2443-1 | guitar25 | Moto Buds Bass | 同上 |
| XT2441-1 | flute | Moto Buds + | 多 **空间音频(106)**，无音量增强(113) |
| XT2643-1 | uke | Moto Buds 2 | 类似 guitar |
| XT2641-1 | bass | Moto Buds 2 Plus | 多 106 + 103/118 |
| XT2545-1 | oboe | moto buds loop | OWS，无 ANC |

能力判定优先级：**model_id 功能表 > 设备名启发式 > 用户手动强制**。检测到的型号/功能列表通过 `ACTION_PODS_DEVICE_INFO_CHANGED` 广播到 UI（工具箱首卡显示）。

## 5. Hi-Res 与游戏模式互斥

硬件上 LDAC（Hi-Res）与游戏模式不能同时开。现在：
- 打开游戏模式时，若 Hi-Res 处于开启状态 → 先下发 `0x030D [0x00]` 关闭，再发游戏模式命令；
- 打开 Hi-Res 时，若游戏模式开启 → 先下发游戏模式关闭，再发 `0x030D [0x01]`；
- 两条链路都会同步 UI 状态广播（`ACTION_PODS_HI_RES_MODE_CHANGED` / `ACTION_PODS_GAME_MODE_CHANGED`）。

## 6. 验证

```text
BUILD SUCCESSFUL  (assembleDebug, JDK 22 / compileSdk 37)
```

真机待验证：
- 工具箱里的型号/功能卡是否出现（依赖 0x0000 响应解析成功）；
- EQ 调档后听感变化、切回预设是否冲突（0x0306 与 0x0302/0x0303 是两套写入，可能互相覆盖）；
- 贴合测试结果是否在 5~10 秒内返回；
- 查找耳机是否只响对应侧；
- 开 Hi-Res 后游戏模式开关是否自动回到关闭。
