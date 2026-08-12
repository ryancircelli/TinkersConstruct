package slimeknights.tconstruct.library.modifiers.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the accumulator backing enchantment projection, notably its additive merge semantics */
class EnchantmentLevelsTest extends BaseMcTest {
  private static final Holder<Enchantment> FORTUNE = holder("fortune");
  private static final Holder<Enchantment> SILK_TOUCH = holder("silk_touch");
  private static final Holder<Enchantment> LOOTING = holder("looting");

  /**
   * Creates a holder of a standalone enchantment.
   * Enchantments are a datapack registry in 1.21, so a unit test has no registry to ask for one; a direct holder is a record and behaves as a map key,
   * which is all this class asks of a holder.
   */
  private static Holder<Enchantment> holder(String name) {
    return Holder.direct(new Enchantment(
      Component.literal(name),
      Enchantment.definition(HolderSet.empty(), 1, 3, Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.MAINHAND),
      HolderSet.empty(), DataComponentMap.EMPTY));
  }

  /** Builds an enchantment component from a single enchantment */
  private static ItemEnchantments component(Holder<Enchantment> enchantment, int level) {
    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    mutable.set(enchantment, level);
    return mutable.toImmutable();
  }


  /* Holders */

  @Test
  void holdersAreComparedByEquality() {
    // the accumulator never creates a holder, it only stores the ones handed to it, so equal holders must be the same key
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 3);
    assertThat(levels.getLevel(Holder.direct(FORTUNE.value()))).isEqualTo(3);
    assertThat(levels.getLevel(SILK_TOUCH)).isZero();
  }


  /* Empty */

  @Test
  void create_isEmpty() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    assertThat(levels.isEmpty()).isTrue();
    assertThat(levels.size()).isZero();
    assertThat(levels.getLevel(FORTUNE)).isZero();
    assertThat(levels.toComponent().isEmpty()).isTrue();
    assertThat(levels).isEmpty();
  }

  @Test
  void removeNonPositive_emptyStaysEmpty() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.removeNonPositive();
    assertThat(levels.isEmpty()).isTrue();
  }


  /* Merge semantics */

  @Test
  void addLevel_startsFromZero() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(2);
  }

  @Test
  void addLevel_sumsInsteadOfReplacing() {
    // several modifiers granting the same enchantment stack, they do not fight over the largest level
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 3);
    levels.addLevel(FORTUNE, 1);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(4);
  }

  @Test
  void addLevel_negativeCancelsOut() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    levels.addLevel(FORTUNE, -3);
    // negatives are allowed to survive until cleanup so a later addition can bring it back
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(-1);
    levels.addLevel(FORTUNE, 2);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(1);
  }

  @Test
  void addLevel_negativeIsWhyThisIsNotItemEnchantmentsMutable() {
    // vanilla's accumulator drops a level of 0 or less on the way in, so the same chain there ends two levels richer
    ItemEnchantments.Mutable vanilla = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    vanilla.set(FORTUNE, 2);
    vanilla.set(FORTUNE, vanilla.getLevel(FORTUNE) - 3);
    vanilla.set(FORTUNE, vanilla.getLevel(FORTUNE) + 2);
    assertThat(vanilla.getLevel(FORTUNE)).isEqualTo(2);

    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    levels.addLevel(FORTUNE, -3);
    levels.addLevel(FORTUNE, 2);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(1);
  }

  @Test
  void addLevel_zeroIsNoOp() {
    // adding zero must not create an entry, otherwise a modifier which computes to level 0 would show an enchantment
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 0);
    assertThat(levels.isEmpty()).isTrue();
    assertThat(levels.toComponent().isEmpty()).isTrue();

    levels.addLevel(FORTUNE, 2);
    levels.addLevel(FORTUNE, 0);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(2);
    assertThat(levels.size()).isEqualTo(1);
  }

  @Test
  void addLevel_independentPerEnchantment() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    levels.addLevel(SILK_TOUCH, 1);
    levels.addLevel(FORTUNE, 1);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(3);
    assertThat(levels.getLevel(SILK_TOUCH)).isEqualTo(1);
    assertThat(levels.size()).isEqualTo(2);
  }

  @Test
  void setLevel_replaces() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 3);
    levels.setLevel(FORTUNE, 1);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(1);
    // unlike adding, setting zero does create an entry, which cleanup then drops
    levels.setLevel(SILK_TOUCH, 0);
    assertThat(levels.size()).isEqualTo(2);
  }

  @Test
  void remove_dropsEntry() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 3);
    levels.remove(FORTUNE);
    assertThat(levels.getLevel(FORTUNE)).isZero();
    assertThat(levels.isEmpty()).isTrue();
  }


  /* Cleanup */

  @Test
  void removeNonPositive_dropsZeroAndNegative() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    levels.setLevel(SILK_TOUCH, 0);
    levels.setLevel(LOOTING, -3);
    levels.removeNonPositive();
    assertThat(levels.size()).isEqualTo(1);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(2);
    assertThat(levels.getLevel(SILK_TOUCH)).isZero();
    assertThat(levels.getLevel(LOOTING)).isZero();
  }


  /* Iteration and conversion */

  @Test
  void iterationIsInsertionOrdered() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(LOOTING, 1);
    levels.addLevel(FORTUNE, 2);
    levels.addLevel(SILK_TOUCH, 3);
    // updating an existing level does not move it to the end
    levels.addLevel(LOOTING, 1);
    assertThat(levels.keys()).containsExactly(LOOTING, FORTUNE, SILK_TOUCH);
    assertThat(levels).extracting(Entry::getValue).containsExactly(2, 2, 3);
  }

  @Test
  void copyOf_preservesLevels() {
    ItemEnchantments source = component(FORTUNE, 2);
    EnchantmentLevels levels = EnchantmentLevels.copyOf(source);
    assertThat(levels.size()).isEqualTo(1);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(2);

    // the copy is independent of the component it came from
    levels.addLevel(FORTUNE, 1);
    assertThat(source.getLevel(FORTUNE)).isEqualTo(2);
  }

  @Test
  void copyOfToComponent_roundTrips() {
    ItemEnchantments source = component(FORTUNE, 3);
    assertThat(EnchantmentLevels.copyOf(source).toComponent()).isEqualTo(source);
  }

  @Test
  void toComponent_dropsNonPositiveLevels() {
    // the component cannot hold them, which is the other half of why the accumulator exists
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    levels.setLevel(SILK_TOUCH, -1);
    ItemEnchantments component = levels.toComponent();
    assertThat(component.getLevel(FORTUNE)).isEqualTo(2);
    assertThat(component.keySet()).containsExactly(FORTUNE);
  }

  @Test
  void toComponent_clampsToTheVanillaMaximum() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 300);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(300);
    assertThat(levels.toComponent().getLevel(FORTUNE)).isEqualTo(255);
  }

  @Test
  void toComponent_dropsEnchantmentsRemovedFromTheAccumulator() {
    EnchantmentLevels levels = EnchantmentLevels.copyOf(component(FORTUNE, 3));
    levels.remove(FORTUNE);
    levels.addLevel(SILK_TOUCH, 1);
    ItemEnchantments component = levels.toComponent();
    assertThat(component.getLevel(FORTUNE)).isZero();
    assertThat(component.getLevel(SILK_TOUCH)).isEqualTo(1);
  }

  @Test
  void toComponent_keepsTheTooltipFlag() {
    EnchantmentLevels levels = EnchantmentLevels.copyOf(component(FORTUNE, 3).withTooltip(false));
    levels.addLevel(FORTUNE, 1);
    assertThat(levels.toComponent()).isEqualTo(component(FORTUNE, 4).withTooltip(false));
  }

  @Test
  void fromStack_readsTheEnchantmentComponent() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    stack.set(DataComponents.ENCHANTMENTS, component(FORTUNE, 3));
    assertThat(EnchantmentLevels.fromStack(stack).getLevel(FORTUNE)).isEqualTo(3);
    assertThat(EnchantmentLevels.fromStack(stack).toComponent()).isEqualTo(stack.getTagEnchantments());
  }

  @Test
  void fromStack_unenchantedIsEmpty() {
    assertThat(EnchantmentLevels.fromStack(new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty()).isTrue();
  }
}
