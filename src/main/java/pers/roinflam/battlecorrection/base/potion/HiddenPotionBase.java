package pers.roinflam.battlecorrection.base.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.battlecorrection.utils.util.PotionUtil;

import javax.annotation.Nonnull;

public abstract class HiddenPotionBase extends Potion {

    protected HiddenPotionBase(boolean isBadEffectIn, int liquidColorIn, @Nonnull String name) {
        super(isBadEffectIn, liquidColorIn);
        PotionUtil.registerPotion(this, name);
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {

    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean shouldRender(@Nonnull PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderInvText(@Nonnull PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderHUD(@Nonnull PotionEffect effect) {
        return false;
    }

}
