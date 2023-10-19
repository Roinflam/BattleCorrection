package pers.roinflam.battlecorrection.proxy;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;
import pers.roinflam.battlecorrection.BattleCorrection;

import javax.annotation.Nonnull;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerItemRenderer(@Nonnull Item item, int meta, String id) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(item.getRegistryName(), id));
    }

    @Override
    public void bindKey() {
        BattleCorrection.keyOpenModList = new KeyBinding("key.battlecorrection.openModList", Keyboard.KEY_K, "key.categories.misc");
        ClientRegistry.registerKeyBinding(BattleCorrection.keyOpenModList);
    }

}
