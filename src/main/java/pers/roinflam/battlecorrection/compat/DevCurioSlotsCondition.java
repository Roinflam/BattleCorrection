package pers.roinflam.battlecorrection.compat;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 数据包条件 {@code battlecorrection:dev_curio_slots}：配置 devCurioSlots 打开才成立
 * <p>
 * 模组 jar 里带了一份 Curios 的玩家饰品栏分配文件（data/battlecorrection/curios/entities/dev_player.json），
 * 把 Curios 自带的 10 种默认饰品栏全部给玩家，用来在开发环境测试。文件头上挂了这个条件，
 * 配置默认关着，正常游戏时这份文件等于不存在，不会和别的饰品模组的分配叠加。
 * <p>
 * 为什么要这么做：Curios 只给"至少被分配了一种饰品栏"的实体装饰品栏能力，一种都没有的玩家连能力都没有，
 * 命令和编辑器都改不了他的饰品栏。开发环境只装 Curios 时玩家就是这种状态，得先靠数据包给一种。
 * <p>
 * 1.21.1：条件写在 JSON 的 {@code neoforge:conditions} 键下（Curios 9 读文件时调 {@code ICondition.conditionsMatched}），
 * 条件类型按编解码器注册（{@code NeoForgeRegistries.Keys.CONDITION_CODECS}），不再有 1.20.1 的序列化器。
 * <p>
 * 这个类只用 NeoForge 的东西，不引用 Curios，没装 Curios 也能加载。
 */
public final class DevCurioSlotsCondition implements ICondition {

    /**
     * 条件 ID
     */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "dev_curio_slots");

    /**
     * 唯一实例（条件没有参数）
     */
    public static final DevCurioSlotsCondition INSTANCE = new DevCurioSlotsCondition();

    /**
     * 编解码器：条件没有参数，读写都是这个唯一实例
     */
    public static final MapCodec<DevCurioSlotsCondition> CODEC = MapCodec.unit(INSTANCE);

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Reference.MOD_ID);

    static {
        CONDITION_CODECS.register(ID.getPath(), () -> CODEC);
    }

    private DevCurioSlotsCondition() {
    }

    /**
     * 把条件类型登记到模组总线（主类构造器里调用一次）
     */
    public static void register(@Nonnull IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }

    @Override
    public boolean test(@Nonnull IContext context) {
        return ConfigAttribute.DEV_CURIO_SLOTS.get();
    }

    @Nonnull
    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
