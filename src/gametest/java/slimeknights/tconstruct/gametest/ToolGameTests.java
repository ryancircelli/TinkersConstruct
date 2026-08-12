package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * In-game behavior tests for tools: part swaps preserving persistent modifier data, repair kit restoration,
 * broken tools losing the ability to break blocks, and the AoE hammer's 3x3 breaking pattern.
 */
@GameTestHolder(TConstruct.MOD_ID)
@PrefixGameTestTemplate(false)
public class ToolGameTests {
  /** Swapping a tool part preserves persistent modifier data (as opposed to volatile data, which is recalculated) */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void partSwapPreservesPersistentModData(GameTestHelper helper) {
    ResourceLocation key = new ResourceLocation("tconstruct_test", "marker");
    ToolStack tool = GameTestFixtures.createPickaxe();
    tool.getPersistentData().putInt(key, 42);

    tool.replaceMaterial(1, GameTestFixtures.WOOD); // handle was already wood; still exercises the rebuild path
    helper.assertTrue(tool.getPersistentData().get(key, CompoundTag::getInt) == 42, "persistent mod data was lost across a part swap");
    helper.succeed();
  }

  /**
   * A repair kit restores durability. {@link slimeknights.tconstruct.tools.item.RepairKitItem#overrideStackedOnOther}
   * ultimately delegates the actual restoration to {@link ToolDamageUtil#repair}; this test exercises that shared
   * restoration path directly rather than constructing a full inventory click context for the item interaction.
   */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void repairKitRestoresDurability(GameTestHelper helper) {
    ToolStack tool = GameTestFixtures.createPickaxe();
    tool.setDamage(50);
    int damageBeforeRepair = tool.getDamage();
    helper.assertTrue(damageBeforeRepair > 0, "test pickaxe was not damaged before repairing");

    ToolDamageUtil.repair(tool, 20);
    helper.assertTrue(tool.getDamage() == damageBeforeRepair - 20, "repairing did not restore the expected amount of durability");
    helper.succeed();
  }

  /** A broken tool cannot break blocks: the mining hook refuses, and the targeted block survives */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void brokenToolCannotBreakBlocks(GameTestHelper helper) {
    BlockPos pos = new BlockPos(4, 2, 4);
    helper.setBlock(pos, Blocks.STONE.defaultBlockState());

    ToolStack tool = GameTestFixtures.createPickaxe();
    tool.setDamage(tool.getStats().getInt(ToolStats.DURABILITY));
    helper.assertTrue(tool.isBroken(), "test pickaxe was not broken");
    ItemStack stack = tool.createStack();

    ServerPlayer player = GameTestFixtures.createFakePlayer(helper, "broken_tool_test");
    // mineBlock takes a world-space BlockPos (it is not a GameTestHelper method, so it does not translate
    // test-relative coordinates itself); translate explicitly rather than relying on the broken-tool early
    // return making the untranslated position harmless today
    BlockPos absolutePos = helper.absolutePos(pos);
    boolean minedSuccessfully = ToolHarvestLogic.mineBlock(stack, helper.getLevel(), helper.getBlockState(pos), absolutePos, player);
    helper.assertFalse(minedSuccessfully, "a broken tool's mine hook reported success");
    helper.assertBlockPresent(Blocks.STONE, pos);
    helper.succeed();
  }

  /** The (built-in AoE) sledge hammer breaks a 3x3 pattern when it hits a wall */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void aoeHammerBreaks3x3Pattern(GameTestHelper helper) {
    // solid 3x3x3 cube of stone; regardless of which two axes the AoE iterator expands into relative to the hit
    // face, a 3x3 slice through the center of a solid cube always lands entirely on stone
    BlockPos center = new BlockPos(4, 3, 5);
    BlockState stone = Blocks.STONE.defaultBlockState();
    for (int x = center.getX() - 1; x <= center.getX() + 1; x++) {
      for (int y = center.getY() - 1; y <= center.getY() + 1; y++) {
        for (int z = center.getZ() - 1; z <= center.getZ() + 1; z++) {
          helper.setBlock(new BlockPos(x, y, z), stone);
        }
      }
    }

    // ToolHarvestLogic.runBlockBreak works in world space (it reads the block through player.serverLevel(), not
    // through the helper), so the target position and the player standing next to it both need the test's
    // world-space origin folded in via absolutePos. Every gametest in a batch is placed at a different offset in
    // the shared world, so using the un-translated relative position here only "worked" when a test happened to
    // land at/near world origin - passing on some runs and reporting 0 blocks broken on others.
    BlockPos absoluteCenter = helper.absolutePos(center);

    ToolStack tool = GameTestFixtures.createSledgeHammer();
    ItemStack stack = tool.createStack();
    ServerPlayer player = GameTestFixtures.createFakePlayer(helper, "aoe_hammer_test");
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    // stand north of the wall looking south at it (south face is the one being hit, see sideHit below)
    player.setPos(absoluteCenter.getX() + 0.5, absoluteCenter.getY() + 0.5, absoluteCenter.getZ() - 2.0);
    player.setYRot(0.0F);   // yaw 0 faces south (+Z), toward the wall
    player.setXRot(0.0F);   // level pitch

    // hit the south face of the center block; this constructs the BlockHitResult directly from the given
    // position and face (see Util.createTraceResult), it does not raytrace from the player's look vector, so
    // the explicit rotation above is belt-and-suspenders rather than load bearing for the hit face itself
    int harvested = ToolHarvestLogic.runBlockBreak(stack, ToolStack.mutable(stack), stone, absoluteCenter, Direction.SOUTH, player, null);
    helper.assertTrue(harvested == 9, "AoE hammer did not break exactly the 9 blocks of a 3x3 pattern (broke " + harvested + ")");
    helper.succeed();
  }
}
