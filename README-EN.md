<div align="center">

<img src="docs/icon.jpg" width="140" height="140" style="border-radius: 28px; box-shadow: 0 8px 32px rgba(0,0,0,0.3);" alt="MotoBuds"/>

# 🎧 MotoBuds for Hyper

### Bring Motorola into HyperOS

[![Platform](https://img.shields.io/badge/Platform-Android%2015+-green?style=for-the-badge&logo=android)](https://android.com)
[![Framework](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=for-the-badge&logo=github)](https://github.com/LSPosed/LSPosed)
[![ROM](https://img.shields.io/badge/ROM-HyperOS%203-orange?style=for-the-badge&logo=xiaomi)](https://hyperos.mi.com)
[![License](https://img.shields.io/badge/License-GPL--3.0-red?style=for-the-badge)](LICENSE)

<br/>

[English](README-EN.md) | [简体中文](README.md) | [日本語](README-jp.md)

*An Xposed module that lets your Moto Buds feel at home in the Xiaomi ecosystem*

</div>

---

## ✨ Why MotoBuds for Hyper?

You spent good money on Moto Buds, only to find they're treated like a "foreigner" on HyperOS — no Super Island battery display, no Fusion Device Center control, and a blank notification bar.

**MotoBuds for Hyper** bridges this gap, giving your Moto Buds the full Xiaomi ecosystem experience.

---

## 🎯 Core Features

<table>
<tr>
<td width="50%">

### 🔇 ANC Control
One-tap switching between **Off** / **Noise Cancellation** / **Adaptive** / **Transparency**

### 🎮 Game Mode
Low-latency audio mode with **auto-enable on connect**

### 🔋 Battery Display
Real-time left earbud, right earbud, and charging case battery

</td>
<td width="50%">

### 🎛️ EQ Presets
5 preset modes: Authentic, Bright Treble, Bass Enhancement, Vocal Enhancement, Manual Tuning

### 🏝️ Super Island
Supports HyperOS 3 official Super Island or module built-in island

### 📱 Fusion Device Center
Direct earphone control in system settings with **multi-device one-tap handoff**

</td>
</tr>
</table>

---

## 🚀 Quick Start

```
1️⃣  Install the APK
2️⃣  Enable module in LSPosed → Select recommended scopes
3️⃣  Restart scope (or reboot phone)
4️⃣  Connect your Moto Buds via Bluetooth — You're all set!
```

<details>
<summary>📱 Recommended Scopes</summary>

- `com.android.bluetooth`
- `com.milink.service`
- `com.xiaomi.bluetooth`

</details>

---

## 🛠️ Technical Architecture

```
┌─────────────────────────────────────────────┐
│              MotoBuds for Hyper              │
├─────────────┬───────────────┬───────────────┤
│  RFCOMM SPP │  Xposed Hook  │  Compose UI   │
│ (Bluetooth) │  (System)     │  (Interface)  │
├─────────────┼───────────────┼───────────────┤
│  UUID:       │  HookEntry:   │  Miuix:       │
│  fc9d9fe0-   │  com.android  │  HyperOS-style│
│  4899-11ee   │  .bluetooth   │  Compose UI   │
│  -be56-...   │  com.xiaomi   │               │
│              │  .bluetooth   │               │
└─────────────┴───────────────┴───────────────┘
```

---

## 📋 Supported Devices

| Device | Model | Status |
|--------|-------|:------:|
| Moto Buds | XT2443-1 (guitar) | ✅ |

---

## 📦 Supported Features

| Feature | Description | Status |
|---------|-------------|:------:|
| 🔇 ANC Control | Off/NC/Adaptive/Transparency | ✅ |
| 🎮 Game Mode | Low-latency audio | ✅ |
| 🔋 Battery Display | Left/Right/Case | ✅ |
| 🎛️ EQ Presets | 5 preset modes | ✅ |
| 🏝️ Super Island | System-level battery island | ✅ |
| 📱 Fusion Device Center | System settings integration | ✅ |
| 🔄 Device Handoff | Multi-device one-tap switch | ✅ |

> **💡 Tip:** When switching ANC mode, if it reverts to Adaptive mode, please create a personal adaptation in the official `com.motorola.motobuds` app first.

---

## 📸 Screenshots

<table>
<tr>
<td align="center"><img src="docs/微信图片_20260625080850_72_9.jpg" width="270" alt="Earphones Page"/></td>
<td align="center"><img src="docs/微信图片_20260625083102_77_9.jpg" width="270" alt="Module Page"/></td>
</tr>
<tr>
<td align="center">Earphones Page</td>
<td align="center">Module Page</td>
</tr>
<tr>
<td align="center"><img src="docs/微信图片_20260625080853_74_9.jpg" width="270" alt="milink Emulated Device"/></td>
<td align="center"><img src="docs/微信图片_20260625080855_75_9.jpg" width="270" alt="Quick Popup"/></td>
</tr>
<tr>
<td align="center">milink Emulated Device</td>
<td align="center">Quick Popup</td>
</tr>
</table>

---

## 🤝 Credits

This project stands on the shoulders of giants:

| Project | Author | Contribution |
|---------|--------|--------------|
| [OPPOPods](https://github.com/1812z/OppoPods) | 1812z | Original framework |
| [HyperPods](https://github.com/Art-Chen/HyperPods) | Art_Chen | Original project |
| [Miuix](https://github.com/YuKongA/miuix) | YuKongA | HyperOS UI components |

---

## 📜 License

```
GPL-3.0 License

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

---

<div align="center">

**If this project helps you, please give it a ⭐ Star!**

<br/>

<img src="docs/icon.jpg" width="60" height="60" style="border-radius: 12px;" alt="MotoBuds"/>

*Made with ❤️ for the Moto Buds community*

</div>
