package pers.roinflam.battlecorrection.init;

import net.minecraft.item.Item;
import pers.roinflam.battlecorrection.item.*;
import pers.roinflam.battlecorrection.item.manage.*;
import pers.roinflam.battlecorrection.item.sword.AdvancedSword;
import pers.roinflam.battlecorrection.item.sword.BaseSword;
import pers.roinflam.battlecorrection.item.sword.MasterSword;
import pers.roinflam.battlecorrection.tabs.BattleCorrectionTab;

import java.util.ArrayList;
import java.util.List;

public class BattleCorrectionItems {
    public static final List<Item> ITEMS = new ArrayList<Item>();

    public static final EnemyStaff ENEMY_STAFF = new EnemyStaff("enemy_staff", BattleCorrectionTab.getTab());
    public static final RebelStaff REBEL_STAFF = new RebelStaff("rebel_staff", BattleCorrectionTab.getTab());
    public static final RiotStaff RIOT_STAFF = new RiotStaff("riot_staff", BattleCorrectionTab.getTab());
    public static final BrawlStaff BRAWL_STAFF = new BrawlStaff("brawl_staff", BattleCorrectionTab.getTab());
    public static final EliminationStaff ELIMINATION_STAFF = new EliminationStaff("elimination_staff", BattleCorrectionTab.getTab());


    public static final SacrificialStaff SACRIFICIAL_STAFF = new SacrificialStaff("sacrificial_staff", BattleCorrectionTab.getTab());
    public static final RangeSacrificialStaff RANGE_SACRIFICIAL_STAFF = new RangeSacrificialStaff("range_sacrificial_staff", BattleCorrectionTab.getTab());
    public static final HealingStaff HEALING_STAFF = new HealingStaff("healing_staff", BattleCorrectionTab.getTab());
    public static final RangeHealingStaff RANGE_HEALING_STAFF = new RangeHealingStaff("range_healing_staff", BattleCorrectionTab.getTab());
    public static final RestorationStaff RESTORATION_STAFF = new RestorationStaff("restoration_staff", BattleCorrectionTab.getTab());

    public static final BaseSword BASE_SWORD = new BaseSword("base_sword", BattleCorrectionTab.getTab());
    public static final AdvancedSword ADVANCED_SWORD = new AdvancedSword("advanced_sword", BattleCorrectionTab.getTab());
    public static final MasterSword MASTER_SWORD = new MasterSword("master_sword", BattleCorrectionTab.getTab());

}
