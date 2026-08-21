# LocusMimic 1.2.3 Root 真机测试报告

测试日期：2026-08-10 至 2026-08-11
测试状态：完整功能回归、Root 真机重启持久化验证和最终环境清理均已完成。

## 1. 测试结论

LocusMimic 1.2.3 在本机的应用级 Hook 主路径可用。WGS-84、GCJ-02、BD-09
坐标转换均实际生效；水平精度、随机半径、海拔和速度参数可传递到目标应用，
目标应用收到的 `Location.isMock()` 为 `false`。应用退到后台、返回桌面、管理器
进程被强停后，Hook 仍可继续工作。

Mock Provider 能模拟 GPS 与 Fused 位置并在停止后恢复真实位置，但 Network
Provider、扩展精度参数和服务恢复存在兼容性问题。管理器 UI、作用域更新、收藏
删除、横屏菜单和主页搜索还存在多项稳定性缺陷，因此本版本不建议在无人值守
自动化场景中直接依赖 UI 状态判断模拟是否真实运行。

## 2. 测试环境

| 项目 | 值 |
|---|---|
| 设备 | Xiaomi M2006J10C（Redmi K30 Ultra / cezanne） |
| 系统 | Android 12 / API 31 |
| ROM | V14.0.5.0.SJNCNXM |
| Root | Magisk 30.6（30600） |
| Xposed | Vector 管理环境；Zygisk-LSPosed v1.11.0（7209） |
| LocusMimic | 1.2.3，versionCode 10203，targetSdk 36 |
| 被测包 | `com.locusmimic.app` |
| QA 探针 | `qa.locationprobe`，Android Framework `LocationManager` |
| QA 探针 APK SHA-256 | `3FB46E0089A2EBD46AC830C26E203A22CB6AE301AD0250C303DDA97060A1387E` |
| 主要测试坐标 | 上海人民广场：31.230400, 121.473700 |

## 3. 最终参数基线

| 参数 | 值 |
|---|---:|
| 定位模式 | 应用级 Hook |
| 目标应用坐标系 | 自动识别（识别结果 WGS-84） |
| 水平精度 | 5 m |
| 随机半径 | 25 m |
| 垂直精度 | 8 m |
| 海拔 | 123 m |
| 速度 | 3 m/s |
| 外部广播控制 | 关闭 |
| 系统应用显示 | 关闭 |
| 地图服务 | OpenStreetMap |

## 4. 功能测试明细

### 4.1 安装、Root 与模块

| 测试项 | 结果 | 说明 |
|---|---|---|
| APK 安装与启动 | 通过 | 首次启动、免责声明和引导流程正常。 |
| Root 授权 | 通过 | `su -c id` 返回 uid 0。 |
| 模块启用 | 通过 | Vector 数据库中 `com.locusmimic.app` 为 enabled=1。 |
| QA 作用域 | 通过 | 重启前后均包含 `295 / qa.locationprobe / user 0`；测试结束时实时作用域 UI 已取消勾选。 |
| 作用域重启持久化 | 通过 | 重启前后 SQL 逻辑数据一致。 |

### 4.2 应用级 Hook

| 测试项 | 结果 | 实测 |
|---|---|---|
| WGS-84 | 通过 | 返回上海坐标附近随机点。 |
| GCJ-02 | 通过 | 返回经转换后的国测局坐标。 |
| BD-09 | 通过 | 返回经转换后的百度坐标。 |
| 自动识别 | 通过 | QA 探针识别为 WGS-84。 |
| 水平精度 | 通过 | callback 为 5.00 m。 |
| 随机半径 | 通过 | 多次 callback 在目标点约 25 m 范围内变化。 |
| 海拔 | 通过 | callback 为 123.00 m。 |
| 速度 | 通过 | callback 为 3.00 m/s。 |
| Mock 标记隐藏 | 通过 | `isMock=false`。 |
| 垂直精度 | 部分通过 | last-known 可见 8 m；实时 callback 为 `n/a`。 |
| 后台持续 | 通过 | 管理器退后台后探针持续收到模拟位置。 |
| 管理器强停 | 通过 | 应用级 Hook 不依赖管理器进程持续运行。 |
| 返回键/重新进入 | 通过 | 模拟状态和参数保持。 |

关键日志示例：

```text
provider: network
latitude: 31.230334
longitude: 121.473624
accuracy: 5.00 m
verticalAccuracy: n/a
altitude: 123.00 m
speed: 3.00 m/s
isMock: false
```

### 4.3 Mock Provider

