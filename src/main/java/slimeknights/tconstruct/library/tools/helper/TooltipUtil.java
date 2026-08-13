package slimeknights.tconstruct.library.tools.helper;

import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.common.NeoForge;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolMaterialHook;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.item.ITinkerStationDisplay;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolComponents;
import slimeknights.tconstruct.library.tools.nbt.ToolDataComponent;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.tools.TinkerToolActions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiPredicate;

/** Helper functions for adding tooltips to tools */
public class TooltipUtil {
  /** Translation key for the tool name format string */
  public static final String KEY_FORMAT = TConstruct.makeTranslationKey("item", "tool.format");
  /** Format for a name ID pair */
  public static final String KEY_ID_FORMAT = TConstruct.makeTranslationKey("item", "tool.id_format");

  /** Function to show all attributes in the tooltip */
  public static final BiPredicate<Holder<Attribute>,Operation> SHOW_ALL_ATTRIBUTES = (att, op) -> true;
  /** Function to show all attributes in the tooltip */
  public static final BiPredicate<Holder<Attribute>,Operation> SHOW_MELEE_ATTRIBUTES = (att, op) -> op != Operation.ADD_VALUE || !(att.is(Attributes.ATTACK_DAMAGE) || att.is(Attributes.ATTACK_SPEED) || att.is(Attributes.ARMOR) || att.is(Attributes.ARMOR_TOUGHNESS) || att.is(Attributes.KNOCKBACK_RESISTANCE));
  /** Function to show all attributes in the tooltip */
  public static final BiPredicate<Holder<Attribute>,Operation> SHOW_ARMOR_ATTRIBUTES = (att, op) -> op != Operation.ADD_VALUE || !(att.is(Attributes.ARMOR) || att.is(Attributes.ARMOR_TOUGHNESS) || att.is(Attributes.KNOCKBACK_RESISTANCE));

  private TooltipUtil() {}

