package slimeknights.tconstruct.tables.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;

public class TinkersChestBlock extends ChestBlock {
  public TinkersChestBlock(Properties builder, BlockEntitySupplier<? extends BlockEntity> be, boolean dropsItems) {
    super(builder, be, dropsItems);
  }

  /** @implNote  {@code Block#getCloneItemStack(BlockState,HitResult,BlockGetter,BlockPos,Player)} is gone; the
   *             equivalent full-context overload is NeoForge's {@code IBlockExtension} one, which vanilla's
   *             narrower {@code getCloneItemStack(LevelReader,BlockPos,BlockState)} now delegates to by default.
   *             {@code DyeableLeatherItem} is gone too - dye color is {@link DyedItemColor}, a data component. */
  @Override
  public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader world, BlockPos pos, Player player) {
    ItemStack stack = new ItemStack(this);
    BlockEntityHelper.get(TinkersChestBlockEntity.class, world, pos).ifPresent(te -> {
      if (te.hasColor()) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(te.getColor(), true));
      }
    });
    return stack;
  }
}
