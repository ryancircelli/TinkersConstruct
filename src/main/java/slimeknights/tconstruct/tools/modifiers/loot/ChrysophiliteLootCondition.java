package slimeknights.tconstruct.tools.modifiers.loot;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modifiers.traits.skull.ChrysophiliteModifier;

import java.util.Set;

/** Condition to check if the enemy has the chrysophilite modifier */
public class ChrysophiliteLootCondition implements LootItemCondition {
  public static final ChrysophiliteLootCondition INSTANCE = new ChrysophiliteLootCondition();
  /**
   * Codec for this condition.
   * @apiNote  Replaces the {@code Serializer} pair, which 1.21 deleted along with the whole Gson loot serializer
   *           system: a {@link LootItemConditionType} is a record wrapping exactly one {@link MapCodec}, so a
   *           condition with no state is a unit codec rather than a serializer that reads nothing.
   */
  public static final MapCodec<ChrysophiliteLootCondition> CODEC = MapCodec.unit(INSTANCE);

  private ChrysophiliteLootCondition() {}

  @Override
  public boolean test(LootContext context) {
    return ChrysophiliteModifier.getTotalGold(context.getParamOrNull(LootContextParams.THIS_ENTITY)) > 0;
  }

  @Override
  public Set<LootContextParam<?>> getReferencedContextParams() {
    return ImmutableSet.of(LootContextParams.THIS_ENTITY);
  }

  @Override
  public LootItemConditionType getType() {
    return TinkerModifiers.chrysophiliteLootCondition.get();
  }
}
