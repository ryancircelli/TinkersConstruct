package slimeknights.tconstruct.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.IMutableTinkerStationContainer;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.tools.ToolDefinitions;
import slimeknights.tconstruct.tools.TinkerTools;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;

/**
 * Shared fixtures for the {@code tconstruct} gametest suite: fixed material ids, tool stack builders, and a
 * minimal 3x3 smeltery built in-code via {@link GameTestHelper#setBlock} so the suite does not need a bespoke
 * structure template per test.
 */
public final class GameTestFixtures {
  private GameTestFixtures() {}

  /** Shared empty/flat template reused by every test in this suite; see {@code data/tconstruct/structures/empty.nbt} */
  public static final String TEMPLATE = "empty";

  /** Fixed materials used to build test tools; both are real, datapack-registered materials (unlike the JUnit suite's fake ones) */
  public static final MaterialId IRON = new MaterialId(TConstruct.MOD_ID, "iron");
  public static final MaterialId WOOD = new MaterialId(TConstruct.MOD_ID, "wood");

  /** Position (relative to the test origin) of the smeltery controller built by {@link #buildSmeltery} */
  public static final BlockPos SMELTERY_CONTROLLER = new BlockPos(4, 1, 5);
  /** Facing of the smeltery controller; the multiblock's interior sits one block to the north */
  public static final BlockPos SMELTERY_INTERIOR = new BlockPos(4, 1, 4);
  /** Facing of the smeltery controller */
  public static final Direction SMELTERY_FACING = Direction.SOUTH;
  /** Position of the wall block replaced with a fuel tank when a test requests one */
  public static final BlockPos SMELTERY_FUEL_TANK = new BlockPos(3, 1, 4);
  /** Y level of the smeltery's seared brick floor */
  public static final int SMELTERY_FLOOR_Y = SMELTERY_CONTROLLER.getY() - 1;

  /** Builds a pickaxe (3 parts: head, handle, binding) with fixed iron/wood/iron materials */
  public static ToolStack createPickaxe() {
    return ToolStack.createTool(TinkerTools.pickaxe.get(), ToolDefinitions.PICKAXE,
      MaterialNBT.builder().add(IRON).add(WOOD).add(IRON).build());
  }

  /** Builds a pickaxe using only wood, so no material trait modifier (e.g. iron's magnetic) is present */
  public static ToolStack createWoodenPickaxe() {
    return ToolStack.createTool(TinkerTools.pickaxe.get(), ToolDefinitions.PICKAXE,
      MaterialNBT.builder().add(WOOD).add(WOOD).add(WOOD).build());
  }

  /** Builds a sledge hammer (4 parts) with fixed iron/wood materials, for the AoE breaking test */
  public static ToolStack createSledgeHammer() {
    return ToolStack.createTool(TinkerTools.sledgeHammer.get(), ToolDefinitions.SLEDGE_HAMMER,
      MaterialNBT.builder().add(IRON).add(WOOD).add(IRON).add(IRON).build());
  }

  /**
   * Creates a fresh {@link net.minecraftforge.common.util.FakePlayer} for a test that needs a real
   * {@link ServerPlayer} for a harvest call, without the network {@code Connection} that
   * {@link GameTestHelper#makeMockServerPlayerInLevel} requires (and which a headless gametest server never
   * provides a working one for).
   * <p>
   * Deliberately does <em>not</em> use {@link FakePlayerFactory#getMinecraft}: that method caches a single
   * {@code FakePlayer} instance keyed by level, and every gametest in a batch shares the same {@link
   * net.minecraft.server.level.ServerLevel}. A test that repositions that shared player (as the AoE hammer test
   * does) would otherwise be racing every other test in the batch that also asks for a fake player. A unique
   * {@link GameProfile} per call keeps each test's fake player private to it.
   * @param name  Distinct name for this test's fake player; only needs to be unique within one gametest run
   */
  public static ServerPlayer createFakePlayer(GameTestHelper helper, String name) {
    return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
  }

  /**
   * Builds a minimal valid 3x3 smeltery in the given test's structure: a full seared brick floor, a ring of
   * seared brick walls (one of which becomes a seared fuel tank when requested), and the controller, placed
   * last so its {@code setPlacedBy} queues an immediate structure check.
   * @return  Position of the controller block, same as {@link #SMELTERY_CONTROLLER}
   */
  public static BlockPos buildSmeltery(GameTestHelper helper, boolean withFuelTank) {
    BlockState brick = TinkerSmeltery.searedBricks.get().defaultBlockState();
    for (int x = 3; x <= 5; x++) {
      for (int z = 3; z <= 5; z++) {
        helper.setBlock(new BlockPos(x, SMELTERY_FLOOR_Y, z), brick);
      }
    }
    int wallY = SMELTERY_CONTROLLER.getY();
    for (int x = 3; x <= 5; x++) {
      for (int z = 3; z <= 5; z++) {
        if (x == 4 && z == 4) {
          continue; // interior column, must stay air
        }
        BlockPos pos = new BlockPos(x, wallY, z);
        if (pos.equals(SMELTERY_CONTROLLER)) {
          continue; // placed separately below
        }
        if (withFuelTank && pos.equals(SMELTERY_FUEL_TANK)) {
          helper.setBlock(pos, TinkerSmeltery.searedTank.get(TankType.FUEL_TANK).defaultBlockState());
        } else {
          helper.setBlock(pos, brick);
        }
      }
    }
    helper.setBlock(SMELTERY_CONTROLLER, TinkerSmeltery.smelteryController.get().defaultBlockState()
                                                                             .setValue(ControllerBlock.FACING, SMELTERY_FACING));
    return SMELTERY_CONTROLLER;
  }

  /** Gets the smeltery controller block entity at the position placed by {@link #buildSmeltery} */
  public static SmelteryBlockEntity getSmeltery(GameTestHelper helper) {
    return (SmelteryBlockEntity) helper.getBlockEntity(SMELTERY_CONTROLLER);
  }

  /**
   * Minimal fake {@link IMutableTinkerStationContainer}, letting tests drive modifier recipes directly against a
   * {@link ToolStack} without needing a real tinker station block entity or menu.
   */
  public static class FakeTinkerInventory implements IMutableTinkerStationContainer {
    private ItemStack tinkerable = ItemStack.EMPTY;
    private final ItemStack[] inputs;

    public FakeTinkerInventory(int inputSlots) {
      this.inputs = new ItemStack[inputSlots];
      Arrays.fill(this.inputs, ItemStack.EMPTY);
    }

    public void setTinkerable(ItemStack stack) {
      this.tinkerable = stack;
    }

    @Override
    public ItemStack getTinkerableStack() {
      return tinkerable;
    }

    @Override
    public ItemStack getInput(int index) {
      return index >= 0 && index < inputs.length ? inputs[index] : ItemStack.EMPTY;
    }

    @Override
    public int getInputCount() {
      return inputs.length;
    }

    @Override
    public void setInput(int index, ItemStack stack) {
      if (index >= 0 && index < inputs.length) {
        inputs[index] = stack;
      }
    }

    @Override
    public void giveItem(ItemStack stack) {
      // no-op: tests do not care about leftover items produced by a recipe
    }

    @Nullable
    @Override
    public MaterialRecipe getInputMaterial(int index) {
      return null;
    }
  }
}
