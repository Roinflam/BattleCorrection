package pers.roinflam.battlecorrection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.network.packet.OpenItemEditorS2CPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 物品编辑器命令（需要 OP 2 级 + 创造模式）
 * <pre>
 * /battlecorrection edit         编辑主手物品
 * /battlecorrection edit tree    直接打开 NBT 树页
 * /battlecorrection edit raw     直接打开原始 NBT 页
 * </pre>
 * 服务端校验通过后给客户端发一个"打开编辑器"的包，界面在客户端开；保存时客户端再发回来，服务端再校验一次。
 * <p>
 * 根节点 {@code battlecorrection} 和 {@link CurioMarkCommand} 共用：Brigadier 会把同名的字面量节点合并，
 * 两边各注册自己的子命令即可。
 */
public final class ItemEditorCommand {

    private ItemEditorCommand() {
    }

    /**
     * 注册命令
     *
     * @param dispatcher 命令分发器
     */
    public static void register(@Nonnull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("battlecorrection")
                .requires(source -> source.hasPermission(ItemEditorAccess.PERMISSION_LEVEL))
                .then(Commands.literal("edit")
                        .executes(context -> open(context, EditorPage.NAME))
                        .then(Commands.literal("tree")
                                .executes(context -> open(context, EditorPage.TREE)))
                        .then(Commands.literal("raw")
                                .executes(context -> open(context, EditorPage.RAW)))));
    }

    /**
     * 校验并通知客户端打开编辑器
     *
     * @param context 命令上下文
     * @param page    初始页面
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int open(@Nonnull CommandContext<CommandSourceStack> context, @Nonnull EditorPage page)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        if (!ItemEditorAccess.isEnabled()) {
            source.sendFailure(ItemEditorAccess.disabledMessage());
            return 0;
        }
        if (!ItemEditorAccess.canUse(player)) {
            source.sendFailure(ItemEditorAccess.noPermissionMessage());
            return 0;
        }
        if (player.getMainHandItem().isEmpty()) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.empty_hand"));
            return 0;
        }

        ModNetwork.sendToPlayer(player, new OpenItemEditorS2CPacket(ItemEditTarget.mainHand(), page));
        if (LogUtil.isDetailed()) {
            LogUtil.debug(String.format("%s 通过命令打开物品编辑器，页面: %s", player.getName().getString(), page.id()));
        }
        return 1;
    }
}
