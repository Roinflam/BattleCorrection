// 文件：AttributeCriticalHitDamage.java
// 路径：src/main/java/pers/roinflam/battlecorrection/attributes/AttributeCriticalHitDamage.java
package pers.roinflam.battlecorrection.attributes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 原版暴击伤害加成属性
 * 只影响原版的下坠暴击（从高处落下攻击）
 */
@Mod.EventBusSubscriber
public class AttributeCriticalHitDamage {
    public static final UUID ID = UUID.fromString("d4b4598c-3bf3-6c67-f0d1-419da33d23aa");
    public static final String NAME = "battlecorrection.vanillaCriticalHitDamage";

    // 原版暴击伤害加成，默认1.0表示100%额外伤害（即原版的150%伤害）
    public static final IAttribute VANILLA_CRITICAL_HIT_DAMAGE = (new RangedAttribute(null, NAME, 1, 1, Float.MAX_VALUE)).setDescription("Vanilla Critical Hit Damage");

    /**
     * 处理原版暴击事件
     * 原版暴击是指玩家从高处落下时的攻击
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            @Nullable EntityLivingBase attacker = evt.getEntityLiving();

            // 获取属性值并加上配置值
            double criticalDamageBonus = AttributesUtil.getAttributeValue(attacker, VANILLA_CRITICAL_HIT_DAMAGE) - 1;
            criticalDamageBonus += ConfigAttribute.vanillaCriticalHitDamage;

            // 设置暴击伤害倍率（原版默认是0.5，即150%伤害）
            evt.setDamageModifier((float) criticalDamageBonus);
        }
    }
}