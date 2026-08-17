package slimeknights.tconstruct.tools.logic;

import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import slimeknights.mantle.MantleEvents;
import slimeknights.mantle.util.CombatHelper;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.common.TinkerDamageTypes;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.json.predicate.TinkerPredicate;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.entity.ReusableProjectile;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.armor.EffectImmunityModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorLevelModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorStatModule;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.capability.TinkerDataKeys;
import slimeknights.tconstruct.library.tools.helper.ModifierLootingHandler;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.SlimeBounceHandler;
import slimeknights.tconstruct.shared.TinkerAttributes;
import slimeknights.tconstruct.shared.TinkerEffects;
import slimeknights.tconstruct.tools.data.ModifierIds;
import slimeknights.tconstruct.tools.modifiers.effect.MagneticEffect;
import slimeknights.tconstruct.tools.modules.ranged.RestrictAngleModule;

import java.util.List;

/** Events to implement modifier specific behaviors, such as those defined by {@link TinkerDataKeys}. General hooks will typically be in {@link ToolEvents} */
@EventBusSubscriber(modid = TConstruct.MOD_ID, bus = Bus.GAME)
public class ModifierEvents {
  /** Multiplier for experience drops from events */
  private static final TinkerDataKey<Float> PROJECTILE_EXPERIENCE = TConstruct.createKey("projectile_experience");
  // TODO: move following to TinkerDataKeys?
  /** Volatile data float for amount of experience granted per level. Used by both projectiles and held tools. */
  public static final ResourceLocation EXPERIENCE = TConstruct.getResource("experience");
  /** Volatile data flag making a modifier grant the tool soulbound */
  public static final ResourceLocation SOULBOUND = TConstruct.getResource("soulbound");
  /** Volatile data int for making a modifier on a shield grant reflecting */
  public static final ResourceLocation REFLECTING = TConstruct.getResource("reflecting");

  @SubscribeEvent
  static void onKnockback(LivingKnockBackEvent event) {
    LivingEntity entity = event.getEntity();
    // read only, so do not hand an attachment to every entity that is ever knocked back
    TinkerDataCapability.Holder data = TinkerDataCapability.getExistingData(entity);
    Float armorKnockback = data != null ? data.get(TinkerDataKeys.KNOCKBACK) : null;
    double knockback = entity.getAttributeValue(TinkerAttributes.KNOCKBACK_MULTIPLIER)
                     + (armorKnockback != null ? armorKnockback : 0f);
    if (knockback != 1) {
      event.setStrength((float) (event.getStrength() * knockback));
    }
    // handle crystalstrike
    if (data != null) {
      // apply crystalbound bonus
      int crystalbound = data.get(TinkerDataKeys.CRYSTALSTRIKE, 0);
      if (crystalbound > 0) {
        RestrictAngleModule.onKnockback(event, crystalbound);
      }
    }
  }

  /** Reduce fall distance for fall damage */
  @SubscribeEvent
  static void onLivingFall(LivingFallEvent event) {
    LivingEntity entity = event.getEntity();
    double boost = entity.getAttributeValue(TinkerAttributes.SAFE_FALL_DISTANCE) + ArmorStatModule.getStat(entity, TinkerDataKeys.JUMP_BOOST);
    if (boost != 0) {
      event.setDistance((float) Math.max(event.getDistance() - boost, 0));
    }
  }

  /** Called on jumping to boost the jump height of the entity */
  @SubscribeEvent
  public static void onLivingJump(LivingJumpEvent event) {
    LivingEntity entity = event.getEntity();
    double boost = entity.getAttributeValue(TinkerAttributes.JUMP_BOOST) + ArmorStatModule.getStat(entity, TinkerDataKeys.JUMP_BOOST);
    if (boost > 0) {
      entity.setDeltaMovement(entity.getDeltaMovement().add(0, boost * 0.1, 0));
    }
  }

