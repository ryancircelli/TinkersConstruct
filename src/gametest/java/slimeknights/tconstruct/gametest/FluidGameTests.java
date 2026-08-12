package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;

/**
 * In-game behavior tests for fluid containers: the copper can filling from a tank via its fluid handler item
 * capability, and a seared tank's fluid handler capability transferring exact amounts.
 */
@GameTestHolder(TConstruct.MOD_ID)
@PrefixGameTestTemplate(false)
public class FluidGameTests {
  /** A copper can fills to its capacity from a tank block, exercising the fluid handler item capability directly (equivalent to the tank's right-click interaction) */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void copperCanFillsFromTank(GameTestHelper helper) {
    BlockPos tankPos = new BlockPos(4, 1, 4);
    helper.setBlock(tankPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    TankBlockEntity tank = (TankBlockEntity) helper.getBlockEntity(tankPos);
    tank.getTank().fill(new FluidStack(TinkerFluids.moltenIron.get(), 200), FluidAction.EXECUTE);

    ItemStack canStack = new ItemStack(TinkerSmeltery.copperCan.get());
    IFluidHandlerItem canHandler = canStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve()
                                            .orElseThrow(() -> new AssertionError("copper can has no fluid handler item capability"));

    FluidStack simulated = tank.getTank().drain(FluidValues.INGOT, FluidAction.SIMULATE);
    int filled = canHandler.fill(simulated, FluidAction.EXECUTE);
    tank.getTank().drain(filled, FluidAction.EXECUTE);

    helper.assertTrue(filled == FluidValues.INGOT, "copper can did not fill by exactly one ingot's worth of fluid");
    FluidStack inCan = canHandler.getFluidInTank(0);
    helper.assertTrue(inCan.getFluid() == TinkerFluids.moltenIron.get() && inCan.getAmount() == FluidValues.INGOT, "copper can does not contain the expected fluid after filling");
    helper.assertTrue(tank.getTank().getFluidAmount() == 200 - FluidValues.INGOT, "source tank was not drained by the amount the can filled");
    helper.succeed();
  }

  /** A seared tank's fluid handler capability moves exact amounts on both fill and drain */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void searedTankFluidIOTransfersExactAmounts(GameTestHelper helper) {
    BlockPos sourcePos = new BlockPos(3, 1, 4);
    BlockPos destPos = new BlockPos(5, 1, 4);
    helper.setBlock(sourcePos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    helper.setBlock(destPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());

    TankBlockEntity source = (TankBlockEntity) helper.getBlockEntity(sourcePos);
    TankBlockEntity dest = (TankBlockEntity) helper.getBlockEntity(destPos);
    source.getTank().fill(new FluidStack(TinkerFluids.moltenIron.get(), 500), FluidAction.EXECUTE);

    FluidStack drained = source.getTank().drain(200, FluidAction.EXECUTE);
    helper.assertTrue(drained.getAmount() == 200, "seared tank drain moved the wrong amount");
    int filled = dest.getTank().fill(drained, FluidAction.EXECUTE);
    helper.assertTrue(filled == 200, "seared tank fill accepted the wrong amount");

    helper.assertTrue(source.getTank().getFluidAmount() == 300, "source tank has the wrong remaining amount after draining");
    helper.assertTrue(dest.getTank().getFluidAmount() == 200, "destination tank has the wrong amount after filling");
    helper.succeed();
  }
}
