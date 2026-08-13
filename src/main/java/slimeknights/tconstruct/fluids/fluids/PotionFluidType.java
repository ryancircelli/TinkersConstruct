package slimeknights.tconstruct.fluids.fluids;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.tconstruct.fluids.TinkerFluids;

import java.util.Objects;

/**
 * Fluid type for the molten potion fluid, which carries its potion as a {@link DataComponents#POTION_CONTENTS}.
 * <p>
 * 1.20 stored the potion as a {@code Potion} string inside the fluid's NBT and read it back with {@code PotionUtils}.
 * Both are gone: a fluid stack carries data components exactly as an item stack does, and the potion component is the
 * same one a vanilla potion bottle uses. That means a molten potion and the bottle it came from now agree on their
 * storage byte for byte, which is what makes Mantle's potion transfer a component copy rather than a translation.
 * <p>
 * It extends {@link FluidType} directly rather than Mantle's {@code TextureFluidType}, which is the *only* thing
 * keeping the client registration unambiguous. {@code FluidType#initializeClient} is deprecated in favour of
 * {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent}, and Mantle's listener claims
 * every {@code TextureFluidType} in the registry - registering a type twice throws, and listener order between two
 * mods is not something either can rely on. Being a plain {@link FluidType} takes this one out of Mantle's loop, so
 * {@code FluidClientEvents} is the only registration and the ordering question does not arise. The textures still come
 * from the fluid texture manager: {@code ClientPotionFluidType} extends Mantle's client class (M7 SS9).
 */
public class PotionFluidType extends FluidType {
  public PotionFluidType(Properties properties) {
    super(properties);
  }

  @Override
  public String getDescriptionId(FluidStack stack) {
    // Potion#getName is static and takes the optional holder in 1.21; its empty branch produces
    // "item.minecraft.potion.effect.empty", which is what this type's own description id already was
    return Potion.getName(stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion(), "item.minecraft.potion.effect.");
  }

  @Override
  public ItemStack getBucket(FluidStack fluidStack) {
    ItemStack itemStack = new ItemStack(fluidStack.getFluid().getBucket());
    itemStack.applyComponents(fluidStack.getComponentsPatch());
    return itemStack;
  }

  /** Creates the component patch holding the given potion */
  private static DataComponentPatch potionPatch(Holder<Potion> potion) {
    return DataComponentPatch.builder().set(DataComponents.POTION_CONTENTS, new PotionContents(potion)).build();
  }

  /** Creates a fluid stack for the given potion */
  public static FluidStack potionFluid(Holder<Potion> potion, int size) {
    return new FluidStack(TinkerFluids.potion.get().builtInRegistryHolder(), size, potionPatch(potion));
  }

  /** Creates a fluid output for the given potion */
  public static FluidOutput potionResult(Holder<Potion> potion, int size) {
    return FluidOutput.fromTag(Objects.requireNonNull(TinkerFluids.potion.getCommonTag()), size, potionPatch(potion));
  }

  /** Creates a potion bucket for the given potion */
  public static ItemStack potionBucket(Holder<Potion> potion) {
    ItemStack stack = new ItemStack(TinkerFluids.potion);
    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
    return stack;
  }
}
