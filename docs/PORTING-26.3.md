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

## 十七、1d 第 5 步：改名表加第三档「限定名搬家」。452 → 418

§十六 末尾说的【单位收益最大那块】这一步就做掉了，而且它顺带把改名机制的适用面补全了一块。

### 为什么必须加一档，而不是塞进现有的两张表

`net.minecraft.Util` 在 26.3 是【搬进 `net.minecraft.util` 子包】—— 标识符一个字没变，
变的是它前面的包名。而 `typeRenames` 那一档是按【标识符】换名的（`ResourceLocation` →
`Identifier`），对这种改名它【完全看不见】：把 `Util` 改成 `Util` 等于没改。
反过来把 `Util` 当标识符写进表也不行 —— 那正是这张表的 `_comment` 当初把它排除出去的理由：
`Util` 太短太通用，词边界锚不住（本仓自己就有 `GuiUtil` 这一族）。

新档叫 `packageRenames`：【整条限定名一起换】。锚点跟类型档差在两处 ——
前面除了标识符字符还禁一个点（`com.foo.net.minecraft.Util` 不算命中），
后面【只禁标识符字符、点要放过】，因为 `net.minecraft.Util.backgroundExecutor()`
这种全限定调用后面跟的就是点。大小写敏感，所以换完得到的 `net.minecraft.util.Util`
不会被同一条规则再咬一遍。这条规则本身够具体，不存在「Util 太通用」那个问题。

五条不变量一条没少，另外【新加了一条】：限定名这一族也判「新名不许已在原文」——
包里带点，锚得住，而「同一个文件里两种写法共存」正是这东西该拦的样子。
原先那条只判类型名，是因为方法名那边 `.text(` 本仓已有 29 处在用，拿同一条判会把好规则拦死。

### 两条规则的命中数（构建当场打的）

```
net.minecraft.Util→net.minecraft.util.Util 改 9 处/9 文件（字面量里留 0 处）
setScreen→setScreenAndShow 改 9 处/7 文件（字面量里留 0 处）
```

`setScreen` 是顺手补的第二条方法改名：26.3 的 `Minecraft` 上【只剩 `setScreenAndShow(Screen)`
一个】public 的开界面方法（那份源里另几处 `setScreen` 全在 `Gui` 上，不是本仓调的那个）。
本仓挂载源里 9 处调用的接收者一律是 `mc` / `minecraft` / `Minecraft.getInstance()`，
全是 `Minecraft`；而自家那个 `setScreenOn(ItemStack)`（`PhoneItemData:105`）名字不同、
方法档的锚点又要求 `(` 紧跟其后，不会被牵连 —— 这两条都取证过，写在表的 `_comment` 里。

### 一个机制边界，是这一步才撞出来的

9 处而不是 10 处、7 个文件而不是 8 个 —— 差的那些在【本平台自己的文件】里。
改名挂载只重写 `shared/` 与四个层的副本，`platforms/26.3-neoforge/src/` 下那 36 个文件
是这一支自己的，不经那道任务。所以本平台的文件得【直接写 26.x 的真名】，
这一步顺手把 `PhoneHud` 那 4 处 `setScreen` 与 `ChatNetworking` 那 1 处 `Util` 改了。

顺着这条量了一条【以前没量过的维度】：418 条按「谁的」分 ——

| | 条 |
|---|---:|
| 挂载副本（改名表管得到） | 328 |
| 本平台自己的文件（改名表管不到） | **90** |

而 90 条里【有 33 条纯粹是 A 桶】（`ResourceLocation` 25 + `GuiGraphics` 8），
落在 7 个文件上（`AppHotkeys` 10、`PhoneHud` 6、`CameraFlash` 6、`NetworkHandler` 5、
`PhoneMultiLineEditBox` 2、`ModSounds` 2、`StoreNetworking` 2）。
意思是：那 26 个文件是从 1.21.1【逐字拷】过来的，拷进来还带着老名字，
而它们不过改名那道任务 —— 这 33 条是【下一级的低垂果实】，机械换名即可。

## 十八、1d 第 6 步：本平台文件里的老名字直接换掉。418 → 392

§十七 末尾那 33 条。做法是给本平台目录下那 36 个文件跑一遍【与挂载任务同一套锚点】的改名
（脚本在 `C:\...\mcphone263probe\plat_rename.py`，一次性工具，不进仓）：
`ResourceLocation` → `Identifier`、`GuiGraphics` → `GuiGraphicsExtractor`，
限定名与 import 行一起算，方法名那两族也带上（本轮命中 0 处 —— 本平台文件里没有
`g.drawString(`，那三处都在 `shared/`，早被挂载改过了）。

当场验三条，与挂载那边对齐：【字面量一字不动】、【代码段改完不许剩旧名】、
【改完不许冒出重复 import】。最后一条是这一边特有的担心：`ResourceLocation` 与
`Identifier` 同在 `net.minecraft.resources` 包下，一个文件两边都 import 过就撞车。

预演命中 9 个文件，落盘只改了 7 个 —— 排除了 `platform/client/PhoneScreenBase.java` 与
`PlayerSkins.java`。理由不是它们干净，而是那两处命中【恰好都是拿旧名做对照的注释】：
一句是「`render(GuiGraphics,...)` 变成 `extractRenderState(GuiGraphicsExtractor,...)`」，
一句是「26.3 上叫 `Identifier`，1.21.1 上叫 `ResourceLocation`，同一个东西」。
挂载那边【注释照改】是对的（那些注释只是顺带提到一个类型名），而这两句里旧名是
【被说的那个东西】，跟着改名就把话讲反了 —— 所以这一族得手工判，不能一把推。
改动 35 行、`+35 / -35` 一字不多一字不少，确认没有整行增删。

### 数字

| | 418（上一步末） | 392（这一步末） |
|---|---:|---:|
| javac 错误 | 418 | **392** |
| 报错文件 | 105 → 97 | **95** |
| A 改名 | 60 | **27** |
| ├ 挂载副本 | 27 | 27 |
| └ 本平台自己的文件 | 33 | **0** |
| G 真断·形变 | 222 | **229** |

G 又涨 7 条，还是那句：类型一解析出来，原本被 error type 盖住的真断就露头。

### 顺手量出一条以前没有的维度：这些错是【谁的】

| | 452 | 418 | 392 |
|---|---:|---:|---:|
| 挂载副本（改名表管得到） | | 328 | 328 |
| 本平台自己的文件（改名表管不到） | | 90 | **64** |

这条比桶号更直接影响下一步怎么选：【管得到的那 328 条】里还能不能再榨出改名，
要看名字本身；【管不到的那 64 条】只能改文件。

### 改名这一族到此见底（这一步最重要的结论）

剩下那 27 条 A 桶逐个查过 26.3 的源 —— 它们【不是搬家，是整类不存在】：

| 名字 | 条 | 26.3 里的实情 |
|---|---:|---|
| `GameProfileCache` | 5 | 全 jar 没有任何 `*ProfileCache.java` |
| `ToastComponent` | 4 | `toasts/` 包只剩 `Toast` 与几个具体 toast，管 toast 的那个类没这个名 |
| `MetadataSectionSerializer` | 3 | 换成 `server/packs/metadata/MetadataSectionType`（还挪了包） |
| `InteractionResultHolder` | 3 | `world/` 下只剩 `InteractionResult` 与 `InteractionHand`，那个 holder 没了 |
| `ItemProperties` / `ClampedItemPropertyFunction` | 5 | 全 jar 没有任何 `*ItemPropert*.java` |
| `BakedModel` | 2 | `client/resources/model/` 下只剩 `UnbakedModel`，`BakedModel` 整个不在这个名下了 |
| `PlayerFaceRenderer` | 2 | 换名 `PlayerFaceExtractor` —— 这一条【是】改名，但方法形状也变了（`draw` → `extractRenderState`，收的是 `PlayerSkin`/`Identifier`），改名表换了名照样编不过 |
| `Tesselator` / `BufferUploader` / `VertexFormat` | 3 | `blaze3d.vertex` 里没了；`VertexFormat` 挪到 `com.mojang.renderpearl.api.vertex`，另两个是渲染栈整个换掉 |

所以【改名表这条路到此为止】。它到这一步为止的命中数（都是构建当场打的，不是估的）：

| 规则 | 挂载副本里改 | 本平台文件里直接改 |
|---|---:|---:|
| `ResourceLocation` → `Identifier` | 333 处 / 89 文件 | 25 处 |
| `GuiGraphics` → `GuiGraphicsExtractor` | 262 处 / 60 文件 | 11 处 |
| `drawString` → `text` | 215 处 / 38 文件 | 0 |
| `drawCenteredString` → `centeredText` | 5 处 / 3 文件 | 0 |
| `net.minecraft.Util` → `net.minecraft.util.Util` | 9 处 / 9 文件 | 1 处（`ChatNetworking`） |
| `setScreen` → `setScreenAndShow` | 9 处 / 7 文件 | 4 处（`PhoneHud`） |

要说清一句：1,523 → 392 这一千多条【不能都记在改名头上】，`PhoneScreenBase`、
六个门面、搬过来的 26 个平台文件各吃了自己那一块（§十三 ~ §十六 分开记的）。
改名的账只到它该到的地方 —— 而它现在【没有下一个名字可换了】：剩下的 27 条 A 桶
全是「整类不存在」。往后 392 条只能一条一条改代码，这也是 §十二 排的那几族真断
（`ServerPlayer.server` 23、`pose` 49、GLFW→SDL 20、容器界面 9、附属模组 56）
从下一步起变成主干的原因。

## 十九、1d 第 7 步：`ServerPlayer.server` 一族清零。392 → 369

§十六 起就挂着的那 23 条。26.3 把 `ServerPlayer` 的 `server` 字段改成了
`private final MinecraftServer server`（对着 26.3 的源验在），而 NeoForge 26.3 的
`IPlayerExtension` / `IEntityExtension` 上【没有】补一个 `getServer()` —— 两条都 `javap` 过，
所以「加载器给补了个 getter」这条岔路是关的。

### 没有新建接缝，因为替换式在四个目标上都在

本来按 §九 的规矩，这种成员换了可见性的要走一个每平台接缝（`ServerPlayers.serverOf(p)`）。
但这一族不用：`p.level().getServer()` 在【四个目标上都编得过】——

- 26.3：`ServerPlayer.level()` 协变返回 `ServerLevel`，而 `ServerLevel.getServer()`
  是 `public MinecraftServer getServer()`（非空）；`Level.getServer()` 也还在，返回 `@Nullable`。
