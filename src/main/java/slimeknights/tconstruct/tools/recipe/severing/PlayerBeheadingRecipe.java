package slimeknights.tconstruct.tools.recipe.severing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;
import slimeknights.tconstruct.tools.TinkerModifiers;

/** Beheading recipe that sets player skin */
public class PlayerBeheadingRecipe extends SeveringRecipe {
  public static final RecordLoadable<PlayerBeheadingRecipe> LOADER = RecordLoadable.create(BASE_CHANCE_FIELD, LOOTING_BONUS_FIELD, PlayerBeheadingRecipe::new);
  public PlayerBeheadingRecipe(float baseChance, float lootingBonus) {
    super(EntityIngredient.of(EntityType.PLAYER), ItemOutput.fromItem(Items.PLAYER_HEAD), baseChance, lootingBonus);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerModifiers.playerBeheadingSerializer.get();
  }

  /**
   * @apiNote  1.21 deleted the {@code SkullOwner} tag along with the rest of item NBT; a head's owner is the
   *           {@code minecraft:profile} component, set exactly as vanilla's {@code fill_player_head} loot function
   *           does it.
   */
  @Override
  public ItemStack getOutput(Entity entity) {
    ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
    if (entity instanceof Player player) {
      stack.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
    }
    return stack;
  }
}
