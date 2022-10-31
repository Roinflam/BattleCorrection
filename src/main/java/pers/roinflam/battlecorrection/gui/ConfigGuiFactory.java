package pers.roinflam.battlecorrection.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.config.ConfigLoader;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

@Mod.EventBusSubscriber
@SideOnly(Side.CLIENT)
public class ConfigGuiFactory implements IModGuiFactory {
    @SubscribeEvent
    public static void onConfigChanged(@Nonnull ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }

    @Override
    public void initialize(Minecraft minecraft) {

    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Nonnull
    @Override
    public GuiScreen createConfigGui(GuiScreen guiScreen) {
        return new GuiConfig(guiScreen, ConfigElement.from(ConfigLoader.class).getChildElements(), Reference.MOD_ID, false, true, "Battle Correction", "For RPG!");
    }

    @Nonnull
    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
