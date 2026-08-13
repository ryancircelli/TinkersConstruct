package slimeknights.tconstruct.library.modifiers.fluid.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.fluid.EffectLevel;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.recipe.TagPredicate;

import java.util.List;

/** Spilling effect that pulls the potion from a NBT potion fluid and applies it */
public record PotionFluidEffect(float scale, TagPredicate predicate) implements FluidEffect<FluidEffectContext.Entity> {
  public static final RecordLoadable<PotionFluidEffect> LOADER = RecordLoadable.create(
    FloatLoadable.FROM_ZERO.requiredField("scale", e -> e.scale),
    TagPredicate.LOADABLE.defaultField("nbt", TagPredicate.ANY, e -> e.predicate),
    PotionFluidEffect::new);

  @Override
  public RecordLoadable<PotionFluidEffect> getLoader() {
    return LOADER;
  }

  /**
   * Gets the potion carried by a fluid stack.
   * @apiNote {@code PotionUtils} and {@code FluidStack#getTag} are both gone in 1.21; a fluid carries data
   * components exactly as an item does, so the primary path is {@link DataComponents#POTION_CONTENTS}, the same
   * component {@link slimeknights.tconstruct.fluids.fluids.PotionFluidType} writes for Tinkers' own potion fluid.
   * The raw "Potion" string read out of {@link DataComponents#CUSTOM_DATA} is compat for a fluid that has not
   * migrated off free-form NBT (e.g. Create's own potion fluid) - unverified against that mod's own 1.21 port, since
   * the closest 1.21 analogue of a fluid's old free-form tag is the same custom-data component an item uses.
   * Defaults to an effect-less {@link Potion} exactly as {@code PotionUtils.getPotion} did for a missing key.
   */
  private static Potion getPotion(FluidStack fluid) {
    PotionContents contents = fluid.get(DataComponents.POTION_CONTENTS);
    if (contents != null && contents.potion().isPresent()) {
      return contents.potion().get().value();
    }
    CustomData data = fluid.get(DataComponents.CUSTOM_DATA);
    if (data != null) {
      CompoundTag tag = data.copyTag();
      if (tag.contains("Potion", Tag.TAG_STRING)) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Potion"));
        if (id != null) {
          Potion potion = BuiltInRegistries.POTION.get(id);
          if (potion != null) {
            return potion;
          }
        }
      }
    }
    return new Potion();
  }

  @Override
  public float apply(FluidStack fluid, EffectLevel level, FluidEffectContext.Entity context, FluidAction action) {
    LivingEntity target = context.getLivingTarget();
    CustomData data = fluid.get(DataComponents.CUSTOM_DATA);
    CompoundTag tag = data != null ? data.copyTag() : null;
    // must match the tag predicate
    if (target != null && predicate.test(tag)) {
      List<MobEffectInstance> effects = getPotion(fluid).getEffects();
      if (!effects.isEmpty()) {
        LivingEntity attacker = context.getEntity();
        Entity directSource = context.getDirectSource();
        Entity effectSource = context.getEffectSource();
        // prevent effects like instant damage from hitting hurt resistance
        int oldInvulnerableTime = target.invulnerableTime;
        // report whichever effect used the most
        float used = 0;
        for (MobEffectInstance instance : effects) {
          Holder<MobEffect> effect = instance.getEffect();
          if (effect.value().isInstantenous()) {
            // instant effects just apply full value always
            used = level.value();
            if (action.execute()) {
              target.invulnerableTime = 0;
              effect.value().applyInstantenousEffect(directSource, attacker, target, instance.getAmplifier(), used * scale);
            }
          } else {
            // if the potion already exists, we scale up the existing time
            MobEffectInstance existingEffect = target.getEffect(effect);
            int duration;
            if (existingEffect != null && existingEffect.getAmplifier() >= instance.getAmplifier()) {
              // if the existing level is larger, just skip, would be a cheese to increase said level
              // lower levels we treat as not having the effect, must be exact match to extend
              if (existingEffect.getAmplifier() > instance.getAmplifier()) {
                continue;
              }
              float existingLevel = existingEffect.getDuration() / scale / instance.getDuration();
              float effective = level.effective(existingLevel);
              // no potion to add? just save effort and stop here
              if (effective <= existingLevel) {
                continue;
              }
              duration = (int) (instance.getDuration() * scale * effective);
              // update how much we used, which is likely less than our max possible
              used = Math.max(used, effective - existingLevel);
            } else {
              // no relevant effect? just compute duration directly
              used = level.value();
              duration = (int) (instance.getDuration() * scale * used);
            }
            if (action.execute()) {
              target.addEffect(new MobEffectInstance(effect, duration, instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon()), effectSource);
            }
          }
        }
        target.invulnerableTime = oldInvulnerableTime;
        return used;
      }
    }
    return 0;
  }
}
