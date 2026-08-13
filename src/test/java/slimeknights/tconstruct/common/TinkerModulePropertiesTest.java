package slimeknights.tconstruct.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

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
  @Test
  void itemProps_areNotSharedBetweenItems() {
    // an item constructor is free to write to the builder it is handed; TieredItem and IModifiable.damageable both
    // call durability(), which is what used to poison every later registration
    Item damageable = new Item(TinkerModule.itemProps().durability(250));
    assertThat(damageable.components().has(DataComponents.MAX_DAMAGE))
      .as("control: the item that asked for durability has it")
      .isTrue();

    Item plain = new Item(TinkerModule.itemProps());
    assertThat(plain.components().has(DataComponents.MAX_DAMAGE))
      .as("an item registered after a damageable one must not inherit its max_damage")
      .isFalse();
  }

  @Test
  void unstackableProps_areNotSharedBetweenItems() {
    Item damageable = new Item(TinkerModule.unstackableProps().durability(500));
    assertThat(damageable.components().has(DataComponents.MAX_DAMAGE)).isTrue();

    Item plain = new Item(TinkerModule.unstackableProps());
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
}
