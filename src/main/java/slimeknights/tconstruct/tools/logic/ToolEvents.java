package slimeknights.tconstruct.tools.logic;

import com.google.common.collect.Multiset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import slimeknights.mantle.data.predicate.damage.DamageSourcePredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerEffect;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.events.TinkerToolEvent.ToolHarvestEvent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.armor.ModifyDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.armor.OnAttackedModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.armor.ProtectionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.mining.BreakSpeedContext;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.armor.MobDisguiseModule;
import slimeknights.tconstruct.library.modifiers.modules.technical.ArmorStatModule;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataKeys;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.context.EquipmentIterator.EquipmentEntry;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.helper.ArmorUtil;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.BlockSideHitListener;
import slimeknights.tconstruct.shared.TinkerAttributes;
import slimeknights.tconstruct.shared.TinkerEffects;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.network.SyncProjectileModifiersPacket;

import java.util.List;
import java.util.Objects;

/**
 * Event subscriber for tool events
 */
@EventBusSubscriber(modid = TConstruct.MOD_ID, bus = Bus.GAME)
public class ToolEvents {
  @SubscribeEvent
  static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
    Player player = event.getEntity();

    // tool break speed hook
    ItemStack stack = player.getMainHandItem();
    if (stack.is(TinkerTags.Items.HARVEST)) {
      IToolStackView tool = ToolStack.from(stack);
      if (!tool.isBroken()) {
        List<ModifierEntry> modifiers = tool.getModifierList();
        if (!modifiers.isEmpty()) {
          // build context
          BreakSpeedContext context = new BreakSpeedContext.Event(
            event,
            BlockSideHitListener.getSideHit(event.getEntity()),
            IsEffectiveToolHook.isEffective(tool, event.getState()),
            BreakSpeedContext.getMiningModifier(event.getEntity())
          );

          // run each modifier hook
          float speed = event.getNewSpeed();
          for (ModifierEntry entry : tool.getModifierList()) {
            speed = entry.getHook(ModifierHooks.BREAK_SPEED).modifyBreakSpeed(tool, entry, context, speed);
            // if any modifier cancels mining, stop right here
            if (speed < 0 || event.isCanceled()) {
              break;
            }
          }
          // update the speed
          event.setNewSpeed(speed);
        }
      }
    }

