# Minecraft 1.18.2 Forge 验证记录

目标为 Minecraft 1.18.2、Forge 40.3.12、Java 17。客户端和专用服务端都安装同一目标的完整 JAR。在 `platforms/1.18.2-forge` 执行 `./gradlew build`，产物为 `build/libs/mcphone-1.12.1.jar`。

| 项目 | 当前结果 |
| --- | --- |
| 本地完整构建 | Java 17 下通过，含 148 组代码断言、重混淆、内嵌 Rhino 校验、客户端与服务端类隔离和数据包目录校验 |
| 无可选模组的专用服务端 | 测试包成功创建世界和启动脚本引擎 |
| 无可选模组的客户端 | 独立 Forge 40 客户端从 mods 目录加载打包 JAR，成功进服并收到 MCphone 的应用状态和价格表 |
| 游戏内操作 | 手机、平板、快捷键、相机与相册、聊天与图片、音乐、笔记、末影箱、脚本及重启后持久化仍需逐项验收 |
| 可选模组 | 专用服务端已与 Curios 5.0.9.2、Net Music、Waystones/Balm、Patchouli、AE2/AE2WTLib、Refined Storage、Tom's Simple Storage 和 Integrated Dynamics 一起启动。联动功能仍需逐项操作验收 |

在游戏内操作和联动验收完成前，目标使用 `buildable: false`，CI 仍会上传测试包。
