package slimeknights.tconstruct.tools;

import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Single authoritative home for all {@link ItemAbility} constants used by Tinkers' Construct.
 * Re-exposes the vanilla actions this mod cares about alongside the custom actions it defines,
 * so every reference in the codebase resolves through here rather than mixing origins.
 */
public class TinkerToolActions {
  /* Vanilla actions, re-exposed from {@link ItemAbilities} for a single point of reference */
  public static final ItemAbility AXE_DIG = ItemAbilities.AXE_DIG;
  public static final ItemAbility AXE_SCRAPE = ItemAbilities.AXE_SCRAPE;
  public static final ItemAbility AXE_STRIP = ItemAbilities.AXE_STRIP;
  public static final ItemAbility AXE_WAX_OFF = ItemAbilities.AXE_WAX_OFF;
  public static final ItemAbility FISHING_ROD_CAST = ItemAbilities.FISHING_ROD_CAST;
  public static final ItemAbility HOE_DIG = ItemAbilities.HOE_DIG;
  public static final ItemAbility HOE_TILL = ItemAbilities.HOE_TILL;
  public static final ItemAbility PICKAXE_DIG = ItemAbilities.PICKAXE_DIG;
  public static final ItemAbility SHEARS_CARVE = ItemAbilities.SHEARS_CARVE;
  public static final ItemAbility SHEARS_DIG = ItemAbilities.SHEARS_DIG;
  public static final ItemAbility SHEARS_DISARM = ItemAbilities.SHEARS_DISARM;
  public static final ItemAbility SHEARS_HARVEST = ItemAbilities.SHEARS_HARVEST;
  public static final ItemAbility SHIELD_BLOCK = ItemAbilities.SHIELD_BLOCK;
  public static final ItemAbility SHOVEL_DIG = ItemAbilities.SHOVEL_DIG;
  public static final ItemAbility SHOVEL_FLATTEN = ItemAbilities.SHOVEL_FLATTEN;
  public static final ItemAbility SWORD_DIG = ItemAbilities.SWORD_DIG;

  /* Custom actions defined by the mod */
  /** Tinker tools that can disable shields on attack */
  public static final ItemAbility SHIELD_DISABLE = ItemAbility.get("shield_disable");
  /** Fishing rods that can act as a grappling hook */
  public static final ItemAbility GRAPPLE_HOOK = ItemAbility.get("grapple_hook");
  /** Makes the tool use the drill attack during its dash action */
  public static final ItemAbility DRILL_ATTACK = ItemAbility.get("drill_attack");
  /** Fishing rods that can collect items */
  public static final ItemAbility ITEM_HOOK = ItemAbility.get("item_hook");
  /** Generic action for the sake of people who want compat but do not want to request a specific action */
  public static final ItemAbility LIGHT_FIRE = ItemAbility.get("light_fire");
  /** Compat with mods adding custom campfires */
  public static final ItemAbility LIGHT_CAMPFIRE = ItemAbility.get("light_campfire");
}
