<div align="center">

# ⚔️ BattleCorrection | 战斗修正

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_battlecorrection_downloads.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)
[![MC Version](https://img.shields.io/badge/Minecraft-1.12.2-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-Latest-orange?style=for-the-badge)](https://files.minecraftforge.net/)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

---

### 🌏 Choose Your Language / 选择语言

</div>

<details open>
<summary><h2>🇺🇸 English (Click to expand/collapse)</h2></summary>

### 📖 About

**BattleCorrection** is an RPG-style combat enhancement mod for Minecraft 1.12.2. It introduces a comprehensive
attribute system, advanced critical hit mechanics, lifesteal abilities, and extensive battle balance options. Perfect
for RPG modpacks and servers that want deeper combat customization.

### ✨ Features

- 🎯 **15+ Custom Attributes** - Magic damage, arrow damage, lifesteal, dodge chance, and more
- 💥 **Advanced Critical System** - Custom crit chance & damage with overflow conversion mechanic
- 🩸 **Lifesteal Mechanics** - Melee lifesteal and universal lifesteal for all damage types
- ⚖️ **Battle Balance** - Fine-tune PVP damage, attack cooldown, invulnerability frames
- 🌋 **Environmental Damage** - Separate multipliers and a damage floor for fall, fire, drowning and explosions
- 🍖 **Hunger System** - Enhanced hunger regeneration and damage decay based on hunger level
- 💍 **Baubles Support** - Attributes on worn baubles, and any item can be marked as a bauble
- 🛡️ **Damage Reduction** - Flat damage ignore and fall damage reduction
- ⚡ **Speed Modifiers** - Bow draw speed and item use speed adjustments
- 🧰 **Management Tools** - Creative staffs for entity control, healing, and more
- ✏️ **Item Editor** - In-game editor for names, lore, enchantments, attributes and raw NBT (operators only)
- 🔧 **Highly Configurable** - Every feature can be adjusted via the in-game config GUI

### 📥 Installation

1. **Download Minecraft Forge** for 1.12.2 (latest version recommended)
    - [Download Forge →](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)

2. **Download BattleCorrection** from CurseForge
    - [Download Mod →](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)

3. **Optional:** [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles) for accessory slot support

4. **Install the mod**
    - Place the `.jar` files into `.minecraft/mods/` folder
    - Launch Minecraft with Forge profile

5. **Done!** Press `K` to open the mod list, configure in Mod Options

### 🎯 Attribute System

<details>
<summary><b>⚔️ Damage Attributes</b></summary>

| Attribute             | Description                                            |
|-----------------------|--------------------------------------------------------|
| **Magic Damage**      | Bonus damage for magic attacks (potions, modded magic) |
| **Arrow Damage**      | Bonus damage for bow attacks                           |
| **Projectile Damage** | Bonus damage for non-arrow projectiles                 |

</details>

<details>
<summary><b>🛡️ Defense Attributes</b></summary>

| Attribute               | Description                                 |
|-------------------------|---------------------------------------------|
| **Immune Damage**       | Chance to completely dodge incoming attacks |
| **Ignore Damage**       | Flat damage reduction from all sources      |
| **Reduced Fall Damage** | Flat fall damage reduction                  |

</details>

<details>
<summary><b>❤️ Recovery Attributes</b></summary>

| Attribute                 | Description                                         |
|---------------------------|-----------------------------------------------------|
| **Restore Heal**          | Multiplier for all healing received                 |
| **Bloodthirsty**          | Lifesteal from melee attacks (requires full charge) |
| **Almighty Bloodthirsty** | Lifesteal from ALL damage types                     |

</details>

<details>
<summary><b>💨 Speed Attributes</b></summary>

| Attribute             | Description                                       |
|-----------------------|---------------------------------------------------|
| **Bow Speed**         | Increases bow draw speed                          |
| **Preparation Speed** | Increases item use speed (food, potions, shields) |
| **Jump Lift**         | Increases jump height                             |

</details>

<details>
<summary><b>💥 Critical System</b></summary>

| Attribute                   | Description                                      |
|-----------------------------|--------------------------------------------------|
| **Vanilla Critical Damage** | Bonus damage for fall-attack crits               |
| **Custom Critical Chance**  | Base chance for custom crits (supports overflow) |
| **Custom Critical Damage**  | Damage multiplier when crit occurs               |

**Overflow Mechanic:** When crit chance exceeds 100%, the overflow converts to bonus crit damage!

</details>

### 🧰 Management Tools

<details>
<summary><b>⚔️ Combat Staffs</b></summary>

| Item                  | Description                                  |
|-----------------------|----------------------------------------------|
| **Enemy Staff**       | Make two entities fight each other           |
| **Rebel Staff**       | Make all nearby entities attack one target   |
| **Riot Staff**        | Make an entity attack random nearby entities |
| **Brawl Staff**       | Make all nearby entities fight each other    |
| **Elimination Staff** | Make different species attack each other     |

</details>

<details>
<summary><b>❤️ Healing Staffs</b></summary>

| Item                        | Description                              |
|-----------------------------|------------------------------------------|
| **Healing Staff**           | Fully restore one entity's health        |
| **Range Healing Staff**     | Fully restore all nearby entities        |
| **Sacrificial Staff**       | Instantly kill one entity                |
| **Range Sacrificial Staff** | Instantly kill all nearby non-players    |
| **Restoration Staff**       | Remove all potion effects from an entity |

</details>

<details>
<summary><b>⚔️ Example Swords</b></summary>

| Item               | Attack Damage | Durability |
|--------------------|---------------|------------|
| **Base Sword**     | 9             | 1,000      |
| **Advanced Sword** | 99            | 10,000     |
| **Master Sword**   | 999           | 100,000    |

*These swords demonstrate the mod's attribute system*

</details>

### 💍 Bauble Marks (requires Baubles)

Operators can turn the held item into a bauble with `/battlecorrection bauble`:

| Command                                              | Description                                                 |
|------------------------------------------------------|-------------------------------------------------------------|
| `add <slot>` / `remove <slot>` / `clear` / `list`    | Choose which bauble slots the item fits (`any` = every slot) |
| `attr add <attribute> <amount> [operation] [slot]`   | Attach an attribute that only applies while worn            |
| `attr remove <attribute> [slot]` / `attr clear`      | Remove attached attributes                                  |

Slots: `amulet`, `ring`, `belt`, `head`, `body`, `charm`. A marked item only works in its bauble slots: its attributes do
nothing in the hand, and marked armor can no longer be worn in armor slots. The marks are stored on the item itself.

### ✏️ Item Editor

Operators (permission level 2) in creative mode can press `I` (or run `/battlecorrection edit`) to edit the held item:
name, lore, enchantments, attributes, bauble marks, the NBT tree and raw SNBT. `O` opens the SNBT page directly and `P`
the NBT tree. Changes are checked on the server before they are applied.

### ⚙️ Configuration Options

<details>
<summary><b>🎮 Battle Settings</b></summary>

| Option               | Description                                        |
|----------------------|----------------------------------------------------|
| **PVP Damage**       | Multiplier for player vs player damage             |
| **Self Damage**      | Multiplier for self-inflicted damage               |
| **Combo Correction** | Uncharged attacks deal reduced damage              |
| **Attack Cooldown**  | Enable/disable 1.9+ attack cooldown                |
| **Hurt Time**        | Adjust invulnerability frames for entities/players |
| **Hunger Decay**     | Damage reduction based on hunger level             |

</details>

<details>
<summary><b>📊 Damage Multipliers</b></summary>

| Option                                                         | Description                                                                                                                               |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| **Player Melee/Arrow/Projectile/Magic Attack**                 | Output damage multipliers                                                                                                                 |
| **Player Suffers Melee/Arrow/Projectile/Magic**                | Incoming damage multipliers                                                                                                               |
| **Player Suffers Environment (General/Fall/Fire/Drowning/Explosion)** | Damage without a living attacker, applied after armor; the general multiplier is multiplied by the matching category; void and /kill are not affected |
| **Player Environment Damage Floor (Max Health % / Flat)**      | Each environmental hit deals at least the larger of the two; off by default, ignored when the general multiplier is 0 (immune)          |
| **Third-party Magic Recognition**                              | Treat other mods' magic damage types as magic (prefix / whitelist / blacklist)                                                            |
| **Entity Max Health**                                          | Health multiplier for non-player entities                                                                                                 |

</details>

<details>
<summary><b>🍖 Hunger System</b></summary>

| Option                           | Description                               |
|----------------------------------|-------------------------------------------|
| **Saturation Healing (Flat)**    | Extra HP per heal from saturation         |
| **Saturation Healing (Percent)** | Extra HP% per heal from saturation        |
| **Hunger Healing (Flat)**        | Extra HP per heal when hunger ≥ 18        |
| **Hunger Healing (Percent)**     | Extra HP% per heal when hunger ≥ 18       |
| **Hunger Damage Decay**          | Damage reduction per missing hunger point |
| **Hunger Decay Limit**           | Maximum damage reduction cap              |

</details>

<details>
<summary><b>💍 Baubles</b></summary>

| Option                         | Description                                                                                         |
|--------------------------------|-----------------------------------------------------------------------------------------------------|
| **Enable Bauble Marks**        | Turn the bauble mark command and marked items on or off                                             |
| **Unmarked Bauble Attributes** | Which vanilla-format attributes on unmarked baubles apply while worn: `ALL` (default), `MOD_ONLY`, `OFF` |

</details>

### 🔧 Requirements

| Component    | Version                    |
|--------------|----------------------------|
| Minecraft    | 1.12.2                     |
| Forge        | 14.23.5.2855 or higher     |
| Java         | 8                          |
| Dependencies | None (Baubles optional)    |

### 🤝 Compatibility

- ✅ **Baubles** - Bauble attribute support and bauble marks
- ✅ **Most weapon mods** - Attributes work with any damage source
- ✅ **RPG modpacks** - Designed for integration

### 📚 Documentation

- 📖 [Wiki (Chinese)](https://www.mcmod.cn/class/8002.html) - Full documentation
- 🐛 [Report Issues](https://github.com/Roinflam/BattleCorrection/issues) - Bug reports and suggestions
- 💬 [Developer's Bilibili](https://space.bilibili.com/285030707/) - Contact and updates

### ❓ FAQ

<details>
<summary><b>How do I add attributes to items?</b></summary>

Use the built-in item editor, NBT editing tools, or mods like CraftTweaker to add attribute modifiers to items. The
attributes will be automatically recognized. Attributes on worn baubles work too.

</details>

<details>
<summary><b>Does lifesteal work with bows?</b></summary>

Yes! "Almighty Bloodthirsty" works with ALL damage types including arrows, projectiles, and magic.

</details>

<details>
<summary><b>Is lifesteal boosted by healing bonuses?</b></summary>

By default, yes: lifesteal healing is multiplied by the heal multiplier config (`restoreHeal`) and the "Restore Heal"
attribute like any other healing. Turn off `lifestealAffectedByHealBonus` in the attribute config to make lifesteal
always heal damage × lifesteal ratio.

</details>

<details>
<summary><b>What is the overflow mechanic?</b></summary>

When your critical chance exceeds 100%, the excess is converted to bonus critical damage. For example, 150% crit
chance = 100% crit + 0.5× bonus crit damage (configurable conversion rate).

</details>

<details>
<summary><b>Can I disable attack cooldown?</b></summary>

Yes! Set "Attack Cooldown" to `false` in the config to get 1.8-style combat with no cooldown.

</details>

<details>
<summary><b>How do I configure the mod?</b></summary>

Open the Mods menu (or press `K`), find BattleCorrection, and click Config. You can also edit `config/battlecorrection.cfg`
directly.

</details>

<details>
<summary><b>I'm updating from an older version. Do I need to change my config?</b></summary>

No. The config is converted automatically on first launch, and the old file is kept as
`config/battlecorrection.cfg.v0.bak`. The update also fixes these old problems:

- "Vanilla Critical Damage" replaced the vanilla 1.5× jump-attack multiplier instead of adding to it, so with the old
  default (0.5) jump attacks dealt only half of a normal hit. It now adds on top of 1.5×, and old values are converted
  so each one gives the damage its description promised (the old 0.5 now really means 150%).
- The hunger damage decay default used to be 0.05, so every hit lost 5% damage whenever hunger was not full. Configs
  that still have exactly that old default are reset to 0 (off); values you changed yourself are kept.
- With the default config, invulnerability frames were removed completely. The default now keeps vanilla
  invulnerability; the old default 1.0 is converted to the new default 0.
- Bow speed and use speed values are converted so the actual speeds stay the same.

</details>

### 📜 License

**Custom License:**

- ✅ Free for personal use and modification
- ✅ Free to include in modpacks
- ❌ No commercial use without permission
- ⚠️ Modified redistributions must credit the original author

### 👤 Credits

**Developer:** Roinflam  
**Special Thanks:** 建议重开

</details>

---

<details>
<summary><h2>🇨🇳 中文 (点击展开/折叠)</h2></summary>

### 📖 关于模组

**战斗修正**是一个为 Minecraft 1.12.2 制作的 RPG 风格战斗增强模组。它引入了全面的属性系统、高级暴击机制、吸血能力和丰富的战斗平衡选项。非常适合需要深度战斗自定义的
RPG 整合包和服务器。

### ✨ 特色功能

- 🎯 **15+ 自定义属性** - 魔法伤害、箭矢伤害、吸血、闪避几率等
- 💥 **高级暴击系统** - 自定义暴击率和暴击伤害，支持溢出转化机制
- 🩸 **吸血机制** - 近战吸血和全能吸血（对所有伤害类型有效）
- ⚖️ **战斗平衡** - 精细调整 PVP 伤害、攻击冷却、无敌帧
- 🌋 **环境伤害** - 摔落、火焰、溺水、爆炸分别设倍率，还能设伤害下限
- 🍖 **饥饿系统** - 增强的饥饿恢复和基于饥饿值的伤害衰减
- 💍 **Baubles 支持** - 饰品栏属性联动，任意物品都能标记成饰品
- 🛡️ **伤害减免** - 固定伤害忽略和摔落伤害减免
- ⚡ **速度调整** - 拉弓速度和物品使用速度
- 🧰 **管理工具** - 创造模式权杖，用于实体控制、治疗等
- ✏️ **物品编辑器** - 游戏内编辑名字、Lore、附魔、属性和原始 NBT（仅管理员）
- 🔧 **高度可配置** - 每个功能都可通过游戏内配置界面调整

### 📥 安装方法

1. **下载 Minecraft Forge** 1.12.2 版本（推荐最新版）
    - [下载 Forge →](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)

2. **下载战斗修正**从 CurseForge
    - [下载模组 →](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)

3. **可选：** [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles)（饰品栏支持）

4. **安装模组**
    - 将 `.jar` 文件放入 `.minecraft/mods/` 文件夹
    - 使用 Forge 启动游戏

5. **完成！**按 `K` 键打开模组列表，在模组选项中配置

### 🎯 属性系统

<details>
<summary><b>⚔️ 伤害属性</b></summary>

| 属性        | 描述                 |
|-----------|--------------------|
| **魔法伤害**  | 魔法攻击的额外伤害（药水、模组魔法） |
| **箭矢伤害**  | 弓箭攻击的额外伤害          |
| **弹射物伤害** | 非箭矢弹射物的额外伤害        |

</details>

<details>
<summary><b>🛡️ 防御属性</b></summary>

| 属性         | 描述          |
|------------|-------------|
| **免疫伤害**   | 完全闪避攻击的几率   |
| **忽略伤害**   | 所有来源的固定伤害减免 |
| **减少摔落伤害** | 固定摔落伤害减免    |

</details>

<details>
<summary><b>❤️ 恢复属性</b></summary>

| 属性       | 描述                |
|----------|-------------------|
| **治疗恢复** | 受到的所有治疗的倍率        |
| **吸血**   | 近战攻击的生命偷取（需要完全蓄力） |
| **全能吸血** | 所有伤害类型的生命偷取       |

</details>

<details>
<summary><b>💨 速度属性</b></summary>

| 属性       | 描述                 |
|----------|--------------------|
| **拉弓速度** | 提高弓的蓄力速度           |
| **使用速度** | 提高物品使用速度（食物、药水、盾牌） |
| **跳跃提升** | 增加跳跃高度             |

</details>

<details>
<summary><b>💥 暴击系统</b></summary>

| 属性          | 描述               |
|-------------|------------------|
| **原版暴击伤害**  | 下坠暴击的额外伤害        |
| **自定义暴击率**  | 自定义暴击的基础几率（支持溢出） |
| **自定义暴击伤害** | 暴击触发时的伤害倍率       |

**溢出机制：**当暴击率超过 100% 时，溢出部分会转化为额外暴击伤害！

</details>

### 🧰 管理工具

<details>
<summary><b>⚔️ 战斗权杖</b></summary>

| 物品       | 描述            |
|----------|---------------|
| **敌对权杖** | 使两个生物互相攻击     |
| **叛军权杖** | 使周围所有生物攻击一个目标 |
| **暴动权杖** | 使生物攻击附近随机生物   |
| **群殴权杖** | 使附近所有生物互相攻击   |
| **消灭权杖** | 使不同种类的生物互相攻击  |

</details>

<details>
<summary><b>❤️ 治疗权杖</b></summary>

| 物品         | 描述            |
|------------|---------------|
| **治疗权杖**   | 完全恢复一个生物的生命   |
| **范围治疗权杖** | 完全恢复附近所有生物的生命 |
| **献祭权杖**   | 立即杀死一个生物      |
| **范围献祭权杖** | 立即杀死附近所有非玩家生物 |
| **恢复权杖**   | 移除生物的所有药水效果   |

</details>

<details>
<summary><b>⚔️ 示例武器</b></summary>

| 物品       | 攻击伤害 | 耐久      |
|----------|------|---------|
| **基础之剑** | 9    | 1,000   |
| **进阶之剑** | 99   | 10,000  |
| **大师之剑** | 999  | 100,000 |

*这些剑用于演示模组的属性系统*

</details>

### 💍 饰品栏标记（需要 Baubles）

管理员可以用 `/battlecorrection bauble` 把手上的物品变成饰品：

| 命令                                         | 说明                              |
|--------------------------------------------|---------------------------------|
| `add <饰品栏>` / `remove <饰品栏>` / `clear` / `list` | 设置物品能放进哪些饰品栏（`any` = 所有饰品栏）     |
| `attr add <属性> <数值> [运算] [饰品栏]`           | 附加只在佩戴时生效的属性                    |
| `attr remove <属性> [饰品栏]` / `attr clear`     | 移除附加属性                          |

饰品栏：`amulet` 护身符、`ring` 指环、`belt` 腰带、`head` 头部、`body` 身体、`charm` 坠饰。
标记过的物品只在饰品栏里生效：拿在手上时属性不生效，标记过的防具也不能再穿进盔甲栏。标记存在物品本身上，复制、交易都会带着。

### ✏️ 物品编辑器

拥有管理员权限（等级 2）且处于创造模式时，按 `I`（或输入 `/battlecorrection edit`）编辑手上的物品：
名字、Lore、附魔、属性、饰品栏标记、NBT 树和原始 SNBT。按 `O` 直接打开 SNBT 页，按 `P` 直接打开 NBT 树。
修改由服务端检查后才会生效。

### ⚙️ 配置选项

<details>
<summary><b>🎮 战斗设置</b></summary>

| 选项         | 描述              |
|------------|-----------------|
| **PVP 伤害** | 玩家对玩家伤害倍率       |
| **自伤**     | 自我伤害倍率          |
| **连击修正**   | 未蓄力攻击伤害降低       |
| **攻击冷却**   | 启用/禁用 1.9+ 攻击冷却 |
| **无敌时间**   | 调整实体/玩家的无敌帧     |
| **饥饿衰减**   | 基于饥饿值的伤害减少      |

</details>

<details>
<summary><b>📊 伤害倍率</b></summary>

| 选项                   | 描述          |
|----------------------|-------------|
| **玩家近战/箭矢/弹射物/魔法攻击** | 输出伤害倍率      |
| **玩家承受近战/箭矢/弹射物/魔法** | 受到伤害倍率      |
| **玩家承受环境伤害（总/摔落/火焰/溺水/爆炸）** | 没有活物攻击者的伤害倍率，在护甲和其他减伤之后结算，总倍率与类别倍率相乘；虚空和 /kill 不受影响 |
| **玩家环境伤害下限（最大生命比例 / 固定值）** | 每次环境伤害至少造成两者中较大的那个，默认关闭；总倍率为 0（免疫）时不生效 |
| **第三方魔法识别**          | 把其他模组的魔法伤害类型也算作魔法（前缀 / 白名单 / 黑名单） |
| **实体最大生命**           | 非玩家实体的生命值倍率 |

</details>

<details>
<summary><b>🍖 饥饿系统</b></summary>

| 选项           | 描述               |
|--------------|------------------|
| **饱和度固定恢复**  | 饱和度恢复时每次额外生命值    |
| **饱和度百分比恢复** | 饱和度恢复时每次额外生命百分比  |
| **饥饿值固定恢复**  | 饥饿值≥18时每次额外生命值   |
| **饥饿值百分比恢复** | 饥饿值≥18时每次额外生命百分比 |
| **饥饿伤害衰减**   | 每少1点饥饿值的伤害减少     |
| **饥饿衰减上限**   | 最大伤害减少百分比        |

</details>

<details>
<summary><b>💍 饰品栏</b></summary>

| 选项            | 描述                                                       |
|---------------|----------------------------------------------------------|
| **启用饰品栏标记**   | 开关饰品栏标记命令和标记过的物品                                         |
| **未标记饰品的原版属性** | 没标记的饰品上原版格式的属性戴着时哪些生效：`ALL`（默认）、`MOD_ONLY`、`OFF` |

</details>

### 🔧 运行需求

| 组件        | 版本                  |
|-----------|---------------------|
| Minecraft | 1.12.2              |
| Forge     | 14.23.5.2855 或更高    |
| Java      | 8                   |
| 前置模组      | 无（Baubles 可选）       |

### 🤝 兼容性

- ✅ **Baubles** - 饰品栏属性支持和饰品栏标记
- ✅ **大多数武器模组** - 属性适用于任何伤害来源
- ✅ **RPG 整合包** - 专为整合设计

### 📚 文档资料

- 📖 [完整百科](https://www.mcmod.cn/class/8002.html) - 完整文档
- 🐛 [反馈问题](https://github.com/Roinflam/BattleCorrection/issues) - Bug 报告和建议
- 💬 [开发者的 B 站](https://space.bilibili.com/285030707/) - 联系和更新

### ❓ 常见问题

<details>
<summary><b>如何为物品添加属性？</b></summary>

用自带的物品编辑器、NBT 编辑工具或 CraftTweaker 之类的模组为物品添加属性修改器。属性会被自动识别。戴在饰品栏里的饰品上的属性同样生效。

</details>

<details>
<summary><b>吸血对弓箭有效吗？</b></summary>

有效！"全能吸血"对所有伤害类型都有效，包括箭矢、弹射物和魔法。

</details>

<details>
<summary><b>吸血会吃治疗加成吗？</b></summary>

默认会：吸血回的血和其他治疗一样，会乘上治疗倍率配置（restoreHeal）和装备上的「治疗恢复」属性。
不想让两者叠加，把属性配置里的「吸血吃治疗加成」（`lifestealAffectedByHealBonus`）关掉，吸血就固定回 伤害 × 吸血比例。

</details>

<details>
<summary><b>什么是溢出机制？</b></summary>

当暴击率超过 100% 时，超出部分会转化为额外暴击伤害。例如，150% 暴击率 = 100% 暴击 + 0.5 倍额外暴击伤害（转化比例可配置）。

</details>

<details>
<summary><b>可以禁用攻击冷却吗？</b></summary>

可以！在配置中将"攻击冷却"设为 `false`，即可获得 1.8 风格的无冷却战斗。

</details>

<details>
<summary><b>如何配置模组？</b></summary>

打开模组列表（或按 `K`），找到战斗修正，点击配置按钮。也可以直接编辑配置文件 `config/battlecorrection.cfg`。

</details>

<details>
<summary><b>从旧版本升级，配置要改吗？</b></summary>

不用。第一次启动时会自动转换配置，旧文件另存为 `config/battlecorrection.cfg.v0.bak`。这次更新还修了这几个老问题：

- 「原版暴击伤害」以前会顶掉原版跳劈的 1.5 倍，而不是在它上面加，旧默认值 0.5 下跳劈只有平 A 的一半。现在改成在 1.5 倍之上加，旧数值会自动换算，按配置说明里写的效果生效（旧的 0.5 现在就是 150%）。
- 饥饿伤害衰减以前默认是 0.05，饥饿值不满时每一击少 5%。配置里还是这个旧默认值的会改成 0（关闭），自己改过的数值保留不动。
- 默认配置下无敌时间被完全清掉了。现在默认保持原版无敌时间，旧默认值 1.0 会换算成新的默认值 0。
- 拉弓速度、使用速度的数值会自动换算，实际速度不变。

</details>

### 📜 许可证

**自定义许可证：**

- ✅ 可自由用于个人使用和修改
- ✅ 可自由加入整合包
- ❌ 未经许可禁止商业使用
- ⚠️ 修改后重新发布必须注明原作者

### 👤 制作人员

**开发者：** Roinflam  
**特别鸣谢：** 建议重开

</details>

---

<div align="center">

**Made with ❤️ by Roinflam**

*"Change the way and difficulty in battle, more suitable for RPG games!"*

[![GitHub Stars](https://img.shields.io/github/stars/Roinflam/BattleCorrection?style=social)](https://github.com/Roinflam/BattleCorrection)

</div>
