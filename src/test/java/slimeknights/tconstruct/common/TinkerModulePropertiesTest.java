package slimeknights.tconstruct.common;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.test.TestRegistries;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the fix for T10 SS10's named debt: {@link TinkerModule}'s base item properties used to be shared mutable
 * instances, so the first item constructor that wrote to the builder wrote to every registration after it.
 * <p>
 * This test lives in {@code slimeknights.tconstruct.common} because {@code itemProps()} and
 * {@code unstackableProps()} are {@code protected static} on an abstract class, which same-package code may call.
 * <p>
 * The A/B shape is the point. Asserting only that a plain item has no {@code max_damage} would pass even if the
 * component were never written at all, so each test builds the damageable item first and asserts it *did* get the
 * component, which is what proves the leak had something to leak.
 */
class TinkerModulePropertiesTest extends BaseMcTest {
  /**
   * Constructing an {@link Item} writes to the item registry in 1.21 - {@code Item}'s constructor asks it for an
   * intrusive holder - so a frozen registry makes {@code new Item(...)} throw "Registry is already frozen" rather
   * than merely leaving the item unregistered. 1.20's constructor did no such thing and this test needed no setup.
   */
  @BeforeAll
  static void unfreezeItems() {
    TestRegistries.unfreezeBuiltIns();
  }

  @Test
  void itemProps_areNotSharedBetweenItems() {
    // an item constructor is free to write to the builder it is handed; TieredItem and IModifiable.damageable both
    // call durability(), which is what used to poison every later registration
    Item damageable = register("item_props_damageable", new Item(TinkerModule.itemProps().durability(250)));
    assertThat(damageable.components().has(DataComponents.MAX_DAMAGE))
      .as("control: the item that asked for durability has it")
      .isTrue();

    Item plain = register("item_props_plain", new Item(TinkerModule.itemProps()));
    assertThat(plain.components().has(DataComponents.MAX_DAMAGE))
      .as("an item registered after a damageable one must not inherit its max_damage")
      .isFalse();
  }

  @Test
  void unstackableProps_areNotSharedBetweenItems() {
    Item damageable = register("unstackable_props_damageable", new Item(TinkerModule.unstackableProps().durability(500)));
    assertThat(damageable.components().has(DataComponents.MAX_DAMAGE)).isTrue();

    Item plain = register("unstackable_props_plain", new Item(TinkerModule.unstackableProps()));
    assertThat(plain.components().has(DataComponents.MAX_DAMAGE))
      .as("every tool in the mod registers with unstackableProps, so this is the pairing that actually bit")
      .isFalse();
    assertThat(plain.getDefaultInstance().getMaxStackSize())
      .as("and the property the builder is for still applies")
      .isEqualTo(1);
  }

  @Test
  void eachCallReturnsAFreshBuilder() {
    Properties first = TinkerModule.itemProps();
    Properties second = TinkerModule.itemProps();
    assertThat(first).isNotSameAs(second);
    assertThat(TinkerModule.unstackableProps()).isNotSameAs(TinkerModule.unstackableProps());
  }

  /**
   * Registers a constructed item so it does not leave a dangling intrusive holder.
   * <p>
   * 1.21's {@code Item} constructor asks the item registry for an intrusive holder, and the registry refuses to
   * freeze while any it handed out is still unbound: "Some intrusive holders were not registered". Freezing happens
   * whenever something builds a HolderLookup over the built-ins, which is somewhere else entirely in the suite, so
   * a bare {@code new Item(...)} here failed a different test class than the one that created it.
   */
  private static Item register(String name, Item item) {
    return Registry.register(BuiltInRegistries.ITEM, TConstruct.getResource("test/" + name), item);
  }
}
