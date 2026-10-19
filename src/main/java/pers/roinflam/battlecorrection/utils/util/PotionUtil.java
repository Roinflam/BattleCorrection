package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.potion.Potion;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

public class PotionUtil {

    @Nonnull
    public static Potion registerPotion(@Nonnull Potion potion, @Nonnull String name) {
        potion.setPotionName("effect." + name);
        // 显式带上模组 ID：药水常量可能在任意时机被类加载初始化，不能依赖当时的活动模组容器
        potion.setRegistryName(Reference.MOD_ID, name);
        return potion;
    }

}
