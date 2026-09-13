package pers.roinflam.battlecorrection.compat;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * 饰品栏校验器 {@code battlecorrection:mark}：物品带对应标记就算"能放进这个饰品栏"
 * <p>
 * Curios 判断"物品能不能进某个饰品栏"有两条路：
 * <ol>
 *   <li>手动拖放：DynamicStackHandler#isItemValid，先发 CurioEquipEvent，我们在 {@link CurioMarkEvents#onCurioEquip} 里放行。</li>
 *   <li>Shift 快速放、提示框里的"栏位:"、以及 isItemValid 没被事件放行时的默认判定：CuriosApi.getItemStackSlots，
 *   只按每种饰品栏配置的校验器（validators）算，默认只有 curios:tag（看物品标签）。NBT 上的标记它根本不看。</li>
 * </ol>
 * 这里往 Curios 注册一个校验器，再用 data/battlecorrection/curios/slots/*.json 给它自带的 10 种饰品栏都挂上
 * （校验器是并集，不影响原来的 curios:tag）。这样标记物品在第二条路上也是合法的：Shift 能放、提示框由 Curios 自己显示栏位、
 * 就算哪次事件没放行也还有默认判定兜底。其他模组自己加的饰品栏 ID 没法预先挂，仍然只靠事件那条路。
 * <p>
 * 这个类引用了 Curios 的类，只能在 {@link CuriosIntegration#isCuriosLoaded()} 为 true 时调用。
 */
public final class CurioMarkValidator {

    /**
     * 校验器 ID，slots/*.json 里的 validators 就写这个
     */
    public static final ResourceLocation ID = new ResourceLocation(Reference.MOD_ID, "mark");

    private CurioMarkValidator() {
    }

    /**
     * 注册校验器（Curios 用 putIfAbsent，重复注册无害；必须在数据包加载前，通用初始化阶段调）
     */
    public static void register() {
        CuriosApi.registerCurioPredicate(ID, CurioMarkValidator::test);
        LogUtil.info("已向 Curios 注册饰品栏校验器 " + ID);
    }

    /**
     * 判定
     *
     * @param result 饰品栏上下文 + 物品
     * @return true = 物品标记里有这个饰品栏（或 any）
     */
    private static boolean test(SlotResult result) {
        if (!ConfigAttribute.CURIO_MARK_ENABLED.get()) {
            return false;
        }
        return CurioMark.canEquip(result.stack(), result.slotContext().identifier());
    }
}
