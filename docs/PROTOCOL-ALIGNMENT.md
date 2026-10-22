# 协议对齐记录（参考 MotoBudsDesktopUtility）

参考项目：[ThisWasAryan/MotoBudsDesktopUtility](https://github.com/ThisWasAryan/MotoBudsDesktopUtility)（GPL-3.0，TypeScript + Python，反编译 Motorola 官方 App 得到，标注 CONFIRMED / 真机验证）。
本地参考副本：`C:\Users\L1872\Desktop\motobuds-ref`；关键文件 `PROTOCOL.md`、`backend/moto_control.py`、`src/store/useDeviceStore.ts`。

## 1. 已确认一致的部分

| 项 | 本项目 | 参考实现 |
|---|---|---|
| 帧头/帧尾 | `HEAD`(0x48 45 41 44) / `TAIL`(0x54 41 49 4C) | 相同 |
| 总长 | LE，= 8 + payload | 相同 |
| opcode | 2B **大端** | 相同 |
| payload 长度 / seq | LE，offset 10 / 12 | 相同 |
| CRC32 | 覆盖 HEAD..payload，LE 存放，多项式 0xEDB88320 | 相同 |
| RFCOMM UUID | `fc9d9fe0-4899-11ee-be56-0242ac120002`（端口 16） | 相同 |
| 电量 | `低7位=百分比，bit7=充电中` | 相同（并说明 bit7 同时代表"在盒") |
| ANC 写 | 0x0201 载荷 `[mode, sub_mode]`，0=关/1=降噪/2=通透 | 相同 |

## 2. 本次按参考修正的点（提交 `62b8a7c`）

| 位置 | 原实现 | 参考协议 | 处理 |
|---|---|---|---|
| 游戏模式通知 0x0312 | `payload[0] == 0x01` 才算开 | 通知用 **0x19=开 / 0x1A=关**，查询响应用 0x01/0x00 | 两者都接受 |
| 音量增强 0x0315 | `payload[0] == 0x01` | 通知用 **0x1D=开 / 0x1E=关** | 两者都接受 |
| Hi-Res 读 0x030C | 取 `payload[0]` | 响应 2 字节 `[支持标志, 状态标志]` | `size>=2` 时取 `payload[1]` |
| 在盒状态 0x040C | 只看 `payload[0]` | 2 字节 `[左, 右]` | 解析两路后取或 |
| BLE GATT | 服务 `fc9d9fe0`，写 `fc9d9ff0`、通知 `fc9d9ff1`，且要求完整 HEAD 帧 | 写 **`fc9d0002`**、通知 **`fc9d0003`**；BLE 帧**去掉** HEAD/总长/CRC/TAIL，只发 8 字节核心头 + payload | 优先新 UUID，保留旧 UUID 兜底；再按 `fc9d` 前缀 + write/notify 属性自动发现；收发两个方向都做 SPP↔GATT 帧转换 |
| 空间音频能力 | `SPATIAL_AUDIO_SUPPORTED_DEVICES = ["OPPO Enco X3"]`（历史遗留） | 仅 **Moto Buds+（XT2441-1 / flute）** 有空间音频 | 改为 `flute` / `XT2441`，其余仍可用设置里的"强制开启" |
| 缺失命令 | 无 | 自定义 EQ 0x0305/0x0306（173 字节）、Fit Test 0x0400、FMD 0x0405、手势 0x0102 | 新增 `MotoBudsCustomEq`（构建/解析）与 `MotoBudsCommands`（fit/FMD/手势/EQ 构建器） |

## 3. 参考实现里值得注意、但本次未改的

1. **ANC 语义**：参考项目认为 `mode=1` 的 `sub_mode=1/2/3` 是"降噪强度档位"，`mode=2 sub=4` 是"通透-人声增强"，并且明确**没有自适应 ANC**（他们专门移除了未实现的 Adaptive 菜单项）。本项目的 UI 仍有"自适应"（`sub_mode=1` 映射为 ADAPTIVE）。两种解释都可能是对的（取决于固件/官方 App 版本），需要真机抓包确认后再动，避免把现有用户用惯的档位改坏。
2. **能力发现用 `model_id` + `buds_features.json`**：参考项目从 0x0000（固件/型号信息）里正则出 `XT####-#`，再用内置 feature 表决定 UI；本项目目前按设备名判断。后续可接：0x0000 响应 → 型号 → 能力位。
3. **Hi-Res 与游戏模式互斥**：两者不能同时开，切换时需要关掉另一个；本项目 UI 目前没做联动。
4. **Hi-Res(LDAC) 会主动断连重协商**，参考实现要等 4 秒再重连（甚至重新 power cycle 适配器）；本项目的 120 秒重连策略能覆盖，但可在开启 Hi-Res 后主动触发一次快速重连。
5. **自定义 EQ 173 字节布局**已按参考实现；UI（10 段滑杆）还没做。
6. **手势配置（0x0100/0x0102/0x0105）**：参考实现解析 16 字节成左右各 4 组映射；本项目目前只打日志。
7. **Fit Test / Find My Device**：命令构建器已加，UI 未接。
8. 参考项目的 BLE 章节只给了特征 UUID，没给服务 UUID；本次用"优先已知 + 按属性扫描"规避了这个不确定点。

## 4. 验证

```text
BUILD SUCCESSFUL in 15s (assembleDebug, compileSdk 37 / JDK 22)
```
改动仅涉及协议层（`pods/`）与 xposed 入口无关，hyperos4 分支仍然可编译。
