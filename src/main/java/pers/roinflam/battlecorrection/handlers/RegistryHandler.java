package pers.roinflam.battlecorrection.handlers;

import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.BattleCorrection;
import pers.roinflam.battlecorrection.init.ModItems;
import pers.roinflam.battlecorrection.utils.IHasModel;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class RegistryHandler {

    @SubscribeEvent
    public static void onItemRegister(@Nonnull RegistryEvent.Register<Item> evt) {
        evt.getRegistry().registerAll(ModItems.ITEMS.toArray(new Item[0]));
    }

    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent evt) {
        for (Item item : ModItems.ITEMS) {
            if (item instanceof IHasModel) {
                BattleCorrection.proxy.registerItemRenderer(item, 0, "inventory");
            }
        }
    }
}
