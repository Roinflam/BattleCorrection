package pers.roinflam.battlecorrection.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：保存编辑器里的修改
 * <p>
 * 带上"编辑的是哪一格 + 当时的物品 ID + 新 NBT + 新数量"。服务端拿到后按顺序校验：
 * 功能开关 → OP 且创造 → 那一格还有物品 → 物品 ID 没变 → NBT 大小没超上限，全过了才写回。
 * 任何一步不过都回一条红字，什么都不改。客户端上的检查一概不信。
 * <p>
 * 物品 ID 不允许改（只改 NBT 和数量），要换物品就换一件再编辑。
 * 数量限制在 1-64：NBT 里 Count 是一个 byte，再大就溢出了。
 */
public class ApplyItemEditC2SPacket {

    /**
     * 数量下限
     */
    public static final int MIN_COUNT = 1;

    /**
     * 数量上限
     */
    public static final int MAX_COUNT = 64;

    private final ItemEditTarget target;
    private final ResourceLocation itemId;
    @Nullable
    private final CompoundTag tag;
    private final int count;

    /**
     * @param target 编辑目标
     * @param itemId 编辑时的物品 ID（防止格子里的东西换了还照写）
     * @param tag    新 NBT；null = 清空 NBT
     * @param count  新数量
     */
    public ApplyItemEditC2SPacket(@Nonnull ItemEditTarget target, @Nonnull ResourceLocation itemId,
                                  @Nullable CompoundTag tag, int count) {
        this.target = target;
        this.itemId = itemId;
        this.tag = tag;
        this.count = count;
    }

    /**
     * 编码
     *
     * @param buf 缓冲
     */
    public void encode(@Nonnull FriendlyByteBuf buf) {
        target.write(buf);
        buf.writeResourceLocation(itemId);
        buf.writeNbt(tag);
        buf.writeVarInt(count);
    }

    /**
     * 解码
     * <p>
     * readNbt 自带 2MB 上限，超过直接抛异常断开，不会把内存吃满。
     *
     * @param buf 缓冲
     * @return 包
     */
    @Nonnull
    public static ApplyItemEditC2SPacket decode(@Nonnull FriendlyByteBuf buf) {
        ItemEditTarget target = ItemEditTarget.read(buf);
        ResourceLocation itemId = buf.readResourceLocation();
        CompoundTag tag = buf.readNbt();
        int count = buf.readVarInt();
        return new ApplyItemEditC2SPacket(target, itemId, tag, count);
    }

    /**
     * 处理（服务端主线程）
     *
     * @param ctx 网络上下文
     */
    public void handle(@Nonnull Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        apply(player);
    }

    /**
     * 校验并写回
     *
     * @param player 发包的玩家
     */
    private void apply(@Nonnull ServerPlayer player) {
        if (!ItemEditorAccess.isEnabled()) {
            player.sendSystemMessage(ItemEditorAccess.disabledMessage());
            return;
        }
        if (!ItemEditorAccess.canUse(player)) {
            player.sendSystemMessage(ItemEditorAccess.noPermissionMessage());
            LogUtil.warn(String.format("玩家 %s 没有权限却发来了物品编辑包，已忽略", player.getName().getString()));
            return;
        }

        ItemStack current = target.get(player);
        if (current.isEmpty()) {
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.missing"));
            return;
        }

        ResourceLocation currentId = ForgeRegistries.ITEMS.getKey(current.getItem());
        if (currentId == null || !currentId.equals(itemId)) {
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.changed"));
            return;
        }

        if (tag != null) {
            int bytes = measureBytes(tag);
            int limit = ItemEditorAccess.maxNbtBytes();
            if (bytes > limit) {
                player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.too_large",
                        ConfigAttribute.ITEM_EDITOR_MAX_NBT_KB.get()));
                return;
            }
        }

        ItemStack edited = current.copy();
        edited.setTag(tag == null || tag.isEmpty() ? null : tag);
        edited.setCount(Mth.clamp(count, MIN_COUNT, MAX_COUNT));

        if (!target.set(player, edited)) {
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.missing"));
            return;
        }
        // 背包类容器由 inventoryMenu 负责同步；容器格子在 set 里已经 broadcastChanges 了
        player.inventoryMenu.broadcastChanges();

        player.sendSystemMessage(Component.translatable("message.battlecorrection.editor.saved",
                edited.getDisplayName()).withStyle(ChatFormatting.GREEN));
        LogUtil.info(String.format("%s 用物品编辑器修改了物品 %s（%s），数量 %d",
                player.getName().getString(), edited.getHoverName().getString(), itemId, edited.getCount()));
    }

    /**
     * 算一份 NBT 序列化后占多少字节（和网络包里的大小一致）
     *
     * @param tag NBT
     * @return 字节数
     */
    private static int measureBytes(@Nonnull CompoundTag tag) {
        FriendlyByteBuf sizer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            sizer.writeNbt(tag);
            return sizer.readableBytes();
        } finally {
            sizer.release();
        }
    }

    /**
     * 红字失败提示
     *
     * @param key  翻译键
     * @param args 参数
     * @return 提示
     */
    @Nonnull
    private static Component failure(@Nonnull String key, @Nonnull Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }
}