| 测试项 | 结果 | 说明 |
|---|---|---|
| GPS Provider | 通过 | 能收到模拟坐标。 |
| Fused Provider | 通过 | 能收到模拟坐标。 |
| Network Provider | 失败 | 仍返回真实位置。 |
| 停止后恢复真实定位 | 通过 | 停止模拟后恢复真实坐标。 |
| 后台运行 | 通过 | 前台服务存活时持续输出。 |
| 管理器强停恢复 | 失败 | UI 仍显示模拟中，但服务未自动恢复。 |
| 海拔/速度/垂直精度 | 失败 | Mock Provider 未应用这些覆盖参数。 |

### 4.4 主页、地图与地点

| 测试项 | 结果 | 说明 |
|---|---|---|
| 地图点选 | 通过 | 可更新当前选中位置。 |
| 坐标输入：有效值 | 通过 | 上海坐标被正确保存并显示。 |
| 坐标输入：空值/非法值/越界值 | 通过 | 有校验提示，不写入无效状态。 |
| 我的定位 | 通过 | 能请求并更新真实位置。 |
| 清除位置 | 通过 | 位置清除后开始按钮禁用。 |
| 收藏添加 | 通过 | 可添加并显示。 |
| 收藏删除 | 失败 | 左滑后删除按钮收到触摸，但数据不变。 |
| 地点搜索 | 失败 | `Shanghai` 搜索超过 25 秒持续转圈，无结果、错误或超时。 |
| OSM 地图 | 受环境限制 | 当前网络无法加载瓦片，应用能显示错误提示。 |
| 百度/高德/Google 切换 | 通过 | 供应商切换、凭据入口与提示可用。 |
| 凭据显示/隐藏/保存/清除 | 通过 | 掩码、显示与清理流程正常。 |
| 清除地图缓存 | 通过 | 操作完成并有反馈。 |

### 4.5 作用应用与坐标系

| 测试项 | 结果 | 说明 |
|---|---|---|
| 应用搜索 | 通过 | 名称和包名搜索有效。 |
| 显示系统应用 | 通过 | 开关能改变列表范围。 |
| 取消作用域 | 通过 | QA 探针从作用域移除后 Hook 失效。 |
| 恢复作用域 | 失败 | 重新勾选后页面永久转圈；需进入 Vector 再点“应用”。 |
| 坐标系保存 | 通过 | WGS-84、GCJ-02、BD-09、自动识别均能保存。 |
| 重启目标应用 | 失败 | 多次测试探针 PID 不变。 |

### 4.6 设置、主题、语言与页面入口

| 测试项 | 结果 | 说明 |
|---|---|---|
| 参数开关和数值编辑 | 通过 | 精度、随机、海拔、速度等可保存。 |
| 中文/英文/跟随系统 | 通过 | 切换和恢复正常。 |
| 亮色/暗色/跟随系统 | 通过 | 主题切换有效。 |
| 隐藏模拟提示 | 通过 | 开关生效。 |
| 横竖屏切换 | 部分通过 | 主页面可旋转；横屏选项菜单底部被裁断且不可滚动。 |
| 赞助/关于 | 通过 | 页面可进入。 |
| GitHub/LSPosed/QQ/Telegram | 通过 | 外部链接能唤起对应处理流程。 |

### 4.7 外部广播控制

| 测试项 | 结果 | 说明 |
|---|---|---|
| 默认关闭 | 通过 | Receiver 默认不可用。 |
| 开启/关闭即时生效 | 通过 | 无需重启。 |
| START/STOP | 通过 | 在系统允许后台唤醒时可切换状态。 |
| SET_LOCATION | 通过 | 使用真实 Android 调用方发送 Double 后生效。 |
| 非法坐标与边界 | 通过 | 非法值被拒绝或钳制。 |
| 精度上限一致性 | 失败 | 广播可写 100000 m，UI 滑杆上限仅 100 m。 |
| ADB 文档示例 | 已修正 | Android 12 不支持 `am --ed`；改用 typed caller。 |
| MIUI 后台唤醒 | 受系统限制 | `WakePathChecker` 可拒绝 QA 调用方唤醒 LocusMimic。 |

## 5. 已确认缺陷

| ID | 严重度 | 缺陷 |
|---|---|---|
| LM-01 | 高 | 作用域重新勾选后永久转圈，不能自行恢复 Hook。 |
| LM-02 | 高 | Mock 模式强停管理器后 UI 与服务真实状态不一致。 |
| LM-03 | 高 | Mock Provider 的 Network Provider 未被模拟。 |
| LM-04 | 中 | Mock Provider 不应用海拔、速度和垂直精度。 |
| LM-05 | 中 | “重新启动目标应用”按钮不改变目标进程 PID。 |
| LM-06 | 中 | 收藏左滑删除按钮无效。 |
| LM-07 | 中 | 地点搜索可能永久加载，没有超时或失败反馈。 |
| LM-08 | 中 | 横屏选项菜单被裁断且不可滚动。 |
| LM-09 | 中 | 广播精度上限 100000 m 与 UI 上限 100 m 不一致。 |
| LM-10 | 低 | 应用级 Hook 实时 callback 缺少垂直精度。 |
| LM-11 | 低 | 英文 `Stop Simulation` 在圆形按钮内断词。 |
| LM-12 | 低 | 暗色模式状态栏图标对比度不足。 |
| LM-13 | 低 | OSM 在受限网络下不可用，但已有错误提示。 |
| LM-14 | 低 | Android 12 不支持原文档中的 `adb --ed` 示例。 |

