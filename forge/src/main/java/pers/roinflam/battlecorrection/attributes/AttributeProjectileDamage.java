package pers.roinflam.battlecorrection.attributes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.init.ModAttributes;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 弹射物伤害加成属性
 * 增加除箭矢外的弹射物攻击造成的伤害，如枪械模组中的子弹
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class AttributeProjectileDamage {
    public static final UUID ID = UUID.fromString("ab4edc53-93b4-c83c-c498-0c0be85d458e");
    public static final String NAME = "battlecorrection.projectileDamage";

    /**
     * 处理弹射物伤害事件
     * 当实体被弹射物（非箭矢、非魔法）击中时触发
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getDirectEntity() instanceof Projectile &&
                    !(damageSource.getDirectEntity() instanceof AbstractArrow) &&
                    !damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO) &&
                    damageSource.getEntity() instanceof @Nullable LivingEntity attacker) {

                @Nullable LivingEntity victim = evt.getEntity();

                float originalAmount = evt.getAmount();
                float attributeValue = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.PROJECTILE_DAMAGE.get());
                float configDamage = ConfigAttribute.PROJECTILE_DAMAGE.get().floatValue();
                float newAmount = (float) AttributesUtil.getAttributeValue(attacker, ModAttributes.PROJECTILE_DAMAGE.get(), originalAmount + configDamage);

                String projectileType = damageSource.getDirectEntity().getClass().getSimpleName();
                LogUtil.debugAttribute("弹射物伤害", attacker.getName().getString(), attributeValue, configDamage, newAmount - originalAmount);
                LogUtil.debugDamage("弹射物攻击", attacker.getName().getString(), victim.getName().getString(), originalAmount, newAmount,
                        String.format("弹射物类型: %s, 属性加成: %.2f, 配置加成: %.2f", projectileType, attributeValue, configDamage));

                evt.setAmount(newAmount);
            }
        }
    }
}