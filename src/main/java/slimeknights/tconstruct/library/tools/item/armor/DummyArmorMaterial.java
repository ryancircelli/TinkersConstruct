package slimeknights.tconstruct.library.tools.item.armor;

import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.registration.object.IdAwareObject;

import java.util.List;
import java.util.Map;

/**
 * The zeroed armor material a modifiable armor item is built on, since Tinkers computes every number the material used
 * to answer.
 * <p>
 * In 1.20 this implemented {@code ArmorMaterial} and answered zero to all of it. 1.21 turned {@link ArmorMaterial} into
 * a record in {@link net.minecraft.core.registries.BuiltInRegistries#ARMOR_MATERIAL}, and {@link ArmorItem} takes a
 * {@link Holder} of one, so there is no interface left to implement. This class became the thing that holds such a
 * holder, keeping the identity - {@link IdAwareObject} - that {@code ModifiableArmorMaterial} and the armor model
 * dispatcher already used it for.
 * <p>
 * The holder is {@link Holder#direct} rather than a registered entry. Registering would mean a
 * {@code DeferredRegister} in the mod's registration code, an id for every armor material an addon invents, and a
 * datapack-visible entry for a record that is all zeroes; nothing repays that. A direct holder is safe here because
 * every consumer of the material either goes through a method the modifiable armor item overrides
 * ({@code getDefaultAttributeModifiers}, {@code getEnchantmentValue}, {@code isValidRepairItem}) or reads a field of the
 * record itself ({@code Equipable#getEquipSound}, the armor layer renderer). The two places vanilla compares a material
 * by registry identity - the piglin gold check and armor trim datagen - are identity comparisons that correctly answer
 * no for a direct holder.
 * <p>
 * {@link ArmorMaterial#layers()} being empty is what removes the dummy armor texture 1.20 needed: with no layers,
 * {@code HumanoidArmorLayer} renders nothing at all and Tinkers' own armor model is the only thing drawn.
 */
@Getter
public class DummyArmorMaterial implements IdAwareObject {
  private final ResourceLocation id;
  /** Holder wrapping this material, for passing to {@link ArmorItem} */
  private final Holder<ArmorMaterial> material;

  public DummyArmorMaterial(ResourceLocation id, SoundEvent equipSound) {
    this.id = id;
    // the sound is direct for the same reason the material is: it comes from a registry that is not frozen when armor
    // materials are created, and the sound packet writes a direct holder inline
    this.material = Holder.direct(new ArmorMaterial(Map.of(), 0, Holder.direct(equipSound), () -> Ingredient.EMPTY, List.of(), 0, 0));
  }

  /** Gets the equip sound for this material */
  public SoundEvent getEquipSound() {
    return material.value().equipSound().value();
  }

  /** @deprecated use {@link #getId()}, the material's name is a {@link ResourceLocation} now that nothing forces it through a string */
  @Deprecated(forRemoval = true)
  public String getName() {
    return id.toString();
  }
}
