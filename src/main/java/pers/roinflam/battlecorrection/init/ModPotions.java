package pers.roinflam.battlecorrection.init;

import net.minecraft.potion.Potion;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.battlecorrection.effect.BrawlEffect;
import pers.roinflam.battlecorrection.effect.EliminationEffect;
import pers.roinflam.battlecorrection.effect.RiotEffect;

import javax.annotation.Nonnull;

/**
 * 模组药水效果注册类
 * <p>
 * 注册名沿用旧版嵌套在权杖里的名字。效果必须进药水注册表，
 * 否则存档和同步时拿不到编号，效果存不下来。
 */
@Mod.EventBusSubscriber
public class ModPotions {

    /**
     * 暴动效果 - 使生物攻击随机目标
     */
    public static final RiotEffect RIOT = new RiotEffect(true, 0xFF0000, "riot_staff_MobEffectRiot");

    /**
     * 群殴效果 - 使生物与随机生物互相攻击
     */
    public static final BrawlEffect BRAWL = new BrawlEffect(true, 0xFF4500, "brawl_staff_MobEffectRiot");

    /**
     * 消灭效果 - 使生物攻击不同种类的目标
     */
    public static final EliminationEffect ELIMINATION = new EliminationEffect(true, 0x8B0000, "elimination_staff_MobEffectRiot");

    @SubscribeEvent
    public static void onPotionRegister(@Nonnull RegistryEvent.Register<Potion> evt) {
        evt.getRegistry().registerAll(RIOT, BRAWL, ELIMINATION);
    }
}
