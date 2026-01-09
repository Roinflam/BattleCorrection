// 文件：HiddenMobEffectBase.java（替代原HiddenPotionBase）
// 路径：src/main/java/pers/roinflam/battlecorrection/base/effect/HiddenMobEffectBase.java
package pers.roinflam.battlecorrection.base.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;

/**
 * 隐藏药水效果基类
 * 不在HUD和物品栏中显示的药水效果
 */
public abstract class HiddenMobEffectBase extends MobEffect {

    protected HiddenMobEffectBase(@Nonnull MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        // 子类重写
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    /**
     * 1.20.1中控制是否渲染效果图标
     * 返回false以隐藏
     */
    public boolean shouldRender(@Nonnull MobEffectInstance effect) {
        return false;
    }

    /**
     * 1.20.1中控制是否在物品栏显示效果文字
     * 返回false以隐藏
     */
    public boolean shouldRenderInvText(@Nonnull MobEffectInstance effect) {
        return false;
    }

    /**
     * 1.20.1中控制是否在HUD显示效果
     * 返回false以隐藏
     */
    public boolean shouldRenderHUD(@Nonnull MobEffectInstance effect) {
        return false;
    }
}