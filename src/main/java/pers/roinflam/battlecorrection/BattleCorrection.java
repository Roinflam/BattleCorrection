package pers.roinflam.battlecorrection;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.player.EntityPlayer;
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

@Mod.EventBusSubscriber
@Mod(modid = Reference.MOD_ID, useMetadata = true, guiFactory = "pers.roinflam.battlecorrection.gui.ConfigGuiFactory", dependencies = "before:infinityeditor")
public class BattleCorrection {

    @SideOnly(Side.CLIENT)
    public static KeyBinding keyOpenModList;

    @Mod.Instance
    public static BattleCorrection instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        proxy.bindKey();
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
    }

    /**
     * 当实体（特别是玩家）进入世界时，确保它们获得所有自定义属性
     * 这使得SetBonus模组能够识别和修改这些属性
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            registerCustomAttributes(player.getAttributeMap());
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
        }
    }
}