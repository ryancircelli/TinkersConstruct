package slimeknights.tconstruct.library.modifiers.data;

import lombok.Getter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;

/** Helper class to keep track the max vanilla level in a modifier, ints and only on four armor slots */
public class VanillaMaxLevel {
  /** Level for each slot */
  private final int[] levels = new int[4];
  /** Max level across all slots */
  @Getter
  private int max = 0;

  /**
   * Sets the given vanilla level in the structure. A slot that is not humanoid armor is ignored.
   * @apiNote  The four entries are indexed by {@link EquipmentSlot#getIndex()}, which is only unique within a slot
   *           type. 1.21's {@link EquipmentSlot#BODY} is index 0 of {@link Type#ANIMAL_ARMOR} and would silently
   *           overwrite {@link EquipmentSlot#FEET}, so it is dropped here rather than counted as a boot.
   */
  public void set(EquipmentSlot slot, int level) {
    if (slot.getType() != Type.HUMANOID_ARMOR) {
      return;
    }
    int oldLevel = levels[slot.getIndex()];
    if (level != oldLevel) {
      levels[slot.getIndex()] = level;
      // if new max, update max
      if (level > max) {
        max = level;
      } else if (max == oldLevel) {
        // if was max before, search for replacement max
        max = 0;
        for (int value : levels) {
          if (value > max) {
            max = value;
          }
        }
      }
    }
  }
}
