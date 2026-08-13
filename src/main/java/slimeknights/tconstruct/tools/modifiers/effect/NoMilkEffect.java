package slimeknights.tconstruct.tools.modifiers.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import slimeknights.tconstruct.common.TinkerEffect;

import java.util.Set;

/**
 * Effect that cannot be cured with milk
 * TODO 1.21: move to {@link slimeknights.tconstruct.shared.effect}
 */
public class NoMilkEffect extends TinkerEffect {
  public NoMilkEffect(MobEffectCategory typeIn, int color, boolean show) {
    super(typeIn, color, show);
  }

  /**
   * @apiNote  Replaces returning an empty curative item list. 1.21 deleted curative items entirely; an effect instead
   *           declares which {@link EffectCure} tokens remove it, and a milk bucket asks for
   *           {@link EffectCures#MILK}. Dropping just that token from the default set is what "no milk" means here.
   *           {@link EffectCures#PROTECTED_BY_TOTEM} is kept: a totem of undying wiped every effect in 1.20 through
   *           {@code removeAllEffects}, which the curative item list had no say over, and 1.21 expresses that same
   *           clear as a cure.
   */
  @Override
  public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    cures.add(EffectCures.PROTECTED_BY_TOTEM);
  }
}
