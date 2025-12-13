package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 魔法伤害加成属性
 * 增加魔法攻击造成的伤害，如药水伤害或模组中的法杖
 */
@Mod.EventBusSubscriber
public class AttributeMagicDamage {
    public static final UUID ID = UUID.fromString("af42b33d-f767-b71d-9ca4-92fc0ce0f04a");
    public static final String NAME = "battlecorrection.magicDamage";

    public static final IAttribute MAGIC_DAMAGE = (new RangedAttribute(null, NAME, 0, 0, Float.MAX_VALUE)).setDescription("Extra Magic Damage");

    /**
     * 处理魔法伤害事件
     * 当实体受到魔法伤害时触发
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.isMagicDamage() && damageSource.getTrueSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                @Nullable EntityLivingBase victim = evt.getEntityLiving();

                float originalAmount = evt.getAmount();
                float attributeValue = AttributesUtil.getAttributeValue(attacker, MAGIC_DAMAGE);
                float configDamage = ConfigAttribute.magicDamage;
                float newAmount = AttributesUtil.getAttributeValue(attacker, MAGIC_DAMAGE, originalAmount + configDamage);

                LogUtil.debugAttribute("魔法伤害", attacker.getName(), attributeValue, configDamage, newAmount - originalAmount);
                LogUtil.debugDamage("魔法攻击", attacker.getName(), victim.getName(), originalAmount, newAmount,
                        String.format("属性加成: %.2f, 配置加成: %.2f", attributeValue, configDamage));

                evt.setAmount(newAmount);
            }
        }
    }
}