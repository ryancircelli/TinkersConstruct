package slimeknights.tconstruct.library.modifiers;

import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.SlotType;

public class ModifierFixture {
  public static final ModifierId TEST_1 = new ModifierId("test", "modifier_1");
  public static final ModifierId TEST_2 = new ModifierId("test", "modifier_2");
  /** Modifier taking an ability slot, used to build a tool with negative slots */
  public static final ModifierId TEST_REMOVE_ABILITY_SLOT = new ModifierId("test", "remove_ability_slot");

  public static final Modifier TEST_MODIFIER_1 = new Modifier();
  public static final Modifier TEST_MODIFIER_2 = new Modifier();
  public static final Modifier TEST_MODIFIER_REMOVE_ABILITY_SLOT = new Modifier() {
    @Override
    protected void registerHooks(Builder hookBuilder) {
      hookBuilder.addModule(ModifierSlotModule.slot(SlotType.ABILITY).eachLevel(-1));
    }
  };

  private static boolean init = false;

  public static void init() {
    if (init) {
      return;
    }
    init = true;
    TEST_MODIFIER_1.setId(TEST_1);
    TEST_MODIFIER_2.setId(TEST_2);
    TEST_MODIFIER_REMOVE_ABILITY_SLOT.setId(TEST_REMOVE_ABILITY_SLOT);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_1, TEST_MODIFIER_1);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_2, TEST_MODIFIER_2);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_REMOVE_ABILITY_SLOT, TEST_MODIFIER_REMOVE_ABILITY_SLOT);
    ModifierManager.INSTANCE.dynamicModifiersLoaded = true;
  }
}
