package slimeknights.tconstruct.library.tools.helper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.ItemAbility;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolDataComponent;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStatsComponent;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerToolActions;
import slimeknights.tconstruct.tools.TinkerTools;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Generic modifier hooks that don't quite fit elsewhere */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModifierUtil {
  /** Drops an item at the given position */
  public static void dropItem(Level level, double x, double y, double z, ItemStack stack) {
    if (!stack.isEmpty() && !level.isClientSide) {
      ItemEntity ent = new ItemEntity(level, x, y, z, stack);
      ent.setDefaultPickUpDelay();
      RandomSource rand = level.random;
      ent.setDeltaMovement(ent.getDeltaMovement().add((rand.nextFloat() - rand.nextFloat()) * 0.1F,
                                                      rand.nextFloat() * 0.05F,
                                                      (rand.nextFloat() - rand.nextFloat()) * 0.1F));
      level.addFreshEntity(ent);
    }
  }

  /** Drops an item at the entity position */
  public static void dropItem(Entity target, ItemStack stack) {
    dropItem(target.level(), target.getX(), target.getY() + 1, target.getZ(), stack);
  }

  /** Drops an item at the entity position */
  public static void dropItem(Level level, Vec3 location, ItemStack stack) {
    dropItem(level, location.x(), location.y(), location.z(), stack);
  }

  /** Gets the entity as a living entity, or null if they are not a living entity */
  @Nullable
  public static LivingEntity asLiving(@Nullable Entity entity) {
    if (entity instanceof LivingEntity living) {
      return living;
    }
    return null;
  }

  /** Gets the entity as a player, or null if they are not a player */
  @Nullable
  public static Player asPlayer(@Nullable Entity entity) {
    if (entity instanceof Player player) {
      return player;
    }
    return null;
  }

  /**
   * Direct method to get the level of a modifier from a stack. If you need to get multiple modifier levels, using {@link ToolStack} is faster
   * @param stack     Stack to check
   * @param modifier  Modifier to search for
   * @return  Modifier level, or 0 if not present or the stack is not modifiable
   */
  public static int getModifierLevel(ItemStack stack, ModifierId modifier) {
    if (!stack.isEmpty() && stack.is(TinkerTags.Items.MODIFIABLE)) {
      // the effective modifier list is the computed half of the tool, absent until the tool has rebuilt at least once
      ToolStatsComponent derived = ToolStatsComponent.get(stack);
      if (derived != null) {
        return derived.modifiers().getLevel(modifier);
      }
    }
    return 0;
  }

  /** Checks if the given stack has upgrades */
  public static boolean hasUpgrades(ItemStack stack) {
    if (!stack.isEmpty() && stack.is(TinkerTags.Items.MODIFIABLE)) {
      return !ToolDataComponent.get(stack).upgrades().isEmpty();
    }
    return false;
  }

  /** Checks if the given slot may contain armor */
  public static boolean validArmorSlot(LivingEntity living, EquipmentSlot slot) {
    return slot.isArmor() || living.getItemBySlot(slot).is(TinkerTags.Items.HELD);
  }

  /** Checks if the given slot may contain armor */
  public static boolean validArmorSlot(IToolStackView tool, EquipmentSlot slot) {
    return slot.isArmor() || tool.hasTag(TinkerTags.Items.HELD);
  }

  /**
   * Empty compound handed back when a stack carries no data at all. Never written to; every caller below only reads.
   * Exists so the shortcuts stay allocation free, which is the entire reason they take a stack instead of a tool.
   */
  private static final CompoundTag NO_DATA = new CompoundTag();

  /**
   * Gets a stack's volatile modifier data without building a tool.
   * Volatile data lives in the computed half of the tool, so a stack that has never rebuilt has none.
   */
  private static CompoundTag volatileData(ItemStack stack) {
    ToolStatsComponent derived = ToolStatsComponent.get(stack);
    return derived == null ? NO_DATA : derived.volatileData();
  }

  /** Gets a stack's persistent modifier data without building a tool */
  private static CompoundTag persistentData(ItemStack stack) {
    return ToolDataComponent.get(stack).data();
  }

  /** Shortcut to get a volatile flag when the tool stack is not needed otherwise */
  public static boolean checkVolatileFlag(ItemStack stack, ResourceLocation flag) {
    return volatileData(stack).getBoolean(flag.toString());
  }

  /**
   * Shortcut to get a persistent flag when the tool stack is not needed otherwise
   * @implNote  Before 1.21 this read the volatile compound despite its name, so its only caller
   * ({@code TinkerItemProperties}, asking after {@code ModifiableLauncherItem.KEY_DRAWBACK_AMMO}, which is written to
   * persistent data) never saw the key. Reads persistent data now, as the name and every caller intend.
   */
  public static boolean checkPersistentPresent(ItemStack stack, ResourceLocation key) {
    return persistentData(stack).contains(key.toString());
  }

  /** Shortcut to get a volatile int value when the tool stack is not needed otherwise */
  public static int getVolatileInt(ItemStack stack, ResourceLocation flag) {
    return volatileData(stack).getInt(flag.toString());
  }

  /** Shortcut to get a volatile int value when the tool stack is not needed otherwise */
  public static int getPersistentInt(ItemStack stack, ResourceLocation flag, int defealtValue) {
    CompoundTag persistent = persistentData(stack);
    String flagString = flag.toString();
    if (persistent.contains(flagString, Tag.TAG_INT)) {
      return persistent.getInt(flagString);
    }
    return defealtValue;
  }

  /** Shortcut to get a persistent string value when the tool stack is not needed otherwise */
  public static String getPersistentString(ItemStack stack, ResourceLocation flag) {
    return persistentData(stack).getString(flag.toString());
  }

  /** Checks if a tool can perform the given action */
  public static boolean canPerformAction(IToolStackView tool, ItemAbility action) {
    if (!tool.isBroken()) {
      // can the tool do this action inherently?
      if (tool.getHook(ToolHooks.TOOL_ACTION).canPerformAction(tool, action)) {
        return true;
      }
      for (ModifierEntry entry : tool.getModifierList()) {
        if (entry.getHook(ModifierHooks.TOOL_ACTION).canPerformAction(tool, entry, action)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Makes the tool use the blocking animation if the blocking modifier is installed, falling back to the given animation.
   * Allows your tool to block while charging up.
   */
  public static UseAnim blockWhileCharging(IToolStackView tool, UseAnim fallback) {
    return canPerformAction(tool, TinkerToolActions.SHIELD_BLOCK) ? UseAnim.BLOCK : fallback;
  }

  /** Calculates inaccuracy from the conditional tool stat. */
  public static float getInaccuracy(IToolStackView tool, @Nullable LivingEntity living) {
    return 3 * (1 / ConditionalStatModifierHook.getModifiedStat(tool, living, ToolStats.ACCURACY) - 1);
  }

  /** @deprecated use {@link GeneralInteractionModifierHook#addCooldown(IToolStackView, Player, float)} */
  @Deprecated(forRemoval = true)
  public static void addCooldown(IToolStackView tool, Player player) {
    GeneralInteractionModifierHook.addCooldown(tool, player, 1);
  }

  /** Checks if this modifier is the one actively being used. Used for failure sound effects. */
  public static boolean isActiveModifier(IToolStackView tool, ModifierEntry modifier, ModifierEntry activeModifier) {
    // active modifier being us, or a bow is firing and no drawback ammo
    return modifier == activeModifier || (activeModifier.getLevel() == 0 && !tool.getPersistentData().contains(ModifiableLauncherItem.KEY_DRAWBACK_AMMO));
  }

  /**
   * Called before you call {@link Projectile#discard()} to update the fishing rod stack on the player.
   *
   * @param projectile  Projectile, will check if its our fishing bobber.
   * @param damage      Damage to deal to the rod.
   * @param applyCooldown  If true, applies draw speed as an item cooldown.
   * @return hand containing the fishing rod, or null if its in neither hand.
   */
  @SuppressWarnings("UnusedReturnValue") // API
  @Nullable
  public static InteractionHand updateFishingRod(Projectile projectile, int damage, boolean applyCooldown) {
    return updateFishingRod(projectile, damage, applyCooldown, ModifierId.EMPTY);
  }

  /**
   * Called before you call {@link Projectile#discard()} to update the fishing rod stack on the player.
   *
   * @param projectile  Projectile, will check if its our fishing bobber.
   * @param damage      Damage to deal to the rod.
   * @param applyCooldown  If true, applies draw speed as an item cooldown.
   * @param cause       Modifier causing the retraction.
   * @return hand containing the fishing rod, or null if its in neither hand.
   */
  @Nullable
  public static InteractionHand updateFishingRod(Projectile projectile, int damage, boolean applyCooldown, ModifierId cause) {
    if (projectile.getType() == TinkerTools.fishingHook.get() && projectile.getOwner() instanceof LivingEntity living) {
      ItemStack stack = living.getMainHandItem();
      InteractionHand hand = InteractionHand.MAIN_HAND;
      // must be able to cast
      if (!stack.canPerformAction(TinkerToolActions.FISHING_ROD_CAST)) {
        stack = living.getOffhandItem();
        if (!stack.canPerformAction(TinkerToolActions.FISHING_ROD_CAST)) {
          return null;
        }
        hand = InteractionHand.OFF_HAND;
      }
      // must be modifiable
      if (stack.is(TinkerTags.Items.MODIFIABLE)) {
        // skip making the tool stack object if not needed, might be asking just for the hand.
        if (applyCooldown || damage > 0) {
          ToolStack tool = ToolStack.mutable(stack);
          // trigger cooldown on the item
          if (applyCooldown && living instanceof Player player) {
            addCooldown(tool, player);
          }
          // damage the rod
          if (damage > 0) {
            // if we are applying cooldown, means this is a full retraction from block so this was primary damage
            // no cooldown is done on secondary effects like entity hitting
            ToolDamageUtil.damageAnimated(tool, damage, living, hand, cause);
          }
          // both branches above may have edited the tool, and the stack is the live one on the holder
          tool.updateStack();
        }
        return hand;
      }
    }
    return null;
  }

  /** Interface used for {@link #foodConsumer} */
  public interface FoodConsumer {
    /** Called when food is eaten to notify compat that food was eaten */
    void onConsume(Player player, ItemStack stack, int hunger, float saturation);
  }

  /** Instance of the current food consumer, will be either no-op or an implementation calling the Diet API, never null. */
  @Nonnull
  public static FoodConsumer foodConsumer = (player, stack, hunger, saturation) -> {};

  /* Shield disabling */
  /** Map of how to disable shields for different targets */
  private static final Map<EntityType<?>, Consumer<Entity>> SHIELD_DISABLER = new HashMap<>();

  /** Registers a method for shield disabling */
  public static void registerShieldDisabler(Consumer<Entity> disabler, EntityType<?>... types) {
    for (EntityType<?> type : types){
      SHIELD_DISABLER.putIfAbsent(type, disabler);
    }
  }

  /** Disables shield for the target entity */
  public static void disableShield(Entity entity) {
    Consumer<Entity> consumer = SHIELD_DISABLER.get(entity.getType());
    if (consumer != null) {
      consumer.accept(entity);
    }
  }

  /* Effect cures */

  /**
   * Gets the cure token for an effect that is cured by removing the item that granted it.
   * <p>
   * 1.21 deleted curative items: an effect no longer carries a list of {@link ItemStack}s that clear it, it carries a
   * set of {@link EffectCure} tokens, and a token is interned by name. 1.20's "this effect is cured by exactly this
   * armor piece" was expressed by putting that piece in the curative list, so naming a token after the item is the
   * same test with the same granularity - and it survives a save, because the token set is part of what a
   * {@link net.minecraft.world.effect.MobEffectInstance} serializes.
   */
  public static EffectCure curedByItem(Item item) {
    return EffectCure.get(TConstruct.resourceString("cured_by_item/" + BuiltInRegistries.ITEM.getKey(item)));
  }

  /**
   * Gets the cure an item performs, for a caller that used to pass a stack to {@code LivingEntity#curePotionEffects}.
   * <p>
   * That method asked every active effect whether the stack was in its curative list; a vanilla effect's list held one
   * item, the milk bucket, which is now {@link EffectCures#MILK}, and honey is the only other vanilla entry. Anything
   * else is a Tinkers-granted effect keyed by {@link #curedByItem(Item)}, so the mapping is total for every item that
   * ever cured anything.
   */
  public static EffectCure cureFromItem(Item item) {
    if (item == Items.MILK_BUCKET) {
      return EffectCures.MILK;
    }
    if (item == Items.HONEY_BOTTLE) {
      return EffectCures.HONEY;
    }
    return curedByItem(item);
  }
}
