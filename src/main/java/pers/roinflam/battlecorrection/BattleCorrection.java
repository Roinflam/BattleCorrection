package pers.roinflam.battlecorrection;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import pers.roinflam.battlecorrection.proxy.CommonProxy;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

@Mod(modid = Reference.MOD_ID, useMetadata = true, guiFactory = "pers.roinflam.battlecorrection.gui.ConfigGuiFactory")
public class BattleCorrection {

    @Mod.Instance
    public static BattleCorrection instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        proxy.setInfinityItemEditor();
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        proxy.setInfinityItemEditor();
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        proxy.setInfinityItemEditor();
    }
}
