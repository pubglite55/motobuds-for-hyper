# 发布记录 v1.3.2

发布日期：2026-10-23
Release：https://github.com/pubglite55/motobuds-for-hyper/releases/tag/v1.3.2

## 产物

| 文件 | 大小 | 说明 |
|---|---|---|
| `release.apk` | 4.6 MB | 正式签名（CN=MotoBuds for Hyper），R8 混淆 + 资源压缩 |
| `debug.apk` | 41.9 MB | 调试版，含完整符号便于排查 |

- versionCode **5** / versionName **1.3.2**
- 签名证书 SHA-256：`97fc3de0cc0b88ffbf516eb98c5423957cbe39bc23fdd53aebfc2e18329a5f04`
- **注意**：与 v1.3.1 的密钥不同（旧版用的是 debug 签名）。从旧版升级需要先卸载，模块配置会丢失。

## 分支

- `main` 已快进合并 `hyperos4`（18 个提交），标签 `v1.3.2` 指向 `main`
- `hyperos4` 分支保留，可继续开发

## 构建命令（本机复现）

```powershell
$env:JAVA_HOME="C:\Users\L1872\tools\jdk22"; $env:ANDROID_HOME="C:\Android"
$env:JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.nonProxyHosts=localhost|127.0.0.1"
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug assembleRelease --console=plain -PKEYSTORE_FILE="motobuds-release.jks" -PKEYSTORE_PASSWORD="motobuds2026" -PKEY_ALIAS="motobuds" -PKEY_PASSWORD="motobuds2026"
```

> 注意：`lsplugin.apksign` 读的是 Gradle 属性（`-P`），不是环境变量；用环境变量会被静默回退到 debug 签名。

## 密钥与 Secrets

本地 keystore：`motobuds-release.jks`（仓库内，已被 `.gitignore` 的 `*.jks` 排除）
密码 / alias：`motobuds2026` / `motobuds`

GitHub Actions secrets 已配置：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`

## CI 修复

`.github/workflows/build.yml`：
1. 构建 JDK 从 17 改为 **22**（项目 toolchain 要求）
2. `secrets.*` 不能用在 step 级 `if`，改为通过 job `env` 注入后再判断
3. 签名改为 `-PKEYSTORE_FILE=...` 传 Gradle 属性（原先只设环境变量会回退 debug 签名）

> CI 的 release 任务在修复 secrets 前失败过（keystore 解得为空）。本版产物是本机用同一份 keystore 签名后上传的，已校验签名与版本号。