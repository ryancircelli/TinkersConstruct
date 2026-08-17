package slimeknights.tconstruct.library.modifiers.hook.behavior;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * Modifier hook for adding attributes to a tool when in the correct slot.
 */
public interface AttributesModifierHook {
  /**
   * IDs for armor attributes on held tools, indexed by {@link EquipmentSlot#getIndex()} of the two hand slots.
   * @apiNote Replaces the pair of UUIDs used before 1.21. An {@link AttributeModifier} is identified by a {@link ResourceLocation} now, and the two
   * are not convertible, so these are new names rather than the old UUIDs rendered differently. An attribute modifier saved by an older world is
   * dropped by vanilla's own component reader, so there is nothing to stay compatible with.
   */
  ResourceLocation[] HELD_ARMOR_ID = { TConstruct.getResource("held_armor.mainhand"), TConstruct.getResource("held_armor.offhand") };

  /**
   * Adds attributes from this modifier's effect. Called whenever the item stack refreshes attributes, typically on equipping and unequipping.
   * It is important that you return the same list when equipping and unequipping the item.
   * <br>
   * Alternatives:
   * <ul>
   *   <li>{@link ToolStatsModifierHook}: Limited context, but can affect durability, mining level, and mining speed.</li>
   * </ul>
   * @param tool      Current tool instance
   * @param modifier  Modifier level
   * @param slot      Slot for the attributes
   * @param consumer  Attribute consumer
   */
  void addAttributes(IToolStackView tool, ModifierEntry modifier, EquipmentSlot slot, BiConsumer<Holder<Attribute>,AttributeModifier> consumer);

  /**
   * Gets attribute modifiers for a weapon with melee capability
   * @param tool  Tool instance
   * @param slot  Held slot
   * @return  Map of attribute modifiers
   */
  static Multimap<Holder<Attribute>,AttributeModifier> getHeldAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
    ImmutableMultimap.Builder<Holder<Attribute>, AttributeModifier> builder = ImmutableMultimap.builder();
    if (!tool.isBroken()) {
      // base melee stats - skip if not melee
      StatsNBT statsNBT = tool.getStats();
      if (slot == EquipmentSlot.MAINHAND && EntityInteractionModifierHook.isMeleeWeapon(tool)) {
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, statsNBT.get(ToolStats.ATTACK_DAMAGE), AttributeModifier.Operation.ADD_VALUE));
        // base attack speed is 4, but our numbers start from 4
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, statsNBT.get(ToolStats.ATTACK_SPEED) - 4d, AttributeModifier.Operation.ADD_VALUE));
      }

      if (slot.getType() == Type.HAND) {
        // shields and slimestaffs can get armor
        if (tool.hasTag(TinkerTags.Items.ARMOR)) {
          ResourceLocation id = HELD_ARMOR_ID[slot.getIndex()];
          double value = statsNBT.get(ToolStats.ARMOR);
          if (value != 0) {
            builder.put(Attributes.ARMOR, new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
          }
          value = statsNBT.get(ToolStats.ARMOR_TOUGHNESS);
          if (value != 0) {
            builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
          }
          value = statsNBT.get(ToolStats.KNOCKBACK_RESISTANCE);
          if (value != 0) {
            builder.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
          }
        }

        // grab attributes from modifiers, only do for hands (other slots would just be weird)
        BiConsumer<Holder<Attribute>,AttributeModifier> attributeConsumer = builder::put;
        for (ModifierEntry entry : tool.getModifierList()) {
          entry.getHook(ModifierHooks.ATTRIBUTES).addAttributes(tool, entry, slot, attributeConsumer);
        }
      }
    }
    return builder.build();
  }

  /** Merger that runs all hooks */
  record AllMerger(Collection<AttributesModifierHook> modules) implements AttributesModifierHook {
    @Override
    public void addAttributes(IToolStackView tool, ModifierEntry modifier, EquipmentSlot slot, BiConsumer<Holder<Attribute>,AttributeModifier> consumer) {
      for (AttributesModifierHook module : modules) {
        module.addAttributes(tool, modifier, slot, consumer);
      }
    }
  }
}
