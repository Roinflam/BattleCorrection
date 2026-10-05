package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：保存编辑器里的修改
 * <p>
 * 带上"编辑的是哪一格 + 打开时那一格的物品 ID + 保存后的物品 ID + 新 NBT + 新数量"。服务端拿到后按顺序校验：
 * 功能开关 → OP 且创造 → 那一格还有物品 → 那一格的物品还是打开时的那件 → 新物品 ID 是已注册的物品，
 * 全过了才写回。包走 {@link ChunkedPacket} 分片，体积上限由配置决定（分片层在解码之前就卡掉了）。
 * 任何一步不过都回一条红字，什么都不改。客户端上的检查一概不信。
 * <p>
 * "打开时的物品 ID"只用来确认格子里的东西没被换过；保存后的物品 ID 和它不同就是换了物品：
 * 按新物品重建，NBT 和数量沿用编辑结果，原物品附带的 Forge 能力数据属于旧物品，不带过去。
 * 数量限制在 1-127：NBT 里 Count 是一个 byte，再大就溢出了。
 */
public class ApplyItemEditC2SPacket {

    /**
     * 数量下限
     */
    public static final int MIN_COUNT = 1;

    /**
     * 数量上限
     */
    public static final int MAX_COUNT = 127;

    private final ItemEditTarget target;
    private final ResourceLocation itemId;
    private final ResourceLocation newItemId;
    @Nullable
    private final CompoundTag tag;
    private final int count;

    /**
     * @param target 编辑目标
     * @param itemId    打开编辑器时那一格的物品 ID（防止格子里的东西换了还照写）
     * @param newItemId 保存后的物品 ID；和 itemId 相同 = 不换物品
     * @param tag       新 NBT；null = 清空 NBT
     * @param count     新数量
     */
    public ApplyItemEditC2SPacket(@Nonnull ItemEditTarget target, @Nonnull ResourceLocation itemId,
                                  @Nonnull ResourceLocation newItemId, @Nullable CompoundTag tag, int count) {
        this.target = target;
        this.itemId = itemId;
        this.newItemId = newItemId;
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
        buf.writeResourceLocation(newItemId);
        buf.writeNbt(tag);
        buf.writeVarInt(count);
    }

    /**
     * 解码（只由分片层调用）
     * <p>
     * 读 NBT 用和原版客户端解物品格子相同的 2 MB 记账（{@link ChunkedPacket#VANILLA_NBT_QUOTA}）：
     * 声明了超大数组长度的 NBT 在分配之前就会被拦下；记账超限的物品原版客户端也解不开，写进存档只会让持有者每次进服都掉线。
     *
     * @param buf 缓冲
     * @return 包
     */
    @Nonnull
    public static ApplyItemEditC2SPacket decode(@Nonnull FriendlyByteBuf buf) {
        ItemEditTarget target = ItemEditTarget.read(buf);
        ResourceLocation itemId = buf.readResourceLocation();
        ResourceLocation newItemId = buf.readResourceLocation();
        CompoundTag tag = buf.readNbt(new NbtAccounter(ChunkedPacket.VANILLA_NBT_QUOTA));
        int count = buf.readVarInt();
        return new ApplyItemEditC2SPacket(target, itemId, newItemId, tag, count);
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

        boolean changeItem = !newItemId.equals(itemId);
        ItemStack edited;
        if (changeItem) {
            Item item = ForgeRegistries.ITEMS.getValue(newItemId);
            if (item == null || item == Items.AIR || !ForgeRegistries.ITEMS.containsKey(newItemId)) {
                player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.unknown_item", newItemId));
                return;
            }
            edited = new ItemStack(item);
        } else {
            edited = current.copy();
        }
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
        LogUtil.info(changeItem
                ? String.format("%s 用物品编辑器把 %s 换成了 %s（%s），数量 %d",
                player.getName().getString(), itemId, newItemId, edited.getHoverName().getString(), edited.getCount())
                : String.format("%s 用物品编辑器修改了物品 %s（%s），数量 %d",
                player.getName().getString(), edited.getHoverName().getString(), itemId, edited.getCount()));
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
