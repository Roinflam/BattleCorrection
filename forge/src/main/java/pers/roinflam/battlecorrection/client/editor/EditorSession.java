package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 一次编辑会话：编辑的是哪一格 + 原物品 + 正在改的副本
 * <p>
 * 所有页面都直接改 {@link #working()} 这份副本，预览也读它；点"保存"才把副本的 NBT 和数量发给服务端。
 * 原物品留一份用来判断"有没有改过"。
 */
public final class EditorSession {

    private final ItemEditTarget target;
    private final ItemStack original;
    private final ItemStack working;
    private final ResourceLocation itemId;
    private final boolean hasForgeCaps;

    /**
     * @param target 编辑目标
     * @param stack  目标位置上的物品（会复制，不持有原引用）
     */
    public EditorSession(@Nonnull ItemEditTarget target, @Nonnull ItemStack stack) {
        this.target = target;
        this.original = stack.copy();
        this.working = stack.copy();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        this.itemId = id == null ? new ResourceLocation("minecraft", "air") : id;
        this.hasForgeCaps = stack.save(new CompoundTag()).contains("ForgeCaps");
    }

    /**
     * 这件物品带不带 Forge 能力数据（ForgeCaps）
     * <p>
     * 匠魂进化模组之类会把关键数据放在能力里而不是 tag 里。编辑器只改 tag，服务端写回时是在原物品的副本上换 tag，
     * 能力数据原样保留——这正是某些 NBT 编辑器"一保存特效就没了"的原因：它们把物品整个重建了一遍。
     *
     * @return true = 带
     */
    public boolean hasForgeCaps() {
        return hasForgeCaps;
    }

    /**
     * 编辑目标
     *
     * @return 目标
     */
    @Nonnull
    public ItemEditTarget target() {
        return target;
    }

    /**
     * 打开时的原物品（只读）
     *
     * @return 原物品
     */
    @Nonnull
    public ItemStack original() {
        return original;
    }

    /**
     * 正在编辑的副本，页面直接改它
     *
     * @return 副本
     */
    @Nonnull
    public ItemStack working() {
        return working;
    }

    /**
     * 物品 ID
     *
     * @return 注册名
     */
    @Nonnull
    public ResourceLocation itemId() {
        return itemId;
    }

    /**
     * 副本的 NBT，没有就创建
     *
     * @return NBT
     */
    @Nonnull
    public CompoundTag tag() {
        return working.getOrCreateTag();
    }

    /**
     * 副本的 NBT，没有返回 null
     *
     * @return NBT 或 null
     */
    @Nullable
    public CompoundTag tagOrNull() {
        return working.getTag();
    }

    /**
     * 整体替换副本的 NBT（原始 NBT 页用）
     *
     * @param tag 新 NBT；null 或空 = 清空
     */
    public void setTag(@Nullable CompoundTag tag) {
        working.setTag(tag == null || tag.isEmpty() ? null : tag);
    }

    /**
     * 清理空壳：display 空了就删掉 display，整个 NBT 空了就置空
     * <p>
     * 保存前调一次，免得物品上留着 {@code {display:{}}} 这种没意义的东西。
     */
    public void cleanupEmpty() {
        CompoundTag tag = working.getTag();
        if (tag == null) {
            return;
        }
        if (tag.contains("display") && tag.getCompound("display").isEmpty()) {
            tag.remove("display");
        }
        if (tag.isEmpty()) {
            working.setTag(null);
        }
    }

    /**
     * 是否改过（物品、数量、NBT 任一不同）
     *
     * @return true = 有修改
     */
    public boolean isModified() {
        return !ItemStack.matches(original, working);
    }
}
