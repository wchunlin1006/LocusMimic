# Telegram 发布通知

GitHub 主发布仓库通过 `Telegram Release` 工作流，在 Release 的 `published` 事件后发送更新公告、APK 和校验文件。正式版和 RC 预发布均会触发；普通提交、标签推送、草稿及 Release 编辑不会触发。LSPosed 仓库不重复发送。

## 配置

在仓库 Settings → Secrets and variables → Actions 配置：

- `TELEGRAM_BOT_TOKEN`：机器人密钥，仅存入加密 Secret。
- `TELEGRAM_CHAT_ID`：目标频道的 `@用户名` 或数字 ID。

机器人必须是目标频道管理员，并具有发布消息权限。不要把密钥写入 URL 示例、代码、Release 正文、日志或 Issue；密钥暴露后应立即在 BotFather 撤销并更新 Secret。

## 发布流程

1. 先创建草稿，上传 APK 和 `SHA256SUMS.txt` 或对应 `.sha256` 文件。
2. 确认公开标签对应的纯发布资料提交包含 `.github/workflows/telegram-release.yml`。
3. 最后发布 Release；工作流会确认附件上传完成、下载并校验 APK 的 SHA-256，然后检查机器人及频道权限。
4. 校验通过后，先发送包含完整 Release 正文和 GitHub 链接的公告，再发送 APK 和校验文件。长公告自动分段。

工作流只读 GitHub 内容，不构建应用、不上传源码、不修改 Release。单个附件上限为 49,000,000 字节；超限时会停止，不会截断或跳过文件后假报成功。

## 试运行与补发

在 Actions → Telegram Release → Run workflow 中指定已发布的 `release_tag`。

- `dry_run=true`：默认选项，只下载核验附件和检查 Telegram 权限，不发布任何频道消息或文件。
- `dry_run=false`：重新发送该 Release 的整套公告及附件，可能与此前已送达的内容重复。

同一 Release 的运行不会并发发送。网络超时后 Telegram 是否收到消息可能不确定，因此不自动重试发送；先检查频道，再决定是否补发。手动触发入口依赖默认分支包含工作流。

后续版本的公开标签也必须包含本工作流；只更新旧 Release 的正文或附件不会自动再通知。
