package pers.roinflam.battlecorrection.utils.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

/**
 * 属性修饰符的 ID
 * <p>
 * 1.21.1 的属性修饰符不再按 UUID 区分，改成按 {@link ResourceLocation} 区分（同一个属性上同 ID 只能有一条）。
 * 本模组自己的修饰符一律用 {@code battlecorrection:} 命名空间。
 * <p>
 * <b>旧存档：</b>1.20.1 的存档升级到 1.21.1 时，原版的数据修复器把认不出的 UUID 修饰符改成
 * {@code minecraft:<小写 UUID>}，仍然是永久修饰符（{@code AttributeModifierIdFix}）。本模组只认新 ID 之后，
 * 这些旧修饰符就再也没人删，比如非玩家生物的额外最大生命会叠两份。
 * 所以凡是 1.20.1 用永久修饰符写进存档过的，加新修饰符前都按 {@link #legacy(UUID)} 把旧的删一次。
 */
public final class ModifierIds {

    private ModifierIds() {
    }

    /**
     * 本模组的修饰符 ID
     *
     * @param path 路径，如 {@code max_health}
     * @return {@code battlecorrection:<path>}
     */
    @Nonnull
    public static ResourceLocation of(@Nonnull String path) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
    }

    /**
     * 1.20.1 存档里按 UUID 存的修饰符升级到 1.21.1 之后的 ID
     *
     * @param uuid 1.20.1 用的 UUID
     * @return {@code minecraft:<小写 UUID>}
     */
    @Nonnull
    public static ResourceLocation legacy(@Nonnull UUID uuid) {
        return ResourceLocation.withDefaultNamespace(uuid.toString().toLowerCase(Locale.ROOT));
    }

    /**
     * 删掉 1.20.1 旧存档留下的那条修饰符（没有就什么也不做）
     *
     * @param instance 属性实例
     * @param uuid     1.20.1 用的 UUID
     * @return 是否真的删掉了一条
     */
    public static boolean removeLegacy(@Nonnull AttributeInstance instance, @Nonnull UUID uuid) {
        return instance.removeModifier(legacy(uuid));
    }
}
