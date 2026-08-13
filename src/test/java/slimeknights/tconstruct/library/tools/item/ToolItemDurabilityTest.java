package slimeknights.tconstruct.library.tools.item;

import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolItemDurabilityTest extends ToolItemTest {

  @Test
  void testNewToolDurability() {
    int statDurability = ToolStack.from(testItemStack).getStats().getInt(ToolStats.DURABILITY);

    assertThat(testItemStack.getDamageValue()).isEqualTo(0);
    assertThat(testItemStack.getMaxDamage()).isEqualTo(statDurability);
    assertThat(IsTestItemBroken()).isFalse();
  }

  @Test
  void testSettingDamage() {
    int statDurability = ToolStack.from(testItemStack).getStats().getInt(ToolStats.DURABILITY);

    testItemStack.setDamageValue(1);

    assertThat(testItemStack.getDamageValue()).isEqualTo(1);
    assertThat(testItemStack.getMaxDamage()).isEqualTo(statDurability);
    assertThat(IsTestItemBroken()).isFalse();
  }

  /*
  @Test
  void testDealingDamage() {
    testItemStack.damageItem(10, TestLivingEntity.getTestLivingEntity(), testLivingEntity -> {});

    assertThat(testItemStack.getDamage()).isEqualTo(10);
    assertThat(isTestitemBroken()).isFalse();
  }
  */

  @Test
  void testMaxDamageBreaksTool() {
    ToolStack tool = ToolStack.mutable(testItemStack);
    int statDurability = tool.getStats().getInt(ToolStats.DURABILITY);

    tool.setDamage(statDurability);

    assertThat(tool.getDamage()).isEqualTo(statDurability);
    assertThat(tool.isBroken()).isTrue();
  }

  @Test
  void testMoreThanMaxDamageBreaksTool() {
    int statDurability = ToolStack.from(testItemStack).getStats().getInt(ToolStats.DURABILITY);

    testItemStack.setDamageValue(99999999);

    // the view is taken after the damage is written. ToolStack.from is a snapshot in 1.21 and says so in its own
    // javadoc - it reads DataComponents.DAMAGE once at construction, where 1.20 held a live reference to the
    // stack's NBT tag and saw a later setDamageValue through it. Taking the view first read damage 0 forever.
    IToolStackView tool = ToolStack.from(testItemStack);

    assertThat(tool.getDamage()).isEqualTo(statDurability);
    assertThat(tool.isBroken()).isTrue();
  }

  /*
  @Test
  void testVanillaBreakCallback() {
    AtomicBoolean callbackCalled = new AtomicBoolean(false);
    testItemStack.damageItem(99999, TestLivingEntity.getTestLivingEntity(),
      testLivingEntity -> callbackCalled.set(true));

    // this works because the callback is called synchronously
    assertThat(callbackCalled.get()).isTrue();
    assertThat(isTestitemBroken()).isTrue();
  }
  */

  /*
  @Test
  void testVanillaBreakDoesNotReduceStacksize() {
    testItemStack.damageItem(99999, TestLivingEntity.getTestLivingEntity(),
      testLivingEntity -> {});

    assertThat(isTestitemBroken()).isTrue();
    assertThat(testItemStack.isEmpty()).isFalse();
  }
  */
}
