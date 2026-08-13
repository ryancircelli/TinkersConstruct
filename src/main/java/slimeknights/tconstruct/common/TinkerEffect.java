package slimeknights.tconstruct.common;

import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

import javax.annotation.Nullable;

/** Effect extension with a few helpers */
public class TinkerEffect extends MobEffect {
  /**
   * If true, the effect is visible in the inventory and the HUD, false to hide it.
   * <p>
   * 1.20 answered this from {@code initializeClient}, an override on the effect itself. 1.21 moved every client
   * extension to {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent}, which is a
   * client-only event, so the effect can only carry the flag; the listener that reads it lives in
   * {@code CommonsClientEvents} and covers every {@link TinkerEffect} in the registry at once.
   */
  @Getter
  private final boolean visible;

  public TinkerEffect(MobEffectCategory typeIn, boolean show) {
    this(typeIn, 0xffffff, show);
  }

  public TinkerEffect(MobEffectCategory typeIn, int color, boolean show) {
    super(typeIn, color);
    this.visible = show;
  }

  // override to change return type
  @Override
  public TinkerEffect addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id, double amount, Operation operation) {
    super.addAttributeModifier(attribute, id, amount, operation);
    return this;
  }

  /* Helpers */

  /**
   * Gets the level of the effect on the entity starting from 1, or 0 if not active
   * @param entity  Entity to check
   * @param effect  Effect to find
   * @return  Level, or 0 if inactive
   */
  public static int getLevel(LivingEntity entity, Holder<MobEffect> effect) {
    return getAmplifier(entity, effect) + 1;
  }

  /**
   * Gets the amplifier of the effect on the entity starting from 0, or -1 if not active
   * @param entity  Entity to check
   * @param effect  Effect to find
   * @return  Amplifier, or -1 if inactive
   */
  public static int getAmplifier(LivingEntity entity, Holder<MobEffect> effect) {
    @Nullable MobEffectInstance instance = entity.getEffect(effect);
    if (instance != null) {
      return instance.getAmplifier();
    }
    return -1;
  }
}
