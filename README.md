<div align="center">

<img src="https://cdn.modrinth.com/data/yitVG9Jx/images/1054ac42466975727523bf60c77469e278d64831.png" alt="BattleCorrection item editor" width="800">

# BattleCorrection | 战斗修正

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_battlecorrection_downloads.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/battlecorrection?style=for-the-badge&logo=modrinth&label=Modrinth)](https://modrinth.com/mod/battlecorrection)
[![MC Version](https://img.shields.io/badge/Minecraft-1.12.2-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-14.23.5.2855+-orange?style=for-the-badge)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey?style=for-the-badge)](LICENSE)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

This is the **1.12.2** branch. The Minecraft 1.20.1 version is on the [`1.20.1` branch](https://github.com/Roinflam/BattleCorrection/tree/1.20.1).

本分支是 **1.12.2** 版，Minecraft 1.20.1 版在 [`1.20.1` 分支](https://github.com/Roinflam/BattleCorrection/tree/1.20.1)。

</div>

<details open>
<summary><h2>English</h2></summary>

### About

BattleCorrection (BC) is for people who build RPG maps, modpacks and servers and want to set the combat numbers themselves. It adds 15 attributes such as critical chance, lifesteal and dodge that work on players and mobs, global multipliers for PVP, mob health, invulnerability frames and environmental damage, and an in-game editor for item names, lore, enchantments, attributes and raw NBT.

- 15 attributes that work on players and mobs alike: critical hits with overflow, two kinds of lifesteal, dodge chance, flat damage reduction, magic, arrow and projectile damage, bow and use speed, jump height.
- Global settings for PVP, damage dealt and taken by players per damage type, environmental damage, invulnerability frames, attack cooldown, mob health and hunger.
- Any single item can become a Baubles accessory, with attributes that only count while it is worn.
- An item editor for operators: coloured names and gradients, lore, enchantments, attributes, an NBT tree, an SNBT text editor and templates. It also edits entity and block entity NBT.

### Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) 14.23.5.2855 or newer for 1.12.2 (Java 8).
2. Download the 1.12.2 version of BattleCorrection from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/battlecorrection) or [Modrinth](https://modrinth.com/mod/battlecorrection).
3. Put the jar into the `mods` folder of **both the client and the server**, same version on both sides.
4. Optional: [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles) 1.5.2 for the accessory features.

No other mods are needed.

### Item editor

Operators (permission level 2) in creative mode press `I` or run `/battlecorrection edit`. The editor opens on the held item, on the item under the mouse in any open inventory or chest, or, with an empty hand, on the entity or block entity you are looking at (mobs, armor stands, chests, spawners; not players). `O` opens the SNBT page directly and `P` the NBT tree. The server checks permission, creative mode, the target and the NBT size again on every save.

- Name and lore: `&` colour codes, swatches and format buttons, gradients over the whole line or the selected text, and a live tooltip preview. Minecraft 1.12.2 only has the 16 vanilla colours, so gradients snap to the nearest of them. Lore lines you did not touch are saved exactly as they were.
- Colour palette: pick from the 16 vanilla colours, build gradients with 2 to 8 colour stops (each character takes the nearest vanilla colour) and ten presets, and reuse your 8 most recent colours.
- Attributes: vanilla, Forge and this mod's attributes plus every attribute your player has, in one searchable list grouped by mod (any other attribute name can be typed in), with amount, operation and slot; typing `20%` switches to a percentage, and a Baubles slot can be picked as the slot.
- Enchantments: search every enchantment, levels up to 32767. Levels above the vanilla maximum and enchantments vanilla would not allow on the item are marked, but still applied.
- Tags: Unbreakable, enchantment glint, hide flags, damage, anvil repair cost and count (1 to 127).
- Baubles: tick the bauble slots the item may be worn in.
- NBT tree: typed nodes, search, jump to a path such as `tag.display.Lore[0]`, copy, paste, duplicate and move nodes.
- SNBT: a text editor with line numbers, highlighting, live error checking, find (`Ctrl+F`), go to line (`Ctrl+G`) and format / compact buttons. Text with errors is never applied.
- Templates: save an item under a name and apply it to other items, whole or in parts. Templates are stored on your own computer.
- Import reads a `/give` command or item SNBT from the clipboard; Copy command exports the item as a `/give` command. `F4` or a click on the item icon changes the item type and keeps its NBT and count.
- `Ctrl+S` saves, `Ctrl+Z` / `Ctrl+Y` undo and redo, `Ctrl+Tab` or `Ctrl+1` to `Ctrl+9` switch pages, `F1` lists all shortcuts.

The screenshots below are from the 1.20.1 version. The 1.12.2 editor has the same pages, except for hex colours, the colour square and RGB sliders in the palette, CustomModelData and the 1.20.1-only My Slots page.

![Item editor, Enchantments page](https://cdn.modrinth.com/data/cached_images/eaa78f8d886fd72e26297794b4e0a9be82863718.png)

![Item editor, Attributes page](https://cdn.modrinth.com/data/cached_images/aa5211549f92643a17dc6fa3be16576a024f5204.png)

![Item editor, NBT tree page](https://cdn.modrinth.com/data/cached_images/650e1da79b7fb21c164d6b84732b5a1e5905645e.png)

![Item editor, SNBT page](https://cdn.modrinth.com/data/cached_images/c9da546b99fc3959324dafff60511f233d447e19.png)

![Item editor, Templates page](https://cdn.modrinth.com/data/cached_images/dbe44539407b5f8b4f430dcd9fd7b736fc646607.png)

![Entity NBT editor](https://cdn.modrinth.com/data/cached_images/7bb0041a62b5c1c7a6444775202f30e779e92846.png)

### Attributes

All 15 attributes are added to every living entity when it joins the world, so `/give`, mob equipment, the editor and any tool that writes attribute modifiers can use them. Each attribute also has a global value in the config that applies to every entity, mobs included. Modifiers count from the main hand, off hand, armor slots and bauble slots.

| Attribute | Name | Effect (Operation 0) |
|---|---|---|
| Magic Damage | `battlecorrection.magicDamage` | Extra damage on magic hits that have an attacker |
| Arrow Damage | `battlecorrection.arrowDamage` | Extra damage on arrow hits |
| Projectile Damage | `battlecorrection.projectileDamage` | Extra damage on other projectiles: fireballs, shulker bullets, llama spit and so on |
| Damage Immunity | `battlecorrection.immuneDamage` | Chance to ignore a hit completely: +0.25 = 25% |
| Ignore Damage | `battlecorrection.ignoreDamage` | Subtracts a flat amount from every hit; hits at or below it are cancelled |
| Reduced Fall Damage | `battlecorrection.reducedFallDamage` | The same, for fall damage only |
| Healing Restoration | `battlecorrection.restoreHeal` | Healing multiplier: +0.5 = 150% healing |
| Lifesteal | `battlecorrection.bloodthirsty` | Heals a share of melee damage dealt (players need a full charge): +0.1 = 10% |
| Almighty Lifesteal | `battlecorrection.almightyBloodthirsty` | The same for every damage type |
| Bow Speed | `battlecorrection.bowSpeed` | Bow draw speed: +1.0 = twice as fast |
| Use Speed | `battlecorrection.preparationSpeed` | Use speed of food, potions and other held items: +1.0 = twice as fast |
| Jump Boost | `battlecorrection.jumpLift` | Extra jump height: 1.0 is about one block |
| Vanilla Critical Damage | `battlecorrection.vanillaCriticalHitDamage` | Adds to the vanilla jump-attack critical: +0.5 turns 150% into 200% |
| Critical Chance | `battlecorrection.customCriticalChance` | Chance of this mod's own critical hit on melee and projectile hits: 0.3 = 30% |
| Critical Damage | `battlecorrection.customCriticalDamage` | Added to the critical multiplier, which is 2.0 by default: +1.0 = 3× damage |

Operation 0 adds a flat amount and is the right choice for every attribute. On Magic, Arrow and Projectile Damage, Operation 1 and 2 scale the hit instead: 0.5 = +50%.

Critical chance above 100% is not wasted: the excess is added to the multiplier (times `criticalOverflowConversion`, default 1.0). With the default settings a chance of 1.5 makes every hit critical for 2.5× damage. Melee hits and projectiles roll once each, and a piercing arrow keeps one roll for all its targets.

A bow with +5 Arrow Damage (the editor's Copy command button writes commands like this for you):

```
/give @p minecraft:bow 1 0 {AttributeModifiers:[{AttributeName:"battlecorrection.arrowDamage",Name:"bonus",Amount:5.0d,Operation:0,UUIDMost:1L,UUIDLeast:1L,Slot:"mainhand"}]}
```

### Combat tuning

Global settings. At their defaults they leave vanilla combat unchanged.

- PVP and self-damage multipliers, set separately; 0 turns either one off.
- Damage dealt and taken by players, per type: melee, arrow, projectile and magic. Magic stacks on top of the other three, so a player's fireball uses the projectile multiplier times the magic multiplier.
- Environmental damage taken by players (damage without a living attacker): a general multiplier plus fall, fire, drowning and explosion, applied after armor; 0 makes players immune. An optional floor makes each environmental hit deal at least a share of max health or a flat amount. Void damage and `/kill` are never changed.
- Invulnerability time after a hit, for players and for other entities: 0 keeps vanilla, -0.5 halves it, 1.0 doubles it.
- Attack cooldown on or off (off gives 1.8-style combat), and combo correction, which scales uncharged swings by the attack charge, never below 50%.
- Max health of every non-player mob: 1.0 doubles it.
- Hunger: extra healing on top of natural regeneration (from saturation when the hunger bar is full, from hunger at 18 or more; flat or a share of max health), and less damage dealt by players per missing hunger point, with an optional cap (0 means no cap).
- Spells from other mods can count as magic damage: list their damage type prefixes in `magicDamageTypePrefixes`, with a whitelist and a blacklist for single types. Melee hits by summons and pets never count as magic.
- Whether lifesteal is boosted by healing bonuses (`lifestealAffectedByHealBonus`, on by default).

### Bauble marks

With Baubles installed, an operator can mark one single item so it fits chosen bauble slots: a sword worn as a charm, a helmet worn as a ring. The mark is stored in the item's NBT (`BattleCorrectionBaubleSlots`, `BattleCorrectionBaubleModifiers`), so copies and trades keep it.

- A marked item is bauble-only. Its own attributes (armor, attack damage, NBT attributes) apply only in the bauble slot, and marked armor is moved out of the armor slots.
- Extra attributes that count only while worn can be attached, optionally for one slot type.
- Right-click equips a marked item into a bauble slot.
- The tooltip lists the attributes per slot, for example "When worn as a ring:".
- For baubles without a mark, `baubleNbtAttributes` decides which vanilla-format attributes apply while worn: `ALL` (default), `MOD_ONLY` or `OFF`.

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
| `/battlecorrection bauble add <slot>` | Lets the held item go into a bauble slot: `amulet`, `ring`, `belt`, `head`, `body`, `charm` or `any`; `remove <slot>`, `clear` and `list` manage the marks |
| `/battlecorrection bauble attr add <attribute> <amount> [add, multiply_base or multiply_total] [slot]` | Attaches an attribute that counts only while worn; `attr remove <attribute> [slot]` and `attr clear` remove them |
| `I` / `O` / `P` | Open the editor / SNBT page / NBT tree; with a container open they act on the slot under the mouse |
| `K` | Opens the mod list |

All commands need permission level 2; `edit` also needs creative mode. `bauble` (alias `curio`) exists only when Baubles is installed, and attribute names use the 1.12.2 form, for example `generic.maxHealth`. The keys can be changed under Options → Controls.

### Configuration

Everything is in `config/battlecorrection.cfg`, in four categories: Battle Configuration, Attribute Configuration, Bauble Mark and Item Editor. All of them can be edited in game (Mods → Battle Correction → Config; `K` opens the mod list). Changes apply as soon as they are saved, even though the config screen marks them as needing a restart; attack cooldown and mob max health apply when an entity joins the world again.

The Item Editor category has `itemEditorEnabled` and `itemEditorMaxNbtKb`, the largest NBT the editor may save onto one item, entity or block entity (default 256 KB, range 16 to 2048).

### Requirements

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2855 or newer |
| Java | 8 |
| Required mods | None |
| Optional | Baubles 1.5.2 |
| Sides | Client and server, same version |

### Compatibility

- Combat changes run on Forge's damage and healing events, so they also apply to weapons and damage from other mods.
- Baubles is optional; without it the bauble features stay off.
- The 1.20.1 version has the same feature set, with Curios instead of Baubles, a freezing damage category, crossbow support, hex colours in the editor and ids such as `battlecorrection:magic_damage`.

### FAQ

<details>
<summary><b>Can ordinary players use the item editor?</b></summary>

No. It needs permission level 2 and creative mode, and the server checks both again on every save. Set `itemEditorEnabled` to `false` to turn it off completely.

</details>

<details>
<summary><b>How do I give an item one of these attributes?</b></summary>

Open the item editor's Attributes page, or write an attribute modifier with `/give` (see above) or any tool that edits item NBT. For baubles, use `/battlecorrection bauble attr add` or pick a bauble slot in the editor.

</details>

<details>
<summary><b>Is lifesteal boosted by healing bonuses?</b></summary>

By default, yes: lifesteal healing is multiplied by the heal multiplier config (`restoreHeal`) and the Healing Restoration attribute like any other healing. Turn off `lifestealAffectedByHealBonus` in the attribute config to make lifesteal always heal damage × lifesteal ratio.

</details>

<details>
<summary><b>Can I disable the attack cooldown?</b></summary>

Yes. Set "Attack Cooldown System" to `false` for 1.8-style combat without a cooldown. It applies when the player joins the world again.

</details>

<details>
<summary><b>Anything to know before updating?</b></summary>

Back up your config folder before updating. The changelog of each version on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/battlecorrection/files) or [Modrinth](https://modrinth.com/mod/battlecorrection/changelog) lists what changed and anything you may want to adjust.

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

The 1.20.1 version bundles [Cloth Config API](https://github.com/shedaniel/cloth-config) (LGPL-3.0, unmodified, Jar-in-Jar); this 1.12.2 version bundles nothing.

### Credits

**Developer:** Roinflam  
**Special Thanks:** 建议重开

</details>

---

<details>
<summary><h2>中文</h2></summary>

### 关于模组

战斗修正（BattleCorrection，简称 BC）为 Minecraft 1.12.2 Forge 加入 15 种战斗属性、全局战斗数值调整，以及一套完整的游戏内物品和 NBT 编辑器，面向自己定数值的 RPG 地图、整合包和服务器作者。

- 15 种属性对玩家和怪物都有效：带溢出的暴击、两种吸血、闪避几率、固定减伤、魔法 / 箭矢 / 弹射物伤害、拉弓和使用速度、跳跃高度。
- 全局设置：PVP、玩家按伤害类型造成和承受的伤害、环境伤害、无敌时间、攻击冷却、生物生命值和饥饿。
- 任意一件物品都能变成 Baubles 饰品，并带上只在佩戴时生效的属性。
- 管理员用的物品编辑器：彩色名字和渐变、Lore、附魔、属性、NBT 树、SNBT 文本编辑和模板，还能编辑实体和方块实体的 NBT。

### 安装方法

1. 安装 1.12.2 的 [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) 14.23.5.2855 及以上（Java 8）。
2. 从 [CurseForge](https://www.curseforge.com/minecraft/mc-mods/battlecorrection) 或 [Modrinth](https://modrinth.com/mod/battlecorrection) 下载战斗修正的 1.12.2 版。
3. 把 jar 放进**客户端和服务端**的 `mods` 文件夹，两端版本要一致。
4. 可选：[Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles) 1.5.2，用于饰品栏功能。

不需要其他前置模组。

### 物品编辑器

拥有 2 级权限且处于创造模式的玩家按 `I` 或输入 `/battlecorrection edit` 打开。可以编辑手上的物品、打开的物品栏或箱子里鼠标指着的物品；空手时编辑准星对着的实体或方块实体（生物、盔甲架、箱子、刷怪笼等，玩家除外）。`O` 直接打开 SNBT 页，`P` 直接打开 NBT 树。每次保存时服务端都会重新检查权限、创造模式、目标和 NBT 大小。

- 名字和 Lore：`&` 颜色码、色块和格式按钮，渐变可以作用于整行或选中的文字，提示框实时预览。1.12.2 只有 16 种原版颜色，渐变会取最接近的颜色。没改过的 Lore 行原样保留。
- 调色盘：从 16 种原版颜色里选色，渐变支持 2 到 8 个色标（每个字取最接近的原版颜色），有 10 个预设，记住最近用过的 8 个颜色。
- 属性：原版、Forge、本模组的属性和玩家身上已有的属性都在一个可搜索的列表里，按模组分组（列表里没有的可以直接输入属性名）；可设数值、运算方式和生效位置，输入 `20%` 自动切换成百分比，生效位置也可以选某个饰品栏。
- 附魔：搜索全部附魔，等级最高 32767；超过原版上限或原版不允许附在该物品上的会标出，但照样生效。
- 标签：不可破坏、附魔光效、隐藏提示信息、耐久损耗、铁砧修复惩罚、数量（1 到 127）。
- 饰品栏：勾选这件物品能放进哪些 Baubles 饰品栏。
- NBT 树：显示每个标签的类型，可搜索，可粘贴 `tag.display.Lore[0]` 这样的路径直接跳转，可复制、粘贴、重复、移动节点。
- SNBT：带行号和语法高亮的文本编辑器，实时检查错误，支持查找（`Ctrl+F`）、跳到行（`Ctrl+G`）、格式化和压成一行；有错误时不会写入物品。
- 模板：把物品按名字存下来，再整份或按部分套用到别的物品上，只保存在本机。
- 「导入」读取剪贴板里的 `/give` 命令或物品 SNBT，「复制为命令」把物品导出成 `/give` 命令；`F4` 或点物品图标可换物品种类，NBT 和数量保留。
- `Ctrl+S` 保存，`Ctrl+Z` / `Ctrl+Y` 撤销重做，`Ctrl+Tab` 或 `Ctrl+1`～`Ctrl+9` 切页，`F1` 查看全部快捷键。

英文部分的截图来自 1.20.1 版。1.12.2 的编辑器页面相同，只是没有十六进制颜色、调色盘的色块方框和 RGB 滑块、CustomModelData，也没有 1.20.1 独有的「玩家饰品栏」页。

### 属性

15 种属性在生物进入世界时加到所有生物身上，`/give`、生物身上的装备、编辑器和任何能写属性修饰符的工具都能用。每种属性在配置里另有一个全局数值，对所有生物（包括怪物）生效。修饰符在主手、副手、盔甲栏和饰品栏里生效。

| 属性 | 名称 | 效果（运算 0） |
|---|---|---|
| 魔法伤害 | `battlecorrection.magicDamage` | 有攻击者的魔法攻击额外造成的伤害 |
| 箭矢伤害 | `battlecorrection.arrowDamage` | 箭矢命中额外造成的伤害 |
| 弹射物伤害 | `battlecorrection.projectileDamage` | 火球、潜影弹、羊驼唾沫等其他弹射物额外造成的伤害 |
| 伤害免疫 | `battlecorrection.immuneDamage` | 完全免疫一次攻击的几率：+0.25 = 25% |
| 忽略伤害 | `battlecorrection.ignoreDamage` | 每次受到的伤害减去固定值，不超过该值的伤害直接取消 |
| 减少摔落伤害 | `battlecorrection.reducedFallDamage` | 同上，只对摔落伤害 |
| 治疗恢复 | `battlecorrection.restoreHeal` | 治疗倍率：+0.5 = 150% 治疗量 |
| 吸血 | `battlecorrection.bloodthirsty` | 按近战伤害的比例回复生命值（玩家要蓄满力）：+0.1 = 10% |
| 全能吸血 | `battlecorrection.almightyBloodthirsty` | 同上，对所有伤害类型有效 |
| 拉弓速度 | `battlecorrection.bowSpeed` | 弓的蓄力速度：+1.0 = 两倍速度 |
| 使用速度 | `battlecorrection.preparationSpeed` | 食物、药水等手持使用物品的速度：+1.0 = 两倍速度 |
| 跳跃提升 | `battlecorrection.jumpLift` | 额外跳跃高度：1.0 约为 1 格 |
| 原版暴击伤害 | `battlecorrection.vanillaCriticalHitDamage` | 加在原版跳劈暴击上：+0.5 让 150% 变成 200% |
| 暴击几率 | `battlecorrection.customCriticalChance` | 本模组自己的暴击，近战和弹射物命中时判定：0.3 = 30% |
| 暴击伤害 | `battlecorrection.customCriticalDamage` | 加到暴击倍率上（默认 2.0）：+1.0 = 3 倍伤害 |

运算 0 是加固定值，所有属性都适合用它。魔法、箭矢、弹射物伤害用运算 1 或 2 时改为按比例放大这一击：0.5 = +50%。

暴击几率超过 100% 的部分不会浪费，会乘上 `criticalOverflowConversion`（默认 1.0）加到暴击倍率上：默认设置下几率 1.5 就是每一击都暴击、造成 2.5 倍伤害。近战每一击、每个弹射物各判定一次，穿透箭对所有目标共用同一次判定。

一把箭矢伤害 +5 的弓（编辑器的「复制为命令」按钮也能生成这种命令）：

```
/give @p minecraft:bow 1 0 {AttributeModifiers:[{AttributeName:"battlecorrection.arrowDamage",Name:"bonus",Amount:5.0d,Operation:0,UUIDMost:1L,UUIDLeast:1L,Slot:"mainhand"}]}
```

### 战斗数值调整

以下都是全局设置，保持默认值时不改变原版战斗。

- PVP 伤害倍率和自伤倍率分开设置，设为 0 即关闭。
- 玩家造成和承受的伤害按类型分别设倍率：近战、箭矢、弹射物、魔法。魔法倍率叠乘在另外三种之上，例如玩家的火球 = 弹射物倍率 × 魔法倍率。
- 玩家承受的环境伤害（没有活物攻击者的伤害）：总倍率加上摔落、火焰、溺水、爆炸各自的倍率，在盔甲减伤之后结算，设为 0 即免疫；还可以设下限，让每次环境伤害至少造成最大生命值的一定比例或一个固定值。虚空和 `/kill` 不受影响。
- 玩家和其他生物的无敌时间分别调整：0 为原版，-0.5 减半，1.0 翻倍。
- 开关攻击冷却（关闭即 1.8 式战斗）；连击修正：未蓄满力的攻击按蓄力比例降低伤害，最低 50%。
- 所有非玩家生物的最大生命值：1.0 即翻倍。
- 饥饿：在自然恢复之外额外回复生命值（饥饿值满且有饱和度时按饱和度算，饥饿值 18 以上时按饥饿值算；固定值或最大生命值比例），以及玩家饥饿值每少 1 点造成的伤害降低多少，可设上限（0 为不设上限）。
- 第三方魔法识别：在 `magicDamageTypePrefixes` 里填其他模组伤害类型的前缀，就能把它们的法术算作魔法伤害，另有白名单和黑名单处理单个类型。召唤物和宠物替主人的近战攻击不算魔法。
- 吸血是否吃治疗加成（`lifestealAffectedByHealBonus`，默认是）。

### 饰品栏标记

装了 Baubles 时，管理员可以给某一件物品打上标记，让它能放进指定的饰品栏，比如把剑当护符戴、把头盔当戒指戴。标记写在物品自己的 NBT 里（`BattleCorrectionBaubleSlots`、`BattleCorrectionBaubleModifiers`），复制、交易都会带着。

- 打过标记的物品只当饰品用：它自己的属性（盔甲值、攻击伤害、NBT 属性）只在饰品栏里生效，标记过的防具会被移出盔甲栏。
- 可以附加只在佩戴时生效的属性，并可限定某一种饰品栏。
- 右键直接戴进饰品栏。
- 提示框按饰品栏分段列出属性，例如「佩戴指环时：」。
- 没有标记的饰品上原版格式的属性戴着时哪些生效，由 `baubleNbtAttributes` 决定：`ALL`（默认）、`MOD_ONLY` 或 `OFF`。

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
| `/battlecorrection bauble add <饰品栏>` | 让手上的物品能放进某种饰品栏：`amulet` 护身符、`ring` 指环、`belt` 腰带、`head` 头部、`body` 身体、`charm` 坠饰或 `any` 任意；`remove <饰品栏>`、`clear`、`list` 管理标记 |
| `/battlecorrection bauble attr add <属性> <数值> [add / multiply_base / multiply_total] [饰品栏]` | 附加只在佩戴时生效的属性；`attr remove <属性> [饰品栏]` 和 `attr clear` 移除 |
| `I` / `O` / `P` | 打开编辑器 / SNBT 页 / NBT 树；打开容器时编辑鼠标指着的那一格 |
| `K` | 打开模组列表 |

所有命令都需要 2 级权限，`edit` 还需要创造模式。`bauble`（也可以写 `curio`）只在装了 Baubles 时才有，属性名用 1.12.2 的写法，例如 `generic.maxHealth`。按键可在「选项 → 按键控制」里修改。

### 配置

所有选项都在 `config/battlecorrection.cfg` 里，分为战斗配置、属性配置、饰品栏标记、物品编辑器四类，都能在游戏内修改（在模组列表里选 Battle Correction，点「配置」；按 `K` 可直接打开模组列表）。配置界面虽然提示要重启，实际保存后立即生效；攻击冷却和生物最大生命值要等实体重新进入世界。

物品编辑器分类有 `itemEditorEnabled` 和 `itemEditorMaxNbtKb`：后者是编辑器给单个物品、实体或方块实体保存的 NBT 上限，默认 256 KB，范围 16 到 2048。

### 运行需求

| 组件 | 版本 |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2855 及以上 |
| Java | 8 |
| 前置模组 | 无 |
| 可选 | Baubles 1.5.2 |
| 安装位置 | 客户端和服务端，版本一致 |

### 兼容性

- 战斗数值都通过 Forge 的伤害和治疗事件处理，其他模组的武器和伤害同样适用。
- Baubles 是可选的，没装时只是没有饰品栏相关功能。
- 1.20.1 版功能相同，区别是用 Curios 代替 Baubles，多了冰冻伤害一项，支持弩，编辑器有十六进制颜色，属性 ID 写成 `battlecorrection:magic_damage` 这样。

### 常见问题

<details>
<summary><b>普通玩家能用物品编辑器吗？</b></summary>

不能。需要 2 级权限和创造模式，每次保存服务端都会重新检查。把 `itemEditorEnabled` 设为 `false` 可以完全关闭。

</details>

<details>
<summary><b>怎么给物品加上这些属性？</b></summary>

打开物品编辑器的属性页，或者用 `/give`（见上文）或任何能改物品 NBT 的工具写属性修饰符。饰品用 `/battlecorrection bauble attr add`，或者在编辑器里把生效位置选成某个饰品栏。

</details>

<details>
<summary><b>吸血会吃治疗加成吗？</b></summary>

默认会：吸血回的血和其他治疗一样，会乘上治疗倍率配置（`restoreHeal`）和装备上的「治疗恢复」属性。不想让两者叠加，把属性配置里的「吸血吃治疗加成」（`lifestealAffectedByHealBonus`）关掉，吸血就固定回复「伤害 × 吸血比例」。

</details>

<details>
<summary><b>可以关掉攻击冷却吗？</b></summary>

可以。在配置中将「攻击冷却系统」设为 `false`，就是没有冷却的 1.8 式战斗，玩家重新进入世界后生效。

</details>

<details>
<summary><b>更新模组前要注意什么？</b></summary>

更新前建议先备份 config 文件夹里的配置文件。每个版本改了什么、配置要不要调整，见 [CurseForge](https://www.curseforge.com/minecraft/mc-mods/battlecorrection/files) 或 [Modrinth](https://modrinth.com/mod/battlecorrection/changelog) 上该版本的更新日志。

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

1.20.1 版通过 Jar-in-Jar 内置了未经修改的 [Cloth Config API](https://github.com/shedaniel/cloth-config)（LGPL-3.0）；本 1.12.2 版没有内置任何其他模组。

### 制作人员

**开发者：** Roinflam  
**特别鸣谢：** 建议重开

</details>

---

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/Roinflam/BattleCorrection?style=social)](https://github.com/Roinflam/BattleCorrection)

</div>
