package pers.roinflam.battlecorrection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 饰品栏标记与附加属性命令（需要 OP 2 级）
 * <pre>
 * /battlecorrection curio add &lt;饰品栏&gt;        手上这件物品可以放进该饰品栏（any = 任意饰品栏）
 * /battlecorrection curio remove &lt;饰品栏&gt;     移除一个饰品栏标记
 * /battlecorrection curio clear               清除全部饰品栏标记
 * /battlecorrection curio list                查看饰品栏标记和附加属性
 *
 * /battlecorrection curio attr add &lt;属性&gt; &lt;数值&gt; [运算] [饰品栏]   追加一条属性
 * /battlecorrection curio attr remove &lt;属性&gt; [饰品栏]              移除某个属性
 * /battlecorrection curio attr clear                              清除全部附加属性
 * </pre>
 * <p>
 * 属性写在本模组自己的 NBT 上，由 CurioMarkEvents 在 Curios 的属性事件里追加，
 * 不碰 Curios 原生的 CurioAttributeModifiers，所以饰品原本的属性不会被覆盖。
 * <p>
 * 这个类引用了 Curios 的类，只能在装了 Curios 时由 {@link ModCommands} 调用注册。
 */
public final class CurioMarkCommand {

    /**
     * 使用命令需要的权限等级（2 = OP）
     */
    private static final int PERMISSION_LEVEL = 2;

    private static final String ARG_SLOT = "slot";
    private static final String ARG_ATTRIBUTE = "attribute";
    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_OPERATION = "operation";