- 1.21.1：`neoforge-21.1.248-sources.jar` 里 `Level` 与 `ServerLevel` 上都有
  `public MinecraftServer getServer()` —— 这条是对着源验的，不是推的。
- 1.20.1：本地 `:compileJava` 真编过（见下）。

所以改的是【共用代码本身】：`self.server` / `sender.server` / `player.server`
一律换成 `X.level().getServer()`，23 处落在 6 个文件（`ChatService` 15、`PhoneChat` 3、
`ChatNetworking` 2、`ChatDelivery` / `FriendGuard` / `TeleportService` 各 1）。
只认 javac 点到的那三种接收者写法 —— 本仓自己也有叫 `server` 的字段与局部量
（`MinecraftServer` 类型的那些），它们在 26.3 上【没问题】，跟着改正好会改坏。

省下一个接缝的代价要说清：`Level.getServer()` 是 `@Nullable`，所以这条式子在类型上比
原来的 `p.server` 松一档。但 `ServerPlayer` 只在服务端存在，它的 `level()` 永远是
`ServerLevel`，拿到的永远是同一个非空实例 —— 这里不是「大概不会 null」，是那条字段本来就是它。

### 数字与验证

| | 392（上一步末） | 369（这一步末） |
|---|---:|---:|
| javac 错误 | 392 | **369** |
| 报错文件 | 95 | **93** |
| G 真断·形变 | 229 | **206** |
| 真断小计 | 322 | **299** |

G 一次掉 23 且【没有新的冒出来】—— 这是这几轮里少见的干净一刀，
因为这一族原先就是纯可见性问题，不是父类没解析出来那种会连累一片的形状。

因为动的是 `shared/`，四支【全部本地重编】：26.3 掉到 369，
`1.21.1-neoforge` / `1.21.1-fabric` / `1.20.1-forge` 三支仍 `BUILD SUCCESSFUL` ——
最后那支是这条替换式能不能用的真正裁判，它编过了。

## 二十、1d 第 8 步：`pose` 那一族收进 `Transforms` 接缝。369 → 324

§十六 排下来的那一族这一步吃掉了 45 条。它【不能走改名表】，理由是形状不一致：
`pushPose` → `pushMatrix` 是改名；`translate(x, y, 0)` → `translate(x, y)` 是【少一个参数】；
`pose().last().pose()` 是【多出来一层】。一张按标识符换名的表吃不下三种改形，
而按 §九 那条判据（两支写出来没有一行是一样的）这就该收在一个每平台接缝上：
新文件 `platform/client/Transforms`，四支各一份，七个静态方法
`push` / `pop` / `translate` / `scale` / `translateAboveItemModel` / `mapX` / `mapY`。

### 26.x 那三支的差异是真的，不是起个新名字

对着 26.3 的源与 `joml-1.10.9` 量过的三条事实：

- `GuiGraphicsExtractor.pose()` 返回 `org.joml.Matrix3x2fStack`，压弹叫
  `pushMatrix()` / `popMatrix()`，`translate` 与 `scale`【只收两个 float】。
- 【整个栈没有 z 这一维】。老平台上 `translate(x, y, 0)` 那个 0 是「不抬深度」，
  丢掉它语义一模一样；`scale(sx, sy, 1)` 那个 1 同理。
- `mapX` / `mapY` 这两支根本不是同一句话：1.21.1 走 `pose().last().pose()` 摊出来的
  那张 `Matrix4f` 再 `transformPosition(x, y, 0, Vector3f)`；26.x 的栈【本身】就是当前那张
  `Matrix3x2f`（`Matrix3x2fStack extends Matrix3x2f`），少的那个参数不是丢了的语义。

### 那个 `z = 200`：这一支靠提交顺序，而且【还没在游戏里复核】

`Renderer.drawItemCount` 写的是 `translate(x, y, 200)`，为的是把数量角标抬到物品模型
（画在 z≈150）之上。26.x 没有那一维，挡不挡只能落在【提交顺序】上 —— 而调用点本来就是
先画物品、紧接着画数字。所以这一支的 `translateAboveItemModel` 收成普通平移。

**这条推断没验过**：角标到底会不会被物品模型压住，得开起来看一眼。它写在接缝的类注释里，
不是在代码里偷偷假设掉的。

### 45 条是怎么数的（两条日志逐条对过，不是估的）

| 消失的错误 | 条 |
|---|---:|
| `找不到符号` · `pushPose` | 11 |
| `找不到符号` · `popPose` | 11 |
| `找不到符号` · `last` | 1（`BrowserScreen:224` 那处【仍在】，见下） |
| `int无法转换为Matrix3x2f`（`translate` 三个参数） | 12 |
| `float无法转换为Matrix3x2f`（`scale` 三个参数） | 9 |
| `从double转换到float可能会有损失` | 1 |
| 合计 | **45** |

前 23 条按 `符号:` 那一行分：`pushPose` 11、`popPose` 11、`last` 由 2 条掉到 1 条。
后 22 条是类型转换那一族，我把日志里这 22 条的【javac 回显源码行】逐条看过：
【22 条全部落在含 `.pose().` 的行上】，按调用名分是 `translate` 12、`scale` 10；
按消息分是 `int无法转换为Matrix3x2f` 12、`float无法转换为Matrix3x2f` 9、
`从double转换到float可能会有损失` 1。两个切法乘起来正好是这 22 条，没有一条是别的族混进来的。

也就是说 45 = 23 + 22，一步不差；而 `setColor` 那 8 条（§十六 表里跟这族记在一起）
这一步【一条没动】，它得单独一刀。

### 改了哪些调用点

脚本切的，不是手抄的：按【括号深度】配平再切参数，不靠正则猜逗号，所以
`translate(x + (NAV_BTN_W - gw) / 2f, y + (h - gh) / 2f, 0)` 这种参数里带括号也切得对。
它只认三种形状：第三参数是字面量 `0`（几种写法都算）→ `translate`；是 `200` →
`translateAboveItemModel`；`scale` 的第三参数是 `1`。【对不上就停下来报出来】，
这一轮停下来的只有接缝自己的那份文件（它的 `translate` 只有两个参数，本来就该跳过）。

- 共用代码 `shared/`：**41 处**，落在 9 个文件：`GuiUtil` 8、`PhoneScreen` 5、
  `Renderer` 4、`BrowserScreen` 4、`CameraStamp` 4、`ClockPage` 4、`PatchouliSource` 4、
  `PhoneHudEditor` 4、`WeatherPage` 4。加上下面那 3 处本平台调用点，脚本报的 44 处正好对上。
- 本平台文件 `PhoneMultiLineEditBox`：四支【各 3 处】。26.3 那 3 处本来就是 3 条错误
  （本平台文件不过改名挂载，老名字得直接写），另三支是被接缝顺手带过去的，见下。
- 手工两处：`GuiUtil.enableScissor` 的投影改成 `Transforms.mapX` / `mapY` 各取两回
  （两头取整方向那段注释原样留着，那是业务不是形状）；`BrowserScreen:224` 那句
  `g.pose().last().pose()` 【不动】—— 它喂的是 `Draw.texturedQuad(Matrix4f, …)`，
  那一路卡在 renderpearl 的顶点上传上（`Tesselator` / `BufferUploader` / `VertexFormat`
  在 26.x 整个没了），单收在这一族里只会把两件事搅在一起。

### 一次「只给新目标打补丁」被棘轮拦下来

第一刀我只改了 26.3 那一份 `PhoneMultiLineEditBox`。`updateTwinBaseline` 当场把它的差异
从 **13 行写成 20 行** —— 而 `verifyPlatformTwins` 的判据是【只许降不许升】，
`worse` 那一档是直接抛异常的（`gradle/mcphone-checks.gradle:1062`、`:1073`）。
换句话说：同一份文件的同一个绘制块，26.3 走接缝、另三支还留着 `g.pose().pushPose()`，
四支都编得过，编译器不会说话，只有这道闸会红。

修法不是把基线抬上去，是把另三支的【同一个块】也收进接缝 —— 于是那 3 行在四份拷贝里
重新逐字相同，差异降回 **13**（顺带把 `GuiGraphics` / `GuiGraphicsExtractor` 那个类型名
从注释里摘掉，注释不进比对，纯为读起来一致）。`Transforms` 自己作为一对新双胞胎记进基线，
差 32 行 —— 那 32 行就是 §上面那小节列的真差异，是有意的接缝，不是漂移。

### 数字

| | 369（上一步末） | 324（这一步末） |
|---|---:|---:|
| javac 错误 | 369 | **324** |
| 报错文件 | 93 | **91** |
| G 真断·形变 | 206 | **161** |
| 真断小计（C+D+F+G） | 299 | **254** |
| 挂载副本 / 本平台自己的文件 | 307 / 62 | **265 / 59** |

其余六桶【一条没动】：D 56、E 30、A 27、F 20、C 17、B 13 —— 加上新掉的 G 45，
两张表都是 369 与 324，闭合。「挂载 / 本平台」那一行是按错误路径落在
`build/generated/name-renames/` 还是 `platforms/26.3-neoforge/src/` 数的，
两轮用同一个脚本量，所以这两格可以直接相减。

四支【全部本地重编】：26.3 到 324，`1.20.1-forge` / `1.21.1-neoforge` / `1.21.1-fabric`
仍 `BUILD SUCCESSFUL`。五道闸全绿：`verifyPlatformTwins` 111 对、差异合计 6,004 行（基线内，
上一轮是 110 对 / 5,972 行），`verifyLoaderTwins` 7 组，`verifySharedIsTargetNeutral`
与 `verifySharedThirdPartyImports` 各 354 个文件，`verifySeamsDocCurrent` 通过 ——
接缝清单四段各 +1（34→35、74→75、36→37、26→27），多的那一个都是 `platform.client.Transforms`。

### 剩下 324 条里下一批（都是本轮聚合的实数）

