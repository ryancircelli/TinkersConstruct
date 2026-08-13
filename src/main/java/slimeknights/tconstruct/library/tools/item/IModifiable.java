package slimeknights.tconstruct.library.tools.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

/** Base interface for all tools that can receive modifiers */
public interface IModifiable extends ItemLike {
  /** @deprecated use {@link IndestructibleItemEntity#INDESTRUCTIBLE_ENTITY} */
  @Deprecated(forRemoval = true)
  ResourceLocation INDESTRUCTIBLE_ENTITY = IndestructibleItemEntity.INDESTRUCTIBLE_ENTITY;
  /** Volatile boolean key to make a tool spawn an indestructable entity */
  ResourceLocation SHINY = TConstruct.getResource("shiny");
  /** Volatile int key to increase a tool's range */
  ResourceLocation EXPANDED = TConstruct.getResource("expanded");
  /** @deprecated use {@link RarityModule#RARITY} */
  @Deprecated(forRemoval = true)
  ResourceLocation RARITY = RarityModule.RARITY;
  /** Modifier key to defer tool interaction to the offhand if present */
  ResourceLocation DEFER_OFFHAND = TConstruct.getResource("defer_offhand");
  /** Modifier key to entirely disable tool interaction */
  ResourceLocation NO_INTERACTION = TConstruct.getResource("no_interaction");

  /** Gets the definition of this tool for building and applying modifiers */
  ToolDefinition getToolDefinition();

  /**
   * Marks the given properties as belonging to a damageable item, the successor to overriding
   * {@code Item#canBeDepleted()} to return true.
   * <p>
   * 1.21 does not ask the item whether it can be damaged. {@code ItemStack#isDamageableItem()} answers from the stack:
   * it needs {@code minecraft:max_damage} and {@code minecraft:damage} present and {@code minecraft:unbreakable}
   * absent, and {@code IItemExtension#isDamageable(ItemStack)} - the closest thing to the old hook - is not consulted
   * anywhere in 21.1. So an item that wants durability has to say so in its default components, and a Tinkers tool that
   * does not is silently indestructible: every {@code hurtAndBreak} returns before it reaches
   * {@code IItemExtension#damageItem}.
   * <p>
   * The value is a marker; the real number comes from {@code getMaxDamage(ItemStack)}, which every damageable
   * modifiable item overrides with the durability stat. One is used rather than zero only because a zero maximum is a
   * division waiting to happen in code that has not been told to ask the item.
   * <p>
   * {@code ModifiableItem} does not call this: {@code TieredItem} already applies
   * {@link TinkerTier#getUses()} through the same {@code durability} call. Ammo does not call it either, as arrows and
   * shurikens are stackable and 1.21 rejects an item that is both stackable and damageable.
   * @apiNote  This mutates the passed properties, as every {@code Item.Properties} method does. Hand each item its own
   * instance; a shared one hands its durability to every item registered after this one.
   */
  static Item.Properties damageable(Item.Properties properties) {
    return properties.durability(1);
  }

  /** Gets the tool definition for the given item, or {@link ToolDefinition#EMPTY} if its not modifiable. */
  static ToolDefinition getToolDefinition(Item item) {
    if (item instanceof IModifiable modifiable) {
      return modifiable.getToolDefinition();
    }
    return ToolDefinition.EMPTY;
  }

  /**
   * Sets the rarity of the stack
   * @param volatileData     NBT
   * @param rarity  Rarity, only supports vanilla values
   */
  @Deprecated(forRemoval = true)
  static void setRarity(ModDataNBT volatileData, Rarity rarity) {
    RarityModule.setRarity(volatileData, rarity);
  }
}