## 6. 重启验证

- 重启前：模块 enabled=1，QA 探针位于作用域，应用级 Hook 输出上海模拟位置。
- 重启后：`vectord` 正常启动；Vector 数据库中的模块和作用域记录保持不变。
- 首次解锁后未打开 LocusMimic 管理器，直接冷启动 QA 探针，连续收到上海人民
  广场附近随机点；实测精度 5.00 m、海拔 123.00 m、速度 3.00 m/s，
  `isMock=false`。这证明 `is_playing`、位置、参数和作用域在重启后可自动恢复。
- 随后打开管理器，UI 正确显示“模拟中”、上海人民广场以及
  `31.230400, 121.473700`，与探针输出一致。
- 点击“停止模拟”后，探针恢复真实网络定位 `31.036044, 121.213284`，精度
  15.00 m、海拔 0、速度 0；系统最终 last location 为
  `31.036079, 121.213169`，未再返回上海模拟点。
- 完成上述持久化验证后未再次重启手机。

## 7. 最终环境清理

| 清理项 | 结果 | 终态 |
|---|---|---|
| 停止模拟 | 通过 | `xposed_shared_prefs.xml` 中 `is_playing=false`。 |
| 恢复真实定位 | 通过 | Network last location 为 `31.036079, 121.213169`。 |
| Mock AppOps | 通过 | `com.locusmimic.app` 的 `android:mock_location` 为 `deny`。 |
| 外部广播 | 通过 | `ControlReceiver` 保持在 `disabledComponents`。 |
| QA 作用域 | 通过 | 取消勾选后实时 UI 显示“已选择 0 个”。 |
| QA 探针 | 通过 | `qa.locationprobe` 已卸载，相关进程和定位监听均已结束。 |
| 临时文件 | 通过 | `/data/local/tmp` 中本轮 QA APK、数据库副本、偏好副本和 WebView 备份已删除。 |
| LocusMimic | 保留 | 1.2.3 继续安装，模块继续启用，但模拟处于停止状态。 |

Vector 数据库使用 WAL，主数据库文件在守护进程运行期间可能仍显示检查点前的
作用域行；WAL 又受 Vector SELinux 上下文保护，未为导出证据而重启守护进程。
清理状态以 Vector 实时作用域 UI、探针卸载状态和定位监听状态为准。

## 8. 证据与复现

本机证据位于 `artifacts/qa-root-1.2.3/current`，包含截图、UI hierarchy、
Logcat 和重启前后 Vector 数据库。该目录包含大量设备截图和二进制数据，默认不
提交 Git，仅提交本报告、QA 探针源码和必要文档。

代表性证据：

- `56-coordinate-gcj.log`、`57-coordinate-wgs.log`、`61-coordinate-bd.log`
- `70-vector-scope-restored.xml`、`70-scope-functional.log`
- `75-landscape-options.png`
- `76-background-hook.log`
- `81-search-timeout.png`
- `101-shanghai-ui-applied.png`
- `107-pre-reboot-hook.log`
- `108-pre-reboot-modules_config.db`、`109-post-reboot-modules_config.db`
- `111-post-reboot-hook.log`、`111-post-reboot-hook.png`
- `111-manager-ui.png`
- `112-stopped-manager-ui.png`、`112-stopped-real.log`、`112-stopped-real.png`
- `113-scope-before.png`、`113-scope-removed.png`

## 9. 建议

1. 作用域写入应增加明确超时、失败原因和回滚，不要无限转圈。
2. 管理器主页应查询实际服务/Hook 状态，不应只显示持久化布尔值。
3. Mock Provider 应明确列出支持的 Provider 和参数，或补齐 Network 与扩展字段。
4. 收藏删除改为确认后按稳定 ID 删除，并增加数据库结果校验。
5. 搜索请求增加 10–15 秒超时、取消和可重试错误态。
6. 所有底部菜单在横屏和小高度窗口中使用可滚动容器。
7. 外部控制统一参数范围，并接受 String/Float/Double 的安全转换，便于 ADB 自动化。
