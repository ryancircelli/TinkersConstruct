package slimeknights.tconstruct.common.json;

import com.mojang.serialization.MapCodec;
import lombok.NoArgsConstructor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

@NoArgsConstructor
public class BlockOrEntityCondition implements LootItemCondition {
  public static final BlockOrEntityCondition INSTANCE = new BlockOrEntityCondition();
  /**
   * Codec for this condition, which has no fields at all.
   * @apiNote  1.21 replaced the loot {@code Serializer} pair with a single {@link MapCodec} held by the
   *           {@link LootItemConditionType}, so a condition with no state is a unit codec rather than a serializer
   *           whose two methods both did nothing. The JSON is unchanged.
   */
  public static final MapCodec<BlockOrEntityCondition> CODEC = MapCodec.unit(INSTANCE);
  /** Loot condition type, registered by {@code TinkerCommons} */
  public static final LootItemConditionType LOOT_TYPE = new LootItemConditionType(CODEC);

  @Override
  public LootItemConditionType getType() {
    return LOOT_TYPE;
  }

  @Override
  public boolean test(LootContext lootContext) {
    return lootContext.hasParam(LootContextParams.THIS_ENTITY) || lootContext.hasParam(LootContextParams.BLOCK_STATE);
  }
}
