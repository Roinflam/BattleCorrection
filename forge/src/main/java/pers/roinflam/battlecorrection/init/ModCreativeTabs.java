// 文件：ModCreativeTabs.java
// 路径：src/main/java/pers/roinflam/battlecorrection/init/ModCreativeTabs.java
package pers.roinflam.battlecorrection.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 创造模式标签页注册类
 */
public class ModCreativeTabs {

    /**
     * 创造模式标签页延迟注册器
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Reference.MOD_ID);

    /**
     * 战斗修正标签页
     */
    public static final RegistryObject<CreativeModeTab> BATTLE_CORRECTION_TAB = CREATIVE_MODE_TABS.register(
            "battlecorrection_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.ENEMY_STAFF.get()))
                    .title(Component.translatable("itemGroup.battlecorrection_tab"))
                    .displayItems((parameters, output) -> {
                        // 管理权杖
                        output.accept(ModItems.ENEMY_STAFF.get());
                        output.accept(ModItems.REBEL_STAFF.get());
                        output.accept(ModItems.RIOT_STAFF.get());
                        output.accept(ModItems.BRAWL_STAFF.get());
                        output.accept(ModItems.ELIMINATION_STAFF.get());

                        // 治疗权杖
                        output.accept(ModItems.HEALING_STAFF.get());
                        output.accept(ModItems.RANGE_HEALING_STAFF.get());
                        output.accept(ModItems.SACRIFICIAL_STAFF.get());
                        output.accept(ModItems.RANGE_SACRIFICIAL_STAFF.get());
                        output.accept(ModItems.RESTORATION_STAFF.get());

                        // 剑类武器
                        output.accept(ModItems.BASE_SWORD.get());
                        output.accept(ModItems.ADVANCED_SWORD.get());
                        output.accept(ModItems.MASTER_SWORD.get());
                    })
                    .build()
    );
}