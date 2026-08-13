package slimeknights.tconstruct.tools.modifiers.traits.skull;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorLevelModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.CureOnRemovalModule;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.tools.TinkerModifiers;

public class StrongBonesModifier extends NoLevelsModifier {
  /** Key for modifiers that are boosted by drinking milk */
  public static final TinkerDataKey<Integer> CALCIFIABLE = TConstruct.createKey("calcifable");
  /** Module to add to any calcifiable modifiers */
  public static final ArmorLevelModule CALCIFIABLE_MODULE = new ArmorLevelModule(CALCIFIABLE, false, TinkerTags.Items.HELD_ARMOR);

  public StrongBonesModifier() {
    // TODO: move this out of constructor to generalized logic
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEntityUseItemEvent.Finish.class, StrongBonesModifier::onItemFinishUse);
  }

  @Override
  protected void registerHooks(Builder hookBuilder) {
    super.registerHooks(hookBuilder);
    hookBuilder.addModule(CureOnRemovalModule.HELMET);
  }

  /**
   * Gets the cure that removes effects granted by the given worn item.
   * @apiNote  Bridges 1.20's per item curative list onto 1.21's {@link EffectCure}. 1.20 let an effect instance name
   *           the item stacks that cured it and cured with {@code LivingEntity#curePotionEffects(ItemStack)}, which
   *           compared items; Tinkers used that to tie an effect to the armor piece that granted it, so it went away
   *           when you took the piece off and milk left it alone. 1.21 deleted curative items outright: an effect
   *           instance carries a set of {@link EffectCure} tokens instead, and a cure removes every effect carrying
   *           its token. A token is interned by name, so naming one after an item reproduces the old test exactly,
   *           item for item, and survives a save since the token set is part of the effect's serialized details.
   *           <p>
   *           These tokens replace only {@link EffectCures#MILK} on such an effect; see
   *           {@link slimeknights.tconstruct.tools.modifiers.effect.NoMilkEffect#fillEffectCures} for why
   *           {@link EffectCures#PROTECTED_BY_TOTEM} stays.
   */
  public static EffectCure curedByItem(Item item) {
    return ModifierUtil.curedByItem(item);
  }

  private static boolean drinkMilk(LivingEntity living, int duration, FluidAction action) {
    // strong bones has to be the helmet as we use it for curing
    // TODO 1.20: can use the new cure effects to make this work in any slot
    ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
    boolean didSomething = false;
    if (ModifierUtil.getModifierLevel(helmet, TinkerModifiers.strongBones.getId()) > 0) {
      MobEffectInstance effect = new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration);
      // cured by removing the helmet that granted it rather than by more milk, see curedByItem
      effect.getCures().remove(EffectCures.MILK);
      effect.getCures().add(curedByItem(helmet.getItem()));
      // on simulate, don't apply the effect, just ask if we can apply
      didSomething = action.execute() ? living.addEffect(effect) : living.canBeAffected(effect);
      // quick exit on simulate: no more information needed
      if (didSomething && action.simulate()) {
        return true;
      }
    }
    if (ArmorLevelModule.getLevel(living, CALCIFIABLE) > 0) {
      MobEffectInstance effect = new MobEffectInstance(TinkerModifiers.calcifiedEffect, duration, 0);
      didSomething |= action.execute() ? living.addEffect(effect) : living.canBeAffected(effect);
    }
    return didSomething;
  }

  /** Called when you finish drinking milk */
  private static void onItemFinishUse(LivingEntityUseItemEvent.Finish event) {
    LivingEntity living = event.getEntity();
    if (event.getItem().getItem() == Items.MILK_BUCKET) {
      drinkMilk(living, 1200, FluidAction.EXECUTE);
    }
  }


  /* Spilling effect */

  /** Singleton instance spilling effect */
  public static final FluidEffect<FluidEffectContext.Entity> FLUID_EFFECT = FluidEffect.simple((fluid, scale, context, action) -> {
    LivingEntity target = context.getLivingTarget();
    // while we could scale, doing it flat ensures we don't charge extra
    if (target != null && drinkMilk(target, (int)(20*10 * scale.value()), action)) {
      return scale.value();
    }
    return 0;
  });
}
