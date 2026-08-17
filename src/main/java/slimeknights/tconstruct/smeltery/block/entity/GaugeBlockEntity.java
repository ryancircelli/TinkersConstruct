package slimeknights.tconstruct.smeltery.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import javax.annotation.Nullable;

/**
 * This class exists simply to allow us to have a block entity renderer for obsidian gauges. Though it is useful as a
 * cache for the capability to render.
 * <p>
 * Deliberately not a {@link slimeknights.tconstruct.library.utils.NeighborCapabilityCache}, unlike every other cached
 * neighbor in this package: this one never subscribed to invalidation in 1.20 either, it fetched once and reused the
 * result forever. Giving it a cache here would quietly fix a preexisting staleness bug rather than port the class, so
 * the fetch-once behavior is kept as it was and the gap is recorded instead.
 */
public class GaugeBlockEntity extends BlockEntity {
  @Nullable
  private IFluidHandler neighbor;
  /** Separate from a null neighbor, which is a position we looked at and found nothing on */
  private boolean fetched = false;
  public GaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  public GaugeBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.gauge.get(), pos, state);
  }

  /** Gets the neighbor fluid handler. Used mainly for rendering client side */
  public IFluidHandler getTank() {
    if (level == null) {
      return EmptyFluidHandler.INSTANCE;
    }
    // if we have not fetched the neighbor, fetch it
    if (!fetched) {
      fetched = true;
      Direction side = getBlockState().getValue(BlockStateProperties.FACING);
      neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK, getBlockPos().relative(side.getOpposite()), side);
    }
    // return tank or empty tank
    return neighbor != null ? neighbor : EmptyFluidHandler.INSTANCE;
  }
}
