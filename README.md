<div align="center">

<img src="https://cdn.modrinth.com/data/yitVG9Jx/images/7280dbec79adfa817ef8696b60f0fedfe8960891.png" alt="BattleCorrection item editor" width="800">

# BattleCorrection | 战斗修正

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_battlecorrection_downloads.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/battlecorrection?style=for-the-badge&logo=modrinth&label=Modrinth)](https://modrinth.com/mod/battlecorrection)
[![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.x-orange?style=for-the-badge)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey?style=for-the-badge)](LICENSE)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

This is the **1.20.1** branch (BattleCorrection 3.0.0). The Minecraft 1.12.2 version (1.3.0) is on the [`1.12.2` branch](https://github.com/Roinflam/BattleCorrection/tree/1.12.2).

本分支是 **1.20.1** 版（战斗修正 3.0.0），Minecraft 1.12.2 版（1.3.0）在 [`1.12.2` 分支](https://github.com/Roinflam/BattleCorrection/tree/1.12.2)。

</div>

<details open>
<summary><h2>English</h2></summary>

### About

BattleCorrection (BC) is for people who build RPG maps, modpacks and servers and want to set the combat numbers themselves. It adds 15 attributes such as critical chance, lifesteal and dodge that work on players and mobs, global multipliers for PVP, mob health, invulnerability frames and environmental damage, and an in-game editor for item names, lore, enchantments, attributes and raw NBT.

- 15 attributes that work on players and mobs alike: critical hits with overflow, two kinds of lifesteal, dodge chance, flat damage reduction, magic, arrow and projectile damage, bow and use speed, jump height.
- Global settings for PVP, damage dealt and taken by players per damage type, environmental damage, invulnerability frames, attack cooldown, mob health and hunger.
- Any single item can become a Curios accessory, with attributes that only count while it is worn.
- An item editor for operators: coloured names and gradients, lore, enchantments, attributes, an NBT tree, an SNBT text editor and templates. It also edits entity and block entity NBT.

The item editor, Curios marks, environmental damage settings and third-party magic recognition are new in 3.0.0.

### Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) 47.x for 1.20.1 (Java 17).
2. Download BattleCorrection 3.0.0 from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/battlecorrection) or [Modrinth](https://modrinth.com/mod/battlecorrection).
3. Put the jar into the `mods` folder of **both the client and the server**, same version on both sides.
4. Optional: [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) 5.14.1 or newer for the accessory features.

Cloth Config API, used for the config screen, is already inside the jar, so you don't need to download it.

### Item editor

Operators (permission level 2) in creative mode press `I` or run `/battlecorrection edit`. The editor opens on the held item, on the item under the mouse in any open inventory or chest, or, with an empty hand, on the entity or block entity you are looking at. `O` opens the SNBT page directly and `P` the NBT tree. The server checks permission, creative mode, the target and the NBT size again on every save, and `itemEditorEnabled = false` turns the editor off completely.

The editor is drawn by the mod itself: page tabs on the left, the current page in the middle, a live tooltip preview on the right, and Copy command, Import, the NBT size, Cancel and Save along the bottom. The screenshots are from 1.20.1; what differs on 1.12.2 is listed at the end of this section.

#### Name and lore

Type `&` colour codes and `&#RRGGBB` hex colours, or click the swatches and format buttons to insert them at the cursor. Clear strips colour and format codes from the selection, and pasted `§` signs become `&`. Insert writes one colour; Gradient blends two colours letter by letter across the selected text, or the whole line when nothing is selected.

![Item editor, Name page](https://cdn.modrinth.com/data/cached_images/0817d9b95e6f57babdc6107d83d2efc533bff3b0.png)

The Lore page has one row per line, with move up / down, delete and add. `Enter` splits a line at the cursor, `Backspace` at the start of a line joins it to the one above, and a multi-line paste fills several rows. Vanilla style switches between plain white text and vanilla's purple italic. Only the lines you change are rebuilt: lines holding things a text box cannot show (click events, hover text, custom fonts) are marked, and stay intact as long as you leave them alone.

![Item editor, Lore page](https://cdn.modrinth.com/data/cached_images/0f3f02192abb4c79dc04c789aa53861b1226be0b.png)

The palette button opens a colour picker with a saturation / brightness square, hue bar, RGB sliders, hex input and your 16 most recent colours. Gradients take 2 to 8 colour stops, with ten presets such as rainbow, fire and ocean, and a preview of the whole line.

![Colour palette](https://cdn.modrinth.com/data/cached_images/867425900f5954e15e82fa8a568a2104cf928be2.png)

#### Enchantments and attributes

The item's enchantments are on the left, each next to its vanilla maximum level: left-click changes the level, right-click removes it. On the right, search every enchantment in the game, other mods' included, and click one to add it. Curses are shown in red, and hovering shows what an enchantment does. Levels go up to 255 (32767 on 1.12.2); levels above the vanilla maximum and enchantments vanilla would not allow on the item still apply.

![Item editor, Enchantments page](https://cdn.modrinth.com/data/cached_images/eaa78f8d886fd72e26297794b4e0a9be82863718.png)

Attributes work the same way: every registered attribute, vanilla or from any mod, in one searchable list, with a description on hover. Set the amount, operation and slot; typing `20%` switches to a percentage. Choosing a Curios slot as the slot also lets the item be worn there.

![Item editor, Attributes page](https://cdn.modrinth.com/data/cached_images/aa5211549f92643a17dc6fa3be16576a024f5204.png)

#### Tags and slots

The Tags page sets Unbreakable, enchantment glint, hide flags, damage, anvil repair cost, count (1 to 127) and, on 1.20.1, CustomModelData. The Curios page is covered under Accessory marks below.

My Slots (1.20.1) is for testing accessories and changes your own Curios slots. It lists every slot type you have with its count: change a count (0 removes that type), or type an id to add a slot, even one no mod has registered (it just has no icon). Curios only gives slots to players who already have at least one slot type, so with none this page cannot change anything. For testing, `devCurioSlots = true` gives every player one of each of Curios' ten built-in slots; keep it off in normal play.

![Item editor, My Slots page](https://cdn.modrinth.com/data/cached_images/0b65dd55625334b182021811fca99da3ae33d410.png)

#### NBT tree and SNBT

The tree shows every tag with its type. Search keys and values (`Ctrl+F`), jump to a path such as `tag.display.Lore[0]`, copy a node's path, edit keys and values in place, and add, delete, copy, paste, duplicate or move nodes.

![Item editor, NBT tree page](https://cdn.modrinth.com/data/cached_images/650e1da79b7fb21c164d6b84732b5a1e5905645e.png)

The SNBT page edits the whole item as text, with line numbers, highlighting and live error marking; text that does not parse is never applied. It has find (`Ctrl+F`), go to line (`Ctrl+G`), indent, duplicate and move line, Format, Compact and `&` / `§` conversion.

![Item editor, SNBT page](https://cdn.modrinth.com/data/cached_images/c9da546b99fc3959324dafff60511f233d447e19.png)

#### Templates, import and changing the item

A template stores an item's full NBT and count under a name, on your own computer. Apply it whole, or tick only the parts you want (name, lore, enchantments, attributes, curio marks and so on) and keep the rest, for example to give several weapons the same enchantments and attributes.

![Item editor, Templates page](https://cdn.modrinth.com/data/cached_images/dbe44539407b5f8b4f430dcd9fd7b736fc646607.png)

Import reads a `/give` command, an item id with NBT, or item SNBT from the clipboard and replaces the current item. Copy command exports the item as a `/give` command; commands longer than 256 characters cannot be sent from chat, so run those from a command block or function. `F4` or a click on the item icon in the title bar changes the item type and keeps its NBT and count. Search by name or id, or type `@` and a mod id (such as `@battlecorrection`) to list one mod's items.

![Change item window](https://cdn.modrinth.com/data/cached_images/ce451130b542d98026ec98f6cf0306550c22203b.png)

#### Entities and block entities

With an empty hand, `I` edits the entity under the crosshair (players excluded) or a block with a block entity, such as a chest, furnace or spawner. The entity and block entity editors have the NBT tree and SNBT pages and work just like they do for items.

![Entity NBT editor](https://cdn.modrinth.com/data/cached_images/7bb0041a62b5c1c7a6444775202f30e779e92846.png)

#### Undo and saving

`Ctrl+Z` / `Ctrl+Y` undo and redo, and typing within one second counts as one step. `Ctrl+Tab` moves to the next page and `Ctrl+1` to `Ctrl+9` jump straight to one. Closing with unsaved changes lists what changed and asks first.

![Unsaved changes prompt](https://cdn.modrinth.com/data/cached_images/7695e03d256039d4dc3ded1e636d7eb33b8721eb.png)

`Ctrl+S` or Save sends the item to the server. The NBT size of one item is capped by `itemEditorMaxNbtKb` (1 MB on 1.20.1 and 256 KB on 1.12.2 by default), and larger items are not saved. Past about 2 MB the vanilla client cannot read an item at all, and players holding one get disconnected. `F1` lists every shortcut.

![Keyboard shortcuts](https://cdn.modrinth.com/data/cached_images/6c848970fc14c5476a48d39444c312d07a295167.png)

#### On 1.12.2

The editor is the same apart from what that game version allows. 1.12.2 has no hex colours, so the palette offers the 16 vanilla colours (no colour square or RGB sliders), gradients snap to the nearest of them and it remembers 8 recent colours. The attribute list holds vanilla, Forge and this mod's attributes plus any your player has, and other names can be typed in. The Tags page has no CustomModelData, the Curios page is a Baubles page, and there is no My Slots page.

### Attributes

All 15 attributes are registered on every living entity type, so `/attribute`, `/give`, data packs, the editor and any tool that writes attribute modifiers can use them. Each attribute also has a global value in `battlecorrection-attribute.toml` that applies to every entity, mobs included. The examples use Operation 0 (addition), which suits all 15.

| Attribute | Id | Effect (Operation 0) |
|---|---|---|
| Magic Damage | `battlecorrection:magic_damage` | Extra damage on magic hits that have an attacker |
| Arrow Damage | `battlecorrection:arrow_damage` | Extra damage on arrow and trident hits |
| Projectile Damage | `battlecorrection:projectile_damage` | Extra damage on other projectiles, such as fireballs and spell projectiles |
| Damage Immunity | `battlecorrection:immune_damage` | Chance to ignore a hit completely: +0.25 = 25% |
| Ignore Damage | `battlecorrection:ignore_damage` | Subtracts a flat amount from every hit; hits at or below it are cancelled |
| Reduced Fall Damage | `battlecorrection:reduced_fall_damage` | The same, for fall damage only |
| Healing Restoration | `battlecorrection:restore_heal` | Healing multiplier: +0.5 = 150% healing |
| Lifesteal | `battlecorrection:bloodthirsty` | Heals a share of melee damage dealt (players need a full charge): +0.1 = 10% |
| Almighty Lifesteal | `battlecorrection:almighty_bloodthirsty` | The same for every damage type |
| Bow Speed | `battlecorrection:bow_speed` | Bow and crossbow draw speed: +1.0 = twice as fast |
| Use Speed | `battlecorrection:preparation_speed` | Use speed of food, potions and other held items: +1.0 = twice as fast |
| Jump Boost | `battlecorrection:jump_lift` | Extra jump height: 1.0 is about one block |
| Vanilla Critical Damage | `battlecorrection:vanilla_critical_hit_damage` | Adds to the vanilla jump-attack critical: +0.5 turns 150% into 200% |
| Critical Chance | `battlecorrection:custom_critical_chance` | Chance of this mod's own critical hit on melee and projectile hits: 0.3 = 30% |
| Critical Damage | `battlecorrection:custom_critical_damage` | Added to the critical multiplier, which is 2.0 by default: +1.0 = 3× damage |

Attributes that start at 0 (the three damage bonuses, Ignore Damage, Reduced Fall Damage, Jump Boost, Critical Chance and Critical Damage) only react to Operation 0. Attributes that start at 1.0 count what is above 1.0, so an amount of 0.25 on its own gives the same bonus with Operation 0 or 1.

Critical chance above 100% is not wasted: the excess is added to the multiplier (times `criticalOverflowConversion`, default 1.0). With the default settings a chance of 1.5 makes every hit critical for 2.5× damage. Melee hits and projectiles roll once each, and a piercing arrow keeps one roll for all its targets.

A bow with +5 Arrow Damage (the editor's Copy command button writes commands like this for you):

```
/give @p bow{AttributeModifiers:[{AttributeName:"battlecorrection:arrow_damage",Name:"bonus",Amount:5.0d,Operation:0,UUID:[I;1,2,3,4],Slot:"mainhand"}]}
```

![A bow with +5 Arrow Damage](https://cdn.modrinth.com/data/cached_images/c2134fa74a6ca5f465c6ebea3f2d7bca1a5340fd.png)

### Combat tuning

Global settings. At their defaults they leave vanilla combat unchanged.

- PVP and self-damage multipliers, set separately; 0 turns either one off.
- Damage dealt and taken by players, per type: melee, arrow, projectile and magic. Magic stacks on top of the other three, so a player's fireball uses the projectile multiplier times the magic multiplier.
- Environmental damage taken by players (damage without a living attacker): a general multiplier plus fall, fire, drowning, freezing and explosion, applied after armor; 0 makes players immune. An optional floor makes each environmental hit deal at least a share of max health or a flat amount. Void damage and `/kill` are never changed.
- Invulnerability time after a hit, for players and for other entities: 0 keeps vanilla, -0.5 halves it, 1.0 doubles it.
- Attack cooldown on or off (off gives 1.8-style combat), and combo correction, which scales uncharged swings by the attack charge, never below 50%.
- Max health of every non-player mob: 1.0 doubles it.
- Hunger: extra regeneration from saturation and from a hunger bar of 18 or more (flat or a share of max health), and less damage dealt by players per missing hunger point, with an optional cap.
- Spells from other mods count as magic damage. Iron's Spells 'n Spellbooks, Ars Nouveau, Goety, Sweet Magic and Geomancy Plus are recognised out of the box by mod namespace, together with the `forge:is_magic` damage type tag, a whitelist and a blacklist. Melee hits by summons and pets never count as magic.
- Whether lifesteal is boosted by healing bonuses (`lifestealAffectedByHealBonus`, on by default).

![Config screen, Player Damage Resistance tab](https://cdn.modrinth.com/data/cached_images/569eb49a50e19651112d41624b970e4f2d35ec8d.png)

*The in-game config screen (Mods → BattleCorrection → Config).*

### Curios marks

With Curios installed, an operator can mark one single item so it fits chosen curio slots: a sword worn as a charm, a helmet worn as a ring. The mark is stored in the item's NBT (`BattleCorrectionCurioSlots`, `BattleCorrectionCurioModifiers`), so copies and trades keep it.

- A marked item is curio-only. Its own attributes (armor, attack damage, `AttributeModifiers`, bonuses from other mods) apply only in the curio slot, and marked armor can no longer be worn in armor slots.
- Extra attributes that count only while worn can be attached, optionally for one slot type. Curios' own `CurioAttributeModifiers` tag is never touched.
- Right-click equips a marked item into a free slot; shift-click works in the Curios screen too.
- The tooltip lists the attributes in one section per slot, in the same style as Curios accessories.
- For curios without a mark, `curioNbtAttributes` decides which vanilla-format attributes apply while worn: `ALL` (default), `MOD_ONLY` or `OFF`.

![Item editor, Curios page](https://cdn.modrinth.com/data/cached_images/1b131b3114fe7bf0fa1994bd70cb27f43b520c6e.png)

*Curios page: this item can now be worn in the Body, Head and Necklace slots.*

### Management staffs and test swords

Ten staffs for map makers and testing. They have no recipes and come only from the Battle Correction creative tab or `/give`, but anyone holding one can use it, in survival too. The area staffs work within 64 blocks, and every staff has a one-second cooldown.

<details>
<summary><b>All staffs and swords</b></summary>

| Item | Use |
|---|---|
| Enemy Staff | Click two mobs: they attack each other |
| Rebel Staff | Click a mob: every mob within 64 blocks attacks it |
| Riot Staff | Click a mob: it attacks random mobs nearby for 10 minutes |
| Brawl Staff | Use in the air: all mobs within 64 blocks fight each other for 10 minutes |
| Elimination Staff | Use in the air: mobs attack mobs of other species for 10 minutes |
| Healing Staff | Click any living entity: full health |
| Range Healing Staff | Full health for every living entity within 64 blocks of the target, players included |
| Sacrificial Staff | Kills the clicked entity, players included |
| Range Sacrificial Staff | Kills every non-player entity within 64 blocks of the target |
| Restoration Staff | Removes all effects from the clicked entity |
| Base / Advanced / Master Sword | 10 / 100 / 1,000 attack damage, 1,000 / 10,000 / 100,000 durability |

</details>

### Commands and keys

| Command or key | What it does |
|---|---|
| `/battlecorrection edit` | Opens the editor on the held item; add `snbt` or `nbt` to open that page |
| `/battlecorrection curio add <slot>` | Lets the held item go into a Curios slot type (`any` = every slot); `remove <slot>`, `clear` and `list` manage the marks |
| `/battlecorrection curio attr add <attribute> <amount> [add, multiply_base or multiply_total] [slot]` | Attaches an attribute that counts only while worn; `attr remove <attribute> [slot]` and `attr clear` remove them |
| `I` / `O` / `P` | Open the editor / SNBT page / NBT tree; with a container open they act on the slot under the mouse |

All commands need permission level 2; `edit` also needs creative mode, and the `curio` commands exist only when Curios is installed. The keys can be changed under Options → Controls → Key Binds → Battle Correction. `P` is also vanilla's default Social Interactions Screen key, and on a server that screen opens first, so rebind one of the two.

### Configuration

| File | Contents |
|---|---|
| `config/battlecorrection-battle.toml` | PVP, combat mechanics, player damage multipliers, environmental damage, mob health, hunger, third-party magic recognition |
| `config/battlecorrection-attribute.toml` | Global values of the 15 attributes, lifesteal option, Curios marks, item editor |

Most options are also in the in-game screen (Mods → BattleCorrection → Config). `curioNbtAttributes`, `itemEditorEnabled` and `itemEditorMaxNbtKb` (default 1024 KB, range 16 to 8192) are only in the file. Both files are common configs that every side reads from its own folder; Bow Draw Speed and Item Use Speed are read on the client as well, so keep those two values the same in the client and server files.

Most changes apply as soon as they are saved. `attackCooldown` and `extraMaxHealth` apply when an entity joins the world again, and `curioNbtAttributes` when the item is put on again.

### Requirements

| Component | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Java | 17 |
| Required mods | None (Cloth Config API is bundled) |
| Optional | Curios API 5.14.1 or newer |
| Sides | Client and server, same version |

### Compatibility

- Combat changes run on Forge's damage and healing events, so they also apply to weapons and damage from other mods.
- Curios API is optional; without it the curio features stay off.
- Cloth Config API is bundled with Jar-in-Jar.
- The magic mods listed under Combat tuning are only defaults in `magicDamageNamespaces`; any mod namespace can be added.

### FAQ

<details>
<summary><b>Can ordinary players use the item editor?</b></summary>

No. It needs permission level 2 and creative mode, and the server checks both again on every save. Set `itemEditorEnabled = false` to turn it off completely.

</details>

<details>
<summary><b>Why does P open the social screen on a server?</b></summary>

Vanilla 1.20.1 also uses `P` for its Social Interactions Screen, and on a server that screen opens before the NBT tree. Rebind one of the two under Options → Controls → Key Binds.

</details>

<details>
<summary><b>How do I give an item one of these attributes?</b></summary>

Open the item editor's Attributes page, or write an attribute modifier with `/give` (see above), a data pack or any tool that edits item NBT. For curios, use `/battlecorrection curio attr add` or pick a curio slot in the editor.

</details>

<details>
<summary><b>Is lifesteal boosted by healing bonuses?</b></summary>

By default, yes: lifesteal healing is multiplied by the heal multiplier config (`restoreHeal`) and the Healing Restoration attribute like any other healing. Turn off `lifestealAffectedByHealBonus` to make lifesteal always heal damage × lifesteal ratio.

</details>

<details>
<summary><b>Can I disable the attack cooldown?</b></summary>

Yes. Set `attackCooldown = false` for 1.8-style combat without a cooldown. It applies when the player joins the world again.

</details>

<details>
<summary><b>I'm updating from 2.0.2. Anything to know?</b></summary>

Your config is kept. Some old bugs are fixed in ways you may notice:

- At the default setting, invulnerability frames are vanilla again; 2.0.2 removed them.
- If you had changed the invulnerability multipliers in 2.0.2, subtract 1 from them (minimum -0.99) to keep the same timing.
- Combo correction no longer halves every hit when it is turned on.
- Magic multipliers now stack on top of the melee and projectile ones.
- Hunger damage decay now works when its limit is left at 0 (0 means no cap).

An installed Curios must now be 5.14.1 or newer, otherwise the game will not start.

</details>

### Links

- [MC百科 (Chinese wiki)](https://www.mcmod.cn/class/8002.html)
- [Report issues](https://github.com/Roinflam/BattleCorrection/issues)
- [Developer's Bilibili](https://space.bilibili.com/285030707/)

### License

Licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International). Full text: [LICENSE](LICENSE).

- ✅ Free to use, modify and include in modpacks
- ❌ No commercial use
- ⚠️ Redistributed or modified versions must credit the original author (Roinflam), state what was changed, and use the same license

The 1.20.1 jar bundles [Cloth Config API](https://github.com/shedaniel/cloth-config) 11.1.136 through Jar-in-Jar. It is included unmodified and stays under its own license, LGPL-3.0, whose text ships with it inside the jar.

### Credits

**Developer:** Roinflam  
**Special Thanks:** 建议重开 (for support and assistance)

</details>

---

<details>
<summary><h2>中文</h2></summary>

### 关于模组

战斗修正（BattleCorrection，简称 BC）为 Minecraft 1.20.1 Forge 加入 15 种战斗属性、全局战斗数值调整，以及一套完整的游戏内物品和 NBT 编辑器，面向自己定数值的 RPG 地图、整合包和服务器作者。

- 15 种属性对玩家和怪物都有效：带溢出的暴击、两种吸血、闪避几率、固定减伤、魔法 / 箭矢 / 弹射物伤害、拉弓和使用速度、跳跃高度。
- 全局设置：PVP、玩家按伤害类型造成和承受的伤害、环境伤害、无敌时间、攻击冷却、生物生命值和饥饿。
- 任意一件物品都能变成 Curios 饰品，并带上只在佩戴时生效的属性。
- 管理员用的物品编辑器：彩色名字和渐变、Lore、附魔、属性、NBT 树、SNBT 文本编辑和模板，还能编辑实体和方块实体的 NBT。

物品编辑器、饰品栏标记、环境伤害设置和第三方魔法识别是 3.0.0 新增的。

### 安装方法

1. 安装 1.20.1 的 [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) 47.x（Java 17）。
2. 从 [CurseForge](https://www.curseforge.com/minecraft/mc-mods/battlecorrection) 或 [Modrinth](https://modrinth.com/mod/battlecorrection) 下载战斗修正 3.0.0。
3. 把 jar 放进**客户端和服务端**的 `mods` 文件夹，两端版本要一致。
4. 可选：[Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) 5.14.1 及以上，用于饰品栏功能。

配置界面用的 Cloth Config API 已经在 jar 里，不用另外下载。

### 物品编辑器

拥有 2 级权限且处于创造模式的玩家按 `I` 或输入 `/battlecorrection edit` 打开。可以编辑手上的物品、打开的物品栏或箱子里鼠标指着的物品；空手时编辑准星对着的实体或方块实体（生物、盔甲架、箱子、刷怪笼等，玩家除外），实体和方块实体只有 NBT 树和 SNBT 两页。`O` 直接打开 SNBT 页，`P` 直接打开 NBT 树。每次保存时服务端都会重新检查权限、创造模式、目标和 NBT 大小。

界面由本模组自行绘制：左侧是标签页，中间是当前页面，右侧实时预览物品的提示框，底部有「复制为命令」「导入」、NBT 大小、取消和保存。截图见英文部分。

- 名字和 Lore：`&` 颜色码和 `&#RRGGBB` 十六进制颜色，也可以点色块和格式按钮插入；渐变可以作用于整行或选中的文字，右边的提示框预览随输入实时更新。Lore 每行一个输入框，可以上下移动、删除和添加，多行粘贴自动拆成多行。没改过的 Lore 行原样保留，点击和悬停事件也不会丢。
- 调色盘：饱和度 / 明度方块、色相条、RGB 滑块和十六进制输入；渐变支持 2 到 8 个色标，有彩虹、火焰、海洋等 10 个预设，记住最近用过的 16 个颜色。
- 附魔：左侧是已有附魔，旁边标出原版上限，左键改等级、右键删除；右侧搜索全部附魔（含其他模组的），诅咒显示为红色，悬停查看说明。等级最高 255；超过原版上限或原版不允许附在该物品上的照样生效。
- 属性：原版和所有模组的属性都在一个可搜索的列表里，悬停查看说明；可设数值、运算方式和生效位置，输入 `20%` 自动切换成百分比，生效位置选某个饰品栏时，物品同时可以戴进这个饰品栏。
- 标签：不可破坏、附魔光效、隐藏提示信息、CustomModelData、耐久损耗、铁砧修复惩罚、数量（1 到 127）。
- 饰品栏：勾选这件物品能放进哪些饰品栏。「玩家饰品栏」页供测试饰品时用，改的是自己的饰品栏格数（填 0 去掉这种饰品栏，也可以输入 ID 添加一格）。Curios 只给至少有一种饰品栏的玩家提供饰品栏，测试时可以把 `devCurioSlots` 设为 `true`，让所有玩家获得 Curios 自带的 10 种饰品栏各 1 格，正常游戏时保持关闭。
- NBT 树：显示每个标签的类型，可搜索键名和值（`Ctrl+F`），可粘贴 `tag.display.Lore[0]` 这样的路径直接跳转，可复制节点路径，可添加、删除、复制、粘贴、重复、移动节点。
- SNBT：带行号和语法高亮的文本编辑器，实时标出错误，支持查找（`Ctrl+F`）、跳到行（`Ctrl+G`）、缩进、复制行、移动行、格式化、压成一行和 `&` 与 `§` 互换；有错误时不会写入物品。
- 模板：把物品的整份 NBT 和数量按名字存在本机。套用时可以整份覆盖，也可以只勾选名字、Lore、附魔、属性、饰品栏标记等部分，例如把同一组附魔和属性套到不同的武器上。
- 其他：「导入」读取剪贴板里的 `/give` 命令、物品 ID 加 NBT 或物品 SNBT，「复制为命令」把物品导出成 `/give` 命令（超过 256 个字符的要用命令方块或函数执行）；`F4` 或点物品图标可换物品种类，NBT 和数量保留，搜索框输入 `@` 加模组 ID（如 `@battlecorrection`）只列出这个模组的物品；`Ctrl+S` 保存，`Ctrl+Z` / `Ctrl+Y` 撤销重做，`Ctrl+Tab` 或 `Ctrl+1`～`Ctrl+9` 切页，`F1` 查看全部快捷键；有未保存的修改时关闭，会先列出改了哪些地方。

### 属性

15 种属性注册在所有生物类型上，`/attribute`、`/give`、数据包、编辑器和任何能写属性修饰符的工具都能用。每种属性在 `battlecorrection-attribute.toml` 里另有一个全局数值，对所有生物（包括怪物）生效。下表的例子都用运算 0（加法），15 种属性都适合用它。

| 属性 | ID | 效果（运算 0） |
|---|---|---|
| 魔法伤害 | `battlecorrection:magic_damage` | 有攻击者的魔法攻击额外造成的伤害 |
| 箭矢伤害 | `battlecorrection:arrow_damage` | 箭矢和三叉戟命中额外造成的伤害 |
| 弹射物伤害 | `battlecorrection:projectile_damage` | 火球、法术弹射物等其他弹射物额外造成的伤害 |
| 伤害免疫 | `battlecorrection:immune_damage` | 完全免疫一次攻击的几率：+0.25 = 25% |
| 忽略伤害 | `battlecorrection:ignore_damage` | 每次受到的伤害减去固定值，不超过该值的伤害直接取消 |
| 减少摔落伤害 | `battlecorrection:reduced_fall_damage` | 同上，只对摔落伤害 |
| 治疗恢复 | `battlecorrection:restore_heal` | 治疗倍率：+0.5 = 150% 治疗量 |
| 吸血 | `battlecorrection:bloodthirsty` | 按近战伤害的比例回复生命值（玩家要蓄满力）：+0.1 = 10% |
| 全能吸血 | `battlecorrection:almighty_bloodthirsty` | 同上，对所有伤害类型有效 |
| 拉弓速度 | `battlecorrection:bow_speed` | 弓和弩的蓄力速度：+1.0 = 两倍速度 |
| 使用速度 | `battlecorrection:preparation_speed` | 食物、药水等手持使用物品的速度：+1.0 = 两倍速度 |
| 跳跃提升 | `battlecorrection:jump_lift` | 额外跳跃高度：1.0 约为 1 格 |
| 原版暴击伤害 | `battlecorrection:vanilla_critical_hit_damage` | 加在原版跳劈暴击上：+0.5 让 150% 变成 200% |
| 暴击几率 | `battlecorrection:custom_critical_chance` | 本模组自己的暴击，近战和弹射物命中时判定：0.3 = 30% |
| 暴击伤害 | `battlecorrection:custom_critical_damage` | 加到暴击倍率上（默认 2.0）：+1.0 = 3 倍伤害 |

起始值为 0 的属性（三种伤害加成、忽略伤害、减少摔落伤害、跳跃提升、暴击几率、暴击伤害）只对运算 0 有反应；起始值为 1.0 的属性只算超出 1.0 的部分，所以单独一条 0.25 用运算 0 和运算 1 效果一样。

暴击几率超过 100% 的部分不会浪费，会乘上 `criticalOverflowConversion`（默认 1.0）加到暴击倍率上：默认设置下几率 1.5 就是每一击都暴击、造成 2.5 倍伤害。近战每一击、每个弹射物各判定一次，穿透箭对所有目标共用同一次判定。

一把箭矢伤害 +5 的弓（编辑器的「复制为命令」按钮也能生成这种命令）：

```
/give @p bow{AttributeModifiers:[{AttributeName:"battlecorrection:arrow_damage",Name:"bonus",Amount:5.0d,Operation:0,UUID:[I;1,2,3,4],Slot:"mainhand"}]}
```

### 战斗数值调整

以下都是全局设置，保持默认值时不改变原版战斗。

- PVP 伤害倍率和自伤倍率分开设置，设为 0 即关闭。
- 玩家造成和承受的伤害按类型分别设倍率：近战、箭矢、弹射物、魔法。魔法倍率叠乘在另外三种之上，例如玩家的火球 = 弹射物倍率 × 魔法倍率。
- 玩家承受的环境伤害（没有活物攻击者的伤害）：总倍率加上摔落、火焰、溺水、冰冻、爆炸各自的倍率，在盔甲减伤之后结算，设为 0 即免疫；还可以设下限，让每次环境伤害至少造成最大生命值的一定比例或一个固定值。虚空和 `/kill` 不受影响。
- 玩家和其他生物的无敌时间分别调整：0 为原版，-0.5 减半，1.0 翻倍。
- 开关攻击冷却（关闭即 1.8 式战斗）；连击修正：未蓄满力的攻击按蓄力比例降低伤害，最低 50%。
- 所有非玩家生物的最大生命值：1.0 即翻倍。
- 饥饿：饱和度和饥饿值 18 以上时的额外生命恢复（固定值或最大生命值比例），以及玩家饥饿值每少 1 点造成的伤害降低多少，可设上限。
- 第三方魔法识别：其他模组的法术算作魔法伤害。默认按命名空间识别 Iron的法术与魔法书（Iron's Spells 'n Spellbooks）、新生魔艺（Ars Nouveau）、诡厄巫法（Goety）、甜蜜魔法（Sweet Magic）和 Geomancy Plus，同时认 `forge:is_magic` 伤害类型标签，另有白名单和黑名单。召唤物和宠物替主人的近战攻击不算魔法。
- 吸血是否吃治疗加成（`lifestealAffectedByHealBonus`，默认是）。

### 饰品栏标记

装了 Curios 时，管理员可以给某一件物品打上标记，让它能放进指定的饰品栏，比如把剑当护符戴、把头盔当戒指戴。标记写在物品自己的 NBT 里（`BattleCorrectionCurioSlots`、`BattleCorrectionCurioModifiers`），复制、交易都会带着。

- 打过标记的物品只当饰品用：它自己的属性（盔甲值、攻击伤害、`AttributeModifiers`、其他模组加的属性）只在饰品栏里生效，标记过的防具也不能再穿进盔甲栏。
- 可以附加只在佩戴时生效的属性，并可限定某一种饰品栏；不会改动 Curios 自己的 `CurioAttributeModifiers` 标签。
- 右键直接戴进空的饰品栏，在 Curios 界面里按住 Shift 点击也行。
- 提示框按饰品栏分段列出属性，样式和 Curios 饰品一致。
- 没有标记的饰品上原版格式的属性戴着时哪些生效，由 `curioNbtAttributes` 决定：`ALL`（默认）、`MOD_ONLY` 或 `OFF`。

### 管理权杖和测试用剑

10 根权杖，给地图作者和测试用。没有合成配方，只能从创造模式物品栏或用 `/give` 拿到，但拿在手里的人都能用，生存模式也一样。范围类权杖作用于 64 格内，所有权杖冷却 1 秒。

<details>
<summary><b>全部权杖和剑</b></summary>

| 物品 | 用法 |
|---|---|
| 敌对权杖 | 先后点两个生物，让它们互相攻击 |
| 叛军权杖 | 点一个生物，64 格内的生物都去攻击它 |
| 暴动权杖 | 点一个生物，它在 10 分钟内随机攻击附近的生物 |
| 群殴权杖 | 对空气使用，64 格内的生物互相攻击 10 分钟 |
| 消灭权杖 | 对空气使用，生物去攻击不同种类的生物，持续 10 分钟 |
| 治疗权杖 | 点任意生物，回满生命值 |
| 范围治疗权杖 | 目标 64 格内的所有生物回满生命值，包括玩家 |
| 献祭权杖 | 杀死点到的生物，包括玩家 |
| 范围献祭权杖 | 杀死目标 64 格内的所有非玩家生物 |
| 恢复权杖 | 清除点到的生物身上的所有状态效果 |
| 基础之剑 / 进阶之剑 / 大师之剑 | 攻击伤害 10 / 100 / 1,000，耐久 1,000 / 10,000 / 100,000 |

</details>

### 命令和按键

| 命令或按键 | 作用 |
|---|---|
| `/battlecorrection edit` | 编辑手上的物品；后面加 `snbt` 或 `nbt` 直接打开对应页 |
| `/battlecorrection curio add <饰品栏>` | 让手上的物品能放进某种 Curios 饰品栏（`any` = 所有）；`remove <饰品栏>`、`clear`、`list` 管理标记 |
| `/battlecorrection curio attr add <属性> <数值> [add / multiply_base / multiply_total] [饰品栏]` | 附加只在佩戴时生效的属性；`attr remove <属性> [饰品栏]` 和 `attr clear` 移除 |
| `I` / `O` / `P` | 打开编辑器 / SNBT 页 / NBT 树；打开容器时编辑鼠标指着的那一格 |

所有命令都需要 2 级权限，`edit` 还需要创造模式，`curio` 命令只在装了 Curios 时才有。按键可在「选项 → 按键控制 → 按键绑定」的「战斗修正」分类里修改。`P` 和原版「社交屏幕」的默认键相同，在服务器上按 P 会先打开社交屏幕，需要改掉其中一个。

### 配置

| 文件 | 内容 |
|---|---|
| `config/battlecorrection-battle.toml` | PVP、战斗机制、玩家伤害倍率、环境伤害、生物生命值、饥饿、第三方魔法识别 |
| `config/battlecorrection-attribute.toml` | 15 种属性的全局数值、吸血选项、饰品栏标记、物品编辑器 |

大部分选项也能在游戏内改（模组 → BattleCorrection → 配置）。`curioNbtAttributes`、`itemEditorEnabled`、`itemEditorMaxNbtKb`（默认 1024 KB，范围 16 到 8192）只在文件里。两个文件都是通用配置，每一端读自己文件夹里的那份；拉弓速度倍率和物品使用速度倍率客户端也会读取，客户端和服务端的文件里这两项要设成一样。

大部分修改保存后立即生效。`attackCooldown` 和 `extraMaxHealth` 要等实体重新进入世界，`curioNbtAttributes` 要重新戴上物品。

### 运行需求

| 组件 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Java | 17 |
| 前置模组 | 无（Cloth Config API 已内置） |
| 可选 | Curios API 5.14.1 及以上 |
| 安装位置 | 客户端和服务端，版本一致 |

### 兼容性

- 战斗数值都通过 Forge 的伤害和治疗事件处理，其他模组的武器和伤害同样适用。
- Curios API 是可选的，没装时只是没有饰品栏相关功能。
- Cloth Config API 以 Jar-in-Jar 方式内置。
- 战斗数值调整里列出的魔法模组只是 `magicDamageNamespaces` 的默认值，任何模组的命名空间都可以加进去。

### 常见问题

<details>
<summary><b>普通玩家能用物品编辑器吗？</b></summary>

不能。需要 2 级权限和创造模式，每次保存服务端都会重新检查。把 `itemEditorEnabled` 设为 `false` 可以完全关闭。

</details>

<details>
<summary><b>在服务器上按 P 为什么打开的是社交屏幕？</b></summary>

原版 1.20.1 的「社交屏幕」默认也是 `P`，在服务器上会先打开它。在「选项 → 按键控制 → 按键绑定」里改掉其中一个即可。

</details>

<details>
<summary><b>怎么给物品加上这些属性？</b></summary>

打开物品编辑器的属性页，或者用 `/give`（见上文）、数据包或任何能改物品 NBT 的工具写属性修饰符。饰品用 `/battlecorrection curio attr add`，或者在编辑器里把生效位置选成某个饰品栏。

</details>

<details>
<summary><b>吸血会吃治疗加成吗？</b></summary>

默认会：吸血回的血和其他治疗一样，会乘上治疗倍率配置（`restoreHeal`）和装备上的「治疗恢复」属性。不想让两者叠加，把属性配置里的「吸血吃治疗加成」（`lifestealAffectedByHealBonus`）关掉，吸血就固定回复「伤害 × 吸血比例」。

</details>

<details>
<summary><b>可以关掉攻击冷却吗？</b></summary>

可以。把 `attackCooldown` 设为 `false` 就是没有冷却的 1.8 式战斗，玩家重新进入世界后生效。

</details>

<details>
<summary><b>从 2.0.2 升级要注意什么？</b></summary>

配置会保留。几个老问题修好后，手感会有变化：

- 默认设置下无敌时间恢复为原版，2.0.2 会把它清掉。
- 在 2.0.2 里改过两项无敌时间倍率的，要减 1（最低 -0.99）才和 2.0.2 一样。
- 开启连击修正后不会再把每一击都砍掉一半。
- 魔法倍率改为叠乘在近战和弹射物倍率之上。
- 饥饿伤害衰减的上限保持 0（不设上限）时也能正常生效了。

装了 Curios 的话，版本要在 5.14.1 及以上，否则游戏无法启动。

</details>

### 相关链接

- [MC百科](https://www.mcmod.cn/class/8002.html)
- [反馈问题](https://github.com/Roinflam/BattleCorrection/issues)
- [开发者的 B 站](https://space.bilibili.com/285030707/)

### 许可证

本项目采用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)（知识共享 署名-非商业性使用-相同方式共享 4.0 国际）协议，全文见 [LICENSE](LICENSE)。

- ✅ 可以免费使用、修改、加入整合包
- ❌ 不得用于商业用途
- ⚠️ 转载或发布修改版必须署原作者（Roinflam）、注明改动，并沿用同一协议

1.20.1 的 jar 通过 Jar-in-Jar 内置了 [Cloth Config API](https://github.com/shedaniel/cloth-config) 11.1.136，未做任何修改，仍按它自己的 LGPL-3.0 协议授权，协议全文随它一起放在 jar 里。

### 制作人员

**开发者：** Roinflam  
**特别鸣谢：** 建议重开（提供支持和帮助）

</details>

---

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/Roinflam/BattleCorrection?style=social)](https://github.com/Roinflam/BattleCorrection)

</div>
