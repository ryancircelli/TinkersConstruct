package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.modifiers.IncrementalModifierEntry;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.tools.helper.ModifierBuilder;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * NBT object containing all current modifiers
 */
@EqualsAndHashCode
@RequiredArgsConstructor
public class ModifierNBT implements Iterable<ModifierEntry> {

  /** Instance containing no modifiers */
  public static final ModifierNBT EMPTY = new ModifierNBT(Collections.emptyList());

  /**
   * Loadable for a single entry of a stored modifier list.
   * <p>
   * This is deliberately not {@link ModifierEntry#LOADABLE}: that one is the datapack spelling, it rejects a level
   * below 1 and it has no place for the incremental amount, which a stored modifier list has to keep. Every field here
   * is permissive and an entry that does not make sense reads as {@link ModifierEntry#EMPTY} for {@link #LOADABLE} to
   * drop, which is what the tag reader did. See {@link MaterialNBT#LOADABLE} for why a stored list may not throw.
   */
  private static final RecordLoadable<ModifierEntry> ENTRY = RecordLoadable.create(
    StringLoadable.DEFAULT.defaultField(ModifierEntry.TAG_MODIFIER, "", true, entry -> entry.getId().toString()),
    IntLoadable.ANY_FULL.defaultField(ModifierEntry.TAG_LEVEL, 0, true, ModifierEntry::getLevel),
    IntLoadable.ANY_FULL.defaultField(ModifierEntry.TAG_AMOUNT, 0, entry -> entry.getAmount(0)),
    IntLoadable.ANY_FULL.defaultField(ModifierEntry.TAG_NEEDED, 0, ModifierEntry::getNeeded),
    (name, level, amount, needed) -> {
      ModifierId id = ModifierId.tryParse(name);
      if (id == null || level <= 0) {
        return ModifierEntry.EMPTY;
      }
      // incremental just has more fields; with both at 0 this hands back the plain entry
      return IncrementalModifierEntry.of(id, level, amount, needed);
    });

  /**
   * Loadable for a modifier list. This is the {@code upgrades} field of the {@code tconstruct:tool} component and the
   * {@code modifiers} field of {@code tconstruct:tool_stats}, and it is the single definition of how a modifier list is
   * written in every format: {@link #readFromNBT(Tag)} and {@link #serializeToNBT()} run it through {@link NbtOps}.
   */
  public static final Loadable<ModifierNBT> LOADABLE = ENTRY.list(0).flatXmap(
    list -> {
      ImmutableList.Builder<ModifierEntry> builder = ImmutableList.builder();
      for (ModifierEntry entry : list) {
        if (entry != ModifierEntry.EMPTY) {
          builder.add(entry);
        }
      }
      List<ModifierEntry> entries = builder.build();
      return entries.isEmpty() ? EMPTY : new ModifierNBT(entries);
    },
    nbt -> nbt.modifiers);

  /** Sorted list of modifiers */
  @Getter
  private final List<ModifierEntry> modifiers;

  /**
   * Checks if the NBT has no modifiers
   * @return  True if there are no modifiers
   */
  public boolean isEmpty() {
    return modifiers.isEmpty();
  }

  /**
   * Gets the modifier entry for a modifier
   * @param modifier  Modifier to check
   * @return  Modifier entry, or {@link ModifierEntry#EMPTY} if absent
   */
  public ModifierEntry getEntry(ModifierId modifier) {
    for (ModifierEntry entry : modifiers) {
      if (entry.matches(modifier)) {
        return entry;
      }
    }
    return ModifierEntry.EMPTY;
  }

  /**
   * Gets the level of a modifier
   * @param modifier  Modifier to check
   * @return  Modifier level, or 0 if modifier is missing
   */
  public int getLevel(ModifierId modifier) {
    return getEntry(modifier).getLevel();
  }

