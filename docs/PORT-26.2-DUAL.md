# MCphone 26.2 双端：上游 v1.12.0 适配记录

## 范围

本次从 26.2 双端共同基线 `cd1a9ece` 出发，合入上游
`897bd9a4`（`v1.12.0`）。26.2 的版本层、Fabric 加载器层和 NeoForge
加载器层继续独立存在，没有把 26.3 的平台目录直接复制过来。

保留的 26.2 行为差异：

- 26.2 不能直接读取 `Minecraft.screen`；共享页面统一经
  `Screens.current(Minecraft)` 判断当前界面。
- 26.x 的输入框光标按已过时间推进；平台门面
  `EditBoxes.tick(EditBox)` 保持为空实现，接住上游脚本文本框调用。
- 上游已移除 `PhoneSoundDevice`；26.2 与 26.3 四个平台中的遗留空壳一并删除。

## 验证

两端都从各自的 `platforms/26.2-*` 目录执行：

```powershell
.\gradlew.bat build -PmcphoneReuseGeneratedRenames=true --console=plain
```

结果：

| 目标 | 任务数 | 断言 | 产物 | SHA-256 |
|---|---:|---:|---|---|
| Fabric | 181 | 140 份 | `mcphone-1.12.0.jar`，4,924,075 字节 | `67A1A4FC4586DEB34EFA14182522E1A363045C2052AE7D2683339AB660C6AFD0` |
| NeoForge | 181 | 139 份 | `mcphone-1.12.0.jar`，3,936,607 字节 | `631E0EFF674472A6F1247571DFE5C5C1F9202CEC2675523EC3A4EC9C6E7AD349` |

两端 `check` 与 `build` 均成功；SPI、dist 隔离、共享层、接缝文档、
平台双胞胎、数据包目录名和 `recipe_unlocked` 条件字段校验通过。

## 运行

- Fabric 专用服务器：真实启动到 `Done (0.502s)`，MCphone 脚本宿主
  完成装配，经 RCON 执行 `stop` 后正常保存并关服。
- NeoForge 专用服务器：真实启动到 `Done (0.608s)`，MCphone、AE2、
  Tom's Simple Storage 等联动加载，经 RCON 执行 `stop` 后正常关服。
- 两端客户端：真实启动到主菜单，OpenGL 后端、资源包重载、音效设备
  和 MCphone 客户端 App 注册均正常。

尚未完成的是在真实客户端进入世界后逐项点验手机界面。构建与启动级
验证不能替代这一步。
