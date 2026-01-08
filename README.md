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
- 🍖 **Hunger System** - Damage decay based on hunger level
- 💍 **Baubles Support** - Full integration with Baubles accessory slots
- 🛡️ **Damage Reduction** - Flat damage ignore and fall damage reduction
- ⚡ **Speed Modifiers** - Bow draw speed and item use speed adjustments
- 🔧 **Highly Configurable** - Every feature can be adjusted via config GUI

### 📥 Installation

1. **Download Minecraft Forge** for 1.12.2 (latest version recommended)
    - [Download Forge →](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)

2. **Download BattleCorrection** from CurseForge
    - [Download Mod →](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)

3. **Install the mod**
    - Place the `.jar` file into `.minecraft/mods/` folder
    - Launch Minecraft with Forge profile

4. **Done!** Press `K` to open mod list, configure in Mod Options

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

### 🔧 Requirements

| Component    | Version                 |
|--------------|-------------------------|
| Minecraft    | 1.12.2                  |
| Forge        | Latest for 1.12.2       |
| Java         | 8 or higher             |
| Dependencies | None (Baubles optional) |

### 🤝 Compatibility

- ✅ **Baubles** - Full accessory slot attribute support
- ✅ **Most weapon mods** - Attributes work with any damage source
- ✅ **RPG modpacks** - Designed for integration

### 📚 Documentation

- 📖 [Wiki (Chinese)](https://www.mcmod.cn/class/xxxx.html) - Full documentation
- 🐛 [Report Issues](https://github.com/Roinflam/BattleCorrection/issues) - Bug reports and suggestions
- 💬 [Developer's Bilibili](https://space.bilibili.com/285030707/) - Contact and updates

### ❓ FAQ

<details>
<summary><b>How do I add attributes to items?</b></summary>

Use NBT editing tools or other mods like ContentTweaker/CraftTweaker to add attribute modifiers to items. The attributes
will be automatically recognized.

</details>

<details>
<summary><b>Does lifesteal work with bows?</b></summary>

Yes! "Almighty Bloodthirsty" works with ALL damage types including arrows, projectiles, and magic.

</details>

<details>
<summary><b>What is the overflow mechanic?</b></summary>

When your critical chance exceeds 100%, the excess is converted to bonus critical damage. For example, 150% crit
chance = 100% crit + 0.5× bonus crit damage.

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
- 🍖 **饥饿系统** - 基于饥饿值的伤害衰减
- 💍 **Baubles 支持** - 完整的饰品栏属性联动
- 🛡️ **伤害减免** - 固定伤害忽略和摔落伤害减免
- ⚡ **速度调整** - 拉弓速度和物品使用速度
- 🔧 **高度可配置** - 每个功能都可通过配置界面调整

### 📥 安装方法

1. **下载 Minecraft Forge** 1.12.2 版本（推荐最新版）
    - [下载 Forge →](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)

2. **下载战斗修正**从 CurseForge
    - [下载模组 →](https://www.curseforge.com/minecraft/mc-mods/battlecorrection)

3. **安装模组**
    - 将 `.jar` 文件放入 `.minecraft/mods/` 文件夹
    - 使用 Forge 启动游戏

4. **完成！**按 `K` 键打开模组列表，在模组选项中配置

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

### 🔧 运行需求

| 组件        | 版本            |
|-----------|---------------|
| Minecraft | 1.12.2        |
| Forge     | 1.12.2 最新版    |
| Java      | 8 或更高         |
| 前置模组      | 无（Baubles 可选） |

### 🤝 兼容性

- ✅ **Baubles** - 完整的饰品栏属性支持
- ✅ **大多数武器模组** - 属性适用于任何伤害来源
- ✅ **RPG 整合包** - 专为整合设计

### 📚 文档资料

- 📖 [完整百科](https://www.mcmod.cn/class/xxxx.html) - 完整文档
- 🐛 [反馈问题](https://github.com/Roinflam/BattleCorrection/issues) - Bug 报告和建议
- 💬 [开发者的 B 站](https://space.bilibili.com/285030707/) - 联系和更新

### ❓ 常见问题

<details>
<summary><b>如何为物品添加属性？</b></summary>

使用 NBT 编辑工具或其他模组如 ContentTweaker/CraftTweaker 为物品添加属性修改器。属性会被自动识别。

</details>

<details>
<summary><b>吸血对弓箭有效吗？</b></summary>

有效！"全能吸血"对所有伤害类型都有效，包括箭矢、弹射物和魔法。

</details>

<details>
<summary><b>什么是溢出机制？</b></summary>

当暴击率超过 100% 时，超出部分会转化为额外暴击伤害。例如，150% 暴击率 = 100% 暴击 + 0.5 倍额外暴击伤害。

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