  /**
   * Checks if the listing has the given modifier tag.
   * To check if it has a specific modifier, use {@link #getLevel(ModifierId)}.
   * @param tag  Modifier tag
   * @return  True if any modifier in the tag is present
   */
  public boolean has(TagKey<Modifier> tag) {
    for (ModifierEntry entry : modifiers) {
      if (ModifierManager.isInTag(entry.getId(), tag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the listing has a modifier matching the given predicate.
   * To check if it has a specific modifier, use {@link #getLevel(ModifierId)}.
   * @param predicate  Predicate to test
   * @return  True if any modifier in the tag is present
   */
  public boolean has(IJsonPredicate<ModifierId> predicate) {
    for (ModifierEntry entry : modifiers) {
      if (predicate.matches(entry.getId())) {
        return true;
      }
    }
    return false;
  }


  /* Iterator */

  @Override
  public Iterator<ModifierEntry> iterator() {
    return modifiers.iterator();
  }

  @Override
  public void forEach(Consumer<? super ModifierEntry> action) {
    modifiers.forEach(action);
  }

  @Override
  public Spliterator<ModifierEntry> spliterator() {
    return modifiers.spliterator();
  }


  /* Withers */

  /**
   * Creates a copy of this NBT with the given modifier added. Result will be unsorted
   * Do not use if you need to make multiple additions, use {@link ModifierNBT.Builder}
   * @param modifier  Modifier
   * @param level     Levels of the modifier to add
   * @return  Instance with the given modifier
   */
  public ModifierNBT withModifier(ModifierId modifier, int level) {
    if (level <= 0) {
      throw new IllegalArgumentException("Invalid level, must be above zero");
    }

    // rather than using the builder, just use a raw list builder
    // easier for adding a single entry, and the cases that call this method don't care about sorting
    ImmutableList.Builder<ModifierEntry> builder = ImmutableList.builder();
    boolean found = false;
    for (ModifierEntry entry : this.modifiers) {
      // first match increases the level
      // shouldn't be a second match (all the methods are protected), but just in case we prevent modifier duplication
      if (!found && entry.matches(modifier)) {
        builder.add(entry.withLevel(entry.getLevel() + level));
        found = true;
      } else {
        builder.add(entry);
      }
    }
    // if no matching modifier, create a new entry
    if (!found) {
      builder.add(new ModifierEntry(modifier, level));
    }
    return new ModifierNBT(builder.build());
  }

  /**
   * Creates a copy of this NBT with the given incremental modifier amount added.
   * Do not use if you need to make multiple additions, use {@link ModifierNBT.Builder}
   * @param modifier  Modifier
   * @param amount    Increments to add
   * @param needed    Increment scale to reach a full level
   * @return  Instance with the given modifier
   */
  public ModifierNBT addAmount(ModifierId modifier, int amount, int needed) {
    // no need to do anything if amount or need is not at least 1
    if (amount <= 0 || needed <= 0) {
      return this;
    }
    // no shortcut here as amount being greater than need should lead to a full level
    // rather than using the builder, just use a raw list builder
    // easier for adding a single entry, and the cases that call this method don't care about sorting
    ImmutableList.Builder<ModifierEntry> builder = ImmutableList.builder();
    boolean found = false;
    for (ModifierEntry entry : this.modifiers) {
      // first match increases the level
      // shouldn't be a second match (all the methods are protected), but just in case we prevent modifier duplication
      if (!found && entry.matches(modifier)) {
        builder.add(entry.addAmount(amount, needed));
        found = true;
      } else {
        builder.add(entry);
      }
    }
    // if no matching modifier, create a new entry
    if (!found) {
      builder.add(IncrementalModifierEntry.of(modifier, 1, amount, needed));
    }
    return new ModifierNBT(builder.build());
  }

  /**
   * Creates a copy of this NBT without the given modifier
   * @param modifier  Modifier to remove
   * @param level     Level to remove
   * @return  ModifierNBT without the given modifier
   */
  public ModifierNBT withoutModifier(ModifierId modifier, int level) {
    if (level <= 0) {
      throw new IllegalArgumentException("Invalid level, must be above zero");
    }

    // rather than using the builder, just use a raw list builder
    // easier for adding a single entry, and the cases that call this method don't care about sorting
    ImmutableList.Builder<ModifierEntry> builder = ImmutableList.builder();
    for (ModifierEntry entry : this.modifiers) {
      if (entry.matches(modifier) && level > 0) {
        if (entry.getLevel() > level) {
          builder.add(entry.withLevel(entry.getLevel() - level));
          level = 0;
        } else {
          level -= entry.getLevel();
        }
      } else {
        builder.add(entry);
      }
    }
    return new ModifierNBT(builder.build());
  }


  /* NBT */

  /** Re-adds the modifier list from NBT */
  public static ModifierNBT readFromNBT(@Nullable Tag inbt) {
    if (inbt == null || inbt.getId() != Tag.TAG_LIST) {
      return EMPTY;
    }

    ListTag listNBT = (ListTag)inbt;
    if (listNBT.getElementType() != Tag.TAG_COMPOUND) {
      return EMPTY;
    }
    return LOADABLE.convert(NbtOps.INSTANCE, inbt, "modifiers");
  }

  /**
   * Writes these modifiers to NBT.
   * <p>
   * Note this no longer writes {@link ModifierEntry#TAG_EFFECTIVE}. That key was a write only cache of the effective
   * level of an incremental entry: nothing reads it, on either side of the port, and it is derivable from the three
   * keys next to it. Keeping it would mean the loadable had to write a field it could not read back.
   */
  public ListTag serializeToNBT() {
    return (ListTag)LOADABLE.serialize(NbtOps.INSTANCE, this);
  }


  /* Builder */

  /**
   * Creates a new builder for modifier NBT
   * @return  Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for creating a modifier list with multiple additions. Builder results will be sorted
   */
  public static class Builder implements ModifierBuilder {
    /** Intentionally using modifiers to ensure they are resolved */
    private final Map<ModifierId, ModifierEntry> modifiers = new LinkedHashMap<>();

    private Builder() {}

    @Override
    public Builder add(ModifierEntry entry) {
      if (entry != ModifierEntry.EMPTY && entry.isBound()) {
        ModifierId id = entry.getId();
        ModifierEntry current = modifiers.get(id);
        if (current != null) {
          entry = current.merge(entry);
        }
        modifiers.put(id, entry);
      }
      return this;
    }

    @Override
    public ModifierNBT build() {
      // converts the map into a list of entries, priority sorted
      // note priority is negated so higher numbers go first
      List<ModifierEntry> list = modifiers.values().stream()
                                          // sort on priority, falls back to the order they were added
                                          .sorted(Comparator.comparingInt(entry -> -entry.getModifier().getPriority()))
                                          .collect(Collectors.toList());
      // it's rare to see no modifiers, but no sense creating a new instance for that
      if (list.isEmpty()) {
        return EMPTY;
      }
      return new ModifierNBT(ImmutableList.copyOf(list));
    }
  }
}
