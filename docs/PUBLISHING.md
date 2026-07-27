# 公开发布说明

本文用于将 LocusMimic 发布到 GitHub，并提交至 LSPosed 模块仓库。当前公开源码仓库为 <https://github.com/wchunlin1006/LocusMimic>，包名为 `com.locusmimic.app`。

## 发布前安全检查

发布前确认下列文件和信息没有进入 Git 暂存区：

- `local.properties`（包含 `BAIDU_WEB_AK`）
- `keystore.properties`、`keystore/`、`*.jks`、`*.keystore`、`*.p12`
- 密码、Token、私钥、账号、服务器地址及其他个人资料
- 构建产物目录 `app/build/`、`.gradle/`、`.kotlin/`

可在 PowerShell 中检查：

```powershell
git status --short
git check-ignore local.properties keystore.properties
git diff --cached --check
```

`local.properties` 与 `keystore.properties` 应显示为已忽略。若曾意外公开密钥或密码，请立即在对应服务端轮换，而非仅从仓库历史中删除。

## 首次上传 GitHub

在项目根目录执行。以下命令只暂存、提交并推送当前项目；请先自行核对 `git status` 输出。

```powershell
git remote set-url origin https://github.com/wchunlin1006/LocusMimic.git
git branch -M main
git add .
git status
git commit -m "Prepare LocusMimic public release"
git push -u origin main
```

如果希望先保留现有 `self-development` 分支，可将最后两行改为：

```powershell
git commit -m "Prepare LocusMimic public release"
git push -u origin self-development
```

随后在 GitHub 创建 Pull Request 合并到 `main`。不要在未检查 `git status` 的情况下直接执行 `git add .`。

## 版本与 GitHub Release

版本号由 `app/build.gradle.kts` 计算：`major.minor.patch` 映射为 `major * 10000 + minor * 100 + patch`。因此 `1.0.0` 的 `versionCode` 是 `10000`。

LSPosed Release 标签固定采用：

```text
versionCode-versionName
```

例如首次公开版本：

```text
10000-1.0.0
```

建议在 GitHub Release 中使用：

```text
标题：LocusMimic 1.0.0
标签：10000-1.0.0
附件：LocusMimic-1.0.0-YYYYMMDD.apk
```

APK 必须使用你自己的正式签名。没有 `keystore.properties` 的本地 Release 会回退到 Android 调试证书，只适合安装测试，不应作为公开升级链路的正式发布包。

## GitHub Actions 自动构建

`.github/workflows/release.yml` 仅可手动触发，并向**已经创建的** GitHub Release 上传 APK。先在仓库 Settings → Secrets and variables → Actions 添加：

```text
LOCUSMIMIC_KEYSTORE_BASE64
LOCUSMIMIC_KEYSTORE_PASSWORD
LOCUSMIMIC_KEY_ALIAS
LOCUSMIMIC_KEY_PASSWORD
LOCUSMIMIC_BAIDU_WEB_AK
```

`LOCUSMIMIC_KEYSTORE_BASE64` 是 JKS 文件的 Base64 内容。PowerShell 示例：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\locusmimic-release.jks")) | Set-Clipboard
```

创建 GitHub Release 后，在 Actions 中运行 “Build and attach release APK”，输入同一标签，例如 `10000-1.0.0`。工作流会校验标签与版本号映射、临时写入签名和地图密钥配置、构建并上传 APK；这些密钥不会写回仓库。

## 提交 LSPosed 模块仓库

1. 确认公开 GitHub 仓库默认分支已包含 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`，并已有带有效 APK 附件的 GitHub Release。
2. 打开 <https://modules.lsposed.org/submission/>，提交包名 `com.locusmimic.app`。
3. 按页面提示在 `Xposed-Modules-Repo/submission` 创建提交；机器人会创建对应包名仓库并邀请维护者。
4. 在创建的模块仓库中保留同步的 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`，并按 `versionCode-versionName` 标签提交后续 Release。

提交描述建议：

```text
LocusMimic — Android Xposed/LSPosed location simulation module for lawful testing.
```

## 合规提示

公开仓库与 Release 仅应描述合法、已授权的测试、开发调试和学习研究用途。不要将项目宣传为绕过规则、虚假签到、获得不当利益或侵犯他人权益的工具。
