package slimeknights.tconstruct.library.tools.item.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbility;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.DurabilityDisplayModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.SlotStackModifierHook;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModifiableArmorItem extends ArmorItem implements IModifiableDisplay {
  /** Volatile modifier tag to make piglins neutal when worn */
  public static final ResourceLocation PIGLIN_NEUTRAL = TConstruct.getResource("piglin_neutral");
  /** Volatile modifier tag to make this item an elytra */
  public static final ResourceLocation ELYTRA = TConstruct.getResource("elyta");
  /** Volatile flag for a boot item to walk on powdered snow. Cold immunity is handled through a tag */
  public static final ResourceLocation SNOW_BOOTS = TConstruct.getResource("snow_boots");
  /** Volatile flag for an item to act as an enderman mask, stopping them from getting angry. */
  public static final ResourceLocation ENDERMASK = TConstruct.getResource("endermask");
  /**
   * IDs for this armor's own attributes, indexed by {@link ArmorItem.Type#ordinal()}.
   * @apiNote Replaces {@code ArmorItem.ARMOR_MODIFIER_UUID_PER_TYPE}, which is gone with the rest of the UUIDs. One id
   * per slot covers all three armor attributes, as vanilla does the same with {@code armor.chestplate} and friends: an
   * id only has to be unique within an attribute.
   */
  private static final ResourceLocation[] ARMOR_ATTRIBUTE_ID =
    Arrays.stream(ArmorItem.Type.values()).map(type -> TConstruct.getResource("armor." + type.getName())).toArray(ResourceLocation[]::new);

  @Getter
  private final ToolDefinition toolDefinition;
  /** Cache of the tool built for rendering */
  private ItemStack toolForRendering = null;

  public ModifiableArmorItem(DummyArmorMaterial materialIn, ArmorItem.Type type, Properties builderIn, ToolDefinition toolDefinition) {
    super(materialIn.getMaterial(), type, IModifiable.damageable(builderIn));
    this.toolDefinition = toolDefinition;
  }

  public ModifiableArmorItem(ModifiableArmorMaterial material, ArmorItem.Type type, Properties properties) {
    this(material, type, properties, Objects.requireNonNull(material.getArmorDefinition(type), "Missing tool definition for " + type.getName()));
  }

  /* Basic properties */

  @Override
  public int getMaxStackSize(ItemStack stack) {
    return 1;
  }

  @Override
  public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
    return ModifierUtil.checkVolatileFlag(stack, PIGLIN_NEUTRAL);
  }

  @Override
  public boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
    return type == Type.BOOTS && ModifierUtil.checkVolatileFlag(stack, SNOW_BOOTS);
  }

  @Override
  public boolean isEnderMask(ItemStack stack, Player player, EnderMan endermanEntity) {
    return type == Type.HELMET && ModifierUtil.checkVolatileFlag(stack, ENDERMASK);
  }

  @Override
  public boolean canPerformAction(ItemStack stack, ItemAbility toolAction) {
    return ModifierUtil.canPerformAction(ToolStack.from(stack), toolAction);
  }

  @Override
  public boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
    return true;
  }


  /* Enchantments */

  @Override
  public boolean isEnchantable(ItemStack stack) {
    return false;
  }

  @Override
  public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
    return false;
  }

  @Override
  public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
    // replaces canApplyAtEnchantingTable; a curse is a tag now rather than a flag on the enchantment
    return enchantment.is(EnchantmentTags.CURSE) && super.supportsEnchantment(stack, enchantment);
  }

  @Override
  public int getEnchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
    return EnchantmentModifierHook.getEnchantmentLevel(stack, enchantment);
  }

  @Override
  public ItemEnchantments getAllEnchantments(ItemStack stack, RegistryLookup<Enchantment> lookup) {
    return EnchantmentModifierHook.getAllEnchantments(stack);
  }


  /* Loading */

  @Override
  public void verifyComponentsAfterLoad(ItemStack stack) {
    ToolStack.verifyComponents(stack, getToolDefinition());
  }

  @Override
  public void onCraftedBy(ItemStack stack, Level levelIn, Player playerIn) {
    ToolStack.ensureInitialized(stack, getToolDefinition());
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level levelIn, Player playerIn, InteractionHand handIn) {
    if (playerIn.isCrouching()) {
      ItemStack stack = playerIn.getItemInHand(handIn);
      InteractionResult result = ToolInventoryCapability.tryOpenContainer(stack, null, getToolDefinition(), playerIn, Util.getSlotType(handIn));
      if (result.consumesAction()) {
        return new InteractionResultHolder<>(result, stack);
      }
    }
    return super.use(levelIn, playerIn, handIn);
  }


  /* Display */

  @Override
  public boolean isFoil(ItemStack stack) {
    // we use enchantments to handle some modifiers, so don't glow from them
    // however, if a modifier wants to glow let them
    return ModifierUtil.checkVolatileFlag(stack, SHINY);
  }


  /* Indestructible items */

  @Override
  public boolean hasCustomEntity(ItemStack stack) {
    return IndestructibleItemEntity.hasCustomEntity(stack);
  }

  @Nullable
  @Override
  public Entity createEntity(Level level, Entity original, ItemStack stack) {
    return IndestructibleItemEntity.createFrom(level, original, stack);
  }


  /* Damage/Durability */

  @Override
  public boolean isRepairable(ItemStack stack) {
    // handle in the tinker station
    return false;
  }

  @Override
  public int getMaxDamage(ItemStack stack) {
    return ToolDamageUtil.getFakeMaxDamage(stack);
  }

  @Override
  public int getDamage(ItemStack stack) {
    return ToolStack.from(stack).getDamage();
  }

  @Override
  public void setDamage(ItemStack stack, int damage) {
    ToolStack tool = ToolStack.mutable(stack);
    tool.setDamage(damage);
    tool.updateStack();
  }

  @Override
  public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T damager, Consumer<Item> onBroken) {
    // We basically emulate ItemStack.hurtAndBreak here. We always return 0 to skip the handling in ItemStack.
    // If we don't tools ignore our damage logic
    ToolDamageUtil.handleDamageItem(stack, amount, damager, onBroken);
    return 0;
  }


  /* Durability display */

  @Override
  public boolean isBarVisible(ItemStack pStack) {
    return DurabilityDisplayModifierHook.showDurabilityBar(pStack);
  }

  @Override
  public int getBarColor(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityRGB(pStack);
  }

  @Override
  public int getBarWidth(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityWidth(pStack);
  }


  /* Armor properties */

  @Override
  public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
    return false;
  }


  @Override
  public Multimap<Holder<Attribute>,AttributeModifier> getAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
    if (slot != getEquipmentSlot()) {
      return ImmutableMultimap.of();
    }

    ImmutableMultimap.Builder<Holder<Attribute>,AttributeModifier> builder = ImmutableMultimap.builder();
    if (!tool.isBroken()) {
      // base stats
      StatsNBT statsNBT = tool.getStats();
      ResourceLocation id = ARMOR_ATTRIBUTE_ID[type.ordinal()];
      float armor = statsNBT.get(ToolStats.ARMOR);
      if (armor > 0) {
        builder.put(Attributes.ARMOR, new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE));
      }
      float toughness = statsNBT.get(ToolStats.ARMOR_TOUGHNESS);
      if (toughness > 0) {
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE));
      }
      double knockbackResistance = statsNBT.get(ToolStats.KNOCKBACK_RESISTANCE);
      if (knockbackResistance > 0) {
        builder.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, knockbackResistance, AttributeModifier.Operation.ADD_VALUE));
      }
      // grab attributes from modifiers
      BiConsumer<Holder<Attribute>,AttributeModifier> attributeConsumer = builder::put;
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.ATTRIBUTES).addAttributes(tool, entry, slot, attributeConsumer);
      }
    }

    return builder.build();
  }

  @Override
  public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
    // an armor piece that never computed its stats grants nothing, the replacement for 1.20's null tag check.
    // ArmorItem's own answer is deliberately not consulted: the material behind it is all zeroes
    if (!ToolStack.isInitialized(stack)) {
      return ItemAttributeModifiers.EMPTY;
    }
    return buildAttributeModifiers(ToolStack.from(stack), getEquipmentSlot());
  }


  /* Elytra */

  @Override
  public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
    return type == Type.CHESTPLATE && !ToolDamageUtil.isBroken(stack) && ModifierUtil.checkVolatileFlag(stack, ELYTRA);
  }

  @Override
  public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
    if (getEquipmentSlot() == EquipmentSlot.CHEST) {
      ToolStack tool = ToolStack.mutable(stack);
      if (!tool.isBroken()) {
        // if any modifier says stop flying, stop flying
        for (ModifierEntry entry : tool.getModifierList()) {
          if (entry.getHook(ModifierHooks.ELYTRA_FLIGHT).elytraFlightTick(tool, entry, entity, flightTicks)) {
            tool.updateStack();
            return false;
          }
        }
        // damage the tool and keep flying
        if (!entity.level().isClientSide && (flightTicks + 1) % 20 == 0) {
          ToolDamageUtil.damageAnimated(tool, 1, entity, EquipmentSlot.CHEST);
        }
        tool.updateStack();
        return true;
      }
    }
    return false;
  }


  /* Ticking */

  @Override
  public void inventoryTick(ItemStack stack, Level levelIn, Entity entityIn, int itemSlot, boolean isSelected) {
    // don't care about non-living, they skip most tool context
    if (entityIn instanceof LivingEntity living) {
      ToolStack tool = ToolStack.mutable(stack);
      if (!levelIn.isClientSide) {
        tool.ensureHasData();
      }
      List<ModifierEntry> modifiers = tool.getModifierList();
      if (!modifiers.isEmpty()) {
        boolean isCorrectSlot = living.getItemBySlot(getEquipmentSlot()) == stack;
        // we pass in the stack for most custom context, but for the sake of armor its easier to tell them that this is the correct slot for effects
        for (ModifierEntry entry : modifiers) {
          entry.getHook(ModifierHooks.INVENTORY_TICK).onInventoryTick(tool, entry, levelIn, living, itemSlot, isSelected, isCorrectSlot, stack);
        }
      }
      // both ensureHasData and any of the hooks may have written, and neither says so, so the tick always commits
      tool.updateStack();
    }
  }

  @Override
  public boolean overrideStackedOnOther(ItemStack held, Slot slot, ClickAction action, Player player) {
    return SlotStackModifierHook.overrideStackedOnOther(held, slot, action, player) || super.overrideStackedOnOther(held, slot, action, player);
  }

  @Override
  public boolean overrideOtherStackedOnMe(ItemStack slotStack, ItemStack held, Slot slot, ClickAction action, Player player, SlotAccess access) {
    return SlotStackModifierHook.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access) || super.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access);
  }


  /* Tooltips */

  @Override
  public Component getName(ItemStack stack) {
    return ToolNameHook.getName(getToolDefinition(), stack);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    TooltipUtil.addInformation(this, stack, context.level(), tooltip, SafeClientAccess.getTooltipKey(), flag);
  }

  @Override
  public List<Component> getStatInformation(IToolStackView tool, ItemStack stack, @Nullable Player player, List<Component> tooltips, TooltipKey key, TooltipFlag tooltipFlag) {
    tooltips = TooltipUtil.getArmorStats(tool, player, tooltips, key, tooltipFlag);
    TooltipUtil.addAttributes(this, tool, player, tooltips, TooltipUtil.SHOW_ARMOR_ATTRIBUTES, getEquipmentSlot());
    return tooltips;
  }

  /* Display items */

  @Override
  public ItemStack getRenderTool() {
    if (toolForRendering == null) {
      toolForRendering = ToolBuildHandler.buildToolForRendering(this, this.getToolDefinition());
    }
    return toolForRendering;
  }
}
