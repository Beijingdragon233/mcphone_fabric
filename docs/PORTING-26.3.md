# 适配 Minecraft 26.3：共用代码挂上来之后的实测

> 本文件是 1c 的产出：把 `platforms/26.3-neoforge` 的共用代码开关从 `false` 翻成
> `true`，让 javac 把整片红吐出来，然后把这堆红**归因**。所有数字来自那一轮日志
> 与 26.3 官方源，不是估计。两条翻案在 §四，结构性结论（不加 `until`）在 §九，
> 1d 的顺序在 §十二。

## 复现

```
$ cd platforms/26.3-neoforge
$ .\gradlew.bat compileJava --console=plain
BUILD FAILED in 21s
1,523 个错误（build.gradle 里 -Xmaxerrs 100000，未被默认的 100 条截断）
```

对照的「26.3 官方源」是
`~/.gradle/caches/neoformruntime/intermediate_results/mergeWithSources_*_output.jar`
里那 7,301 个 `.java`（26.3 不混淆，见 `versions/targets.json` 的 26.3-neoforge note）。
下面每一行「26.3 是什么形状」都是在那个 jar 里查到的，带 `文件:行号`。

## 0. 先说两条翻案

- **`fill` 不要改。** `GuiGraphicsExtractor.fill(int,int,int,int,int)` 还在
  （`GuiGraphicsExtractor.java:184`，内部自己填 `RenderPipelines.GUI`）。原先记着
  「26.x 起 fill/blit 强制 RenderPipeline 首参」，对 `fill` 不成立。
- **`Screen` 的 `width`/`height`/`font`/`minecraft` 都还在**
  （`Screen.java:66`、`:68`、`:69`、`:71`，宽高还从 `protected` 变成了 `public`）。
  所以这一轮日志里那 169 条「找不到 `font`(51) / `width`(31) / `height`(30) / `super`(26) /
  `screen`(21) / `minecraft`(10)」**不是 26.x 的断裂**，是级联，见 §二。

## 一、七个桶

归因规则本身也是结论的一部分：级联怎么判见 §二，哪些覆写在 26.3 里其实一字未改见 §五，
「本仓自己的门面」算哪一类见 §六。

| 桶 | 条数 | 占比 | 涉及文件 |
|---|---:|---:|---:|
| A 26.x 改名（`GuiGraphics`、`ResourceLocation` 一族） | 588 | 38.6% | 132 |
| B 本平台文件还没补（连带它的直接引用） | 504 | 33.1% | 157 |
| E 级联：26.3 里该成员仍在，父类修好就自己消失 | 186 | 12.2% | 23 |
| G 真断：其它 API 形变 | 134 | 8.8% | 57 |
| D 真断：附属模组在 26.3 没有构件 | 54 | 3.5% | 14 |
| C 真断：覆写签名变了 | 41 | 2.7% | 13 |
| F 真断：GLFW 整个没了 | 16 | 1.1% | 4 |

**真断小计（C+D+F+G）= 245 条，落在 69 个文件里。**
剩下 145 个文件只被「改名 / 补平台文件 / 级联」卡着 —— 它们不构成设计问题。
这一支挂载一共 **422 个源文件**（`shared/` 354 + 四层 67 + 本平台 1），其中 **208 个
一个字都没改就编过了**（49.3%）。然后下面那三块（级联、`until`、`MCphone.java`）才是本轮的产出。

---

## 二、级联为什么白占三分之一

`platforms/26.3-neoforge/` 到现在只有**一个** java 文件（那个探针 `MCphone.java`），
而 `1.21.1-neoforge/` 有 **48 个**。共用代码里 `PhoneScreen extends PhoneScreenBase`，
而 `PhoneScreenBase` 是每平台一份的 —— 26.3 没有，于是：

1. `PhoneScreen` 的父类成了 error type，它从 `Screen` 继承来的 `font` / `width` / `height`
   / `minecraft` 全部报「找不到符号」（这一族 169 条；E 桶合计 186 条）；
2. 它自己写的 `@Override removed()` / `isPauseScreen()` / `onClose()` / `init()` /
   `onFilesDrop()` 全部报「方法不会覆盖或实现超类型的方法」—— 而这些方法在 26.3 的
   `Screen.java` 里一字未改（`:387`、`:438`、`:208`、`:381`、`:477`）。

**所以这一轮日志不能当工作量表用**：1523 条里约 690 条（B 的直接引用 504 + E 的级联 186，
其中含 11 条「签名其实没变却报不会覆盖」的覆写）会在补完平台文件之后自己消失。

顺带纠正一个原先记着的判断：**`Screen` 的 `width`/`height`/`font`/`minecraft` 在 26.3 都还在**，
宽高还从 `protected` 变成 `public`（`Screen.java:66`、`:68`、`:69`、`:71`）。
那 169 条「找不到 `font`/`width`/`height`/`super`/`screen`/`minecraft`」不是 26.x 的断裂；
E 桶一共 186 条，多出的 17 条是 `setDirty`（`SavedData.java:6` 还在）与那 11 条签名未变的覆写。

---

## 三、A 桶：改名一族，逐条对着 26.3 的源验过

| 旧 | 新（26.3 里在哪） | 条数 |
|---|---|---:|
| `GuiGraphics` | `GuiGraphicsExtractor` | 244 |
| `ResourceLocation` | `net.minecraft.resources.Identifier`，`fromNamespaceAndPath`/`withDefaultNamespace`/`parse`/`STREAM_CODEC` 形状不变 | 315 |
| `ToastComponent` | `ToastManager#addToast(Toast)`；`Toast` 变接口，覆写点是 `extractRenderState(GuiGraphicsExtractor, Font, long)` | 4 |
| `GameProfileCache`、`MinecraftServer#getProfileCache()` | `net.minecraft.server.players.ProfileResolver`（经 `Services`） | 10 |
| `MetadataSectionSerializer<T>` | `record MetadataSectionType<T>(String name, Codec<T> codec)` —— 匿名类要改写成 `Codec` | 3 |
| `PlayerFaceRenderer` | `PlayerFaceExtractor` | 2 |
| `InteractionResultHolder<T>` | `net.minecraft.world.InteractionResult` | 3 |
| `Util`（整类搬家） | `net.minecraft.Util` → **`net.minecraft.util.Util`**；`backgroundExecutor()`、`ioPool()`、`getFilenameFormattedDateTime()`、`getPlatform()` 四个方法都还在。但 `Util.OS` 那个枚举现在只剩 `telemetryName()`，**`openPath` 从枚举上摘掉了**，换成 `com.mojang.blaze3d.Blaze3D.openPath(Path)` | 21 |
| `ServerPlayer#server` | 字段还在但**私有**（`ServerPlayer.java:1057` 自己用 `this.server`），要换 getter | 21 |
| `Player#displayClientMessage` | 0 命中；`sendSystemMessage(Component)` 到处在用 | 5 |
| `NativeImage#setPixelRGBA` | `setPixel(x, y, int)` | 2 |
| `DynamicTexture(NativeImage)` | 构造器变成 `(Supplier<String>, NativeImage)` | 2 |
| `Biome#getPrecipitationAt(BlockPos)` | `(BlockPos, int seaLevel)`，`Biome.java:107` | 1 |
| `KeyMapping#getKey(int,int)` / `getScanCode()` | `getKey()` 现在返回 `InputConstants.Key`；`getScanCode` 0 命中 | 3 |
| `ItemStack#appendHoverText` | 挪到 `Item`：`ItemStack.java:951` 转调 `getItem().appendHoverText(...)` | 1 |
| `GameProfile#getName()` | 类在 authlib 不在 26.3 源里，`getName` 0 命中，大概率 record 化成 `name()` | 3 |
| `FriendlyByteBuf#writeCollection` / `StackCodecs` | 待查 | 5 |
| `Options#hideGui` | 0 命中，去处未定 | 3 |
| `Level`/`ServerLevel#getDayTime()` | 0 命中；`LevelData` 那侧只剩 `getDayTimeFraction()` | 2 |