| 族 | 条 | 说法 |
|---|---:|---|
| GLFW → SDL | 20 | `GLFW` 15 + `程序包 org.lwjgl.glfw 不存在` 5。`Window.handle()` 那条 §十六 记过，整族要对着 SDL 重写 |
| `displayClientMessage` | 13 | 消息组件参数换了形状 |
| 容器界面 | 9 | `imageWidth` 3 + `imageHeight` 3（变 `protected final`，"无法为 final 变量分配值"）+ `renderBg` 覆写 3 → 一个 `PhoneContainerScreenBase`，§十五 同一思路 |
| 鼠标键盘事件 | 9 | `AbstractWidget.mouseClicked` 3、`EditBox.keyPressed` 3、`EditBox.charTyped` 3，全是"应用到给定类型"即签名变了，本轮聚合里刚冒到前面 |
| `setColor` | 8 | 26.x 的 `GuiGraphicsExtractor` 上【没有】`setColor`，走 `AbstractWidget.setAlpha(float)` + 每次绘制传 `ARGB.white(alpha)` |
| `ModAttachments` | 9 | `Builder.serialize` 只剩 `IAttachmentSerializer<T>` / `MapCodec<T>`，裸 `Codec<T>` 不收 |
| `hideGui` | 4 | `Options.hideGui` 整字段没了，`CameraGui` 那个返回 boolean 的门面形状撑不住，得重设计 |
| renderpearl 顶点上传 | 一坨 | `Draw.texturedQuad` 那条链（含 `BrowserScreen:224` 那个 `last()`），`Tesselator` / `BufferUploader` / `VertexFormat` 在 26.x 查无此类 |
| 附属模组（D） | 56 | 【还是那句话，等一个产品决策】首发要不要带那几个联动：Curios / Patchouli / MCEF / RefinedStorage 在 26.3 一个构件都没有 |

## 廿一、这一段 CI【一条都没跑】：PR 脏了，不是 26.3 编不过

这一步不是编译问题，值得单独记，因为它长得非常像编译问题。

`port/26.3` 推到 `39adf00` 之后，本地编出来 452 → 418 → 392 → 369 一路在降，
但 GitHub 上看着始终"构建不成功"。真实情况是：**从 run 12 之后一条 workflow run 都没创建过**。

### 症状 → 根因

| | |
|---|---|
| 症状 | 推了两个提交，`commits/<sha>/check-runs` 回来 `total_count: 0`；`actions/runs` 最新一条还是上一天的 run 12 |
| 排除"没启用" | `actions/permissions` 是 `enabled: true` / `allowed_actions: all`；`actions/workflows` 三个都是 `active` |
| 排除触发条件 | `build.yml` 是 `pull_request: branches: [main]`，PR #1 的 base 就是 `main`，run 6~12 都是这么来的 |
| 根因 | `pulls/1` 是 `mergeable=false` / `mergeable_state=dirty` —— PR 与 base 冲突了，GitHub 对脏 PR 不建 `pull_request` run |

冲突的来路量清楚了：`main` 在 2026-09-19 15:10:07Z（本地 23:10）同步进上游 23 个提交，
那是一次 `push` 事件的 run 13，结论 `success`。而 run 12 是 14:58:17Z 建的 ——
**只差 12 分钟**，之后这个分支上的每次推送都不会再有 CI。所以"自动化构建不成功"这个观感
其实混着两件事：run 12 及以前是【真的红】（26.3 编不过），那之后是【压根没跑】。

### 顺带记一条：这类"静默不跑"只能靠数 run 的创建时间发现

`check-runs` 返回空数组的时候，看不出是"排队中"还是"不会跑"。判据只有两条：
`.total_count` 是不是 0，以及 `pulls/1` 的 `mergeable_state` 是不是 `dirty`。
下次再看到"推了没反应"，先看这两样，别看编译日志。

### 合并这一刀的实际形状

`git merge-tree` 读过再动的：23 个提交带进 4,973 行共用代码（economy 那一整套：
`EconomyData` 603 行、`CurrencyGateway` 228 行、`TxnLog`、`GatedCurrencyProvider` 等），
**真冲突只有 `versions/platform-twins.json` 一个文件**，而它是 `updateTwinBaseline` 的生成物 ——
取一份当底、按合并后的树重算就完了，不用手工调解任何一行代码。

重算出来：110 → **111 对**，差异合计 5,972（main 侧）/ 6,004（我们侧）→ **5,960**，
比两边都低。三格值得说的是 `MCphone.java` 257 → **279**（+22）：上游给另三支接上了
`EconomyRuntime.start/stop/tick` 与 `EconomyCommand.register`，而 26.3 那一份
`MCphone.java` 本来就是【登记清单全空的骨架】（它自己的 javadoc 里记着那六条要还的账），
不是漏改一处。这一格涨的是既有欠账的口径，不是这次引入的漂移。

合并后本地重编四支：`1.20.1-forge` / `1.21.1-neoforge` / `1.21.1-fabric` 仍
`BUILD SUCCESSFUL`；26.3 从 324 涨到 **369**（+45，全在 G 桶：161 → 206），
其中 42 条在上游新增的 economy 文件里。推上去之后 `mergeable=true`，run 14 起来了，
另三支在 CI 上 `success`，26.3 那条 job（id 105932515594）红，日志最后一行是
**`369 errors`** —— 与本地对同一提交量到的 369【一字不差】，这条线上第一次两边对得上。
顺手记一句取日志的姿势：`gh run view --job <checkRunId> --log-failed`，
`checkRunId` 从 `commits/<sha>/check-runs` 里拿，别用 `--run`（这个 flag 不存在）。

## 廿二、1d 第 9 步：`CompoundTag` 一族收进 `Nbt` 接缝。369（合并后） → 331

先把两个 369 分清：§十九 末的 369 是【合并前】的，§廿一 末的 369 是【合并后】的，
数字撞上了但内容不一样（合并后那 45 条新增全在 G 桶：161 → 206）。这一节往后走的是合并后那条线。

合并带进来的 45 条里 42 条在上游新写的 economy 文件里，其中 `EconomyData.java` 一个文件 38 条，
根子全是一句话：**26.x 把 `CompoundTag` 的取值口换成了 `Optional`**。

### 看着最顺的那个改法是错的

`getLong(k)` 返 `Optional<Long>`，那把 `contains(k, Tag.TAG_LONG)` 换成
`getLong(k).isPresent()` 不就完了？——不行，两支的严格程度不是一回事：

| | 判"这个键上是 long" | 取值 |
|---|---|---|
| 1.21.1 | `contains(k, 4)` 就是 `getTagType(k) == 4`，**精确相等**（`99` 那个"任意数值"是另一档，代码里没人用） | `getLong(k)` 反而**会换算**：它内部是 `contains(k, 99)` 再 `((NumericTag) …).getAsLong()`，所以 `IntTag` 也给得出数 |
| 26.3 | `getLong(k)` = `getOptional(k).flatMap(Tag::asLong)`，`NumericTag.asLong()` 对六种数值标签一律给得出 | `getString(k)` 更松：不是 `StringTag` 的标签会走默认那条 `asString()`，一个 `IntTag` 给出 `Optional.of("5")` |

拿 `isPresent()` 当判型，意思就是【坏存档被当成好存档收进来】，而这一族读的是余额和托管。
所以判型必须照旧走"类型正好是它"，也就是 `get(k) instanceof LongTag` 那种写法。

### 接缝的底下两支其实是同一句话

`CompoundTag.get(String)` 和 `net.minecraft.nbt` 下那几个 `*Tag` 类，四个目标上都在、也没改名
（26.3 的源里 16 个 `*Tag.java` 全在；`LongTag`、`IntTag`、`StringTag`、`ByteTag`、`ListTag`
都还是那个包那个名）。**真两样的只有取值那一步**：

- 1.21.1：`NumericTag` 还是个抽象类，方法是 `getAsLong()` / `getAsInt()` / `getAsByte()`，
  `StringTag.getAsString()`。
- 26.3：`LongTag` 已经是 `public record LongTag(long value)`，`NumericTag` 变成 sealed 接口，
  访问器是 `value()` 与 `longValue()` / `intValue()` —— `getAsLong()` 【没有了】。

两支在取值上没有一个共同拼法，这才立的这层 `platform/Nbt`。十四个静态方法里
六个 `isXxx` 两支写得一模一样，留在里面是为了让调用点读起来"判一句取一句"成对，
不是为了绕一圈 —— 这条判断我写进类的 javadoc 了，不是悄悄做的。

### 一句要单独代的账：`getList(key, Tag.TAG_COMPOUND)`

`EconomyData.java:455` 靠这句筛"列表里混进了不是表的条目"：1.21.1 的实现是看列表
【声明的】元素类型，非空而不合就返回新的空列表；26.3 只剩 `getList(key)`，什么也筛不掉。
接缝在 26.3 这一支改成【逐条看】，因为 `ListTag.getElementType()` 在这支
【不再是 public】（把 26.3 那份 `ListTag` 的公开方法整个扫过一遍，只剩
`getId()` / `getType()` / `getCompoundOrEmpty(int)` 这一套）。正常列表上两种看法等价，
不一样的那种正好就是要拦的那种，逐条看反而拦得准。

`getAllKeys() → keySet()` 本来是可以丢进改名表里解决的（它就是"标识符没变、方法换了个字"的形状），
没收进表里：它和同族其余三十多句是一对，一条在改名表、其余在接缝，读代码的人得两头跑。

### 调用点

`EconomyData.java` 里 41 处，脚本按【接收者白名单 + 括号配平】切的，`list.getCompound(i)`
（`ListTag` 上的）排在 `X.getCompound(k)`（`CompoundTag` 上的）之前先替掉，不然两条会撞：

| 老写法 | 新写法 | 处 |
|---|---|---:|
| `X.contains(K, Tag.TAG_LONG)` | `Nbt.isLong(X, K)` | 9 |
| `X.contains(K, TAG_INT / STRING / COMPOUND / LIST / BYTE)` | `Nbt.isInt / isString / isCompound / isList / isBoolean` | 6 |
| `X.getLong(K)` | `Nbt.longOf(X, K)` | 11 |
| `X.getString(K)` | `Nbt.stringOf(X, K)` | 7 |
| `X.getInt(K)` / `getBoolean(K)` / `getCompound(K)` | `intOf` / `booleanOf` / `compoundOf` | 1 / 1 / 2 |
| `X.getList(K, Tag.TAG_COMPOUND)` | `Nbt.compoundListOf(X, K)` | 1 |
| `X.getAllKeys()` | `Nbt.keys(X)` | 2 |
| `list.getCompound(i)` | `Nbt.compoundAt(list, i)` | 1 |

改完当场验三条：全文件不再出现 `Tag.TAG_`、`.getAllKeys(`、`.getList(`；
`Nbt.isList(` 确实在（防的是"正则没命中也算改完了"那种假成功）。
1-参数的 `contains(K)` 那四处【一处没动】—— 它在四个目标上都还在。

### 数字

| | 369（合并后末） | 331（这一步末） |
|---|---:|---:|
| javac 错误 | 369 | **331** |
| 报错文件 | 94 | **93** |
| G 真断·形变 | 206 | **168** |
| 真断小计（C+D+F+G） | 299 | **261** |
| 挂载副本 / 本平台自己的文件 | 304 / 65 | **266 / 65** |

