package slimeknights.tconstruct.shared.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.tools.TinkerToolActions;

public class WaxedPlatformBlock extends PlatformBlock {
  public static final MapCodec<WaxedPlatformBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    WeatherState.CODEC.fieldOf("weathering_state").forGetter(block -> block.age),
    propertiesCodec()
  ).apply(instance, WaxedPlatformBlock::new));

  private final WeatherState age;
  public WaxedPlatformBlock(WeatherState age, Properties prop) {
    super(prop);
    this.age = age;
  }

  @Override
  public MapCodec<? extends WaxedPlatformBlock> codec() {
    return CODEC;
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
