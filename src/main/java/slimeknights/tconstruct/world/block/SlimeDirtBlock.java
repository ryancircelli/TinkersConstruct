package slimeknights.tconstruct.world.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.neoforge.common.util.TriState;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class SlimeDirtBlock extends Block {

  public SlimeDirtBlock(Properties properties) {
    super(properties);
  }

  /**
   * 1.21 replaced the {@code IPlantable}/{@code PlantType} query with this soil-side hook, and there is no longer a
   * granular "plant kind" to compare against - just the plant's own state. Slime dirt kept the broad intent of the
   * old check (sustain slimy plants and ordinary overworld plants alike) by allowing anything to grow here; a plant
   * that wants a more specific soil (farmland, nylium, etc) still asks for it through its own {@code mayPlaceOn}.
   */
  @Override
  public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, BlockState plant) {
    return TriState.TRUE;
  }
}
