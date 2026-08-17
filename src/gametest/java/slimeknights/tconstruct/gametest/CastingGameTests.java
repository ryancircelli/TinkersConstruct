package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

/**
 * In-game behavior tests for the casting table and basin: cast handling (reusable vs single-use) and casting a
 * block with no cast at all.
 */
@GameTestHolder(TConstruct.MOD_ID)
@PrefixGameTestTemplate(false)
public class CastingGameTests {
  private static final BlockPos POS = new BlockPos(4, 1, 4);

  /** Casting table with a reusable metal cast produces the part and keeps the cast in the input slot */
  @GameTest(template = GameTestFixtures.TEMPLATE, timeoutTicks = 100)
  public static void reusableCastProducesPartAndKeepsCast(GameTestHelper helper) {
    helper.setBlock(POS, TinkerSmeltery.searedTable.get().defaultBlockState());
    CastingBlockEntity table = (CastingBlockEntity) helper.getBlockEntity(POS);
    ItemStack cast = new ItemStack(TinkerSmeltery.ingotCast.get());
    table.setItem(CastingBlockEntity.INPUT, cast);
    table.updateFluidTo(new FluidStack(TinkerFluids.moltenIron.get(), 90));

    helper.startSequence()
          .thenIdle(65) // cooling_time is 60 for the reusable ingot cast recipe
          .thenExecute(() -> {
            helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_INGOT), "casting table did not produce an iron ingot");
            helper.assertTrue(table.getItem(CastingBlockEntity.INPUT).is(TinkerSmeltery.ingotCast.get()), "casting table consumed the reusable cast");
          })
          .thenSucceed();
  }

  /** Casting table with a single-use sand cast produces the part but consumes the cast */
  @GameTest(template = GameTestFixtures.TEMPLATE, timeoutTicks = 100)
  public static void sandCastConsumedOnUse(GameTestHelper helper) {
    helper.setBlock(POS, TinkerSmeltery.searedTable.get().defaultBlockState());
    CastingBlockEntity table = (CastingBlockEntity) helper.getBlockEntity(POS);
    ItemStack cast = new ItemStack(TinkerSmeltery.ingotCast.getSand());
    table.setItem(CastingBlockEntity.INPUT, cast);
    table.updateFluidTo(new FluidStack(TinkerFluids.moltenIron.get(), 90));

    helper.startSequence()
          .thenIdle(65) // cooling_time is 60 for the sand cast recipe
          .thenExecute(() -> {
            helper.assertTrue(table.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_INGOT), "casting table did not produce an iron ingot");
            helper.assertTrue(table.getItem(CastingBlockEntity.INPUT).isEmpty(), "casting table did not consume the single-use sand cast");
          })
          .thenSucceed();
  }

  /** Casting basin with no cast at all produces a block straight from the fluid */
  @GameTest(template = GameTestFixtures.TEMPLATE, timeoutTicks = 250)
  public static void basinCastsBlock(GameTestHelper helper) {
    helper.setBlock(POS, TinkerSmeltery.searedBasin.get().defaultBlockState());
    CastingBlockEntity basin = (CastingBlockEntity) helper.getBlockEntity(POS);
    basin.updateFluidTo(new FluidStack(TinkerFluids.moltenIron.get(), 810));

    helper.startSequence()
          .thenIdle(190) // cooling_time is 180 for the iron block casting recipe
          .thenExecute(() -> helper.assertTrue(basin.getItem(CastingBlockEntity.OUTPUT).is(Items.IRON_BLOCK), "casting basin did not produce an iron block"))
          .thenSucceed();
  }
}
