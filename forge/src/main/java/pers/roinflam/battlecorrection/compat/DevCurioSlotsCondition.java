package pers.roinflam.battlecorrection.compat;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 数据包条件 {@code battlecorrection:dev_curio_slots}：配置 devCurioSlots 打开才成立
 * <p>
 * 模组 jar 里带了一份 Curios 的玩家饰品栏分配文件（data/battlecorrection/curios/entities/dev_player.json），
 * 把 Curios 自带的 10 种默认饰品栏全部给玩家，用来在开发环境测试。文件头上挂了这个条件，
 * 配置默认关着，正式服上这份文件等于不存在，不会和别的饰品模组的分配叠加。
 * <p>
 * 为什么要这么做：Curios 只给"至少被分配了一种饰品栏"的实体装饰品栏能力，一种都没有的玩家连能力都没有，
 * 命令和编辑器都改不了他的饰品栏。开发环境只装 Curios 时玩家就是这种状态，得先靠数据包给一种。
 * <p>
 * 这个类只用 Forge 的东西，不引用 Curios，没装 Curios 也能加载。
 */
public final class DevCurioSlotsCondition implements ICondition {

    /**
     * 条件 ID
     */
    public static final ResourceLocation ID = new ResourceLocation(Reference.MOD_ID, "dev_curio_slots");

    /**
     * 唯一实例（条件没有参数）
     */
    public static final DevCurioSlotsCondition INSTANCE = new DevCurioSlotsCondition();

    private static final IConditionSerializer<DevCurioSlotsCondition> SERIALIZER = new Serializer();

    private DevCurioSlotsCondition() {
    }

    /**
     * 注册序列化器（通用初始化阶段调用一次）
     */
    public static void register() {
        CraftingHelper.register(SERIALIZER);
        LogUtil.info("已注册数据包条件 " + ID + "（devCurioSlots 当前 " + ConfigAttribute.DEV_CURIO_SLOTS.get() + "）");
    }

    @Nonnull
    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(@Nonnull IContext context) {
        return ConfigAttribute.DEV_CURIO_SLOTS.get();
    }

    /**
     * 序列化器：条件没有参数，读写都是空的
     */
    private static final class Serializer implements IConditionSerializer<DevCurioSlotsCondition> {

        @Override
        public void write(@Nonnull JsonObject json, @Nonnull DevCurioSlotsCondition value) {
        }

        @Nonnull
        @Override
        public DevCurioSlotsCondition read(@Nonnull JsonObject json) {
            return INSTANCE;
        }

        @Nonnull
        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
