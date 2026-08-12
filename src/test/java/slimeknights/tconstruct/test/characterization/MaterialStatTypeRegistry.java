package slimeknights.tconstruct.test.characterization;

import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.tools.stats.GripMaterialStats;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;
import slimeknights.tconstruct.tools.stats.PlatingMaterialStats;
import slimeknights.tconstruct.tools.stats.RepairStats;
import slimeknights.tconstruct.tools.stats.SkullStats;
import slimeknights.tconstruct.tools.stats.SlimeStats;
import slimeknights.tconstruct.tools.stats.StatlessMaterialStats;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-only mirror of every real {@link MaterialStatType} registered by {@code MaterialRegistry}'s constructor,
 * keyed by the full stat id string used as a JSON key under a material stats file's {@code "stats"} object
 * (e.g. {@code "tconstruct:head"}). Avoids needing to construct a full {@code MaterialRegistry}/event bus setup
 * just to reach the loadable each stat type already exposes directly as a public field.
 */
public final class MaterialStatTypeRegistry {
  private MaterialStatTypeRegistry() {}

  public static final Map<String,MaterialStatType<?>> TYPES = new HashMap<>();

  private static void add(MaterialStatType<?> type) {
    TYPES.put(type.getId().toString(), type);
  }

  static {
    add(HeadMaterialStats.TYPE);
    add(HandleMaterialStats.TYPE);
    add(LimbMaterialStats.TYPE);
    add(GripMaterialStats.TYPE);
    add(PlatingMaterialStats.HELMET);
    add(PlatingMaterialStats.CHESTPLATE);
    add(PlatingMaterialStats.LEGGINGS);
    add(PlatingMaterialStats.BOOTS);
    add(PlatingMaterialStats.SHIELD);
    add(SkullStats.TYPE);
    add(SlimeStats.TYPE);
    add(RepairStats.RIBCAGE);
    add(RepairStats.SHELL);
    add(RepairStats.LACES);
    add(StatlessMaterialStats.BINDING.getType());
    add(StatlessMaterialStats.BOWSTRING.getType());
    add(StatlessMaterialStats.MAILLE.getType());
    add(StatlessMaterialStats.SHIELD_CORE.getType());
    add(StatlessMaterialStats.REPAIR_KIT.getType());
    add(StatlessMaterialStats.CUIRASS.getType());
    add(StatlessMaterialStats.ARROW_HEAD.getType());
    add(StatlessMaterialStats.ARROW_SHAFT.getType());
    add(StatlessMaterialStats.FLETCHING.getType());
  }
}
