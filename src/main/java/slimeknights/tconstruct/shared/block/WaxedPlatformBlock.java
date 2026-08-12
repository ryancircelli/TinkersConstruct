package slimeknights.tconstruct.shared.block;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.tools.TinkerToolActions;

public class WaxedPlatformBlock extends PlatformBlock {
  private final WeatherState age;
  public WaxedPlatformBlock(WeatherState age, Properties prop) {
    super(prop);
    this.age = age;
  }

  @Override
  protected boolean verticalConnect(BlockState state) {
    return state.is(TinkerTags.Blocks.COPPER_PLATFORMS);
  }

  @Nullable
  @Override
  public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility toolAction, boolean simulate) {
    if (TinkerToolActions.AXE_WAX_OFF.equals(toolAction)) {
      return TinkerCommons.copperPlatform.get(age).withPropertiesOf(state);
    }
    return null;
  }
}
