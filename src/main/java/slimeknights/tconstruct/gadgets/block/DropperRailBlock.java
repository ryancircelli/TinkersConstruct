package slimeknights.tconstruct.gadgets.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import slimeknights.mantle.inventory.EmptyItemHandler;
import slimeknights.mantle.util.CapabilityHelper;

public class DropperRailBlock extends RailBlock {

  public DropperRailBlock(Properties properties) {
    super(properties);
  }

  @Override
  public void onMinecartPass(BlockState state, Level world, BlockPos pos, AbstractMinecart cart) {
    if (Capabilities.ItemHandler.ENTITY.getCapability(cart, null) == null || !(cart instanceof Hopper)) {
      return;
    }
    BlockEntity tileEntity = world.getBlockEntity(pos.below());
    if (tileEntity == null || CapabilityHelper.itemHandler(tileEntity, Direction.DOWN) == null) {
      return;
    }

    // cart is an entity, not a stack/block entity, so it stays on the raw EntityCapability API - CapabilityHelper only covers those two receivers
    IItemHandler itemHandlerCart = Capabilities.ItemHandler.ENTITY.getCapability(cart, null);
    if (itemHandlerCart == null) {
      itemHandlerCart = EmptyItemHandler.INSTANCE;
    }
    IItemHandler itemHandlerTE = CapabilityHelper.itemHandler(tileEntity, Direction.UP);
    if (itemHandlerTE == null) {
      itemHandlerTE = EmptyItemHandler.INSTANCE;
    }

    for (int i = 0; i < itemHandlerCart.getSlots(); i++) {
      ItemStack itemStack = itemHandlerCart.extractItem(i, 1, true);
      if (itemStack.isEmpty()) {
        continue;
      }
      if (ItemHandlerHelper.insertItem(itemHandlerTE, itemStack, true).isEmpty()) {
        itemStack = itemHandlerCart.extractItem(i, 1, false);
        ItemHandlerHelper.insertItem(itemHandlerTE, itemStack, false);
        break;
      }
    }
  }

}
