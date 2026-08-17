package slimeknights.tconstruct.smeltery.block.entity.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.utils.NeighborCapabilityCache;

import javax.annotation.Nullable;

/** Fuel module variant that supports both item and fluid fuels. Only supports a single fluid position which should not change. */
public class SolidFuelModule extends FuelModule {
  /** Location of the fuel tank */
  private final BlockPos fuelPos;
  /** Item handler at {@link #fuelPos}, the solid fuel half of this module */
  private final NeighborCapabilityCache<IItemHandler> itemHandler =
    new NeighborCapabilityCache<>(Capabilities.ItemHandler.BLOCK, () -> !this.parent.isRemoved());

  public SolidFuelModule(MantleBlockEntity parent, BlockPos fuelPos) {
    super(parent);
    this.fuelPos = fuelPos;
  }

  /** Gets the liquid fuel handler at the fuel position, or null if there is none */
  @Nullable
  private IFluidHandler getFluidHandler() {
    return fluidHandler.get(getLevel(), fuelPos, null);
  }

  /** Gets the solid fuel handler at the fuel position, or null if there is none */
  @Nullable
  private IItemHandler getItemHandler() {
    return itemHandler.get(getLevel(), fuelPos, null);
  }


  /* Fuel updating */

  /**
   * Tries to consume fuel from the given fluid handler
   * @param handler  Handler to consume fuel from
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  private int trySolidFuel(IItemHandler handler, boolean consume) {
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack stack = handler.getStackInSlot(i);
      int time = stack.getBurnTime(TinkerRecipeTypes.FUEL.get()) / 4;
      if (time > 0) {
        MeltingFuel solid = MeltingFuelLookup.getSolid();
        if (consume) {
          ItemStack extracted = handler.extractItem(i, 1, false);
          if (ItemStack.isSameItem(extracted, stack)) {
            fuel += time;
            fuelQuality = time;
            temperature = solid.getTemperature();
            rate = solid.getRate();
            parent.setChangedFast();
            // return the container
            ItemStack container = extracted.getCraftingRemainingItem();
            if (!container.isEmpty()) {
              // if we cannot insert the container back, spit it on the ground
              ItemStack notInserted = ItemHandlerHelper.insertItem(handler, container, false);
              if (!notInserted.isEmpty()) {
                Level world = getLevel();
                double x = (world.random.nextFloat() * 0.5F) + 0.25D;
                double y = (world.random.nextFloat() * 0.5F) + 0.25D;
                double z = (world.random.nextFloat() * 0.5F) + 0.25D;
                ItemEntity itementity = new ItemEntity(world, fuelPos.getX() + x, fuelPos.getY() + y, fuelPos.getZ() + z, container);
                itementity.setDefaultPickUpDelay();
                world.addFreshEntity(itementity);
              }
            }
          } else {
            TConstruct.LOG.error("Invalid item removed from solid fuel handler");
          }
        }
        return solid.getTemperature();
      }
    }
    return 0;
  }

  @Override
  public int findFuel(boolean consume) {
    // prioritize liquid fuel - it usually goes hotter
    // on the chance both are present, fluid wins; we don't expect that to change
    int temperature = 0;
    IFluidHandler fluid = getFluidHandler();
    if (fluid != null) {
      temperature = tryLiquidFuel(fluid, consume);
    }
    // next, try solid fuel
    if (temperature == 0) {
      IItemHandler item = getItemHandler();
      if (item != null) {
        temperature = trySolidFuel(item, consume);
      }
    }
    // no handler found, tell client of the lack of fuel
    if (temperature == 0 && consume) {
      this.temperature = 0;
      this.rate = 0;
    }
    return temperature;
  }


  /* UI Syncing */

  @Override
  public FuelInfo getFuelInfo() {
    FuelInfo info = getFuelInfo(getFluidHandler());
    if (info.isEmpty() && getItemHandler() != null) {
      return FuelInfo.ITEM;
    }
    return info;
  }


  /* Fluid handler */

  /** Gets the fluid handler for proxy */
  public IFluidHandler getTank() {
    IFluidHandler handler = getFluidHandler();
    return handler != null ? handler : EmptyFluidHandler.INSTANCE;
  }
}
