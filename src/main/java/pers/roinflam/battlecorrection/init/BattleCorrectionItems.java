package pers.roinflam.battlecorrection.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import pers.roinflam.battlecorrection.item.*;

import java.util.ArrayList;
import java.util.List;

public class BattleCorrectionItems {
    public static final List<Item> ITEMS = new ArrayList<Item>();

    public static final EnemyStaff ENEMY_STAFF = new EnemyStaff("enemy_staff", CreativeTabs.TOOLS);
    public static final RebelStaff REBEL_STAFF = new RebelStaff("rebel_staff", CreativeTabs.TOOLS);
    public static final RiotStaff RIOT_STAFF = new RiotStaff("riot_staff", CreativeTabs.TOOLS);
    public static final BrawlStaff BRAWL_STAFF = new BrawlStaff("brawl_staff", CreativeTabs.TOOLS);
    public static final EliminationStaff ELIMINATION_STAFF = new EliminationStaff("elimination_staff", CreativeTabs.TOOLS);

}
