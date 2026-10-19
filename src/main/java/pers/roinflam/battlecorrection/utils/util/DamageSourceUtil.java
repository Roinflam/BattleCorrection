package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLlamaSpit;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.util.DamageSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 伤害源判定工具
 * <p>
 * 1.12.2 没有伤害类型 tag，「算不算弹射物 / 火焰 / 环境伤害」这些判断集中写在这里，
 * 各个属性和监听器共用同一套口径。
 */
public final class DamageSourceUtil {

    private DamageSourceUtil() {
    }

    /**
     * 实体是否算弹射物
     * <p>
     * 1.12.2 的弹射物没有共同父类：箭、雪球、药水、羊驼口水实现了 {@link IProjectile}，
     * 火球（含凋灵之首、末影龙火球）和潜影弹却没有，这里一并算上，与 1.20.1 的 Projectile 范围一致。
     *
     * @param entity 伤害源的直接实体
     * @return true = 弹射物
     */
    public static boolean isProjectile(@Nullable Entity entity) {
        return entity instanceof IProjectile
                || entity instanceof EntityFireball
                || entity instanceof EntityShulkerBullet
                || entity instanceof EntityLlamaSpit;
    }

    /**
     * 是否无视无敌的伤害（虚空、/kill）
     *
     * @param source 伤害源
     * @return true = 不应被本模组改动
     */
    public static boolean bypassesInvulnerability(@Nonnull DamageSource source) {
        return source.canHarmInCreative();
    }

    /**
     * 是否环境伤害：伤害背后没有活物攻击者，也不是发射器之类打出来的弹射物。
     * 摔落、火焰、岩浆、溺水、窒息、中毒/凋零效果、无主 TNT、坠落方块、闪电都算。
     *
     * @param source 伤害源
     * @return true = 环境伤害
     */
    public static boolean isEnvironmental(@Nonnull DamageSource source) {
        return !(source.getTrueSource() instanceof EntityLivingBase) && !isProjectile(source.getImmediateSource());
    }

    /**
     * 是否摔落伤害（1.12.2 只有 fall 一种）
     */
    public static boolean isFall(@Nonnull DamageSource source) {
        return source == DamageSource.FALL || "fall".equals(source.getDamageType());
    }

    /**
     * 是否溺水伤害
     */
    public static boolean isDrowning(@Nonnull DamageSource source) {
        return source == DamageSource.DROWN || "drown".equals(source.getDamageType());
    }
}