-38 与 `EconomyData.java` 那个文件的错误数【正好相等】，所以 G 从 206 掉到 168 是一整族清完、
没有留下级联；本平台那 65 条一条没动（这一刀全在共用代码里），这也是挂载/本平台两格能对得上的原因。
其余五桶 D 56、E 30、A 27、F 20、C 17 与合并后一样。

接缝清单四段各 +1（35→36、75→76、37→38、27→28），多出来的都是 `platform.Nbt`；
双胞胎基线 111 → **112 对**、5,960 → **5,978 行**，新记的那一对是
`platform/Nbt.java` 差 18 行 —— 与 `Transforms` 同一形状：三支老平台逐字相同一份，26.3 一份。

### 行为没变这件事是怎么验的

`docs/EconomyDataTest.java` 那 49 个用例是这一族的真裁判，但它在本机跑不完：
第 12 个 `unreadableIsNotAbsent` 用 `Files.setPosixFilePermissions`，Windows 上直接抛
`UnsupportedOperationException`。改【之前】我先跑了一遍拿到基线：

- `assertTestCurrencyTest` 绿；`assertTestEconomyDataTest` 红在 `EconomyDataTest.java:929`。
- 改【之后】再跑：红在**同一行**，前 11 个用例照样全过 —— 而排在它前面的
  `roundTrip` / `missingAndUnknownFields` / `strictLoad` / `snapshotAtomicity` /
  `snapshotPreference` 正是走这条 NBT 校验路的。

这只能算【一半】证据：剩下 38 个用例排在第 12 个之后，本机压根没跑到。
补齐的办法是让 Linux 跑，也就是推上去看 CI —— `1.20.1-forge` / `1.21.1-neoforge` /
`1.21.1-fabric` 三支的 `check` 里含这套断言测试，绿了就说明接缝在老两支上【一字未改语义】。
另外 `1.20.1-forge` 本地 `:compileJava` 真编过（24s，不是 UP-TO-DATE），这一条把
"老平台那份接缝的 `getAsLong` / `getAsString` / `getAsByte` / `getAllKeys` /
`getCompound(int)` 在 1.20.1 上也都在"钉住了，不是从 1.21.1 推的。

### CI 这一轮把上面两句都裁完了（`dbf2e5e`）

| job | 结论 | 说明 |
|---|---|---|
| `1.20.1-forge` / `1.21.1-neoforge` / `1.21.1-fabric` | `success` | 走的是 `gradlew build` → `check` → `assertTests`，也就是那 49 个用例在 Linux 上【全部跑完并且过了】 |
| `26.3-neoforge` | `failure`，日志两处 `331 errors` | 与本地同一提交量到的 331【一字不差】 |

顺手在 CI 日志上核了两条负证据：`EconomyData.java:<行>: error` 出现 **0 次**，
`platform/Nbt` 相关报错出现 **0 次** —— 新接缝自己在 26.3 上干净，那 38 条确实是整族清掉的。
老三支的 49 个用例全过，也就是"接缝在老两支上一字未改语义"这件事现在是【被机器证过的】，
不再是本机那 11 个用例的半截证据。

## 廿三、1d 第 10 步：`displayClientMessage` 收进 `ClientMessages`。331 → 318

这一族的形状与 §二十、§廿二 都不同：不是改名、也不是换返回类型，是【整条路拆了】。
对着 26.3 的源搜过一圈：`Entity.java`、`Player.java`、`LocalPlayer.java`、`ChatListener.java`
四个文件里都没有 `displayClientMessage` 这个名字。

### 拆完之后新路长这样

| | `Player#displayClientMessage` | 谁覆写了它 |
|---|---|---|
| 1.21.1 | 还在 | `ServerPlayer` → `sendSystemMessage(msg, actionBar)` 发封包；`LocalPlayer` → `chatListener().handleSystemMessage(msg, actionBar)`，第二参数是 `isOverlay` |
| 26.3 | 【整个没了】 | `ServerPlayer.sendSystemMessage(msg, overlay)` 原样还在；`LocalPlayer` 那半得自己叫 `Gui.chatListener()` 上的两个方法 |

下面讲的是 `LocalPlayer` 那一半，服务端那一半两支同写法、没什么可讲。

要紧的是第二个参数换了意思：26.x 那个 `remote` 说的是「这条算服务端来的系统消息」，
`true` 会【多落一条日志】，`false` 走 `addClientSystemMessage` 且【不记日志】。
老那句非动作栏那一支正是 `addMessage` 加一条 `logSystemMessage`，所以要对上就得传 `true`。
传 `false` 看着更像"本地产生的消息"，但那是少了一件事的另一条路 —— 接缝里传 `true`
是为了行为一致，不是为了语义好听。

还有一处只可能更保守、但确实对不上：26.x 的 `handleSystemMessage` 外面多套了
`receiver.chatAbilities().canReceiveSystemMessages()` 与 `isFriendOnlyRestricted(uuid)` 两道闸。
它们只会让消息【少显示】不会多显示，而且那正是游戏自己处理系统消息走的路，
跟着它走比自创一条绕过社交过滤的路要老实。

### 这一句本来就有两侧，而我差点把服务端那侧写丢了

第一版注释里我写的是"`Player#displayClientMessage` 是空方法体，所以拿 `ServerPlayer`
调这一句什么都不发生"。**这句是错的**，而且是会咬人的那种：空方法体只在 `Player` 上，
两个子类各自覆写过。对着 1.21.1 的源逐个看：

- `ServerPlayer` 覆写成 `sendSystemMessage(msg, actionBar)`，发一个
  `ClientboundSystemChatPacket` 给那个玩家 —— 真会把字儿送到屏幕上。
- `LocalPlayer` 覆写成 `chatListener().handleSystemMessage(msg, actionBar)` —— 直接往本地上画。

本仓 14 个调用点里【10 处走的是服务端那侧】（`TeleportService`、`TerminalOpener` 两处、
`NetworkHandler` 两处、`ChatNetworking`、`MusicNetworking`、`NotesNetworking`、
`StoreNetworking` 两处，接收者都声明成 `ServerPlayer`），只有 4 处是本地玩家
（`PhoneScreen`、`ChatConversation`、`ChatImageSender`、`CameraHandler`，都是
`Minecraft.getInstance().player`）。我那道 `instanceof LocalPlayer` 要是照原样留下，
26.3 上这 10 处提示会【静默消失】：编译照过、`verifyDistIsolation` 照绿、
断言测试没有一个碰得着 —— 因为是运行时少发一个包。

修法是把两条分支都接上。好消息是服务端那半在 26.3【根本没动】：
`ServerPlayer.sendSystemMessage(Component, boolean overlay)` 还在（`ServerPlayer.java:1927`），
第二参数还叫 `overlay`、还发同一个包。所以：

| 接收者是 | 1.20.1 / 1.21.1 | 26.3 |
|---|---|---|
| `ServerPlayer` | `p.displayClientMessage(...)` → 覆写发封包 | `sp.sendSystemMessage(msg, actionBar)`，原样 |
| `LocalPlayer` | 同上 → 覆写走 `chatListener()` | `handleOverlay(msg)` / `handleSystemMessage(msg, true)` |
| 其它（`RemoteClientPlayer`） | 拿 `Player` 的空方法体，什么都不发生 | 两个分支都不落，同样什么都不发生 |

真正没了的只有 `LocalPlayer` 那一侧的入口，而那一侧才需要处理
"`isOverlay` 换成了 `remote`" 这件事（`remote=true` 会多落一条日志，
`false` 走 `addClientSystemMessage` 且不记日志 —— 老那句非动作栏那一支是
`addMessage` 加一条 `logSystemMessage`，所以传 `true` 才是对上行为）。

### 第二次踩同一个棘轮

这一族总共有 40 处调用点：共用代码 6 处，四支本平台合计 34 处。第一遍我的脚本只扫了
`shared/`、`layers/` 与 26.3 那一路，结果 `updateTwinBaseline` 一口气把六个 `net` 文件的差异
推高：`NetworkHandler` 205→212、`StoreNetworking` 86→93、`CameraHandler` 85→88、
`ChatNetworking` 284→287、`MusicNetworking` 103→106、`NotesNetworking` 125→128。
同一句玩家反馈，26.3 走接缝、另三支留着老写法 —— 四支各编各的，编译器一个字都不会说。
把 `ROOTS` 扩到四支、补掉剩下 26 处之后，这六个文件全部回到原基线。

这是【第二次】同一形状的坑（§二十 那次是 `PhoneMultiLineEditBox` 13→20），
这次是六个文件一起涨。

剩下一格降不回去，得说明白：`Ae2Integration` 68 → **70**。查过原因，不是语义漂了：
那句"终端没电"的提示【只有 1.20.1 这一份写了】，另两份在同一个位置压根没有这句，
于是 `import com.november.mcphone.platform.client.ClientMessages;` 这一行只出现在一份里，
一对算一遍、两对就是 +2。我用多重集逐行数过确认了这一点（改动前那份独有 1 行，改动后独有 2 行，
多的正是 import）。真正那条提示的不对称是【上游既有】的，这一刀没碰它，
也没顺手替 1.21.1 补一句它今天没有的提示。

### 数字

| | 331（上一步末） | 318（这一步末） |
|---|---:|---:|
| javac 错误 | 331 | **318** |
| 报错文件 | 93 | **87** |
| G 真断·形变 | 168 | **155** |
| 真断小计（C+D+F+G） | 261 | **248** |
| 挂载副本 / 本平台自己的文件 | 266 / 65 | **260 / 58** |

-13 拆成挂载 -6（共用代码那 6 处）与本平台 -7（26.3 那 8 处调用点里有 7 处此前是独立错误，
第 8 处 `CameraHandler:33` 本来是被别处压住的级联）。文件一次少 6 个。
其余五桶 D 56、E 30、A 27、F 20、C 17 一条没动。

四支本地重编：`1.20.1-forge`（真编 23s）/ `1.21.1-neoforge` / `1.21.1-fabric` 均
`BUILD SUCCESSFUL`；`assertTests --continue` 摊平跑全套 48 个任务，46 绿、
红的还是改动前就红的 `EconomyDataTest`（POSIX）与 `ScriptEngineTest`（zh-CN locale）那两个，
没有新增。八道闸全绿：112 → **113 对**、差异合计 5,978 → **5,995 行**（都在基线内），
接缝清单四段各 +1（36→37、76→77、38→39、28→29），多的那个都是 `platform.client.ClientMessages`。
新记的那一对差 **15 行** —— 就是上面那张表的真实形状：26.3 那一份要多三个 import
（`Minecraft`、`ChatListener`、`LocalPlayer`）与两条分支，老那三份一个方法体就完了。


