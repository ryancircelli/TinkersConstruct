package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.fixture.MaterialFixture;
import slimeknights.tconstruct.library.materials.RandomMaterial;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBuildHandlerTest extends ToolItemTest {
  /** Materials for a tool made entirely of the slot removing material */
  private static final MaterialNBT SLOT_TRAIT_MATERIALS = MaterialNBT.of(
    MaterialFixture.MATERIAL_WITH_SLOT_TRAIT, MaterialFixture.MATERIAL_WITH_SLOT_TRAIT, MaterialFixture.MATERIAL_WITH_SLOT_TRAIT);

  /** Ensures the fixture material really does make an invalid tool, so the tests below are not vacuous */
  @Test
  void createTool_slotTraitGoesNegative() {
    ToolStack built = ToolStack.createTool(tool, tool.getToolDefinition(), SLOT_TRAIT_MATERIALS);
    assertThat(built.getFreeSlots(SlotType.ABILITY)).isNegative();
    assertThat(built.tryValidate()).isNotNull();
  }

  @Test
  void ensureValidSlots_clampsNegativeSlots() {
    ToolStack built = ToolBuildHandler.ensureValidSlots(ToolStack.createTool(tool, tool.getToolDefinition(), SLOT_TRAIT_MATERIALS));
    assertThat(built.getFreeSlots(SlotType.ABILITY)).isZero();
    assertThat(built.tryValidate()).isNull();
  }

  @Test
  void ensureValidSlots_leavesValidToolAlone() {
    ToolStack built = ToolStack.from(testItemStack);
    int upgrades = built.getFreeSlots(SlotType.UPGRADE);
    int abilities = built.getFreeSlots(SlotType.ABILITY);
    ToolBuildHandler.ensureValidSlots(built);
    assertThat(built.getFreeSlots(SlotType.UPGRADE)).isEqualTo(upgrades);
    assertThat(built.getFreeSlots(SlotType.ABILITY)).isEqualTo(abilities);
    assertThat(built.getPersistentData().getSlots(SlotType.ABILITY)).isZero();
  }

  /** The loot path proper: a randomly built tool must never come out with negative slots */
  @Test
  void buildToolRandomMaterials_neverNegativeSlots() {
    ToolStack built = ToolBuildHandler.buildToolRandomMaterials(
      tool, RandomMaterial.fixed(MaterialFixture.MATERIAL_WITH_SLOT_TRAIT.getIdentifier()), RandomSource.create(0));
    assertThat(built.getMaterials().get(0).getId()).isEqualTo(MaterialFixture.MATERIAL_WITH_SLOT_TRAIT.getIdentifier());
    assertThat(built.getFreeSlots(SlotType.ABILITY)).isZero();
    assertThat(built.tryValidate()).isNull();
  }
}
