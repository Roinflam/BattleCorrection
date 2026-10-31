package pers.roinflam.battlecorrection.config;

import net.minecraftforge.common.config.Config;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 饰品栏标记配置类
 * 需要 Baubles；没装 Baubles 时这里的选项都不起作用
 */
@Config(modid = Reference.MOD_ID, category = "Baubles")
@Config.LangKey("config." + Reference.MOD_ID + ".baubles")
public final class ConfigBaubles {

    /**
     * 未标记饰品上原版格式属性（AttributeModifiers NBT、物品自带的、其他模组追加的）的生效范围
     */
    public enum BaubleNbtAttributes {
        /**
         * 不生效（Baubles 原样）
         */
        OFF,
        /**
         * 只有本模组的属性生效（battlecorrection.*）
         */
        MOD_ONLY,
        /**
         * 全部属性生效
         */
        ALL
    }

    @Config.Comment({
            "[EN] Enable the bauble mark feature (/battlecorrection bauble)",
            "     An admin can let the held item go into chosen bauble slots and attach",
            "     extra attributes that only apply while it is worn there",
            "     • true = Enabled (default)",
            "     • false = Command disabled, marked items can no longer be put into bauble slots",
            "       and behave like ordinary items again (held / worn attributes come back)",
            "     Items already worn keep their attributes until taken off",
            "",
            "[中文] 启用饰品栏标记功能(/battlecorrection bauble)",
            "     管理员可以让手上的物品放进指定的饰品栏，并附加只在佩戴时生效的属性",
            "     • true = 启用(默认)",
            "     • false = 命令不可用，标记过的物品不能再放进饰品栏，",
            "       并恢复成普通物品(拿在手上、穿在身上的属性照常生效)",
            "     已经戴着的物品在摘下前属性照常生效"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".baubles.baubleMarkEnabled")
    public static boolean baubleMarkEnabled = true;

    @Config.Comment({
            "[EN] Which vanilla-format attributes on an UNMARKED bauble take effect while it is worn",
            "     (AttributeModifiers NBT, the item's own attributes, attributes added by other mods)",
            "     • ALL = Every attribute (default)",
            "     • MOD_ONLY = Only battlecorrection.* attributes",
            "     • OFF = None",
            "     Marked items always apply everything",
            "     Versions before this setting existed behaved like MOD_ONLY; switch to it to keep that",
            "",
            "[中文] 未标记饰品上原版格式的属性戴着时哪些生效",
            "     (AttributeModifiers NBT、物品自带的、其他模组追加的)",
            "     • ALL = 全部生效(默认)",
            "     • MOD_ONLY = 只有 battlecorrection.* 的属性生效",
            "     • OFF = 都不生效",
            "     标记过的物品始终全部生效",
            "     没有这个选项之前的版本相当于 MOD_ONLY，想保持老存档原来的效果就改成它"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".baubles.baubleNbtAttributes")
    public static BaubleNbtAttributes baubleNbtAttributes = BaubleNbtAttributes.ALL;

    private ConfigBaubles() {
    }
}
