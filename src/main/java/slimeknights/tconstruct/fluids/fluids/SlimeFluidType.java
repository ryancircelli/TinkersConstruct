package slimeknights.tconstruct.fluids.fluids;

import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.fluid.InvertedFluidType;
import slimeknights.mantle.fluid.TextureFluidType;
import slimeknights.tconstruct.common.TinkerTags;

/** Fluid Type that does not affect slimes */
public class SlimeFluidType extends TextureFluidType {
  public SlimeFluidType(Properties properties) {
    super(properties);
  }

  @Override
  public boolean canDrownIn(LivingEntity entity) {
    return !canDrown(entity);
  }

  /** Shared by both variants below, since they cannot share a superclass */
  static boolean canDrown(LivingEntity entity) {
    return entity.getType().is(TinkerTags.EntityTypes.SLIMES);
  }

  /**
   * Slime fluid whose in-world block flips its flowing texture.
   * <p>
   * 1.20 spelled this as an override of {@code initializeClient} on the outer class, so the variant could extend it and
   * change one client method. 1.21 deprecated that in favour of
   * {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent}, and Mantle's listener picks
   * the client extension by testing the type against {@code InvertedFluidType} and {@code TextureFluidType}, which are
   * siblings. So the variant extends the other sibling and repeats the four-line drown check, rather than inheriting
   * from {@link SlimeFluidType} and being handed the wrong client extension.
   */
  public static class Inverted extends InvertedFluidType {
    public Inverted(Properties properties) {
      super(properties);
    }

    @Override
    public boolean canDrownIn(LivingEntity entity) {
      return !canDrown(entity);
    }
  }
}
