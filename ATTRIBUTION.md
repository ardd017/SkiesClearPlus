# Attribution — SkiesClear Plus

**SkiesClear Plus** is a fork of [SkiesClear](https://github.com/PokeSkies/SkiesClear) by
**PokeSkies** (GitHub: [@PokeSkies](https://github.com/PokeSkies), commit author `Stampede2011`).

- **Upstream:** <https://github.com/PokeSkies/SkiesClear> — Apache License 2.0
- **Upstream version forked from:** `1.1.2` (commit `19a95b4`, "Fixed crash relating to sending
  players notification messages")
- **This fork:** maintained by **ardd_017**
- **License:** Apache License 2.0 (see `LICENSE`)

The original project is licensed under the Apache License 2.0, which permits modification and
redistribution provided that copyright notices are retained and modified files are marked as
changed. Both requirements are met: `LICENSE` is kept verbatim, the upstream author remains listed
in `fabric.mod.json`, and every file changed by this fork carries a header naming the original
author and this fork's maintainer.

---

## What is original, what is new

This is not a re-skin. The upstream `1.1.2` release was a 39-class server mod whose player-facing
text was hard-coded English strings scattered across the command classes. Roughly 80% of the Kotlin
source in this fork was written for it — the upstream command, config and clearing logic survives,
but most of it was rewritten rather than patched.

### New in this fork

| Area | What was added |
|---|---|
| Localization layer | `utils/I18n.kt` — classpath scanner, per-player locale resolution (`ServerPlayer.clientInformation().language`), fallback chains (`zh_hk`→`zh_tw`, `pt_pt`→`pt_br`, `es_mx`→`es_es`, `nb`/`nn`→`no_no`), `<lang:key arg="value">` tag expansion, `{placeholder}` and `%placeholder%` substitution |
| Language files | 21 locales × 132 keys, generated through `tools/gen_lang.py` plus sidecar tables (`gui_lang.json`, `menu_lang.json`) |
| In-game chest menu | `gui/SettingsMenu.kt` — a 9×6 `MenuType.GENERIC_9x6` menu, click-to-edit buttons, right-click reversal, live countdown refresh; no client mod required |
| Mod Menu panel | `src/client/` — an AutoConfig-managed client preference file plus a hand-written Cloth Config screen that edits the server config over sliders, switches, string lists and an exact-seconds field |
| Grace period | `ClearConfig.graceSeconds` + `Clearable.isWithinGrace` — skips entities younger than N seconds, default 15 |
| Announcements | `utils/Announcer.kt`, `utils/PlayerTracker.kt`, `config/BroadcastConfig.kt` — server-start, per-join and first-join notices, with `players.json` tracking |
| Commands | `/skiesclear gui`, `/skiesclear language`, `/skiesclear broadcast` |
| Config model | `broadcast` block, `display_name`, `warning_seconds`, `grace_seconds`, mutable fields for live editing |

### Bug fixes carried in this fork

| Bug | Effect before |
|---|---|
| `Clearable` had a deserializer but no serializer | Every save wrote `clearables: {}` and silently deleted all blacklist/whitelist rules |
| `ClearManager` used `if (ticks++ > 20)` | Timers drifted to 21 ticks; a 300 s interval fired every 315 s |
| `CobblemonClearable.isWhitelisted` consulted `blacklist` | Whitelists behaved as a second blacklist |
| `EntityClearable` NBT matching set `passes = true` on the first key | Multi-key `#nbt=` rules matched when only one key matched |
| `SkiesClearClient` was a Kotlin `object` | private constructor → `IllegalAccessException` from Fabric's entrypoint adapter, crash on startup |
| `!flag.also { target = it }` | `also` bound to the negated value, so toggles always wrote `false` |
| `ClearManager.reload()` rebuilt every task | Changing the language or saving either menu reset every countdown back to a full interval |

### Known upstream issue left unfixed

`utils/FlexibleListAdaptorFactory.kt` writes a single-entry list as a bare element, so
`dimensions: ["minecraft:overworld"]` round-trips into `dimensions: "minecraft:overworld"`. Reading
still works (the adapter handles the scalar form), so this is cosmetic churn rather than data loss,
and the file is byte-identical to upstream. It is documented rather than patched so the diff stays
attributable.

### Upstream code retained

The clearing engine itself is PokeSkies' design and largely his code: `ClearManager`, `ClearTask`,
`config/ClearConfig.kt`, `config/MainConfig.kt`, `config/clearables/*`, the Gson adaptors, the
command tree structure and the `config.json` schema. Those files were edited — localized, made
mutable, hardened, wired to the new subsystems — but their architecture and the majority of their
lines originate upstream.

---

## Icon artwork

The icon is **not** a modification of upstream artwork. Upstream ships no icon at all —
its `fabric.mod.json` has no `"icon"` field and its jar contains no image files. The Modrinth
listing shows a picture uploaded separately to the platform, which is not part of the source
or the released jar.

This fork's icon was drawn from scratch with Pillow primitives by
[`tools/make_icon.py`](tools/make_icon.py) and is generated, not traced: a night-sky
gradient with procedurally placed stars, a line-art trash can drawn from seven straight
segments, and a gold `+` badge. It borrows the upstream *idea* — a trash can, the obvious
visual for a clearing mod — and the cyan accent, but no pixels, paths or gradients are shared.
Because the upstream Modrinth icon is offered under MPL-2.0 and this fork is Apache-2.0,
keeping the artwork independently authored avoids mixing licence obligations.

Regenerate the whole set with `python tools/make_icon.py`. Files involved:

| Path | Purpose |
|---|---|
| `src/main/resources/assets/skiesclear/icon.png` | 256 px, shipped inside the jar |
| `docs/icon.png` | same image, referenced by both READMEs |
| `docs/icon-large.png` | 1024 px master, for Modrinth / GitHub social preview |
| `docs/icon-rounded.png` | same art, rounded corners, for platform avatars |
| `docs/banner.png` | 1280×400 header strip |

---

## Note on the upstream license declaration

The upstream repository is inconsistent, and this fork follows the `LICENSE` file:

| Source | Declares |
|---|---|
| `LICENSE` (present since the initial commit `2cb5806`, 2024-02-25) | Apache License 2.0 |
| `fabric.mod.json` → `"license"` | `All-Rights-Reserved` |
| Modrinth project page | MPL-2.0 |

The Apache 2.0 text in `LICENSE` is the only formal grant among the three, so this fork is released
under Apache-2.0 and the `fabric.mod.json` field was corrected from the inherited
`All-Rights-Reserved` to `Apache-2.0`. If the upstream author clarifies the intended license,
this fork will follow it.

The basis for that correction, stated so it can be checked line by line:

- The `LICENSE` file at the repository root is the Apache License 2.0 in full, and it has been
  present since the initial commit `2cb5806` (2024-02-25) — never replaced, never removed.
- GitHub's licence detection for the repository reports `apache-2.0`.
- Apache-2.0 §2 grants "a perpetual, worldwide, non-exclusive, no-charge, royalty-free,
  irrevocable copyright license to reproduce, prepare Derivative Works of, publicly display,
  publicly perform, sublicense, and distribute the Work and such Derivative Works in Source or
  Object form."
- The `All-Rights-Reserved` string in `fabric.mod.json` is Fabric template boilerplate that was
  never updated to match `LICENSE`. At worst, Apache-2.0 and `All-Rights-Reserved` conflict while
  the full licence text explicitly grants redistribution — resolving that in favour of the file
  actually shipped with the source is the conservative reading, not the permissive one.

The upstream `LICENSE` still contains the unfilled Apache appendix boilerplate
(`Copyright [yyyy] [name of copyright owner]`), so no copyright holder is named in the license text
itself. `fabric.mod.json` names both authors instead.

---

## Contact

If you are the upstream author and would prefer a different arrangement — a rename, a removal of
this fork's public listing, or different attribution wording — open an issue and it will be
honoured.
