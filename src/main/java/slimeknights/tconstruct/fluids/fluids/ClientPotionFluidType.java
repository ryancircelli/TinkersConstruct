package slimeknights.tconstruct.fluids.fluids;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.fluid.texture.ClientTextureFluidType;

/**
 * Client extensions for {@link PotionFluidType}, tinting the fluid with the colour of the potion it carries.
 * <p>
 * 1.20 spelled this as an anonymous {@code ClientTextureFluidType} handed back from
 * {@code PotionFluidType#initializeClient}. That method is deprecated and unused in 1.21: client extensions are
 * registered against {@link RegisterClientExtensionsEvent}, so the class is a client-only file of its own and
 * {@code FluidClientEvents} installs it. See {@code FluidClientEvents#registerClientExtensions} for why the
 * registration is conditional.
 */
@OnlyIn(Dist.CLIENT)
public class ClientPotionFluidType extends ClientTextureFluidType {
  public ClientPotionFluidType(FluidType type) {
    super(type);
  }

  /**
   * Gets the colour of the contained potion, matching the tint vanilla gives a potion bottle.
   * <p>
   * The 1.20 form read {@code CustomPotionColor} out of NBT by hand and fell back to mixing the effect colours.
   * {@link PotionContents#getColor()} is that whole calculation, custom colour included. A stack with no potion
   * component keeps the fluid's own texture colour, which is what the old {@code Potions.EMPTY} branch did.
   * @param stack  Fluid stack instance
   * @return  Colour for the fluid
   */
  @Override
  public int getTintColor(FluidStack stack) {
    PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
    if (contents == null) {
      return getTintColor();
    }
    return contents.getColor() | 0xFF000000;
  }
}
