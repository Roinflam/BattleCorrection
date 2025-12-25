// 文件：BattleCorrection.java
// 路径：src/main/java/pers/roinflam/battlecorrection/BattleCorrection.java
package pers.roinflam.battlecorrection;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.proxy.CommonProxy;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * BattleCorrection 战斗修正模组 - 主类
 * <p>
 * 提供RPG风格的战斗系统增强，包括：
 * - 自定义属性系统（魔法伤害、暴击、吸血等）
 * - 超额暴击机制（支持多层暴击）
 * - 全局伤害倍率调整
 * - PVP平衡系统
 * - 连击修正机制
 * - 饥饿影响伤害系统
 * - Baubles饰品栏联动支持
 *
 * @author Roinflam
 * @version 1.1.0
 * @since 1.0.0
 * <p>
 * 特别鸣谢：
 * - 建议重开：为本项目提供的支持和帮助
 */
@Mod.EventBusSubscriber
@Mod(
        modid = Reference.MOD_ID,
        useMetadata = true,
        guiFactory = "pers.roinflam.battlecorrection.gui.ConfigGuiFactory",
        dependencies = "after:baubles;before:infinityeditor"
)
public class BattleCorrection {

    /**
     * 客户端按键绑定 - 打开模组列表
     */
    @SideOnly(Side.CLIENT)
    public static KeyBinding keyOpenModList;

    /**
     * 模组实例
     */
    @Mod.Instance(Reference.MOD_ID)
    public static BattleCorrection instance;

    /**
     * 代理实例（客户端/服务端）
     */
    @SidedProxy(
            clientSide = Reference.CLIENT_PROXY_CLASS,
            serverSide = Reference.COMMON_PROXY_CLASS
    )
    public static CommonProxy proxy;

    /**
     * 预初始化阶段
     * 执行模组的早期设置，包括按键绑定和Baubles集成初始化
     *
     * @param evt 预初始化事件
     */
    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        LogUtil.info("═══════════════════════════════════════");
        LogUtil.info("  战斗修正模组 (Battle Correction)");
        LogUtil.info("  版本: 1.1.0");
        LogUtil.info("  作者: Roinflam");
        LogUtil.info("  特别鸣谢: 建议重开");
        LogUtil.info("═══════════════════════════════════════");

        // 绑定快捷键
        proxy.bindKey();
        LogUtil.info("快捷键绑定完成");

        // ★ 重要：在最早期注册全局属性
        ModAttributes.init();

        // 初始化Baubles集成（可选依赖）
        BaublesIntegration.init();

        LogUtil.info("战斗修正模组 - 预初始化完成");
    }

    /**
     * 初始化阶段
     * 执行模组的主要初始化工作
     *
     * @param evt 初始化事件
     */
    @Mod.EventHandler
    public static void init(@Nonnull FMLInitializationEvent evt) {
        LogUtil.info("战斗修正模组 - 初始化完成");
    }

    /**
     * 后初始化阶段
     * 执行模组加载的最后阶段工作
     *
     * @param evt 后初始化事件
     */
    @Mod.EventHandler
    public static void postInit(@Nonnull FMLPostInitializationEvent evt) {
        LogUtil.info("战斗修正模组 - 后初始化完成");
        LogUtil.info("所有战斗系统增强已激活");
        LogUtil.info("═══════════════════════════════════════");
    }

    /**
     * 实体加入世界事件处理
     * 当实体进入世界时，为其注册所有自定义属性
     * 适用于所有EntityLivingBase（包括玩家和生物）
     *
     * @param event 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityLivingBase) {
            EntityLivingBase entity = (EntityLivingBase) event.getEntity();
            registerCustomAttributes(entity.getAttributeMap());

            if (!event.getWorld().isRemote) {
                LogUtil.debug(String.format("为实体注册自定义属性: %s [%s]",
                        entity.getName(),
                        entity.getClass().getSimpleName()));
            }
        }
    }

    /**
     * 向实体的属性映射中注册所有自定义属性
     * <p>
     * 注册的属性包括：
     * - 伤害类：魔法伤害、箭矢伤害、弹射物伤害
     * - 防御类：免疫伤害、忽略伤害、减少摔落伤害
     * - 恢复类：治疗恢复、近战吸血、全能吸血
     * - 速度类：拉弓速度、使用速度
     * - 特殊类：跳跃提升、原版暴击、自定义暴击率、自定义暴击伤害
     *
     * @param attributeMap 实体的属性映射
     */
    public static void registerCustomAttributes(@Nonnull AbstractAttributeMap attributeMap) {
        // ===== 伤害增强属性 =====
        ensureAttributeExists(attributeMap, ModAttributes.MAGIC_DAMAGE);
        ensureAttributeExists(attributeMap, ModAttributes.ARROW_DAMAGE);
        ensureAttributeExists(attributeMap, ModAttributes.PROJECTILE_DAMAGE);

        // ===== 防御与减伤属性 =====
        ensureAttributeExists(attributeMap, ModAttributes.IMMUNE_DAMAGE);
        ensureAttributeExists(attributeMap, ModAttributes.IGNORE_DAMAGE);
        ensureAttributeExists(attributeMap, ModAttributes.REDUCED_FALL_DAMAGE);

        // ===== 生命恢复属性 =====
        ensureAttributeExists(attributeMap, ModAttributes.RESTORE_HEAL);
        ensureAttributeExists(attributeMap, ModAttributes.BLOODTHIRSTY);
        ensureAttributeExists(attributeMap, ModAttributes.ALMIGHTY_BLOODTHIRSTY);

        // ===== 速度与机动性属性 =====
        ensureAttributeExists(attributeMap, ModAttributes.BOW_SPEED);
        ensureAttributeExists(attributeMap, ModAttributes.PREPARATION_SPEED);
        ensureAttributeExists(attributeMap, ModAttributes.JUMP_LIFT);

        // ===== 暴击系统属性 =====
        ensureAttributeExists(attributeMap, ModAttributes.VANILLA_CRITICAL_HIT_DAMAGE);
        ensureAttributeExists(attributeMap, ModAttributes.CUSTOM_CRITICAL_CHANCE);
        ensureAttributeExists(attributeMap, ModAttributes.CUSTOM_CRITICAL_DAMAGE);

        LogUtil.debug("已为实体注册 15 个自定义属性");
    }

    /**
     * 确保属性存在于实体的属性映射中
     * 如果属性不存在，则注册该属性
     *
     * @param attributeMap 实体的属性映射
     * @param attribute    要检查和注册的属性
     */
    private static void ensureAttributeExists(
            @Nonnull AbstractAttributeMap attributeMap,
            @Nonnull net.minecraft.entity.ai.attributes.IAttribute attribute) {

        if (attributeMap.getAttributeInstance(attribute) == null) {
            attributeMap.registerAttribute(attribute);
            LogUtil.debug(String.format("  ✓ 注册属性: %s (默认值: %.2f)",
                    attribute.getName(),
                    attribute.getDefaultValue()));
        }
    }
}