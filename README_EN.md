# SkiesClear Plus

<img align="right" width="140" src="docs/icon.png" alt="SkiesClear Plus"/>

[中文](README.md) ｜ **English**

> **This is a fork.** SkiesClear Plus is a modified version of
> [SkiesClear](https://github.com/PokeSkies/SkiesClear) by **PokeSkies**, used under the
> Apache License 2.0. It is not affiliated with or endorsed by the original author.
> Maintained by **ardd_017**. See [ATTRIBUTION.md](ATTRIBUTION.md) for the full list of changes
> and the licence situation inherited from upstream.
>
> ⚠️ **Do not install this alongside upstream SkiesClear.** This fork keeps the technical mod id
> `skiesclear` so that existing `config/skiesclear/config.json` files and the `players.json`
> first-join history carry over unchanged. Two mods with the same id cannot load at once — remove
> the upstream jar before adding this one.

<img width="100" height="50" src="https://i.imgur.com/c1DH9VL.png" alt="Requires Fabric Kotlin"/> <img src="https://img.shields.io/badge/Enviroment-Server-purple" alt="Enviroment Server"/>

A Fabric server-sided entity clearing mod: timed automatic removal of dropped items, mobs and even Cobblemon, with a countdown before every clear.

Full documentation (English) follows below. A complete Simplified Chinese manual lives in [使用说明.md](使用说明.md).

## What this fork adds

Everything below is new relative to upstream `1.1.2`:

- **21 languages, resolved per player.** Every player-facing string lives in
  `assets/skiesclear/lang/<locale>.json` and is rendered in the joining player's own language,
  with fallback chains for regional variants (`zh_hk` → `zh_tw`, `pt_pt` → `pt_br`, …).
- **In-game settings menu.** `/skiesclear gui [id]` opens a double-chest UI that edits a timer
  without touching `config.json`. Vanilla clients only — no client mod needed.
- **Mod Menu / Cloth Config panel.** Optional client-side gear button for the same config, with
  sliders, switches, string lists, an exact-seconds field and the grace period.
- **Announcements.** Server-start, per-join and first-join notices, so players can tell the mod is
  running before the first five-minute timer elapses.
- **New commands.** `/skiesclear gui`, `/skiesclear language`, `/skiesclear broadcast`.
- **Grace period.** `grace_seconds` (default 15) skips anything younger than that, so a block
  broken the instant a timer fires does not vanish the same tick. `0` disables it.
- **Reload keeps the countdown.** Changing the language or saving a menu no longer restarts every
  timer; only an actual `interval` change does.

Plus a set of bug fixes — most notably a config serializer that used to blank out every
`clearables` block on save, and a whitelist that consulted the blacklist. All of them are listed in
[ATTRIBUTION.md](ATTRIBUTION.md).

## Features

- Create practically infinite clear timers *(idk, haven't tested that)*
- Limit timers to certain dimensions
- Customizable clear messages and sound
- Customizable warning messages and sound (choose when your warnings appear)
- Customize which types of entities should be cleared, including:
  - Items (whitelists and blacklists)
  - Any Entities (whitelists and blacklists, plus `#tag=` / `#nbt=` matching)
  - Cobblemon Pokemon (whitelists and blacklists, with optional flags like shiny, legendary, and aspects)
- Announcements so players can tell the mod is running: one at server start, one on every join, and
  a one-off welcome for a first-time player
- An in-game settings menu (`/skiesclear gui`) and an optional Mod Menu gear panel
- 21 shipped languages, resolved per player

## Installation

1. Build from source with `./gradlew build`, or take the jar from a release of this fork. The
   upstream build is on [Modrinth](https://modrinth.com/mod/skiesclear) and does **not** contain
   any of the features listed above.
2. Download all required dependencies:
   - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
   - [Fabric Permissions API](https://github.com/PokeSkies/fabric-permissions-api)
3. Download any optional dependencies:
   - [Cobblemon](https://modrinth.com/mod/cobblemon)
   - [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)
     — **client only**, adds the gear settings panel; the server does not need them
4. Install the mod and dependencies into your server's `mods` folder.
5. Configure your clear timers in the `./config/skiesclear/config.json` file, or run
   `/skiesclear gui` to edit them in-game.

## Commands/Permissions

| Command                                 | Description                                        | Permission                   |
|-----------------------------------------|----------------------------------------------------|------------------------------|
| /skiesclear                             | List every enabled clear timer                     | skiesclear.command.base      |
| /skiesclear reload                      | Reload the mod                                     | skiesclear.command.reload    |
| /skiesclear info <timer_id>             | View info related to the specified timer           | skiesclear.command.info      |
| /skiesclear force <timer_id> [announce] | Force a clear timer to activate                    | skiesclear.command.force     |
| /skiesclear debug                       | Toggle the debug mode for more insight into errors | skiesclear.command.debug     |
| /skiesclear language [locale]           | Show or change the message language                | skiesclear.command.language  |
| /skiesclear gui [timer_id]              | Open the in-game settings menu                     | skiesclear.command.gui       |
| /skiesclear broadcast [option]          | Show or toggle the join/startup announcements      | skiesclear.command.broadcast |

`/skiesclear broadcast` accepts `startup`, `join`, `first_join` (toggle) and `reset` (forget every
recorded first join, so the next join of each player counts as new).

`/skiesclear force` resets the timer; the TNT icon in the chest menu does not. `/skiesclear debug`
writes the config to disk immediately; the bell icon in the menu only changes it in memory.

## In-game settings menu

`/skiesclear gui` (optionally with a timer id) opens a double-chest menu that edits a clear timer
without touching `config.json` by hand. It is a plain vanilla chest menu, so vanilla clients render
and drive it — nothing extra is needed on the client side.

| Icon | Action |
|------|--------|
| Clock / comparator (top centre) | Header: entry name, id, enabled state, countdown, unsaved-changes warning |
| Dyes around the clock | `-60 / -10 / -1 / +1 / +10 / +60` seconds and *Reset to default* |
| Lever, name tag, item frame, egg, compass | Toggle enabled, clear-named, clear-persistent, clear-passengers, warn-in-listed-dimensions |
| Shield / grey pane | Grace period, ±5 s (shield = on, grey pane = off) |
| TNT | Run the clear right now |
| Hopper, arrow, nether star | Toggle the `items`, `entities` and `cobblemon` clearables |
| Barrier / paper / bell / emerald / book | Close, discard, toggle debug logging, save, cycle language |

- Clicks are buttons only — the menu never hands out items, so nothing can be taken or moved.
- Stepper and language buttons reverse direction on a right click.
- Edits stay in memory until *Save*, so a player can experiment and then *Discard*.
- The interval clamps to 1 second … 7 days; the default is 5 minutes (300 s).
- Saving also fixes the older behaviour where writing the config blanked out every `clearables`
  block: the map now has a matching serializer, so rules survive a save.

## A note on entrypoints

Both entrypoint classes (`SkiesClear` and `SkiesClearClient`) are plain Kotlin `class`es, not
`object`s. A Kotlin `object` compiles to a class with a **private** constructor, and Fabric's default
entrypoint adapter instantiates entrypoints through `Constructor.newInstance()`, which rejects
private constructors with `IllegalAccessException` — the game crashes during "Initializing game"
before any screen appears. Static members live in `companion object`, so call sites read the same.

## Mod Menu / Cloth Config panel

Installing [Mod Menu](https://modrinth.com/mod/modmenu) and
[Cloth Config](https://modrinth.com/mod/cloth-config) adds a gear button next to SkiesClear in the
mod list. The panel edits exactly the same `config/skiesclear/config.json` as the chest menu, but
with real widgets — sliders, switches and string lists — instead of clickable icons.

| Category | Contents |
|----------|----------|
| General | Debug logging, message language, the config file path, plus a *This client* sub-category |
| This client | Show tooltips, confirm before closing, language of the screen itself |
| Announcements | Startup / join / first-join toggles, plus how many players are recorded |
| One per clear entry | Enabled, interval slider **plus an exact-seconds field**, grace period, display name, dimensions, filters, clearables |

- Both mods are optional: SkiesClear does not bundle them, and only `recommends` them in
  `fabric.mod.json`. Without them the mod behaves exactly as before.
- The panel edits the config in memory and only writes the file on *Save*, then reloads the timers.
- Client-only preferences (tooltips, confirmation prompt, screen language) live in a separate
  `config/skiesclear-client.json` managed by AutoConfig, so they never travel to a server.
- Every label and tooltip is localized through the same 132-key set, so the panel follows the
  in-game language instead of being English-only.
- The interval has two widgets on purpose: the slider is for coarse drags, the *exact seconds* field
  below it is for precision. When both change in the same save, the field wins.
- Each clearable block prints the rule syntax inline, so the blacklist/whitelist format
  (`minecraft:*`, `#tag=name`, `#nbt={"NoAI":1b}`) never has to be guessed.

## Announcements

A five-minute default interval means a player can stand on the server for minutes without any sign
the mod exists. Three announcements fix that, all configurable under the `broadcast` block of
`config.json` and all rendered per player, so everyone reads them in their own language.

| Trigger | Config key | Default | What it does |
|---------|------------|---------|--------------|
| Server start | `broadcast.startup` | `true` | Console line plus an in-game notice (the notice only reaches players if any are already online) |
| Every join | `broadcast.join` | `true` | Schedule summary sent to the joining player |
| First join | `broadcast.first_join` | `true` | Extra one-off welcome, tracked in `config/skiesclear/players.json` |

- The console line `[SkiesClear] Started with N active clear task(s)` is printed even when
  `startup` is `false`, so the operator always has proof the schedule is live.
- Each announcement is a list of lines (`startup_lines`, `join_lines`, `first_join_lines`) using the
  same `<lang:...>` references as any other message; emptying a list silences that notice too.
- One extra line per active clear entry is appended automatically, so a player sees the schedule
  itself and not just a count.
- `/skiesclear broadcast reset` clears `players.json`; the next join of every player is then treated
  as a first join again. Useful after rewriting the welcome text.

## Languages

Every player-facing message is resolved from `assets/skiesclear/lang/<locale>.json`, so a single
server can serve players in different languages at the same time.

- `language: "auto"` (default) renders each message in the locale the player's client reports.
- `language: "zh_cn"` (or any shipped locale) forces one language for everybody.
- Change it in-game with `/skiesclear language <locale>`, or edit `language` in `config/skiesclear/config.json`.

Shipped locales: `en_us`, `zh_cn`, `zh_tw`, `ja_jp`, `ko_kr`, `ru_ru`, `uk_ua`, `de_de`, `fr_fr`,
`es_es`, `es_mx`, `pt_br`, `it_it`, `pl_pl`, `tr_tr`, `nl_nl`, `sv_se`, `cs_cz`, `vi_vn`, `th_th`, `id_id`.

Regional variants without a file of their own resolve through a fallback chain, e.g.
`zh_hk` → `zh_tw`, `pt_pt` → `pt_br`, `es_mx` → `es_es`, `nb_no` → `no_no` → `en_us`.

### Adding a language

1. Copy `assets/skiesclear/lang/en_us.json` to `<locale>.json` and translate the values, keeping every key.
2. Rebuild the mod; nothing else needs to change.
3. Missing keys fall back to `en_us`, so a partially translated file is safe.

### Using language keys in config text

Any message inside `config.json` may reference a language key with the `<lang:...>` tag:

```json
"messages": {
  "clear": ["", "<lang:message.clear.success>", ""],
  "warnings": { "10": ["", "<lang:message.clear.warning>", ""] }
}
```

Arguments are supported: `<lang:command.force.success id="example">`.

## Support

Open an issue in [this repository](../../issues). The upstream Discord is unrelated to this fork and
its discussions are not visible here.

## Licence

Apache License 2.0 — full text in [LICENSE](LICENSE). The list of changes and the note on the
inherited upstream licence declaration are in [ATTRIBUTION.md](ATTRIBUTION.md).

Upstream SkiesClear © PokeSkies. Modifications in this fork © ardd_017.
