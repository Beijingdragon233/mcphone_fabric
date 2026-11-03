# 这一支的 access transformer：机制从哪来、要守什么

## 一句话结论

这一支【使用】access transformer，不【实现】access transformer。全仓唯一一处放宽
写在 `src/main/resources/META-INF/accesstransformer.cfg` 里（放宽 `MultiLineEditBox`
那个私有构造器），实现与许可都在上游工件手里。

## 谁在干活（本地取证，2026-09-29）

- 编译期：`net.neoforged.moddev` 插件 2.0.147（`build.gradle` 的 `plugins` 块钉的版本）。
  DSL 上那个入口在 `net.neoforged.moddevgradle.dsl.ModDevExtension`，javap 量到的三个成员是
  `accessTransformers(Action<DataFileCollection>)`、`getAccessTransformers()`、
  `setAccessTransformers(Object...)`，另有一道 `Property<Boolean> getValidateAccessTransformers()`
  ——它是【抽象属性】，说明这一支可以把「AT 条目必须命中真实成员」这个校验打开，
  写歪的条目会以构建错误收场，不会变成一句静默的 no-op。
- 真正改写 Minecraft 类的动作不在这个插件的 jar 里：它把 AT 文件交给 NeoForge 的
  userdev / NeoForm 补丁流程（`net/neoforged/nfrtgradle/CreateMinecraftArtifacts.class`
  等类里有这条链）。
- 运行期：NeoForge 加载器读 mod jar 里的 AT。`neoforge.mods.toml` 模板自己不写
  `[[accessTransformers]]` 时，回落到 `META-INF/accesstransformer.cfg`（模板注释里
  写了这条回落，见 `src/main/templates/META-INF/neoforge.mods.toml:61-64`）。
- 插件工件自带许可证文本：本地那个 jar 里有 `META-INF/LICENSE`、`META-INF/NOTICE.txt`
  与 `licenses/` 目录（条目名核过，内容【没有】抄进本仓库）。

## 所以要守的三条

1. 不改上游源码、不重发布上游工件，本项目里新增的只有那份 `.cfg`（一行 AT 条目）
   和 `build.gradle` 里的两行声明。AT 语法与语义归上游，本仓库只照它的格式写条目。
2. 发布的 mod jar 里带着那份 cfg（它在 `src/main/resources` 下，本来就会被打进产物），
   跟模组现有的 LGPL/MIT 双许可声明【没有新增冲突】：它不含上游代码的副本。
3. 这一支的 `validateAccessTransformers` 一旦关掉，AT 写歪就没人报错。留着 true。

## 待核实（没查完，别当成结论）

- AT 条目【格式】的规范出处（NeoForge docs 里那一页的版本对应关系），以及 `protected`
  在 NeoForge 26.3 的 AT 实现里是否允许把 `private`【降级】到 `protected`（本轮构建会
  给第一个答案；文档出处等下一次补）。
- 若将来还要放宽第二个成员，先回来看这一份，别往同一个 cfg 里随手加行。