    /**
     * 运算方式的名字，下标对应 AttributeModifier.Operation 的序号
     */
    private static final String[] OPERATION_NAMES = {"add", "multiply_base", "multiply_total"};

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
                                        .executes(CurioMarkCommand::addSlot)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_SLOT, StringArgumentType.word())
                                        .suggests(CurioMarkCommand::suggestMarkedSlots)
                                        .executes(CurioMarkCommand::removeSlot)))
                        .then(Commands.literal("clear")
                                .executes(CurioMarkCommand::clearSlots))
                        .then(Commands.literal("list")
                                .executes(CurioMarkCommand::list))
                        .then(Commands.literal("attr")
                                .then(Commands.literal("add")
                                        .then(Commands.argument(ARG_ATTRIBUTE, ResourceLocationArgument.id())
                                                .suggests(CurioMarkCommand::suggestAttributes)
                                                .then(Commands.argument(ARG_AMOUNT, DoubleArgumentType.doubleArg())
                                                        .executes(context -> addAttribute(context, 0, CurioMark.ANY))
                                                        .then(Commands.argument(ARG_OPERATION, StringArgumentType.word())
                                                                .suggests((context, builder) ->
                                                                        SharedSuggestionProvider.suggest(OPERATION_NAMES, builder))
                                                                .executes(context -> addAttribute(context,
                                                                        readOperation(context), CurioMark.ANY))
                                                                .then(Commands.argument(ARG_SLOT, StringArgumentType.word())
                                                                        .suggests((context, builder) -> suggestAllSlots(builder))
                                                                        .executes(context -> addAttribute(context,
                                                                                readOperation(context), readSlot(context))))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument(ARG_ATTRIBUTE, ResourceLocationArgument.id())
                                                .suggests(CurioMarkCommand::suggestOwnAttributes)
                                                .executes(context -> removeAttribute(context, null))
                                                .then(Commands.argument(ARG_SLOT, StringArgumentType.word())
                                                        .suggests((context, builder) -> suggestAllSlots(builder))
                                                        .executes(context -> removeAttribute(context, readSlot(context))))))
                                .then(Commands.literal("clear")
                                        .executes(CurioMarkCommand::clearAttributes)))));
    }

    // ═══════════════════════════════════════════════════════════════
    // 饰品栏标记
    // ═══════════════════════════════════════════════════════════════

    /**
     * add：给手上的物品添加饰品栏标记
     *
     * @param context 命令上下文
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int addSlot(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        String slotId = readSlot(context);
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
        warnNativeModifiers(source, stack);
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
    private static int removeSlot(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        String slotId = readSlot(context);
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
    private static int clearSlots(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        if (!CurioMark.clearSlots(stack)) {
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
     * list：查看手上物品的饰品栏标记和附加属性
     *
     * @param context 命令上下文
     * @return 有内容返回 1，没有返回 0
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
        List<CurioMark.ModifierEntry> modifiers = CurioMark.getModifiers(stack);

        if (slots.isEmpty() && modifiers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.list_none",
                    stack.getDisplayName()), false);
            return 0;
        }

        if (!slots.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.list",
                    stack.getDisplayName(), CurioMark.joinSlotNames(slots)), false);
        }
        for (CurioMark.ModifierEntry entry : modifiers) {
            ResourceLocation attributeId = ForgeRegistries.ATTRIBUTES.getKey(entry.attribute());
            source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.attr_line",
                    attributeId == null ? "?" : attributeId.toString(),
                    ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(entry.modifier().getAmount()),
                    OPERATION_NAMES[entry.modifier().getOperation().toValue()],
                    CurioMark.slotName(entry.slotId())), false);
        }
        warnNativeModifiers(source, stack);
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════
    // 附加属性
    // ═══════════════════════════════════════════════════════════════

    /**
     * attr add：给手上的物品追加一条属性
     *
     * @param context     命令上下文
     * @param operationId 运算方式序号（0 加法 / 1 基础乘 / 2 总计乘）
     * @param slotId      生效的饰品栏
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int addAttribute(@Nonnull CommandContext<CommandSourceStack> context, int operationId,
                                    @Nonnull String slotId) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        if (operationId < 0) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.bad_operation",
                    String.join(" / ", OPERATION_NAMES)));
            return 0;
        }
        if (!CurioMark.ANY.equals(slotId) && !CuriosApi.getSlots(false).containsKey(slotId)) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.unknown_slot", slotId));
            return 0;
        }

        Attribute attribute = readAttribute(source, context);
        if (attribute == null) {
            return 0;
        }

        double amount = DoubleArgumentType.getDouble(context, ARG_AMOUNT);
        if (amount == 0) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.zero_amount"));
            return 0;
        }

        UUID uuid = CurioMark.newModifierUuid();
        CurioMark.addModifier(stack, attribute, amount,
                AttributeModifier.Operation.fromValue(operationId), slotId, uuid);

        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.attr_added",
                Component.translatable(attribute.getDescriptionId()),
                ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(amount),
                OPERATION_NAMES[operationId],
                CurioMark.slotName(slotId)), true);

        if (!CurioMark.isMarked(stack)) {
            // 属性已经写上了，但物品还放不进饰品栏，提醒一句免得以为没生效
            source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.attr_not_marked")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        warnNativeModifiers(source, stack);

        LogUtil.info(String.format("%s 给物品 %s 追加了饰品属性: %s %s (%s, 饰品栏: %s)",
                player.getName().getString(), stack.getHoverName().getString(),
                attribute.getDescriptionId(), amount, OPERATION_NAMES[operationId], slotId));
        return 1;
    }

    /**
     * attr remove：移除手上物品某个属性的全部条目
     *
     * @param context 命令上下文
     * @param slotId  只移除该饰品栏的条目；null = 不限饰品栏
     * @return 成功返回移除条数，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int removeAttribute(@Nonnull CommandContext<CommandSourceStack> context,
                                       @Nullable String slotId) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        Attribute attribute = readAttribute(source, context);
        if (attribute == null) {
            return 0;
        }

        int removed = CurioMark.removeModifiers(stack, attribute, slotId);
        if (removed == 0) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.attr_not_found",
                    Component.translatable(attribute.getDescriptionId())));
            return 0;
        }

        int count = removed;
        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.attr_removed",
                Component.translatable(attribute.getDescriptionId()), count), true);
        LogUtil.info(String.format("%s 移除了物品 %s 的饰品属性: %s (%d 条)",
                player.getName().getString(), stack.getHoverName().getString(),
                attribute.getDescriptionId(), removed));
        return removed;
    }

    /**
     * attr clear：清除手上物品的全部附加属性
     *
     * @param context 命令上下文
     * @return 成功返回 1，失败返回 0
     * @throws CommandSyntaxException 执行者不是玩家时抛出
     */
    private static int clearAttributes(@Nonnull CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!checkEnabled(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = getHeldItem(source, player);
        if (stack == null) {
            return 0;
        }

        if (!CurioMark.clearModifiers(stack)) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.attr_none",
                    stack.getDisplayName()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.attr_cleared",
                stack.getDisplayName()), true);
        LogUtil.info(String.format("%s 清除了物品 %s 的全部饰品属性",
                player.getName().getString(), stack.getHoverName().getString()));
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════
    // 公用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 物品带有 Curios 原生属性 NBT 时提醒一句
     * <p>
     * 那个 NBT 会让 Curios 跳过饰品自己的属性方法，原属性被整个替换掉。本模组不会写它，
     * 但物品可能是别处（手写 NBT、其他工具）弄上去的。
     *
     * @param source 命令来源
     * @param stack  物品
     */
    private static void warnNativeModifiers(@Nonnull CommandSourceStack source, @Nonnull ItemStack stack) {
        if (CurioMark.hasNativeCuriosModifiers(stack)) {
            source.sendSuccess(() -> Component.translatable("command.battlecorrection.curio.native_warning")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
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
    private static String readSlot(@Nonnull CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, ARG_SLOT).toLowerCase(Locale.ROOT);
    }

    /**
     * 读取运算方式参数
     *
     * @param context 命令上下文
     * @return 运算方式序号；填了非法值时返回 -1
     */
    private static int readOperation(@Nonnull CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, ARG_OPERATION).toLowerCase(Locale.ROOT);
        for (int i = 0; i < OPERATION_NAMES.length; i++) {
            if (OPERATION_NAMES[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 读取属性参数
     *
     * @param source  命令来源（用于报错）
     * @param context 命令上下文
     * @return 属性；不存在时返回 null 并已发出提示
     */
    @Nullable
    private static Attribute readAttribute(@Nonnull CommandSourceStack source,
                                           @Nonnull CommandContext<CommandSourceStack> context) {
        ResourceLocation attributeId = ResourceLocationArgument.getId(context, ARG_ATTRIBUTE);
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        if (attribute == null) {
            source.sendFailure(Component.translatable("command.battlecorrection.curio.unknown_attribute",
                    attributeId.toString()));
            return null;
        }
        return attribute;
    }

    // ═══════════════════════════════════════════════════════════════
    // 补全
    // ═══════════════════════════════════════════════════════════════

    /**
     * 属性补全：游戏里注册过的所有属性
     *
     * @param context 命令上下文
     * @param builder 补全构建器
     * @return 补全结果
     */
    @Nonnull
    private static CompletableFuture<Suggestions> suggestAttributes(
            @Nonnull CommandContext<CommandSourceStack> context, @Nonnull SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(ForgeRegistries.ATTRIBUTES.getKeys(), builder);
    }

    /**
     * 属性补全：只列出手上物品已有的附加属性
     *
     * @param context 命令上下文
     * @param builder 补全构建器
     * @return 补全结果
     */
    @Nonnull
    private static CompletableFuture<Suggestions> suggestOwnAttributes(
            @Nonnull CommandContext<CommandSourceStack> context, @Nonnull SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return Suggestions.empty();
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (CurioMark.ModifierEntry entry : CurioMark.getModifiers(player.getMainHandItem())) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(entry.attribute());
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return SharedSuggestionProvider.suggestResource(ids, builder);
    }

    /**
     * 饰品栏补全：第一项固定是 any（通用），后面是服务器上所有饰品栏（按字母排序）
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
     * 饰品栏补全：只列出手上物品已有的标记（有 any 时 any 排第一）
     *
     * @param context 命令上下文
     * @param builder 补全构建器
     * @return 补全结果
     */
    @Nonnull
    private static CompletableFuture<Suggestions> suggestMarkedSlots(
            @Nonnull CommandContext<CommandSourceStack> context, @Nonnull SuggestionsBuilder builder) {
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
     * 客户端只会把"开头匹配的"提到前面，不会再整体重排。
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
