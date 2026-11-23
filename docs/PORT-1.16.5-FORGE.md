# Minecraft 1.16.5 Forge / Java 8 验证记录

本目标仍为 `buildable: false`。可用来试跑的构建不代表游戏内验收通过；完成下面的操作记录后，才能进入未来发布矩阵。本轮不发布 GitHub Release、CurseForge 或 Modrinth。

## 目标与结构

- 基线：`26.x-`，`dd99bf73eb45eeb570f0e10710948bbd28efcf12`。
- Minecraft 1.16.5，Forge 36.2.42，官方映射。
- ForgeGradle 6.0.54，Gradle 8.4 运行于 JDK 17；生产代码由 Java 8 工具链编译，游戏运行于 Java 8。
- 本地 Java 8：Zulu 8.98.0.201 / 1.8.0_514。
- 共享 Java 8 工具位于 `shared/.../util/Java8*.java`；Minecraft 差异位于 `layers/version/1.16.5` 和 `platforms/1.16.5-forge`。后续旧版本不能直接假定这些 Minecraft API 可复用。
- Fabric / NeoForge 只有矩阵占位。本轮不处理 1.21.11 的已有移植问题。

## 可重复构建

先安装 JDK 8 和 JDK 17，把 `JAVA_HOME` 指向 JDK 17，并通过 `JAVA8_HOME` 或 `JAVA_HOME_8_X64` 指向 JDK 8。在 `platforms/1.16.5-forge` 执行：

```powershell
./gradlew.bat build --no-daemon
```

Linux CI 使用相同的工具链和 `./gradlew build`。PR 矩阵构建所有已存在的目标目录；`buildable: false` 的成功构建也上传带“未验证”标记的试玩 artifact。发布流程仍只选择 `buildable: true`。

本机使用隔离的 Gradle 缓存和本地仓库镜像 init script。日志中的 `-Dnet.minecraftforge.gradle.check.certs=false` 是本机 ForgeGradle 证书检查排障参数，没有写入工程或 CI 默认配置。

## Java 8 与内嵌库

- Java 8 Rhino 为 `1.7.15.1-mcphone.2`，锁文件为 `versions/private-rhino-java8.json`；现代目标继续使用原有引擎。
- 私有 Rhino、JavaMP3 1.0.1 和私有化的 Bouncy Castle 1.87 合入重混淆 JAR。Forge 36 无需嵌套 JAR 加载器。
- Rhino 和加密库包名分别为 `com.november.mcphone.internal.rhino` 和 `com.november.mcphone.internal.crypto`；不向全局 Security 注册提供者。
- JavaMP3、Rhino 和 Bouncy Castle 许可证随成品分发。Rhino 对应源码随 Maven vendor artifact 保存。
- `verifyJava8Distribution` 扫描成品所有 class，拒绝 major > 52、嵌套 JAR、未私有化 Rhino / BC 包及缺失许可证，并逐条核对 Rhino 与锁定构件的内容。
- Java 8 下执行脚本样例、静态检查、宿主 API / 预算 / 权限断言，以及 HTTP 状态、重定向禁止、大小限制、非法 UTF-8、取消和并发超时恢复测试。
- Java 8 与 Java 17 的独立进程双向交换 Ed25519 编码公私钥与签名，校验篡改、截断和小阶公钥拒绝。另直接用生产合包 JAR 在 Java 8 运行签名断言。
- 使用 JDK 17 / ASM 9.9.1 从锁定上游文件重新运行 `Java8RhinoProducer`，四个输出文件 SHA-256 与 vendor 完全一致。

## 联动实例固定版本

以下版本已安装于标准 Forge 36.2.42 / Java 8 的专服验证实例。启动成功不等于 App 操作通过。MCEF 只加入客户端实例。

| 联动 | 固定版本 | 当前验证范围 | 尚需实测 |
| --- | --- | --- | --- |
| Curios | 4.1.0.0 | 与其他联动共同启动 | 饰品槽手机、快捷键、重连 |
| Net Music | 1.1.3 | 专服共同启动 | 搜索、播放、暂停和切换 |
| Waystones | 7.6.4 | 手机专用选择菜单与受限 warp stone 桥接 | 传送、费用、冷却、维度限制 |
| Patchouli | 1.16.4-53.3 | 专服共同启动 | App 打开书籍与翻页 |
| FTB Quests | 1605.3.7-build.165 | 专服共同启动；反射入口与该版 API 对齐 | 打开任务、提交与奖励 |
| AE2 | 8.4.7 | 专服加载；手机终端 host / 容器存活 Mixin | 真实网络绑定、存取、电量与重连 |
| AE2 无线终端（AE2WTLib REDUX） | 4.0.2（CurseForge 1686686 / file 9357345） | 手机槽 host 身份 + 五个终端容器 stillValid Mixin；离线编译通过 | 真实网络绑定、电量、合成格 UI 与重连 |
| Refined Storage | 1.9.18 | 手机能力槽 PlayerSlot 桥接 | 真实网络打开、存取、电量与重连 |
| Tom’s Simple Storage | 1.2.24 | 专服共同启动 | 绑定、范围限制和存取 |
| Integrated Dynamics | 1.10.15 | 专服共同启动、兼容模块加载 | 手册 / App 操作 |
| MCEF | 2.4，CurseForge file 5830325 | 已固定客户端依赖 | 本地库加载、真实网页渲染及键鼠输入 |

