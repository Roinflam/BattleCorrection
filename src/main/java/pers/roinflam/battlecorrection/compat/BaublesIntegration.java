package pers.roinflam.battlecorrection.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.handlers.BaubleMarkTooltipHandler;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Baubles模组集成类
 * <p>
 * 只做三件事：检测 Baubles 是否存在；存在时注册 {@link BaubleMarkEvents}（两端事件）和
 * {@link BaubleMarkTooltipHandler}（客户端）；给 AttributesUtil 提供饰品栏里的属性。
 * 引用 Baubles 的类都在 {@link BaublesAccess}、{@link BaubleMarkCapability} 里，这个类本身不碰，
 * 所以没装 Baubles 时照样能加载，只是什么都不做。
 * <p>
 * 饰品上的属性怎么生效，见 {@link BaubleWornModifiers}：
 * 本模组的附加属性和物品自身的原版格式属性，戴上时作为真正的修饰符加到玩家身上，摘下时撤掉；
 * AttributesUtil 算本模组属性时读同一份缓存。
 * <p>
 * 注意：Baubles饰品栏仅支持玩家（EntityPlayer）
 */
public class BaublesIntegration {

    private static boolean baublesLoaded = false;

    /**
     * 检查Baubles是否已加载
     *
     * @return true = 已加载且能力可用
     */
    public static boolean isBaublesLoaded() {
        return baublesLoaded;
    }

    /**
     * 初始化Baubles集成（主类预初始化时调用，必须在 Baubles 自己的预初始化之后）
     * <p>
     * 事件要在物品注册之前挂上：物品能力是物品创建时挂的，预初始化之后才开始注册物品、建配方，
     * 这时注册能保证游戏里的物品都带着标记饰品的能力。
     */
    public static void init() {
        // 用模组列表判断，而不是 Class.forName：有的模组把 Baubles API 打进了自己的 jar，类有、模组没有
        baublesLoaded = Loader.isModLoaded("baubles") && BaublesAccess.capabilitiesReady();
        if (baublesLoaded) {
            // 只有装了 Baubles 才注册，这几个类会用到 Baubles 的类
            MinecraftForge.EVENT_BUS.register(BaubleMarkEvents.class);
            if (FMLCommonHandler.instance().getSide().isClient()) {
                MinecraftForge.EVENT_BUS.register(BaubleMarkTooltipHandler.class);
            }
            LogUtil.info("成功检测到Baubles模组，已启用饰品栏属性支持和饰品栏标记功能");
        } else if (Loader.isModLoaded("baubles")) {
            LogUtil.warn("检测到Baubles模组，但它的能力没有注册（加载顺序不对？），饰品栏功能将被禁用");
        } else {
            LogUtil.info("未检测到Baubles模组，饰品栏功能将被禁用");
        }
    }

    /**
     * 获取实体的所有饰品栏物品
     *
     * @param entity 实体（必须是玩家）
     * @return 饰品栏物品列表（不含空格子），如果不是玩家则返回空列表
     */
    @Nonnull
    public static List<ItemStack> getBaubles(@Nonnull EntityLivingBase entity) {
        List<ItemStack> baubles = new ArrayList<>();
        if (!baublesLoaded || !(entity instanceof EntityPlayer)) {
            return baubles;
        }

        IItemHandler handler = BaublesAccess.getHandler((EntityPlayer) entity);
        if (handler == null) {
            return baubles;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                baubles.add(stack);
            }
        }
        return baubles;
    }

    /**
     * 从饰品栏收集指定属性的修改器（给 AttributesUtil 算本模组的属性）
     * <p>
     * 包括：每件饰品自身的属性（6 个装备槽都问一遍，同一件物品里按"属性 + UUID"去重），
     * 以及本模组的附加属性里在它所在那一格生效的条目。不同格子里的物品各算各的，
     * 两件一样的戒指戴在两个指环格就算两份（不能按数值去重，否则第二件会被吞掉）。
     * 未标记饰品自身的属性按配置 baubleNbtAttributes 筛选。
     *
     * @param entity    实体（只有玩家有饰品栏）
     * @param attribute 属性
     * @return 属性修改器列表数组 [加法, 乘法1, 乘法2]，每次都是新建的列表
     */
    @Nonnull
    public static List<Double>[] collectModifiers(@Nonnull EntityLivingBase entity, @Nonnull IAttribute attribute) {
        return collectModifiers(entity, attribute.getName());
    }

    /**
     * 按属性名收集，见 {@link #collectModifiers(EntityLivingBase, IAttribute)}
     *
     * @param entity        实体
     * @param attributeName 属性名
     * @return [加法, 乘法1, 乘法2]
     */
    @Nonnull
    public static List<Double>[] collectModifiers(@Nonnull EntityLivingBase entity, @Nonnull String attributeName) {
        if (!baublesLoaded || !(entity instanceof EntityPlayer)) {
            return BaubleWornModifiers.emptyResult();
        }
        EntityPlayer player = (EntityPlayer) entity;
        IItemHandler handler = BaublesAccess.getHandler(player);
        if (handler == null) {
            return BaubleWornModifiers.emptyResult();
        }

        List<Double>[] result = BaubleWornModifiers.collect(player, handler, attributeName);
        if (ConfigBattle.enableDetailedLogging) {
            int total = result[0].size() + result[1].size() + result[2].size();
            if (total > 0) {
                LogUtil.debug(String.format("玩家 %s 从饰品栏收集到属性 %s: 加法=%d个, 乘法1=%d个, 乘法2=%d个",
                        player.getName(), attributeName, result[0].size(), result[1].size(), result[2].size()));
            }
        }
        return result;
    }

    /**
     * 旧名字，保留给还没改过来的调用方
     *
     * @param entity        实体
     * @param attributeName 属性名
     * @return [加法, 乘法1, 乘法2]
     * @deprecated 改用 {@link #collectModifiers(EntityLivingBase, IAttribute)}
     */
    @Deprecated
    @Nonnull
    public static List<Double>[] collectModifiersFromBaubles(@Nonnull EntityLivingBase entity,
                                                             @Nonnull String attributeName) {
        return collectModifiers(entity, attributeName);
    }
}