---

## 四、`fill` / `blit` 这一族：本轮翻案

原先记着的是「26.x 起 `fill`/`blit` 强制 `RenderPipeline` 首参」，据此估出「139 个 `fill`、
80 个文件要接渲染管线」。**实测不成立**：

```java
// GuiGraphicsExtractor.java:184 —— 无 pipeline 的 5 参 fill 还在，内部自己转
public void fill(int x0, int y0, int x1, int y1, int col) {
    this.fill(RenderPipelines.GUI, x0, y0, x1, y1, col);
}
```

共用代码里这些调用的**实参个数分布**（扫 `shared/` + 三层，不看错误日志）：

| 调用 | 实参个数 → 处数 | 26.3 的对应 | 要改什么 |
|---|---|---|---|
| `fill(` | 5 参 → 139（另有 2 参 4 处、0 参 1 处是别的同名方法） | `fill(int,int,int,int,int)` 存在 | 只换接收者类型名 |
| `drawString(` | 6 参 → 217 | `text(Font,String,int,int,int,boolean)` 同 6 参 | 换方法名 |
| `drawCenteredString(` | 5 参 → 5 | `centeredText(Font,String,int,int,int)` 同 5 参 | 换方法名 |
| `pose()` | 0 参 → 45 | `pose()` 存在，返回 `Matrix3x2fStack` | 不用改 |
| `blit(` | **11 参 → 2** | 长形态全部要 `RenderPipeline` 首参 | 这 2 处插 `RenderPipelines.GUI_TEXTURED` |
| `blitSprite(` / `drawSpecial` / `hLine` / `vLine` / `drawItem` / `renderItemDecorations` | **0 处** | —— | 这几条改名对本仓零成本，物品绘制走 `Draw` 那个平台门面 |

也就是说：**绘制族的真实工作量是「改名字」，不是「接管线」**。需要动签名的 `blit` 只有 2 处，
不是几十处。这件事必须在补完平台文件之后再复核一遍 —— 现在有些 `fill` 调用可能因为
父类不可解析而没被计数到。

那 2 处 `blit` 分别在 `shared/src/main/java/com/november/mcphone/core/client/GuiUtil.java:93`
与 `layers/loader/neoforge/docs/AddonApiExamples.java:86`（后者是文档里那份可编译副本）。

---

## 五、C 桶：覆写签名，41 条，落在 13 个文件

26.3 的输入事件全线换成记录类型（`GuiEventListener.java`）：

```java
default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)   // record(double x, double y, MouseButtonInfo)
default boolean mouseReleased(MouseButtonEvent event)
default boolean mouseDragged(MouseButtonEvent event, double dx, double dy)
default boolean mouseScrolled(double x, double y, double scrollX, double scrollY)
default boolean keyPressed(KeyEvent event)      // record(int key, int keycode, int modifiers)
default boolean keyReleased(KeyEvent event)
default boolean charTyped(CharacterEvent event) // record(int codepoint)
default void mouseMoved(double x, double y)
```

绘制侧 `Renderable.extractRenderState(GuiGraphicsExtractor, int, int, float)` 取代 `render(...)`，
`Screen.extractRenderState` 在 `Screen.java:121`；`Screen.resize` 变成 `resize(int,int)`（`:454`，
1.21.1 那个 `resize(Minecraft,int,int)` 没了）；`AbstractContainerScreen` 的 `renderBg` **0 命中**，
`imageWidth` / `imageHeight` 变 `protected final` 且从构造器传入（`:38`、`:39`、`:64`）——
这就是那 6 条「无法为 final 变量分配值」的来历。

按方法名分：`render` 7、`renderBg` 3、`resize` 3、`mouseClicked` 3、`mouseReleased` 3、
`onScroll` 3、`keyPressed` 3、`mouseDragged` 2、`charTyped` 2、`write` 2（`SavedData#save` 一族），
以及 `getMetadataSectionName`、`fromJson`、`renderLabels`、`appendHoverText`、`keyReleased`、
`extractRenderState`（`PhoneToast` 没实现新抽象方法）等单条。

另有 11 条「不会覆盖」的 `removed` / `isPauseScreen` / `onClose` / `init` / `onFilesDrop` /
`mouseMoved` 归进了 E 桶：它们在 26.3 的 `Screen` 里签名一字未改，是父类不可解析造成的。

**`onScroll` 那 3 条不是 26.x 的断裂，查实了。** `onScroll` 是 `platform/client/PhoneScreenBase.java:44`
自己定义的方法，各平台在那份文件的 `:34` 用 `mouseScrolled(...)` 转调它 —— 那个类的 javadoc
第一句就写着「**它存在的唯一理由是 `mouseScrolled` 的签名在版本之间变了**」（1.21 把三参改成四参）。
26.3 的父类没补，所以 `shared/` 里这 3 处 `@Override onScroll` 报「不会覆盖」。
上面那 41 条真断里扣掉这 3 条，**下界是 38 条**。

**这批覆写集中在三个文件。** 按文件数（含那 11 条级联，共 52 条）：`PhoneScreen` 13、
`BrowserScreen` 13、`PhoneHudEditor` 9 —— 这三家占掉 35 条；剩下散在 10 个文件里：
`TerminalSlotScreen` 3、`TerminalSlotReference` 3、`PhoneSkin` 2、`PhoneContainerScreen` 2、
`DiscBayScreen` 2，`FriendData` / `PhoneToast` / `ChatData` /
`TerminalSlotReferenceFactory` / `PhoneItem` 各 1。

---

## 六、四处硬骨头（改不动的，只能重写的）

**1. GLFW 整个没了（16 条 / 4 文件）。** 26.3 的源里 `glfw` 0 命中，
`com/mojang/blaze3d/platform/` 下换成了 `SDLEventHandler`、`Window`、`WindowEventHandler`、
`TextInputManager`、`MonitorManager`、`ClipboardManager`、`VideoMode`、`InputConstants` 这一套。
本模组用 GLFW 的地方是热键与组合键：`PhoneKeys`、`AppHotkeyHandler`、`AppManagerDetail`
（`GLFW.glfwGetKey` 之类 + `GLFW_KEY_*` 常量），共 12 处。
这不是改名，是换一套输入后端 —— 而且 `AppHotkeys` / `KeyModifiers` 本来就是每平台一份，
**正好落在平台文件那一层里改**，不用碰 `shared/`。

