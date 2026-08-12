package slimeknights.tconstruct.library.modifiers.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

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
 * Iteration order is insertion order, matching {@link EnchantmentHelper#deserializeEnchantments(net.minecraft.nbt.ListTag)}, as the order is visible in
 * serialized NBT via {@link EnchantmentHelper#setEnchantments(Map, ItemStack)}.
 */
public final class EnchantmentLevels implements Iterable<Entry<Holder<Enchantment>,Integer>> {
  /** Backing map, insertion ordered. May contain values of 0 or less until {@link #removeNonPositive()} runs. */
  private final Map<Holder<Enchantment>,Integer> levels;

  private EnchantmentLevels(Map<Holder<Enchantment>,Integer> levels) {
    this.levels = levels;
  }

  /** Creates a new empty accumulator */
  public static EnchantmentLevels create() {
    return new EnchantmentLevels(new LinkedHashMap<>());
  }

  /**
   * Creates an accumulator containing every entry from the given map, preserving its iteration order.
   * Used at the Forge boundary where enchantments arrive as raw objects.
   */
  public static EnchantmentLevels copyOf(Map<Enchantment,Integer> map) {
    EnchantmentLevels levels = create();
    for (Entry<Enchantment,Integer> entry : map.entrySet()) {
      levels.levels.put(holder(entry.getKey()), entry.getValue());
    }
    return levels;
  }

  /** Creates an accumulator containing the enchantments stored in the stack's NBT, ignoring any projected by modifiers */
  public static EnchantmentLevels fromStack(ItemStack stack) {
    return copyOf(EnchantmentHelper.getEnchantments(stack));
  }

  /** Creates an accumulator containing the enchantments stored in the given serialized enchantment list */
  public static EnchantmentLevels fromTag(ListTag tag) {
    return copyOf(EnchantmentHelper.deserializeEnchantments(tag));
  }

  /**
   * Wraps the given enchantment as a registry holder.
   * Registered enchantments always produce the same {@link Holder.Reference} instance, so holders may be compared by identity;
   * unregistered enchantments fall back to {@link Holder#direct(Object)} which compares by value.
   */
  public static Holder<Enchantment> holder(Enchantment enchantment) {
    return BuiltInRegistries.ENCHANTMENT.wrapAsHolder(enchantment);
  }


  /* Query */

  /** Gets the level of the given enchantment, 0 if absent. May be negative while the hook chain runs. */
  public int getLevel(Holder<Enchantment> enchantment) {
    return levels.getOrDefault(enchantment, 0);
  }

  /** Overload of {@link #getLevel(Holder)} taking a raw enchantment */
  public int getLevel(Enchantment enchantment) {
    return getLevel(holder(enchantment));
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

  /** Overload of {@link #addLevel(Holder, int)} taking a raw enchantment */
  public void addLevel(Enchantment enchantment, int amount) {
    addLevel(holder(enchantment), amount);
  }

  /** Sets the level of the enchantment, discarding any level already present */
  public void setLevel(Holder<Enchantment> enchantment, int level) {
    levels.put(enchantment, level);
  }

  /** Overload of {@link #setLevel(Holder, int)} taking a raw enchantment */
  public void setLevel(Enchantment enchantment, int level) {
    setLevel(holder(enchantment), level);
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
   * Converts to a mutable map of raw enchantments for the Forge and vanilla boundary, preserving iteration order.
   * The result is independent of this accumulator as vanilla mutates the maps it receives, notably in the anvil.
   */
  public Map<Enchantment,Integer> toMap() {
    Map<Enchantment,Integer> map = new LinkedHashMap<>(levels.size());
    for (Entry<Holder<Enchantment>,Integer> entry : levels.entrySet()) {
      map.put(entry.getKey().value(), entry.getValue());
    }
    return map;
  }

  @Override
  public String toString() {
    return "EnchantmentLevels" + levels;
  }
}
