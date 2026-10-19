package pers.roinflam.battlecorrection.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import pers.roinflam.battlecorrection.config.ConfigBattle;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemEditTarget;
import pers.roinflam.battlecorrection.editor.ItemEditorAccess;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.network.packet.OpenItemEditorS2CPacket;
import pers.roinflam.battlecorrection.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 物品编辑器命令（需要 OP 2 级 + 创造模式）
 * <pre>
 * /battlecorrection edit         编辑主手物品
 * /battlecorrection edit snbt    直接打开 SNBT 文本页（raw 是它的旧名，照样能用）
 * /battlecorrection edit nbt     直接打开 NBT 树页（tree 是它的旧名，照样能用）
 * </pre>
 * 服务端校验通过后给客户端发一个"打开编辑器"的包，界面在客户端开；保存时客户端再发回来，服务端再校验一次。
 * <p>
 * 根命令 {@code /battlecorrection} 是另外注册的，收到 {@code edit} 时把后面的参数交给 {@link #execute}。
 */
public final class ItemEditorCommand {

    /**
     * 用法提示的翻译键
     */
    public static final String USAGE = "command.battlecorrection.edit.usage";

    /**
     * edit 后面能跟的参数（补全用；raw / tree 是旧名，不提示）
     */
    private static final List<String> PAGES = Arrays.asList("snbt", "nbt");

    private ItemEditorCommand() {
    }

    /**
     * 执行 {@code /battlecorrection edit ...}
     *
     * @param server 服务器
     * @param sender 执行者
     * @param args   edit 之后的参数
     * @throws CommandException 执行者不是玩家、参数不对、没开功能、没权限、主手为空时抛出（原版会把它显示成红字）
     */
    public static void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args)
            throws CommandException {
        EditorPage page = parsePage(args);
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);

        if (!ItemEditorAccess.isEnabled()) {
            throw new CommandException("message.battlecorrection.editor.disabled");
        }
        if (!ItemEditorAccess.canUse(player)) {
            throw new CommandException("message.battlecorrection.editor.no_permission");
        }
        if (player.getHeldItemMainhand().isEmpty()) {
            throw new CommandException("command.battlecorrection.bauble.empty_hand");
        }

        ModNetwork.sendToPlayer(player, new OpenItemEditorS2CPacket(ItemEditTarget.mainHand(), page));
        if (ConfigBattle.enableDetailedLogging) {
            LogUtil.debug(String.format("%s 通过命令打开物品编辑器，页面: %s", player.getName(), page.id()));
        }
    }

    @Nonnull
    private static EditorPage parsePage(@Nonnull String[] args) throws CommandException {
        if (args.length == 0) {
            return EditorPage.NAME;
        }
        if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "snbt":
                case "raw":
                    return EditorPage.RAW;
                case "nbt":
                case "tree":
                    return EditorPage.TREE;
                default:
                    break;
            }
        }
        throw new WrongUsageException(USAGE);
    }

    /**
     * Tab 补全
     *
     * @param args edit 之后的参数
     * @return 候选
     */
    @Nonnull
    public static List<String> tabComplete(@Nonnull String[] args) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, PAGES);
        }
        return Collections.emptyList();
    }
}
