package slimeknights.tconstruct.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.recipe.worktable.IModifierWorktableRecipe;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.data.ModifierIds;

/**
 * In-game behavior tests for modifiers: applying/removing them through the tinker station and modifier worktable
 * recipe machinery directly (bypassing the GUI), material traits, durability, and the reinforced modifier's
 * damage-reduction hook.
 */
@GameTestHolder(TConstruct.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModifierGameTests {
  /** Applying a modifier at the tinker station via its recipe consumes an upgrade slot */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void applyingModifierConsumesSlot(GameTestHelper helper) {
    Level level = helper.getLevel();
    ToolStack tool = GameTestFixtures.createPickaxe();
    int slotsBefore = tool.getFreeSlots(SlotType.UPGRADE);

    GameTestFixtures.FakeTinkerInventory inv = new GameTestFixtures.FakeTinkerInventory(1);
    inv.setTinkerable(tool.createStack());
    inv.setInput(0, new ItemStack(Items.DIAMOND));

    ITinkerStationRecipe recipe = level.getRecipeManager().getRecipeFor(TinkerRecipeTypes.TINKER_STATION.get(), inv, level).map(RecipeHolder::value).orElse(null);
    helper.assertTrue(recipe != null, "no tinker station recipe matched a diamond modifier input");
    RecipeResult<LazyToolStack> result = recipe.getValidatedResult(inv, level.registryAccess());
    helper.assertTrue(result.isSuccess(), "diamond modifier recipe did not succeed");

    ToolStack resultTool = result.getResult().getTool();
    helper.assertTrue(resultTool.getFreeSlots(SlotType.UPGRADE) == slotsBefore - 1, "applying a modifier did not consume exactly one upgrade slot");
    helper.succeed();
  }

  /** Applying the same modifier's recipe twice accumulates its level additively */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void modifierLevelsAccumulateAdditively(GameTestHelper helper) {
    Level level = helper.getLevel();
    // an all-wood pickaxe has no baseline "magnetic" trait (that's iron's, see materialTraitPresentAfterPartSwap),
    // so the level read back afterward is purely from the two recipe applications
    ItemStack toolStack = GameTestFixtures.createWoodenPickaxe().createStack();

    for (int i = 0; i < 2; i++) {
      GameTestFixtures.FakeTinkerInventory inv = new GameTestFixtures.FakeTinkerInventory(1);
      inv.setTinkerable(toolStack);
      inv.setInput(0, new ItemStack(Items.COMPASS));
      ITinkerStationRecipe recipe = level.getRecipeManager().getRecipeFor(TinkerRecipeTypes.TINKER_STATION.get(), inv, level).map(RecipeHolder::value).orElse(null);
      helper.assertTrue(recipe != null, "no tinker station recipe matched a compass modifier input on application " + i);
      RecipeResult<LazyToolStack> result = recipe.getValidatedResult(inv, level.registryAccess());
      helper.assertTrue(result.isSuccess(), "magnetic modifier recipe did not succeed on application " + i);
      toolStack = result.getResult().getStack();
    }

    IToolStackView finalTool = ToolStack.from(toolStack);
    helper.assertTrue(finalTool.getModifierLevel(ModifierIds.magnetic) == 2, "magnetic modifier level did not accumulate additively across two applications");
    helper.succeed();
  }

  /** The modifier worktable's removal recipe restores the upgrade slot a modifier had consumed */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void removalRecipeRestoresSlot(GameTestHelper helper) {
    Level level = helper.getLevel();
    ToolStack tool = GameTestFixtures.createPickaxe();
    int slotsBefore = tool.getFreeSlots(SlotType.UPGRADE);

    // apply magnetic (1 slot) via its tinker station recipe
    GameTestFixtures.FakeTinkerInventory addInv = new GameTestFixtures.FakeTinkerInventory(1);
    addInv.setTinkerable(tool.createStack());
    addInv.setInput(0, new ItemStack(Items.COMPASS));
    ITinkerStationRecipe addRecipe = level.getRecipeManager().getRecipeFor(TinkerRecipeTypes.TINKER_STATION.get(), addInv, level).map(RecipeHolder::value).orElse(null);
    helper.assertTrue(addRecipe != null, "no tinker station recipe matched a compass modifier input");
    RecipeResult<LazyToolStack> addResult = addRecipe.getValidatedResult(addInv, level.registryAccess());
    helper.assertTrue(addResult.isSuccess(), "magnetic modifier recipe did not succeed");
    ToolStack withModifier = addResult.getResult().getTool();
    int slotsAfterApply = withModifier.getFreeSlots(SlotType.UPGRADE);
    helper.assertTrue(slotsAfterApply == slotsBefore - 1, "applying magnetic did not consume a slot");

    // remove it via the modifier worktable's generic "wet sponge" removal recipe
    GameTestFixtures.FakeTinkerInventory removeInv = new GameTestFixtures.FakeTinkerInventory(1);
    removeInv.setTinkerable(withModifier.createStack());
    removeInv.setInput(0, new ItemStack(Items.WET_SPONGE));
    IModifierWorktableRecipe removeRecipe = level.getRecipeManager().getRecipeFor(TinkerRecipeTypes.MODIFIER_WORKTABLE.get(), removeInv, level).map(RecipeHolder::value).orElse(null);
    helper.assertTrue(removeRecipe != null, "no modifier worktable recipe matched a wet sponge removal input");
    ModifierEntry toRemove = removeRecipe.getModifierOptions(removeInv).stream()
                                          .filter(entry -> entry.getId().equals(ModifierIds.magnetic))
                                          .findFirst().orElse(null);
    helper.assertTrue(toRemove != null, "removal recipe did not offer magnetic as a removable modifier");
    RecipeResult<LazyToolStack> removeResult = removeRecipe.getResult(removeInv, toRemove);
    helper.assertTrue(removeResult.isSuccess(), "removing magnetic did not succeed");

    ToolStack afterRemoval = removeResult.getResult().getTool();
    helper.assertTrue(afterRemoval.getFreeSlots(SlotType.UPGRADE) == slotsBefore, "removing the modifier did not restore the upgrade slot");
    helper.succeed();
  }

  /** A material's trait becomes present on the tool after swapping a part to a material carrying that trait */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void materialTraitPresentAfterPartSwap(GameTestHelper helper) {
    ToolStack tool = GameTestFixtures.createWoodenPickaxe();
    helper.assertTrue(tool.getModifierLevel(ModifierIds.magnetic) == 0, "an all-wood pickaxe unexpectedly already has iron's magnetic trait");

    // swap the handle (index 1) from wood to iron; iron's material trait is "magnetic" (see tinkering/materials/traits/iron.json)
    tool.replaceMaterial(1, GameTestFixtures.IRON);
    helper.assertTrue(tool.getModifierLevel(ModifierIds.magnetic) > 0, "swapping in an iron part did not add iron's magnetic trait");
    helper.succeed();
  }

  /** Damaging a tool to its full durability sets the broken flag and stops it from being usable to mine */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void durabilityZeroBreaksTool(GameTestHelper helper) {
    ToolStack tool = GameTestFixtures.createPickaxe();
    int durability = tool.getStats().getInt(ToolStats.DURABILITY);
    helper.assertTrue(durability > 0, "test pickaxe has no durability stat to exhaust");

    tool.setDamage(durability);
    helper.assertTrue(tool.isBroken(), "tool was not marked broken once damage reached its durability");

    ItemStack stack = tool.createStack();
    // BlockPos.ZERO is never dereferenced here (mineBlock returns as soon as it sees the broken tool, before it
    // would need a real position), so it does not need helper.absolutePos translation the way a position that's
    // actually used against the world would
    LivingEntity player = GameTestFixtures.createFakePlayer(helper, "durability_zero_test");
    boolean minedSuccessfully = ToolHarvestLogic.mineBlock(stack, helper.getLevel(), Blocks.STONE.defaultBlockState(), BlockPos.ZERO, player);
    helper.assertFalse(minedSuccessfully, "a broken tool was still usable to mine a block");
    helper.succeed();
  }

  /**
   * Reinforced reduces damage taken by the tool. The reduction is probabilistic (see ReduceToolDamageModule),
   * so this seeds the shared RNG for reproducibility and compares against an identical unreinforced tool rather
   * than asserting an exact reduced value.
   */
  @GameTest(template = GameTestFixtures.TEMPLATE)
  public static void reinforcedReducesDamageTaken(GameTestHelper helper) {
    ToolStack plain = GameTestFixtures.createPickaxe();
    ToolStack reinforced = GameTestFixtures.createPickaxe();
    reinforced.addModifier(ModifierIds.reinforced, 5);

    int amount = 20;
    ItemStack plainStack = plain.createStack();
    ItemStack reinforcedStack = reinforced.createStack();

    TConstruct.RANDOM.setSeed(12345L);
    ToolDamageUtil.damage(plain, amount, null, plainStack);
    TConstruct.RANDOM.setSeed(12345L);
    ToolDamageUtil.damage(reinforced, amount, null, reinforcedStack);

    helper.assertTrue(plain.getDamage() == amount, "unreinforced tool took an unexpected amount of damage");
    helper.assertTrue(reinforced.getDamage() < plain.getDamage(), "reinforced modifier did not reduce damage taken compared to an unreinforced tool");
    helper.succeed();
  }
}
