# Telegram 发布通知

GitHub 主发布仓库通过 `Telegram Release` 工作流，在 Release 的 `published` 事件后发送一条包含 APK 和格式化更新日志的消息。正式版和 RC 预发布分别触发；普通提交、标签推送、草稿及 Release 编辑不会触发。LSPosed 仓库不重复发送。

## 配置

在仓库 Settings → Secrets and variables → Actions 配置：

- `TELEGRAM_BOT_TOKEN`：机器人密钥，仅存入加密 Secret。
- `TELEGRAM_CHAT_ID`：目标频道的 `@用户名` 或数字 ID。

机器人必须是目标频道管理员，并具有发布消息权限。不要把密钥写入 URL 示例、代码、Release 正文、日志或 Issue；密钥暴露后应立即在 BotFather 撤销并更新 Secret。

## 发布流程

1. 先创建草稿，上传 APK 和 `SHA256SUMS.txt` 或对应 `.sha256` 文件。
2. 确认公开标签对应的纯发布资料提交包含 `.github/workflows/telegram-release.yml`。
3. 最后发布 Release；工作流会确认附件上传完成、下载并校验 APK 的 SHA-256，然后检查机器人及频道权限。
4. 校验通过后，通过 `sendRichMessage` 在一条消息中发送 APK、完整 Release 正文和 GitHub 链接。正文采用 Rich Markdown，由 Telegram 渲染标题、加粗、列表等格式，不以纯文本展示 Markdown 源码。校验文件只用于内部核验，不发送到频道。

每个 Release 必须恰好包含一个 APK；正式版和 RC 各发一条。日志不得嵌入其他媒体；超长日志、多 APK 或接口拒绝会报错，不截断日志、不拆消息、不自动降级为旧发送方式。频道最终显示仍需实际验收。

工作流只读 GitHub 内容，不构建应用、不上传源码、不修改 Release。单个附件上限为 49,000,000 字节；超限时会停止，不会截断或跳过文件后假报成功。

## 试运行与补发

在 Actions → Telegram Release → Run workflow 中指定已发布的 `release_tag`。

- `dry_run=true`：默认选项，只下载核验附件和检查 Telegram 权限，不发布任何频道消息或文件。
- `dry_run=false`：重新发送该 Release 的单条 APK 与日志消息，可能与此前已送达的内容重复。

同一 Release 的运行不会并发发送。网络超时后 Telegram 是否收到消息可能不确定，因此不自动重试发送；先检查频道，再决定是否补发。手动触发入口依赖默认分支包含工作流。

后续版本的公开标签也必须包含本工作流；只更新旧 Release 的正文或附件不会自动再通知。
