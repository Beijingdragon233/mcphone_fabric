# S16b 核实记录：安装界面与签名工具

> 这是一份核实记录，不是设计。**施工方案本身不在本仓库里**，文中的 §编号指它。

对应 §12.4 / §12.5 / §12.6 的可见性那一半。日期 2026-09-17，分支 `feature/sign-ui`。

## 一、这一轮真跑起来的（不只是编过）

### `./gradlew signApp` 端到端

```
$ ./gradlew signApp -PappDir=<样例> -PgameDir=<run> -Pauthor=yumeka -PoutZip=<out.zip>
[signApp] 读到 3 个文件
[signApp] 摘要 0dbf631ccdbaeb4d9feff22de1160ee5558b2f1569c51cdb779f697f6a0bf1b2
[signApp] 作者指纹 ad88-d3ca-61de-73ee
[signApp] 签好了：…/gradle-signed.zip
[signApp] 提醒：签名确认的是「谁做的、有没有被改过」，不是「内容安不安全」。
BUILD SUCCESSFUL in 44s
```

四条失败路径各自报清是哪一步：

```
失败于「读私钥」：还没有作者密钥。先在游戏里「设置 → 开发者 → 我的签名密钥」生成一对…
失败于「读包」：包根缺 manifest.json —— zip 形态的包必须有它（§11.2）
失败于「读包」：<路径>（目录不存在）
```

### 五档在一个真签出来的包上各走一遍

```
① 签名包、空信任库   UNKNOWN_AUTHOR  指纹 ad88-…  能继续 true
② 同一个包、已信任   TRUSTED                      能继续 true
③ 改一字节重压       INVALID         指纹 ad88-…  能继续 false   ← 唯一的硬拒绝
④ 只换 sig.json      KEY_CHANGED     旧 ad88-… → 新 080e-…
     点一下就走？ false      抄对新指纹？ true
⑤ 未签名             UNSIGNED        指纹 null    能继续 true
```

③ 那一条**按判据原文的做法做的**：解开 zip、改内容、压回去，`sig.json` 原样带着。
（第一次我直接在 zip 字节里找 `hi` 去翻转 —— 那找不到，因为 zip 是 DEFLATE 压过的，
字面不在里面。这类"测试看起来通过其实什么都没测"的写法值得记一笔。）

## 二、文案

`SigCopy` 把五条 + `INSTALL_NOTE` 集中一处，`docs/SignCopyTest.java` **49 条断言**里有两组是硬的：

- **逐字比对**：读 `assets/mcphone/lang/zh_cn.json`，与 §12.5 原文逐字对
- **不许出现「安全」**：五条文案里中文查「安全」、英文查 `safe` / `secure`。
  `INSTALL_NOTE` 是例外 —— 它正是用来说"不是内容安不安全"的

确认短语的形态定成**抄新指纹本身**，不是打"确认"两个字。理由：打两个字谁都会打，
而抄一串十六进制必须先把目光挪到指纹那一行 —— 这一档存在的全部意义就是让玩家注意到
"这次不是上次那个人"。断言里钉住了：`"确认"` 不放行、抄成旧指纹不放行、
省掉连字符不放行、大小写与首尾空白不拦人。

## 三、判定只有一处实现

界面**不重判签名**。`LocalScriptSource` 把 §12.4 判好的结果整理成
`AppInfo.Signature`（四格纯字符串）带给 `AppDetail`，界面只渲染。
UI 里再判一遍就会有两份判据，而它们迟早对不上 —— 表现是"界面说能装，装下去被拒"，
或者更糟，反过来。

`AppInfo.Signature` 用纯字符串而不是 `TrustState.Verdict`：`api/` 不该依赖 `core.script.pkg`。

## 四、`signApp` 为什么落在 `gradle/`

`guard-version` 那道闸查的是 `platforms/*/gradle.properties` 里有没有出现
`mod_id|mod_name|mod_license|mod_group_id|mod_version` 五个仓库级的键（核实过它的正文）。
新任务定义在 `gradle/mcphone-sign.gradle`，三个平台各 `apply from` 一行，
**没有往平台的 gradle.properties 里加任何东西**，那道闸碰不到。

**不挂 check / build**：签名要私钥，挂上去等于每次构建都要有一把私钥在场，而 CI 上没有、
也不该有。签名是命令式操作（§12.6）。

命令行入口 `SignAppCli` 住在 `docs/` 下 —— 那一层每个目标都编，但**不进模组 jar**。

## 五、这一步我做不到的

| 判据 | 状态 |
|---|---|
| 四档安装截图（含 INSTALL_NOTE 与指纹） | ❌ **要 runClient** |
| 「作者密钥变了」的确认短语输入截图 | ❌ 同上（逻辑已判：`"确认"` 进不去） |
| 「我的签名密钥」页截图 | ❌ 同上 |
| 「已签名」处没有暗示内容安全的图标 | ◐ 代码里确实没画对勾，但"看着像不像"要人看 |
| `./gradlew check` / `build` 行为不变 | ◐ 新任务没挂生命周期、没加仓库级键；**完整 build 我没跑** |
| 1.20.1 支线能签能装 | ◐ 签过了（上面那次就是在 1.20.1-forge 上跑的）；装要 runClient |

**界面代码一次都没渲染过。** 编得过、判定有断言、文案逐字比对过，
但像素、换行、点击热区没人看过 —— `AppDetail` 那一段插在描述与价格之间，
文字多的时候会不会挤掉价格行，只有开一次客户端才知道。

## 怎么复现

```bash
cd platforms/1.20.1-forge
./gradlew assertTests
./gradlew signApp -PappDir=<一个 App 目录> -PgameDir=run -Pauthor=<名字>
```