附加依赖：FTB Library 1605.3.4-build.90、FTB Teams 1605.2.3-build.40、Architectury 1.32.68、Item Filters 1605.2.5-build.9、Cyclops Core 1.13.0、Common Capabilities 2.8.0。

AE2 / RS 通过保留的整型槽位标记解析玩家手机能力中的同一个 ItemStack；不把终端临时挪入背包。Waystones 仅在手机专用服务端菜单存在且玩家携带手机时提供 warp stone，保留其自身的费用、冷却和传送校验。

## 必须完成的游戏验收

| 场景 | 状态 |
| --- | --- |
| 标准 Forge 从 mods 加载同一成品 JAR，纯净专服与带联动专服 | 有启动证据，最终成品复验见下方记录 |
| 无可选模组单机，手机和平板、快捷键 | 待操作 |
| 两个 Java 8 客户端连接同版专服 | 待操作 |
| 相机与相册、聊天与图片、音乐、笔记、商店、末影箱、脚本 | 待操作 |
| 服务端关闭发图 / 传送时客户端界面及权威限制一致 | 待操作 |
| 重启后笔记、聊天、终端、脚本等持久化 | 待操作 |
| 上表每项可用联动实际打开并操作 | 待操作 |
| MCEF 真实网页渲染与输入 | 待操作 |

当前桌面控制工具拒绝访问 Java / Minecraft（`Computer Use was not approved to use java`），因此不能把已启动客户端或专服日志当作已完成 UI 验收。恢复该权限后继续执行实测。

## 本地证据（2026-11-23）

本机证据目录为 `Y:/mod-手机/.build-tools`；它不属于需要安装的生产 JAR。

- `1165-java8-transport-signatures2.log`：Java 8 脚本与 HTTP / 签名测试、双向 JVM 签名交换通过。
- `1165-private-crypto-build2.log`：私有加密库合包、重混淆、字节码及许可证检查通过。
- `1165-final-build6.log`：完整断言套件通过；差异基线和生成说明因 RS 桥接变更过期失败，不能称为完整构建成功。
- `1165-rs-final-package.log`：更新基线和说明后，最新 RS 源码编译、合包、重混淆和成品检查通过。
- `1165-final-build7.log`：完整构建通过，199 个任务；之后补充 AE2 槽位锁定保护。
- `1165-final-build8.log`：包含 AE2 修复的最终完整构建通过，199 个任务，耗时 7 分 2 秒。
- `1182-final-shared-compile.log`、`1201-forge-java8-shared-regress.log`：共享代码在 1.18.2 Forge / 1.20.1 Forge 编译通过。其他现代目标单独记录，不修改 1.21.11。
- `1165-evidence/addons-before-rs-*.log`：带上表服务端联动的启动、AE2 Mixin 应用和正常存档退出；对应较早 JAR。
- `addons1165/ae2wtlib-4.0.2-1.16.5.jar`：AE2WTLib REDUX 4.0.2（CurseForge 1686686 / file 9357345，release 2026-10-28），367,681 字节，SHA1 `7D097F8A3FE71082DEE9B2C765145F8AF243E715`；源自 `github.com/BlobAWV/AE2WirelessTerminalLibrary-Redux` 的 1.16.5 分支，编译目标 AE2 8.4.7 / Forge 36.2.42，mods.toml 依赖 `appliedenergistics2 [8.4.0,)`。已镜像到 `1165-maven/curse/maven/ae2-wireless-terminals-redux-1686686/9357345/`，`compileJava --offline` 通过。

现代目标编译回归全部通过：1.20.1 Fabric（`1.20.1-fabric-java8-shared-regress2.log`）、1.21.1 Fabric（`1.21.1-fabric-java8-shared-regress.log`）、1.21.1 NeoForge 和 26.1.2 NeoForge（各自 `*-java8-shared-regress2.log`）。发现并修复本轮引入的一处 NeoForge import 拼写问题后重跑通过。未处理 1.21.11。

第 8 轮完整构建成品：`platforms/1.16.5-forge/build/libs/mcphone-1.12.1.jar`。该轮 SHA-256（后续修订不能沿用此摘要）：

```text
16c0597db2ab19c063546d04b9d9070149802e85856d281ae6f96f8a5aa72a4d
```

