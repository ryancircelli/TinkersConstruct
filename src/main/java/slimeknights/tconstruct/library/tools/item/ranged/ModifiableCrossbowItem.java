package slimeknights.tconstruct.library.tools.item.ranged;

import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.client.item.ModifiableCrossbowClientExtension;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.BowAmmoModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.TinkerToolActions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook.KEY_DRAWTIME;

/**
 * A crossbow that keeps its loaded ammo in {@code minecraft:charged_projectiles}.
 * <p>
 * 1.20 kept it in the tool's own persistent data under {@code tconstruct:crossbow_ammo}, as an {@code ItemStack} saved
 * to NBT by hand. Neither half of that survives: an item stack cannot write itself to a tag without a registry lookup
 * in 1.21, and the thing it would be written into is a component of its own already. Using vanilla's component instead
 * of reinventing it costs the {@code KEY_CROSSBOW_AMMO} constant that outside code read, and buys the loaded state
 * being legible to everything that understands a crossbow - including {@code CrossbowAttackGoal}, which is the mob
 * usage the old TODO in {@link #fireCrossbow} asked about.
 */
public class ModifiableCrossbowItem extends ModifiableLauncherItem {
  private static final String PROJECTILE_KEY = "item.minecraft.crossbow.projectile";
  @Getter
  private final Predicate<ItemStack> supportedHeldProjectiles;
  /** If true, adds the item data to the drawback model. Its a bit less efficient but produces better models. False will just set a boolean. */
  private final boolean storeDrawingItem;

  public ModifiableCrossbowItem(Properties properties, ToolDefinition toolDefinition, Predicate<ItemStack> supportedHeldProjectiles, boolean storeDrawingItem) {
    super(properties, toolDefinition);
    this.supportedHeldProjectiles = supportedHeldProjectiles;
    this.storeDrawingItem = storeDrawingItem;
  }

  public ModifiableCrossbowItem(Properties properties, ToolDefinition toolDefinition, Predicate<ItemStack> supportedHeldProjectiles) {
    this(properties, toolDefinition, supportedHeldProjectiles, false);
  }

  public ModifiableCrossbowItem(Properties properties, ToolDefinition toolDefinition, boolean storeDrawingItem) {
    this(properties, toolDefinition, ARROW_OR_FIREWORK, storeDrawingItem);
  }

  public ModifiableCrossbowItem(Properties properties, ToolDefinition toolDefinition) {
    this(properties, toolDefinition, ARROW_OR_FIREWORK);
  }

  /* Properties */

  @Override
  public Predicate<ItemStack> getAllSupportedProjectiles() {
    return ARROW_ONLY;
  }

  @Override
  public int getDefaultProjectileRange() {
    return 8;
  }

  @Override
  public UseAnim getUseAnimation(ItemStack stack) {
    // crossbow is superhardcoded to crossbows, so use none and rely on the model
    return ModifierUtil.blockWhileCharging(ToolStack.from(stack), UseAnim.CROSSBOW);
  }

  @Override
  public boolean useOnRelease(ItemStack stack) {
    return true;
  }

