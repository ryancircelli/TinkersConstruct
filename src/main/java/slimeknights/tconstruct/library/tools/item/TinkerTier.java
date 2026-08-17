package slimeknights.tconstruct.library.tools.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * Dummy tier implementation to allow Tinkers' Construct to make modifiable items extend TieredItem, for piglin compat.
 * <p>
 * The reason this class exists is unchanged in 1.21: {@code AbstractPiglin} still decides whether a mob is holding a
 * melee weapon by asking whether the item is a {@link net.minecraft.world.item.TieredItem}. What a tier <i>says</i> has
 * changed. {@code getLevel()} and {@code getTag()} are gone and {@link #getIncorrectBlocksForDrops()} replaced them;
 * Forge's {@code TierSortingRegistry}, which existed to give modded tiers a place in the mining level order, has no
 * NeoForge successor because a tier is no longer ordered at all - it just names the blocks it fails to drop.
 * <p>
 * None of these answers reach gameplay. {@code ModifiableItem} overrides {@code isCorrectToolForDrops} and
 * {@code getDestroySpeed} with the tool's own harvest logic, and nothing asks this tier for a
 * {@link Tier#createToolProperties(TagKey)} component, so the {@code minecraft:tool} component a vanilla tiered item
 * carries is never built.
 * <p>
 * One answer did become load bearing: {@code TieredItem} feeds {@link #getUses()} to
 * {@code Item.Properties#durability(int)}, which stamps the {@code minecraft:max_damage} and {@code minecraft:damage}
 * components onto the item. Those two components are what makes a stack damageable in 1.21, replacing 1.20's
 * {@code Item#canBeDepleted()} - see {@link IModifiable#damageable(net.minecraft.world.item.Item.Properties)}. The
 * number is still irrelevant, as {@code ModifiableItem} overrides {@code getMaxDamage(ItemStack)} with the durability
 * stat, but their presence is not.
 */
public enum TinkerTier implements Tier {
  INSTANCE;

  @Override
  public int getUses() {
    return 0;
  }

  @Override
  public float getSpeed() {
    return 0;
  }

  @Override
  public float getAttackDamageBonus() {
    return 0;
  }

  /**
   * @return  The netherite tag, the empty end of vanilla's five incorrect block tags. A tool that answers this mines
   * everything, which is the closest a dummy gets to the old level 0 with no opinion.
   */
  @Override
  public TagKey<Block> getIncorrectBlocksForDrops() {
    return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
  }

  @Override
  public int getEnchantmentValue() {
    return 0;
  }

  @Override
  public Ingredient getRepairIngredient() {
    return Ingredient.EMPTY;
  }
}
