<div align="center">

# ⚔️ BattleCorrection | 战斗修正

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_battlecorrection_downloads.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)
[![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-Latest-orange?style=for-the-badge)](https://files.minecraftforge.net/)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

---

### 🌏 Choose Your Language / 选择语言

</div>

<details open>
<summary><h2>🇺🇸 English (Click to expand/collapse)</h2></summary>

### 📖 About

**BattleCorrection** is an RPG-style combat enhancement mod for Minecraft 1.20.1. It introduces a comprehensive
attribute system, advanced critical hit mechanics, lifesteal abilities, and extensive battle balance options. Perfect
for RPG modpacks and servers that want deeper combat customization.

### ✨ Features

- 🎯 **15+ Custom Attributes** - Magic damage, arrow damage, lifesteal, dodge chance, and more
- 💥 **Advanced Critical System** - Custom crit chance & damage with overflow conversion mechanic
- 🩸 **Lifesteal Mechanics** - Melee lifesteal and universal lifesteal for all damage types
- ⚖️ **Battle Balance** - Fine-tune PVP damage, attack cooldown, invulnerability frames
- 🍖 **Hunger System** - Enhanced hunger regeneration and damage decay based on hunger level
- 💍 **Curios Support** - Full integration with Curios accessory slots
- 🛡️ **Damage Reduction** - Flat damage ignore and fall damage reduction
- ⚡ **Speed Modifiers** - Bow draw speed and item use speed adjustments
- 🧰 **Management Tools** - Creative staffs for entity control, healing, and more
- 🔧 **Highly Configurable** - In-game config GUI powered by Cloth Config
- 🎮 **1.9+ Combat** - Toggle attack cooldown and combo correction systems

### 📥 Installation

1. **Download Minecraft Forge** for 1.20.1 (latest version recommended)
    - [Download Forge →](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)

2. **Download BattleCorrection** from CurseForge
    - [Download Mod →](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)

3. **Install required dependencies**
    - [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config) (Required)
    - [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) (Optional, for accessory support)

4. **Install the mod**
    - Place all `.jar` files into `.minecraft/mods/` folder
    - Launch Minecraft with Forge profile

5. **Done!** Open Mod Menu and configure BattleCorrection settings

### 🎯 Attribute System

<details>
<summary><b>⚔️ Damage Attributes</b></summary>

| Attribute             | Description                                            |
|-----------------------|--------------------------------------------------------|
| **Magic Damage**      | Bonus damage for magic attacks (potions, modded magic) |
| **Arrow Damage**      | Bonus damage for bow and crossbow attacks              |
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

| Option                                          | Description                               |
|-------------------------------------------------|-------------------------------------------|
| **Player Melee/Arrow/Projectile/Magic Attack**  | Output damage multipliers                 |
| **Player Suffers Melee/Arrow/Projectile/Magic** | Incoming damage multipliers               |
| **Entity Max Health**                           | Health multiplier for non-player entities |

</details>

<details>
<summary><b>🍖 Hunger System</b></summary>

| Option                           | Description                               |
|----------------------------------|-------------------------------------------|
| **Saturation Healing (Flat)**    | Extra HP per tick from saturation         |
| **Saturation Healing (Percent)** | Extra HP% per tick from saturation        |
| **Hunger Healing (Flat)**        | Extra HP per tick when hunger ≥ 18        |
| **Hunger Healing (Percent)**     | Extra HP% per tick when hunger ≥ 18       |
| **Hunger Damage Decay**          | Damage reduction per missing hunger point |
| **Hunger Decay Limit**           | Maximum damage reduction cap              |

</details>

### 🔧 Requirements

| Component    | Version                  |
|--------------|--------------------------|
| Minecraft    | 1.20.1                   |
| Forge        | 47.3.0 or higher         |
| Java         | 17 or higher             |
| Dependencies | Cloth Config (Required)  |
| Optional     | Curios API (Recommended) |

### 🤝 Compatibility

- ✅ **Curios API** - Full accessory slot attribute support
- ✅ **Most weapon mods** - Attributes work with any damage source
- ✅ **RPG modpacks** - Designed for integration
- ✅ **Multiplayer** - Full server/client synchronization

### 📚 Documentation

- 📖 [Wiki (Chinese)](https://www.mcmod.cn/class/8002.html) - Full documentation
- 🐛 [Report Issues](https://github.com/Roinflam/BattleCorrection/issues) - Bug reports and suggestions
- 💬 [Developer's Bilibili](https://space.bilibili.com/285030707/) - Contact and updates

### ❓ FAQ

<details>
<summary><b>How do I add attributes to items?</b></summary>

Use data packs, NBT editing tools, or mods like KubeJS/CraftTweaker to add attribute modifiers to items. The attributes
will be automatically recognized. For Curios accessories, the attributes work automatically when worn.

</details>

<details>
<summary><b>Does lifesteal work with bows?</b></summary>

Yes! "Almighty Bloodthirsty" works with ALL damage types including arrows, projectiles, and magic.

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

Open the Mods menu (Mod List), find BattleCorrection, and click the config button. You can also edit the config files
directly in `config/battlecorrection-battle.toml` and `config/battlecorrection-attribute.toml`.

</details>

### 📜 License

**Custom License:**

- ✅ Free for personal use and modification
- ✅ Free to include in modpacks
- ❌ No commercial use without permission
- ⚠️ Modified redistributions must credit the original author

### 👤 Credits

**Developer:** Roinflam  
**Special Thanks:** 建议重开 (for support and assistance)

</details>

---

<details>
<summary><h2>🇨🇳 中文 (点击展开/折叠)</h2></summary>

### 📖 关于模组

**战斗修正**是一个为 Minecraft 1.20.1 制作的 RPG 风格战斗增强模组。它引入了全面的属性系统、高级暴击机制、吸血能力和丰富的战斗平衡选项。非常适合需要深度战斗自定义的
RPG 整合包和服务器。

### ✨ 特色功能

- 🎯 **15+ 自定义属性** - 魔法伤害、箭矢伤害、吸血、闪避几率等
- 💥 **高级暴击系统** - 自定义暴击率和暴击伤害，支持溢出转化机制
- 🩸 **吸血机制** - 近战吸血和全能吸血（对所有伤害类型有效）
- ⚖️ **战斗平衡** - 精细调整 PVP 伤害、攻击冷却、无敌帧
- 🍖 **饥饿系统** - 增强的饥饿恢复和基于饥饿值的伤害衰减
- 💍 **Curios 支持** - 完整的饰品栏属性联动
- 🛡️ **伤害减免** - 固定伤害忽略和摔落伤害减免
- ⚡ **速度调整** - 拉弓速度和物品使用速度
- 🧰 **管理工具** - 创造模式权杖，用于实体控制、治疗等
- 🔧 **高度可配置** - 游戏内配置界面，由 Cloth Config 驱动
- 🎮 **1.9+ 战斗** - 可切换攻击冷却和连击修正系统

### 📥 安装方法

1. **下载 Minecraft Forge** 1.20.1 版本（推荐最新版）
    - [下载 Forge →](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)

2. **下载战斗修正**从 CurseForge
    - [下载模组 →](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)

3. **安装必需前置**
    - [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config)（必需）
    - [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)（可选，饰品栏支持）

4. **安装模组**
    - 将所有 `.jar` 文件放入 `.minecraft/mods/` 文件夹
    - 使用 Forge 启动游戏

5. **完成！**打开模组菜单，配置战斗修正设置

### 🎯 属性系统

<details>
<summary><b>⚔️ 伤害属性</b></summary>

| 属性        | 描述                 |
|-----------|--------------------|
| **魔法伤害**  | 魔法攻击的额外伤害（药水、模组魔法） |
| **箭矢伤害**  | 弓和弩攻击的额外伤害         |
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
| **实体最大生命**           | 非玩家实体的生命值倍率 |

</details>

<details>
<summary><b>🍖 饥饿系统</b></summary>

| 选项           | 描述               |
|--------------|------------------|
| **饱和度固定恢复**  | 饱和度恢复时每刻额外生命值    |
| **饱和度百分比恢复** | 饱和度恢复时每刻额外生命百分比  |
| **饥饿值固定恢复**  | 饥饿值≥18时每刻额外生命值   |
| **饥饿值百分比恢复** | 饥饿值≥18时每刻额外生命百分比 |
| **饥饿伤害衰减**   | 每少1点饥饿值的伤害减少     |
| **饥饿衰减上限**   | 最大伤害减少百分比        |

</details>

### 🔧 运行需求

| 组件        | 版本               |
|-----------|------------------|
| Minecraft | 1.20.1           |
| Forge     | 47.3.0 或更高       |
| Java      | 17 或更高           |
| 前置模组      | Cloth Config（必需） |
| 可选前置      | Curios API（推荐）   |

### 🤝 兼容性

- ✅ **Curios API** - 完整的饰品栏属性支持
- ✅ **大多数武器模组** - 属性适用于任何伤害来源
- ✅ **RPG 整合包** - 专为整合设计
- ✅ **多人游戏** - 完整的服务端/客户端同步

### 📚 文档资料

- 📖 [完整百科](https://www.mcmod.cn/class/8002.html) - 完整文档
- 🐛 [反馈问题](https://github.com/Roinflam/BattleCorrection/issues) - Bug 报告和建议
- 💬 [开发者的 B 站](https://space.bilibili.com/285030707/) - 联系和更新

### ❓ 常见问题

<details>
<summary><b>如何为物品添加属性？</b></summary>

使用数据包、NBT 编辑工具或模组如 KubeJS/CraftTweaker 为物品添加属性修改器。属性会被自动识别。对于 Curios 饰品，佩戴后属性会自动生效。

</details>

<details>
<summary><b>吸血对弓箭有效吗？</b></summary>

有效！"全能吸血"对所有伤害类型都有效，包括箭矢、弹射物和魔法。

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

打开模组菜单（模组列表），找到战斗修正，点击配置按钮。你也可以直接编辑配置文件：`config/battlecorrection-battle.toml`
和 `config/battlecorrection-attribute.toml`。

</details>

### 📜 许可证

**自定义许可证：**

- ✅ 可自由用于个人使用和修改
- ✅ 可自由加入整合包
- ❌ 未经许可禁止商业使用
- ⚠️ 修改后重新发布必须注明原作者

### 👤 制作人员

**开发者：** Roinflam  
**特别鸣谢：** 建议重开（提供支持和帮助）

</details>

---

<div align="center">

**Made with ❤️ by Roinflam**

*"Change the way and difficulty in battle, more suitable for RPG games!"*

[![GitHub Stars](https://img.shields.io/github/stars/Roinflam/BattleCorrection?style=social)](https://github.com/Roinflam/BattleCorrection)

</div>