  @Override
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(ModifiableCrossbowClientExtension.INSTANCE);
  }


  /* Ammo */

  /**
   * Gets the ammo loaded in the given crossbow, empty if it is not loaded.
   * @apiNote  Replaces reading {@code KEY_CROSSBOW_AMMO} out of the tool's persistent data.
   * Tinkers stores a multishot volley as one stack with a count, where vanilla stores one entry per shot, so only the
   * first entry is read; nothing but this class writes the component on a Tinkers crossbow.
   */
  public static ItemStack getChargedAmmo(ItemStack bow) {
    List<ItemStack> items = bow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).getItems();
    return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
  }

  /** Checks whether the given crossbow is loaded */
  public static boolean isCharged(ItemStack bow) {
    return !bow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
  }

  /** Loads the given ammo into the crossbow, or clears it if empty */
  public static void setChargedAmmo(ItemStack bow, ItemStack ammo) {
    if (ammo.isEmpty()) {
      bow.remove(DataComponents.CHARGED_PROJECTILES);
    } else {
      bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ammo));
    }
  }


  /* Arrow launching */

  /** Gets the arrow pitch */
  private static float getRandomShotPitch(float angle, RandomSource pRandom) {
    if (angle == 0) {
      return 1.0f;
    }
    return 1.0F / (pRandom.nextFloat() * 0.5F + 1.8F) + 0.53f + (angle / 10f);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack bow = player.getItemInHand(hand);

    ToolStack tool = ToolStack.mutable(bow);
    if (tool.isBroken()) {
      return InteractionResultHolder.fail(bow);
    }

    // yeah, its hardcoded, I cannot see a need to not hardcode this, request it if you need it
    boolean sinistral = hand == InteractionHand.MAIN_HAND && tool.getModifierLevel(TinkerModifiers.sinistral.getId()) > 0;

    // no ammo? not charged
    ItemStack heldAmmo = getChargedAmmo(bow);
    if (heldAmmo.isEmpty()) {
      // do not charge if sneaking and we have sinistral, gives you a way to activate the offhand when the crossbow is not charged
      if (sinistral && !player.getOffhandItem().isEmpty() && player.isCrouching()) {
        return InteractionResultHolder.pass(bow);
      }

      // if we have ammo, start charging
      ItemStack ammo = BowAmmoModifierHook.getAmmo(tool, bow, player, getSupportedHeldProjectiles());
      if (!ammo.isEmpty() || tool.getModifiers().has(TinkerTags.Modifiers.CHARGE_EMPTY_BOW_WITH_DRAWTIME)) {
        GeneralInteractionModifierHook.startDrawing(tool, player, 1);
        if (!ammo.isEmpty()) {
          if (storeDrawingItem) {
            // see ModifiableBowItem: the stack goes through the registry aware codec now
            tool.getPersistentData().put(KEY_DRAWBACK_AMMO, ammo.save(level.registryAccess()));
          } else {
            // boolean is enough to get detected by the property override, but won't bother the model
            tool.getPersistentData().putBoolean(KEY_DRAWBACK_AMMO, true);
          }
        }
        tool.updateStack();
        player.startUsingItem(hand);
        if (!level.isClientSide) {
          level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_QUICK_CHARGE_1, SoundSource.PLAYERS, 0.75F, 1.0F);
        }
        return InteractionResultHolder.consume(bow);
      }
      // can also block without ammo
      if (tool.getModifiers().has(TinkerTags.Modifiers.CHARGE_EMPTY_BOW_WITHOUT_DRAWTIME)) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bow);
      }
      return InteractionResultHolder.fail(bow);
    }

    // coming down here means we have ammo, try to use it

    // sinistral shoots on left click when in main hand, and lets us block instead of shooting if the offhand is empty
    if (sinistral) {
      ItemStack offhand = player.getOffhandItem();
      if (!offhand.isEmpty() && !offhand.is(Items.FIREWORK_ROCKET)) {
        return InteractionResultHolder.pass(bow);
      }
      // can block while filled with ammo
      if (ModifierUtil.canPerformAction(tool, TinkerToolActions.SHIELD_BLOCK)) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bow);
      }
    }

    // ammo already loaded? time to fire
    fireCrossbow(tool, bow, player, hand, heldAmmo);
    tool.updateStack();
    return InteractionResultHolder.consume(bow);
  }

  /**
   * Fires the crossbow
   * @param tool       Tool instance. The caller commits it, as firing damages the bow
   * @param bow        Crossbow stack, the ammo is cleared off it
   * @param player     Player firing
   * @param hand       Hand fired from
   * @param heldAmmo   Ammo used to fire, should be non-empty
   */
  public static void fireCrossbow(IToolStackView tool, ItemStack bow, Player player, InteractionHand hand, ItemStack heldAmmo) {
    fireCrossbow(tool, bow, player, player.getAbilities().instabuild, hand, heldAmmo);
  }

  /**
   * Fires the crossbow
   * @param tool       Tool instance. The caller commits it, as firing damages the bow
   * @param bow        Crossbow stack, the ammo is cleared off it
   * @param living     Entity firing
   * @param creative   If true, was fired in creative
   * @param hand       Hand fired from
   * @param heldAmmo   Ammo used to fire, should be non-empty
   */
  public static void fireCrossbow(IToolStackView tool, ItemStack bow, LivingEntity living, boolean creative, InteractionHand hand, ItemStack heldAmmo) {
    // ammo already loaded? time to fire
    Level level = living.level();
    if (!level.isClientSide) {
      // shoot the projectile
      int damage = 0;

      // don't need to calculate these multiple times
      float velocity = ConditionalStatModifierHook.getModifiedStat(tool, living, ToolStats.VELOCITY);
      float inaccuracy = ModifierUtil.getInaccuracy(tool, living);

      // the ammo has a stack size that may be greater than 1 (meaning multishot)
      ItemStack ammo = heldAmmo;
      float startAngle = getAngleStart(ammo.getCount());
      int primaryIndex = ammo.getCount() / 2;
      for (int arrowIndex = 0; arrowIndex < ammo.getCount(); arrowIndex++) {
        // setup projectile
        AbstractArrow arrow = null;
        Projectile projectile;
        float speed;
        if (ammo.is(Items.FIREWORK_ROCKET)) {
          // TODO: don't hardcode fireworks, perhaps use a map or a JSON behavior list
          projectile = new FireworkRocketEntity(level, ammo, living, living.getX(), living.getEyeY() - 0.15f, living.getZ(), true);
          speed = 1.5f;
          damage += 3;
        } else {
          ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem a ? a : (ArrowItem)Items.ARROW;
          // passing the bow as the firing weapon replaces setShotFromCrossbow, which 1.21 dropped: an arrow reads what
          // fired it off the weapon stack now, which is also where piercing comes from
          arrow = arrowItem.createArrow(level, ammo, living, bow);
          projectile = arrow;
          arrow.setCritArrow(true);
          arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
          speed = 3f;
          damage += 1;

          // vanilla arrows have a base damage of 2, cancel that out then add in our base damage to account for custom arrows with higher base damage
          float baseArrowDamage = (float)(arrow.getBaseDamage() - 2 + tool.getStats().get(ToolStats.PROJECTILE_DAMAGE));
          arrow.setBaseDamage(ConditionalStatModifierHook.getModifiedStat(tool, living, ToolStats.PROJECTILE_DAMAGE, baseArrowDamage));

          // fortunately, don't need to deal with vanilla infinity here, our infinity was dealt with during loading
          if (creative) {
            arrow.pickup = Pickup.CREATIVE_ONLY;
          }
        }

        // setup projectile
        Vec3 upVector = living.getUpVector(1.0f);
        float angle = startAngle + (10 * arrowIndex);
        Vector3f targetVector = living.getViewVector(1.0f).toVector3f().rotate((new Quaternionf()).setAngleAxis(angle * Math.PI / 180F, upVector.x, upVector.y, upVector.z));
        projectile.shoot(targetVector.x(), targetVector.y(), targetVector.z(), velocity * speed, inaccuracy);

        // add modifiers to the projectile, will let us use them on impact
        ModifierNBT modifiers = tool.getModifiers();
        EntityModifierCapability.getCapability(projectile).addModifiers(modifiers);

        // fetch the persistent data for the arrow as modifiers may want to store data
        ModDataNBT projectileData = PersistentDataCapability.getData(projectile);

        // let modifiers set properties
        for (ModifierEntry entry : modifiers.getModifiers()) {
          entry.getHook(ModifierHooks.PROJECTILE_LAUNCH).onProjectileLaunch(tool, entry, living, ammo, projectile, arrow, projectileData, arrowIndex == primaryIndex);
        }

        // finally, fire the projectile
        level.addFreshEntity(projectile);
        level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, getRandomShotPitch(angle, living.getRandom()));
      }

      // clear the ammo, damage the bow
      bow.remove(DataComponents.CHARGED_PROJECTILES);
      ToolDamageUtil.damageAnimated(tool, damage, living, hand);

      // stats
      if (living instanceof ServerPlayer serverPlayer) {
        CriteriaTriggers.SHOT_CROSSBOW.trigger(serverPlayer, living.getItemInHand(hand));
        serverPlayer.awardStat(Stats.ITEM_USED.get(tool.getItem()));
      }
    }
  }

  @Override
  public void releaseUsing(ItemStack bow, Level level, LivingEntity living, int chargeRemaining) {
    ToolStack tool = ToolStack.mutable(bow);

    // call the stop using modifier hook
    int duration = getUseDuration(bow, living);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, living, duration, chargeRemaining, ModifierEntry.EMPTY);
    }

    // any reason we shouldn't load?
    // specifically: broken, not fully charged, already have ammo
    ModDataNBT persistentData = tool.getPersistentData();
    if (tool.isBroken() || duration - chargeRemaining < persistentData.getInt(KEY_DRAWTIME) || isCharged(bow)) {
      tool.updateStack();
      return;
    }

    // find ammo and store it on the bow
    Player player = living instanceof Player p ? p : null;
    ItemStack ammo = BowAmmoModifierHook.consumeAmmo(tool, bow, living, player, getSupportedHeldProjectiles());
    if (!ammo.isEmpty()) {
      level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
      if (!level.isClientSide) {
        setChargedAmmo(bow, ammo);
        // if the crossbow broke during loading, fire immediately
        if (tool.isBroken()) {
          fireCrossbow(tool, bow, living, player != null && player.getAbilities().instabuild, living.getUsedItemHand(), ammo);
        }
      }
    }
    tool.updateStack();
  }

  @Override
  public List<Component> getStatInformation(IToolStackView tool, ItemStack stack, @Nullable Player player, List<Component> tooltips, TooltipKey key, TooltipFlag tooltipFlag) {
    tooltips = super.getStatInformation(tool, stack, player, tooltips, key, tooltipFlag);

    // if we have ammo, render that in the tooltip
    ItemStack heldStack = getChargedAmmo(stack);
    if (!heldStack.isEmpty()) {
      // basic info: item and count
      MutableComponent component = Component.translatable(PROJECTILE_KEY);
      int count = heldStack.getCount();
      if (count > 1) {
        component.append(" " + count + " ");
      } else {
        component.append(" ");
      }
      tooltips.add(component.append(heldStack.getDisplayName()));

      // copy the stack's tooltip if advanced
      if (tooltipFlag.isAdvanced() && player != null) {
        List<Component> nestedTooltip = new ArrayList<>();
        heldStack.getItem().appendHoverText(heldStack, Item.TooltipContext.of(player.level()), nestedTooltip, tooltipFlag);
        for (Component nested : nestedTooltip) {
          tooltips.add(Component.literal("  ").append(nested).withStyle(ChatFormatting.GRAY));
        }
      }
    }
    return tooltips;
  }
}
