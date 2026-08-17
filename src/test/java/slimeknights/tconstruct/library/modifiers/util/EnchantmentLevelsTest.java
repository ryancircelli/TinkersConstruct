package slimeknights.tconstruct.library.modifiers.util;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the accumulator backing enchantment projection, notably its additive merge semantics */
class EnchantmentLevelsTest extends BaseMcTest {
  private static final Holder<Enchantment> FORTUNE = EnchantmentLevels.holder(Enchantments.BLOCK_FORTUNE);
  private static final Holder<Enchantment> SILK_TOUCH = EnchantmentLevels.holder(Enchantments.SILK_TOUCH);
  private static final Holder<Enchantment> LOOTING = EnchantmentLevels.holder(Enchantments.MOB_LOOTING);


  /* Holders */

  @Test
  void holder_registeredEnchantmentIsCanonical() {
    // registered enchantments resolve to the shared registry reference, so holders may be compared by identity
    assertThat(EnchantmentLevels.holder(Enchantments.BLOCK_FORTUNE)).isSameAs(FORTUNE);
    assertThat(FORTUNE.value()).isSameAs(Enchantments.BLOCK_FORTUNE);
    assertThat(FORTUNE.is(BuiltInRegistries.ENCHANTMENT.getKey(Enchantments.BLOCK_FORTUNE))).isTrue();
  }

  @Test
  void holder_unregisteredEnchantmentComparesByValue() {
    // unregistered enchantments fall back to a direct holder, which is a record so it still behaves as a map key
    Enchantment unregistered = new UnregisteredEnchantment();
    Holder<Enchantment> first = EnchantmentLevels.holder(unregistered);
    Holder<Enchantment> second = EnchantmentLevels.holder(unregistered);
    assertThat(first).isNotSameAs(second).isEqualTo(second);

    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(first, 3);
    assertThat(levels.getLevel(second)).isEqualTo(3);
  }


  /* Empty */

  @Test
  void create_isEmpty() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    assertThat(levels.isEmpty()).isTrue();
    assertThat(levels.size()).isZero();
    assertThat(levels.getLevel(FORTUNE)).isZero();
    assertThat(levels.toMap()).isEmpty();
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
  void addLevel_zeroIsNoOp() {
    // adding zero must not create an entry, otherwise a modifier which computes to level 0 would show an enchantment
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 0);
    assertThat(levels.isEmpty()).isTrue();
    assertThat(levels.toMap()).isEmpty();

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

  @Test
  void rawEnchantmentOverloadsMatchHolders() {
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(Enchantments.BLOCK_FORTUNE, 2);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(2);
    assertThat(levels.getLevel(Enchantments.BLOCK_FORTUNE)).isEqualTo(2);
    levels.setLevel(Enchantments.BLOCK_FORTUNE, 5);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(5);
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
    // enchantment order is visible in serialized NBT, so the accumulator must not reorder
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(LOOTING, 1);
    levels.addLevel(FORTUNE, 2);
    levels.addLevel(SILK_TOUCH, 3);
    // updating an existing level does not move it to the end
    levels.addLevel(LOOTING, 1);
    assertThat(levels.keys()).containsExactly(LOOTING, FORTUNE, SILK_TOUCH);
    assertThat(levels).extracting(Entry::getValue).containsExactly(2, 2, 3);
    assertThat(levels.toMap().keySet()).containsExactly(Enchantments.MOB_LOOTING, Enchantments.BLOCK_FORTUNE, Enchantments.SILK_TOUCH);
  }

  @Test
  void copyOf_preservesOrderAndLevels() {
    Map<Enchantment,Integer> source = new LinkedHashMap<>();
    source.put(Enchantments.MOB_LOOTING, 1);
    source.put(Enchantments.BLOCK_FORTUNE, 2);
    EnchantmentLevels levels = EnchantmentLevels.copyOf(source);
    assertThat(levels.size()).isEqualTo(2);
    assertThat(levels.getLevel(LOOTING)).isEqualTo(1);
    assertThat(levels.getLevel(FORTUNE)).isEqualTo(2);
    assertThat(levels.keys()).containsExactly(LOOTING, FORTUNE);

    // the copy is independent of the source map
    levels.addLevel(FORTUNE, 1);
    assertThat(source).containsExactly(Map.entry(Enchantments.MOB_LOOTING, 1), Map.entry(Enchantments.BLOCK_FORTUNE, 2));
  }

  @Test
  void toMap_isAnIndependentMutableCopy() {
    // vanilla mutates the map it receives, notably in the anvil, so it must not be a live view
    EnchantmentLevels levels = EnchantmentLevels.create();
    levels.addLevel(FORTUNE, 2);
    Map<Enchantment,Integer> map = levels.toMap();
    assertThat(map).containsExactly(Map.entry(Enchantments.BLOCK_FORTUNE, 2));

    map.put(Enchantments.SILK_TOUCH, 1);
    assertThat(levels.getLevel(SILK_TOUCH)).isZero();
    levels.addLevel(LOOTING, 1);
    assertThat(map).doesNotContainKey(Enchantments.MOB_LOOTING);
  }

  @Test
  void copyOfToMap_roundTrips() {
    Map<Enchantment,Integer> source = ImmutableMap.of(Enchantments.BLOCK_FORTUNE, 3, Enchantments.MOB_LOOTING, 1);
    assertThat(EnchantmentLevels.copyOf(source).toMap()).isEqualTo(source);
  }

  @Test
  void fromTag_matchesVanillaDeserialization() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
    EnchantmentHelper.setEnchantments(ImmutableMap.of(Enchantments.BLOCK_FORTUNE, 3), stack);
    ListTag tag = stack.getEnchantmentTags();
    assertThat(EnchantmentLevels.fromTag(tag).toMap()).isEqualTo(EnchantmentHelper.deserializeEnchantments(tag));
    assertThat(EnchantmentLevels.fromStack(stack).toMap()).isEqualTo(EnchantmentHelper.getEnchantments(stack));
    assertThat(EnchantmentLevels.fromStack(stack).getLevel(FORTUNE)).isEqualTo(3);
  }

  @Test
  void fromStack_unenchantedIsEmpty() {
    assertThat(EnchantmentLevels.fromStack(new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty()).isTrue();
  }


  /** Enchantment which was never added to the registry, used to prove the direct holder fallback works */
  private static class UnregisteredEnchantment extends Enchantment {
    UnregisteredEnchantment() {
      super(Rarity.COMMON, Enchantments.BLOCK_FORTUNE.category, Enchantments.BLOCK_FORTUNE.slots);
    }
  }
}
