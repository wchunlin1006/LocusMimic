# 公开发布说明

本文用于将 LocusMimic 的二进制版本发布到 GitHub，并同步至 LSPosed 模块仓库。公开发布仓库为 <https://github.com/wchunlin1006/LocusMimic>，包名为 `com.locusmimic.app`。

自 `1.2.1` 起，所有后续版本只发布签名 APK、校验和、版本说明与必要文档，不再提交或推送 Android、Web、服务端及构建工程源码。

## 发布前安全检查

发布前确认下列文件和信息没有进入 Git 暂存区：

- `local.properties`（包含 `BAIDU_WEB_AK`）
- `keystore.properties`、`keystore/`、`*.jks`、`*.keystore`、`*.p12`
- 密码、Token、私钥、账号、服务器地址及其他个人资料
- 构建产物目录 `app/build/`、`.gradle/`、`.kotlin/`
- Android、Web、服务端源码及构建工程文件

可在 PowerShell 中检查：

```powershell
git status --short
git check-ignore local.properties keystore.properties
git diff --cached --check
```

`local.properties` 与 `keystore.properties` 应显示为已忽略。若曾意外公开密钥或密码，请立即在对应服务端轮换，而非仅从仓库历史中删除。

## 发布分支

每个版本使用独立的纯发布资料分支或 worktree。分支仅允许包含 README、许可证、摘要、版本说明、校验和、海报和必要文档；APK 只作为 GitHub Release 附件上传。

禁止推送 `main` 或任何包含开发源码的分支。提交前必须使用 `git ls-tree -r --name-only HEAD` 和 `git diff --cached --name-only` 检查完整文件清单。

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

## APK Release 附件

APK 只在本地可信环境构建和签名。发布前验证包名、`versionCode`、`versionName`、签名证书和 SHA-256，并将 APK 与校验和作为同名 GitHub Release 附件上传。不得通过公开仓库恢复源码构建流程，也不得上传签名密钥或服务端配置。

## 提交 LSPosed 模块仓库

1. 确认公开 GitHub 发布资料分支包含 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`，并已有带有效 APK 附件的 GitHub Release。
2. 打开 <https://modules.lsposed.org/submission/>，提交包名 `com.locusmimic.app`。
3. 按页面提示在 `Xposed-Modules-Repo/submission` 创建提交；机器人会创建对应包名仓库并邀请维护者。
4. 在创建的模块仓库中保留同步的 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`，并按 `versionCode-versionName` 标签提交后续 Release。

提交描述建议：

```text
LocusMimic — Android Xposed/LSPosed location simulation module for lawful testing.
```

## 合规提示

公开仓库与 Release 仅应描述合法、已授权的测试、开发调试和学习研究用途。不要将项目宣传为绕过规则、虚假签到、获得不当利益或侵犯他人权益的工具。
