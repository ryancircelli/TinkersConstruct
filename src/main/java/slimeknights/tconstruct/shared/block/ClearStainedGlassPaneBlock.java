package slimeknights.tconstruct.shared.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import slimeknights.tconstruct.shared.block.ClearStainedGlassBlock.GlassColor;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class ClearStainedGlassPaneBlock extends ClearGlassPaneBlock {
  public static final MapCodec<ClearStainedGlassPaneBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    propertiesCodec(),
    GlassColor.CODEC.fieldOf("color").forGetter(block -> block.glassColor)
  ).apply(instance, ClearStainedGlassPaneBlock::new));

  private final GlassColor glassColor;
  public ClearStainedGlassPaneBlock(Properties builder, GlassColor glassColor) {
    super(builder);
    this.glassColor = glassColor;
  }

  @Override
  public MapCodec<? extends ClearStainedGlassPaneBlock> codec() {
    return CODEC;
  }

  @Override
  public Integer getBeaconColorMultiplier(BlockState state, LevelReader world, BlockPos pos, BlockPos beaconPos) {
    return this.glassColor.getBeaconColor();
  }
}