**2. `RenderSystem` 的 GL 状态方法没了（本轮日志 9 条，源码侧 13 处调用点 / 4 个文件）。**
`setShaderColor`、`setShaderTexture`、
`enableBlend` / `disableBlend`、`defaultBlendFunc`、`enableDepthTest` / `disableDepthTest`
在 `com/mojang/blaze3d/systems/RenderSystem.java` 里全部 0 命中。混合现在是**声明式**的：
`com.mojang.renderpearl.api.pipeline.BlendFunction` / `ColorTargetState` / `RenderPipeline`，
现成的管线常量在 `net.minecraft.client.renderer.RenderPipelines`。
也就是说这几处要改成「挑一条既有 pipeline」或「自己声明一条」，不是换个方法名。
四个文件与处数：`core/client/GuiUtil.java` 3、`feature/browser/client/BrowserScreen.java` 5、
`core/script/client/render/IconAtlas.java` 2，外加
`layers/loader/neoforge/docs/AddonApiExamples.java` 3（文档里那份可编译副本）。

**3. `OggAudioStream` 没了，但本仓只有一层薄皮依赖它。** `com/mojang/blaze3d/audio/` 只剩
12 个类（`SoundBuffer`、`OpenAlUtil`、`Library`、`Channel`、`Listener`、`DeviceTracker` 一族），
**没有任何 Ogg 解码**；`AudioStream` 接口在 `net/minecraft/client/sounds/AudioStream.java`。
好消息是本模组的解码早就是自己的：`shared/.../feature/music/client/playback/` 下有
`AudioDecoder`、`OggDecoder`、`Mp3Decoder`、`WavDecoder`、`PcmAudioStream` 一整族，
`OggAudioStream` 全仓只出现在**每平台一份**的 `platform/client/VanillaAudio.java`
（1.20.1 三处、1.21.1 各一处）与 `shared/.../KnownDuration.java` 的一句注释里。
所以真正的活是：给 26.3 写一份 `VanillaAudio`，把现成的 `PcmAudioStream` 接到 26.3 的
`AudioStream` 上。26.x 的声音引擎怎么供流（`SoundBuffer` 直喂？还是要 `Library` 侧改动），
本轮未取证 —— 挂上 `VanillaAudio` 之后自然就量得出来。

**4. `ItemProperties` 那套换成数据驱动。** `ItemProperties` 与 `ClampedItemPropertyFunction`
两个类都不存在了，新框架在 `net/minecraft/client/renderer/item/properties/conditional/`
（那里只有 `ItemModelPropertyTest` 这类条件属性实现，没有旧的注册表）。
本轮日志里它只报了 3 条，全在 `shared/.../core/client/PhoneItemProperties.java`；
1.21.1-neoforge 那一份里还有 5 处（`MCphoneClient` 3、`ModItems` 1、`ModDataComponents` 1），
这些文件 26.3 还没写 —— 等 §八 补进来才会真正暴露。也就是说这一处的真实规模，
现在看到的是下界，不是全貌。

另外 `Tesselator` / `BufferUploader` 没了但 `BufferBuilder` + `MeshData` 还在（改用
`buildOrThrow()` + `try (MeshData …)`），`VertexFormat` 搬去 `com.mojang.renderpearl.api.vertex`。
`PostChain` 类还在，但 `process` 多了个 `GraphicsResourceAllocator` 参数、`setUniform` 0 命中 ——
本仓用它的全在**每平台一份**的 `feature/camera/client/CameraFlash.java`（1.20.1 七处、
1.21.1 各五处），26.3 那份还没写，所以本轮日志里一条都没报。
`BakedModel`、`RecordItem` 两个类整体消失，去处未定。

---

## 七、D 桶：附属模组在 26.3 没有构件（54 条 / 14 文件）

这些不是 Mojang 改了什么，是**26.3 上根本没有那个 jar**。骨架的 `gradle.properties`
一个联动都没挂，所以一挂上带联动的代码就红在这里。

上一轮在 Modrinth 查过（2026-09-19）：Curios、Patchouli、MCEF、Refined Storage 在 26.3
**零构建**；Fabric API、Waystones、Balm 有。AE2、FTB Quests、NetMusic、GuideME 当时
slug 猜错拿到 404，**未取证**。

落到代码上，受影响的就是 `feature/reader/client/source/PatchouliSource`(11)、
`GuideMeSource`(9 条，多在本平台文件未补那几类里)、`feature/browser/client/McefBackend`(7)、
`feature/terminal/integration/{ae2,refinedstorage}/*`、`compat/CuriosCompat`(4)、
`compat/NetMusicCompat`(3)、`feature/waystone/client/WaystoneApp`(3) 等。

这一桶要的不是改代码，是**决定 26.3 首发支持哪几个联动**。在有人给出 26.3 构建之前，
这些文件要么按目标排除，要么留到附属接口那层做适配 —— 这是产品决策，不是技术问题。

---

## 八、B 桶：本平台还缺 47 个文件、5279 行

`platforms/1.21.1-neoforge/src/main/java` 有 48 个文件，`platforms/26.3-neoforge` 只有 1 个。
缺的这 47 个就是 1d 的活，其中最大几块：

```text
 444  core/client/PhoneHud.java              394  core/client/ClientConfig.java
 363  feature/chat/net/ChatNetworking.java   275  core/client/AppHotkeys.java
 275  feature/music/DiscService.java         265  core/net/NetworkHandler.java
 201  core/PhonePlayerData.java              192  compat/WaystonesCompat.java
 192  MCphoneClient.java                     167  feature/camera/client/CameraFlash.java
 152  feature/store/net/StoreNetworking.java 147  core/ServerConfig.java
 140  compat/IntegratedDynamicsCompat.java   129  core/net/MCphoneNetwork.java
 127  core/ModAttachments.java                86  platform/client/Draw.java
  78  .../refinedstorage/RefinedStorageIntegration.java
  72  api/client/ui/PhoneMultiLineEditBox.java
  48  platform/client/PhoneScreenBase.java    34  platform/client/VanillaAudio.java
```

**`MCphone.java` 不是「缺文件」，是缺成员**：骨架那一份只有探针，缺 `LOGGER`、`MODID`、
`getVersion()`。就这三个符号，直接报出 **279 条**（`LOGGER` 216 + `MODID` 61 + `getVersion` 2），
占总量的 18%。补它是 1d 的第一步，也是把这份日志变干净的第一步。

`shared/PLATFORM-SEAMS.md` 里 `26.3-neoforge` 那一段现在只列了 `MCphone` 一个
（「共用代码引用了的（1）」+「平台内部的（0）」）。那不是漏 —— 那段是**扫本平台目录下实际存在的
java 文件**生成的，而这里现在确实只有那一个文件。补进上面那 47 个之后它会自动变长，而忘了跑
`updateSeamsDoc` 的那次提交会被 `verifySeamsDocCurrent` 当场拦住（这一轮跑过了，绿）。

---

## 九、那个结构性问题：要不要给层加「到 X 为止」的谓词

