package pers.roinflam.battlecorrection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.utils.LogUtil;
import top.theillusivec4.curios.api.CuriosApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 饰品栏标记命令（需要 OP 2 级）
 * <pre>
 * /battlecorrection curio add &lt;饰品栏&gt;      手上这件物品可以放进该饰品栏（any = 任意饰品栏）
 * /battlecorrection curio remove &lt;饰品栏&gt;   移除一个饰品栏标记
 * /battlecorrection curio clear             清除全部饰品栏标记
 * /battlecorrection curio list              查看手上物品的饰品栏标记
 * </pre>
 * <p>
 * 这个类引用了 Curios 的类，只能在装了 Curios 时由 {@link ModCommands} 调用注册。
 */
public final class CurioMarkCommand {

    /**
     * 使用命令需要的权限等级（2 = OP）
     */
    private static final int PERMISSION_LEVEL = 2;

    /**
     * 饰品栏参数名
     */
    private static final String ARG_SLOT = "slot";

    private CurioMarkCommand() {
    }

    /**
     * 注册命令
     *
     * @param dispatcher 命令分发器
     */
    public static void register(@Nonnull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("battlecorrection")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("curio")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_SLOT, StringArgumentType.word())
                                        .suggests((context, builder) -> suggestAllSlots(builder))
                                        .executes(CurioMarkCommand::add)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_SLOT, StringArgumentType.word())
                                        .suggests(CurioMarkCommand::suggestMarkedSlots)
                                        .executes(CurioMarkCommand::remove)))
                        .then(Commands.literal("clear")
                                .executes(CurioMarkCommand::clear))
                        .then(Commands.literal("list")
                                .executes(CurioMarkCommand::list))));
    }

    /**
     * add：给手上的物品添加饰品栏标记
     *
     * @param context 命令上下文
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int add(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        String slotId = readSlotArgument(context);
        if (!CurioMark.ANY.equals(slotId) && !CuriosApi.getSlots(false).containsKey(slotId)) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.unknown_slot", slotId));
            return 0;
        }

        if (!CurioMark.addSlot(stack, slotId)) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.already",
                    stack.getDisplayName(), CurioMark.slotName(slotId)));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.added",
                stack.getDisplayName(), CurioMark.slotName(slotId)), true);
        LogUtil.info(String.format("%s 给物品 %s 添加了饰品栏标记: %s",
                player.getName().getString(), stack.getHoverName().getString(), slotId));
        return 1;
    }

    /**
     * remove：移除手上物品的一个饰品栏标记
     *
     * @param context 命令上下文
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int remove(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        String slotId = readSlotArgument(context);
        if (!CurioMark.removeSlot(stack, slotId)) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.not_marked_slot",
                    stack.getDisplayName(), CurioMark.slotName(slotId)));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.removed",
                stack.getDisplayName(), CurioMark.slotName(slotId)), true);
        LogUtil.info(String.format("%s 移除了物品 %s 的饰品栏标记: %s",
                player.getName().getString(), stack.getHoverName().getString(), slotId));
        return 1;
    }

    /**
     * clear：清除手上物品的全部饰品栏标记
     *
     * @param context 命令上下文
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int clear(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        if (!CurioMark.clear(stack)) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.list_none",
                    stack.getDisplayName()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.cleared",
                stack.getDisplayName()), true);
        LogUtil.info(String.format("%s 清除了物品 %s 的全部饰品栏标记",
                player.getName().getString(), stack.getHoverName().getString()));
        return 1;
    }

    /**
     * list：查看手上物品的饰品栏标记
     *
     * @param context 命令上下文
     * @return 有标记返回 1，没有返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int list(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        List<String> slots = CurioMark.getSlots(stack);
        if (slots.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.list_none",
                    stack.getDisplayName()), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.list",
                stack.getDisplayName(), CurioMark.joinSlotNames(slots)), false);
        return 1;
    }

    /**
     * 检查总开关，关闭时给出提示
     *
     * @param source 命令来源
     * @return true = 功能已开启
     */
    private static boolean checkEnabled(@Nonnull CommandSourceStack source) {
        if (ConfigAttribute.CURIO_MARK_ENABLED.get()) {
            return true;
        }
        source.sendFailure(Component.translatable("command.battlecorrection.curio.disabled"));
        return false;
    }

    /**
     * 获取玩家主手物品，空手时给出提示
     *
     * @param source 命令来源
     * @param player 玩家
     * @return 主手物品；空手时返回 null
     */
    @Nullable
    private static ItemStack getHeldItem(@Nonnull CommandSourceStack source, @Nonnull ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.empty_hand"));
            return null;
        }
        return stack;
    }

    /**
     * 读取饰品栏参数（统一转小写，Curios 的饰品栏ID都是小写）
     *
     * @param context 命令上下文
     * @return 饰品栏ID
     */
    @Nonnull
    private static String readSlotArgument(@Nonnull CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, ARG_SLOT).toLowerCase(Locale.ROOT);
    }

    /**
     * add 的补全：第一项固定是 any（通用），后面是服务器上所有饰品栏（按字母排序）
     *
     * @param builder 补全构建器
     * @return 补全结果
     */
    @Nonnull
    private static CompletableFuture<Suggestions> suggestAllSlots(@Nonnull SuggestionsBuilder builder) {
        List<String> slotIds = new ArrayList<>(CuriosApi.getSlots(false).keySet());
        slotIds.sort(String::compareTo);
        return buildOrderedSuggestions(builder, true, slotIds);
    }

    /**
     * remove 的补全：只列出手上物品已有的标记（有 any 时 any 排第一）
     *
     * @param context 命令上下文
     * @param builder 补全构建器
     * @return 补全结果
     */
    @Nonnull
    private static CompletableFuture<Suggestions> suggestMarkedSlots(@Nonnull CommandContext<CommandSourceStack> context,
                                                                     @Nonnull SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return Suggestions.empty();
        }
        List<String> marked = new ArrayList<>(CurioMark.getSlots(player.getMainHandItem()));
        boolean hasAny = marked.remove(CurioMark.ANY);
        marked.sort(String::compareTo);
        return buildOrderedSuggestions(builder, hasAny, marked);
    }

    /**
     * 按给定顺序生成补全
     * <p>
     * Brigadier 的 SuggestionsBuilder.build() 会把所有补全按字母重新排序，any 不一定排第一
     * （比如有模组加了 amulet 饰品栏）。这里直接构造 Suggestions 保留顺序；
     * 只有一个参数节点时服务端不会再重新排序，客户端也只会把"开头匹配的"提到前面，顺序保持不变。
     *
     * @param builder    补全构建器
     * @param includeAny 是否在第一项加入 any
     * @param slotIds    其余饰品栏ID（已排好序）
     * @return 补全结果
     */
    @Nonnull
    private static CompletableFuture<Suggestions> buildOrderedSuggestions(@Nonnull SuggestionsBuilder builder,
                                                                          boolean includeAny,
                                                                          @Nonnull Collection<String> slotIds) {
        String typed = builder.getRemainingLowerCase();
        StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
        List<Suggestion> suggestions = new ArrayList<>(slotIds.size() + 1);

        if (includeAny && CurioMark.ANY.startsWith(typed)) {
            suggestions.add(new Suggestion(range, CurioMark.ANY,
                    Component.translatable("command.battlecorrection.curio.any_tooltip")));
        }
        for (String slotId : slotIds) {
            if (slotId.startsWith(typed)) {
                suggestions.add(new Suggestion(range, slotId, CurioMark.slotName(slotId)));
            }
        }

        if (suggestions.isEmpty()) {
            return Suggestions.empty();
        }
        return CompletableFuture.completedFuture(new Suggestions(range, suggestions));
    }
}
