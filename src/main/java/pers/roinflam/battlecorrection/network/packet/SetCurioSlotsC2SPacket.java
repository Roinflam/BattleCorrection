package pers.roinflam.battlecorrection.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.battlecorrection.compat.CurioSlotManager;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：把自己某种饰品栏的数量设成指定值（编辑器"玩家饰品栏"页，测试用）
 * <p>
 * 校验和物品编辑一样：功能开关 → OP 且创造 → 装了 Curios → ID 合法。只能改自己的，改不了别人。
 * 真正的操作在 {@link CurioSlotManager}，那个类引用了 Curios，所以先确认 Curios 存在再去碰它。
 */
public class SetCurioSlotsC2SPacket {

    private final String slotId;
    private final int amount;

    /**
     * @param slotId 饰品栏 ID
     * @param amount 目标数量（0 = 没有）
     */
    public SetCurioSlotsC2SPacket(@Nonnull String slotId, int amount) {
        this.slotId = slotId;
        this.amount = amount;
    }

    /**
     * 编码
     *
     * @param buf 缓冲
     */
    public void encode(@Nonnull FriendlyByteBuf buf) {
        buf.writeUtf(slotId, 64);
        buf.writeVarInt(amount);
    }

    /**
     * 解码
     *
     * @param buf 缓冲
     * @return 包
     */
    @Nonnull
    public static SetCurioSlotsC2SPacket decode(@Nonnull FriendlyByteBuf buf) {
        String slotId = buf.readUtf(64);
        int amount = buf.readVarInt();
        return new SetCurioSlotsC2SPacket(slotId, amount);
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
        if (!ItemEditorAccess.isEnabled()) {
            player.sendSystemMessage(ItemEditorAccess.disabledMessage());
            return;
        }
        if (!ItemEditorAccess.canUse(player)) {
            player.sendSystemMessage(ItemEditorAccess.noPermissionMessage());
            return;
        }
        if (!CuriosIntegration.isCuriosLoaded()) {
            player.sendSystemMessage(Component.translatable("message.battlecorrection.slots.no_curios")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!CurioSlotManager.isValidId(slotId)) {
            player.sendSystemMessage(Component.translatable("message.battlecorrection.slots.bad_id", slotId)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(CurioSlotManager.setSlotCount(player, slotId, amount));
    }
}