**结论：不要。本轮实测不支持它。**

理由分三层说：

1. **层的准入条件本仓写死了**：一层里的代码必须对挂载它的每个目标**逐字相同**、且已经在
   那些版本上各自编过（`versions/layers.json` 的 note 就是这个意思）。而 26.x 这批改的是
   **方法签名**，签名不同的同一段代码不可能同时在 1.21.1 和 26.3 上编过 —— 它连入层的
   资格都没有。加一个 `until` 谓词只是把「不能共用」变成「可以分开抄」，那是 `platforms/`
   已经在做的事。
2. **真断的覆写只有 38~41 条、13 个文件**，而且高度集中：`render`/`extractRenderState` 一处，
   六个输入事件一处，`resize`、`renderBg`、`imageWidth/Height` 各一处。这种形状**本仓已经解决过一次**：
   `platform/client/PhoneScreenBase.java` 的 javadoc 第一句是「它存在的唯一理由是 `mouseScrolled`
   的签名在版本之间变了」（1.20.1 三参 → 1.21 四参），它在那份文件的 `:34` 把原版的
   `mouseScrolled` 转调到自己定义的 `onScroll`（`:44`），`shared/` 里三家就只覆写 `onScroll`。
   26.x 这批签名差异要的就是同一个招，再往下扩一个 `PhoneContainerScreenBase` 而已。
   为它新增一条谓词轴、一份 Gradle 语义、CI 里那份同语义的 jq，代价远大于把基类下沉到各平台。
3. **A 桶那 588 条改名不需要任何新机制**：它们要的是「一份源码在两个名字下都能编」，
   而这在 Java 里做不到 —— 所以只能各平台各留一份 facade。本仓的 `platform/client/Draw`、
   `StackCodecs`、`ModPresence` 这些 facade 类就是干这个的，26.3 补上同名文件即可。

   `Util` 是这一条最干净的样本。它是**整类换包**（`net.minecraft` → `net.minecraft.util`），
   四个用到的方法一个没少 —— 名字对不上纯粹是 import 那一行。而 Java 里没有一个 import
   不可能同时在 1.21.1 和 26.3 上成立，所以直接 `import net.minecraft.Util;` 的那 10 个文件
   （8 个在 `shared/`，`ChatImageStore` 在两个加载器层各一份，对 26.3 实际挂载的是 9 个文件）、
   15 处引用（`backgroundExecutor` 11、`getFilenameFormattedDateTime` 2、`getPlatform` 1、
   `ioPool` 1，含注释里的提及）**必须收进门面**。本仓其实已经收了一半：
   `platform/client/SystemFiles.java` 的 javadoc 写着「全仓唯一碰这一句的地方」，
   但它收的只有「打开文件夹」那一句；剩下这 15 处是直接 import 原版 `Util` 的，
   其中 `BookList.java:566` 那句 `Util.getPlatform().openFile(...)` 本来就已经绕过了门面 ——
   26.3 里 `Util.OS` 只剩 `telemetryName()`，`openFile` / `openPath` 一并没了，
   去处是 `com.mojang.blaze3d.Blaze3D.openPath(Path)`（同类还有 `openUri(URI)`）。
   顺带一句：`verifySharedIsTargetNeutral` 这次是**绿的**（354 个文件），也就是说这类
   「共用代码直接 import 一个会换包的原版类」它现在拦不住 —— 要不要给它补一条判据，
   是 1d 之后值得单独议的一件事。

**建议的形状**（不需要改任何构建脚本，等 1d 印证）：

- `extractRenderState` / 输入事件 / `resize` / 容器背景 → 下沉到 `platform/client/PhoneScreenBase`
  与一个新的 `PhoneContainerScreenBase`，各平台各写一份，`shared/` 只调自己定义的方法名。
- `GuiGraphicsExtractor` / `Identifier` 两个类型名 → 各平台一个 `Draw` facade 收口，
  `shared/` 里不再出现原版绘制类型。
- GLFW / `RenderSystem` GL 状态 → 同上，收进平台层，别漏进 `shared/`。

这件事要落定，得先把平台文件补齐、让日志干净一遍。所以下面第十节的建议里，
`until` 这个选项**暂时按「不做」处理**，等补完再看数字有没有翻。

---

## 十、附带挖出来的一道闸在重复计数

翻完开关顺手在 `1.21.1-neoforge` 上跑 `verifyPlatformTwins`，它红了，报 62 对「漂开」，
而且**每一条都是基线的 1.5 倍**：`44 → 66`、`86 → 129`、`120 → 180`。

不是代码漂了，是那道闸的分数算重了。`collect()` 早就写明「同一个文件被两个目标看到，
不算两份拷贝」，但它只在**要不要盯这一组**时按绝对路径去了重；`measure()` 算分仍按
「名义目标」逐个算。于是当一个层文件被 N 个目标挂上，同一份真实差异就被加进 N-1 次。
26.3-neoforge 一挂上 `1.20.5+` 层，那批 `feature/chat/net/*` 就从「2 份拷贝」变成
「3 份名义拷贝」，分数齐刷刷 ×1.5。

修法是把去重挪到算分之前（`gradle/mcphone-checks.gradle` 的 `measure()`：按
`canonicalPath` 收敛，每个物理文件留一个代表）。**方向是反的**：去重之后 55 对报的是
「差异变小了」，说明仓库里那份基线一直含着这份重复计数。按这道闸自己的规矩
（降了也红，要求把基线跟着调下去），已跑 `updateTwinBaseline` 重算：

```text
110 对，差异合计  5986 行（旧，含重复计数）→ 4728 行（新）
```

对数一条没变，只是那 55 对回到真实值。顺带一提，这个 bug 在只有一对目标挂同一个层时
显不出来 —— 它需要**第二个**挂同一层的目标，所以 1c 是它第一次露出来的时候。

---

## 十一、本轮的构建事实

| 项 | 值 |
|---|---|
| 提交 | `mountSharedSources = true` + `26.3-neoforge` 的 `layers` 同时翻成四层（声明与实际成对翻，理由写在那份 build.gradle 的文件头） |
| 声明闸 | 十道全绿，CI 矩阵会编的目标：`1.20.1-forge`、`1.21.1-fabric`、`1.21.1-neoforge`、`26.3-neoforge` |
| `:compileJava` | **FAILED**，1523 条错误，214 个文件，21 秒 |
| 未截断 | 骨架阶段加的 `-Xmaxerrs 100000` 生效：javac 默认在 100 条停，这次 1523 条全打出来了 |
| 工具链 | MDG 2.0.147 + NeoForge 26.3.0.3-beta + Java 25，配置阶段与 neoform 流水线全过 —— 红只在 `:compileJava`，工具链那一问仍然是绿的 |

`mcphone-checks.gradle` 挂上了，但 `:build` 这条路只到 `:compileJava` 就停，所以四道
**不依赖编译产物**的闸是单独在 26.3 上跑的，全绿：

```text
verifySharedIsTargetNeutral  shared/ 校验通过：354 个文件，禁 版本、加载器轴
verifyPlatformTwins          110 对，差异合计 4728 行（基线内）
verifyLoaderTwins            7 组（7 组各 2 份），层：forge、neoforge
verifySeamsDocCurrent        通过（26.3 那段标记早就手工加好了）
```

