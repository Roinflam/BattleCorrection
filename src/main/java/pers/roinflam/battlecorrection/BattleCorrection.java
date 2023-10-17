package pers.roinflam.battlecorrection;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;
import pers.roinflam.battlecorrection.proxy.CommonProxy;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

//, dependencies = "required-after:potioncore;after:baubles"
// dependencies = "after:baubles"

@Mod(modid = Reference.MOD_ID, useMetadata = true, guiFactory = "pers.roinflam.battlecorrection.gui.ConfigGuiFactory", dependencies = "before:infinityeditor")
public class BattleCorrection {
    public static KeyBinding keyOpenModList;

    @Mod.Instance
    public static BattleCorrection instance;
    
    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        keyOpenModList = new KeyBinding("key.battlecorrection.openModList", Keyboard.KEY_K, "key.categories.misc");
        ClientRegistry.registerKeyBinding(keyOpenModList);
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
    }
}
