package pers.roinflam.battlecorrection.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import pers.roinflam.battlecorrection.compat.BaubleMark;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;
import pers.roinflam.battlecorrection.config.ConfigBaubles;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 饰品栏标记与附加属性命令（需要 OP 2 级）
 * <pre>
 * /battlecorrection bauble add &lt;饰品栏&gt;        手上这件物品可以放进该饰品栏（any = 任意饰品栏）
 * /battlecorrection bauble remove &lt;饰品栏&gt;     移除一个饰品栏标记
 * /battlecorrection bauble clear               清除全部饰品栏标记
 * /battlecorrection bauble list                查看饰品栏标记和附加属性
 *
 * /battlecorrection bauble attr add &lt;属性&gt; &lt;数值&gt; [运算] [饰品栏]   追加一条属性
 * /battlecorrection bauble attr remove &lt;属性&gt; [饰品栏]              移除某个属性
 * /battlecorrection bauble attr clear                              清除全部附加属性
 * </pre>
 * 饰品栏：amulet / ring / belt / head / body / charm / any（trinket 当成 any）。
 * 属性：1.12.2 的属性名，例如 generic.maxHealth、battlecorrection.customCriticalChance；
 * 只认执行者自己身上有的属性（1.12.2 没有全局属性注册表，玩家身上没有的属性加了也不会生效）。
 * <p>
 * 属性写在本模组自己的 NBT 上，由 BaubleWornModifiers 在佩戴时加到玩家身上。
 * <p>
 * 这个类不引用 Baubles 的类，由根命令 /battlecorrection 把 bauble（1.20.1 版用的 curio 也认）后面的参数转过来。
 */
public final class BaubleMarkCommand {

    /**
     * 根命令名（用来找根命令实例，让成功消息按原版规则通知其他管理员）
     */
    private static final String ROOT_COMMAND = "battlecorrection";

    /**
     * 使用命令需要的权限等级（2 = OP）
     */
    private static final int PERMISSION_LEVEL = 2;

    /**
     * 用法提示的翻译键
     */
    public static final String USAGE_KEY = "command.battlecorrection.bauble.usage";

    /**
     * 运算方式的名字，下标对应 AttributeModifier 的运算方式序号
     */
    private static final String[] OPERATION_NAMES = {"add", "multiply_base", "multiply_total"};

    private BaubleMarkCommand() {
    }

    // ═══════════════════════════════════════════════════════════════
    // 入口
    // ═══════════════════════════════════════════════════════════════