  /** Tooltip telling the player to hold shift for more info */
  public static final Component TOOLTIP_HOLD_SHIFT = TConstruct.makeTranslation("tooltip", "hold_shift", TConstruct.makeTranslation("key", "shift").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
  /** Tooltip telling the player to hold control for part info */
  public static final Component TOOLTIP_HOLD_CTRL = TConstruct.makeTranslation("tooltip", "hold_ctrl", TConstruct.makeTranslation("key", "ctrl").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
  /** Tooltip for when tool data is missing */
  private static final Component NO_DATA = TConstruct.makeTranslation("tooltip", "missing_data").withStyle(ChatFormatting.GRAY);
  /** Tooltip for when a tool is uninitialized */
  private static final Component UNINITIALIZED = TConstruct.makeTranslation("tooltip", "uninitialized").withStyle(ChatFormatting.GRAY);
  /** Extra tooltip for multipart tools with no materials */
  private static final Component RANDOM_MATERIALS = TConstruct.makeTranslation("tooltip", "random_materials").withStyle(ChatFormatting.GRAY);

  /**
   * Registers the tooltip listeners this class owns. Must be called during mod construction, beside
   * {@link ModifierLootingHandler#init()}.
   */
  public static void init() {
    NeoForge.EVENT_BUS.addListener(TooltipUtil::onGatherSkippedAttributes);
  }

  /**
   * If true, this stack was created for display, so some of the tooltip is suppressed
   * @param stack  Stack to check
   * @return  True if marked display
   */
  public static boolean isDisplay(ItemStack stack) {
    return stack.has(ToolComponents.DISPLAY);
  }

  /** Marks the given stack as a display stack, see {@link ToolComponents#DISPLAY} */
  public static void setDisplay(ItemStack stack) {
    stack.set(ToolComponents.DISPLAY, Unit.INSTANCE);
  }

  /**
   * Sets the tool name in a way that will not be italic, passing null to clear it.
   * @apiNote  This is {@code minecraft:item_name} now, and takes the {@link Component} that component holds rather than
   * the string the 1.20 {@code tic_name} tag did. 1.20 needed its own key because the only vanilla name was
   * {@code CUSTOM_NAME}, which the tooltip italicises; 1.21 added {@code ITEM_NAME} for exactly this, and
   * {@link ItemStack#getHoverName()} falls back to it while the italic branch still checks only {@code CUSTOM_NAME}.
   * A component rather than a string because that is what the value is: the one round trip in the codebase reads a
   * name off one stack and writes it to another, which is lossless this way and lossy through {@code Component#getString}.
   * Null rather than the empty string for "no name", as the empty string conflated absent with named-empty.
   */
  public static void setDisplayName(ItemStack tool, @Nullable Component name) {
    if (name == null) {
      tool.remove(DataComponents.ITEM_NAME);
    } else {
      tool.set(DataComponents.ITEM_NAME, name);
    }
  }

  /** Gets the name set by {@link #setDisplayName(ItemStack, Component)}, or null if this tool has no name override */
  @Nullable
  public static Component getDisplayName(ItemStack tool) {
    return tool.get(DataComponents.ITEM_NAME);
  }

  /**
   * Gets the display name for a tool including the head material in the name
   * @param stack           Stack instance
   * @param toolDefinition  Tool definition
   * @return  Display name including the head material
   * @deprecated call using {@link ToolNameHook#getName(ToolDefinition, ItemStack)}.
   */
  @Deprecated(forRemoval = true)
  public static Component getDisplayName(ItemStack stack, ToolDefinition toolDefinition) {
    return ToolNameHook.getName(toolDefinition, stack);
  }

  /**
   * Gets the display name for a tool including the head material in the name
   * @param stack  Stack instance
   * @param tool   Tool instance
   * @return  Display name including the head material
   * @deprecated call using {@link ToolNameHook#getName(ToolDefinition, ItemStack)}.
   */
  @Deprecated(forRemoval = true)
  public static Component getDisplayName(ItemStack stack, @Nullable IToolStackView tool, ToolDefinition toolDefinition) {
    return ToolNameHook.getName(toolDefinition, stack, tool);
  }

  /** Replaces the world argument with the local player */
  public static void addInformation(IModifiableDisplay item, ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    Player player = world == null ? null : SafeClientAccess.getPlayer();
    TooltipUtil.addInformation(item, stack, player, tooltip, tooltipKey, tooltipFlag);
  }

  /**
   * Full logic for adding tooltip information, other than attributes
   */
  public static void addInformation(IModifiableDisplay item, ItemStack stack, @Nullable Player player, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    // if the display tag is set, just show modifiers
    ToolDefinition definition = item.getToolDefinition();
    if (isDisplay(stack)) {
      IToolStackView tool = ToolStack.from(stack);
      addModifierNames(tool, player, tooltip, tooltipFlag);
      // No definition?
    } else if (!definition.isDataLoaded()) {
      tooltip.add(NO_DATA);

      // if not initialized, show no data tooltip on non-standard items
    } else if (!ToolStack.isInitialized(stack)) {
      tooltip.add(UNINITIALIZED);
      if (definition.hasMaterials() && ToolDataComponent.get(stack).materials().isEmpty()) {
        tooltip.add(RANDOM_MATERIALS);
      }
    } else {
      switch (tooltipKey) {
        case SHIFT:
          item.getStatInformation(ToolStack.from(stack), stack, player, tooltip, tooltipKey, tooltipFlag);
          break;
        case CONTROL:
          if (definition.hasMaterials()) {
            getComponents(item, stack, tooltip, tooltipFlag);
            break;
          }
          // intentional fallthrough
        default:
          IToolStackView tool = ToolStack.from(stack);
          getDefaultInfo(tool, player, tooltip, tooltipFlag);
          break;
      }
    }
  }

  /**
   * Adds modifier names to the tooltip
   * @param tool       Tool instance
   * @param player     Player holding the tool
   * @param tooltips   Tooltip list
   * @param flag       Tooltip flag
   * @apiNote  Took the {@link ItemStack} before 1.21 so it could render the stack's vanilla enchantments here, in the
   * modifier list, with {@code DEFAULT_HIDE_FLAGS} suppressing vanilla's own enchantment section. Nothing suppresses
   * that section in 1.21 (see {@link #onGatherSkippedAttributes}), so rendering them here would duplicate every line; vanilla
   * runs instead, which the 1.20 code already wondered aloud whether it should. Nothing is lost - vanilla prints the
   * same names from the same component, just below our block rather than inside it.
   */
  public static void addModifierNames(IToolStackView tool, @Nullable Player player, List<Component> tooltips, TooltipFlag flag) {
    RegistryAccess access = player == null ? null : player.level().registryAccess();
    for (ModifierEntry entry : tool.getModifierList()) {
      if (entry.getModifier().shouldDisplay(false)) {
        Component name = entry.getModifier().getDisplayName(tool, entry, access);
        if (flag.isAdvanced() && Config.CLIENT.modifiersIDsInAdvancedTooltips.get()) {
          tooltips.add(Component.translatable(KEY_ID_FORMAT, name, Component.literal(entry.getModifier().getId().toString())).withStyle(ChatFormatting.DARK_GRAY));
        } else {
          tooltips.add(name);
        }
      }
    }
  }

  /**
   * Adds information when holding neither control nor shift
   * @param tool      Tool stack instance
   * @param player    Player holding the tool
   * @param tooltips  Tooltip list
   * @param flag      Tooltip flag
   */
  public static void getDefaultInfo(IToolStackView tool, @Nullable Player player, List<Component> tooltips, TooltipFlag flag) {
    // shows as broken when broken, hold shift for proper durability
    // Item#canBeDepleted is gone; the item's default components are where a damageable item declares its durability.
    // Not the stack's, as this only has the tool view, and not ItemStack#isDamageableItem, which folds in the
    // unbreakable check the next clause already makes.
    if (tool.getItem().components().has(DataComponents.MAX_DAMAGE) && !tool.isUnbreakable() && tool.hasTag(TinkerTags.Items.DURABILITY)) {
      tooltips.add(TooltipBuilder.formatDurability(tool.getCurrentDurability(), tool.getStats().getInt(ToolStats.DURABILITY), true));
    }
    // modifier tooltip
    addModifierNames(tool, player, tooltips, flag);
    tooltips.add(Component.empty());
    tooltips.add(TOOLTIP_HOLD_SHIFT);
    if (tool.getDefinition().hasMaterials()) {
      tooltips.add(TOOLTIP_HOLD_CTRL);
    }
  }

  /**
   * Gets the  default information for the given tool stack
   *
   * @param tool      the tool stack
   * @param tooltip   Tooltip list
   * @param flag      Tooltip flag
   * @return List from the parameter after filling
   */
  public static List<Component> getDefaultStats(IToolStackView tool, @Nullable Player player, List<Component> tooltip, TooltipKey key, TooltipFlag flag) {
    TooltipBuilder builder = new TooltipBuilder(tool, tooltip);
    if (tool.hasTag(TinkerTags.Items.DURABILITY)) {
      builder.addDurability();
    }
    boolean allowMelee = !EntityInteractionModifierHook.meleeDisabled(tool);
    boolean meleePrimary = allowMelee && tool.hasTag(TinkerTags.Items.MELEE_PRIMARY);
    if (meleePrimary) {
      builder.addWithAttribute(ToolStats.ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE);
      builder.add(ToolStats.ATTACK_SPEED);
    }
    if (tool.hasTag(TinkerTags.Items.RANGED)) {
      builder.add(ToolStats.DRAW_SPEED);
      builder.add(ToolStats.VELOCITY);
      if (tool.hasTag(TinkerTags.Items.LAUNCHERS)) {
        builder.add(ToolStats.PROJECTILE_DAMAGE);
      }
      builder.add(ToolStats.ACCURACY);
    }
    if (allowMelee && !meleePrimary && tool.hasTag(TinkerTags.Items.MELEE_WEAPON)) {
      builder.addWithAttribute(ToolStats.ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE);
      builder.add(ToolStats.ATTACK_SPEED);
    }
    if (tool.hasTag(TinkerTags.Items.HARVEST)) {
      if (tool.hasTag(TinkerTags.Items.HARVEST_PRIMARY)) {
        builder.addTier();
      }
      builder.add(ToolStats.MINING_SPEED);
    }
    // slimestaffs and shields are holdable armor, so show armor stats
    if (tool.hasTag(TinkerTags.Items.ARMOR)) {
      builder.addOptional(ToolStats.ARMOR);
      builder.addOptional(ToolStats.ARMOR_TOUGHNESS);
      builder.addOptional(ToolStats.KNOCKBACK_RESISTANCE, 10f);
    }
    if (ModifierUtil.canPerformAction(tool, TinkerToolActions.SHIELD_BLOCK)) {
      builder.add(ToolStats.BLOCK_AMOUNT);
      builder.add(ToolStats.BLOCK_ANGLE);
    }

    builder.addAllFreeSlots();
    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getHook(ModifierHooks.TOOLTIP).addTooltip(tool, entry, player, tooltip, key, flag);
    }
    return builder.getTooltips();
  }

  /**
   * Gets the armor information for the given tool stack
   *
   * @param tool      the tool stack
   * @param tooltip   Tooltip list
   * @param flag      Tooltip flag
   * @return List from the parameter after filling
   */
  public static List<Component> getArmorStats(IToolStackView tool, @Nullable Player player, List<Component> tooltip, TooltipKey key, TooltipFlag flag) {
    TooltipBuilder builder = new TooltipBuilder(tool, tooltip);
    if (tool.hasTag(TinkerTags.Items.DURABILITY)) {
      builder.addDurability();
    }
    if (tool.hasTag(TinkerTags.Items.ARMOR)) {
      builder.add(ToolStats.ARMOR);
      builder.addOptional(ToolStats.ARMOR_TOUGHNESS);
      builder.addOptional(ToolStats.KNOCKBACK_RESISTANCE, 10f);
    }
    if (tool.hasTag(TinkerTags.Items.UNARMED)) {
      builder.addWithAttribute(ToolStats.ATTACK_DAMAGE, Attributes.ATTACK_DAMAGE);
    }

    builder.addAllFreeSlots();

    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getHook(ModifierHooks.TOOLTIP).addTooltip(tool, entry, player, tooltip, key, flag);
    }
    return builder.getTooltips();
  }