`assertTests`（把仓库级 `docs/**` 那批断言测试对着 `sourceSets.main` 的产物编一遍）
仍然够不着 —— 它要 `compileJava` 绿。所以「挂了 checks 会不会多引出别的红」这句话，
本轮只答了一半，剩下那一半等 §十二 第 3 步。

## 十二、下一步（1d）的顺序，按收益排

1. 补 `MCphone.java` 的 `LOGGER` / `MODID` / `getVersion()` —— 279 条当场消失。
   **做完了，实测见 §十三。**
2. 补 `platform/client/PhoneScreenBase`、`Draw`、`ModPresence`、`SystemFiles`、
   `KeyModifiers`、`EditBoxes`、`CameraGui`、`VanillaAudio`、`PlayerSkins`、`Slots`、
   `StackCodecs`、`ClientTicks`、`CompatModules` 这批**小门面**（13 个文件、约 560 行），
   它们能同时压掉 B 的大半和 E 的全部 186 条级联。
3. 重跑一次，取**干净**的直方图。这时候量出来的才是真工作量。
4. 再往下是 `PhonePlayerData` / 各 `*Networking` / `ClientConfig` 这批大的（约 1500 行），
   以及 §六 那四处硬骨头 —— 其中 Ogg 解码的去处要先查。
5. 附属模组那一桶（14 文件）等首发范围定下来再动。

一句话：**这一支现在离「能玩」还差 47 个平台文件 + 69 个真改文件，但原先估的「绘制族要
全面接渲染管线」是错的，那一族其实只是改名。**

---

## 十三、1d 第 1 步做完了：真入口替掉探针

`platforms/26.3-neoforge/.../MCphone.java` 从探针换成真入口：`MODID`、`LOGGER`、
`version` 加 `getVersion()`，构造函数 `(IEventBus, ModContainer)`。注册那一大段
【没有抄过来】—— 它引用的 12 个类在这一支一个都还没有，写上去只换来十几行
`cannot find symbol`。为什么现在不写、以及一份逐条注明「等谁」的清单，都在那个文件的
类注释里；构造形状另取过一次证（`NeoForgeMod(IEventBus, Dist, ModContainer)`、
`ClientNeoForgeMod(IEventBus, ModContainer)`、`@Mod` 的 `@Target` 仍只有 `TYPE`、
`IModInfo.getVersion()` 返回 `ArtifactVersion`）。

| | 1c | 这一步之后 |
|---|---:|---:|
| javac 错误 | 1,523 | **1,244** |
| 报错文件数 | 214 | **193** |
| 一个字没改就编过 | 208 / 422 | **229 / 422**（54.3%） |
| A 改名 | 588 | 588 |
| B 平台文件未补 | 504 | **225** |
| E 级联 | 186 | 186 |
| C 覆写 / D 附属 / F GLFW / G 形变 | 41 / 54 / 16 / 134 | 一字未动 |
| 真断小计 | 245 条 / 69 文件 | 245 条 / 69 文件 |

少的 279 条【全部落在 B 桶】，真断一条没动 —— 这正是这一步的本意：把日志洗干净，
不碰设计问题。21 个文件当场全绿（214 − 193），而 §一 那句「只要改名 + 补平台文件
就可能转绿」的文件数从 145 掉到 124，差的正是那 21 个。
闸那边：`verifyPlatformTwins` 先按设计红了（差异 277 → 257，基线没跟上），
跑 `updateTwinBaseline` 把合计从 4,728 压到 4,708，其余四道绿。

### A 桶现在拆得开了：588 条其实是【两个名字】

| 名字 | 条数 | 文件 | 其中 import 行 |
|---|---:|---:|---:|
| `ResourceLocation` → `Identifier` | 314 | 86 | 85 |
| `GuiGraphics` → `GuiGraphicsExtractor` | 244 | 55 | 55 |
| 其余 11 个改名（`ToastComponent`、`GameProfileCache` 等） | 30 | — | — |

128 个文件里这两个名字至少出现一次；**79 个文件的错误【全部】只来自这两个名字**，
共 287 条。这两个名字一解决，1,244 当场下去 588。

### 但 588 是下界：还有一族被它盖着

`GuiGraphics` 解析不成功的时候，javac 不会去报它身上的方法名。类型一到位，这一批会
当场冒出来：`drawString(` 215 处（213 处接收者写作 `g.`，另外 2 处在 `PhoneCanvas`
与 `IPhonePage` 的 javadoc 示例里）加 `drawCenteredString(` 5 处，26.3 上改叫
`text` / `centeredText`，实参个数一字未改 —— 与 §四 那条普查一致。

顺手量了一件对「要不要机械改」有决定意义的事：全仓 `drawString` / `drawCenteredString`
【没有任何自家定义】（查过，零命中），而 213 处的接收者名字写死了是 `g`。
也就是说这一族真要批量改，没有歧义地雷。

### 岔路：这两个名字怎么办，要人拍板（未决）

§九 定过「不给层加 `until`」，签名不同的东西走门面。那条路对 `GuiGraphics` 走得住
（`Draw` 收口，244 条），但对 `ResourceLocation` 走不住：它是被当成【类型】用的 ——
字段、形参、局部变量、泛型实参，86 个文件 229 处非 import 的引用。门面能包住调用，
包不住「这个类型叫什么名字」。两条路：

1. **门面收口**（§十二 原方案）：`shared/` 不再直呼这两个类型名。要动 128 个文件，
   其中 `ResourceLocation` 那一族等于把全仓的 id 类型换个说法重写。结构最干净，
   代价最大，而且 `1.21.1-neoforge` 那支跟着一起改（同一个 `shared/`）。
2. **挂载时改名**：给目标声明一张改名表（26.3 就是那两条，也许再加 `drawString→text`），
   挂载共用代码时按词边界生成一份改过名的副本。一条声明换 588 条（连被盖住的那 220 处），
   代价是引入一个仓库里没有过的机制：改的是【源码文本】而不是【文件归属】，
   而且挂载点编的东西跟 `git show` 出来的不再是同一份字。

这一步没顺手往下做，因为两条路的下一步长得不一样：走 1 要先写 `Draw`，走 2 不用。

---

## 十四、岔路拍了：走 2（挂载时按目标改名）。1,244 → 756

三样东西：

| 干什么 | 在哪 |
|---|---|
| 声明表（两族规则 + 五条不变量 + 每条规则的取证） | `versions/name-renames.json` |
| 机制（复制一份改过名的副本，验那五条） | `gradle/mcphone-renames.gradle` |
| 挂载点 | `platforms/26.3-neoforge/build.gradle` 里那一次 `mcphoneMountSharedJava(...)` |

**表里的两族规则用两种锚**：类型名走词边界任意位置（`readResourceLocation`、
`ResourceLocationCodec` 都不算命中）；方法名只在「`.` 之后、`(` 之前」命中，
所以自家的 `void drawString(` 定义与裸 token 不会被牵连。版本轴沿用
`datapack-renames.json` 那套补零比较（`since: "26"`），1.20.1 / 1.21.1 拿不到规则，
照旧原样挂载，那三支编的东西一字未变。

