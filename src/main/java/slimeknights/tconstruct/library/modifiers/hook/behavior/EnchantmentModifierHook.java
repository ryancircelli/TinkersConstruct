package slimeknights.tconstruct.library.modifiers.hook.behavior;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.mining.BlockHarvestModifierHook;
import slimeknights.tconstruct.library.modifiers.util.EnchantmentLevels;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Collection;
import java.util.Map;

/**
 * This interface exposes two methods, {@link #updateEnchantmentLevel(IToolStackView, ModifierEntry, Holder, int)} and {@link #updateEnchantments(IToolStackView, ModifierEntry, EnchantmentLevels)}
 * to allow tools to claim to have enchantments to vanilla APIs without modifying NBT. For performance reasons we don't simply have one hook call the other, but their behavior must be consistent.
 * That is, whatever change you make to the level in {@link #updateEnchantmentLevel(IToolStackView, ModifierEntry, Holder, int)} must also be reflected in the accumulator in {@link #updateEnchantments(IToolStackView, ModifierEntry, EnchantmentLevels)}.
 */
public interface EnchantmentModifierHook {
  /**
   * Gets the enchantment level for the given tool
   * @param tool         Tool instance
   * @param modifier     Modifier instance
   * @param enchantment  Enchantment being queried
   * @param level        Level before this enchantment makes any changes. May be negative, will be capped to 0+ after the hook runs.
   * @return Enchantment level, typically added to {@code level} instead of replacing it. May be negative, will be capped to 0+ after the hook runs.
   */
  int updateEnchantmentLevel(IToolStackView tool, ModifierEntry modifier, Holder<Enchantment> enchantment, int level);

  /**
   * Adds all enchantment modifications made by this tool to the accumulator.
   * You may reduce a level to a negative, all levels 0 or less will be removed after the hook runs.
   * @param tool          Tool instance
   * @param modifier      Modifier instance
   * @param enchantments  A mutable accumulator to add enchantments from this modifier. May contain negatives.
   * @see EnchantmentLevels#addLevel(Holder, int)
   */
  void updateEnchantments(IToolStackView tool, ModifierEntry modifier, EnchantmentLevels enchantments);

  /**
   * Gets the enchantment level for the given tool
   * @param stack        Item stack instance
   * @param enchantment  Enchantment to query
   * @return  Enchantment level
   */
  static int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
    int level = EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
    Holder<Enchantment> holder = EnchantmentLevels.holder(enchantment);
    IToolStackView tool = ToolStack.from(stack);
    for (ModifierEntry entry : tool.getModifierList()) {
      level = entry.getHook(ModifierHooks.ENCHANTMENTS).updateEnchantmentLevel(tool, entry, holder, level);
    }
    // we allow hooks to return negative, such as to cancel out an enchantment
    return Math.max(level, 0);
  }

  /**
   * Gets all enchantments on the given stack
   * @param stack  Stack instance
   * @return  All contained enchantments
   */
  static Map<Enchantment,Integer> getAllEnchantments(ItemStack stack) {
    EnchantmentLevels enchantments = EnchantmentLevels.fromStack(stack);
    IToolStackView tool = ToolStack.from(stack);
    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getHook(ModifierHooks.ENCHANTMENTS).updateEnchantments(tool, entry, enchantments);
    }
    // we allow hooks to return negative, such as to cancel out an enchantment
    enchantments.removeNonPositive();
    return enchantments.toMap();
  }

  /** Merger that combines all modules together */
  record AllMerger(Collection<EnchantmentModifierHook> modules) implements EnchantmentModifierHook {
    @Override
    public int updateEnchantmentLevel(IToolStackView tool, ModifierEntry modifier, Holder<Enchantment> enchantment, int level) {
      for (EnchantmentModifierHook module : modules) {
        level = module.updateEnchantmentLevel(tool, modifier, enchantment, level);
      }
      return level;
    }

    @Override
    public void updateEnchantments(IToolStackView tool, ModifierEntry modifier, EnchantmentLevels enchantments) {
      for (EnchantmentModifierHook module : modules) {
        module.updateEnchantments(tool, modifier, enchantments);
      }
    }
  }

  /**
   * Simple implementation of this hook with just a single enchantment
   */
  @SuppressWarnings("unused") // API
  interface SingleEnchantment extends EnchantmentModifierHook {
    /**
     * Gets the enchantment this hook adds to the tool
     * @param tool      Tool instance
     * @param modifier  Modifier instance
     * @return  Enchantment for this hook to add
     */
    Holder<Enchantment> getEnchantment(IToolStackView tool, ModifierEntry modifier);

    /**
     * Gets the level of the enchantment to add
     * @param tool      Tool instance
     * @param modifier  Modifier instance
     * @return  Level of the enchantment to add
     */
    default int getEnchantmentLevel(IToolStackView tool, ModifierEntry modifier) {
      return modifier.getLevel();
    }

    @Override
    default int updateEnchantmentLevel(IToolStackView tool, ModifierEntry modifier, Holder<Enchantment> enchantment, int level) {
      if (enchantment.value() == getEnchantment(tool, modifier).value()) {
        level += getEnchantmentLevel(tool, modifier);
      }
      return level;
    }

    @Override
    default void updateEnchantments(IToolStackView tool, ModifierEntry modifier, EnchantmentLevels enchantments) {
      enchantments.addLevel(getEnchantment(tool, modifier), getEnchantmentLevel(tool, modifier));
    }
  }

  /** Combination of {@link SingleEnchantment} with {@link BlockHarvestModifierHook.MarkHarvesting} */
  interface SingleHarvestEnchantment extends SingleEnchantment, BlockHarvestModifierHook.MarkHarvesting {
    @Override
    default int updateEnchantmentLevel(IToolStackView tool, ModifierEntry modifier, Holder<Enchantment> enchantment, int level) {
      if (BlockHarvestModifierHook.MarkHarvesting.isHarvesting(tool)) {
        return SingleEnchantment.super.updateEnchantmentLevel(tool, modifier, enchantment, level);
      }
      return level;
    }

    @Override
    default void updateEnchantments(IToolStackView tool, ModifierEntry modifier, EnchantmentLevels enchantments) {
      if (BlockHarvestModifierHook.MarkHarvesting.isHarvesting(tool)) {
        SingleEnchantment.super.updateEnchantments(tool, modifier, enchantments);
      }
    }
  }
}
