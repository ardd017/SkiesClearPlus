# SkiesClear Plus

<img align="right" width="140" src="docs/icon.png" alt="SkiesClear Plus"/>

**中文** ｜ [English](README_EN.md)

> **这是二创分支。** SkiesClear Plus 基于 **PokeSkies** 的
> [SkiesClear](https://github.com/PokeSkies/SkiesClear) 修改，依 Apache-2.0 授权使用。
> 与原作者的团队无隶属关系，也未获其背书。维护者：**ardd_017**。
> 完整改动清单与上游的授权状况见 [ATTRIBUTION.md](ATTRIBUTION.md)。
>
> ⚠️ **不要与原版 SkiesClear 同时安装。** 本分支沿用了技术标识 `skiesclear`，为的是让已有的
> `config/skiesclear/config.json` 和 `players.json`（首次进服记录）原样继承。两个同 id 的 mod
> 无法同时加载 —— 装本版前请先删掉原版 jar。

<img width="100" height="50" src="https://i.imgur.com/c1DH9VL.png" alt="Requires Fabric Kotlin"/> <img src="https://img.shields.io/badge/Enviroment-Server-purple" alt="Enviroment Server"/>

Fabric 服务端实体清理模组：按周期自动清理掉落物、生物、甚至 Cobblemon 宝可梦，清理前有倒计时提醒。

完整中文手册：[使用说明.md](使用说明.md)（安装、GUI 图例、配置逐字段、规则语法、多语言、FAQ）

## 相对上游 1.1.2 的新增内容

- **21 种语言，按玩家各自渲染。** 所有面向玩家的文案都在
  `assets/skiesclear/lang/<locale>.json`，按进服玩家自己的客户端语言渲染，区域变体走回退链
  （`zh_hk` → `zh_tw`、`pt_pt` → `pt_br` …）。
- **游戏内箱子菜单。** `/skiesclear gui [id]` 打开一个双箱界面，改计时器不用碰 `config.json`。
  **纯原版客户端即可**，不需要装任何客户端 mod。
- **Mod Menu / Cloth Config 面板。** 可选的客户端齿轮按钮，编辑同一份配置，带滑块、开关、
  字符串列表、精确秒数输入框和保护期。
- **播报。** 开服、每次进服、首次进服三种提示，让玩家在第一个 5 分钟倒计时走完之前就知道模组在跑。
- **新命令。** `/skiesclear gui`、`/skiesclear language`、`/skiesclear broadcast`。
- **保护期。** `grace_seconds`（默认 15）跳过比这更年轻的实体，避免「倒计时归零那一秒刚挖出来的
  方块直接消失」。填 `0` 关闭。
- **重载不再打断倒计时。** 切语言或保存菜单不会再让所有计时器从头开始，只有真的改了 `interval` 才会。

另外修了一批 bug —— 最要紧的是「保存配置会把所有 `clearables` 写成 `{}`」的序列化问题，以及
「白名单去查黑名单」的逻辑错误。全部列在 [ATTRIBUTION.md](ATTRIBUTION.md)。

## 功能

- 建任意多个清理计时器
- 把计时器限制在指定维度
- 可自定义清理提示与音效
- 可自定义警告提示与音效（自己选在剩余多少秒时提醒）
- 自定义清理哪些类型的实体：
  - 掉落物（黑白名单）
  - 任意实体（黑白名单，支持 `#tag=` / `#nbt=` 匹配）
  - Cobblemon 宝可梦（黑白名单 + `#shiny` / `#legendary` / `#aspect=` 等旗标）
- 三种播报：开服一次、每次进服一次、首次进服额外一次
- 游戏内箱子菜单（`/skiesclear gui`）与可选的 Mod Menu 齿轮面板
- 内置 21 种语言，按玩家渲染

## 安装

1. 用 `./gradlew build` 自行构建，或从本仓库的 Release 取 jar。上游在
   [Modrinth](https://modrinth.com/mod/skiesclear) 的版本**不包含**上面列的任何功能。
2. 必装依赖：
   - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
   - [Fabric Permissions API](https://github.com/PokeSkies/fabric-permissions-api)
3. 可选依赖：
   - [Cobblemon](https://modrinth.com/mod/cobblemon)
   - [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)
     —— **仅客户端**，装上才有齿轮面板；服务端不需要
4. 把 mod 和依赖放进服务端的 `mods` 目录。
5. 在 `./config/skiesclear/config.json` 里配置清理计时器，或用 `/skiesclear gui` 直接在游戏里改。

## 命令与权限

| 命令 | 说明 | 权限节点 | 等级 |
|---|---|---|---|
| `/skiesclear` | 列出所有已启用的清理项 | `skiesclear.command.base` | 4 |
| `/skiesclear reload` | 重载模组 | `skiesclear.command.reload` | 4 |
| `/skiesclear info <id>` | 查看指定清理项的信息 | `skiesclear.command.info` | 4 |
| `/skiesclear force <id> [announce]` | 立刻执行一次清理 | `skiesclear.command.force` | 4 |
| `/skiesclear debug` | 切换调试日志 | `skiesclear.command.debug` | 4 |
| `/skiesclear language [locale]` | 查看或修改提示语言 | `skiesclear.command.language` | 3 |
| `/skiesclear gui [id]` | 打开游戏内设置菜单 | `skiesclear.command.gui` | 3 |
| `/skiesclear broadcast [option]` | 查看或切换播报开关 | `skiesclear.command.broadcast` | 3 |

`/skiesclear broadcast` 接受 `startup`、`join`、`first_join`（切换开关）和 `reset`（忘掉所有
首次进服记录，让每个玩家的下次进服重新计为「首次」）。

`/skiesclear force` 会重置计时器，菜单里的 TNT 不会。`/skiesclear debug` 立即写盘，菜单里的
铃铛只改内存。

## 游戏内设置菜单

`/skiesclear gui`（可带计时器 id）打开一个双箱菜单，改配置不用手写 JSON。它是原版箱子菜单，
所以原版客户端直接渲染和操作 —— 客户端不需要任何额外东西。

| 图标 | 作用 |
|---|---|
| 时钟 / 比较器（顶部中央） | 总览：名称、id、是否启用、倒计时、是否有未保存改动 |
| 时钟周围的染料 | `-60 / -10 / -1 / +1 / +10 / +60` 秒，以及「重置为默认」 |
| 拉杆、命名牌、物品展示框、鸡蛋、指南针 | 切换 启用 / 清有名字的 / 清持久化的 / 清乘客 / 只在列出的维度播报 |
| TNT | 立刻执行一次清理 |
| 漏斗、箭、下界之星 | 切换 `items`、`entities`、`cobblemon` 三类清理对象 |
| 屏障 / 纸 / 铃铛 / 绿宝石 / 书 | 关闭、放弃、切调试日志、保存、循环切换语言 |

- 点击全部是按钮，菜单从不发放物品，所以拿不走也放不进任何东西。
- 步进按钮和语言按钮右键反向。
- 改动只在内存里，按「保存」才写盘，所以可以随便试然后「放弃」。
- 周期限制在 1 秒 ~ 7 天，默认 5 分钟（300 秒）。
- 保存同时修掉了旧版「写配置会把每个 `clearables` 块清空」的问题：这个 map 现在有配套的
  serializer，规则能挺过保存。

## 关于入口点

两个入口点类（`SkiesClear`、`SkiesClearClient`）都是普通 Kotlin `class`，不是 `object`。
Kotlin `object` 编译出的类构造函数是 **private**，而 Fabric 的默认入口点适配器用
`Constructor.newInstance()` 实例化，拒绝 private 构造并抛 `IllegalAccessException` ——
游戏会在「Initializing game」阶段、任何界面出现之前崩溃。静态成员放在 `companion object` 里，
调用方写法完全一样。

## Mod Menu / Cloth Config 面板

装上 [Mod Menu](https://modrinth.com/mod/modmenu) 和
[Cloth Config](https://modrinth.com/mod/cloth-config) 后，模组列表里 SkiesClear 旁边会出现齿轮按钮。
面板编辑的正是同一份 `config/skiesclear/config.json`，只是用真正的控件 —— 滑块、开关、字符串
列表 —— 而不是可点击的图标。

| 分类 | 内容 |
|---|---|
| 常规 | 调试日志、提示语言、配置文件路径，外加一个「本客户端」子分类 |
| 本客户端 | 显示提示、关闭前确认、面板自身语言 |
| 播报 | 开服 / 进服 / 首次进服开关，以及已记录玩家数 |
| 每个清理项 | 启用、周期滑块**加精确秒数输入框**、保护期、显示名、维度、各过滤项、各 clearable |

- 两个 mod 都是可选的：SkiesClear 不打包它们，只在 `fabric.mod.json` 里 `recommends`。
  没装时模组行为完全不变。
- 面板只改内存，按「保存」才写文件，然后重载计时器。
- 客户端偏好（提示开关、确认弹窗、面板语言）存在单独的 `config/skiesclear-client.json`，
  由 AutoConfig 管理，不会传到服务端。
- 所有标签和 tooltip 都走同一套 132 键的语言文件，所以面板跟随游戏语言，不是纯英文。
- 周期**故意有两个控件**：滑块用来粗调，下面的「精确秒数」用来精调。同一次保存里两者都改过时，
  以输入框为准。
- 每个 clearable 区块内都直接印出规则语法，黑白名单格式
  （`minecraft:*`、`#tag=name`、`#nbt={"NoAI":1b}`）不用猜。

## 播报

默认周期 5 分钟，意味着玩家可能站在服务器里好几分钟都看不出模组存在。三种播报解决这个问题，
都在 `config.json` 的 `broadcast` 块里配，都按玩家各自的语言渲染。

| 触发时机 | 配置键 | 默认 | 行为 |
|---|---|---|---|
| 开服 | `broadcast.startup` | `true` | 一行控制台日志 + 一条游戏内提示（提示只在开服瞬间已有人在线时才送达） |
| 每次进服 | `broadcast.join` | `true` | 给进服玩家发一条日程摘要 |
| 首次进服 | `broadcast.first_join` | `true` | 额外一条一次性欢迎，记录在 `config/skiesclear/players.json` |

- 即使 `startup` 是 `false`，控制台仍会打印 `[SkiesClear] Started with N active clear task(s)`，
  让服主始终有证据证明日程在跑。
- 每条播报都是若干行（`startup_lines`、`join_lines`、`first_join_lines`），用和其它消息一样的
  `<lang:...>` 引用；把列表清空即可静音。
- 每个已启用的清理项会自动追加一行，所以玩家看到的是具体日程，不只是一个数量。
- `/skiesclear broadcast reset` 清空 `players.json`，之后每个玩家的下次进服都重新算首次。
  改完欢迎文案想重放一遍时很有用。

## 多语言

所有面向玩家的消息都从 `assets/skiesclear/lang/<locale>.json` 解析，所以一台服务器可以同时服务
不同语言的玩家。

- `language: "auto"`（默认）按玩家客户端上报的语言渲染每条消息。
- `language: "zh_cn"`（或任何已内置的 locale）给所有人强制一种语言。
- 游戏内用 `/skiesclear language <locale>` 改，或直接编辑 `config/skiesclear/config.json` 的
  `language`。

内置 locale：`en_us`、`zh_cn`、`zh_tw`、`ja_jp`、`ko_kr`、`ru_ru`、`uk_ua`、`de_de`、`fr_fr`、
`es_es`、`es_mx`、`pt_br`、`it_it`、`pl_pl`、`tr_tr`、`nl_nl`、`sv_se`、`cs_cz`、`vi_vn`、`th_th`、`id_id`。

没有独立文件的区域变体走回退链，例如 `zh_hk` → `zh_tw`、`pt_pt` → `pt_br`、`es_mx` → `es_es`、
`nb_no` → `no_no` → `en_us`。

### 新增一种语言

1. 复制 `assets/skiesclear/lang/en_us.json` 为 `<locale>.json`，翻译所有值，键一个都别删。
2. 重新构建，别的地方都不用改。
3. 缺的键会回退到 `en_us`，所以翻译到一半的文件也是安全的。

### 在配置文本里引用语言键

`config.json` 里任何消息都可以用 `<lang:...>` 引用语言键：

```json
"messages": {
  "clear": ["", "<lang:message.clear.success>", ""],
  "warnings": { "10": ["", "<lang:message.clear.warning>", ""] }
}
```

支持传参：`<lang:command.force.success id="example">`。

## 计划中的功能

- 命令 hook（清理前后执行配置里的命令，见上游 issue #1）
- 在菜单里新建 / 删除清理项
- BossBar 倒计时
- 清理统计（上次清了多少、什么时候）
- 玩家附近的实体保护

有想法欢迎提 issue。

## 问题反馈

请在本仓库的 [Issues](../../issues) 提出。上游的 Discord 与本分支无关，那边的讨论本分支看不到。

## 授权

Apache License 2.0，全文见 [LICENSE](LICENSE)。改动清单与上游授权状况的说明见
[ATTRIBUTION.md](ATTRIBUTION.md)。

原项目 SkiesClear © PokeSkies。本分支的修改 © ardd_017。
