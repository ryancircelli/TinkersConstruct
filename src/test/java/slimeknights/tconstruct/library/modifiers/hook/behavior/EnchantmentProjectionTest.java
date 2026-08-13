package slimeknights.tconstruct.library.modifiers.hook.behavior;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.util.EnchantmentLevels;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the enchantment projection surface: the two statics that {@code ModifiableItem},
 * {@code ModifiableArmorItem} and {@code ModifiableLauncherItem} delegate their
 * {@code IItemExtension#getEnchantmentLevel} and {@code #getAllEnchantments} overrides to.
 * <p>
 * This earns its own test in 1.21 because NeoForge widened what those overrides reach.
 * {@code EnchantmentHelper#runIterationOnItem} - the entry point for every enchantment <em>effect</em> in the game -
 * is patched to read {@code stack.getAllEnchantments(lookup)} rather than the {@code minecraft:enchantments}
 * component, so a projected enchantment now drives effects and not merely tooltips and the handful of sites Forge
 * routed. The merge rules themselves belong to {@link EnchantmentLevels} and are covered by
 * {@code EnchantmentLevelsTest}; what is covered here is the projection - that the chain starts from what the stack
 * actually stores, that a modifier's contribution adds on top, that a negative contribution can cancel a stored level
 * without reporting a negative, and that the two halves of the surface agree, which {@code IItemExtension} states as
 * prose on both methods and nothing else enforces.
 */
class EnchantmentProjectionTest extends ToolItemTest {
  /** Enchantment the fixture modifiers project */
  private static final Holder<Enchantment> PROJECTED = enchantment("projected");
  /** Enchantment nothing projects, for the stored-only case */
  private static final Holder<Enchantment> STORED_ONLY = enchantment("stored_only");

  /** Modifier granting one level of {@link #PROJECTED} per modifier level */
  private static Modifier projecting;
  /** Modifier removing one level of {@link #PROJECTED} per modifier level, so a negative reaches the accumulator */
  private static Modifier cancelling;

  /**
   * Creates a holder of a standalone enchantment.
   * Enchantments are a datapack registry in 1.21, so a unit test has no registry to ask for one; a direct holder is a
   * record and behaves as a map key, which is all the projection chain asks of a holder.
   */
  private static Holder<Enchantment> enchantment(String name) {
    return Holder.direct(new Enchantment(
      Component.literal(name),
      Enchantment.definition(HolderSet.empty(), 1, 3, Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.MAINHAND),
      HolderSet.empty(), DataComponentMap.EMPTY));
  }

  @BeforeAll
  static void setUpModifiers() {
    projecting = ModifierFixture.register("projecting", ModuleHookMap.builder()
      .addHook(new EnchantmentGranting(PROJECTED, 1), ModifierHooks.ENCHANTMENTS).build());
    cancelling = ModifierFixture.register("cancelling", ModuleHookMap.builder()
      .addHook(new EnchantmentGranting(PROJECTED, -1), ModifierHooks.ENCHANTMENTS).build());
  }

  /** Grants a fixed amount of one enchantment per modifier level, implementing both halves of the interface */
  private record EnchantmentGranting(Holder<Enchantment> enchantment, int amount) implements EnchantmentModifierHook {
    @Override
    public int updateEnchantmentLevel(IToolStackView tool, ModifierEntry modifier, Holder<Enchantment> query, int level) {
      return query.equals(enchantment) ? level + amount * modifier.getLevel() : level;
    }

    @Override
    public void updateEnchantments(IToolStackView tool, ModifierEntry modifier, EnchantmentLevels enchantments) {
      enchantments.addLevel(enchantment, amount * modifier.getLevel());
    }
  }

  /** Builds a test tool carrying the given modifier, and optionally a stored enchantment level */
  private ItemStack toolWith(Modifier modifier, int level, int storedLevel) {
    ToolStack tool = ToolStack.copyFrom(buildTestTool(EnchantmentProjectionTest.tool));
    tool.addModifier(modifier.getId(), level);
    tool.rebuildStats();
    ItemStack stack = tool.createStack();
    if (storedLevel > 0) {
      stack.set(DataComponents.ENCHANTMENTS, component(PROJECTED, storedLevel));
    }
    return stack;
  }

  /** Builds an enchantment component from a single enchantment */
  private static ItemEnchantments component(Holder<Enchantment> enchantment, int level) {
    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    mutable.set(enchantment, level);
    return mutable.toImmutable();
  }

  @Test
  void getAllEnchantments_startsFromTheStoredComponent() {
    ItemStack stack = buildTestTool(tool);
    stack.set(DataComponents.ENCHANTMENTS, component(STORED_ONLY, 3));
    assertThat(EnchantmentModifierHook.getAllEnchantments(stack).getLevel(STORED_ONLY)).isEqualTo(3);
  }

  @Test
  void getAllEnchantments_addsTheModifiersContribution() {
    assertThat(EnchantmentModifierHook.getAllEnchantments(toolWith(projecting, 2, 0)).getLevel(PROJECTED)).isEqualTo(2);
  }

  @Test
  void getAllEnchantments_addsOnTopOfTheStoredLevel() {
    // additive, not max wins - T-A6's rule 1, reaching the item boundary
    assertThat(EnchantmentModifierHook.getAllEnchantments(toolWith(projecting, 2, 1)).getLevel(PROJECTED)).isEqualTo(3);
  }

  @Test
  void getAllEnchantments_cancelledOutEnchantmentIsAbsentRatherThanZero() {
    ItemEnchantments result = EnchantmentModifierHook.getAllEnchantments(toolWith(cancelling, 1, 1));
    // removeNonPositive runs once at the end of the chain, so the key is dropped rather than reported as zero
    assertThat(result.keySet()).doesNotContain(PROJECTED);
  }

  @Test
  void getEnchantmentLevel_neverGoesBelowZero() {
    // two levels of cancelling against one stored level reaches -1 inside the chain
    assertThat(EnchantmentModifierHook.getEnchantmentLevel(toolWith(cancelling, 2, 1), PROJECTED)).isZero();
  }

  @Test
  void getEnchantmentLevel_agreesWithGetAllEnchantments() {
    // the two run different code paths, one per enchantment and one over an accumulator, so they can drift
    ItemStack stack = toolWith(projecting, 3, 2);
    ItemEnchantments all = EnchantmentModifierHook.getAllEnchantments(stack);
    for (Holder<Enchantment> enchantment : all.keySet()) {
      assertThat(EnchantmentModifierHook.getEnchantmentLevel(stack, enchantment))
        .as("level of %s", enchantment)
        .isEqualTo(all.getLevel(enchantment));
    }
    assertThat(all.getLevel(PROJECTED)).isEqualTo(5);
  }

  @Test
  void getAllEnchantments_readsTheStoredComponentAndNotItself() {
    // the chain reads the component through EnchantmentLevels.fromStack, which goes to getTagEnchantments rather than
    // back through ItemStack#getAllEnchantments. That is the only reason the item override does not recurse, and it is
    // one rename away from being broken silently.
    ItemStack stack = toolWith(projecting, 1, 0);
    assertThat(EnchantmentModifierHook.getAllEnchantments(stack).getLevel(PROJECTED)).isEqualTo(1);
    assertThat(stack.getTagEnchantments().getLevel(PROJECTED)).isZero();
  }
}