五条不变量都在挂载任务里当场验：规则必须命中（空转的规则红）、新名不许已在原文里
（只对类型名判 —— `.text(` 本来就有 29 处在用）、**字面量不改**、代码段改完不许还剩
旧名、挂进来的文件数必须对得上。

第二条与第三条不是纸面规矩，是建起来第一轮就撞上的：整份文本一起改的版本被
`ScriptAppFolder.java:196` 那句 `"' 不是合法的 ResourceLocation"` 拦住 ——
那是给玩家看的文案，改了一处就是一处不报错的行为变化。现在切段只改代码，
而那两处留在字面量里的旧名**每次构建报出处数**（不改，但出声）。

### 实测

| | 1d 第 1 步后 | 只改类型名 | 再加方法名 |
|---|---:|---:|---:|
| javac 错误 | 1,244 | 969 | **756** |
| 报错文件 | 193 | 129 | **118** |
| 一个字没改就编过 | 229 / 422 | 293 / 422 | **304 / 422（72.0%）** |

命中：`ResourceLocation→Identifier` 333 处 / 89 文件、`GuiGraphics→GuiGraphicsExtractor`
262 处 / 60 文件、`drawString→text` 215 处 / 38 文件、`drawCenteredString→centeredText`
5 处 / 3 文件；改动落在 136 个文件上。闸那边五道全绿（`1.21.1-neoforge` 上跑的，
那三支不挂这张表）。

剩下的 756 条：

| 桶 | 条数 | 说明 |
|---|---:|---|
| B 本平台文件没补 | 225 | 下一步那批小门面 |
| G 真断：其它 API 形变 | 205 | 见下面那条翻案 |
| E 级联（26.3 里成员仍在） | 186 | 一个 `PhoneScreenBase` 能吃掉大半 |
| D 真断：附属模组没构件 | 54 | 等产品决策 |
| C 真断：覆写签名 | 41 | 走 `PhoneScreenBase` 那条接缝 |
| A 改名（表没覆盖的那 11 个） | 29 | 逐个查过去处 |
| F 真断：GLFW | 16 | 只能重写 |

### 一条翻案：§四 那句「`pose()` 45 处不变」不够准

名字确实没变，坏的是【消费端】：`pose()` 现在返回 `Matrix3x2fStack` 而不是 `Matrix4f`。
改名之前这些一律被 `GuiGraphics` 那个 error type 盖着，量不到。类型名一到位就冒出：
`不兼容的类型: int/float 无法转换为 Matrix3x2f` **21 条**、`pushPose` **10**、
`popPose` **10**、`setColor` **8**。这一族不是改名能了结的，是矩阵栈换了类型 ——
`Draw` 门面这一族仍然要写，只是要写的东西比原先以为的多。

---

## 十五、1d 第 3 步：`PhoneScreenBase` 与六个门面。756 → 526

### `platform/client/PhoneScreenBase` —— E 桶那 186 条级联的根源

§二 说的级联就是它：共用代码里三个界面 `extends PhoneScreenBase`，而这一支还没有这个
文件，于是父类是 error type，子类从 `Screen` 继承来的一切都报「找不到符号」。

1.21.1 那一份只为 `mouseScrolled` 而立。26.x 上 `mouseScrolled(double,double,double,double)`
【签名反而没变】（`GuiEventListener` 里还是那四个 double），换掉的是【一整族派发入口】：

| 共用代码覆写的 | 26.3 原版叫什么 |
|---|---|
| `render(GuiGraphics,int,int,float)` | `extractRenderState(GuiGraphicsExtractor,int,int,float)` |
| `mouseClicked(double,double,int)` | `mouseClicked(MouseButtonEvent,boolean)` |
| `mouseReleased(double,double,int)` | `mouseReleased(MouseButtonEvent)` |
| `mouseDragged(double,double,int,double,double)` | `mouseDragged(MouseButtonEvent,double,double)` |
| `keyPressed(int,int,int)` | `keyPressed(KeyEvent)` |
| `keyReleased(int,int,int)` | `keyReleased(KeyEvent)` |
| `charTyped(char,int)` | `charTyped(CharacterEvent)` |
| `resize(Minecraft,int,int)` | `resize(int,int)` |

按 §九 定的那条：这些是【覆写】，门面改不了「一个方法被谁覆写」，所以全部收在这一层 ——
基类替子类覆写 26.x 那八个，再转调八个中立的旧形状方法。子类不必知道外面换了什么。

中立方法的默认实现不是 `return false`：那 25 处 `super.mouseClicked(...)` /
`super.keyPressed(...)` 是要真的把事件交回原版走一遍的，原版靠它做控件命中测试与焦点。
所以每次覆写进来先把事件记在字段里，默认实现拿它去调 `super.<26.x 形状>`，
`finally` 立刻擦回去 —— 派发在渲染线程上、一次点击只进一次，不会重入。
顺序也与 1.21.1 对齐：先子类逻辑，再原版派发。

`charTyped` 的修饰位是个已知的让步：26.x 的 `CharacterEvent` 只带 `codepoint` 一个字段，
所以那一支的中立形状第二个参数【恒传 0】。本仓没有一处读它，行为不变；
真要用到修饰位的那天，中立形状得换成正经的修饰位类型，而不是继续骗 0。

### 六个门面是逐字相同的副本

`ModPresence`、`Slots`、`StackCodecs`、`EditBoxes`、`KeyModifiers`、`ClientTicks`
—— 先从 1.21.1 逐字拷过来，再逐个对着 26.3 取证【不用改一行】：

- `ModList.get()` / `isLoaded(String)` / `getModContainerById(String)` 与
  `IModInfo.getDisplayName()` 都在（`loader-12.0.0.jar`，FML 12 这一版）
- `Slot.setByPlayer(ItemStack,ItemStack)` 两参重载还在 ✓（它同时还有单参的那个）
- `ItemStack.OPTIONAL_CODEC` 还在，`.fieldOf(name)` 仍返回 `MapCodec<ItemStack>` ✓
  —— 存档格式没变，这条最要紧
- `EditBox.moveCursorToEnd(boolean)` ✓
- `KeyModifier.isKeyCodeModifier(InputConstants$Key)` ✓
- `ClientTickEvent.Pre/Post` 仍在 `net.neoforged.neoforge.client.event`，
  `NeoForge.EVENT_BUS` 仍是那条游戏总线 ✓

**其中 `ModPresence` 与 `KeyModifiers` 是「该进 `layers/loader/neoforge`」的候选** ——
它们身上只有加载器轴、没有版本轴，下一个 NeoForge 目标不必再抄第三遍。
这一步没有直接搬：搬动要动层的内容与 `verifyLoaderTwins` 的口径，那属于「改 CI 看得见的
声明」，留到这里说明，等一句授权。`Slots`/`StackCodecs`/`EditBoxes`/`ClientTicks`
【不该搬】—— 它们本身就是为了版本轴而存在的（各自的类注释写着差在哪个版本），
放进禁版本轴的加载器层是放错地方。