## 廿四、附属联动真正缺 jar 的只有 13 条：先量"摘文件"，再按判据收 Curios。318 → 313

### 一、先把"把联动文件从 26.3 摘掉"这条路的代价量实了

`build.gradle` 里临时加 11 行 `sourceSets.main.java.exclude(...)`，把 11 个联动叶子文件
挡在 26.3 的编译源集外，实测：

| | 基线 | 摘掉之后 |
|---|---:|---:|
| javac 错误 | 318 | **303** |
| D 附属模组缺依赖 | 56 | 13 |
| G 真断·形变 | 155 | **187** |

净只降 **15**。省下的 43 条里 32 条没有消失，是**转到了 12 个上游引用方头上**，
变成"找不到符号 / 程序包不存在"：

| 上游文件 | 断在哪个被摘的类 |
|---|---|
| `terminal/client/TerminalApp` | refinedstorage、toms、ae2 |
| `terminal/integration/Terminals`（neoforge 层） | ae2、refinedstorage、toms |
| `settings/client/AboutPage` | CuriosCompat、NetMusicCompat |
| `music/DiscService`（26.3 平台自有） | NetMusicCompat |
| `music/client/NetSongPlayback`、`NetSongSound` | NetMusicPlayback |
| `reader/client/ReaderApp`、`reader/client/source/BookSources` | GuideMeSource、PatchouliSource |
| `quests/client/QuestsApp` | FtbQuestsBook |
| `browser/client/BrowserBackends` | McefBackend |
| `core/PhoneLocation`（1.20.5+ 层）、`core/PhoneItem`（1.21+ 层） | CuriosCompat 等 |

这 12 个都在手机主界面与注册链路上，再摘就是第二轮级联。**"改 build.gradle 摘文件"
这条路作废，`build.gradle` 一个字没动**（实验已 `git checkout` 回滚，工作区当时是干净的）。

### 二、顺手把 D 桶查了个底：真缺 jar 的只有 13 条

按 import 逐个查这 11 个"联动文件"，有 5 个（`CuriosCompat`、`NetMusicPlayback`、
`FtbQuestsBook`、`GuideMeSource`、`WaystoneApp`）**本来就没有一行第三方 import**，
全靠 `Class.forName` 反射——它们进 D 桶是我按关键字归因归错了。剩下真碰 jar 的是 13 条：

| 文件 | 条数 | 缺的包 |
|---|---:|---|
| `refinedstorage/TerminalSlotReference` + `...Factory` | 6 | `com.refinedmods.refinedstorage.*` |
| `reader/client/source/PatchouliSource` | 4 | `vazkii.patchouli.*` |
| `browser/client/McefBackend` | 2 | `com.cinemamod.mcef` |
| `compat/NetMusicCompat` | 1 | `com.github.tartaricacid.netmusic` |
| `terminal/integration/toms/TomsStorageIntegration` | 1 | `com.tom.storagemod` |

**D 桶另外 43 条与 jar 无关**，是"26.3 少建了平台接缝文件"（`CuriosInventories`、
`client/Draw`、`client/VanillaAudio` 这几支老目标都有、26.3 还没建）加上普通移植错。

### 三、这一步：Curios 那一族按仓库自己的判据收进接缝

判据是 `gradle/mcphone-checks.gradle:765` 写着的：

> 门面（ModPresence / Slots / StackCodecs / CuriosInventories）也是每个平台各一份，
> 却不需要闸 —— 因为 **shared/ 必须在【所有目标】上都编过，门面签名一漂移当场断构建**。

而 `CuriosInventories.of()` 的返回类型是 `Optional<ICuriosItemHandler>`，
**签名带着 Curios 的类型**。于是 `CuriosCompat` 虽然没有一行 Curios import，
也照样必须在编译期看得见 Curios —— 26.3 卡死在这里（它那 5 条错）。

做法是把**四个操作整个搬进接缝**，往外只递原版类型：

```java
// shared/compat/CuriosCompat.java：只剩"判在不在场 + 委托"
public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
    if (!isLoaded()) return false;
    return CuriosInventories.isEquipped(entity, filter);
}
public static Optional<CurioSlotRef> findEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
    if (!isLoaded()) return Optional.empty();
    return CuriosInventories.findFirst(entity, filter)
            .map(slot -> new CurioSlotRef(slot.slotId(), slot.index()));
}
```

`CuriosInventories` 四个目标各一份：老三支把 `CuriosApi` 的 handler 关在自己的
`of()` 里转成 `Slot`，26.3 那一份四个方法直接返回"没有"（`false` / 空 / 空堆 / 什么都不做），
**不写任何一个 Curios 类型**。

"判断在不在场"与"真去调它"仍分在两个方法里 —— 那条 `NoClassDefFoundError` 的规矩没破，
只是"真调用"这一半从 shared 挪到了接缝。对外公开签名一个字没改，
`PhoneLocation`、`PhoneItem`、`AboutPage` 三个调用方**不需要跟着动**。

### 四、实测

| | 改动前 | 改动后 |
|---|---:|---:|
| javac 错误 | 318 | **313** |
| `CuriosCompat` 一个文件的错 | 5 | **0** |
| 其余 86 个报错文件的错 | 313 | **313**（一条没动） |

**零新增、零级联** —— 与第一节那个"省 43 条、赔 32 条"的摘文件方案正好相反。

老三支本地重编全部 `BUILD SUCCESSFUL`；`1.21.1-neoforge` 上 `assertTests --continue`
摊平跑 47 个任务（42 执行 + 5 up-to-date），红的仍然只有改动前就红的
`assertTestEconomyDataTest`（Windows 无 POSIX 权限）与 `assertTestScriptEngineTest`
（zh-CN locale），**没有新增**。

三支的 `CuriosInventories` 彼此差异反而从 4 行缩到 **1 行**（只剩 `.resolve()` 那句）。

八道闸：`verifyPlatformTwins` 通过，113 对不变、差异合计 5,995 → **6,013 行**。
涨的 18 行全在 `platform/CuriosInventories.java`（4 → 22）—— 这是"给一个新目标补一份
**有意的**接缝副本"必然产生的量，与同目录里 `Nbt` 18、`ClientMessages` 15、
`StackCodecs` 15 是一个族；理由写进了 26.3 那份的类注释和 `shared/PLATFORM-SEAMS.md`
（`updateSeamsDoc` 已重写）。26.3 那一份刻意压到 44 行，多写的每一行注释都是这个基线上的成本。

### 五、剩下的 13 条怎么走

1. **9 条可以就地反射掉**：`NetMusicCompat` 1、`McefBackend` 2、`PatchouliSource` 4、
   `TomsStorageIntegration` 1、`refinedstorage` 里那 2 条 import。照
   `FtbQuestsBook` / `GuideMeSource` / `NetMusicPlayback` 已经在用的
   `Class.forName` 写法改，**不需要任何构建改动**，老三支行为不变。
2. **`refinedstorage` 剩下 4 条另算**：`SlotReference` / `SlotReferenceFactory`
   出现在方法签名和返回类型上，要先擦成一个不带第三方类型的句柄，动的面比上面九条大，
   单独一步做。
3. 这条路走完，"联动以后做成附属模组"是顺手的：这套"签名只说原版类型"的接缝
   本来就是挂载点，26.3 那份将来换成真实现或换成附属发现都行。


## 廿五、1d 第 12 步（上半）：GLFW 键码里有 16 条可以【一字不改四支共用】。313 → 297

### 先取证：26.3 的键码有几套

`InputConstants` 在 26.3 里同时发布两套码：

```
KEY_A = 4      KEY_ESCAPE = 41     <-- SDL 【scancode】
KEYCODE_A = 97 KEYCODE_RETURN = 13 <-- SDL 【keycode】
```

用错一套不会编译失败，只会热键静默失灵。决定性证据在 `SDLEventHandler.handleKeyEvent`：

```java
int action = event.type() == 769 ? 0 : (keyEvent.repeat() ? -1 : 1);
KeyEvent key = new KeyEvent(keyEvent.scancode(), keyEvent.key(), keyEvent.mod());
```

`KeyEvent` 是 `record KeyEvent(int key, int keycode, int modifiers)` —— 第一位也就是
`Screen.keyPressed` 那个 `keyCode`，装的是 **scancode**；而 `InputConstants.KEY_*` 正是 scancode。
动作也一样：`PRESS = 1`、`RELEASE = 0`、`REPEAT = -1`，和 GLFW 的 `GLFW_PRESS = 1` 同值。

### 于是这一族根本不用建接缝

老三支的 `InputConstants.KEY_ESCAPE` 是 GLFW 值（256），26.3 的是 SDL scancode（41），
**各自都恰好是本版本 `KeyMapping` 与 `keyPressed` 认的那一个数**。所以把
`GLFW.GLFW_KEY_ESCAPE` 写成 `InputConstants.KEY_ESCAPE`，四支拿到的是各自正确的码 ——
共用代码照旧四支逐字相同，双胞胎基线一个字没动（113 对 / 6,013 行，`verifyPlatformTwins` 通过）。

这一步不需要任何产品决策，先把这半边清掉。改了六个文件：

| 文件 | 处数 | 换成 |
|---|---:|---|
| `shared/core/client/PhoneKeys.java` | 8 | `KEY_V`/`KEY_X`/`KEY_H`/`KEY_PAGEUP`/`KEY_PAGEDOWN`/`KEY_LALT`/`KEY_G` + 一句注释 |
| `layers/loader/neoforge/core/client/AppHotkeyHandler.java` | 3 | `InputConstants.PRESS` |
| `layers/loader/forge/core/client/AppHotkeyHandler.java` | 3 | 同上（孪生，必须一字不差一起改） |
| `platforms/1.21.1-fabric/core/client/AppHotkeyHandler.java` | 3 | 同上 |
| `shared/feature/settings/client/AppManagerDetail.java` | 2 | `KEY_ESCAPE`、`MOUSE_BUTTON_LEFT` |
| `shared/feature/settings/client/PhoneHudEditor.java` | 1 | `KEY_R` |

六份文件里的 `import org.lwjgl.glfw.GLFW;` 全部删掉，现在**整个仓库的 java 源码里 GLFW 只剩注释**
（`KeyModifier` 是 fabric 专有文件、`PhoneHud` 各支自己那份、`BrowserScreen` 那几行是 MCEF 的掩码常量，都不在这 16 条里）。

