# 设备型号检测与能力判定

固件在 `0x0000`（Profile Version / Firmware Info）响应里带回 ASCII 型号（`XT####-#`）。
`RfcommController.parseProfileVersion()` 用 `XT[0-9]{4}-[0-9]` 提取，再查
`pods/DeviceModelRegistry.kt` 的功能表（移植自 Motorola 的 `buds_features.json`，
参考实现：ThisWasAryan/MotoBudsDesktopUtility，GPL-3.0）。

## 功能表

| 型号 | 代号 | 市场名 | 关键能力 |
|---|---|---|---|
| XT2443-1 | guitar | Moto Buds | ANC(104)、自适应(105)、Hi-Res(109)、游戏模式(110)、低音增强(112)、音量增强(113)、入耳(116)、贴合(117)、双连(120)、响铃(121) |
| XT2443-1 | guitar25 | Moto Buds Bass | 同上 |
| XT2441-1 | flute | Moto Buds + | 多 **空间音频(106)**，无音量增强(113) |
| XT2643-1 | uke | Moto Buds 2 | 类似 guitar |
| XT2641-1 | bass | Moto Buds 2 Plus | 多 106 / 103 / 118 |
| XT2545-1 | oboe | moto buds loop | OWS，无 ANC |

## 判定优先级

1. `model_id` 功能表（0x0000 响应解析成功时）
2. 设备名启发式（未拿到型号时兜底）
3. 用户在「设置 → 设备能力」里的强制开关

检测到的型号 + 功能列表通过 `ACTION_PODS_DEVICE_INFO_CHANGED` 广播到 UI，
显示在 **设置 → 设备能力** 页首卡。

## Hi-Res 与游戏模式互斥

硬件上 LDAC（Hi-Res）与游戏模式不能同时开：
- 开游戏模式时若 Hi-Res 开着 → 先发 `0x030D [0x00]`，再发游戏模式；
- 开 Hi-Res 时若游戏模式开着 → 先关游戏模式，再发 `0x030D [0x01]`；
- 两侧都会同步 UI 状态广播。

## 已移除的功能

按需求移除：自定义 10 段 EQ（0x0305/0x0306）、贴合测试（0x0400/0x0401）、
查找耳机（0x0405/0x040E）。协议常量仍保留在 `Cmd` 里，方便以后重新接入。
