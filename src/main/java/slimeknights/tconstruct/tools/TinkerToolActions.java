package slimeknights.tconstruct.tools;

import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * Single authoritative home for all {@link ToolAction} constants used by Tinkers' Construct.
 * Re-exposes the vanilla actions this mod cares about alongside the custom actions it defines,
 * so every reference in the codebase resolves through here rather than mixing origins.
 */
public class TinkerToolActions {
  /* Vanilla actions, re-exposed from {@link ToolActions} for a single point of reference */
  public static final ToolAction AXE_DIG = ToolActions.AXE_DIG;
  public static final ToolAction AXE_SCRAPE = ToolActions.AXE_SCRAPE;
  public static final ToolAction AXE_STRIP = ToolActions.AXE_STRIP;
  public static final ToolAction AXE_WAX_OFF = ToolActions.AXE_WAX_OFF;
  public static final ToolAction FISHING_ROD_CAST = ToolActions.FISHING_ROD_CAST;
  public static final ToolAction HOE_DIG = ToolActions.HOE_DIG;
  public static final ToolAction HOE_TILL = ToolActions.HOE_TILL;
  public static final ToolAction PICKAXE_DIG = ToolActions.PICKAXE_DIG;
  public static final ToolAction SHEARS_CARVE = ToolActions.SHEARS_CARVE;
  public static final ToolAction SHEARS_DIG = ToolActions.SHEARS_DIG;
  public static final ToolAction SHEARS_DISARM = ToolActions.SHEARS_DISARM;
  public static final ToolAction SHEARS_HARVEST = ToolActions.SHEARS_HARVEST;
  public static final ToolAction SHIELD_BLOCK = ToolActions.SHIELD_BLOCK;
  public static final ToolAction SHOVEL_DIG = ToolActions.SHOVEL_DIG;
  public static final ToolAction SHOVEL_FLATTEN = ToolActions.SHOVEL_FLATTEN;
  public static final ToolAction SWORD_DIG = ToolActions.SWORD_DIG;

  /* Custom actions defined by the mod */
  /** Tinker tools that can disable shields on attack */
  public static final ToolAction SHIELD_DISABLE = ToolAction.get("shield_disable");
  /** Fishing rods that can act as a grappling hook */
  public static final ToolAction GRAPPLE_HOOK = ToolAction.get("grapple_hook");
  /** Makes the tool use the drill attack during its dash action */
  public static final ToolAction DRILL_ATTACK = ToolAction.get("drill_attack");
  /** Fishing rods that can collect items */
  public static final ToolAction ITEM_HOOK = ToolAction.get("item_hook");
  /** Generic action for the sake of people who want compat but do not want to request a specific action */
  public static final ToolAction LIGHT_FIRE = ToolAction.get("light_fire");
  /** Compat with mods adding custom campfires */
  public static final ToolAction LIGHT_CAMPFIRE = ToolAction.get("light_campfire");
}
