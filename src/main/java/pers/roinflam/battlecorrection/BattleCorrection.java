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
import pers.roinflam.battlecorrection.attributes.*;
import pers.roinflam.battlecorrection.proxy.CommonProxy;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * BattleCorrection 模组主类
 * 提供RPG风格的战斗系统增强
 */
@Mod.EventBusSubscriber
@Mod(modid = Reference.MOD_ID, useMetadata = true, guiFactory = "pers.roinflam.battlecorrection.gui.ConfigGuiFactory", dependencies = "before:infinityeditor")
public class BattleCorrection {

    @SideOnly(Side.CLIENT)
    public static KeyBinding keyOpenModList;

    @Mod.Instance
    public static BattleCorrection instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    public static final Logger LOGGER = Logger.getLogger("BattleCorrection");

    /**
     * 预初始化阶段
     */
    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        proxy.bindKey();
        LOGGER.info("BattleCorrection 预初始化完成");
    }

    /**
     * 初始化阶段
     */
    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        LOGGER.info("BattleCorrection 初始化完成");
    }

    /**
     * 后初始化阶段
     */
    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        LOGGER.info("BattleCorrection 后初始化完成");
    }

    /**
     * 当实体进入世界时，为其注册所有自定义属性
     * 适用于所有EntityLivingBase（包括玩家和生物）
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityLivingBase) {
            EntityLivingBase entity = (EntityLivingBase) event.getEntity();
            registerCustomAttributes(entity.getAttributeMap());

            if (!event.getWorld().isRemote) {
                LOGGER.info("为实体注册属性: " + entity.getName() + " [" + entity.getClass().getSimpleName() + "]");
            }
        }
    }

    /**
     * 向实体的属性映射中注册所有自定义属性
     *
     * @param attributeMap 实体的属性映射
     */
    public static void registerCustomAttributes(AbstractAttributeMap attributeMap) {
        // 伤害类属性
        ensureAttributeExists(attributeMap, AttributeArrowDamage.ARROW_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeMagicDamage.MAGIC_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeProjectileDamage.PROJECTILE_DAMAGE);

        // 防御类属性
        ensureAttributeExists(attributeMap, AttributeImmuneDamage.IMMUNE_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeIgnoreDamage.IGNORE_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeReducedFallDamage.REDUCED_FALL_DAMAGE);

        // 恢复类属性
        ensureAttributeExists(attributeMap, AttributeRestoreHeal.RESTORE_HEAL);
        ensureAttributeExists(attributeMap, AttributeBloodthirsty.BLOODTHIRSTY);
        ensureAttributeExists(attributeMap, AttributeAlmightyBloodthirsty.ALMIGHTY_BLOODTHIRSTY);

        // 速度类属性
        ensureAttributeExists(attributeMap, AttributeBowSpeed.BOW_SPEED);
        ensureAttributeExists(attributeMap, AttributePreparationSpeed.PREPARATION_SPEED);

        // 移动类属性
        ensureAttributeExists(attributeMap, AttributeJumpLift.JUMP_LIFT);

        // 暴击类属性
        ensureAttributeExists(attributeMap, AttributeCriticalHitDamage.VANILLA_CRITICAL_HIT_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeCustomCriticalChance.CUSTOM_CRITICAL_CHANCE);
        ensureAttributeExists(attributeMap, AttributeCustomCriticalDamage.CUSTOM_CRITICAL_DAMAGE);
    }

    /**
     * 确保属性存在于实体的属性映射中
     * 如果属性不存在，则注册该属性
     *
     * @param attributeMap 实体的属性映射
     * @param attribute    要检查和注册的属性
     */
    private static void ensureAttributeExists(AbstractAttributeMap attributeMap, net.minecraft.entity.ai.attributes.IAttribute attribute) {
        if (attributeMap.getAttributeInstance(attribute) == null) {
            attributeMap.registerAttribute(attribute);
            LOGGER.info("注册属性: " + attribute.getName());
        }
    }
}