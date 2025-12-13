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

@Mod.EventBusSubscriber
@Mod(modid = Reference.MOD_ID, useMetadata = true, guiFactory = "pers.roinflam.battlecorrection.gui.ConfigGuiFactory", dependencies = "before:infinityeditor")
public class BattleCorrection {

    @SideOnly(Side.CLIENT)
    public static KeyBinding keyOpenModList;

    @Mod.Instance
    public static BattleCorrection instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    // 添加一个日志记录器
    public static final Logger LOGGER = Logger.getLogger("BattleCorrection");

    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        proxy.bindKey();
        LOGGER.info("BattleCorrection 预初始化完成");
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        LOGGER.info("BattleCorrection 初始化完成");
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        LOGGER.info("BattleCorrection 后初始化完成");
    }

    /**
     * 当实体（不仅是玩家，所有生物实体）进入世界时，确保它们获得所有自定义属性
     * 修改：现在为所有EntityLivingBase注册属性，不仅仅是玩家
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
     * 向实体的属性映射中添加自定义属性
     */
    public static void registerCustomAttributes(AbstractAttributeMap attributeMap) {
        // 确保所有自定义属性都被添加到实体的属性映射中
        ensureAttributeExists(attributeMap, AttributeArrowDamage.ARROW_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeMagicDamage.MAGIC_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeProjectileDamage.PROJECTILE_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeRestoreHeal.RESTORE_HEAL);
        ensureAttributeExists(attributeMap, AttributeImmuneDamage.IMMUNE_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeBowSpeed.BOW_SPEED);
        ensureAttributeExists(attributeMap, AttributePreparationSpeed.PREPARATION_SPEED);
        ensureAttributeExists(attributeMap, AttributeBloodthirsty.BLOODTHIRSTY);
        ensureAttributeExists(attributeMap, AttributeAlmightyBloodthirsty.ALMIGHTY_BLOODTHIRSTY);
        ensureAttributeExists(attributeMap, AttributeIgnoreDamage.IGNORE_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeCriticalHitDamage.CRITICAL_HIT_DAMAGE);
        ensureAttributeExists(attributeMap, AttributeJumpLift.JUMP_LIFT);
        ensureAttributeExists(attributeMap, AttributeReducedFallDamage.REDUCED_FALL_DAMAGE);
    }

    /**
     * 确保属性存在于实体的属性映射中
     */
    private static void ensureAttributeExists(AbstractAttributeMap attributeMap, net.minecraft.entity.ai.attributes.IAttribute attribute) {
        if (attributeMap.getAttributeInstance(attribute) == null) {
            attributeMap.registerAttribute(attribute);
            LOGGER.info("注册属性: " + attribute.getName());
        }
    }
}