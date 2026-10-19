package pers.roinflam.battlecorrection.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 根命令 /battlecorrection
 * <p>
 * 1.12.2 的命令系统没有 Brigadier 那样的子节点合并，子命令都挂在这一个类上，
 * 具体逻辑在各自的类里（{@link BaubleMarkCommand} 等），这里只按第一个参数分发。
 * <ul>
 *     <li>{@code edit [snbt|nbt]}：物品编辑器（{@link ItemEditorCommand}）</li>
 *     <li>{@code bauble ...}：饰品栏标记，只在装了 Baubles 时可用；{@code curio} 是兼容 1.20.1 习惯的别名</li>
 * </ul>
 */
public class ModCommands extends CommandBase {

    public static final String NAME = "battlecorrection";
    public static final String USAGE_KEY = "command.battlecorrection.usage";

    @Nonnull
    @Override
    public String getName() {
        return NAME;
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return USAGE_KEY;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(USAGE_KEY);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if ("edit".equals(sub)) {
            ItemEditorCommand.execute(server, sender, rest);
            return;
        }
        if (BaubleMarkCommand.subcommandWords().contains(sub)) {
            BaubleMarkCommand.execute(server, sender, rest);
            return;
        }
        throw new WrongUsageException(USAGE_KEY);
    }

    @Nonnull
    @Override
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                          @Nonnull String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, subcommands());
        }
        if (args.length > 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            if ("edit".equals(sub)) {
                return ItemEditorCommand.tabComplete(rest);
            }
            if (BaubleMarkCommand.subcommandWords().contains(sub)) {
                return BaubleMarkCommand.tabComplete(server, sender, rest, targetPos);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 补全时列出的子命令（curio 只是别名，不列）
     */
    @Nonnull
    private static List<String> subcommands() {
        List<String> words = new ArrayList<>();
        words.add("edit");
        if (BaublesIntegration.isBaublesLoaded()) {
            words.add("bauble");
        }
        return words;
    }
}
