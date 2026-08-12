package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.tools.SlotType;

import java.util.function.BiFunction;

/**
 * Read only view of {@link ModDataNBT}.
 * <p>
 * Reads are value semantic: a {@link CompoundTag} or {@link ListTag} handed back by any method here is a copy, so
 * editing it does nothing to the data it came from. Editing it and then storing it through {@link ModDataNBT#put(ResourceLocation, Tag)}
 * is how a change reaches the data, which keeps every write to a tool visible at the site making it.
 */
public interface IModDataView {
  /** Empty variant of tool data */
  IModDataView EMPTY = new IModDataView() {
    @Override
    public <T> T get(ResourceLocation name, BiFunction<CompoundTag,String,T> function) {
      return function.apply(new CompoundTag(), name.toString());
    }

    @Override
    public boolean contains(ResourceLocation name) {
      return false;
    }

    @Override
    public boolean contains(ResourceLocation name, int type) {
      return false;
    }
  };

  /**
   * Gets a namespaced key from NBT.
   * The function runs against the backing compound; if it returns an entry of that compound rather than a new value,
   * a copy of the entry is returned instead, so the caller cannot edit the data through the result.
   * @param name      Namedspaced key
   * @param function  Function to get data using the key
   * @param <T>  NBT type of output
   * @return  Data based on the function
   */
  <T> T get(ResourceLocation name, BiFunction<CompoundTag,String,T> function);

  /**
   * Checks if the data contains the given tag with any type.
   * Generally, its better to use {@link #contains(ResourceLocation, int)}, but there are rare benefits to this method.
   * @param name  Namespaced key
   * @return  True if the tag is contained
   */
  boolean contains(ResourceLocation name);

  /**
   * Checks if the data contains the given tag
   * @param name  Namespaced key
   * @param type  Tag type, see {@link Tag} for values
   * @return  True if the tag is contained
   */
  boolean contains(ResourceLocation name, int type);

  /**
   * Gets the number of slots provided by this data. Will be 0 if this data does not support slots.
   * @param type  Type of slot to get
   * @return  Number of slots
   */
  default int getSlots(SlotType type) {
    return 0;
  }


  /* Helpers */

  /**
   * Reads an generic NBT value from the mod data
   * @param name  Name
   * @return  Copy of the value, or null if absent
   */
  default Tag get(ResourceLocation name) {
    return get(name, CompoundTag::get);
  }

  /**
   * Reads an integer from the mod data
   * @param name  Name
   * @return  Integer value
   */
  default int getInt(ResourceLocation name) {
    return get(name, CompoundTag::getInt);
  }

  /**
   * Reads an boolean from the mod data
   * @param name  Name
   * @return  Boolean value
   */
  default boolean getBoolean(ResourceLocation name) {
    return get(name, CompoundTag::getBoolean);
  }

  /**
   * Reads an float from the mod data
   * @param name  Name
   * @return  Float value
   */
  default float getFloat(ResourceLocation name) {
    return get(name, CompoundTag::getFloat);
  }

  /**
   * Reads a string from the mod data
   * @param name  Name
   * @return  String value
   */
  default String getString(ResourceLocation name) {
    return get(name, CompoundTag::getString);
  }

  /**
   * Reads a compound from the mod data
   * @param name  Name
   * @return  Copy of the compound value, empty if absent
   */
  default CompoundTag getCompound(ResourceLocation name) {
    return get(name, CompoundTag::getCompound);
  }

  /**
   * Reads a list from the mod data
   * @param name  Name
   * @param type  List type
   * @return  Copy of the list value, empty if absent
   */
  default ListTag getList(ResourceLocation name, int type) {
    return get(name, (tag, key) -> tag.getList(key, type));
  }
}
