package pers.roinflam.battlecorrection.compat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.battlecorrection.handlers.CurioMarkTooltipHandler;
import pers.roinflam.battlecorrection.utils.LogUtil;

/**
 * Curios模组集成类（替代1.12.2的Baubles）
 * <p>
 * 只做两件事：检测 Curios 是否存在，存在时注册 {@link CurioMarkValidator}（饰品栏校验器）、
 * {@link CurioMarkEvents}（两端事件）和 {@link CurioMarkTooltipHandler}（客户端）。这几个类引用了 Curios 的类，
 * 不能用 @Mod.EventBusSubscriber 自动注册，否则没装 Curios 时启动就会找不到类而崩溃。
 * <p>
 * 饰品上的属性怎么生效，全在 {@link CurioMarkEvents#onCurioAttributeModifiers} 里：
 * 不论是 Curios 自己的属性、命令追加的附加属性、还是物品上原版格式的 AttributeModifiers，
 * 都在饰品戴上时作为真正的实体属性修饰符加到身上，摘下时由 Curios 移除。
 * 之前这里还有一套"每隔几 tick 扫一遍饰品栏，把原版格式的属性现算出来"的逻辑，
 * 饰品一多，拉弓、吃东西、举盾时每半秒就要解析几百次 NBT 加发几百次事件，客户端和服务端都会周期性卡一下，
 * 已经整个删掉。
 */
public class CuriosIntegration {

    /**
     * Curios 是否已加载
     */
    private static boolean curiosLoaded = false;

    /**
     * Curios 是否已加载
     *
     * @return true = 已加载
     */
    public static boolean isCuriosLoaded() {
        return curiosLoaded;
    }

    /**
     * 初始化：检测 Curios 并注册相关事件（主类构造时调用）
     */
    public static void init() {
        curiosLoaded = ModList.get().isLoaded("curios");
        if (curiosLoaded) {
            // 只有装了 Curios 才注册，这几个类都引用了 Curios 的类
            CurioMarkValidator.register();
            MinecraftForge.EVENT_BUS.register(CurioMarkEvents.class);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                MinecraftForge.EVENT_BUS.register(CurioMarkTooltipHandler.class);
            }
            LogUtil.info("成功检测到Curios模组，已启用饰品栏属性支持和饰品栏标记功能");
        } else {
            LogUtil.info("未检测到Curios模组，饰品栏功能将被禁用");
        }
    }
}
