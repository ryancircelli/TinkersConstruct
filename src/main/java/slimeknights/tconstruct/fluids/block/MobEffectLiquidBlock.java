package slimeknights.tconstruct.fluids.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import slimeknights.mantle.registration.deferred.FluidDeferredRegister;

import java.util.function.Function;
import java.util.function.Supplier;

/** Liquid block applying an effect to the entity inside it */
public class MobEffectLiquidBlock extends LiquidBlock {
  private final Supplier<MobEffectInstance> effect;
  // LiquidBlock takes the fluid rather than a supplier in 1.21, see BurningLiquidBlock for why resolving here is safe
  public MobEffectLiquidBlock(FlowingFluid fluid, Properties properties, Supplier<MobEffectInstance> effect) {
    super(fluid, properties);
    this.effect = effect;
  }

  @Override
  public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    if (entity.getFluidTypeHeight(fluid.getFluidType()) > 0 && entity instanceof LivingEntity living) {
      // 1.20 called setCurativeItems(empty list) here so milk and friends would not clear the effect. NeoForge dropped
      // curative items entirely in 1.21 with no successor, so the effect is simply added as-is.
      living.addEffect(this.effect.get());
    }
  }

  /** Creates a new block supplier */
  public static Function<Supplier<? extends FlowingFluid>, LiquidBlock> createEffect(MapColor color, int lightLevel, Supplier<MobEffectInstance> effect) {
    return fluid -> new MobEffectLiquidBlock(fluid.get(), FluidDeferredRegister.createProperties(color, lightLevel), effect);
  }
}
