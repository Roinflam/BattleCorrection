package pers.roinflam.battlecorrection.network.packet;

import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * 客户端 → 服务端：保存编辑器里的修改
 * <p>
 * 带上"编辑的是哪一格 + 打开时那一格的物品 ID + 编辑后整件物品的根 NBT"。服务端拿到后按顺序校验：
 * 功能开关 → OP 且创造 → 那一格还有物品 → 那一格的物品还是打开时的那件 → 根里的物品 ID 是已注册的物品 → 根能解析成物品，
 * 全过了才写回。包走 {@link ChunkedPacket} 分片，体积上限由配置决定（分片层在解码之前就卡掉了）。
 * 任何一步不过都回一条红字，什么都不改。客户端上的检查一概不信。
 * <p>
 * 1.21.1：物品没有 tag 了，传的是 {@code ItemStack.save()} 出来的根 {@code {id, count, components}}，
 * 服务端用原版的物品编解码器读回来，组件的合法性（附魔等级、Lore 行数之类）由编解码器把关。
 * 根里的物品 ID 和打开时不同就是换了物品，组件和数量沿用编辑结果。1.20.1 换物品时要丢掉的 ForgeCaps 在 1.21.1 不存在了。
 * 数量限制在 1-99：1.21.1 存盘格式里数量的合法范围就是这么大（1.20.1 是 byte，上限 127）。
 */
public class ApplyItemEditC2SPacket {

    /**
     * 数量下限
     */
    public static final int MIN_COUNT = 1;

    /**
     * 数量上限：1.21.1 物品存盘格式（{@code ItemStack.CODEC}）里 count 只认 1 ~ 99，再大存不进存档
     */
    public static final int MAX_COUNT = 99;

    private final ItemEditTarget target;
    private final ResourceLocation itemId;
    private final CompoundTag root;

    /**
     * @param target 编辑目标
     * @param itemId 打开编辑器时那一格的物品 ID（防止格子里的东西换了还照写）
     * @param root   编辑后整件物品的根（{@code {id, count, components}}）
     */
    public ApplyItemEditC2SPacket(@Nonnull ItemEditTarget target, @Nonnull ResourceLocation itemId, @Nonnull CompoundTag root) {
        this.target = target;
        this.itemId = itemId;
        this.root = root;
    }

    /**
     * 编码
     *
     * @param buf 缓冲
     */
    public void encode(@Nonnull FriendlyByteBuf buf) {
        target.write(buf);
        buf.writeResourceLocation(itemId);
        buf.writeNbt(root);
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
        CompoundTag root = ChunkedPacket.readCompound(buf, ChunkedPacket.VANILLA_NBT_QUOTA);
        return new ApplyItemEditC2SPacket(target, itemId, root == null ? new CompoundTag() : root);
    }

    /**
     * 处理（服务端主线程）
     *
     * @param ctx 网络上下文
     */
    public void handle(@Nonnull IPayloadContext ctx) {
        ServerPlayer player = ctx.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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

        ResourceLocation currentId = BuiltInRegistries.ITEM.getKey(current.getItem());
        if (!currentId.equals(itemId)) {
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.changed"));
            return;
        }

        ResourceLocation newItemId = ResourceLocation.tryParse(root.getString("id"));
        Item item = newItemId == null || !BuiltInRegistries.ITEM.containsKey(newItemId) ? null : BuiltInRegistries.ITEM.get(newItemId);
        if (newItemId == null || item == null || item == Items.AIR) {
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.unknown_item", root.getString("id")));
            return;
        }
        boolean changeItem = !newItemId.equals(itemId);

        // 数量先夹到合法范围再解析：超出 1 ~ 99 的数量原版编解码器会整件拒收
        CompoundTag input = root.copy();
        long count = input.get("count") instanceof NumericTag number ? number.getAsLong() : 1L;
        input.putInt("count", (int) Mth.clamp(count, (long) MIN_COUNT, (long) MAX_COUNT));
        DataResult<ItemStack> result;
        try {
            result = ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), input);
        } catch (RuntimeException e) {
            result = DataResult.error(() -> String.valueOf(e.getMessage()));
        }
        Optional<DataResult.Error<ItemStack>> error = result.error();
        ItemStack edited = error.isPresent() ? ItemStack.EMPTY : result.result().orElse(ItemStack.EMPTY);
        if (edited.isEmpty()) {
            String reason = error.map(DataResult.Error::message).orElse("empty");
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.invalid", reason));
            LogUtil.warn(String.format("玩家 %s 用物品编辑器保存的物品解析失败：%s", player.getName().getString(), reason));
            return;
        }

        if (!target.set(player, edited)) {
            player.sendSystemMessage(failure("message.battlecorrection.editor.save_failed.missing"));
            return;
        }
        // 背包类容器由 inventoryMenu 负责同步；容器格子在 set 里已经 broadcastChanges 了
        player.inventoryMenu.broadcastChanges();

        player.sendSystemMessage(Component.translatable("message.battlecorrection.editor.saved",
                ItemUtil.chatName(edited, player.registryAccess())).withStyle(ChatFormatting.GREEN));
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