### 数字（两个动作分开记，它们各吃掉不同的桶）

| | 756（上一步末） | 补 `PhoneScreenBase` 后 | 再补六个门面后 |
|---|---:|---:|---:|
| javac 错误 | 756 | 568 | **526** |
| 报错文件 | 118 | 118 | **109** |
| B 平台文件没补 | 225 | 213 | **175** |
| E 级联 | 186 | **27** | 27 |
| C 覆写签名 | 41 | **17** | 17 |
| G 真断·形变 | 205 | 210 | 208 |
| D 附属模组 | 54 | 58 | 56 |

E 从 186 掉到 27 是【第一个动作】干的：这一支的父类不再是 error type，那 169 条
「`font`/`width`/`height`/`minecraft`/`screen` 找不到符号」自己消失，正是 §二 的预判。
C 同时掉 24 条 —— 八个派发入口收进基类之后，子类那些 `@Override` 重新对得上了。
六个门面吃掉的是 B 的 38 条。

G 与 D 这两步里【不降反微升】（+5 与 +4，然后各回落一点）：父类一修好，原先被
error type 挡住的真断就露出来了。这不是回退，是量尺变准 —— 剩下 526 条里真断占 297。
双胞胎基线 4,708 → 4,838（新增 8 对同名文件，其中六个是逐字相同的零差异对），
接缝清单 26.3 那一段从 1 个长到 8 个。五道闸全绿。

### 剩下 526 条的形状

B 那一桶还剩 175 条。按【直接引用数】排（一个文件引一次、级联另计），
最大的一块已经收敛到少数几个：`MCphoneNetwork` 17、`PhonePlayerData` 9、
`ServerConfig` 6、`Draw` 5、`PhoneSavedData` 4、`ClientConfig` 4、
`PhoneMultiLineEditBox` 3，之后是一串 1~2 处的（`ModItems` / `ModSounds` / `ModMenus` /
`NetworkHandler` / `DiscService` / `PhoneHud` / `AppHotkeys` / `VanillaAudio` /
`PlayerSkins` / `SystemFiles` / `CameraGui` / `DiscSongs` ……）。
这一桶从今天起是【一个文件一个文件地磨】，不再有那种「补一个门面掉一百条」的台阶。

挂载源本身也从 422 长到了 **429**（`shared/` 354 + 四层 67 + 本平台 8），
所以「一个字没改就编过」现在是 **320 / 429（74.6%）**。
注意分母在动：拿这个比例跨轮次比之前，先确认分子分母是同一份挂载。

G 那一桶里新冒出来的两族值得单记：`ServerPlayer.server` 变 private（21 条）与
`Util`（21 条，§十一 说的那次换包）。这两族都得靠门面，而 `Util` 那族没法用改名表 ——
它是整类换包【且成员有增减】，`openFile` / `openPath` 的去处是 `Blaze3D.openPath(Path)`
（已验在 `Blaze3D.java:39`），要收进 `SystemFiles`。

## 十六、1d 第 4 步：把 1.21.1 的本平台文件搬过来。526 → 452

§八 那张「还缺 47 个文件」的清单，这一步按【直接引用数】从大头往下补，一次补了 28 个：
26 个从 1.21.1 逐字拷，2 个是新写的门面（`SystemFiles`、`PlayerSkins`）。
本平台目录从 8 个 java 文件长到 36 个，挂载源 429 → **457**。

### 拷完就零错误的那 9 个，是一条值得记下来的好消息

`ModItems`、`ModDataComponents`、`ModCreativeTabs`、`ModMenus`、`ServerConfig`、
`PhonePlayerData`、`ClientConfig`、`ScriptNetworking`、`TerminalNetworking`
—— 从 1.21.1 拷过来【一个字符没改】就编过了。合起来说：
**NeoForge 那一面的注册 API 从 21.1 到 26.3 没动**（`DeferredRegister` /
`DataComponentType` / `CreativeModeTab` / `AttachmentType` 的注册骨架、配置同步、
网络 payload 注册、玩家数据）。这一支后面再补 26.x 的其它目标时，这批文件不用重看第二遍。

剩下 17 个文件合计 97 条，每一条都是【本平台自己的文件】要改，不是共用代码的锅。
最贵的几个：

| 文件 | 条 | 卡在哪 |
|---|---:|---|
| `core/client/PhoneHud.java` | 29 | §六 那族渲染形变（`GuiGraphics` → `GuiGraphicsExtractor`、`pose`、`hideGui` 全中） |
| `core/client/AppHotkeys.java` | 10 | GLFW → SDL |
| `feature/camera/client/CameraFlash.java` | 10 | `getMainRenderTarget` / `getWindow` 那族换到了 renderpearl |
| `core/ModAttachments.java` | 9 | `Builder.serialize(Codec<T>)` 这个重载没了（见下） |
| `core/net/NetworkHandler.java` | 8 | `ServerPlayer.server` private 与 `displayClientMessage` |

三条这次顺手量准了根因，都不用再猜：

- `ModAttachments` 那 9 条：`javap` 打 `neoforge-26.3.0.3-beta-universal.jar` 里的
  `AttachmentType$Builder`，`serialize` 还在，但重载只剩 `serialize(IAttachmentSerializer<T>)`
  与 `serialize(MapCodec<T>)`（外加带 `Predicate` 的那个）—— 【裸 `Codec<T>` 那一档不收】。
  所以这不是「注册面变了」，是传进去的 codec 得收窄成 `MapCodec`。9 处各自看给的是哪个。
- `MCphoneClient` 只剩 2 条，但方向变了：`RegisterClientReloadListenersEvent` 这个类在 26.3
  的 universal jar 里【查无此类】，同目录下取而代之的是 `AddClientReloadListenersEvent`
  （服务端那面还有 `AddServerReloadListenersEvent`）。名字换了、语义八成也换了，
  得先看它给的是「往里 add」还是「往事件对象上 register」。
- `MCphoneNetwork` 也只剩 1 条：`PacketDistributor.sendToServer(packet)` 没了，
  那个类现在只剩七个 `sendTo*Players*` / `sendToPlayersTracking*`，全是【服务端往客户端】的方向。
  客户端往服务端在 26.3 走的是 `connection.send(...)`（`IPayloadContext` 那面是 `reply`）。
  这一条只差这一个调用，补完 `MCphoneNetwork` 就整份绿。

`CameraHandler` / `CameraFlash` 那 3 条 `getMainRenderTarget()` 也量准了：
`Minecraft` 上那个 getter 没了，26.3 的 `Minecraft.java` 自己改用
`this.gameRenderer.mainRenderTarget()`（同一份源里出现 4 次），`RenderTarget` 这个类本身还在
`com/mojang/blaze3d/pipeline/` —— 那 3 条是换个取法。
但 `CameraFlash` 不止这一档：`RenderTarget` 上【`bindWrite(boolean)` 没了】（那个类现在只剩
`resize` / `destroyBuffers` / `getColorTexture` / `getColorTextureView` / `getDepthTexture(View)`，
取到的都是 renderpearl 的 `GpuTexture(View)`），后处理那条链 `PostChain` 也从
`com/mojang/blaze3d/postprocessing/`【搬到了】`net/minecraft/client/renderer/`，
`process(float)` 的参数形状同时变了（报的是「应用到给定类型」）。
所以【相机闪光这一处是真要重写的】，不是改名 —— 它得从「绑 FBO 直接画」换成 renderpearl 的
纹理/RenderPass 说法。归到 §六 那族里，别当成低垂果实。

