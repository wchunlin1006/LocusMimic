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

对外版本名和安装代码以最终签名 APK 中的 `versionName`、`versionCode` 为准。安装代码可独立指定，并须高于已有公开版本；不要将兼容版本代码误作安装代码，也不能只改 APK 文件名。

本批次包含正式版 `2.3.1` 和 RC 版 `2.3.1-rc.1`，安装代码与兼容版本代码均为 `20301`。公开标签分别为 `20301-2.3.1` 和 `20301-2.3.1-rc.1`。正式版可设为稳定版 Latest；RC 必须标记为预发布，不替换稳定版 Latest。

本批次附件分别为 `LocusMimic-2.3.1-20261104.apk` 和 `LocusMimic-2.3.1-rc.1-20261104.apk`，SHA-256 以本批次 `SHA256SUMS.txt` 为准。正式版资料分支为 `release/20301-2.3.1`，RC 资料分支为 `release/20301-2.3.1-rc.1`；两者复用最终提交的同一份纯资料快照。两个 APK 分别上传至对应 Release，不混用。发布时先创建包含完整附件的草稿并核验，再公开正式版和 RC 版。

本次两个 Release 正文分别使用 [RELEASE_NOTES_2.3.1.md](RELEASE_NOTES_2.3.1.md) 和 [RELEASE_NOTES_2.3.1-rc.1.md](RELEASE_NOTES_2.3.1-rc.1.md)，正文均采用用户提供的同一份原文，与仓库根目录的 [更新日志.md](../更新日志.md) 当前版本部分一致。公开发布前须将最终材料提交到纯发布资料分支，并确认公开标签指向该提交；旧标签不能代表尚未提交的新材料。

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

### Telegram 自动公告

GitHub Release 发布后，工作流只向频道发送 APK；校验和仍用于内部完整性校验，不作为 Telegram 附件发送。每个 Release 必须恰好包含一个 APK 和对应校验和。

公告采用兼容手机和 PC 的普通 `sendDocument`：APK 与 HTML 格式的更新摘要合并为一条消息。摘要从 Release 正文提取顶层条目，不展示 Markdown 源码；嵌套细节省略，超长时按分组保留完整条目，显示省略提示，确保解析后不超过 1024。始终附完整日志链接，不修改 GitHub Release 原文，不发送独立文字公告，不发送其他媒体。多 APK 或 API 拒绝均报错，人工检查后再重试；网络超时先核对频道，避免重复发送。不使用手机已实测不兼容的 `sendRichMessage`。

手动执行时 `dry_run=true` 只检查附件、校验和和频道权限，不发布消息。离线回归先安装 `markdown-it-py==3.0.0`，再执行 `python .github/tests/test_telegram_release.py`。频道内的最终样式仍需真实发送验收。补发旧版本时使用默认分支的最新工作流，不移动已公开的资料标签。

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
