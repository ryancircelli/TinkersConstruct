package slimeknights.tconstruct.shared.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Dispenser behavior that places a block
 */
public class PlaceBlockDispenserBehavior extends OptionalDispenseItemBehavior {
  public static PlaceBlockDispenserBehavior INSTANCE = new PlaceBlockDispenserBehavior();

  private PlaceBlockDispenserBehavior() {}

  @Override
  protected ItemStack execute(BlockSource source, ItemStack stack) {
    // 1.21 turned BlockSource into a record, and it is now always a ServerLevel, so the client side check is gone
    ServerLevel level = source.level();
    BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
    if (level.isEmptyBlock(target) && stack.getItem() instanceof BlockItem blockItem) {
      Block block = blockItem.getBlock();
      // could use getPlacementState, but that requires a context, not worth creating
      BlockState state = block.defaultBlockState();
      level.setBlock(target, state, Block.UPDATE_ALL);
      if (level.getBlockState(target).is(block)) {
        BlockItem.updateCustomBlockEntityTag(level, null, target, stack);
        block.setPlacedBy(level, target, state, null, stack);
      }
      level.gameEvent(null, GameEvent.BLOCK_PLACE, target);
      SoundType sound = state.getSoundType(level, target, null);
      level.playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
      stack.shrink(1);
      this.setSuccess(true);
    }
    return stack;
  }
}
