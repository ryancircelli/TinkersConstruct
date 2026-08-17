package slimeknights.tconstruct.library.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeInput;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Container that contains a tinkerable stack and a number of inputs after
 * @apiNote  1.21 replaced the {@link net.minecraft.world.Container} a recipe took with {@link RecipeInput}, a far
 *           smaller interface of exactly the three methods a recipe ever used. Mantle's {@code IRecipeContainer},
 *           which existed only to stub out the container methods this interface had no answer for, is gone with it.
 */
public interface ITinkerableContainer extends RecipeInput {
  /**
   * Gets the stack in the tinkerable slot.
   *
   * @return the itemstack in the tinkerable slot (slot 5/center slot)
   */
  ItemStack getTinkerableStack();

  /** Gets the tinkerable item as a tool stack instance, or returns null if not valid */
  default ToolStack getTinkerable() {
    return ToolStack.mutable(getTinkerableStack());
  }

  /** Gets the size of the tinkerable stack, common operation */
  default int getTinkerableSize() {
    return getTinkerableStack().getCount();
  }

  /**
   * Gets the stack in the given input slot
   * @param index  Slot index
   * @return  Stack
   */
  ItemStack getInput(int index);

  /**
   * Gets the number of input slots
   * @return  Input slot count
   */
  int getInputCount();


  /* Base methods */

  /** @deprecated use {@link #getInput(int)} */
  @Deprecated
  @Override
  default ItemStack getItem(int index) {
    if (index == 0) {
      return getTinkerableStack();
    }
    return getInput(index - 1);
  }

  @Override
  default boolean isEmpty() {
    if (!getTinkerableStack().isEmpty()) {
      return false;
    }
    int max = getInputCount();
    for (int i = 0; i < max; i++) {
      if (!getInput(i).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /** @deprecated use {@link #getInputCount()} */
  @Deprecated
  @Override
  default int size() {
    return getInputCount() + 1;
  }

  interface Mutable extends ITinkerableContainer {
    /**
     * Sets the input in the given slot
     * @param index  Slot to set
     * @param stack  New stack
     */
    void setInput(int index, ItemStack stack);

    /**
     * Gives an extra recipe result to the user of the inventory
     * @param stack  Extra stack
     */
    void giveItem(ItemStack stack);


    /**
     * Shrinks a slot by the given count, returning the properly sized container
     * @param slot    Slot to shrink
     * @param amount  Amount to shrink by
     */
    default void shrinkInput(int slot, int amount, ItemStack container) {
      ItemStack stack = getInput(slot);
      if (!stack.isEmpty()) {
        // determine how large to make the container
        int count = stack.getCount();
        if (!container.isEmpty()) {
          container.setCount(Math.min(count, amount));
        }
        // if removing all items, set the slot to the container. If container is empty that is fine
        if (amount >= count) {
          setInput(slot, container);
        } else {
          // otherwise, shrink the stack and add the container
          stack = stack.copy();
          stack.shrink(amount);
          setInput(slot, stack);
          if (!container.isEmpty()) {
            giveItem(container);
          }
        }
      }
    }

    /**
     * Shrinks a slot by the given count, returning the properly sized container
     * @param slot    Slot to shrink
     * @param amount  Amount to shrink by
     */
    default void shrinkInput(int slot, int amount) {
      ItemStack stack = getInput(slot);
      if (!stack.isEmpty()) {
        ItemStack container = stack.getCraftingRemainingItem();
        if (container.isEmpty() && stack.getItem() == Items.POTION) {
          container = new ItemStack(Items.GLASS_BOTTLE);
        }
        shrinkInput(slot, amount, container);
      }
    }
  }
}
