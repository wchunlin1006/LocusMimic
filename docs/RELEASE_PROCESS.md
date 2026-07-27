# Release Process

本流程只使用 GitHub Releases 与 LSPosed 模块仓库；项目不使用远程启停、远程强制更新或服务器控制文件。

## 1. 准备版本

1. 确定语义化版本 `major.minor.patch`，例如 `1.0.0`。
2. 计算 `versionCode = major * 10000 + minor * 100 + patch`；`1.0.0` 对应 `10000`。
3. 更新 [更新日志.md](../更新日志.md) 的版本说明。
4. 确认 `README.md`、`SUMMARY`、`SUMMARY.md`、`SOURCE_URL` 与实际能力一致。

## 2. 创建正式签名包

1. 将 `keystore.properties.example` 复制为本地 `keystore.properties`，填写自己的 JKS 路径、库密码、别名和密钥密码。
2. 将 `local.properties.example` 复制为本地 `local.properties`，填写 `BAIDU_WEB_AK`。
3. 构建并在真实设备上验证主要流程：打开地图、搜索、开始/停止模拟、所选模式和作用域。
4. 使用 `apksigner verify --verbose <APK 路径>` 验证 APK 签名。

`keystore.properties`、`local.properties` 和 JKS 都不得提交到 Git。

## 3. 创建 GitHub Release

1. 将源码和文档提交到 GitHub 默认分支。
2. 创建标签：`versionCode-versionName`，例如 `10000-1.0.0`。
3. 创建同名 GitHub Release，标题使用 `LocusMimic 1.0.0`。
4. 上传已验证的正式签名 APK，文件名保留构建输出的版本与日期。
5. 下载 Release 附件再次安装或校验签名，确认附件未损坏。

也可在创建 Release 后手动触发 GitHub Actions 工作流。工作流会从 Secrets 中读取签名与百度地图 AK，并把构建出的 APK 附加到该 Release。

## 4. 同步 LSPosed 模块仓库

1. 首次发布时到 <https://modules.lsposed.org/submission/> 提交 `com.locusmimic.app`。
2. 在模块仓库中提交与源码仓库一致的 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`。
3. 为每次版本发布创建同样的 `versionCode-versionName` 标签，并提供对应的有效 APK Release 附件。
4. 等待 LSPosed 仓库处理完成后，在模块页确认名称、摘要、链接和下载版本正确。

## 5. 发布后检查

- GitHub Release 标签、标题和 APK 版本一致。
- APK 使用正式签名，且可在测试设备安装。
- README 下载链接、`SOURCE_URL` 和 LSPosed 包名均指向 `com.locusmimic.app` / LocusMimic。
- 未公开任何密钥、账号、设备日志中的敏感字段或私人地址。
- 仅保留合法、已授权测试用途的项目描述。
