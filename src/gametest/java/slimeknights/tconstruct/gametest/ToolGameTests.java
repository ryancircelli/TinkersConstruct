package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.tools.capability.BlockItemProviderCapability;
import slimeknights.tconstruct.library.tools.capability.BlockItemProviderModifierHook;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.logic.InteractionHandler;

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
    ResourceLocation key = ResourceLocation.fromNamespaceAndPath("tconstruct_test", "marker");
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

  /**
   * Left clicking an entity damages the tool, and that damage reaches the <em>stack</em>.
   * <p>
   * {@link EntityInteractionModifierHook#leftClickEntity} takes a mutable tool and falls through to
   * {@code ToolAttackUtil.attackEntity(IToolStackView, Player, Entity)},
   * the view overload, which damages the tool but never commits. Asserting on the tool instance would pass either
   * way - only reading the damage back off the stack distinguishes a committed write from a lost one.
   */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void leftClickEntityDamageReachesTheStack(GameTestHelper helper) {
    ToolStack tool = GameTestFixtures.createPickaxe();
    ItemStack stack = tool.createStack();
    helper.assertTrue(ToolStack.from(stack).getDamage() == 0, "test pickaxe started out damaged");

    ServerPlayer player = GameTestFixtures.createFakePlayer(helper, "left_click_entity_test");
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    Cow target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));

    EntityInteractionModifierHook.leftClickEntity(stack, player, target);
    helper.assertTrue(ToolStack.from(stack).getDamage() > 0, "attacking an entity left the stack undamaged, so the tool's damage never reached it");
    helper.succeed();
  }

  /**
   * Ending an armor interaction clears the interaction data off the <em>stack</em>.
   * <p>
   * {@link InteractionHandler#stopArmorInteract} ends with {@link GeneralInteractionModifierHook#finishUsing}, which
   * removes the active modifier and drawtime from the tool's persistent data. Uncommitted, the armor stayed "mid
   * interaction" on the stack forever.
   */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void stopArmorInteractClearsDrawtimeOnTheStack(GameTestHelper helper) {
    ItemStack helmet = new ItemStack(TinkerTools.travelersGear.get(ArmorItem.Type.HELMET));
    ToolStack tool = ToolStack.mutable(helmet);
    tool.getPersistentData().putInt(GeneralInteractionModifierHook.KEY_DRAWTIME, 20);
    tool.updateStack();
    helper.assertTrue(ToolStack.from(helmet).getPersistentData().getInt(GeneralInteractionModifierHook.KEY_DRAWTIME) == 20,
                      "test helmet did not start with a drawtime");

    ServerPlayer player = GameTestFixtures.createFakePlayer(helper, "stop_armor_interact_test");
    player.setItemSlot(EquipmentSlot.HEAD, helmet);
    helper.assertTrue(InteractionHandler.stopArmorInteract(player, EquipmentSlot.HEAD), "stopArmorInteract did not recognise the helmet");

    helper.assertTrue(ToolStack.from(helmet).getPersistentData().getInt(GeneralInteractionModifierHook.KEY_DRAWTIME) == 0,
                      "the drawtime was still on the stack, so finishUsing's clear was lost");
    helper.succeed();
  }

  /**
   * Providing a block item off a tool damages the tool, and that damage reaches the <em>stack</em>.
   * <p>
   * {@link BlockItemProviderModifierHook.CapabilityImpl#consume} runs the hook that
   * {@link slimeknights.tconstruct.library.modifiers.modules.behavior.BlockItemProviderModule} implements, and that
   * module damages the tool through a view which never commits. The shipped {@code glowing} modifier provides a glow
   * block for 5 durability, so uncommitted the glow block was free.
   */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void blockItemProviderConsumeDamagesTheStack(GameTestHelper helper) {
    ToolStack tool = GameTestFixtures.createPickaxe();
    tool.addModifier(ModifierIds.glowing, 1);
    ItemStack stack = tool.createStack();
    helper.assertTrue(ToolStack.from(stack).getDamage() == 0, "test pickaxe started out damaged");

    ToolStack bound = ToolStack.mutable(stack);
    BlockItemProviderCapability capability = new BlockItemProviderModifierHook.CapabilityImpl(bound, bound);
    ItemStack provided = capability.getBlockItemStack(stack, null);
    helper.assertTrue(!provided.isEmpty(), "the glowing modifier provided no block item to consume");

    capability.consume(stack, provided, null);
    helper.assertTrue(ToolStack.from(stack).getDamage() > 0,
                      "providing a block off the tool left the stack undamaged, so the tool's damage never reached it");
    helper.succeed();
  }
}
