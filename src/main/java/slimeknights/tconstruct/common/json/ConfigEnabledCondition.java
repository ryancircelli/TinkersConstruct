package slimeknights.tconstruct.common.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConfigEnabledCondition implements ICondition, LootItemCondition {
  public static final ResourceLocation ID = TConstruct.getResource("config");
  /* Map of config names to condition cache */
  private static final Map<String,ConfigEnabledCondition> PROPS = new HashMap<>();

  /**
   * Codec serving as both the {@link ICondition} codec and the body of {@link #LOOT_TYPE}.
   * <p>
   * 1.20 needed two serializers for this class - Forge's {@code IConditionSerializer} and the loot {@code Serializer} -
   * whose four methods were two implementations written twice. 1.21 asks both registries for a {@link MapCodec}, so
   * there is one. The JSON is the same single {@code prop} string it always was.
   * <p>
   * An unknown property name is a {@link DataResult} error rather than the {@code JsonSyntaxException} 1.20 threw:
   * the codec is used by the datapack condition reader, where a thrown exception fails the whole pack load rather
   * than the one file that named a property this version does not have.
   */
  public static final MapCodec<ConfigEnabledCondition> CODEC = Codec.STRING.comapFlatMap(
    prop -> {
      ConfigEnabledCondition config = PROPS.get(prop.toLowerCase(Locale.ROOT));
      return config == null ? DataResult.error(() -> "Invalid config property name '" + prop + "'") : DataResult.success(config);
    },
    condition -> condition.configName).fieldOf("prop");
  /** Loot condition type, registered by {@code TinkerCommons} */
  public static final LootItemConditionType LOOT_TYPE = new LootItemConditionType(CODEC);

  private final String configName;
  private final BooleanSupplier supplier;

  @Override
  public MapCodec<? extends ICondition> codec() {
    return CODEC;
  }

  @Override
  public boolean test(IContext context) {
    return supplier.getAsBoolean();
  }

  @Override
  public boolean test(LootContext lootContext) {
    return supplier.getAsBoolean();
  }

  @Override
  public LootItemConditionType getType() {
    return LOOT_TYPE;
  }

  /**
   * Adds a condition
   * @param prop     Property name
   * @param supplier Boolean supplier
   * @return Added condition
   */
  private static ConfigEnabledCondition add(String prop, BooleanSupplier supplier) {
    ConfigEnabledCondition conf = new ConfigEnabledCondition(prop, supplier);
    PROPS.put(prop.toLowerCase(Locale.ROOT), conf);
    return conf;
  }

  /**
   * Adds a condition
   * @param prop     Property name
   * @param supplier Config value
   * @return Added condition
   */
  private static ConfigEnabledCondition add(String prop, BooleanValue supplier) {
    return add(prop, supplier::get);
  }

  @Override
  public String toString() {
    return "config_setting_enabled(\"" + this.configName + "\")";
  }

  /* Properties */
  public static final ConfigEnabledCondition SPAWN_WITH_BOOK = add("spawn_with_book", Config.COMMON.shouldSpawnWithTinkersBook);
  public static final ConfigEnabledCondition GRAVEL_TO_FLINT = add("gravel_to_flint", Config.COMMON.addGravelToFlintRecipe);
  public static final ConfigEnabledCondition CHEAPER_NETHERITE_ALLOY = add("cheaper_netherite_alloy", Config.COMMON.cheaperNetheriteAlloy);
  public static final ConfigEnabledCondition WITHER_BONE_DROP = add("wither_bone_drop", Config.COMMON.witherBoneDrop);
  /** @deprecated use datapacks to remove specific recipes */
  @Deprecated(forRemoval = true)
  public static final ConfigEnabledCondition WITHER_BONE_CONVERSION = add("wither_bone_conversion", () -> false);
  public static final ConfigEnabledCondition SLIME_RECIPE_FIX = add("slime_recipe_fix", Config.COMMON.glassRecipeFix);
  public static final ConfigEnabledCondition GLASS_RECIPE_FIX = add("glass_recipe_fix", Config.COMMON.glassRecipeFix);
  public static final ConfigEnabledCondition ALLOW_INGOTLESS_ALLOYS = add("allow_ingotless_alloys", Config.COMMON.allowIngotlessAlloys);
  public static final ConfigEnabledCondition FORCE_INTEGRATION_MATERIALS = add("force_integration_materials", Config.COMMON.forceIntegrationMaterials);
  public static final ConfigEnabledCondition SLIMY_LOOT_CHESTS = add("slimy_loot_chests", Config.COMMON.slimyLootChests);
  public static final ConfigEnabledCondition SYNC_KNOCKBACK_RESISTANCE = add("sync_knockback_resistance", Config.COMMON.syncKnockbackResistance);
}