`PhoneHud` 里那 2 条 `window.getWindow()` 属于 F 桶那条链：`Window` 上取 GLFW 句柄的方法在
26.3 叫 `handle()`，但句柄本身是不是还发得出一个 GLFW 窗口 id，本轮没验 —— 它跟 GLFW→SDL
那 20 条是同一个问题，一起解。

### 数字

| | 526（上一步末） | 452（这一步末） |
|---|---:|---:|
| javac 错误 | 526 | **452** |
| 报错文件 | 109 | **105** |
| 挂载源 | 429 | **457** |
| 一字未改就编过 | 320（74.6%） | **352（77.0%）** |
| B 平台文件没补 | 175 | **13** |
| G 真断·形变 | 208 | **256** |
| E 级联 | 27 | **30** |
| A 改名 | 60 | 60 |
| D 附属模组 | 56 | 56 |
| F GLFW | 20 | 20 |
| C 覆写 | 17 | 17 |

B 从 175 掉到 **13**：这条台阶【到此吃完】。往后没有「补一个文件掉一百条」了。
真断小计（C+D+F+G）349 条 / 452，也就是剩下 77% 全是得逐处重写的。
G 与 E 这两步里升（+48、+3）跟 §十五 同理 —— 父类与兄弟文件一修好，原先被 error type
挡住的真断就露出来，量尺变准不是回退。

接缝清单 26.3 那一段 8 → **36** 个（共用代码引用到 26 个），双胞胎基线 4,838 → **5,904** 行 /
110 对。`updateSeamsDoc` 与 `updateTwinBaseline` 都重新生成过，五道闸全绿。

### 剩下 452 条里最大那几块（按聚合计数，取自本轮 `hist`）

| 族 | 条 | 去处 |
|---|---:|---|
| `Util` 换包 | 24 | 【这一族不用门面，一次改名就吃完，见下】 |
| `ServerPlayer.server` private | 23 | 一个小门面 |
| GLFW 一族 | 20 | `com.mojang.blaze3d.platform` 下换成了 SDL（`SDLEventHandler`） |
| `pose()` → `Matrix3x2fStack` | 49（`pushPose` 10 / `popPose` 10 / `int→Matrix3x2f` 12 / `float→` 9 / 其余 8 条 `setColor`） | `Draw` 里的 2D 变换门面；**`Renderer.java:264` 那处 `translate(x,y,200)` 的 z 在 26.x 的 2D 栈里没有对应物，得单独定** |
| `displayClientMessage` | 13 | 消息组件参数变了形状 |
| `setScreen` | 10 | 26.3 叫 `setScreenAndShow(Screen)`（已验在）；改名表的【方法】那半本来就吃这个，加上 `setScreen → setScreenAndShow` 一条就是 10 条 |
| `AbstractContainerScreen`（`renderBg` 没了、`imageWidth/imageHeight` 变 final） | 9 | 一个 `PhoneContainerScreenBase`，跟 §十五 同一个思路 |
| 附属模组（D） | 56 | 【产品决策，等一句话】首发要不要带那几个联动 |

`hideGui`（4 条）单记：`Options.hideGui` 这个字段在 26.3 全 jar 查不到，F3 那套现在叫
`Hud.isHidden()` + `toggle()`，是【状态搬了家且只有一个开关】，所以 `CameraGui` 那个
返回 boolean 的门面形状撑不住，得重新设计。

### `Util` 那 24 条：§十一 那句「成员有增减」说重了

本轮把 24 条【逐条落到源码行上】数了一遍（脚本 `ut1l1d4.py`，不是估的）：

| | 条 |
|---|---:|
| `import net.minecraft.Util;` 那一行 | 10 |
| `Util.backgroundExecutor()` | 12（其中 `PhoneScreen.java:412` 那处写的是全限定名） |
| `Util.getFilenameFormattedDateTime()` | 1 |
| `Util.getPlatform().openFile(File)` | 1 |

对着 26.3 的源逐个看：`net/minecraft/Util.java`【不存在】而 `net/minecraft/util/Util.java`
【存在】，搬家这条是真的；`backgroundExecutor()` 与 `getFilenameFormattedDateTime()`
在那份源里【原样还在】（`public static TracingExecutor backgroundExecutor()` /
`public static String getFilenameFormattedDateTime()`），`getPlatform()` 也在、返回 `Util.OS`。
唯一真没了的是【`OS` 这个枚举的成员】：26.3 里它是 `public enum OS { OS(String telemetryName) }`
—— 只剩一个 `telemetryName()`，`openFile` / `openPath` 都不在了，去处是
`com/mojang/blaze3d/Blaze3D.java` 的 `openPath(Path)` 与 `openUri(URI)`。

所以准确的说法是【23 条纯粹是包路径搬家，1 条是真断】：那 1 条就是 `BookList.java:566` 的
`Util.getPlatform().openFile(TxtLibrary.directory().toFile())`，它得改走本轮新写的 `SystemFiles`。
（另两处 `Util.ioPool()` / 第二处 `getFilenameFormattedDateTime` 出现在注释里，本来就不算错误。）

顺带把 §十一 那句话改准：当时写「整类换包【且成员有增减】」，方向对、比例错得离谱 ——
听起来像一整族要重写，实际是 10 行 import 加 13 个调用点。

所以这 24 条的根因只有一句话：【包路径从 `net.minecraft` 挪到了 `net.minecraft.util`】。
改名表现在只按【标识符】换名，而这里标识符没变、变的是它前面的包名，所以吃不下 ——
这不是要加门面，是要给改名表加【整行精确替换】这一档（`import net.minecraft.Util;` →
`import net.minecraft.util.Util;`，另外那 1 处全限定名同理）。
加完这一档就是【23 条一次清】（那 1 条真断的 `openFile` 另算），是 452 之后单位收益最大的一块；
`setScreen` 那 10 条也顺手一起 —— 它的锚点本来就合适用【方法名】那一档：报错的 10 处
全写作 `.setScreen(`，前面必定带点，`PhoneItemData.setScreenOn(ItemStack)` 那种自家定义撞不上。

`setScreen → setScreenAndShow` 这条加之前先记两个坑（本轮量的）：
仓库里另有一个【自己声明的】`setScreenOn(ItemStack)`（`core/PhoneItemData.java:105`），
改名要是按【前缀】匹配就会把它一起带走 —— 现在这套机制是按标识符整词换的，验一条即可；
另外有 3 处 `setScreen` 出现在【注释与 javadoc】里（`PhoneScreenOpener.java:23`、
`IPhonePage.java:10`、`ImmersiveEngineeringManual.java:25`，都是写给模组作者看的说明文字），
按标识符换会连注释一起换 —— 换完读起来别扭但不算错，先记下，别到时就忘了它为什么变了。
