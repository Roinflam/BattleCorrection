// 文件：ModItems.java
// 路径：src/main/java/pers/roinflam/battlecorrection/init/ModItems.java
package pers.roinflam.battlecorrection.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.battlecorrection.item.manage.*;
import pers.roinflam.battlecorrection.item.sword.AdvancedSword;
import pers.roinflam.battlecorrection.item.sword.BaseSword;
import pers.roinflam.battlecorrection.item.sword.MasterSword;
import pers.roinflam.battlecorrection.item.sword.ModToolTiers;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 模组物品注册类
 * 使用DeferredRegister进行延迟注册
 */
public class ModItems {

    /**
     * 物品延迟注册器
     */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Reference.MOD_ID);

    // ===== 管理权杖 =====

    /**
     * 敌对权杖 - 右键两个生物使它们互相攻击
     */
    public static final RegistryObject<Item> ENEMY_STAFF = ITEMS.register("enemy_staff",
            () -> new EnemyStaff(new Item.Properties().stacksTo(1)));

    /**
     * 叛军权杖 - 右键一个生物使周围所有生物攻击它
     */
    public static final RegistryObject<Item> REBEL_STAFF = ITEMS.register("rebel_staff",
            () -> new RebelStaff(new Item.Properties().stacksTo(1)));

    /**
     * 暴动权杖 - 右键一个生物使它攻击附近随机生物
     */
    public static final RegistryObject<Item> RIOT_STAFF = ITEMS.register("riot_staff",
            () -> new RiotStaff(new Item.Properties().stacksTo(1)));

    /**
     * 群殴权杖 - 右键使附近所有生物互相攻击
     */
    public static final RegistryObject<Item> BRAWL_STAFF = ITEMS.register("brawl_staff",
            () -> new BrawlStaff(new Item.Properties().stacksTo(1)));

    /**
     * 消灭权杖 - 右键使不同种类生物互相攻击
     */
    public static final RegistryObject<Item> ELIMINATION_STAFF = ITEMS.register("elimination_staff",
            () -> new EliminationStaff(new Item.Properties().stacksTo(1)));

    // ===== 治疗权杖 =====

    /**
     * 治疗权杖 - 右键生物使其完全恢复生命
     */
    public static final RegistryObject<Item> HEALING_STAFF = ITEMS.register("healing_staff",
            () -> new HealingStaff(new Item.Properties().stacksTo(1)));

    /**
     * 范围治疗权杖 - 右键生物使周围所有生物完全恢复生命
     */
    public static final RegistryObject<Item> RANGE_HEALING_STAFF = ITEMS.register("range_healing_staff",
            () -> new RangeHealingStaff(new Item.Properties().stacksTo(1)));

    /**
     * 献祭权杖 - 右键生物使其立即死亡
     */
    public static final RegistryObject<Item> SACRIFICIAL_STAFF = ITEMS.register("sacrificial_staff",
            () -> new SacrificialStaff(new Item.Properties().stacksTo(1)));

    /**
     * 范围献祭权杖 - 右键生物使周围所有非玩家生物立即死亡
     */
    public static final RegistryObject<Item> RANGE_SACRIFICIAL_STAFF = ITEMS.register("range_sacrificial_staff",
            () -> new RangeSacrificialStaff(new Item.Properties().stacksTo(1)));

    /**
     * 恢复权杖 - 右键生物清除其所有药水效果
     */
    public static final RegistryObject<Item> RESTORATION_STAFF = ITEMS.register("restoration_staff",
            () -> new RestorationStaff(new Item.Properties().stacksTo(1)));

    // ===== 剑类武器 =====

    /**
     * 基础之剑
     */
    public static final RegistryObject<Item> BASE_SWORD = ITEMS.register("base_sword",
            () -> new BaseSword(ModToolTiers.BASE, 3, -2.4F, new Item.Properties()));

    /**
     * 进阶之剑
     */
    public static final RegistryObject<Item> ADVANCED_SWORD = ITEMS.register("advanced_sword",
            () -> new AdvancedSword(ModToolTiers.ADVANCED, 3, -2.4F, new Item.Properties()));

    /**
     * 大师之剑
     */
    public static final RegistryObject<Item> MASTER_SWORD = ITEMS.register("master_sword",
            () -> new MasterSword(ModToolTiers.MASTER, 3, -2.4F, new Item.Properties()));
}