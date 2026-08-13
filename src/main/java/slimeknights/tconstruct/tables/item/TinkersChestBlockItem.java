package slimeknights.tconstruct.tables.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;

import javax.annotation.Nullable;

/**
 * Dyeable chest block
 * @implNote  {@code DyeableLeatherItem} is gone: dye color is a {@link DataComponents#DYED_COLOR} data component
 *            (a {@link DyedItemColor} record) on every item now, not an interface a dyeable item opts into.
 */
public class TinkersChestBlockItem extends BlockItem {
  public TinkersChestBlockItem(Block blockIn, Properties builder) {
    super(blockIn, builder);
  }

  @Override
  protected boolean updateCustomBlockEntityTag(BlockPos pos, Level worldIn, @Nullable Player player, ItemStack stack, BlockState state) {
    boolean result = super.updateCustomBlockEntityTag(pos, worldIn, player, stack, state);
    if (stack.has(DataComponents.DYED_COLOR)) {
      int color = DyedItemColor.getOrDefault(stack, TinkersChestBlockEntity.DEFAULT_COLOR);
      BlockEntityHelper.get(TinkersChestBlockEntity.class, worldIn, pos).ifPresent(te -> te.setColor(color));
    }
    return result;
  }
}
