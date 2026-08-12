package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.ChannelBlock;
import slimeknights.tconstruct.smeltery.block.ChannelBlock.ChannelConnection;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-game behavior tests for the smeltery multiblock: structure detection, melting, fuel consumption, alloying,
 * faucets, and channels. This is 1.20.1's first automated coverage of the smeltery's in-game behavior.
 */
@GameTestHolder(TConstruct.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmelteryGameTests {
  /** Sums the amount of the given fluid across every tank slot of a handler */
  static int fluidAmount(IFluidHandler handler, Fluid fluid) {
    int total = 0;
    for (int i = 0; i < handler.getTanks(); i++) {
      FluidStack stack = handler.getFluidInTank(i);
      if (stack.getFluid() == fluid) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  /** Multiblock forms once the structure is complete, and stops being formed as soon as a wall block is broken */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void multiblockFormsAndDetectsBrokenWall(GameTestHelper helper) {
    BlockPos controllerPos = GameTestFixtures.buildSmeltery(helper, false);
    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(GameTestFixtures.getSmeltery(helper).getStructure() != null, "smeltery did not form"))
          .thenExecute(() -> helper.assertTrue(helper.getBlockState(controllerPos).getValue(ControllerBlock.IN_STRUCTURE), "controller not marked as in structure"))
          // break an edge-middle wall block: corners are not validated by the multiblock's wall check (no frame
          // is required), so removing one would leave the structure formed; an edge-middle block is checked
          .thenExecute(() -> helper.setBlock(new BlockPos(4, 1, 3), Blocks.AIR.defaultBlockState()))
          .thenWaitUntil(() -> helper.assertFalse(helper.getBlockState(controllerPos).getValue(ControllerBlock.IN_STRUCTURE), "controller still marked as in structure after a wall block was removed"))
          .thenExecute(() -> helper.assertTrue(GameTestFixtures.getSmeltery(helper).getStructure() == null, "smeltery structure still present after a wall block was removed"))
          .thenSucceed();
  }

  /** Melting an iron ingot at temperature yields exactly 90mb (one ingot's worth) of molten iron */
  @GameTest(template = GameTestFixtures.TEMPLATE, timeoutTicks = 400)
  public static void meltingIronYieldsMoltenIron(GameTestHelper helper) {
    GameTestFixtures.buildSmeltery(helper, true);
    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(GameTestFixtures.getSmeltery(helper).getStructure() != null, "smeltery did not form"))
          .thenExecute(() -> {
            TankBlockEntity tank = (TankBlockEntity) helper.getBlockEntity(GameTestFixtures.SMELTERY_FUEL_TANK);
            tank.getTank().fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
            SmelteryBlockEntity smeltery = GameTestFixtures.getSmeltery(helper);
            smeltery.getMeltingInventory().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
          })
          .thenWaitUntil(() -> helper.assertTrue(fluidAmount(GameTestFixtures.getSmeltery(helper).getTank(), TinkerFluids.moltenIron.get()) >= FluidValues.INGOT, "iron ingot did not finish melting"))
          .thenExecute(() -> helper.assertTrue(fluidAmount(GameTestFixtures.getSmeltery(helper).getTank(), TinkerFluids.moltenIron.get()) == FluidValues.INGOT, "melting produced the wrong amount of molten iron"))
          .thenSucceed();
  }

  /** Fuel drains from the fuel tank block's own fluid handler (not just an internal counter) when the smeltery melts an item */
  @GameTest(template = GameTestFixtures.TEMPLATE, timeoutTicks = 400)
  public static void fuelDrainsFromTankOnMelt(GameTestHelper helper) {
    GameTestFixtures.buildSmeltery(helper, true);
    AtomicInteger initialFuel = new AtomicInteger(-1);
    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(GameTestFixtures.getSmeltery(helper).getStructure() != null, "smeltery did not form"))
          .thenExecute(() -> {
            TankBlockEntity tank = (TankBlockEntity) helper.getBlockEntity(GameTestFixtures.SMELTERY_FUEL_TANK);
            tank.getTank().fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
            initialFuel.set(tank.getTank().getFluidAmount());
            GameTestFixtures.getSmeltery(helper).getMeltingInventory().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
          })
          .thenWaitUntil(() -> {
            TankBlockEntity tank = (TankBlockEntity) helper.getBlockEntity(GameTestFixtures.SMELTERY_FUEL_TANK);
            helper.assertTrue(tank.getTank().getFluidAmount() < initialFuel.get(), "fuel tank did not drain while melting");
          })
          .thenExecute(() -> {
            TankBlockEntity tank = (TankBlockEntity) helper.getBlockEntity(GameTestFixtures.SMELTERY_FUEL_TANK);
            // the lava fuel recipe drains 50mb per fuel-find event; only one should have fired in this window
            helper.assertTrue(tank.getTank().getFluidAmount() == initialFuel.get() - 50, "fuel tank drained by an unexpected amount");
          })
          .thenSucceed();
  }

  /**
   * Alloying two molten metals already in the smeltery's tank produces the alloy recipe's result.
   * Note: neither "iron + coal -> steel" nor "slime + ender" exist as alloy recipes in this port's generated data
   * (steel is not craftable via alloying here, and there is no slime+ender alloy). This substitutes the nearest
   * unconditional two-input alloy recipe that actually exists: molten copper + molten gold -> molten rose gold
   * (see molten_rose_gold.json, temperature 550, no recipe conditions).
   */
  @GameTest(template = GameTestFixtures.TEMPLATE, timeoutTicks = 400)
  public static void alloyingProducesRoseGold(GameTestHelper helper) {
    GameTestFixtures.buildSmeltery(helper, true);
    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(GameTestFixtures.getSmeltery(helper).getStructure() != null, "smeltery did not form"))
          .thenExecute(() -> {
            TankBlockEntity tank = (TankBlockEntity) helper.getBlockEntity(GameTestFixtures.SMELTERY_FUEL_TANK);
            tank.getTank().fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE);
            SmelteryBlockEntity smeltery = GameTestFixtures.getSmeltery(helper);
            smeltery.getTank().fill(new FluidStack(TinkerFluids.moltenCopper.get(), 90), FluidAction.EXECUTE);
            smeltery.getTank().fill(new FluidStack(TinkerFluids.moltenGold.get(), 90), FluidAction.EXECUTE);
          })
          .thenWaitUntil(() -> helper.assertTrue(fluidAmount(GameTestFixtures.getSmeltery(helper).getTank(), TinkerFluids.moltenRoseGold.get()) >= 180, "copper and gold did not alloy into rose gold"))
          .thenSucceed();
  }

  /** A faucet transfers fluid from its input side (the smeltery's reservoir, informally "the basin") down into a casting table below it */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void faucetTransfersFluidToTable(GameTestHelper helper) {
    BlockPos sourcePos = new BlockPos(4, 3, 3);
    BlockPos faucetPos = new BlockPos(4, 3, 4);
    BlockPos tablePos = new BlockPos(4, 2, 4);
    helper.setBlock(sourcePos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    // faucet's input handler is on the side opposite FACING, so FACING must point away from the source
    helper.setBlock(faucetPos, TinkerSmeltery.searedFaucet.get().defaultBlockState().setValue(FaucetBlock.FACING, Direction.SOUTH));
    helper.setBlock(tablePos, TinkerSmeltery.searedTable.get().defaultBlockState());

    TankBlockEntity source = (TankBlockEntity) helper.getBlockEntity(sourcePos);
    source.getTank().fill(new FluidStack(TinkerFluids.moltenIron.get(), 200), FluidAction.EXECUTE);
    // pre-load the table with a reusable cast so the fluid has a matching recipe to enter its tank under
    CastingBlockEntity table = (CastingBlockEntity) helper.getBlockEntity(tablePos);
    table.setItem(CastingBlockEntity.INPUT, new ItemStack(TinkerSmeltery.ingotCast.get()));

    FaucetBlockEntity faucet = (FaucetBlockEntity) helper.getBlockEntity(faucetPos);
    faucet.activate();

    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(table.getTank().getFluid().getAmount() > 0, "faucet did not transfer fluid to the table below it"))
          .thenExecute(() -> helper.assertTrue(table.getTank().getFluid().getFluid() == TinkerFluids.moltenIron.get(), "table received the wrong fluid"))
          .thenSucceed();
  }

  /** A channel routes fluid flow only to the side it is configured to output on */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void channelRoutesFlowToPointedSide(GameTestHelper helper) {
    BlockPos channelPos = new BlockPos(4, 3, 4);
    BlockPos receiverPos = new BlockPos(5, 3, 4); // east of the channel

    helper.setBlock(receiverPos, TinkerSmeltery.searedTank.get(TankType.INGOT_TANK).defaultBlockState());
    helper.setBlock(channelPos, TinkerSmeltery.searedChannel.get().defaultBlockState().setValue(ChannelBlock.EAST, ChannelConnection.OUT));

    ChannelBlockEntity channel = (ChannelBlockEntity) helper.getBlockEntity(channelPos);
    channel.updateFluidTo(new FluidStack(TinkerFluids.moltenIron.get(), 40));

    TankBlockEntity receiver = (TankBlockEntity) helper.getBlockEntity(receiverPos);
    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(receiver.getTank().getFluidAmount() > 0, "channel did not route fluid to the side it points"))
          .thenExecute(() -> helper.assertTrue(channel.isFlowing(Direction.EAST), "channel did not mark the output side as flowing"))
          .thenSucceed();
  }
}
