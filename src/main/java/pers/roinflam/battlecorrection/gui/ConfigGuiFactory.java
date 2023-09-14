package pers.roinflam.battlecorrection.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class ConfigGuiFactory implements IModGuiFactory {

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
        return new GuiConfig(guiScreen, ConfigElement.from(ConfigBattle.class).getChildElements(), Reference.MOD_ID, false, true, "Battle Correction", "For RPG!");
    }

    @Nonnull
    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
