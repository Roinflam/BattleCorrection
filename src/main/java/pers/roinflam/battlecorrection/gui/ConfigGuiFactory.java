package pers.roinflam.battlecorrection.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.DummyConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.config.ConfigBaubles;
import pers.roinflam.battlecorrection.config.ConfigEditor;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 模组配置界面
 * <p>
 * 每个配置类一个分类按钮（战斗 / 属性 / 饰品 / 物品编辑器）。
 * 旧版本只列出了战斗分类的条目，属性配置在界面里改不了。
 */
@SideOnly(Side.CLIENT)
public class ConfigGuiFactory implements IModGuiFactory {

    /**
     * 不在界面里显示的配置项（只给程序用）
     */
    private static final String HIDDEN_CONFIG_VERSION = "configVersion";

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
        List<IConfigElement> categories = new ArrayList<>();
        categories.add(category(ConfigBattle.class));
        categories.add(category(ConfigAttribute.class));
        categories.add(category(ConfigBaubles.class));
        categories.add(category(ConfigEditor.class));
        return new GuiConfig(guiScreen, categories, Reference.MOD_ID, false, true, "Battle Correction", "For RPG!");
    }

    /**
     * 把一个 @Config 类包装成分类按钮
     * <p>
     * Forge 自带的 ConfigElement.from 给所有分类用同一个名字（模组 id），这里按 category 重新命名，
     * 顺便去掉只给程序用的配置项。
     *
     * @param configClass 配置类
     * @return 分类元素
     */
    @Nonnull
    private static IConfigElement category(@Nonnull Class<?> configClass) {
        IConfigElement element = ConfigElement.from(configClass);
        List<IConfigElement> children = new ArrayList<>();
        for (IConfigElement child : element.getChildElements()) {
            if (!HIDDEN_CONFIG_VERSION.equals(child.getName())) {
                children.add(child);
            }
        }
        Config annotation = configClass.getAnnotation(Config.class);
        return new DummyConfigElement.DummyCategoryElement(annotation.category(), element.getLanguageKey(), children);
    }

    @Nonnull
    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
