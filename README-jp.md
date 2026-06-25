<div align="center">

<img src="docs/icon.jpg" width="140" height="140" style="border-radius: 28px; box-shadow: 0 8px 32px rgba(0,0,0,0.3);" alt="MotoBuds"/>

# 🎧 MotoBuds for Hyper

### Motorola を HyperOS に導入

[![Platform](https://img.shields.io/badge/Platform-Android%2015+-green?style=for-the-badge&logo=android)](https://android.com)
[![Framework](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=for-the-badge&logo=github)](https://github.com/LSPosed/LSPosed)
[![ROM](https://img.shields.io/badge/ROM-HyperOS%203-orange?style=for-the-badge&logo=xiaomi)](https://hyperos.mi.com)
[![License](https://img.shields.io/badge/License-GPL--3.0-red?style=for-the-badge)](LICENSE)

<br/>

[English](README-EN.md) | [简体中文](README.md) | **日本語**

*Moto Buds を Xiaomi エコシステムで快適に使うための Xposed モジュール*

</div>

---

## ✨ なぜ MotoBuds for Hyper が必要？

Moto Buds に高いお金を出して買ったのに、HyperOS 上では「外国人」扱い——スーパーアイランドのバッテリー表示もなく、フュージョンデバイスセンターの制御もなく、通知バーも空っぽ。

**MotoBuds for Hyper** はこのギャップを埋め、Moto Buds に Xiaomi エコシステムの全機能を提供します。

---

## 🎯 主な機能

<table>
<tr>
<td width="50%">

### 🔇 ノイズキャンセリング制御
**オフ** / **ノイズキャンセリング** / **アダプティブ** / **トランスペアレンシー** モードをワンタップで切り替え

### 🎮 ゲームモード
低レイテンシーオーディオモード、**接続時に自動有効化**対応

### 🔋 バッテリー表示
左耳、右耳、充電ケースのバッテリーをリアルタイム表示

</td>
<td width="50%">

### 🎛️ EQプリセット
5つのプリセット：オーソン、ブライトトレブル、ベースブースト、ボーカルブースト、マニュアルチューニング

### 🏝️ スーパーアイランド
HyperOS 3 公式スーパーアイランドまたはモジュール内蔵アイランド対応

### 📱 フュージョンデバイスセンター
システム設定で直接ヘッドセットを制御、**マルチデバイスワンタップハンドオフ**対応

</td>
</tr>
</table>

---

## 🚀 クイックスタート

```
1️⃣  APK をインストール
2️⃣  LSPosed でモジュールを有効化 → 推奨スコープを選択
3️⃣  スコープを再起動（またはスマホを再起動）
4️⃣  Bluetooth で Moto Buds を接続 — 準備完了！
```

<details>
<summary>📱 推奨スコープ</summary>

- `com.android.bluetooth`
- `com.milink.service`
- `com.xiaomi.bluetooth`

</details>

---

## 🛠️ 技術アーキテクチャ

```
┌─────────────────────────────────────────────┐
│              MotoBuds for Hyper              │
├─────────────┬───────────────┬───────────────┤
│  RFCOMM SPP │  Xposed Hook  │  Compose UI   │
│ (Bluetooth) │  (システム)    │  (インターフェース) │
├─────────────┼───────────────┼───────────────┤
│  UUID:       │  HookEntry:   │  Miuix:       │
│  fc9d9fe0-   │  com.android  │  HyperOS風    │
│  4899-11ee   │  .bluetooth   │  Compose UI   │
│  -be56-...   │  com.xiaomi   │               │
│              │  .bluetooth   │               │
└─────────────┴───────────────┴───────────────┘
```

---

## 📋 対応デバイス

| デバイス | 型番 | ステータス |
|----------|------|:----------:|
| Moto Buds | XT2443-1 (guitar) | ✅ |

---

## 📦 対応機能

| 機能 | 説明 | ステータス |
|------|------|:----------:|
| 🔇 NC制御 | オフ/NC/アダプティブ/トランスペアレンシー | ✅ |
| 🎮 ゲームモード | 低レイテンシーオーディオ | ✅ |
| 🔋 バッテリー表示 | 左/右/ケース | ✅ |
| 🎛️ EQプリセット | 5つのプリセット | ✅ |
| 🏝️ スーパーアイランド | システムレベルバッテリーアイランド | ✅ |
| 📱 フュージョンデバイスセンター | システム設定統合 | ✅ |
| 🔄 デバイスハンドオフ | マルチデバイスワンタップ切替 | ✅ |

> **💡 ヒント：** NCモードを切り替えたときにアダプティブモードに戻る場合は、まず公式の `com.motorola.motobuds` アプリで個人アダプテーションを作成してください。

---

## 📸 スクリーンショット

<table>
<tr>
<td align="center"><img src="docs/微信图片_20260625080850_72_9.jpg" width="270" alt="ヘッドセットページ"/></td>
<td align="center"><img src="docs/微信图片_20260625080851_73_9.jpg" width="270" alt="モジュールページ"/></td>
</tr>
<tr>
<td align="center">ヘッドセットページ</td>
<td align="center">モジュールページ</td>
</tr>
<tr>
<td align="center"><img src="docs/微信图片_20260625080853_74_9.jpg" width="270" alt="milinkエミュレートデバイス"/></td>
<td align="center"><img src="docs/微信图片_20260625080855_75_9.jpg" width="270" alt="クイックポップアップ"/></td>
</tr>
<tr>
<td align="center">milink エミュレートデバイス</td>
<td align="center">クイックポップアップ</td>
</tr>
</table>

---

## 🤝 クレジット

このプロジェクトは巨人の肩の上に立っています：

| プロジェクト | 著者 | 貢献 |
|-------------|------|------|
| [OPPOPods](https://github.com/1812z/OppoPods) | 1812z | オリジナルフレームワーク |
| [HyperPods](https://github.com/Art-Chen/HyperPods) | Art_Chen | オリジナルプロジェクト |
| [Miuix](https://github.com/YuKongA/miuix) | YuKongA | HyperOS UIコンポーネント |

---

## 📜 ライセンス

```
GPL-3.0 ライセンス

このプログラムはフリーソフトウェアです。Free Software Foundation が発行する
GNU General Public License のバージョン 3 または（あなたの選択する）それ以降の
いずれかのバージョンの下で再配布および/または変更できます。
```

---

<div align="center">

**このプロジェクトが役に立った場合は、⭐ Star で応援してください！**

<br/>

<img src="docs/icon.jpg" width="60" height="60" style="border-radius: 12px;" alt="MotoBuds"/>

*Moto Buds コミュニティのために ❤️ を込めて*

</div>