### 实测

| | 改动前 | 改动后 |
|---|---:|---:|
| 26.3 javac 错误 | 313 | **297** |
| F 桶 GLFW 一族 | 21 | **5** |

`1.20.1-forge`（45s）/ `1.21.1-neoforge`（12s）/ `1.21.1-fabric`（13s）三支重编全
`BUILD SUCCESSFUL` —— 这一支是最老的，它绿了就说名 `InputConstants` 那批名字在 1.20.1 上也全存在。

### 剩下那 5 条：只有它们真要碰 SDL

都在 `platforms/26.3-neoforge/core/client/PhoneHud.java`，是"直接问底层窗口"那两件事：

```java
GLFW.glfwSetCursorPos(window.getWindow(), x, y);                        // 396（2 条）
case MOUSE -> GLFW.glfwGetMouseButton(handle, value) == GLFW.GLFW_PRESS; // 415（2 条）+ import 1 条
```

26.3 这边：`Window` 上取句柄的方法改叫 `handle()`（`Window.java:649` 有 `public long handle()`），
原版自己对外的口是 `InputConstants.setCursorPos(...)`，里面是
`SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float)x, (float)y)`。
**"此刻某个鼠标键按着没有"在 26.3 的原版源码里搜不到现成口**（`getMouseState`/`SDL_BUTTON` 零命中），
原版是靠事件自己记状态。所以这 5 条要单独一步：建一个每平台接缝，
26.3 那一份自己调 `SDLMouse.SDL_GetMouseState` 或改成自己记，另外三支仍走 GLFW。

### 下半：那 5 条（实测 7 条）不需要新接缝，`PhoneHud` 本来就是每平台一份

上面写"要单独一步建每平台接缝"——做完发现**接缝是多余的**：`core/client/PhoneHud.java` 在
四个平台目录下各有一份，本来就已经是各说各话的文件（双胞胎基线里它对已有 93 行差异），
再套一层接缝只是把同样的差异换个地方放。所以 26.3 那一份里直接换实现：

```java
case KEYSYM -> InputConstants.isKeyDown(value);                              // 26.3 去掉了句柄参数
case MOUSE  -> (SDLMouse.SDL_GetMouseState(null, null) & (1 << (value - 1))) != 0;  // SDL 给位掩码
// 光标：SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float)x, (float)y)
```

三处要留档的取证：`org.lwjgl:lwjgl-sdl:3.4.3` **就在编译类路径上**（改完编译零 SDL 报错，
不必往 `build.gradle` 加依赖）；`Window` 取句柄的方法在 26.3 叫 `handle()`（`Window.java:649`）；
原版那个 `InputConstants.grabMouse` 不能用——它 `SDL_WarpMouseInWindow` 之后还顺手
`SDL_SetWindowRelativeMouseMode(true)`，会把光标藏起来，不是"把光标挪过去"这层意思。
`SDL_BUTTON(n) = 1 << (n - 1)` 这个位掩码换算对得上，因为 `InputConstants.MOUSE_BUTTON_LEFT`
在 26.3 上就是 1，等于 SDL_BUTTON_LEFT。

顺带修掉一条别的桶的错：26.3 的 `InputConstants.isKeyDown` 从 `(window, key)` 变成了 `(scancode)`，
那句 `isKeyDown(handle, value)` 本来也是一条签名错，所以这一下净掉 **7 条**。

| | 上半之后 | 现在 |
|---|---:|---:|
| 26.3 javac 错误 | 297 | **290** |
| F 桶 GLFW | 5 | **0** |

分桶现在：G 152 / D 51 / E 30 / A 27 / C 17 / B 13 / **F 0** = 290。
`verifyPlatformTwins`：113 对、6,013 → **6,026 行**，涨的 13 行全在 `PhoneHud.java`（93 → 106）,
就是 GLFW 与 SDL 两套不可能共用的实现，理由写在那个方法的注释里。
另外三支没动（改的是 26.3 自己那份平台文件）。


## 廿六、审计（未改代码）：鼠标键编号在 26.3 换了基数，而它【跨着对外 API 的边界】

低版本口径定成"选 2"之后，第一件事不是清错误数，而是查清一件**不会编译失败**的事。

### 事实

1. 26.3 的 `AbstractWidget.isValidClickButton` 是 `buttonInfo().button() == 1`
   （`AbstractWidget.java:140-142`），而 1.20.1/1.21.1 上 GLFW 的左键是 0。
   `InputConstants.MOUSE_BUTTON_LEFT` 也跟着从 0 变成 1。
2. 26.3 没有任何旧重载：`Screen` 只剩 `keyPressed(KeyEvent)`，`AbstractWidget` 只剩
   `mouseClicked(MouseButtonEvent, boolean)` / `mouseDragged(MouseButtonEvent, double, double)`，
   `EditBox` 只剩 `keyPressed(KeyEvent)` / `charTyped(CharacterEvent)`。
   `MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo)`、
   `MouseButtonInfo(int button, int modifiers)`、`KeyEvent(int key, int keycode, int modifiers)`、
   `CharacterEvent(int codepoint)`。
3. 本模组 `shared/` 里有 **23 处**直接拿字面量 `0` 判左键（`rg "button\s*[!=]==?\s*0"` 实测，
   分布在 `PhoneScreen`、`ChatConversation`、`Gallery`、`MusicPage`、`NotesList`、`BookList`、
   `AppManagerPage/Detail`、`WallpaperPicker`、`FontColorPicker`、`PhoneHudEditor`、
   `DeviceNameEditor`、`NoteEditor`、`ChatList`、`ChatMediaPicker`、`ChatAddContact`）。
   26.3 上它们**全部不匹配左键**，而且编译、八道闸、断言测试一声不响。

### 为什么不能一把梭全换成 `InputConstants.MOUSE_BUTTON_LEFT`

因为这一个 `int button` 同时服务两种语义，而其中一种**跨着给附属模组用的对外 API**：

| 去处 | 要的编号 | 依据 |
|---|---|---|
| `api/client/ui/IPhonePage.mouseClicked(double,double,int)`（对外） | 与今天一致：左=0 | 玩家要求「对外 API 与原本行为一样」 |
| `captureMouse(int)` → `InputConstants.Type.MOUSE.getOrCreate(button)` | 本版本原生编号 | 存进 `KeyMapping`，之后要跟原版比 |

`PhoneScreen.captureHotkeyMouse` 与 `PhoneScreen.mouseClicked` 收的是**同一个** `button` 参数
（`PhoneScreen.java:1131` 与 `:1137`），而里面既有喂原生的 `captureMouse`、又有
`if (button != 0)`（`:1144`）。老两支上原生==GLFW 所以一个值两用；26.3 上这两个语义**分叉了**。

### 下一步该做的（顺序固定）

1. 对外 API 侧加公开常量把既有编号**钉死**：`IPhonePage.BUTTON_LEFT = 0` / `BUTTON_RIGHT = 1` /
   `BUTTON_MIDDLE = 2`。纯新增，不改任何既有签名与取值，附属模组照旧写 `0` 也对。
2. 在 `platform/client/PhoneScreenBase` 上加一个 `pageButton(int 原生编号)`：老三支原样返回；
   26.3 那一份把 SDL 的 1..5 映成 0..4（`SDL_BUTTON_LEFT=1`、`GLFW_MOUSE_BUTTON_LEFT=0`，
   两边是 `n-1` 的关系，中键/侧键同理）。
3. `PhoneScreen` 派发进页面之前过这道换算；`PhoneScreen` 自己那几处判左键改判
   `InputConstants.MOUSE_BUTTON_LEFT`（那是原生侧）；`captureMouse` 保持原生，
   `AppManagerDetail:424` 那句已经在上一步换成了 `InputConstants.MOUSE_BUTTON_LEFT`，是对的。
4. 那 23 处里属于**页面层**的，保持字面量 0 不动（它们的入参已经是对外编号），
   可顺手改用第 1 步的常量拼写；属于**屏幕/原生层**的才需要动。

顺带说一句：`EditBoxes` 那一族转发（11 条编译错）必须排在第 2 步之后做——
因为往 `new MouseButtonInfo(button, 0)` 里塞的是原生编号，边界没钉死之前先改会把编号搞反。
`modifiers` 传 0 是有据的：`AbstractWidget`/`AbstractTextAreaWidget` 里没有任何地方读
`buttonInfo().modifiers()`，`isValidClickButton` 只看 `button()`。


## 廿七、1d 第 13 步：把鼠标键编号与硬编码键码这两件【不会编译失败】的事钉掉，顺手清掉输入转发 11 条。290 → 279

### 这一轮的低版本口径

用户定的第 2 档：`shared/` 不再要求在 1.20.1 / 1.21.1 上也编得过，26.x 的 API 断裂可以原地改共用代码，
低版本留在 `main`、要新东西从 `main` 单向并进这个分支。**但**给附属模组用的对外 API 与行为必须保持一致。

实际做法仍然是"四支同签名的接缝"——不是因为被要求，而是因为这次改的东西**跨着对外 API 的边界**，
不这么写就会把编号搞反。代价没变高：老三支本轮全部 `BUILD SUCCESSFUL`，回归网白捡。

### 一、鼠标键编号在 26.3 换了基数

`AbstractWidget.isValidClickButton` 在 26.3 判的是 `buttonInfo().button() == 1`（`AbstractWidget.java:140-142`），
而 GLFW 的左键是 0；`InputConstants.MOUSE_BUTTON_LEFT` 也跟着从 0 变 1。
`shared/` 里有 **23 处**直接拿字面量 `0` 判左键，26.3 上全部不匹配左键，而编译、八道闸、断言测试**一声不响**。

这 23 处**不能一把梭换成 `InputConstants.MOUSE_BUTTON_LEFT`**，因为同一个 `int button` 同时服务两种语义，
而其中一种就是对外 API：

| 去处 | 要的编号 | 为什么 |
|---|---|---|
| `api/client/ui/IPhonePage.mouseClicked(double,double,int)` | 与今天一致：左 = 0 | 对外 API，附属模组照今天写的 `button == 0` 在任何版本都得是左键 |
| `captureMouse(int)` → `InputConstants.Type.MOUSE.getOrCreate(button)` | 本版本**原生**值 | 存进 `KeyMapping`，之后要跟原版比 |

而 `PhoneScreen.captureHotkeyMouse(button)` 与 `PhoneScreen.mouseClicked(..., button)` 收的是**同一个**参数
（`:1131` 与 `:1137`）。老两支原生 == GLFW 所以一个值两用，26.3 上这两个语义**分叉**了。

