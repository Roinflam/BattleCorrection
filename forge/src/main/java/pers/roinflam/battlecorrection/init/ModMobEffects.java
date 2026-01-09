// 文件：ModMobEffects.java
// 路径：src/main/java/pers/roinflam/battlecorrection/init/ModMobEffects.java
package pers.roinflam.battlecorrection.init;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.battlecorrection.effect.BrawlEffect;
import pers.roinflam.battlecorrection.effect.EliminationEffect;
import pers.roinflam.battlecorrection.effect.RiotEffect;
import pers.roinflam.battlecorrection.utils.Reference;

/**
 * 模组药水效果注册类
 */
public class ModMobEffects {

    /**
     * 药水效果延迟注册器
     */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Reference.MOD_ID);

    /**
     * 暴动效果 - 使生物攻击随机目标
     */
    public static final RegistryObject<MobEffect> RIOT_EFFECT = MOB_EFFECTS.register(
            "riot_effect",
            () -> new RiotEffect(MobEffectCategory.HARMFUL, 0xFF0000)
    );

    /**
     * 群殴效果 - 使生物攻击随机目标
     */
    public static final RegistryObject<MobEffect> BRAWL_EFFECT = MOB_EFFECTS.register(
            "brawl_effect",
            () -> new BrawlEffect(MobEffectCategory.HARMFUL, 0xFF4500)
    );

    /**
     * 消灭效果 - 使生物攻击不同种类的目标
     */
    public static final RegistryObject<MobEffect> ELIMINATION_EFFECT = MOB_EFFECTS.register(
            "elimination_effect",
            () -> new EliminationEffect(MobEffectCategory.HARMFUL, 0x8B0000)
    );
}