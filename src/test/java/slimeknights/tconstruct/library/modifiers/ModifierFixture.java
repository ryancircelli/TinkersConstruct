package slimeknights.tconstruct.library.modifiers;

import slimeknights.tconstruct.library.module.ModuleHookMap;

public class ModifierFixture {
  public static final ModifierId TEST_1 = new ModifierId("test", "modifier_1");
  public static final ModifierId TEST_2 = new ModifierId("test", "modifier_2");

  public static final Modifier TEST_MODIFIER_1 = new Modifier();
  public static final Modifier TEST_MODIFIER_2 = new Modifier();

  private static boolean init = false;

  public static void init() {
    if (init) {
      return;
    }
    init = true;
    TEST_MODIFIER_1.setId(TEST_1);
    TEST_MODIFIER_2.setId(TEST_2);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_1, TEST_MODIFIER_1);
    ModifierManager.INSTANCE.staticModifiers.put(TEST_2, TEST_MODIFIER_2);
    ModifierManager.INSTANCE.dynamicModifiersLoaded = true;
  }

  /**
   * Registers a static test modifier carrying the given hooks, for a test whose subject is a hook rather than the
   * modifier list.
   * @apiNote  The hook map is built by the caller and passed to the constructor rather than registered from an
   * override of {@code registerHooks}: that method runs from {@link Modifier}'s own constructor, before an anonymous
   * subclass has assigned the fields it captured, so anything it read would be null.
   */
  public static Modifier register(String name, ModuleHookMap hooks) {
    Modifier modifier = new Modifier(hooks) {};
    modifier.setId(new ModifierId("test", name));
    ModifierManager.INSTANCE.staticModifiers.put(modifier.getId(), modifier);
    ModifierManager.INSTANCE.dynamicModifiersLoaded = true;
    return modifier;
  }
}