此 SHA 的同一 JAR 在无可选模组专服（`forge1165-final-clean`，127.0.0.1:25667）和固定联动专服（`forge1165-addons`，127.0.0.1:25666）均成功启动。无客户端类加载异常。

固定联动专服运行独立测试模组：**47 passed / 0 failed / 0 skipped**。原始报告为 `docs/validation/1.16.5-forge/bridge-fixture.txt`，可复现源码和构建说明位于 `tools/forge1165-fixture`。它覆盖 RS 原始对象、槽位锁定与网络序列化、Waystones 手机/普通菜单及携带状态边界、AE2 host 与真实容器构造和摘除检测。AE2 测试为无真实网络的 host 注入空存储 facade，仅用于抵达槽位锁定与存活判断；**不能据此认为 AE2 的网络、能量或存取已通过**。

`1165-evidence/final-addons-*.log` 记录全部四个 Mixin 的实际应用及该轮运行。上游联动有已知启动告警：RS 更新地址 404、NetMusic 可选外部物品配方缺失、Tom’s 的 netherite 进度物品名、ID 手册连接器配方；它们没有阻止启动，但仍应在真实 App 操作时检查。

PR：[#4](https://github.com/Beijingdragon233/mcphone_fabric/pull/4)，保持草稿。首轮 [CI #74](https://github.com/Beijingdragon233/mcphone_fabric/actions/runs/45182169593) 的 1.16.5 Forge 完整构建通过并上传未验证试玩包；现代目标回归发现共享解析入口影响新 Rhino，现已将 String / Reader 入口分别置于共享层与 Java 8 覆盖层。

本地 `1165-parser-regress.log` 的 Java 8 脚本与四组客户端回归、重混淆和成品检查通过；`121-modern-parser-http-regress2.log` 的现代引擎四组回归通过。HTTP 传输拆分引入的响应体超时分类也已修复：总期限到期返回 `TIMEOUT` 并取消底层请求。Rhino `.2` 补齐上游 `NOTICE-tools.txt`，旧构件 `.1` 保持原摘要；完整重建及新 CI 记录随后更新。

[CI #76](https://github.com/Beijingdragon233/mcphone_fabric/actions/runs/45189406904)（`13c21038`）的全部目标已通过；1.21.1 NeoForge 首次仅布局测试的 50ms 性能断言失败，单独重跑通过。Java 8 仅在 1.16.5 任务安装，消除了其下载摘要校验故障对现代目标的影响。1.21.11 只随既有矩阵运行，没有另做其移植工作。本地 `1182-full-shared-regress3.log` 完整构建通过，190 个任务。

`1165-final-build9.log` 的完整重建未通过：Java 8 HTTP 测试在 Windows 原生套接字调用中崩溃，`EXCEPTION_INVALID_HANDLE`；崩溃报告保存在 `1165-evidence/hs_err_pid53268.log`。该轮其它断言和合包检查通过，但不能称为完整成功。后续加入 REDUX 时的一次重试受新增依赖尚未进入本地镜像影响，未实际执行 HTTP 测试；正在完成依赖解析后复验。

第 11 轮（含 REDUX 桥接与空菜单返回修复）完整构建通过：`1165-final-build11.log`，201 个任务，6 分 34 秒。第 10 轮所有运行断言与 Java 8 HTTP 测试通过，仅版本差异基线过期；更新已审查的版本差异基线后，第 11 轮整体通过。

最新本地 JAR：`platforms/1.16.5-forge/build/libs/mcphone-1.12.1.jar`，SHA-256：

```text
be6f8caddbe6e4d5bd05b88e43a6714a8e321d8c7510bfd850ee9c359f730d84
```

此摘要的同一 JAR 再次在纯净专服与包含 REDUX 4.0.2 的固定联动专服启动成功，随后正常执行 stop 存档退出。日志为 `1165-evidence/redux-final-clean-latest.log`、`redux-final-addons-latest.log` 和 `redux-final-addons-debug.log`。最新夹具 **63 passed / 0 failed / 0 skipped**，原始报告覆盖到 `docs/validation/1.16.5-forge/bridge-fixture.txt`。REDUX 的真实 WCT 容器、活对象身份、取走手机、替换终端及普通背包槽对照通过；第一次因夹具反射漏写 int 参数而失败的记录保留于 `1165-evidence/redux-first-fixture-fail.txt`。

WCT 的空存储 facade 只为抵达容器与存活检查，未验证真实网络、能量、范围、合成、NetworkHooks 打开或客户端界面。空 WUT 的用例也使用未绑定终端，不能代替有效网络下的菜单分发验证。CI artifact 随包附 `SHA256SUMS.txt`，其中摘要属于该次 CI JAR，不能假定与本地 JAR 相同。

保持 `buildable: false`，不因构建或测试夹具通过而提前宣布完整验收。
