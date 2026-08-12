package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fixture.MaterialFixture;
import slimeknights.tconstruct.fixture.ToolDefinitionFixture;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolStackTest extends ToolItemTest {
  private final StatsNBT testStatsNBT = StatsNBT.builder()
                                                .set(ToolStats.DURABILITY, 100f)
                                                .set(ToolStats.HARVEST_TIER, Tiers.NETHERITE)
                                                .set(ToolStats.ATTACK_DAMAGE, 2f)
                                                .set(ToolStats.MINING_SPEED, 3f)
                                                .set(ToolStats.ATTACK_SPEED, 5f)
                                                .build();

  @BeforeAll
  static void before() {
    ModifierFixture.init();
  }

  /** Reads the damage component off a stack without going through the item's own damage hooks */
  private static int rawDamage(ItemStack stack) {
    return stack.getOrDefault(DataComponents.DAMAGE, 0);
  }

  /* From */

  @Test
  void from_preservesItem() {
    ToolStack stack = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    assertThat(stack.getItem()).isEqualTo(Items.DIAMOND_PICKAXE);
  }

  @Test
  void from_findToolCoreDefinition() {
    ItemStack stack = new ItemStack(tool);
    IToolStackView tool = ToolStack.from(stack);
    assertThat(tool.getDefinition()).isEqualTo(ToolStackTest.tool.getToolDefinition());
  }

  @Test
  void mutable_writesBackOnUpdateStack() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    tool.setDamage(10);
    tool.updateStack();
    assertThat(rawDamage(testItemStack)).overridingErrorMessage("ToolStack damage was not committed to the original stack").isEqualTo(10);
  }

  @Test
  void mutable_doesNotWriteBackUntilAsked() {
    // this is THE behaviour change of the component migration and the reason ToolStack.mutable exists.
    // In 1.20 the tool shared the stack's tag, so this assertion read isEqualTo(10).
    ToolStack tool = ToolStack.mutable(testItemStack);
    int before = rawDamage(testItemStack);
    tool.setDamage(10);
    assertThat(rawDamage(testItemStack)).overridingErrorMessage("An uncommitted edit reached the stack").isEqualTo(before);
    tool.updateStack();
    assertThat(rawDamage(testItemStack)).isEqualTo(10);
  }

  @Test
  void mutable_updateStackWritesToTheBoundStack() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    assertThat(tool.isSameStack(testItemStack)).isTrue();
    tool.addModifier(ModifierFixture.TEST_1, 1);
    assertThat(tool.updateStack()).isSameAs(testItemStack);
    assertThat(ToolDataComponent.get(testItemStack).upgrades().getLevel(ModifierFixture.TEST_1)).isEqualTo(1);
  }

  @Test
  void from_isNotBoundToTheStack() {
    // a read only view has nowhere to write, so it is nobody's tool and updateStack has no destination
    ItemStack stack = new ItemStack(tool);
    IToolStackView view = ToolStack.from(stack);
    assertThat(view.isSameStack(stack)).isFalse();
    assertThatThrownBy(() -> ((ToolStack)view).updateStack()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void from_doesNotSeeLaterWrites() {
    // in 1.20 both handles shared the stack's tag, so a write through one was visible through the other.
    // They are independent snapshots now, and that is what every read site has to be safe against.
    IToolStackView view = ToolStack.from(testItemStack);
    ToolStack mutable = ToolStack.mutable(testItemStack);
    mutable.setDamage(10);
    mutable.updateStack();
    assertThat(view.getDamage()).overridingErrorMessage("A view taken earlier saw a later write").isEqualTo(0);
    assertThat(ToolStack.from(testItemStack).getDamage()).isEqualTo(10);
  }

  @Test
  void from_doesNotTouchTheStack() {
    // the read path makes no change to the stack at all, including on a tool that has never been built
    ItemStack stack = new ItemStack(tool);
    stack.remove(ToolComponents.TOOL);
    stack.remove(ToolComponents.TOOL_STATS);
    IToolStackView view = ToolStack.from(stack);
    assertThat(stack.has(ToolComponents.TOOL)).overridingErrorMessage("Reading an unbuilt tool wrote a component onto the stack").isFalse();
    assertThat(stack.has(ToolComponents.TOOL_STATS)).isFalse();
    assertThat(view.getStats()).isEqualTo(StatsNBT.EMPTY);
  }

  @Test
  void from_setDamageOnAViewGoesNowhere() {
    // IToolStackView still exposes setDamage, and on a view the write is simply lost. A dev run reports it;
    // this pins the behaviour so a later change that makes the view read only at the type level fails here.
    IToolStackView view = ToolStack.from(testItemStack);
    view.setDamage(10);
    assertThat(rawDamage(testItemStack)).isEqualTo(0);
  }

  @Test
  void from_editingPersistentDataOnAViewGoesNowhere() {
    ResourceLocation key = TConstruct.getResource("test");
    IToolStackView view = ToolStack.from(testItemStack);
    view.getPersistentData().putInt(key, 5);
    assertThat(ToolStack.from(testItemStack).getPersistentData().getInt(key)).isEqualTo(0);
  }

  @Test
  void copyFrom_notSharedData() {
    ToolStack tool = ToolStack.copyFrom(testItemStack);
    tool.setDamage(10);
    assertThat(rawDamage(testItemStack)).overridingErrorMessage("Copied ToolStack damage was transferred to the original stack").isEqualTo(0);
  }

  @Test
  void copy_notSharedData() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    ToolStack copy = tool.copy();
    tool.setDamage(10);
    assertThat(copy.getDamage()).overridingErrorMessage("Copied ToolStack damage was transferred to the original stack").isEqualTo(0);
  }

  @Test
  void deserialize_empty() {
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    assertThat(tool.getItem()).isNotNull();
    assertThat(tool.getDefinition()).isNotNull();
    assertThat(tool.getDamage()).isEqualTo(0);
    assertThat(tool.isBroken()).isFalse();
    assertThat(tool.getMaterials()).isEqualTo(MaterialNBT.EMPTY);
    assertThat(tool.getUpgrades()).isEqualTo(ModifierNBT.EMPTY);
    assertThat(tool.getPersistentData()).isEqualTo(new ToolDataNBT());
    assertThat(tool.getModifiers()).isEqualTo(ModifierNBT.EMPTY);
    assertThat(tool.getStats()).isEqualTo(StatsNBT.EMPTY);
    assertThat(tool.getVolatileData()).isEqualTo(IModDataView.EMPTY);
    // a tool that never rebuilt has no derived component at all, which is distinct from an empty one
    assertThat(tool.isInitialized()).isFalse();
    assertThat(tool.getStatsComponent()).isNull();
  }


  /* Creating and update stacks */

  @Test
  void createStack_setsComponents() {
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    tool.setBrokenRaw(true);
    ItemStack stack = tool.createStack();
    assertThat(ToolDataComponent.get(stack)).isEqualTo(tool.getPersistentComponent());
    assertThat(ToolDataComponent.get(stack).broken()).isTrue();
  }

  @Test
  void updateStack_writesTheComponent() {
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    tool.setDamage(0);
    tool.setBrokenRaw(true);

    ItemStack stack = tool.updateStack(new ItemStack(Items.DIAMOND_PICKAXE));
    assertThat(ToolDataComponent.get(stack)).isEqualTo(tool.getPersistentComponent());
  }

  @Test
  void updateStack_doesNotSaveDerivedData() {
    // tconstruct:tool_stats is network synchronized only. It is on the stack, but it is not in the saved patch,
    // which is the whole reason the tool is split in two.
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    tool.setStats(testStatsNBT);
    ItemStack stack = tool.createStack();
    assertThat(stack.has(ToolComponents.TOOL_STATS)).isTrue();
    assertThat(ToolComponents.TOOL_STATS.get().isTransient()).overridingErrorMessage("tconstruct:tool_stats must never be persistent").isTrue();
    assertThat(ToolComponents.TOOL.get().isTransient()).isFalse();
  }

  @Test
  void updateStack_leavesDerivedDataAloneWhenNeverComputed() {
    // the registries-not-ready fallback: a tool that could not rebuild must not overwrite a good answer with nothing
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    new ToolStatsComponent(testStatsNBT, MultiplierNBT.EMPTY, ModifierNBT.EMPTY, new CompoundTag()).set(stack);
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    assertThat(tool.getStatsComponent()).isNull();
    tool.updateStack(stack);
    assertThat(ToolStatsComponent.get(stack)).isNotNull();
    assertThat(ToolStatsComponent.get(stack).stats()).isEqualTo(testStatsNBT);
  }

  @Test
  void updateStack_validatesItem() {
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    assertThatThrownBy(() -> tool.updateStack(new ItemStack(Items.DIAMOND_AXE))).isInstanceOf(IllegalArgumentException.class);
  }


  /* Damage and broken */

  @Test
  void serialize_damageBroken() {
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinitionFixture.getStandardToolDefinition(), ToolDataComponent.EMPTY);
    tool.setStats(StatsNBT.builder().set(ToolStats.DURABILITY, 100f).build());
    tool.setDamage(1);
    tool.setBrokenRaw(true);

    // broken rides in the tool component, damage is minecraft:damage now
    assertThat(tool.getPersistentComponent().broken()).isTrue();
    ItemStack stack = tool.createStack();
    assertThat(ToolDataComponent.get(stack).broken()).isTrue();
    assertThat(rawDamage(stack)).isEqualTo(1);
  }

  @Test
  void deserialize_damageBroken() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    new ToolDataComponent(MaterialNBT.EMPTY, ModifierNBT.EMPTY, new CompoundTag(), true).set(stack);
    stack.set(DataComponents.DAMAGE, 4);

    ToolStack tool = ToolStack.copyFrom(stack);
    assertThat(tool.getDamageRaw()).isEqualTo(4);
    assertThat(tool.isBroken()).isTrue();
  }

  @Test
  void damage_getDamageValidates() {
    testItemStack.set(DataComponents.DAMAGE, 9999);

    IToolStackView tool = ToolStack.from(testItemStack);
    assertThat(tool.getDamage()).isLessThanOrEqualTo(tool.getStats().getInt(ToolStats.DURABILITY));
  }

  @Test
  void damage_setDamageBreaksTool() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    assertThat(tool.isBroken()).isFalse();
    tool.setDamage(99999);
    assertThat(tool.isBroken()).isTrue();
  }

  @Test
  void damage_setDamageUnbreaksTool() {
    ToolDataComponent.get(testItemStack).withBroken(true).set(testItemStack);

    ToolStack tool = ToolStack.mutable(testItemStack);
    assertThat(tool.isBroken()).isTrue();
    tool.setDamage(10);
    assertThat(tool.isBroken()).isFalse();
  }

  @Test
  void damage_damageTool() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    int oldDamage = tool.getDamage();
    ToolDamageUtil.directDamage(tool, 100, null, null);
    assertThat(tool.getDamage()).isEqualTo(oldDamage + 100);
  }

  @Test
  void damage_repairTool() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    tool.setDamage(50);
    int oldDamage = tool.getDamage();
    ToolDamageUtil.repair(tool, 25);
    assertThat(tool.getDamage()).isEqualTo(oldDamage - 25);
  }

  @Test
  void broken_quickCheck() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    tool.breakTool();
    ItemStack stack = tool.createStack();
    assertThat(ToolDamageUtil.isBroken(stack)).isTrue();
  }


  /* Materials */

  @Test
  void materials_serialize() {
    ToolStack toolStack = ToolStack.from(tool, tool.getToolDefinition(), ToolDataComponent.EMPTY);
    MaterialNBT setMaterials = MaterialNBT.of(MaterialFixture.MATERIAL_WITH_HEAD, MaterialFixture.MATERIAL_WITH_HANDLE, MaterialFixture.MATERIAL_WITH_EXTRA);
    toolStack.setMaterialsRaw(setMaterials);

    assertThat(toolStack.getPersistentComponent().materials()).isEqualTo(setMaterials);
  }

  @Test
  void materials_deserialize() {
    ItemStack stack = new ItemStack(tool);
    MaterialNBT setMaterials = MaterialNBT.of(MaterialFixture.MATERIAL_WITH_HEAD, MaterialFixture.MATERIAL_WITH_HANDLE, MaterialFixture.MATERIAL_WITH_EXTRA);
    ToolDataComponent.get(stack).withMaterials(setMaterials).set(stack);

    IToolStackView tool = ToolStack.from(stack);
    MaterialNBT readMaterials = tool.getMaterials();
    assertThat(readMaterials).isNotEqualTo(MaterialNBT.EMPTY);
    assertThat(readMaterials).isEqualTo(setMaterials);
  }

  @Test
  void materials_replaceMaterial() {
    ToolStack toolStack = ToolStack.mutable(testItemStack);
    assertThat(toolStack.getMaterials().size()).isEqualTo(3);
    assertThat(toolStack.getMaterial(0).get()).isEqualTo(MaterialFixture.MATERIAL_WITH_HEAD);
    assertThat(toolStack.getMaterial(1).get()).isEqualTo(MaterialFixture.MATERIAL_WITH_HANDLE);
    assertThat(toolStack.getMaterial(2).get()).isEqualTo(MaterialFixture.MATERIAL_WITH_EXTRA);

    // ensure it updated and no side-effects
    toolStack.replaceMaterial(0, MaterialFixture.MATERIAL_WITH_ALL_STATS.getIdentifier());
    assertThat(toolStack.getMaterials().size()).isEqualTo(3);
    assertThat(toolStack.getMaterial(0).get()).isEqualTo(MaterialFixture.MATERIAL_WITH_ALL_STATS);
    assertThat(toolStack.getMaterial(1).get()).isEqualTo(MaterialFixture.MATERIAL_WITH_HANDLE);
    assertThat(toolStack.getMaterial(2).get()).isEqualTo(MaterialFixture.MATERIAL_WITH_EXTRA);
  }


  /* Stats */

  @Test
  void stats_serialize() {
    ToolStack tool = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    tool.setStats(testStatsNBT);
    ItemStack stack = tool.createStack();

    ToolStatsComponent derived = ToolStatsComponent.get(stack);
    assertThat(derived).isNotNull();
    assertThat(derived.stats()).isEqualTo(testStatsNBT);
  }

  @Test
  void stats_deserialize() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    new ToolStatsComponent(testStatsNBT, MultiplierNBT.EMPTY, ModifierNBT.EMPTY, new CompoundTag()).set(stack);

    IToolStackView tool = ToolStack.from(stack);
    StatsNBT readStats = tool.getStats();
    assertThat(readStats).isNotEqualTo(StatsNBT.EMPTY);
    assertThat(readStats).isEqualTo(testStatsNBT);
  }

  @Test
  void stats_lowDurabilityUpdatesDurability() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    stack.set(DataComponents.DAMAGE, 100);

    ToolStack tool = ToolStack.mutable(stack);
    tool.setStats(StatsNBT.builder().set(ToolStats.DURABILITY, 50f).build());
    assertThat(tool.getDamageRaw()).isEqualTo(50);
    assertThat(tool.isBroken()).isTrue();
  }


  /* Modifiers */

  @Test
  void modifiers_addModifier() {
    ToolStack toolStack = ToolStack.mutable(testItemStack);
    assertThat(toolStack.getUpgrades().getLevel(ModifierFixture.TEST_1)).isEqualTo(0);
    toolStack.addModifier(ModifierFixture.TEST_1, 1);
    assertThat(toolStack.getUpgrades().getLevel(ModifierFixture.TEST_1)).isEqualTo(1);
  }

  @Test
  void modifiers_serialize() {
    ToolStack toolStack = ToolStack.mutable(testItemStack);
    toolStack.addModifier(ModifierFixture.TEST_1, 1);
    toolStack.updateStack();

    ModifierNBT readModifiers = ToolDataComponent.get(testItemStack).upgrades();
    assertThat(readModifiers).isNotEqualTo(ModifierNBT.EMPTY);
    assertThat(readModifiers).isEqualTo(ModifierNBT.EMPTY.withModifier(ModifierFixture.TEST_1, 1));
  }

  @Test
  void modifiers_deserialize() {
    ModifierNBT setModifiers = ModifierNBT.EMPTY.withModifier(ModifierFixture.TEST_1, 1);
    ToolDataComponent.get(testItemStack).withUpgrades(setModifiers).set(testItemStack);

    IToolStackView tool = ToolStack.from(testItemStack);
    ModifierNBT readModifiers = tool.getUpgrades();
    assertThat(readModifiers).isNotEqualTo(ModifierNBT.EMPTY);
    assertThat(readModifiers).isEqualTo(setModifiers);
  }

  @Test
  void allMods_serialize() {
    ToolStack toolStack = ToolStack.mutable(testItemStack);
    ModifierNBT setModifiers = ModifierNBT.EMPTY.withModifier(ModifierFixture.TEST_1, 1);
    toolStack.setModifiers(setModifiers);
    toolStack.updateStack();

    ToolStatsComponent derived = ToolStatsComponent.get(testItemStack);
    assertThat(derived).isNotNull();
    assertThat(derived.modifiers()).isEqualTo(setModifiers);
  }

  @Test
  void allMods_deserialize() {
    ModifierNBT setModifiers = ModifierNBT.EMPTY.withModifier(ModifierFixture.TEST_1, 1);
    new ToolStatsComponent(StatsNBT.EMPTY, MultiplierNBT.EMPTY, setModifiers, new CompoundTag()).set(testItemStack);

    IToolStackView tool = ToolStack.from(testItemStack);
    ModifierNBT readModifiers = tool.getModifiers();
    assertThat(readModifiers).isNotEqualTo(ModifierNBT.EMPTY);
    assertThat(readModifiers).isEqualTo(setModifiers);
  }


  /* Mod data */

  @Test
  void persistentModData_serialize() {
    ToolStack toolStack = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    assertThat(toolStack.getPersistentComponent().data().isEmpty()).isTrue();

    ToolDataNBT modData = toolStack.getPersistentData();
    modData.setSlots(SlotType.UPGRADE, 1);

    assertThat(toolStack.getPersistentComponent().data()).isEqualTo(modData.getData());
  }

  @Test
  void persistentModData_deserialize() {
    ToolDataNBT modData = new ToolDataNBT();
    modData.setSlots(SlotType.UPGRADE, 1);
    ToolDataComponent.get(testItemStack).withData(modData.getData().copy()).set(testItemStack);

    IToolStackView toolStack = ToolStack.from(testItemStack);
    assertThat(toolStack.getPersistentData().getSlots(SlotType.UPGRADE)).isEqualTo(1);
  }

  @Test
  void persistentModData_isCopiedOffTheComponent() {
    // the component is a value; a tool must never edit the compound it was handed or two stacks would share it
    ToolDataNBT modData = new ToolDataNBT();
    modData.setSlots(SlotType.UPGRADE, 1);
    ToolDataComponent component = new ToolDataComponent(MaterialNBT.EMPTY, ModifierNBT.EMPTY, modData.getData().copy(), false);
    component.set(testItemStack);

    ToolStack tool = ToolStack.mutable(testItemStack);
    tool.getPersistentData().setSlots(SlotType.UPGRADE, 5);
    assertThat(component.data()).isEqualTo(modData.getData());
    assertThat(ToolDataComponent.get(testItemStack).data()).isEqualTo(modData.getData());
    tool.updateStack();
    assertThat(ToolDataComponent.get(testItemStack).data()).isNotEqualTo(modData.getData());
  }

  @Test
  void volatileModData_serialize() {
    ToolStack toolStack = ToolStack.from(Items.DIAMOND_PICKAXE, ToolDefinition.EMPTY, ToolDataComponent.EMPTY);
    ToolDataNBT modData = new ToolDataNBT();
    modData.setSlots(SlotType.UPGRADE, 1);
    toolStack.setVolatileModData(modData);
    toolStack.setStats(StatsNBT.EMPTY);

    ToolStatsComponent derived = toolStack.getStatsComponent();
    assertThat(derived).isNotNull();
    assertThat(derived.volatileData()).isEqualTo(modData.getData());
  }

  @Test
  void volatileModData_deserialize() {
    ToolDataNBT modData = new ToolDataNBT();
    modData.setSlots(SlotType.UPGRADE, 1);
    new ToolStatsComponent(StatsNBT.EMPTY, MultiplierNBT.EMPTY, ModifierNBT.EMPTY, modData.getData().copy()).set(testItemStack);

    IToolStackView toolStack = ToolStack.from(testItemStack);
    assertThat(toolStack.getVolatileData().getSlots(SlotType.UPGRADE)).isEqualTo(1);
  }

  @Test
  void persistentModData_editingAReadTagDoesNotReachTheTool() {
    ResourceLocation key = TConstruct.getResource("test");
    CompoundTag stored = new CompoundTag();
    stored.putInt("value", 1);
    ToolStack toolStack = ToolStack.mutable(testItemStack);
    toolStack.getPersistentData().put(key, stored);

    // editing the tag the read handed back leaves the tool alone (T-A3)
    CompoundTag read = toolStack.getPersistentData().getCompound(key);
    read.putInt("value", 2);
    assertThat(toolStack.getPersistentData().getCompound(key).getInt("value")).isEqualTo(1);

    // storing it is what lands the edit, and updateStack is what puts it on the stack
    toolStack.getPersistentData().put(key, read);
    toolStack.updateStack();
    assertThat(ToolDataComponent.get(testItemStack).data().getCompound(key.toString()).getInt("value")).isEqualTo(2);
  }


  /* Raw data */

  @Test
  void rawData_landsOnCustomData() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    tool.getRawData().putBoolean("theoneprobe", true);
    tool.updateStack();
    assertThat(testItemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("theoneprobe")).isTrue();
  }


  /* Rebuild */

  @Test
  void setMaterials_refreshesData() {
    ToolStack toolStack = ToolStack.from(tool, tool.getToolDefinition(), ToolDataComponent.EMPTY);
    assertThat(toolStack.getStats()).isEqualTo(StatsNBT.EMPTY);

    MaterialNBT materials = MaterialNBT.of(MaterialFixture.MATERIAL_WITH_HEAD, MaterialFixture.MATERIAL_WITH_HANDLE, MaterialFixture.MATERIAL_WITH_EXTRA);
    toolStack.setMaterials(materials);
    assertThat(toolStack.getStats()).isNotEqualTo(StatsNBT.EMPTY);
  }

  @Test
  void addModifier_refreshesData() {
    ToolStack toolStack = ToolStack.from(tool, tool.getToolDefinition(), ToolDataComponent.EMPTY);
    // need materials for rebuild
    toolStack.setMaterialsRaw(MaterialNBT.of(MaterialFixture.MATERIAL_WITH_HEAD, MaterialFixture.MATERIAL_WITH_HANDLE, MaterialFixture.MATERIAL_WITH_EXTRA));
    // set some data that will get cleared out
    ToolDataNBT volatileData = new ToolDataNBT();
    volatileData.setSlots(SlotType.UPGRADE, 4);
    toolStack.setVolatileModData(volatileData);
    assertThat(toolStack.getModifiers()).isEqualTo(ModifierNBT.EMPTY);

    toolStack.addModifier(ModifierFixture.TEST_1, 2);
    assertThat(toolStack.getVolatileData()).isNotEqualTo(volatileData);
    assertThat(toolStack.getModifiers().getLevel(ModifierFixture.TEST_1)).isEqualTo(2);
  }
}