  /**
   * Gets the ammo information for the given tool stack
   *
   * @param tool      the tool stack
   * @param tooltip   Tooltip list
   * @param flag      Tooltip flag
   * @return List from the parameter after filling
   */
  public static List<Component> getAmmoStats(IToolStackView tool, @Nullable Player player, List<Component> tooltip, TooltipKey key, TooltipFlag flag) {
    TooltipBuilder builder = new TooltipBuilder(tool, tooltip);
    builder.add(ToolStats.PROJECTILE_DAMAGE);
    builder.add(ToolStats.VELOCITY);
    builder.add(ToolStats.ACCURACY);
    builder.addAllFreeSlots();
    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getHook(ModifierHooks.TOOLTIP).addTooltip(tool, entry, player, tooltip, key, flag);
    }
    return builder.getTooltips();
  }


  /**
   * Gets the tooltip of the components list of a tool
   * @param item      Modifiable item instance
   * @param stack     Item stack being displayed
   * @param tooltips  List of tooltips
   * @param flag      Tooltip flag, if advanced will show material IDs
   */
  public static void getComponents(IModifiable item, ItemStack stack, List<Component> tooltips, TooltipFlag flag) {
    // no components, nothing to do
    ToolDefinition definition = item.getToolDefinition();
    ToolMaterialHook hook = definition.getHook(ToolHooks.TOOL_MATERIALS);
    List<MaterialStatsId> components = hook.getStatTypes(definition);
    if (components.isEmpty()) {
      return;
    }
    // no materials is bad
    MaterialNBT materials = ToolStack.from(stack).getMaterials();
    if (materials.isEmpty()) {
      tooltips.add(NO_DATA);
      return;
    }
    // wrong number is bad
    if (materials.size() < components.size()) {
      return;
    }
    // start by displaying all tool parts
    int max = components.size() - 1;
    List<IToolPart> parts = ToolPartsHook.parts(item.getToolDefinition());
    int partCount = parts.size();
    for (int i = 0; i <= max; i++) {
      MaterialVariantId material = materials.get(i).getVariant();
      // display tool parts as the tool part name, nicer to work with
      Component componentName;
      if (i < partCount) {
        componentName = parts.get(i).withMaterial(material).getHoverName();
      } else {
        componentName = Component.translatable(KEY_FORMAT, MaterialTooltipCache.getDisplayName(material), Component.translatable(Util.makeTranslationKey("stat", components.get(i))));
      }
      // underline it and color it with the material name
      tooltips.add(componentName.copy().withStyle(ChatFormatting.UNDERLINE).withStyle(style -> style.withColor(MaterialTooltipCache.getColor(material))));
      // material IDs on advanced
      if (flag.isAdvanced()) {
        tooltips.add((Component.literal(material.toString())).withStyle(ChatFormatting.DARK_GRAY));
      }
      // material stats
      float scale = hook.scaleStats(definition, i);
      MaterialRegistry.getInstance().getMaterialStats(material.getId(), components.get(i)).ifPresent(stat -> tooltips.addAll(stat.getLocalizedInfo(scale)));
      if (i != max) {
        tooltips.add(Component.empty());
      }
    }
  }

  /**
   * Adds attributes to the tooltip
   * @param item           Modifiable item instance
   * @param tool           Tool instance, primary source of info for the tool
   * @param player         Player instance
   * @param tooltip        Tooltip instance
   * @param showAttribute  Predicate to determine whether an attribute should show
   * @param slots          List of slots to display
   */
  public static void addAttributes(ITinkerStationDisplay item, IToolStackView tool, @Nullable Player player, List<Component> tooltip, BiPredicate<Holder<Attribute>,Operation> showAttribute, EquipmentSlot... slots) {
    for (EquipmentSlot slot : slots) {
      Multimap<Holder<Attribute>,AttributeModifier> modifiers = item.getAttributeModifiers(tool, slot);
      if (!modifiers.isEmpty()) {
        if (slots.length > 1) {
          tooltip.add(Component.empty());
          tooltip.add((Component.translatable("item.modifiers." + slot.getName())).withStyle(ChatFormatting.GRAY));
        }

        for (Entry<Holder<Attribute>,AttributeModifier> entry : modifiers.entries()) {
          Holder<Attribute> attribute = entry.getKey();
          AttributeModifier modifier = entry.getValue();
          Operation operation = modifier.operation();
          // allow suppressing specific attributes
          if (!showAttribute.test(attribute, operation)) {
            continue;
          }
          addAttribute(attribute.value(), operation, modifier.amount(), modifier.id(), player, tooltip);
        }
      }
    }
  }

  /**
   * Adds a single attribute to the tooltip
   * @param attribute  Attribute type
   * @param operation  Attribute operationm
   * @param amount     Attribute amount
   * @param id         Attribute modifier ID
   * @param player     Player instance
   * @param tooltip    Tooltip list
   * @apiNote  Identifies the modifier by {@link ResourceLocation} rather than {@code UUID}, as that is what an
   * {@link AttributeModifier} carries in 1.21. Still takes a bare {@link Attribute} rather than a holder: everything it
   * needs is on the value, and the modules that call it hold the attribute itself.
   */
  public static void addAttribute(Attribute attribute, Operation operation, double amount, @Nullable ResourceLocation id, @Nullable Player player, List<Component> tooltip) {
    // find value
    boolean showEquals = false;
    if (player != null && id != null) {
      if (id.equals(Item.BASE_ATTACK_DAMAGE_ID)) {
        amount += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        showEquals = true;
      } else if (id.equals(Item.BASE_ATTACK_SPEED_ID)) {
        amount += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
        showEquals = true;
      }
    }
    // some numbers display a bit different
    double displayValue = amount;
    if (operation == Operation.ADD_VALUE) {
      // vanilla multiplies knockback resist by 10 for some odd reason
      if (attribute == Attributes.KNOCKBACK_RESISTANCE.value()) {
        displayValue *= 10;
      }
    } else {
      // display multiply as percentage
      displayValue *= 100;
    }
    // final tooltip addition
    Component name = Component.translatable(attribute.getDescriptionId());
    if (showEquals) {
      tooltip.add(Component.literal(" ")
                           .append(Component.translatable("attribute.modifier.equals." + operation.id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(displayValue), name))
                           .withStyle(ChatFormatting.DARK_GREEN));
    } else if (amount > 0.0D) {
      tooltip.add((Component.translatable("attribute.modifier.plus." + operation.id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(displayValue), name))
                    .withStyle(ChatFormatting.BLUE));
    } else if (amount < 0.0D) {
      displayValue *= -1;
      tooltip.add((Component.translatable("attribute.modifier.take." + operation.id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(displayValue), name))
                    .withStyle(ChatFormatting.RED));
    }
  }

  /**
   * Checks whether we are drawing the tool's attribute modifiers ourselves for the current ctrl+shift combination,
   * meaning vanilla's own attribute section has to be suppressed. Client sensitive; on a server the tooltip key is
   * always {@link TooltipKey#NORMAL} so this is always false.
   * @see #onGatherSkippedAttributes(GatherSkippedAttributeTooltipsEvent)
   */
  public static boolean hidesAttributes(ToolDefinition definition) {
    TooltipKey key = SafeClientAccess.getTooltipKey();
    return key == TooltipKey.SHIFT || (key == TooltipKey.CONTROL && definition.hasMaterials());
  }

  /**
   * Suppresses vanilla's attribute modifier section on our tools whenever {@link #getDefaultStats} and friends are
   * drawing it themselves.
   * @apiNote  Replaces the {@code getModifierHideFlags} int mask and the {@code IForgeItem#getDefaultTooltipHideFlags}
   * override that consumed it, both of which are gone: 1.21 replaced {@code ItemStack.TooltipPart} with a
   * {@code showInTooltip} flag on each component, and NeoForge's attribute tooltip reads that flag off the stored
   * {@code minecraft:attribute_modifiers} component. Our tools compute their attributes rather than storing that
   * component, so there is no flag of ours for it to read; this event, which NeoForge fires from
   * {@code AttributeUtil#applyModifierTooltips} for exactly this purpose, is the only hook that can answer per stack
   * and per keypress the way the old mask did.
   * <p>
   * The enchantment half of the old mask has no equivalent at all - {@code ItemEnchantments#showInTooltip} is stored
   * on the component and our tools do not own theirs - so it is dropped; see {@link #addModifierNames}.
   */
  private static void onGatherSkippedAttributes(GatherSkippedAttributeTooltipsEvent event) {
    if (event.getStack().getItem() instanceof IModifiable modifiable && hidesAttributes(modifiable.getToolDefinition())) {
      event.setSkipAll(true);
    }
  }
}
