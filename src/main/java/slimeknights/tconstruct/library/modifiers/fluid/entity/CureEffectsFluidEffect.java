package slimeknights.tconstruct.library.modifiers.fluid.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.mantle.data.loadable.common.ItemStackLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.fluid.EffectLevel;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext.Entity;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;

/**
 * Effect to clear all effects using the given stack
 * @param stack  Stack used for curing, standard is milk bucket
 */
public record CureEffectsFluidEffect(ItemStack stack) implements FluidEffect<FluidEffectContext.Entity> {
  public static final RecordLoadable<CureEffectsFluidEffect> LOADER = RecordLoadable.create(ItemStackLoadable.REQUIRED_ITEM.requiredField("item", e -> e.stack), CureEffectsFluidEffect::new);

  public CureEffectsFluidEffect(ItemLike item) {
    this(new ItemStack(item));
  }

  @Override
  public float apply(FluidStack fluid, EffectLevel level, Entity context, FluidAction action) {
    LivingEntity target = context.getLivingTarget();
    if (target != null && level.isFull()) {
      // 1.21 deleted curative items: an effect names the EffectCure tokens that clear it rather than the stacks, so
      // the configured item is translated to the cure it used to perform. The datapack field is unchanged.
      EffectCure cure = ModifierUtil.cureFromItem(stack.getItem());
      // when simulating, search the effects list directly for curable effects
      // may still be wrong if the event cancels things though, no way to safely simulate it
      if (action.simulate()) {
        return target.getActiveEffects().stream().anyMatch(effect -> effect.getCures().contains(cure)) ? 1 : 0;
      }
      return target.removeEffectsCuredBy(cure) ? 1 : 0;
    }
    return 0;
  }

  @Override
  public RecordLoadable<CureEffectsFluidEffect> getLoader() {
    return LOADER;
  }
}
