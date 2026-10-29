# SkiesClear Plus 1.0.0

首个公开版本。基于 **PokeSkies** 的 [SkiesClear](https://github.com/PokeSkies/SkiesClear) 1.1.2
（Apache-2.0）重写而来，改动清单见 [ATTRIBUTION.md](https://github.com/ardd017/SkiesClearPlus/blob/main/ATTRIBUTION.md)。

> ⚠️ 不要和原版 SkiesClear 同时安装。本分支沿用技术标识 `skiesclear`，为的是让已有的
> `config/skiesclear/config.json` 与 `players.json` 原样继承。装本版前请先删掉原版 jar。

## 相对上游 1.1.2 的变化

### 新增

- **21 种语言，按玩家各自渲染。** 所有面向玩家的文案移到
  `assets/skiesclear/lang/<locale>.json`，按进服玩家自己的客户端语言渲染，区域变体走回退链
  （`zh_hk` → `zh_tw`、`pt_pt` → `pt_br`、`es_mx` → `es_es`、`nb_no` → `no_no` → `en_us`）。
- **游戏内箱子菜单。** `/skiesclear gui [id]` 打开双箱界面，改周期、开关、黑白名单不用手写 JSON。
  纯原版客户端即可，不需要任何客户端 mod。
- **Mod Menu / Cloth Config 面板。** 可选客户端齿轮按钮，同一个配置，带滑块、开关、字符串列表、
  精确秒数输入框、保护期。两个 mod 都不打包，只在 `fabric.mod.json` 里 `recommends`。
- **播报。** 开服、每次进服、首次进服三种提示，让玩家在第一个 5 分钟倒计时走完前就知道模组在跑。
- **保护期 `grace_seconds`**（默认 15）。跳过比这更年轻的实体，避免「倒计时归零那一秒刚挖出来的
  方块直接消失」。填 `0` 关闭。
- **新命令**：`/skiesclear gui`、`/skiesclear language`、`/skiesclear broadcast`。
- **模组图标**。上游没有图标（`fabric.mod.json` 无 `icon` 字段，jar 里也没有图），本版补了一个
  自绘的夜空方块 + 线稿垃圾桶 + 金色 `+` 角标，用 `tools/make_icon.py` 生成，没有复用上游素材。

### 修复

| 问题 | 说明 |
|---|---|
| 保存配置清空清理规则 | `clearables` 的 map 只有反序列化器、没有序列化器，任何一次写盘都把它写成 `{}`。现在改用读写双实现，规则能挺过保存 |
| 白名单查了黑名单 | `CobblemonClearable.isWhitelisted` 里 `#busy` / `#uncatchable` 两处误用 `blacklist.contains(...)` |
| 计时器被 reload 重置 | 切语言或保存菜单会让所有倒计时从头开始。现在只有真的改了 `interval` 才重开 |
| 21 tick 漂移 | `if (ticks++ > 20)` 实际每 21 tick 跳一次，300 秒配置跑成 315 秒 |
| NBT 规则只认第一个键 | `EntityClearable` 的 `passes` 在第一个键命中时就置位，多键规则退化为「任一命中」 |
| 未知 clearable 键会让整份配置加载失败 | 改为打日志跳过 |

## 安装

1. 必装：[Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)、
   [Fabric Permissions API](https://github.com/PokeSkies/fabric-permissions-api)
2. 可选：[Cobblemon](https://modrinth.com/mod/cobblemon)；
   客户端另加 [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)
3. 把 jar 丢进服务端 `mods`，改 `config/skiesclear/config.json` 或用 `/skiesclear gui`

## 文件

| 文件 | 说明 |
|---|---|
| `SkiesClearPlus-fabric-1.21.1-1.0.0.jar` | 正式版，装这个 |
| `SkiesClearPlus-1.0.0-sources.jar` | 源码包 |

## 文档

- [README 中文](https://github.com/ardd017/SkiesClearPlus/blob/main/README.md) ｜
  [English](https://github.com/ardd017/SkiesClearPlus/blob/main/README_EN.md)
- [使用说明（中文手册）](https://github.com/ardd017/SkiesClearPlus/blob/main/使用说明.md)
- [ATTRIBUTION（改动清单与授权说明）](https://github.com/ardd017/SkiesClearPlus/blob/main/ATTRIBUTION.md)

---

**English summary.** First public build, rewritten from SkiesClear 1.1.2 (Apache-2.0). Adds 21
per-player languages, a vanilla double-chest settings menu (`/skiesclear gui`), an optional Mod
Menu / Cloth Config panel, startup/join/first-join announcements, a 15-second grace period so
freshly dropped items survive the tick a timer fires, and a hand-drawn mod icon (upstream ships
none). Fixes a config serializer that blanked every
`clearables` block on save, a whitelist that consulted the blacklist, reload resetting every
countdown, a 21-tick drift in the scheduler, and multi-key NBT rules collapsing into "any key
matches". Do not install alongside upstream SkiesClear — the mod id is shared on purpose.
