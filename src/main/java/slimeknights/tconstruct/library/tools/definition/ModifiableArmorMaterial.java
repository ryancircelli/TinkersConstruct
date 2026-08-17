package slimeknights.tconstruct.library.tools.definition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import slimeknights.tconstruct.library.tools.item.armor.DummyArmorMaterial;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Armor material that doubles as a container for tool definitions for each armor slot.
 * <p>
 * The per-slot definitions are an {@link EnumMap} keyed by {@link ArmorItem.Type}, which is also how vanilla's own
 * {@link net.minecraft.world.item.ArmorMaterial} record stores its per-slot defense. Until 1.21 this was a
 * four-length array indexed by {@link ArmorItem.Type#ordinal()}, and 1.21 added a fifth type, {@code BODY}, for the
 * animal armor slot: every {@code values()} loop then wrote past the end of the array and every lookup for the new
 * type read past it. A map has no length to get wrong, so a sixth type would cost this class nothing at all.
 */
public class ModifiableArmorMaterial extends DummyArmorMaterial {
  /**
   * The armor slots a Tinkers armor material covers, in the order the armor hooks walk them.
   * @apiNote This is deliberately the four humanoid slots and not {@link EquipmentSlot#BODY}. {@code BODY} is the
   * animal armor slot - wolf armor and horse armor - which nothing in Tinkers equips, and which is worn by an entity
   * that has no equipment change events, no modifier data and no armor model. Every hook that iterates this array is
   * asking "which pieces of Tinkers armor is this player wearing".
   */
  public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
  /** The armor types {@link #create(ResourceLocation, SoundEvent)} builds a definition for, the humanoid counterpart of {@link #ARMOR_SLOTS} */
  public static final ArmorItem.Type[] ARMOR_TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

  /** Map of slot type to tool definition for the slot, absent for a slot this material does not cover */
  private final Map<ArmorItem.Type,ToolDefinition> armorDefinitions;

  private ModifiableArmorMaterial(ResourceLocation id, SoundEvent equipSound, Map<ArmorItem.Type,ToolDefinition> armorDefinitions) {
    super(id, equipSound);
    if (armorDefinitions.isEmpty()) {
      throw new IllegalArgumentException("Must have an armor definition for at least one slot");
    }
    this.armorDefinitions = armorDefinitions;
  }

  /** Creates a modifiable armor material, creates tool definition for the selected slots */
  public static ModifiableArmorMaterial create(ResourceLocation id, SoundEvent equipSound, ArmorItem.Type... slots) {
    Map<ArmorItem.Type,ToolDefinition> definitions = new EnumMap<>(ArmorItem.Type.class);
    for (ArmorItem.Type slot : slots) {
      definitions.put(slot, ToolDefinition.create(id.withSuffix("_" + slot.getName())));
    }
    return new ModifiableArmorMaterial(id, equipSound, definitions);
  }

  /**
   * Creates a modifiable armor material, creates tool definition for all four humanoid armor slots.
   * @apiNote This used to pass {@link ArmorItem.Type#values()}, which is no longer the same list: 1.21 added
   * {@code BODY} for animal armor. The four types are named so that adding a fifth is an opt-in rather than something
   * every armor material in the game silently grows a definition for.
   */
  public static ModifiableArmorMaterial create(ResourceLocation id, SoundEvent equipSound) {
    return create(id, equipSound, ARMOR_TYPES);
  }

  /**
   * Gets the armor definition for the given armor slot, used in item construction
   * @param slotType  Slot type
   * @return  Armor definition, or null if this material does not cover that slot
   */
  @Nullable
  public ToolDefinition getArmorDefinition(ArmorItem.Type slotType) {
    return armorDefinitions.get(slotType);
  }
}
