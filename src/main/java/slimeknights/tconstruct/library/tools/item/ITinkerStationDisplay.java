package slimeknights.tconstruct.library.tools.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map.Entry;

/**
 * Interface to implement for tools that also display in the tinker station
 */
public interface ITinkerStationDisplay extends ItemLike {
  /**
   * The "title" displayed in the GUI
   */
  default Component getLocalizedName() {
    return Component.translatable(asItem().getDescriptionId());
  }

  /**
   * Returns the tool stat information for this tool
   * @param tool         Tool to display
   * @param stack        Stack being displayed, for the parts of a tool that live in a vanilla component rather than in
   *                     the tool. Not always the stack {@code tool} came from: the tinker station shows the tool a
   *                     recipe would produce next to the stack it would produce it on.
   * @param tooltips     List of tooltips for display
   * @param tooltipFlag  Determines the type of tooltip to display
   */
  default List<Component> getStatInformation(IToolStackView tool, ItemStack stack, @Nullable Player player, List<Component> tooltips, TooltipKey key, TooltipFlag tooltipFlag) {
    tooltips = TooltipUtil.getDefaultStats(tool, player, tooltips, key, tooltipFlag);
    TooltipUtil.addAttributes(this, tool, player, tooltips, TooltipUtil.SHOW_MELEE_ATTRIBUTES, EquipmentSlot.MAINHAND);
    return tooltips;
  }

  /**
   * Allows making attribute tooltips more efficient by not parsing the tool twice
   * @param tool   Tool to check for attributes
   * @param slot   Slot with attributes
   * @return  Attribute map
   */
  default Multimap<Holder<Attribute>,AttributeModifier> getAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
    return ImmutableMultimap.of();
  }

  /**
   * Collects {@link #getAttributeModifiers(IToolStackView, EquipmentSlot)} over the given slots into the value the
   * {@code minecraft:attribute_modifiers} component holds, for
   * {@code IItemExtension#getDefaultAttributeModifiers(ItemStack)}.
   * <p>
   * 1.21 asks an item for its attributes once rather than once per slot, tagging each entry with the
   * {@link EquipmentSlotGroup} it applies to, so the per-slot method stays the one an implementor writes and this
   * assembles the answer. The slots are passed rather than derived because which ones a tool answers for is the item's
   * business: a held tool answers for both hands with different values in each, a piece of armor for exactly one.
   * <p>
   * Named apart from {@link #getAttributeModifiers(IToolStackView, EquipmentSlot)} rather than overloading it: a
   * single slot argument would silently pick the multimap overload over the varargs one.
   * @param tool   Tool to read attributes from
   * @param slots  Slots this item grants attributes in
   * @return  Attribute modifiers component value
   */
  default ItemAttributeModifiers buildAttributeModifiers(IToolStackView tool, EquipmentSlot... slots) {
    ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
    for (EquipmentSlot slot : slots) {
      EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
      for (Entry<Holder<Attribute>,AttributeModifier> entry : getAttributeModifiers(tool, slot).entries()) {
        builder.add(entry.getKey(), entry.getValue(), group);
      }
    }
    return builder.build();
  }
}
