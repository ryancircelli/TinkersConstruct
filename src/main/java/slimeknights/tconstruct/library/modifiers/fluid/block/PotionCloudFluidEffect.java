package slimeknights.tconstruct.library.modifiers.fluid.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
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

/** Effect to create a lingering cloud at the hit block */
public record PotionCloudFluidEffect(float scale, TagPredicate predicate) implements FluidEffect<FluidEffectContext.Block> {
  public static final RecordLoadable<PotionCloudFluidEffect> LOADER = RecordLoadable.create(
    FloatLoadable.FROM_ZERO.requiredField("scale", e -> e.scale),
    TagPredicate.LOADABLE.defaultField("nbt", TagPredicate.ANY, e -> e.predicate),
    PotionCloudFluidEffect::new);

  @Override
  public RecordLoadable<PotionCloudFluidEffect> getLoader() {
    return LOADER;
  }

  /**
   * Gets the potion carried by a fluid stack.
   * @apiNote {@code PotionUtils} and {@code FluidStack#getTag} are both gone in 1.21; a fluid carries data
   * components exactly as an item does, so the primary path is {@link net.minecraft.core.component.DataComponents#POTION_CONTENTS},
   * the same component {@link slimeknights.tconstruct.fluids.fluids.PotionFluidType} writes for Tinkers' own potion
   * fluid. The raw "Potion" string read out of {@link DataComponents#CUSTOM_DATA} is compat for a fluid that has not
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
  public float apply(FluidStack fluid, EffectLevel level, FluidEffectContext.Block context, FluidAction action) {
    CustomData data = fluid.get(DataComponents.CUSTOM_DATA);
    CompoundTag tag = data != null ? data.copyTag() : null;
    if (predicate.test(tag) && context.isOffsetReplaceable()) {
      Potion potion = getPotion(fluid);
      List<MobEffectInstance> effects = potion.getEffects();
      if (!effects.isEmpty()) {
        float scale = level.value();
        if (action.execute()) {
          AreaEffectCloud cloud = MobEffectCloudFluidEffect.makeCloud(context);
          // not using set potion as we want to change the effect duration outself
          float effectScale = this.scale * scale;
          // keep track of how many effects are actually added
          boolean used = false;
          for (MobEffectInstance instance : effects) {
            if (instance.getEffect().value().isInstantenous()) {
              // only thing we have to scale on instant effects is the amplifier, though clouds automatically half instant effects for us
              int amplifier = (int)((instance.getAmplifier() + 1) * effectScale * 2) - 1;
              if (amplifier >= 0) {
                cloud.addEffect(new MobEffectInstance(instance.getEffect(), instance.getDuration(), amplifier, instance.isAmbient(), instance.isVisible(), instance.showIcon()));
                used = true;
              }
            } else {
              int duration = (int)(instance.getDuration() * effectScale);
              if (duration > 10) {
                cloud.addEffect(new MobEffectInstance(instance.getEffect(), duration, instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon()));
                used = true;
              }
            }
          }
          // TODO: custom effects from potion NBT?
          // TODO: custom color from potion NBT?
          if (used) {
            context.getLevel().addFreshEntity(cloud);
          } else {
            cloud.discard();
            return 0;
          }
        }
        return scale;
      }
    }
    return 0;
  }
}
