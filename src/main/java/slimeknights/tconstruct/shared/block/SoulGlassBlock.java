package slimeknights.tconstruct.shared.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SoulGlassBlock extends TransparentBlock {
  public static final MapCodec<SoulGlassBlock> CODEC = simpleCodec(SoulGlassBlock::new);

  public SoulGlassBlock(Properties properties) {
    super(properties);
  }

  @Override
  public MapCodec<? extends SoulGlassBlock> codec() {
    return CODEC;
  }

  @Override
  public VoxelShape getBlockSupportShape(BlockState pState, BlockGetter pReader, BlockPos pPos) {
    return Shapes.block();
  }

  @Override
  public boolean isPathfindable(BlockState pState, PathComputationType pType) {
    return false;
  }
}
