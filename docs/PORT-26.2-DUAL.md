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

## 相机 HUD 修复

26.x 的 `Hud.extractRenderState()` 在 `isHidden` 为真时会跳过准星、hotbar
等整段 HUD。Fabric 原先把相机取景框挂在 `VanillaHudElements.HOTBAR` 后面，
因此相机模式隐藏原版 HUD 时，取景框也被一起跳过。

- 26.2/26.3 Fabric 共用 `layers/both/26.2-fabric` 中的
  `CameraHudMixin`，在 `extractRenderState()` 尾部单独绘制取景框；两个
  Fabric 目标的 `mcphone.mixins.json` 都已登记这个 Mixin。
- 26.2/26.3 NeoForge 继续使用 `RenderGuiEvent.Post`。该事件位于原版 HUD
  隐藏判断之外，不需要相同 Mixin。
- 用户已在 26.2 Fabric 的真实存档中测试相机 UI，结果正常。

## 验证

四个目标都从各自的 `platforms/26.2-*` 或 `platforms/26.3-*` 目录执行：

```powershell
.\gradlew.bat build -PmcphoneReuseGeneratedRenames=true --offline --console=plain
```

结果：

| 目标 | 任务数 | 断言 | 产物 | SHA-256 |
|---|---:|---:|---|---|
| 26.2 Fabric | 183 | 140 份 | `mcphone-1.12.0.jar`，4,924,541 字节 | `0B53EABFC16EB14F3240C0D5C25A4A2129D5E4E76D952865AFD6C9BB558C3968` |
| 26.2 NeoForge | 181 | 139 份 | `mcphone-1.12.0.jar`，3,936,573 字节 | `8B916D2D3D4F835A375C3D5414AF4D193864E961DA914FA57570A5CBF093DA4F` |
| 26.3 Fabric | 182 | 140 份 | `mcphone-1.12.0.jar`，4,924,083 字节 | `CF8689A6ADF3F8CCB7FFD277B2CBDB9EDFAB53F888DD8007FC10DF3E50202779` |
| 26.3 NeoForge | 180 | 139 份 | `mcphone-1.12.0.jar`，3,933,733 字节 | `538119EF3D17114CD29F63494A1D5703812E734A23BF8136E3AC03CE7BD74292` |

四个目标的 `check` 与 `build` 均成功；SPI、dist 隔离、共享层、接缝文档、
平台双胞胎、数据包目录名和 `recipe_unlocked` 条件字段校验通过。两个
Fabric 最终 jar 内都确认存在 `CameraHudMixin.class`，且
`mcphone.mixins.json` 已登记该类。

## 运行

- Fabric 专用服务器：真实启动到 `Done (0.502s)`，MCphone 脚本宿主
  完成装配，经 RCON 执行 `stop` 后正常保存并关服。
- NeoForge 专用服务器：真实启动到 `Done (0.608s)`，MCphone、AE2、
  Tom's Simple Storage 等联动加载，经 RCON 执行 `stop` 后正常关服。
- 两端客户端：真实启动到主菜单，OpenGL 后端、资源包重载、音效设备
  和 MCphone 客户端 App 注册均正常。
- 26.2 Fabric 已在真实存档中进入游戏并实测相机取景 UI。

尚未完成的是 26.2 NeoForge、26.3 Fabric 与 26.3 NeoForge 的完整 UI
矩阵实测。构建、打包和 26.2 Fabric 相机实测不能替代其余实机点验。
