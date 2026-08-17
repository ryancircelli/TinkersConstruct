package slimeknights.tconstruct.library.tools.nbt;


import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.stat.INumericToolStat;

/**
 * Provides mostly read only access to {@link ToolStack}.
 * Used since modifiers should not be modifying the tool materials or modifiers in their behaviors.
 * If you receive an instance of this interface a parameter, do NOT use an instanceof check and cast it to a ToolStack. Don't make me use a private wrapper class.
 */
public interface IToolStackView extends IToolContext {
  /* Item stack */

  /**
   * Checks if this tool is the one {@link ToolStack#updateStack()} will write to.
   * In 1.20 this asked whether the tool shared its NBT with the stack, so that writes were mirrored. A tool holds its
   * own copy of the data now, so the equivalent question is which stack it is bound to.
   */
  default boolean isSameStack(ItemStack stack) {
    return false;
  }

  /* Stats */

  /** On built tools, contains the full tool stats. During tool rebuild, contains the base stats before considering modifiers. */
  StatsNBT getStats();

  /**
   * Gets the tool stats if parsed, or parses from NBT if not yet parsed
   * @return stats
   */
  MultiplierNBT getMultipliers();

  /** Commonly used operation, getting a stat multiplier */
  default float getMultiplier(INumericToolStat<?> stat) {
    return getMultipliers().get(stat);
  }


  /* Durability */

  /** Gets the current lost durability of the tool */
  int getDamage();

  /** Gets the current durability remaining for this tool */
  int getCurrentDurability();

  /** Checks whether the tool is broken */
  boolean isBroken();

  /** If true, tool is marked unbreakable by vanilla NBT. This is distinct from {@link slimeknights.tconstruct.tools.data.ModifierIds#unbreakable} */
  boolean isUnbreakable();

  /**
   * Sets the tools current damage. Durability is {@code minecraft:damage} in 1.21, but this is still the way to write
   * it on a tool, because the value is clamped against the durability stat and decides the broken flag.
   * <p>
   * The write is local, like every other write a tool makes: it reaches the item stack when
   * {@link ToolStack#updateStack(ItemStack)} runs, and a view taken with {@link ToolStack#from(ItemStack)} has nowhere
   * to write at all. A development run reports either mistake.
   * <p>
   * Note in general you should use {@link ToolDamageUtil#damage(IToolStackView, int, LivingEntity, ItemStack)} or {@link ToolDamageUtil#repair(IToolStackView, int)} as they handle modifiers.
   * @param damage  New damage
   */
  void setDamage(int damage);

  /**
   * Gets persistent modifier data from the tool.
   * This data may be edited by modifiers and will persist when stats rebuild. Edits are local to this tool until
   * {@link ToolStack#updateStack(ItemStack)} commits them, so a hook that writes here has to have been handed a tool
   * somebody is going to commit.
   */
  @Override
  ModDataNBT getPersistentData();

  /**
   * Gets volatile modifier data from the tool.
   * This data will be reset whenever modifiers reload and should not be edited.
   */
  IModDataView getVolatileData();


  /* Helpers */

  /**
   * Gets the free upgrade slots remaining on the tool
   * @return  Free upgrade slots
   */
  default int getFreeSlots(SlotType type) {
    return getPersistentData().getSlots(type) + getVolatileData().getSlots(type);
  }
}
