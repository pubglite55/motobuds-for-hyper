
<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="MotoBuds Icon"/>

# MotoBuds

**为 HyperOS 设备提供系统级 Moto Buds 耳机控制**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-orange?style=flat-square)](https://hyperos.mi.com)

**简体中文**

</div>

为小米 HyperOS 设备提供系统级 Moto Buds 耳机控制的 Xposed 模块。


### 耳机功能

- **降噪控制** — 在关闭 / 降噪 / 自适应 / 通透模式之间切换
- **低延迟模式** — 低延迟音频开关，支持连接时自动开启
- **电量显示** — 实时显示左耳、右耳、充电盒电量

### 澎湃集成
- **超级岛** — 支持官方超级岛或模块内建超级岛
- **融合设备中心** — 支持融合设备中心控制
- **设置集成** — 支持系统蓝牙设置控制
- **设备流转** — 支持融合设备中心内多设备一键流转
- **型号伪装** — 伪装受支持的小米耳机

### 模块功能
- **快捷弹窗** — 点击通知或控制中心耳机卡片，弹出浮窗显示电量、降噪、游戏模式控制；点击「更多」进入完整页面
- **快捷跳转** — 通知或控制中心耳机卡片，支持快速跳转MotoBuds官方app/模块设置/系统设置

### 系统要求

- 小米设备，运行 **HyperOS**（Android 15+）(超级岛仅支持OS3)
- **LSPosed** API版本>=101

### 支持设备

- Moto Buds (guitar/XT2443-1)

### 使用

1. 安装 APK
2. 在 LSPosed 中启用模块并勾选推荐作用域
3. 软件右上角一键重启作用域
4. 通过蓝牙连接你的 Moto Buds 耳机

### 致谢

- [OPPOPods](https://github.com/1812z/OppoPods) by 1812z — 原始项目
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — 原始项目
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 风格 Compose UI 组件

### 许可证

GPL-3.0