处理：
1. `IPhonePage` 上加 `BUTTON_LEFT=0 / BUTTON_RIGHT=1 / BUTTON_MIDDLE=2 / BUTTON_SIDE_1=3 / BUTTON_SIDE_2=4`
   —— 把既有编号**钉成契约**，纯新增，不改任何既有签名与取值，附属模组照旧写 `0` 也对。
2. `platform/client/PhoneScreenBase` 四份各加 `pageButton(原生)` 与 `nativeButton(对外)` 一对换算。
   老三支两者都是恒等（GLFW 本来就是 0/1/2）；26.3 那一份按 SDL↔GLFW 的严格 `n-1` 关系平移
   （LEFT 1/0、RIGHT 2/1、MIDDLE 3/2、BUTTON4 4/3、BUTTON5 5/4，所以 1..5 整体移一位）。
3. `PhoneScreen.mouseClicked` / `mouseDragged` 各在**原生侧**判左键（改判 `InputConstants.MOUSE_BUTTON_LEFT`），
   派发进页面之前过一次 `pageButton`，新增局部量 `pb` 承接（两个方法各一份，别跨方法用 ——
   第一遍就是这么写错、`PhoneScreen` 当场多冒一条 `找不到符号 变量 pb`）。

那 23 处页面层里的 `button == 0` 一个字没动：它们收到的已经是对外编号，本来就还是 0。

### 二、硬编码键码 12 处

同类问题的另一半：`keyCode == 257 || keyCode == 335`（Enter / 小键盘 Enter）这种字面量散在 6 个文件里，
26.3 上 `KEY_RETURN` 是 40 不是 257 —— 一样是**不报错的静默失灵**。全部换成命名常量：

```java
keyCode == InputConstants.KEY_RETURN / KEY_NUMPADENTER / KEY_BACKSPACE / KEY_TAB / KEY_ESCAPE
```

在两支上都是**纯等价替换**：1.21.1 上 `KEY_RETURN = 257`、`KEY_NUMPADENTER = 335`、`KEY_BACKSPACE = 259`、
`KEY_TAB = 258`、`KEY_ESCAPE = 256`，与原字面量逐一对得上；26.3 上它们换成各自的 SDL scancode，
而 `SDLEventHandler` 传进 `KeyEvent.key` 的正是 scancode（§廿五取证过），所以两边都拿得到本版本正确的那个值。
换完 `rg "keyCode [!=]==? [0-9]{3}"` 在 `shared/`、`layers/`、`platforms/26.3-neoforge/` 上**零命中**。

### 三、输入转发 11 条：走 `EditBoxes` 接缝

`DeviceNameEditor` / `ChatConversation` / `NoteEditor` / `BookList` 四处把事件转给页面里嵌的原版控件，
26.3 那边被调方换成了事件记录（`mouseClicked(MouseButtonEvent,boolean)`、`keyPressed(KeyEvent)`、
`charTyped(CharacterEvent)`、`mouseDragged(MouseButtonEvent,double,double)`）。
四支的 `platform/client/EditBoxes` 各加 `click / drag / key / character` 四个口：老三支原样调旧签名，
26.3 那一份就地构造事件对象，**进去的是对外编号、出来前过 `nativeButton` 换成原生**——顺序反了就把编号搞反，
这也是为什么这一步必须排在第一节那套换算之后。

两处写下来的取舍：

- `modifiers` 传 `0` 有据：`AbstractWidget` 与 `AbstractTextAreaWidget` 里没有任何一处读
  `buttonInfo().modifiers()`，`isValidClickButton` 只看 `button()`。
- `doubleClick` 只能传 `false`：对外页面接口 `IPhonePage.mouseClicked` 本来就不带这个信息，
  老三支同样传不进去，所以「双击选词」这一条在**四支上都没有**，这里不比老三支少什么。
  （不是"26.3 少了功能"，写清楚免得后来人以为能补。）

### 四、实测

| | 第 12 步之后 | 现在 |
|---|---:|---:|
| 26.3 javac 错误 | 290 | **279** |

按（文件, 错误信息）多重集比对的进出账：11 条转发错**全部消失**
（`DeviceNameEditor` 3、`ChatConversation` 3、`NoteEditor` 4、`BookList` 1），新增 **0** 条。
老三支重编全 `BUILD SUCCESSFUL`（45s / 13s / 14s）。
`verifyPlatformTwins` 通过：113 对、6,028 → **6,044 行**。两处涨幅都是有意的接缝分歧 ——
`EditBoxes` 6 → 20（+14，26.3 那一份要多构造事件对象）、`PhoneScreenBase` 122 → 124（+2，那一对换算）。

### 五、还欠着的（别当成做完了）

**本轮全部改动只有编译期与静态取证，没有一次真实点击验证。** 尤其这两条要在 26.3 里进游戏复核：
左键能不能点开手机上的页面（靠 `pageButton` 那一步平移，写反了就是"点什么都没反应"），
以及绑键界面对侧键的原生编号是否仍然对得上。


## 廿八、1d 第 14 步：`Minecraft#screen` 那 30 条 —— 字段搬进了 `Gui`，而改名表写不出这种形变。279 → 249

### 一、为什么这条不能进改名表，只能建接缝

`versions/name-renames.json` 里已经有 `methodRenames`，`setScreen → setScreenAndShow` 走的就是那一族
（`versions/name-renames.json:92`），所以【开界面】那条路 26.3 早就接上了。这一轮治的是【看现在开着谁】：

- 老三支：`Minecraft#screen` 是个 public 字段，`mc.screen` 直接读。
- 26.3：`Minecraft` 里再没有这个字段，它搬进了 `Gui` —— `Gui.java:79` 是
  `private @Nullable Screen screen;`，取值口是 `Gui.java:220` 的 `public @Nullable Screen screen()`，
  方法体就一句 `return this.screen;`。原版自己也全走这条：`Minecraft.java:1129`、`:1457`、`:1859`
  读的都是 `this.gui.screen()`。

看着像改名表该管的，其实管不了。`gradle/mcphone-renames.gradle:65-66` 那条 `methodAlt` 是

```groovy
Pattern.compile('(?<=\\.\\s*)(' + names.collect { Pattern.quote(it) }.join('|') + ')(?=\\s*\\()')
```

**两头都是定位用的**：前面必须有 `.`，后面必须有 `(`。字段读没有那对括号，规则根本命不中；
而要是为它放宽成「标识符 → 表达式」，`screen` 这个满仓都是的普通词就跟着遭殃 ——
`Screen screen = ...`、`instanceof PhoneScreen screen`、`net.minecraft.client.gui.screens` 那个包名，
哪一个都得坏。何况这次要插的不只是换个词，是【中间多一段 `.gui`、末尾多一对括号】。
改名表那三族规则干的都是等长的标识符替换，形变不是它的活。所以：接缝。

### 二、位点清点：全仓 65 处，报错的只有 30 处

| 位置 | 处数 | 26.3 上报不报 |
|---|---:|---|
| `shared/` 17 个文件 | 17 | 报（26.3 编共用代码） |
| `layers/loader/neoforge/AppHotkeyHandler` | 4 | 报（这一层 26.3 也挂） |
| `layers/loader/forge/AppHotkeyHandler` | 4 | 不报（26.3 不挂 forge 层） |
| `platforms/1.21.1-fabric/AppHotkeyHandler` | 4 | 不报 |
| 四份 `platform/*/core/client/PhoneHud` | 9 × 4 = 36 | 只有 26.3 那份报 |

26.3 上 `javac` 报的正好是 17 + 4 + 9 = **30** 条，占剩余错误的一成，是当时最大的一簇。
另外 35 处**本来就编得过**，本轮照样一并收进接缝 —— 因为这四支的同名文件在双胞胎基线里本该逐字相同：
只改 26.3 那份，`PhoneHud` 与两份 `AppHotkeyHandler` 的接缝差异立刻凭空涨一截，
而那一截不携带任何信息。同一个坑 §廿三 栽过一次（第一遍只改了 26.3 一路，六个 net 文件的双胞胎差异整体推高）。

### 三、接缝形状：一个静态方法，只管读

`platform/client/Screens.java` 四份，对外只有一个口：

```java
public static Screen current(Minecraft mc)   // 老三支 return mc.screen; / 26.3 return mc.gui.screen();
```

两点选择：

- **收 `Minecraft` 参数，不在接缝里自己 `getInstance()`**。65 处调用点本来就全握着实例
  （`mc`、`minecraft`、`Minecraft.getInstance()`），多拿一次没意义。`ClientMessages` 那份之所以自己取，
  是因为它得先判 `instanceof LocalPlayer` 才敢碰客户端单例 —— 专用服上 `RuntimeDistCleaner`
  一加载碰了客户端类型的类就抛。这里没这个问题：四支都是纯读，26.3 那份也只是 `return this.screen;`。
- **不管写**。开界面走改名表那条（老 `setScreen(Screen)` → 26.3 `setScreenAndShow(Screen)`），
  本轮一个字没动。顺带记一句两边不止同名换掉：26.3 的 `setScreenAndShow`（`Minecraft.java:2281`）
  是 `gui.setScreen(screen)` 外加一次 `renderFrame(false)` 强制出帧。那是第 5 步定下的账，本轮没改判据。

`PhoneHud` 里同一个方法读两次的两处（`tick` 与 `onTogglePressed`）提了局部量 `var shown = ...`，
四支改法逐字相同，接缝副本之间因此没新增分歧。改完 `rg` 复查：
`(mc|minecraft|client|Minecraft.getInstance()).screen` 在代码行上**零命中**（注释里那句
"手机成了 mc.screen 就走不动路"留着，那说的是这件事本身，不是代码）。

### 四、实测

| | 第 13 步之后 | 现在 |
|---|---:|---:|
| 26.3 javac 错误 | 279 | **249** |

少 30，正好等于 `变量 screen` 那一簇：按（文件, 错误信息）多重集比对，30 条全消、新增 **0** 条。
老三支 `compileJava` 全 `BUILD SUCCESSFUL`（1.20.1-forge 37s / 1.21.1-neoforge 6s / 1.21.1-fabric 40s），
`assertTests --continue` 三支摊平各自的任务，红的都还是那两个改动前就红的 Windows 环境问题
（`assertTestEconomyDataTest` 的 `setPosixFilePermissions`、`assertTestScriptEngineTest` 的 zh-CN locale），
三支各 2 个失败，一个不多。`verifyPlatformTwins` 通过：**113 → 114 对**（新增的就是 `Screens.java` 这一对）、
差异合计 6,044 → **6,046 行**，涨的 2 行全在 `Screens.java`：四份里三份逐字相同，只有 26.3 那一份的取值式子两样。
`updateSeamsDoc` 刷过，十道配置闸全绿（9 目标）。

