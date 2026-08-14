package slimeknights.tconstruct.plugin.jei.util;

import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import javax.annotation.Nullable;

/**
 * Common logic for subtype interpreter between the fluid and item form of our potion. Based on a JEI class with the same name
 * @apiNote  1.21 has no {@code PotionUtils} and no potion NBT; a potion is the {@code minecraft:potion_contents} data
 *           component on both forms, so what the two implementations share is now the component rather than a tag.
 */
public interface PotionSubtypeInterpreter<T> extends IIngredientSubtypeInterpreter<T> {
  @Nullable
  PotionContents getContents(T ingredient);

  @Override
  default String apply(T ingredient, UidContext context) {
    PotionContents contents = getContents(ingredient);
    if (contents == null) {
      return IIngredientSubtypeInterpreter.NONE;
    }
    StringBuilder stringBuilder = new StringBuilder(Potion.getName(contents.potion(), ""));
    // custom effects are part of the contents now rather than a separate tag key, and getAllEffects covers both them
    // and the base potion's, which is what PotionUtils#getAllEffects did
    for (MobEffectInstance effect : contents.getAllEffects()) {
      stringBuilder.append(";").append(effect);
    }
    return stringBuilder.toString();
  }
}
