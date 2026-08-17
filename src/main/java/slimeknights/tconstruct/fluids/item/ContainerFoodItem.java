package slimeknights.tconstruct.fluids.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodProperties.PossibleEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.fluids.util.ConstantFluidContainerWrapper;

import java.util.List;
import java.util.function.Supplier;

public class ContainerFoodItem extends Item {
  /** Tick rate assumed when no tooltip context is available, matching {@link net.minecraft.world.item.Item.TooltipContext#EMPTY} */
  private static final float DEFAULT_TICK_RATE = 20.0f;

  public ContainerFoodItem(Properties props) {
    super(props);
  }

  @Override
  public int getUseDuration(ItemStack stack, LivingEntity entity) {
    return 32;
  }

  @Override
  public UseAnim getUseAnimation(ItemStack pStack) {
    return UseAnim.DRINK;
  }

  /**
   * Adds effects to the tooltip
   * @param food     Food to display
   * @param tooltip  Tooltip list
   * @param tickRate Ticks per second of the level being displayed, used to convert effect durations to seconds
   */
  public static void addEffectTooltip(FoodProperties food, List<Component> tooltip, float tickRate) {
    // add effects to the tooltip, code based on potion items
    // 1.21 replaced the Pair<MobEffectInstance,Float> list with a PossibleEffect record, and MobEffect is a Holder
    for (PossibleEffect possible : food.effects()) {
      MobEffectInstance effect = possible.effect();
      MutableComponent mutable = Component.translatable(effect.getDescriptionId());
      if (effect.getAmplifier() > 0) {
        mutable = Component.translatable("potion.withAmplifier", mutable, Component.translatable("potion.potency." + effect.getAmplifier()));
      }
      if (effect.getDuration() > 20) {
        mutable = Component.translatable("potion.withDuration", mutable, MobEffectUtil.formatDuration(effect, 1.0f, tickRate));
      }
      tooltip.add(mutable.withStyle(effect.getEffect().value().getCategory().getTooltipFormatting()));
    }
  }

  /** Adds effects to the tooltip, assuming the standard tick rate */
  public static void addEffectTooltip(FoodProperties food, List<Component> tooltip) {
    addEffectTooltip(food, tooltip, DEFAULT_TICK_RATE);
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    FoodProperties food = stack.getFoodProperties(null);
    if (food != null) {
      addEffectTooltip(food, tooltip, context.tickRate());
    }
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
    ItemStack container = stack.getCraftingRemainingItem();
    ItemStack result = super.finishUsingItem(stack, level, living);
    Player player = living instanceof Player p ? p : null;
    if (player == null || !player.getAbilities().instabuild) {
      container = container.copy();
      if (result.isEmpty()) {
        return container;
      }
      if (player != null) {
        if (!player.getInventory().add(container)) {
          player.drop(container, false);
        }
      }
    }
    return result;
  }

  /**
   * Food item containing a constant fluid.
   * <p>
   * 1.20 attached the handler from {@code Item#initCapabilities}. That hook is gone; the fluid is exposed here so
   * {@code TinkerFluids#registerCapabilities} can build a {@link ConstantFluidContainerWrapper} for every instance
   * from one registration.
   */
  public static class FluidContainerFoodItem extends ContainerFoodItem {
    private final Supplier<FluidStack> fluid;
    public FluidContainerFoodItem(Properties props, Supplier<FluidStack> fluid) {
      super(props);
      this.fluid = fluid;
    }

    /** Gets the fluid contained in this item */
    public FluidStack getFluid() {
      return fluid.get();
    }
  }
}