    // next, add in armor haste
    double armorMultiplier = player.getAttributeValue(TinkerAttributes.MINING_SPEED_MULTIPLIER) + ArmorStatModule.getStat(player, TinkerDataKeys.MINING_SPEED);
    if (armorMultiplier >= 0) {
      event.setNewSpeed((float) (event.getNewSpeed() * armorMultiplier));
    }
  }

  /**
   * Implements area of effect mining for hammers, excavators and scythes.
   * <p>
   * In 1.20 this entered through {@code IForgeItem#onBlockStartBreak}, overridden by {@link ModifiableItem} and
   * {@link ModifiableLauncherItem}. NeoForge 21.1 deleted that hook outright rather than renaming it, leaving
   * {@link BreakEvent} as the only cancellable pre-break hook: {@code CommonHooks#fireBlockBreak} posts it at the very
   * top of {@code ServerPlayerGameMode#destroyBlock}, before {@code playerWillDestroy}, {@code ItemStack#mineBlock} and
   * the block removal, which is exactly where the old hook ran. Cancelling it also sends the breaking player a
   * {@code ClientboundBlockUpdatePacket}, which is the client resync returning true from {@code onBlockStartBreak}
   * used to buy us.
   * <p>
   * Lowest priority plus the cancellation bail reproduces the 1.20 ordering exactly: Forge fired {@code BreakEvent}
   * first and only then called {@code onBlockStartBreak}, so Tinkers ran after every other listener had had its say and
   * only when the break was going ahead. NeoForge hands an event to every listener regardless of cancellation, so that
   * check is ours to make.
   */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  static void startBlockBreak(BreakEvent event) {
    // someone else already stopped this break, there is nothing left for us to take over
    if (event.isCanceled()) {
      return;
    }
    Player player = event.getPlayer();
    ItemStack stack = player.getMainHandItem();
    // guard on exactly the two item classes that carried the 1.20 override. A tag would be wider: modifiable armor,
    // arrows and shurikens are all tagged but none of them ever had it
    Item item = stack.getItem();
    if (item instanceof ModifiableItem) {
      // a stack of more than one tool refuses to break blocks, matching ModifiableItem#getDestroySpeed returning 0.
      // short circuits before the harvest logic just as the 1.20 override's `||` did
      if (stack.getCount() > 1 || ToolHarvestLogic.handleBlockBreak(stack, event.getPos(), player)) {
        event.setCanceled(true);
      }
    } else if (item instanceof ModifiableLauncherItem && ToolHarvestLogic.handleBlockBreak(stack, event.getPos(), player)) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  static void onHarvest(ToolHarvestEvent event) {
    // prevent processing if already processed
    if (event.getOverride() != TriState.DEFAULT) {
      return;
    }
    BlockState state = event.getState();
    Block block = state.getBlock();
    Level world = event.getWorld();
    BlockPos pos = event.getPos();

    // carve pumpkins
    if (block == Blocks.PUMPKIN) {
      Direction facing = event.getContext().getClickedFace();
      if (facing.getAxis() == Direction.Axis.Y) {
        facing = event.getContext().getHorizontalDirection().getOpposite();
      }
      // carve block
      world.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
      world.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, facing), 11);
      // spawn seeds
      ItemEntity itemEntity = new ItemEntity(
        world,
        pos.getX() + 0.5D + facing.getStepX() * 0.65D,
        pos.getY() + 0.1D,
        pos.getZ() + 0.5D + facing.getStepZ() * 0.65D,
        new ItemStack(Items.PUMPKIN_SEEDS, 4));
      itemEntity.setDeltaMovement(
        0.05D * facing.getStepX() + world.random.nextDouble() * 0.02D,
        0.05D,
        0.05D * facing.getStepZ() + world.random.nextDouble() * 0.02D);
      world.addFreshEntity(itemEntity);
      event.setOverride(TriState.TRUE);
    }

    // hives: get the honey
    if (block instanceof BeehiveBlock beehive) {
      int level = state.getValue(BeehiveBlock.HONEY_LEVEL);
      if (level >= 5) {
        // first, spawn the honey
        world.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
        Block.popResource(world, pos, new ItemStack(Items.HONEYCOMB, 3));

        // if not smoking, make the bees angry
        if (!CampfireBlock.isSmokeyPos(world, pos)) {
          if (beehive.hiveContainsBees(world, pos)) {
            beehive.angerNearbyBees(world, pos);
          }
          beehive.releaseBeesAndResetHoneyLevel(world, state, pos, event.getPlayer(), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        } else {
          beehive.resetHoneyLevel(world, state, pos);
        }
        event.setOverride(TriState.TRUE);
      } else {
        event.setOverride(TriState.FALSE);
      }
    }
  }

  /**
   * Determines how much to damage armor based on the given damage to the player
   * @param damage  Amount to damage the player
   * @return  Amount to damage the armor
   */
  private static int getArmorDamage(float damage) {
    damage /= 4;
    if (damage < 1) {
      return 1;
    }
    return (int)damage;
  }

  /** Modifier ID used for the extra correction damage on armor. Needs to be a "real modifier" for the tag so we use protection as a reasonable enough source. */
  private static final ModifierId ARMOR_DAMAGE = new ModifierId(TConstruct.MOD_ID, "protection");

  /**
   * Runs every armor hook that used to be split across {@code LivingAttackEvent} and {@code LivingHurtEvent}.
   * <p>
   * 1.21 deleted both of those events and left exactly one slot before mitigation, so all four hooks share this
   * listener and the order between them is decided here rather than by which event fires first. It is the order the two
   * events ran in: cancel ({@link ModifierHooks#DAMAGE_BLOCK}), then respond ({@link ModifierHooks#ON_ATTACKED} and
   * {@link ModifierHooks#DAMAGE_DEALT}), then modify ({@link ModifierHooks#MODIFY_HURT} and
   * {@link ModifierHooks#PROTECTION}). The responding hooks still see the unmodified incoming amount, as they did when
   * they ran an event earlier.
   * <p>
   * The 1.20 attack listener opened by returning on the client, on a dead entity and on an invulnerable one.
   * {@link LivingEntity#hurt(DamageSource, float)} only reaches this event on the server, for a living entity that is
   * not invulnerable to the source, so all three are now the event's own preconditions.
   * <p>
   * Low priority to minimize conflict as we apply reduction as if we are the final change to damage before vanilla.
   */
  @SubscribeEvent(priority = EventPriority.LOW)
  static void livingHurt(LivingIncomingDamageEvent event) {
    LivingEntity entity = event.getEntity();

    // determine if there is any modifiable armor, if not nothing to do
    DamageSource source = event.getSource();
    EquipmentContext context = new EquipmentContext(entity);
    float vanillaModifier = 0;
    float modifierValue = 0;
    float originalDamage = event.getAmount();
    Entity attacker = source.getEntity();


    /* cancel, then respond: what used to be LivingAttackEvent */

    // a lot of counterattack hooks want to detect direct attacks, so save time by calculating once
    boolean isDirectDamage = OnAttackedModifierHook.isDirectDamage(source);
    if (context.hasModifiableArmor()) {
      // first we need to determine if any of the four slots want to cancel the event
      for (EquipmentSlot slotType : EquipmentSlot.values()) {
        IToolStackView toolStack = context.getValidTool(slotType);
        if (toolStack != null && !toolStack.isBroken()) {
          for (ModifierEntry entry : toolStack.getModifierList()) {
            if (entry.getHook(ModifierHooks.DAMAGE_BLOCK).isDamageBlocked(toolStack, entry, context, slotType, source, originalDamage)) {
              event.setCanceled(true);
              return;
            }
          }
        }
      }

      // then we need to determine if any want to respond assuming its not canceled
      OnAttackedModifierHook.handleAttack(ModifierHooks.ON_ATTACKED, context, source, originalDamage, isDirectDamage);
    }

    // next, consider the attacker is wearing modifiable armor
    if (attacker instanceof LivingEntity livingAttacker) {
      EquipmentContext attackerContext = new EquipmentContext(livingAttacker);
      if (attackerContext.hasModifiableArmor()) {
        for (EquipmentSlot slotType : ModifiableArmorMaterial.ARMOR_SLOTS) {
          IToolStackView toolStack = attackerContext.getToolInSlot(slotType);
          if (toolStack != null && !toolStack.isBroken()) {
            for (ModifierEntry entry : toolStack.getModifierList()) {
              entry.getHook(ModifierHooks.DAMAGE_DEALT).onDamageDealt(toolStack, entry, attackerContext, slotType, entity, source, originalDamage, isDirectDamage);
            }
          }
        }
      }
    }


    /* modify: what used to be LivingHurtEvent */

    if (attacker instanceof LivingEntity living) {
      // boost damage based on monster's melee weapon
      if (Config.COMMON.allowMonsterMeleeModifiers.get() && source.is(TinkerTags.DamageTypes.MODIFIER_WHITELIST) && !living.getType().is(TinkerTags.EntityTypes.DAMAGE_MODIFIER_BLACKLIST)) {
        ItemStack weapon = living.getMainHandItem();
        if (!weapon.isEmpty() && weapon.is(TinkerTags.Items.MELEE_WEAPON)) {
          IToolStackView tool = ToolStack.from(weapon);
          // already know the player is null
          ToolAttackContext meleeContext = ToolAttackContext.attacker(living, null).target(entity).applyAttributes().build();
          float baseDamage = originalDamage;
          for (ModifierEntry entry : tool.getModifiers()) {
            originalDamage = entry.getHook(ModifierHooks.MONSTER_MELEE_DAMAGE).getMeleeDamage(tool, entry, meleeContext, baseDamage, originalDamage);
          }
        }
      }

      // run shulking global damage "boost", its a bit hardcoded Java wise to make it softcoded in JSON
      if (attacker.isCrouching()) {
        double crouchMultiplier = living.getAttributeValue(TinkerAttributes.CROUCH_DAMAGE_MULTIPLIER);
        crouchMultiplier += ArmorStatModule.getStat(attacker, TinkerDataKeys.CROUCH_DAMAGE);
        if (crouchMultiplier != 0) {
          originalDamage *= crouchMultiplier;
        }
      }
    }

    // conducting - boosts damage from fire
    if (source.is(TinkerTags.DamageTypes.FIRE_PROTECTION)) {
      int level = TinkerEffect.getLevel(entity, TinkerEffects.conductive);
      if (level > 0) {
        originalDamage *= Math.pow(2, level);
      }
    }
    // venom - boosts damage from magic
    if (source.is(TinkerTags.DamageTypes.MAGIC_PROTECTION)) {
      int level = TinkerEffect.getLevel(entity, TinkerEffects.venom);
      if (level > 0) {
        originalDamage *= Math.pow(2, level);
      }
    }
    
    // ensure any changes made so far apply, though we may change it again
    event.setAmount(originalDamage);

    // for our own armor, we have boosts from modifiers to consider
    if (context.hasModifiableArmor()) {
      // first, allow modifiers to change the damage being dealt and respond to it happening
      originalDamage = ModifyDamageModifierHook.modifyDamageTaken(ModifierHooks.MODIFY_HURT, context, source, originalDamage, isDirectDamage);
      event.setAmount(originalDamage);
      if (originalDamage <= 0) {
        event.setCanceled(true);
        return;
      }

      // remaining logic is reducing damage like vanilla protection
      // fetch vanilla enchant level, assuming its not bypassed in vanilla
      // 1.21 asks the level instead of the armor slots, as protection is an enchantment effect on the whole loadout now
      if (DamageSourcePredicate.CAN_PROTECT.matches(source) && entity.level() instanceof ServerLevel server) {
        modifierValue = vanillaModifier = EnchantmentHelper.getDamageProtection(server, entity, source);
      }

      // next, determine how much tinkers armor wants to change it
      // note that armor modifiers can choose to block "absolute damage" if they wish, currently just starving damage I think
      for (EquipmentEntry entry : context.iterateTools()) {
        ModifierEntry modifier = entry.modifier();
        modifierValue = modifier.getHook(ModifierHooks.PROTECTION).getProtectionModifier(entry.tool(), modifier, context, entry.slot(), source, modifierValue);
      }

      // give slimes a 4x armor boost
      if (entity.getType().is(TinkerTags.EntityTypes.SMALL_ARMOR)) {
        modifierValue *= 4;
      }
    } else if (DamageSourcePredicate.CAN_PROTECT.matches(source) && entity.getType().is(TinkerTags.EntityTypes.SMALL_ARMOR) && entity.level() instanceof ServerLevel server) {
      vanillaModifier = EnchantmentHelper.getDamageProtection(server, entity, source);
      modifierValue = vanillaModifier * 4;
    }

    // if we changed anything, run our logic. Changing the cap has 2 problematic cases where same value will work:
    // * increased cap and vanilla is over the vanilla cap
    // * decreased cap and vanilla is now under the cap
    // that said, don't actually care about cap unless we have some protection, can use vanilla to simplify logic
    float cap = 20f;
    if (modifierValue > 0) {
      cap = (float) ProtectionModifierHook.getProtectionCap(entity, context.getDataHolder());
    }
    if (vanillaModifier != modifierValue || (cap > 20 && vanillaModifier > 20) || (cap < 20 && vanillaModifier > cap)) {
      // fetch armor and toughness if blockable, passing in 0 to the logic will skip the armor calculations
      float armor = 0, toughness = 0;
      if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
        armor = entity.getArmorValue();
        toughness = (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
      }

      // set the final dealt damage
      float finalDamage = ArmorUtil.getDamageForEvent(entity, source, originalDamage, armor, toughness, vanillaModifier, modifierValue, cap);
      event.setAmount(finalDamage);

      // armor is damaged less as a result of our math, so damage the armor based on the difference if there is one
      if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
        int damageMissed = getArmorDamage(originalDamage) - getArmorDamage(finalDamage);
        // TODO: is this check sufficient for whether the armor should be damaged? I partly wonder if I need to use reflection to call damageArmor
        if (damageMissed > 0 && entity instanceof Player) {
          for (EquipmentSlot slotType : ModifiableArmorMaterial.ARMOR_SLOTS) {
            // fire resistance moved off the item and onto the minecraft:fire_resistant component, which ItemStack#canBeHurtBy
            // already reads along with the NeoForge item hook, so it replaces the whole `is fire and item is fire resistant` test
            ItemStack armorStack = entity.getItemBySlot(slotType);
            // for our own armor, saves effort to damage directly with our utility
            IToolStackView tool = context.getToolInSlot(slotType);
            if (tool != null && armorStack.canBeHurtBy(source)) {
              // mark this as protection (any valid modifier really would do) so tanned can reduce it to not count it as separate damage
              ToolDamageUtil.damageAnimated(tool, damageMissed, entity, slotType, ARMOR_DAMAGE);
            } else {
              // if not our armor, damage using vanilla like logic. hurtAndBreak broadcasts the break itself now
              if (!armorStack.isEmpty() && armorStack.canBeHurtBy(source) && armorStack.getItem() instanceof ArmorItem) {
                armorStack.hurtAndBreak(damageMissed, entity, slotType);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Implements {@link ModifierHooks#MODIFY_DAMAGE}, which responds to the damage that is about to be taken.
   * @apiNote  This is {@link LivingDamageEvent.Pre}, which fires <b>before</b> absorption where 1.20's
   * {@code LivingDamageEvent} fired after it. A modifier reading the amount here therefore sees the damage that would
   * land with no absorption hearts, and one that reduces it reduces what absorption then has to soak. That is a real
   * gameplay change and it is the one T8a accepted: {@code Post} is immutable and cannot host a hook whose job is
   * returning a new amount. The event is also not cancellable any more, so zeroing the damage takes the place of the
   * 1.20 cancel; vanilla skips the health change and {@code Post} when the amount lands on zero.
   */
  @SubscribeEvent
  static void livingDamage(LivingDamageEvent.Pre event) {
    LivingEntity entity = event.getEntity();
    DamageSource source = event.getSource();

    // give modifiers a chance to respond to damage happening
    float amount = event.getNewDamage();
    EquipmentContext context = new EquipmentContext(entity);
    if (context.hasModifiableArmor()) {
      amount = ModifyDamageModifierHook.modifyDamageTaken(ModifierHooks.MODIFY_DAMAGE, context, source, amount, OnAttackedModifierHook.isDirectDamage(source));
      if (amount <= 0) {
        amount = 0;
      }
      event.setNewDamage(amount);
    }

    // for remaining code, ensure amount is not more than they will take
    amount = Math.min(amount, entity.getHealth());

    // apply post hit modifier effects. Done regardless of damage dealt - don't care if absorption took it all
    if (Config.COMMON.allowMonsterMeleeModifiers.get() && source.is(TinkerTags.DamageTypes.MODIFIER_WHITELIST)) {
      Entity attacker = event.getSource().getEntity();
      if (attacker != null && !attacker.getType().is(TinkerTags.EntityTypes.DAMAGE_MODIFIER_BLACKLIST) && attacker instanceof LivingEntity living) {
        ItemStack weapon = living.getMainHandItem();
        if (!weapon.isEmpty() && weapon.is(TinkerTags.Items.MELEE_WEAPON)) {
          // already know we are not a player
          ToolAttackContext meleeContext = ToolAttackContext.attacker(living, null).target(event.getEntity()).applyAttributes().build();
          IToolStackView tool = ToolStack.from(weapon);
          for (ModifierEntry entry : tool.getModifiers()) {
            entry.getHook(ModifierHooks.MONSTER_MELEE_HIT).onMonsterMeleeHit(tool, entry, meleeContext, amount);
          }
        }
      }
    }

    // when damaging ender dragons, may drop scales - must be player caused explosion, end crystals and TNT are examples
    if (amount > 0 && Config.COMMON.dropDragonScales.get() && entity.getType() == EntityType.ENDER_DRAGON && event.getNewDamage() > 0
        && source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() != null && source.getEntity().getType() == EntityType.PLAYER) {
      // drops 1 - 8 scales
      ModifierUtil.dropItem(entity, new ItemStack(TinkerModifiers.dragonScale, 1 + entity.level().random.nextInt(8)));
    }
  }

  /**
   * Called the modifier hook when an entity's position changes.
   * @apiNote  Pre rather than Post: the hook is documented as running before vanilla updates {@code lastPos}, and
   * {@code LivingEntity#aiStep} is what updates it, so only the pre-tick phase still has the previous position.
   */
  @SubscribeEvent
  static void livingWalk(EntityTickEvent.Pre event) {
    if (!(event.getEntity() instanceof LivingEntity living)) {
      return;
    }
    // this event runs before vanilla updates prevBlockPos
    BlockPos pos = living.blockPosition();
    if (!living.isSpectator() && !living.level().isClientSide() && living.isAlive() && !Objects.equals(living.lastPos, pos)) {
      ItemStack boots = living.getItemBySlot(EquipmentSlot.FEET);
      if (!boots.isEmpty() && boots.is(TinkerTags.Items.BOOTS)) {
        IToolStackView tool = ToolStack.from(boots);
        for (ModifierEntry entry : tool.getModifierList()) {
          entry.getHook(ModifierHooks.BOOT_WALK).onWalk(tool, entry, living, living.lastPos, pos);
        }
      }
    }
  }

  /** Handles visibility effects of mob disguise and projectile protection */
  @SubscribeEvent
  static void livingVisibility(LivingVisibilityEvent event) {
    // always nonnull in vanilla, not sure when it would be nullable but I dont see a need for either modifier
    Entity lookingEntity = event.getLookingEntity();
    if (lookingEntity == null) {
      return;
    }
    LivingEntity living = event.getEntity();
    // read only path, so do not hand an attachment to every entity a mob happens to look at
    TinkerDataCapability.Holder data = TinkerDataCapability.getExistingData(living);
    if (data != null) {
      // mob disguise
      Multiset<EntityType<?>> disguises = data.get(MobDisguiseModule.DISGUISES);
      if (disguises != null) {
        int count = disguises.count(lookingEntity.getType());
        if (count > 0) {
          // halves the range per level
          event.modifyVisibility(1 / Math.pow(2, count));
        }
      }
    }
  }

  /** Syncs arrow modifier list to the client */
  @SubscribeEvent
  static void projectileSync(PlayerEvent.StartTracking event) {
    Entity entity = event.getTarget();
    if (entity instanceof Projectile) {
      TinkerNetwork.getInstance().sendTo(new SyncProjectileModifiersPacket(entity), event.getEntity());
    }
  }

  /** Implements projectile hit hook */
  @SubscribeEvent
  static void projectileHit(ProjectileImpactEvent event) {
    Projectile projectile = event.getProjectile();
    ModifierNBT modifiers = EntityModifierCapability.getOrEmpty(projectile);
    if (!modifiers.isEmpty()) {
      ModDataNBT nbt = PersistentDataCapability.getData(projectile);
      HitResult hit = event.getRayTraceResult();
      HitResult.Type type = hit.getType();
      // extract a firing entity as that is a common need
      LivingEntity attacker = projectile.getOwner() instanceof LivingEntity l ? l : null;
      ModuleHook<ProjectileHitModifierHook> hook = projectile.level().isClientSide ? ModifierHooks.PROJECTILE_HIT_CLIENT : ModifierHooks.PROJECTILE_HIT;
      switch(type) {
        case ENTITY -> {
          EntityHitResult entityHit = (EntityHitResult)hit;
          // cancel all effects on endermen unless we have enderference, endermen like to teleport away
          // yes, hardcoded to enderference, if you need your own enderference for whatever reason, talk to us
          Entity entity = entityHit.getEntity();
          // extract a living target as that is the most common need
          LivingEntity target = ToolAttackUtil.getLivingEntity(entity);
          if (TinkerEffects.canHitWithProjectile(target) || nbt.getBoolean(TinkerEffects.ENDERFERENCE_KEY)) {

            // ensure we are not blocking, that means projectile shouldn't hit
            boolean notBlocked = true;
            if (target != null && target.isBlocking() && (!(projectile instanceof AbstractArrow arrow) || arrow.getPierceLevel() == 0)) {
              Vec3 direction = projectile.position().vectorTo(target.position()).normalize();
              direction = new Vec3(direction.x, 0.0D, direction.z);
              if (direction.dot(target.getViewVector(1.0F)) < 0.0D) {
                notBlocked = false;
              }
            }
            for (ModifierEntry entry : modifiers.getModifiers()) {
              if (entry.getHook(hook).onProjectileHitEntity(modifiers, nbt, entry, projectile, entityHit, attacker, target, notBlocked)) {
                // on forge, a cancelled entity would not be hit again by a piercing arrow. Neo has no per-entity skip,
                // so the projectile keeps flying and will be offered the same entity again next tick
                event.setCanceled(true);
                break;
              }
            }
          }
        }
        case BLOCK -> {
          BlockHitResult blockHit = (BlockHitResult)hit;
          for (ModifierEntry entry : modifiers.getModifiers()) {
            if (entry.getHook(hook).onProjectileHitsBlock(modifiers, nbt, entry, projectile, blockHit, attacker)) {
              event.setCanceled(true);
              break;
            }
          }
        }
      }
    }
  }
}