  /**
   * Prevents effects on the entity.
   * @apiNote  {@code Event.Result} is gone from the event bus, but this event kept a tri-state of its own:
   * {@link MobEffectEvent.Applicable.Result#DO_NOT_APPLY} is the old {@code DENY}.
   */
  @SubscribeEvent
  static void isPotionApplicable(MobEffectEvent.Applicable event) {
    // read only, so do not create the data for every entity that is ever handed an effect
    TinkerDataCapability.Holder data = TinkerDataCapability.getExistingData(event.getEntity());
    if (data != null) {
      Multiset<MobEffect> multiset = data.get(EffectImmunityModule.EFFECT_IMMUNITY);
      if (multiset != null) {
        // only grant immunity if the amount is high enough
        MobEffectInstance effectInstance = event.getEffectInstance();
        if (multiset.count(effectInstance.getEffect().value()) > effectInstance.getAmplifier()) {
          event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
      }
    }
  }

  /** Called when the player dies to store the item in the original inventory */
  @SubscribeEvent
  static void onLivingDeath(LivingDeathEvent event) {
    // if a projectile kills the target, mark the projectile level
    DamageSource source = event.getSource();
    if (source != null && source.getDirectEntity() instanceof Projectile projectile) {
      ModifierNBT modifiers = EntityModifierCapability.getOrEmpty(projectile);
      if (!modifiers.isEmpty()) {
        // write path, so create the data if the dying entity has none
        ModDataNBT projectileData = PersistentDataCapability.getData(projectile);
        TinkerDataCapability.getData(event.getEntity()).put(PROJECTILE_EXPERIENCE, projectileData.getFloat(EXPERIENCE));
      }
    }
    // this is the latest we can add slot markers to the items so we can return them to slots
    LivingEntity entity = event.getEntity();
    if (!entity.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && entity instanceof Player player && !(player instanceof FakePlayer)) {
      // start with the hotbar, must be soulbound or soul belt
      boolean soulBelt = ArmorLevelModule.getLevel(player, TinkerDataKeys.SOUL_BELT) > 0;
      Inventory inventory = player.getInventory();
      int hotbarSize = Inventory.getSelectionSize();
      for (int i = 0; i < hotbarSize; i++) {
        ItemStack stack = inventory.getItem(i);
        if (!stack.isEmpty() && (soulBelt || ModifierUtil.checkVolatileFlag(stack, SOULBOUND))) {
          markSoulboundSlot(stack, i);
        }
      }
      // rest of the inventory, only check soulbound (no modifier that moves non-soulbound currently)
      // note this includes armor and offhand
      int totalSize = inventory.getContainerSize();
      for (int i = hotbarSize; i < totalSize; i++) {
        ItemStack stack = inventory.getItem(i);
        if (!stack.isEmpty() && ModifierUtil.checkVolatileFlag(stack, SOULBOUND)) {
          markSoulboundSlot(stack, i);
        }
      }
    }
  }


  /**
   * Marks a stack with the inventory slot Mantle should return it to on respawn.
   * @apiNote  Item NBT does not exist in 1.21; Mantle's soulbound entry is a value inside the
   * {@code minecraft:custom_data} component under its original bare name, which
   * {@link MantleEvents#SOULBOUND_SLOT} still spells and which its javadoc keeps public for exactly this caller.
   * Writing the entry directly is what Mantle's own {@code DataKey} does underneath; Tinkers cannot build a key of its
   * own for the name, as a name may only be claimed once per game.
   */
  private static void markSoulboundSlot(ItemStack stack, int slot) {
    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MantleEvents.SOULBOUND_SLOT, slot));
  }


  /* Experience */

  /**
   * Boosts the experience a broken block drops.
   * @apiNote  1.21's {@code BlockEvent.BreakEvent} carries no experience at all - a block pops its own now - so this
   * moved to {@link BlockDropsEvent}, the event which does have the amount. Its value is already post enchantment,
   * exactly like the field this used to read, and the tool comes off the event rather than being guessed from the main
   * hand, which is a strictly better answer to the "correct hand" note below.
   */
  @SubscribeEvent
  static void blockDropsExperience(BlockDropsEvent event) {
    // a cancelled event drops no experience regardless, so there is nothing to boost
    if (event.isCanceled() || !(event.getBreaker() instanceof Player player)) {
      return;
    }
    int experience = event.getDroppedExperience();
    if (experience > 0) {
      // directly use modifier for held to ensure the correct hand applies
      // TODO: can we make that datapack configurable?
      double bonus = player.getAttributeValue(TinkerAttributes.EXPERIENCE_MULTIPLIER)
                   + ModifierUtil.getModifierLevel(event.getTool(), ModifierIds.experienced) * 0.5f
                   + ArmorStatModule.getStat(player, TinkerDataKeys.EXPERIENCE);
      event.setDroppedExperience(Math.max(0, (int)(experience * bonus)));
    }
  }

  @SubscribeEvent
  static void onExperienceDrop(LivingExperienceDropEvent event) {
    // boost entity experience if they are under the effects of experienced
    LivingEntity entity = event.getEntity();
    MobEffectInstance instance = entity.getEffect(TinkerEffects.experienced);
    double multiplier = 1 + (instance != null ? instance.getAmplifier() : 0);

    // always add armor boost, unfortunately no good way to stop shield stuff here
    Player player = event.getAttackingPlayer();
    if (player != null) {
      multiplier += player.getAttributeValue(TinkerAttributes.EXPERIENCE_MULTIPLIER) + ArmorStatModule.getStat(player, TinkerDataKeys.EXPERIENCE);
    }
    // if the target was killed by an experienced arrow, use that level
    // read only; onLivingDeath created the data if a projectile was involved
    TinkerDataCapability.Holder data = TinkerDataCapability.getExistingData(entity);
    Float projectileBoost = data != null ? data.get(PROJECTILE_EXPERIENCE) : null;
    if (projectileBoost != null) {
      multiplier += projectileBoost;
    // being -1 means no projectile was involved, so boost by held tool
    } else if (player != null) {
      IToolStackView tool = Modifier.getHeldTool(player, ModifierLootingHandler.getLootingSlot(player));
      if (tool != null) {
        multiplier += tool.getVolatileData().getFloat(EXPERIENCE);
      }
    }
    event.setDroppedExperience((int) (event.getDroppedExperience() * multiplier));
  }

  /**
   * Boosts critical hit damage.
   * @apiNote  The event's tri-state {@code Result} became the single {@link CriticalHitEvent#isCriticalHit()} boolean,
   * which already carries vanilla's answer plus whatever earlier listeners decided, so {@code ALLOW} is simply reading
   * it. {@code DENY} has no exact counterpart: it is only distinguishable from "vanilla says no" when it contradicts
   * vanilla, which is the case honoured below. A deny on a hit vanilla did not consider critical now looks identical
   * to the default and is treated as one, which is the single behaviour change in this handler.
   */
  @SubscribeEvent
  static void onCritical(CriticalHitEvent event) {
    // another listener explicitly turned off a critical vanilla wanted, respect that and stay out of it
    if (event.isVanillaCritical() && !event.isCriticalHit()) {
      return;
    }
    // force critical if not already critical and in the air
    LivingEntity living = event.getEntity();

    // critical boost is defined where the base value is 150%, setting smaller amounts can reduce the critical damage
    // this event however is defined in terms of adding or subtracting critical, so just treat it as additive
    Holder<Attribute> attribute = TinkerAttributes.CRITICAL_DAMAGE;
    double criticalBoost = living.getAttributeValue(attribute) - attribute.value().getDefaultValue() + ArmorStatModule.getStat(living, TinkerDataKeys.CRITICAL_DAMAGE);
    if (criticalBoost > 0) {
      // make it critical if we meet our simpler conditions, note this does not boost attack damage
      boolean isCritical = event.isCriticalHit();
      if (!isCritical && TinkerPredicate.AIRBORNE.matches(living)) {
        isCritical = true;
        event.setCriticalHit(true);
      }

      // if we either were or became critical, time to boost
      if (isCritical) {
        // adds +5% critical hit per level
        event.setDamageMultiplier((float) (event.getDamageMultiplier() + criticalBoost));
      }
    }
  }

  /**
   * Adjusts the duration of an effect as it starts.
   * @apiNote  Forge's curative items list became NeoForge's {@code EffectCure} set, so "this effect can be cured by
   * something" is {@code getCures().isEmpty()} instead of {@code getCurativeItems().isEmpty()}. Same question, same
   * default (milk cures everything unless the effect says otherwise), new spelling.
   */
  @SubscribeEvent
  static void onPotionStart(MobEffectEvent.Added event) {
    MobEffectInstance newEffect = event.getEffectInstance();
    if (!newEffect.isInfiniteDuration() && !newEffect.getCures().isEmpty()) {
      // use two different stats based on whether the effect is beneficial
      boolean beneficial = newEffect.getEffect().value().isBeneficial();
      LivingEntity entity = event.getEntity();
      double multiplier = entity.getAttributeValue(beneficial ? TinkerAttributes.GOOD_EFFECT_DURATION : TinkerAttributes.BAD_EFFECT_DURATION)
                        + ArmorStatModule.getStat(entity, beneficial ? TinkerDataKeys.GOOD_EFFECT_DURATION : TinkerDataKeys.BAD_EFFECT_DURATION);
      if (multiplier != 1) {
        // adjust duration as requested
        newEffect.duration = Math.max(1, (int)(newEffect.getDuration() * multiplier));
      }
    }
  }

  /** Called when an entity lands to handle bouncing */
  @SubscribeEvent
  static void bounceOnFall(LivingFallEvent event) {
    LivingEntity living = event.getEntity();
    // using fall distance as the event distance could be reduced by jump boost
    if (living == null || (living.getDeltaMovement().y > -0.3 && living.fallDistance < 3)) {
      return;
    }
    // can the entity bounce?
    if (living.getAttributeValue(TinkerAttributes.BOUNCY) < 1) {
      return;
    }

    // reduced fall damage when crouching
    if (living.isSuppressingBounce()) {
      event.setDamageMultiplier(0.5f);
      return;
    } else {
      event.setDamageMultiplier(0.0f);
    }

    // server players behave differently than non-server players, they have no velocity during the event, so we need to reverse engineer it
    Vec3 motion = living.getDeltaMovement();
    if (living instanceof ServerPlayer) {
      // velocity is lost on server players, but we dont have to defer the bounce
      // Forge's entity gravity attribute is vanilla's Attributes.GRAVITY in 1.21, same meaning and same default
      double gravity = living.getAttributeValue(Attributes.GRAVITY);
      double time = Math.sqrt(living.fallDistance / gravity);
      double velocity = gravity * time;
      living.setDeltaMovement(motion.x / 0.975f, velocity, motion.z / 0.975f);
      living.hurtMarked = true;

      // preserve momentum
      SlimeBounceHandler.addBounceHandler(living);
    } else {
      // for non-players, need to defer the bounce
      // only slow down half as much when bouncing
      float factor = living.fallDistance < 2 ? -0.7f : -0.9f;
      living.setDeltaMovement(motion.x / 0.975f, motion.y * factor, motion.z / 0.975f);
      SlimeBounceHandler.addBounceHandler(living, living.getDeltaMovement());
    }
    // update airborn status
    event.setDistance(0.0F);
    if (!living.level().isClientSide) {
      living.hasImpulse = true;
      event.setCanceled(true);
      living.setOnGround(false); // need to be on ground for server to process this event
    }
    living.playSound(Sounds.SLIMY_BOUNCE.getSound(), 1f, 1f);
  }

  @SubscribeEvent
  static void onProjectile(LivingGetProjectileEvent event) {
    // the held projectile method is not stack sensitive, so use this instead
    ItemStack bow = event.getProjectileWeaponItemStack();
    ItemStack ammo = event.getProjectileItemStack();
    // if the bow supports it, and we currently have arrows or nothing, we have a chance to swap the ammo
    // skip if the b
    if (bow.is(TinkerTags.Items.BALLISTAS) && ModifierUtil.checkVolatileFlag(bow, ModifiableBowItem.KEY_BALLISTA) && (ammo.isEmpty() || ammo.is(ItemTags.ARROWS))) {
      // check active flag
      int flag = ModifierUtil.getPersistentInt(bow, ModifiableBowItem.KEY_BALLISTA, 0);

      // if requesting a held ballista or haven't decided, find it in either hand
      if (flag <= ModifiableBowItem.FLAG_BALLISTA_HELD) {
        // try both hands, but don't return the bow itself
        LivingEntity entity = event.getEntity();
        ItemStack check = entity.getOffhandItem();
        if (check != bow && check.is(TinkerTags.Items.BALLISTA_AMMO)) {
          event.setProjectileItemStack(check);
        }
        check = entity.getMainHandItem();
        if (check != bow && check.is(TinkerTags.Items.BALLISTA_AMMO)) {
          event.setProjectileItemStack(check);
        }
      // if requesting a ballista from the quiver, cancel whatever stack we got from inventory
      } else if (flag == ModifiableBowItem.FLAG_BALLISTA_QUIVER) {
        event.setProjectileItemStack(ItemStack.EMPTY);
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.LOW) // lower priority so general modifier hook runs first
  static void projectileImpact(ProjectileImpactEvent event) {
    Entity entity = event.getEntity();
    Level level = entity.level();
    Projectile projectile = event.getProjectile();
    HitResult hit = event.getRayTraceResult();
    if (hit.getType() == Type.ENTITY && ((EntityHitResult) hit).getEntity() instanceof LivingEntity target) {
      // reflecting //
      // handle blacklist for projectiles
      if (!level.isClientSide && !RegistryHelper.contains(TinkerTags.EntityTypes.REFLECTING_BLACKLIST, projectile.getType()) && target != projectile.getOwner() && target.isUsingItem()) {
        ItemStack stack = target.getUseItem();
        // living entity must be using one of our shields
        if (stack.is(TinkerTags.Items.SHIELDS)) {
          ToolStack tool = ToolStack.mutable(stack);
          // make sure we actually have the modifier
          int reflectingTime = tool.getVolatileData().getInt(REFLECTING);
          if (reflectingTime > 0) {
            ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
            if (activeModifier != ModifierEntry.EMPTY) {
              GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
              int time = hook.getUseDuration(tool, activeModifier) - target.getUseItemRemainingTicks();
              // must be blocking, started blocking within the last 2*level seconds, and be within the block angle
              if (hook.getUseAction(tool, activeModifier) == UseAnim.BLOCK
                && (time >= 5 && time < reflectingTime)
                && InteractionHandler.canBlock(target, projectile.position(), tool)) {

                // time to actually reflect, this code is strongly based on code from the Parry mod
                // take ownership of the projectile so it counts as a player kill, except in the case of fishing bobbers
                if (!RegistryHelper.contains(TinkerTags.EntityTypes.REFLECTING_PRESERVE_OWNER, projectile.getType())) {
                  // arrows are dumb and mutate their pickup status when owner is set, so disagree and set it back
                  if (projectile instanceof AbstractArrow arrow) {
                    Pickup pickup = arrow.pickup;
                    arrow.setOwner(target);
                    arrow.pickup = pickup;
                  } else {
                    projectile.setOwner(target);
                  }
                  projectile.leftOwner = true;
                }

                Vec3 reboundAngle = target.getLookAngle();
                // use the shield accuracy and velocity stats when reflecting
                float velocity = ConditionalStatModifierHook.getModifiedStat(tool, target, ToolStats.VELOCITY) * 1.1f;
                projectile.shoot(reboundAngle.x, reboundAngle.y, reboundAngle.z, velocity, ModifierUtil.getInaccuracy(tool, target));
                if (projectile instanceof AbstractHurtingProjectile hurting) {
                  // the xPower/yPower/zPower acceleration vector became a scalar applied along the current movement,
                  // and shoot above already pointed that movement down the rebound angle, so the magnitude is all
                  // that is left to set. 0.1 is the same length the old reboundAngle * 0.1 had, as the angle is a unit vector
                  hurting.accelerationPower = 0.1;
                }
                if (target.getType() == EntityType.PLAYER) {
                  TinkerNetwork.getInstance().sendVanillaPacket(new ClientboundSetEntityMotionPacket(projectile), target);
                }
                level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F + level.random.nextFloat() * 0.4F);
                // NeoForge dropped ImpactResult along with setImpactResult; cancelling is the surviving spelling of
                // "do not process this impact, keep flying", which is what SKIP_ENTITY bought for a reflected projectile
                event.setCanceled(true);
                // damage the shield, and stop using it if needed
                if (ToolDamageUtil.damageAnimated(tool, 3, target, target.getUsedItemHand())) {
                  target.stopUsingItem();
                  entity.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + entity.level().random.nextFloat() * 0.4F);
                }
                // damageAnimated takes a view and never commits, so reflecting a projectile was free without this
                tool.updateStack();
              }
            }
          }
        }
      }

      // enderference //
      // TODO: this is a lot of code to make enderference work. A mixin would likely be better and provide better compatability
      // endermen are hardcoded to not take arrow damage, so disagree by reimplementing arrow damage right here
      // blacklist lets us not run on tridents or thrown tools. Former does not work with enderference, latter does so internally
      EntityType<?> projectileType = projectile.getType();
      if (TinkerEffects.needsEnderferenceOverride(target) && !projectileType.is(TinkerTags.EntityTypes.ENDERFERENCE_ARROW_BLACKLIST) && projectile instanceof AbstractArrow arrow) {
        // first, give up if we reached pierce capacity, and ensure list are created
        if (arrow.getPierceLevel() > 0) {
          if (arrow.piercingIgnoreEntityIds == null) {
            arrow.piercingIgnoreEntityIds = new IntOpenHashSet(5);
          }
          if (arrow.piercedAndKilledEntities == null) {
            arrow.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
          }
          if (arrow.piercingIgnoreEntityIds.size() >= arrow.getPierceLevel() + 1) {
            ReusableProjectile.discard(projectile);
            event.setCanceled(true);
            return;
          }
          arrow.piercingIgnoreEntityIds.add(target.getId());
        }

        // calculate damage, bonus on crit
        int damage = Mth.ceil(Mth.clamp(arrow.getDeltaMovement().length() * arrow.getBaseDamage(), 0.0D, Integer.MAX_VALUE));
        if (arrow.isCritArrow()) {
          damage = (int) Math.min(target.getRandom().nextInt(damage / 2 + 2) + (long) damage, Integer.MAX_VALUE);
        }

        // create damage source, don't use projectile sources as that makes endermen ignore it
        Entity owner = arrow.getOwner();
        DamageSource damageSource = CombatHelper.damageSource(TinkerDamageTypes.MELEE_ARROW, projectile, owner);
        LivingEntity livingOwner = ModifierUtil.asLiving(owner);
        if (livingOwner != null) {
          livingOwner.setLastHurtMob(target);
        }

        // handle fire
        int remainingFire = target.getRemainingFireTicks();
        if (arrow.isOnFire()) {
          target.igniteForSeconds(5);
        }

        // hurt the enderman
        if (target.hurt(damageSource, (float) damage)) {
          if (!level.isClientSide && arrow.getPierceLevel() <= 0) {
            target.setArrowCount(target.getArrowCount() + 1);
          }

          // knockback from punch. AbstractArrow#getKnockback is gone with the punch enchantment's level: knockback is an
          // enchantment value effect on the firing weapon now, read exactly as AbstractArrow#doKnockback reads it
          // (that method is protected and we are not an arrow). It also gained the knockback resistance factor vanilla applies
          ItemStack weapon = arrow.getWeaponItem();
          if (level instanceof ServerLevel server && weapon != null) {
            double knockback = EnchantmentHelper.modifyKnockback(server, weapon, target, damageSource, 0.0F);
            if (knockback > 0) {
              double resisted = Math.max(0.0D, 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
              Vec3 knockbackVec = arrow.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale(knockback * 0.6D * resisted);
              if (knockbackVec.lengthSqr() > 0.0D) {
                target.push(knockbackVec.x, 0.1D, knockbackVec.z);
              }
            }
          }

          // the two post hit enchantment calls collapsed into one: doPostAttackEffectsWithItemSource covers both halves,
          // and is what AbstractArrow#onHitEntity calls in their place
          if (level instanceof ServerLevel server && livingOwner != null) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(server, target, damageSource, weapon);
          }

          arrow.doPostHurtEffects(target);

          if (!target.isAlive() && arrow.piercedAndKilledEntities != null) {
            arrow.piercedAndKilledEntities.add(target);
          }

          if (!level.isClientSide && arrow.shotFromCrossbow() && owner instanceof ServerPlayer player) {
            if (arrow.piercedAndKilledEntities != null) {
              CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(player, arrow.piercedAndKilledEntities);
            } else if (!target.isAlive()) {
              CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(player, List.of(target));
            }
          }

          arrow.playSound(arrow.soundEvent, 1.0F, 1.2F / (target.getRandom().nextFloat() * 0.2F + 0.9F));
          if (arrow.getPierceLevel() <= 0) {
            ReusableProjectile.discard(projectile);
          }
        } else {
          // reset fire and drop the arrow
          target.setRemainingFireTicks(remainingFire);
          arrow.setDeltaMovement(arrow.getDeltaMovement().scale(-0.1D));
          arrow.setYRot(arrow.getYRot() + 180.0F);
          arrow.yRotO += 180.0F;
          if (!level.isClientSide && arrow.getDeltaMovement().lengthSqr() < 1.0E-7D) {
            if (arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
              arrow.spawnAtLocation(arrow.getPickupItem(), 0.1F);
            }

            ReusableProjectile.discard(projectile);
          }
        }
        // cancel event so arrow does not bounce
        event.setCanceled(true);
      }
    }
  }

  @SubscribeEvent
  static void onTeleport(EntityTeleportEvent event) {
    if (event.getEntity() instanceof LivingEntity living && living.hasEffect(TinkerEffects.enderference)) {
      event.setCanceled(true);
    }
  }

  /**
   * Called to perform the magnet for armor.
   * @apiNote  {@code LivingEntity.LivingTickEvent} fired at the top of the entity's tick, which is
   * {@link EntityTickEvent.Pre}. It covers every entity rather than only living ones, hence the pattern check.
   */
  @SubscribeEvent
  static void onLivingTick(EntityTickEvent.Pre event) {
    if (!(event.getEntity() instanceof LivingEntity entity)) {
      return;
    }
    if (!entity.isSpectator() && (entity.tickCount & 1) == 0) {
      int level = ArmorLevelModule.getLevel(entity, TinkerDataKeys.MAGNET);
      if (level > 0) {
        MagneticEffect.applyMagnet(entity, level - 1);
      }
    }
  }
}
