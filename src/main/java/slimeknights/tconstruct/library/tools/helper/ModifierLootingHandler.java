package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import slimeknights.tconstruct.common.TinkerDamageTypes;
import slimeknights.tconstruct.common.TinkerEffect;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.hook.combat.ArmorLootingModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.LootingModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.context.LootingContext;
import slimeknights.tconstruct.library.tools.nbt.DummyToolStack;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.shared.TinkerEffects;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Logic to handle the looting event for all main tinker tools.
 *
 * <h2>Where the event went</h2>
 * 1.20 delivered the number this class computes through {@code LootingLevelEvent}, fired by Forge wherever the game
 * wanted a looting level. That event does not exist in NeoForge 21.1 and nothing replaced it: looting is an
 * enchantment value effect in 1.21, read by {@code EnchantedCountIncreaseFunction} straight out of
 * {@code EnchantmentHelper#getEnchantmentLevel(Holder, LivingEntity)}, which walks the slots the {@code minecraft:looting}
 * enchantment declares and asks each stack for its level. Tinkers already answers that question through
 * {@code EnchantmentModifierHook#getEnchantmentLevel}, so a mainhand tool's looting reaches vanilla loot tables with no
 * help from here.
 * <p>
 * What is lost is the context the event carried. {@link #getLootingLevel} still computes exactly what it did before -
 * bleeding amplifier, projectile modifiers, held tool, then armor - but the only remaining caller shape,
 * {@code getEnchantmentLevel(ItemStack, Holder)}, is handed neither a target nor a damage source, so it cannot build a
 * {@link LootingContext}. Consequently {@link ArmorLootingModifierHook} and any {@link LootingModifierHook} on a
 * non-mainhand tool have no delivery path in 1.21 unless one is built for them. The computation is kept public and
 * intact so that whoever builds it does not have to reconstruct it.
 */
public class ModifierLootingHandler {
  /** If contained in the set, they should use the offhand for looting */
  private static final Map<UUID,EquipmentSlot> LOOTING_OFFHAND = new HashMap<>();
  private static boolean init = false;

  /** Initializies this listener */
  public static void init() {
    if (init) {
      return;
    }
    init = true;
    NeoForge.EVENT_BUS.addListener(ModifierLootingHandler::onLeaveServer);
  }

  /**
   * Sets the hand used for looting, so the tool is fetched from the proper context
   * @param entity    Player to set
   * @param slotType  Slot type
   */
  public static void setLootingSlot(LivingEntity entity, EquipmentSlot slotType) {
    if (slotType == EquipmentSlot.MAINHAND) {
      LOOTING_OFFHAND.remove(entity.getUUID());
    } else {
      LOOTING_OFFHAND.put(entity.getUUID(), slotType);
    }
  }

  /** Gets the slot to use for looting */
  public static EquipmentSlot getLootingSlot(@Nullable LivingEntity entity) {
    return entity != null ? LOOTING_OFFHAND.getOrDefault(entity.getUUID(), EquipmentSlot.MAINHAND) : EquipmentSlot.MAINHAND;
  }

  /**
   * Computes the looting level for a kill, applying every Tinkers looting modifier that has a say in it.
   * @param target        Entity that was killed
   * @param damageSource  Damage source that killed it
   * @param level         Looting level from before Tinkers has a say, typically from enchantments
   * @return  Final looting level, never negative
   */
  public static int getLootingLevel(LivingEntity target, DamageSource damageSource, int level) {
    // bleeding kills use the level of the effect for looting
    if (damageSource.is(TinkerDamageTypes.BLEEDING)) {
      return Math.max(0, TinkerEffect.getAmplifier(target, TinkerEffects.bleeding.get()));
    }

    // otherwise, use the proper tool. Must be an attacker with our tool
    Entity source = damageSource.getEntity();
    if (!(source instanceof LivingEntity holder)) {
      return level;
    }
    Entity direct = damageSource.getDirectEntity();

    // determine who is in charge of the looting
    LootingContext context;
    IToolStackView tool = null;
    if (direct instanceof Projectile) {
      // need to build a context from the relevant capabilities to use the modifier
      ModifierNBT modifiers = EntityModifierCapability.getOrEmpty(direct);
      context = new LootingContext(holder, target, damageSource, null);
      // no modifiers means its not a projectile we fired, so just defer to dumb vanilla behavior of whatever looting
      // since we don't set the enchantment on our tools, our looting modifiers won't set anything here anyways
      if (!modifiers.isEmpty()) {
        ModDataNBT persistentData = PersistentDataCapability.getData(direct);
        level = LootingModifierHook.getLooting(new DummyToolStack(Items.AIR, modifiers, persistentData), context, 0);
      }
    } else {
      // not an arrow? means the held tool is to blame
      EquipmentSlot slotType = getLootingSlot(holder);
      context = new LootingContext(holder, target, damageSource, slotType);
      ItemStack held = holder.getItemBySlot(slotType);

      // if its modifiable, let it increase the level
      if (held.is(TinkerTags.Items.MODIFIABLE)) {
        tool = ToolStack.from(held);
        level = LootingModifierHook.getLooting(tool, context, level);
      } else if (slotType != EquipmentSlot.MAINHAND) {
        // if it's not modifiable, yet we have a lot marked to blame for looting, ignore the passed value
        level = 0;
      }
    }
    // boost looting with armor regardless, hopefully you did not switch your pants mid arrow firing
    level = ArmorLootingModifierHook.getLooting(tool, context, level);
    // we allow the hook to return negatives to cancel out looting, so ensure its at least 0
    return Math.max(level, 0);
  }

  /** Called when a player leaves the server to clear the face */
  private static void onLeaveServer(PlayerLoggedOutEvent event) {
    LOOTING_OFFHAND.remove(event.getEntity().getUUID());
  }
}
