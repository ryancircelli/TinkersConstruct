package slimeknights.tconstruct.library.tools.nbt;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * A tool's {@code minecraft:custom_data}, offered to modifiers that have to write NBT other mods will read.
 * <p>
 * This replaces {@code RestrictedCompoundTag}, and the replacement is smaller than the thing it replaces because most
 * of what that class did has stopped being a problem. It existed to hand a modifier the tool's whole item tag with
 * nine keys blocked off, because in 1.20 Tinkers' materials, stats, modifiers and mod data shared one compound with
 * everything vanilla and every other mod stored on the stack. In 1.21 all of Tinkers' data is in
 * {@code tconstruct:tool} and {@code tconstruct:tool_stats}, everything vanilla is in its own typed component, and
 * {@code minecraft:custom_data} holds only what mods put there by hand. There is nothing left to restrict, so this
 * offers the compound whole and the allowlist is gone.
 * <p>
 * Reads are value semantic on the same terms as {@link ModDataNBT}, and for the same reason: a getter that hands back
 * a stored entry would make every read a write path. Keys are plain strings rather than
 * {@link net.minecraft.resources.ResourceLocation}s because the point of this hook is to write the exact key some
 * other mod reads, and those are not namespaced.
 * <p>
 * The edits land on the stack when {@link ToolStack#updateStack(net.minecraft.world.item.ItemStack)} runs, like every
 * other write a tool makes.
 */
@EqualsAndHashCode
@RequiredArgsConstructor
public class RawDataNBT {
  /** Backing compound, the tool's custom data */
  private final CompoundTag data;

  /** Creates raw data over an empty compound */
  public RawDataNBT() {
    this(new CompoundTag());
  }

  /** Reads the raw data off a stack's custom data component, copying so edits do not reach the stack early */
  public static RawDataNBT from(CustomData customData) {
    return new RawDataNBT(customData.copyTag());
  }

  /** Gets the backing compound, for {@link ToolStack} to store. Never hand this to anything that edits it. */
  CompoundTag getData() {
    return data;
  }

  /** Checks whether anything has been written */
  public boolean isEmpty() {
    return data.isEmpty();
  }


  /* Get functions */

  /**
   * Gets a key from the data.
   * The function runs against the backing compound; if it returns an entry of that compound rather than a new value,
   * a copy of the entry is returned instead, so the caller cannot edit the data through the result.
   * @param name      Key name
   * @param function  Function to get data using the key
   * @param <T>  Type of output
   * @return  Data based on the function
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String name, BiFunction<CompoundTag,String,T> function) {
    T value = function.apply(data, name);
    if (value instanceof Tag tag && tag == data.get(name)) {
      return (T)tag.copy();
    }
    return value;
  }

  /** Checks if the data contains the given key with any type */
  public boolean contains(String name) {
    return data.contains(name);
  }

  /**
   * Checks if the data contains the given key
   * @param name  Key name
   * @param type  Tag type, see {@link Tag} for values
   */
  public boolean contains(String name, int type) {
    return data.contains(name, type);
  }

  /** Reads a generic NBT value, or null if absent. The result is a copy. */
  @Nullable
  public Tag get(String name) {
    return get(name, CompoundTag::get);
  }

  /** Reads an integer, 0 if absent */
  public int getInt(String name) {
    return get(name, CompoundTag::getInt);
  }

  /** Reads a boolean, false if absent */
  public boolean getBoolean(String name) {
    return get(name, CompoundTag::getBoolean);
  }

  /** Reads a float, 0 if absent */
  public float getFloat(String name) {
    return get(name, CompoundTag::getFloat);
  }

  /** Reads a string, empty if absent */
  public String getString(String name) {
    return get(name, CompoundTag::getString);
  }

  /** Reads a copy of a compound, empty if absent */
  public CompoundTag getCompound(String name) {
    return get(name, CompoundTag::getCompound);
  }

  /** Reads a copy of a list, empty if absent */
  public ListTag getList(String name, int type) {
    return data.getList(name, type).copy();
  }


  /* Put functions */

  /** Stores an NBT value. This is how an edit to a tag from a getter reaches the data. */
  public void put(String name, Tag nbt) {
    data.put(name, nbt);
  }

  /** Stores an integer */
  public void putInt(String name, int value) {
    data.putInt(name, value);
  }

  /** Stores a boolean */
  public void putBoolean(String name, boolean value) {
    data.putBoolean(name, value);
  }

  /** Stores a float */
  public void putFloat(String name, float value) {
    data.putFloat(name, value);
  }

  /** Stores a string */
  public void putString(String name, String value) {
    data.putString(name, value);
  }

  /** Removes a key */
  public void remove(String name) {
    data.remove(name);
  }
}