    /**
     * 执行命令
     *
     * @param server 服务器
     * @param sender 执行者
     * @param args   bauble 之后的参数
     * @throws CommandException 参数不对、执行者不是玩家、空手等情况，消息由命令系统显示成红字
     */
    public static void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                               @Nonnull String[] args) throws CommandException {
        if (!sender.canUseCommand(PERMISSION_LEVEL, ROOT_COMMAND)) {
            throw new CommandException("commands.generic.permission");
        }
        if (!BaublesIntegration.isBaublesLoaded()) {
            throw new CommandException("command.battlecorrection.bauble.not_loaded");
        }
        if (args.length == 0) {
            throw new WrongUsageException(USAGE_KEY);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            list(sender, args);
            return;
        }
        checkEnabled();
        switch (sub) {
            case "add":
                addSlot(server, sender, args);
                break;
            case "remove":
                removeSlot(server, sender, args);
                break;
            case "clear":
                clearSlots(server, sender, args);
                break;
            case "attr":
                attr(server, sender, args);
                break;
            default:
                throw new WrongUsageException(USAGE_KEY);
        }
    }

    /**
     * Tab 补全
     *
     * @param server 服务器
     * @param sender 执行者
     * @param args   bauble 之后的参数（最后一个是正在输入的）
     * @param pos    指着的方块
     * @return 补全列表
     */
    @Nonnull
    public static List<String> tabComplete(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                           @Nonnull String[] args, @Nullable BlockPos pos) {
        if (args.length == 0 || !BaublesIntegration.isBaublesLoaded()) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "add", "remove", "clear", "list", "attr");
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("add".equals(sub) && args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, allSlots());
        }
        if ("remove".equals(sub) && args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, markedSlots(sender));
        }
        if (!"attr".equals(sub)) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "add", "remove", "clear");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("add".equals(action)) {
            switch (args.length) {
                case 3:
                    return CommandBase.getListOfStringsMatchingLastWord(args, playerAttributes(sender));
                case 5:
                    return CommandBase.getListOfStringsMatchingLastWord(args, OPERATION_NAMES);
                case 6:
                    return CommandBase.getListOfStringsMatchingLastWord(args, allSlots());
                default:
                    return Collections.emptyList();
            }
        }
        if ("remove".equals(action)) {
            if (args.length == 3) {
                return CommandBase.getListOfStringsMatchingLastWord(args, ownAttributes(sender));
            }
            if (args.length == 4) {
                return CommandBase.getListOfStringsMatchingLastWord(args, allSlots());
            }
        }
        return Collections.emptyList();
    }

    // ═══════════════════════════════════════════════════════════════
    // 饰品栏标记
    // ═══════════════════════════════════════════════════════════════

    /**
     * add：给手上的物品添加饰品栏标记
     */
    private static void addSlot(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                @Nonnull String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);
        String slotId = readSlot(args[1]);

        if (!BaubleMark.addSlot(stack, slotId)) {
            throw new CommandException("command.battlecorrection.bauble.already",
                    ItemUtil.chatName(stack), BaubleMark.slotName(slotId));
        }

        notifySuccess(server, sender, "command.battlecorrection.bauble.added",
                ItemUtil.chatName(stack), BaubleMark.slotName(slotId));
        warnNativeBauble(sender, stack);
        LogUtil.info(String.format("%s 给物品 %s 添加了饰品栏标记: %s",
                player.getName(), stack.getDisplayName(), slotId));
    }

    /**
     * remove：移除手上物品的一个饰品栏标记
     */
    private static void removeSlot(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                   @Nonnull String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);
        // 移除时不检查 ID 认不认识：手写 NBT 标上的奇怪 ID 也要能删掉
        String slotId = BaubleMark.normalizeSlotId(args[1]);

        if (!BaubleMark.removeSlot(stack, slotId)) {
            throw new CommandException("command.battlecorrection.bauble.not_marked_slot",
                    ItemUtil.chatName(stack), BaubleMark.slotName(slotId));
        }

        notifySuccess(server, sender, "command.battlecorrection.bauble.removed",
                ItemUtil.chatName(stack), BaubleMark.slotName(slotId));
        LogUtil.info(String.format("%s 移除了物品 %s 的饰品栏标记: %s",
                player.getName(), stack.getDisplayName(), slotId));
    }

    /**
     * clear：清除手上物品的全部饰品栏标记
     */
    private static void clearSlots(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                   @Nonnull String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);

        if (!BaubleMark.clearSlots(stack)) {
            throw new CommandException("command.battlecorrection.bauble.list_none", ItemUtil.chatName(stack));
        }

        notifySuccess(server, sender, "command.battlecorrection.bauble.cleared", ItemUtil.chatName(stack));
        LogUtil.info(String.format("%s 清除了物品 %s 的全部饰品栏标记",
                player.getName(), stack.getDisplayName()));
    }

    /**
     * list：查看手上物品的饰品栏标记和附加属性（总开关关着也能看）
     */
    private static void list(@Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);

        List<String> slots = BaubleMark.getSlots(stack);
        List<BaubleMark.ModifierEntry> modifiers = BaubleMark.getModifiers(stack);

        if (slots.isEmpty() && modifiers.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("command.battlecorrection.bauble.list_none",
                    ItemUtil.chatName(stack)));
            warnNativeBauble(sender, stack);
            return;
        }

        if (!slots.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("command.battlecorrection.bauble.list",
                    ItemUtil.chatName(stack), BaubleMark.joinSlotNames(slots)));
        }
        for (BaubleMark.ModifierEntry entry : modifiers) {
            sender.sendMessage(new TextComponentTranslation("command.battlecorrection.bauble.attr_line",
                    attributeText(entry.attributeName()),
                    ItemStack.DECIMALFORMAT.format(entry.modifier().getAmount()),
                    OPERATION_NAMES[entry.modifier().getOperation()],
                    BaubleMark.slotName(entry.slotId())));
        }
        warnNativeBauble(sender, stack);
    }

    // ═══════════════════════════════════════════════════════════════
    // 附加属性
    // ═══════════════════════════════════════════════════════════════

    /**
     * attr：附加属性的三个子命令
     */
    private static void attr(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                             @Nonnull String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException(USAGE_KEY);
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add":
                addAttribute(server, sender, args);
                break;
            case "remove":
                removeAttribute(server, sender, args);
                break;
            case "clear":
                clearAttributes(server, sender, args);
                break;
            default:
                throw new WrongUsageException(USAGE_KEY);
        }
    }

    /**
     * attr add &lt;属性&gt; &lt;数值&gt; [运算] [饰品栏]：给手上的物品追加一条属性
     */
    private static void addAttribute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                     @Nonnull String[] args) throws CommandException {
        if (args.length < 4 || args.length > 6) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);

        String attributeName = readAttribute(player, args[2]);
        double amount = CommandBase.parseDouble(args[3]);
        int operation = args.length >= 5 ? readOperation(args[4]) : 0;
        String slotId = args.length >= 6 ? readSlot(args[5]) : BaubleMark.ANY;

        if (amount == 0) {
            throw new CommandException("command.battlecorrection.bauble.zero_amount");
        }

        UUID uuid = BaubleMark.newModifierUuid();
        BaubleMark.addModifier(stack, attributeName, amount, operation, slotId, uuid);

        notifySuccess(server, sender, "command.battlecorrection.bauble.attr_added",
                attributeText(attributeName),
                ItemStack.DECIMALFORMAT.format(amount),
                OPERATION_NAMES[operation],
                BaubleMark.slotName(slotId));

        if (!BaubleMark.isMarked(stack) && !BaubleMark.isNativeBauble(stack)) {
            // 属性已经写上了，但物品还放不进饰品栏，提醒一句免得以为没生效
            sendWarning(sender, new TextComponentTranslation("command.battlecorrection.bauble.attr_not_marked"));
        }

        LogUtil.info(String.format("%s 给物品 %s 追加了饰品属性: %s %s (%s, 饰品栏: %s)",
                player.getName(), stack.getDisplayName(), attributeName, amount, OPERATION_NAMES[operation], slotId));
    }

    /**
     * attr remove &lt;属性&gt; [饰品栏]：移除手上物品某个属性的全部条目
     */
    private static void removeAttribute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                        @Nonnull String[] args) throws CommandException {
        if (args.length < 3 || args.length > 4) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);

        // 移除不要求玩家身上有这个属性：属性被别的模组删掉之后，留在物品上的条目也得能清掉
        String attributeName = args[2];
        String slotId = args.length == 4 ? BaubleMark.normalizeSlotId(args[3]) : null;

        int removed = BaubleMark.removeModifiers(stack, attributeName, slotId);
        if (removed == 0) {
            throw new CommandException("command.battlecorrection.bauble.attr_not_found", attributeText(attributeName));
        }

        notifySuccess(server, sender, "command.battlecorrection.bauble.attr_removed",
                attributeText(attributeName), removed);
        LogUtil.info(String.format("%s 移除了物品 %s 的饰品属性: %s (%d 条)",
                player.getName(), stack.getDisplayName(), attributeName, removed));
    }

    /**
     * attr clear：清除手上物品的全部附加属性
     */
    private static void clearAttributes(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                        @Nonnull String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException(USAGE_KEY);
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        ItemStack stack = getHeldItem(player);

        if (!BaubleMark.clearModifiers(stack)) {
            throw new CommandException("command.battlecorrection.bauble.attr_none", ItemUtil.chatName(stack));
        }

        notifySuccess(server, sender, "command.battlecorrection.bauble.attr_cleared", ItemUtil.chatName(stack));
        LogUtil.info(String.format("%s 清除了物品 %s 的全部饰品属性",
                player.getName(), stack.getDisplayName()));
    }

    // ═══════════════════════════════════════════════════════════════
    // 公用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 物品本身就是饰品时提醒一句：标记改变不了它能放进哪个格子
     *
     * @param sender 执行者
     * @param stack  物品
     */
    private static void warnNativeBauble(@Nonnull ICommandSender sender, @Nonnull ItemStack stack) {
        if (BaubleMark.isNativeBauble(stack)) {
            sendWarning(sender, new TextComponentTranslation("command.battlecorrection.bauble.native_warning"));
        }
    }

    /**
     * 发一行黄色提示（只给执行者）
     *
     * @param sender  执行者
     * @param message 消息
     */
    private static void sendWarning(@Nonnull ICommandSender sender, @Nonnull ITextComponent message) {
        message.getStyle().setColor(TextFormatting.YELLOW);
        sender.sendMessage(message);
    }

    /**
     * 成功消息：和原版命令一样发给执行者，并通知其他管理员
     * <p>
     * 原版的通知要传命令实例（拿它判断其他管理员有没有权限），这里从命令表里找根命令；
     * 找不到（理论上不会）时只发给执行者。
     *
     * @param server 服务器
     * @param sender 执行者
     * @param key    翻译键
     * @param args   参数
     */
    private static void notifySuccess(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                      @Nonnull String key, @Nonnull Object... args) {
        ICommand root = server.getCommandManager().getCommands().get(ROOT_COMMAND);
        if (root != null) {
            CommandBase.notifyCommandListener(sender, root, key, args);
        } else {
            sender.sendMessage(new TextComponentTranslation(key, args));
        }
    }

    /**
     * 检查总开关
     *
     * @throws CommandException 功能已关闭
     */
    private static void checkEnabled() throws CommandException {
        if (!ConfigBaubles.baubleMarkEnabled) {
            throw new CommandException("command.battlecorrection.bauble.disabled");
        }
    }

    /**
     * 获取玩家主手物品
     *
     * @param player 玩家
     * @return 主手物品
     * @throws CommandException 空手
     */
    @Nonnull
    private static ItemStack getHeldItem(@Nonnull EntityPlayer player) throws CommandException {
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.isEmpty()) {
            throw new CommandException("command.battlecorrection.bauble.empty_hand");
        }
        return stack;
    }

    /**
     * 读取饰品栏参数（转小写，trinket 当成 any），必须是认识的饰品栏
     *
     * @param arg 参数
     * @return 饰品栏ID
     * @throws CommandException 不认识的饰品栏
     */
    @Nonnull
    private static String readSlot(@Nonnull String arg) throws CommandException {
        String slotId = BaubleMark.normalizeSlotId(arg);
        if (!BaubleMark.isKnownSlot(slotId)) {
            throw new CommandException("command.battlecorrection.bauble.unknown_slot", arg);
        }
        return slotId;
    }

    /**
     * 读取运算方式参数
     *
     * @param arg 参数
     * @return 运算方式序号
     * @throws CommandException 非法值
     */
    private static int readOperation(@Nonnull String arg) throws CommandException {
        String value = arg.toLowerCase(Locale.ROOT);
        for (int i = 0; i < OPERATION_NAMES.length; i++) {
            if (OPERATION_NAMES[i].equals(value)) {
                return i;
            }
        }
        throw new CommandException("command.battlecorrection.bauble.bad_operation", String.join(" / ", OPERATION_NAMES));
    }

    /**
     * 读取属性参数：执行者身上必须有这个属性
     *
     * @param player 执行者
     * @param arg    参数
     * @return 属性的正式名字
     * @throws CommandException 没有这个属性
     */
    @Nonnull
    private static String readAttribute(@Nonnull EntityPlayer player, @Nonnull String arg) throws CommandException {
        IAttributeInstance instance = player.getAttributeMap().getAttributeInstanceByName(arg);
        if (instance == null) {
            throw new CommandException("command.battlecorrection.bauble.unknown_attribute", arg);
        }
        return instance.getAttribute().getName();
    }

    /**
     * 属性的显示名（按客户端语言翻译）
     *
     * @param attributeName 属性名
     * @return 可翻译的名称
     */
    @Nonnull
    private static ITextComponent attributeText(@Nonnull String attributeName) {
        return new TextComponentTranslation("attribute.name." + attributeName);
    }

    // ═══════════════════════════════════════════════════════════════
    // 补全
    // ═══════════════════════════════════════════════════════════════

    /**
     * 饰品栏补全：第一项固定是 any，后面按 Baubles 界面里的格子顺序
     *
     * @return 补全候选
     */
    @Nonnull
    private static List<String> allSlots() {
        List<String> slots = new ArrayList<>(BaubleMark.SLOT_IDS.size() + 1);
        slots.add(BaubleMark.ANY);
        slots.addAll(BaubleMark.SLOT_IDS);
        return slots;
    }

    /**
     * 饰品栏补全：只列出手上物品已有的标记
     *
     * @param sender 执行者
     * @return 补全候选
     */
    @Nonnull
    private static List<String> markedSlots(@Nonnull ICommandSender sender) {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) {
            return Collections.emptyList();
        }
        ItemStack stack = ((EntityPlayer) sender.getCommandSenderEntity()).getHeldItemMainhand();
        List<String> marked = new ArrayList<>();
        for (String slotId : BaubleMark.getSlots(stack)) {
            if (!marked.contains(slotId)) {
                marked.add(slotId);
            }
        }
        return marked;
    }

    /**
     * 属性补全：执行者身上的全部属性（按名字排序）
     *
     * @param sender 执行者
     * @return 补全候选
     */
    @Nonnull
    private static List<String> playerAttributes(@Nonnull ICommandSender sender) {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (IAttributeInstance instance : ((EntityPlayer) sender.getCommandSenderEntity()).getAttributeMap().getAllAttributes()) {
            names.add(instance.getAttribute().getName());
        }
        Collections.sort(names);
        return names;
    }

    /**
     * 属性补全：只列出手上物品已有的附加属性
     *
     * @param sender 执行者
     * @return 补全候选
     */
    @Nonnull
    private static List<String> ownAttributes(@Nonnull ICommandSender sender) {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) {
            return Collections.emptyList();
        }
        ItemStack stack = ((EntityPlayer) sender.getCommandSenderEntity()).getHeldItemMainhand();
        List<String> names = new ArrayList<>();
        for (BaubleMark.ModifierEntry entry : BaubleMark.getModifiers(stack)) {
            if (!names.contains(entry.attributeName())) {
                names.add(entry.attributeName());
            }
        }
        return names;
    }

    /**
     * 根命令下认的子命令词：bauble，以及兼容 1.20.1 习惯的 curio
     *
     * @return 子命令词列表
     */
    @Nonnull
    public static List<String> subcommandWords() {
        return Arrays.asList("bauble", "curio");
    }
}
