package slimeknights.tconstruct.library.modifiers.util;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Mutable accumulator of enchantment levels keyed by {@link Holder}, used to project modifier granted enchantments onto a tool.
 * <p>
 * Levels are <b>additive</b>: {@link #addLevel(Holder, int)} sums into any level already present, which lets several modifiers stack the same enchantment
 * and lets a modifier cancel another out by adding a negative. As a consequence levels are allowed to go negative while the hook chain runs;
 * call {@link #removeNonPositive()} before handing the result to anything which expects real enchantment levels.
 * <p>
 * This exists rather than {@link ItemEnchantments.Mutable} for that reason alone: vanilla's accumulator drops any level of 0 or less on the way in
 * ({@link ItemEnchantments.Mutable#set(Holder, int)}) and its enchantment holder is validated to 0-255 on the way out, so a chain which cancels an
 * enchantment out and then adds it back would come out one level richer than it went in. {@link #toComponent()} is where the two meet.
 */
public final class EnchantmentLevels implements Iterable<Entry<Holder<Enchantment>,Integer>> {
  /** Backing map, insertion ordered. May contain values of 0 or less until {@link #removeNonPositive()} runs. */
  private final Map<Holder<Enchantment>,Integer> levels;
  /** Component this accumulator was read from, used to carry over anything on it we do not model */
  private final ItemEnchantments source;

  private EnchantmentLevels(Map<Holder<Enchantment>,Integer> levels, ItemEnchantments source) {
    this.levels = levels;
    this.source = source;
  }

  /** Creates a new empty accumulator */
  public static EnchantmentLevels create() {
    return new EnchantmentLevels(new LinkedHashMap<>(), ItemEnchantments.EMPTY);
  }

  /** Creates an accumulator containing every entry from the given component */
  public static EnchantmentLevels copyOf(ItemEnchantments enchantments) {
    Map<Holder<Enchantment>,Integer> levels = new LinkedHashMap<>(enchantments.size());
    for (Holder<Enchantment> enchantment : enchantments.keySet()) {
      levels.put(enchantment, enchantments.getLevel(enchantment));
    }
    return new EnchantmentLevels(levels, enchantments);
  }

  /** Creates an accumulator containing the enchantments stored on the stack, ignoring any projected by modifiers */
  public static EnchantmentLevels fromStack(ItemStack stack) {
    return copyOf(stack.getTagEnchantments());
  }


  /* Query */

  /** Gets the level of the given enchantment, 0 if absent. May be negative while the hook chain runs. */
  public int getLevel(Holder<Enchantment> enchantment) {
    return levels.getOrDefault(enchantment, 0);
  }

  /** Checks if no enchantments are present */
  public boolean isEmpty() {
    return levels.isEmpty();
  }

  /** Gets the number of enchantments present */
  public int size() {
    return levels.size();
  }

  /** Gets an unmodifiable view of the contained enchantments in insertion order */
  public Set<Holder<Enchantment>> keys() {
    return Collections.unmodifiableSet(levels.keySet());
  }

  @Override
  public Iterator<Entry<Holder<Enchantment>,Integer>> iterator() {
    return Collections.unmodifiableMap(levels).entrySet().iterator();
  }


  /* Mutation */

  /**
   * Adds the given amount to the level of the enchantment, summing with any level already present.
   * An amount of 0 is a no-op; notably it will not create an entry for an enchantment which is absent.
   * @param enchantment  Enchantment to change
   * @param amount       Amount to add. May be negative to cancel out levels from another source.
   */
  public void addLevel(Holder<Enchantment> enchantment, int amount) {
    if (amount != 0) {
      levels.put(enchantment, getLevel(enchantment) + amount);
    }
  }

  /** Sets the level of the enchantment, discarding any level already present */
  public void setLevel(Holder<Enchantment> enchantment, int level) {
    levels.put(enchantment, level);
  }

  /** Removes the enchantment entirely, notably differing from setting it to 0 in that later {@link #addLevel(Holder, int)} calls start from 0 either way */
  public void remove(Holder<Enchantment> enchantment) {
    levels.remove(enchantment);
  }

  /** Removes every enchantment whose level is 0 or less, which the hook chain allows as a way to cancel out enchantments */
  public void removeNonPositive() {
    levels.values().removeIf(value -> value == null || value <= 0);
  }


  /* Output */

  /**
   * Converts to the enchantment component for the vanilla boundary, preserving the tooltip flag of whatever this was read from.
   * Levels are clamped to the 0-255 vanilla allows for a stored enchantment; a modifier producing a larger level had no way to say so before either,
   * as the component constructor rejects it outright.
   */
  public ItemEnchantments toComponent() {
    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(source);
    mutable.removeIf(enchantment -> !levels.containsKey(enchantment));
    for (Entry<Holder<Enchantment>,Integer> entry : levels.entrySet()) {
      mutable.set(entry.getKey(), entry.getValue());
    }
    return mutable.toImmutable();
  }

  @Override
  public String toString() {
    return "EnchantmentLevels" + levels;
  }
}