一支的环境坑记下来免得重踩：`1.21.1-neoforge` 这次第一遍跑**没带** `--init-script gw-proxy.gradle`，
配置缓存因此失效，`createMinecraftArtifacts` 去重拉 `neoform-runtime:2.0.24`，直连 `maven.neoforged.net`
吃一个 `Connection reset`，6 秒红。带上那条初始化脚本重跑，6 秒 `BUILD SUCCESSFUL`。
**四支的 gradle 命令都得带同一个 `--init-script`**，不然失效的不只是网络，还有缓存。

### 五、`hideGui` 那 4 条：取到证了，但没有顺手做

按错误数排，下一簇本来是 `变量 hideGui` 4 条（`CameraMode.java:37/39/49` 与 26.3 的 `PhoneHud:212`），
看着像"再建一个接缝"就完，查下去不是：

26.3 的 `Options` 里 `hideGui` 这个字段**整个没了** —— 整棵 `src263full`（7301 个 java）里只剩
`ScreenEffectRenderer` 一个文件的 5 处，还是方法参数名。F1 那条路改成
`Options.keyToggleGui`（`Options.java:694`）→ `Gui#handleKeybinds`（`Gui.java:341`）→ `this.hud.toggle()`，
状态存在 `Hud.isHidden`（`Hud.java:156`），读口是 `public boolean isHidden()`（`Hud.java:222`），
`Gui#hud` 是 public final（`Gui.java:76`）。

卡住的地方是：**只有 `toggle()`，没有 setter**，而 `CameraMode` 要的是"进取景写成 A、退出恢复成 B"这种三处写。
`isHidden() != 目标值才 toggle()` 能编得过，但 `CameraGui` 那套"取景时到底谁该藏"本来就分平台
（1.20.1 那支逼着 `hideGui=false`、改由事件自己画 HUD，NeoForge 两支用原版 `hideGui`），
而 26.3 那份 `CameraGui` 还没建。所以这 4 条并进 `platform/client/CameraGui` 那一步一起做，
不在这里塞一个只为了让错误数掉的半个接缝。

### 六、还欠着的（别当成做完了）

本轮仍然只有编译期与静态取证。§廿七 那两条进游戏复核的账还挂着（左键能不能点开手机上的页面、
绑键界面对侧键的原生编号）。本轮再加一条同一性质的：`Screens.current` 在 26.3 上读的是 `gui.screen()`，
而 26.x 的 `Gui` 另有 `overlay()`（`Gui.java:297`）与 `pushScreenLayer(Screen)`（`Gui.java:498`）这一层 ——
手机是走 `setScreenAndShow` 进去的，落在 `screen` 这一层，所以"有没有别的东西挡着"判 `screen()` 是对的；
但挂在副手 HUD 上的那面会不会被某个 overlay 层的东西挡住，得进游戏才知道。


## 廿九、1d 第 15 步：`GameProfile` 取名与档案缓存那 14 条 —— 接缝选路要看【会不会联网】。249 → 235

### 一、一个语义，两处断裂

「查这个人叫什么」这件事在 26.3 上断了两个环节，六个调用点一起红（`ChatService` 3、`PhoneChat` 1、
`Scores` 2、`ScriptRpcHandler` 1，错误 14 条）：

| 环节 | 老三支 | 26.3 |
|---|---|---|
| 档案上的名字 | `GameProfile#getName()`（authlib 普通类） | `GameProfile#name()`（换成了带访问器的形状） |
| 那本档案 | `MinecraftServer#getProfileCache()` → `net.minecraft.server.players.GameProfileCache` | 方法与类【都没了】，换成 `server.services().nameToIdCache()`（`UserNameToIdResolver`） |

证据链：`Services.java:15-21` 那个 record 里 `nameToIdCache`（`:19`）与 `profileResolver`（`:20`）
是两个分量；`create` 里 `:27` 先建档案缓存，`:28` 才把 `ProfileResolver.Cached(sessionService, profileCache)`
套在它上面。而档案文件名还是 `:22` 那句 `USERID_CACHE_FILE = "usercache.json"` ——
老 `GameProfileCache` 落的也正是这一份。
`NameAndId.java` 是 `record NameAndId(UUID id, String name)`，不再是 `GameProfile`。

### 二、选路判据是「联不联网」，不是「名字像不像」

`MinecraftServer#getProfileCache()` 没了之后，全 `src263full` 里语义最近的候选是
`ProfileResolver`（`fetchById(UUID)` / `fetchByName(String)`，返回的正是 `Optional<GameProfile>`，连返回类型都对得上）。
**但它不能用来替老那句**：`ProfileResolver.java` 里 `Cached` 的
`profileCacheById` 是个 Guava `LoadingCache`，`CacheLoader.load(UUID)` 直接
`sessionService.fetchProfile(profileId, true)` —— **缓存未命中就打 HTTP**，还是在服务器主线程上。
而老的 `getProfileCache().get(id)` 读的是内存表，未命中就是空，从不发请求。
替过去的话：离线模式、私服、断网测试机上，每次"查一个陌生 UUID 的名字"都要白等一趟网络超时，
而这三处调用点（好友列表、脚本 RPC、计分板持有者名）全是读多写多的路径。

真正对得上的是 `services().nameToIdCache().get(id)`：
`CachedUserNameToIdResolver.java:135-143` 就一句 `profilesByUUID.get(id)`，
拿不到直接 `Optional.empty()`，没有网络。（它那个【按名字】的 `get(String)` 才会走
`profileRepository.findProfileByName`，但本仓这六个点全是按 UUID 查，碰不到那条。）

顺带一条：**没给它编名字**。老注释里那句"从没上过线的人查不到，那是事实，不该编一个名字出来"
在 26.3 上照样成立，所以 `cachedName` 查不到就返回空，由调用方自己退回 UUID 前 8 位。

### 三、往外递 `String`，不递 `GameProfile`

`NameAndId` 手上只有 `(id, name)`，要拼一个 `GameProfile` 回来就得在 26.3 那份里 `new GameProfile(...)`
—— 那是为了保住一个老类型名而凭空造对象，还会把"档案里有纹理属性吗"这种这支根本没有的东西装成有。
六个调用点要的只是名字或"有没有记录"，所以接缝就递 `String`：

```java
public static String name(GameProfile p)                            // 老 getName() / 26.3 name()
public static Optional<String> cachedName(MinecraftServer s, UUID)  // 档案缓存里的名字，不联网
```

`name(GameProfile)` 单独留着，是因为 `ChatService:307/353` 与 `ScriptRpcHandler:47` 手上已经有
真档案（在线玩家的 `getGameProfile()`），不需要查缓存，只需要那个取值口换了名字。

### 四、为什么 `getName → name` 不进改名表

改名表要是有 `methodRenames: {"getName": "name"}` 就省事了吗 —— 不行，那一族是**全局**的，
`methodAlt` 只认「点后、括号前」这个形状，不管 receiver 是什么类型。而 `getName(`
在 `shared/`、`layers/`、`platforms/` 里有 **45 个文件、87 处**，服务于四五个互不相干的类：
`Class#getName`（`SpiLoader`、`EmcWallets`、`TerminalSlotLocator`）、
`Player#getName`（返回 `Component`，`RequestThrottle`、`StoreNetworking`、`NetworkHandler` 在用）、
`Container#getMetadata().getName()`（`ModPresence`）、`User#getName()`（`MCphoneClient` 两处）、
`WorldVersion#getName()`（`AboutPage:104`，那一条还没修）。
一把换下去这 87 处一起遭殃，而 `Player#getName` 与 `GameProfile#getName` 本来就不是一个东西。
何况这次动的是 **authlib 那本 jar**，不是原版类的改名。所以进接缝，和 `Nbt` / `ClientMessages` 同族。

### 五、局部量重名：同类坑第二次被 javac 抓到

第一遍改完是 **237** 而不是 235：多出的两条是 `已在方法 ... 中定义了变量 cached` ——
`PhoneChat.findPlayer` 里本来就有 `List<UUID> cached = friends.idsNamed(name);`，
`ChatService.rawName` 里本来就有 `String cached = friends.getName(id);`，
我新写的 lambda 参数与局部量都撞上了 `cached` 这个名字。
改成 `rec` 与 `fromCache` 之后掉到 235。
这与 §廿七 那条 `pb` 是同一件事：**往共用代码里塞新局部量之前，先读一遍这个方法已有的名字**；
四支同改，靠编译发现比靠眼看便宜，但前提是没有写成静默兜底。

### 六、实测

| | 第 14 步之后 | 现在 |
|---|---:|---:|
| 26.3 javac 错误 | 249 | **235** |

按（文件, 错误信息, 符号）多重集比对：14 条全消 —— `GameProfileCache` 5、`getProfileCache()` 5、
`getName()` 4（3 条"找不到符号" + 1 条 `Scores` 的"方法引用无效"）；新增 **0** 条。
老三支 `compileJava` 全 `BUILD SUCCESSFUL`（22s / 3s / 3s），
`assertTests --continue` 摊平 43 / 51 / 46 个任务，三支各红 **2** 个，
且都是改动前就红的那两个 Windows 环境问题（`assertTestEconomyDataTest` 的
`Files.setPosixFilePermissions`、`assertTestScriptEngineTest` 的 zh-CN locale），
一个不多 —— 这一轮动到了 `Scores`（EconomyData 那条链路）与 `ChatService`/`PhoneChat`，
`assertTestScriptRpcTest`、`assertTestMailboxTest`、`assertTestConversationKeyTest` 全绿。
`verifyPlatformTwins` 通过：**114 → 115 对**、6,046 → **6,054 行**，
涨的 8 行全在新的一对 `Profiles.java`（import 那行 + 两个方法体）；
`Scores.java` 那一对仍是 25 行 —— 因为两份副本改的是同一段，`1.20.1-forge` 自己那份
`platforms/.../economy/Scores.java` 跟着一起收了，不然基线要凭空涨。
十道配置闸全绿。

### 七、还欠着的

`getName` 那一族还剩 `AboutPage:104` 一条：`SharedConstants.getCurrentVersion().getName()`，
那是 `WorldVersion` 的改名，跟玩家档案无关，归到"整类不存在/改名见底"那一堆里再算。
进游戏复核的账仍然全部挂着（§廿七、§廿八 各一条）。
