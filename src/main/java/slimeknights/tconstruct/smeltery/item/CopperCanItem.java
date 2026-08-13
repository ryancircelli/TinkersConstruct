package slimeknights.tconstruct.smeltery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluid container holding 1 ingot of fluid
 */
public class CopperCanItem extends Item {
  public CopperCanItem(Properties properties) {
    super(properties);
  }

  @Override
  public boolean hasCraftingRemainingItem(ItemStack stack) {
    return getFluid(stack) != Fluids.EMPTY;
  }

  @Override
  public ItemStack getCraftingRemainingItem(ItemStack stack) {
    if (hasCraftingRemainingItem(stack)) {
      return new ItemStack(this);
    }
    return ItemStack.EMPTY;
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    FluidStack fluid = getFluidStack(stack);
    if (!fluid.isEmpty()) {
      // 1.20 fell back to the fluid type's own name when the stack had no fluid_tag, since it could not build a
      // FluidStack without one. A FluidStack with an empty component patch is a perfectly ordinary stack now, so the
      // hover name is always the right answer and the branch is gone.
      tooltip.add(Component.translatable(this.getDescriptionId() + ".contents", fluid.getHoverName().plainCopy()).withStyle(ChatFormatting.GRAY));
      if (flag.isAdvanced()) {
        tooltip.add(Component.translatable(TankItem.FLUID_ID, Loadables.FLUID.getKey(fluid.getFluid())).withStyle(ChatFormatting.DARK_GRAY));
      }
    } else {
      tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
  }

  /**
   * Gets the fluid stored on the given stack, normalized to one ingot.
   * The handler scales that by the stack size; storing the normalized amount is what keeps two cans of the same fluid
   * stacking with each other.
   */
  public static FluidStack getFluidStack(ItemStack stack) {
    return stack.getOrDefault(TinkerSmeltery.canFluid.get(), SimpleFluidContent.EMPTY).copy();
  }

  /** Sets the fluid on the given stack, normalizing the amount to one ingot */
  public static ItemStack setFluid(ItemStack stack, FluidStack fluid) {
    // removing the component rather than storing an empty one keeps an emptied can stacking with a fresh one
    if (fluid.isEmpty()) {
      stack.remove(TinkerSmeltery.canFluid.get());
    } else {
      stack.set(TinkerSmeltery.canFluid.get(), SimpleFluidContent.copyOf(fluid.copyWithAmount(FluidValues.INGOT)));
    }
    return stack;
  }

  /** Sets the fluid on the given stack, with no extra fluid data */
  public static ItemStack setFluid(ItemStack stack, Fluid fluid) {
    return setFluid(stack, fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, FluidValues.INGOT));
  }

  /** Gets the fluid from the given stack */
  public static Fluid getFluid(ItemStack stack) {
    return getFluidStack(stack).getFluid();
  }

  /** Adds filled variants of the copper can to the given consumer */
  @SuppressWarnings("deprecation")
  public static void addFilledVariants(Consumer<ItemStack> output) {
    BuiltInRegistries.FLUID.holders().filter(holder -> {
      Fluid fluid = holder.get();
      return fluid.isSource(fluid.defaultFluidState()) && !holder.is(TinkerTags.Fluids.HIDE_IN_CREATIVE_TANKS);
    }).forEachOrdered(holder -> {
      output.accept(CopperCanItem.setFluid(new ItemStack(TinkerSmeltery.copperCan), holder.value()));
    });
  }

  /**
   * Gets a string variant name for the given stack
   * @param stack  Stack instance to check
   * @return  String variant name
   */
  public static String getSubtype(ItemStack stack) {
    FluidStack fluid = getFluidStack(stack);
    if (fluid.isEmpty()) {
      return "";
    }
    return Loadables.FLUID.getKey(fluid.getFluid()).toString();
  }
}
