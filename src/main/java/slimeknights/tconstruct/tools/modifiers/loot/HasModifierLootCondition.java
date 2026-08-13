package slimeknights.tconstruct.tools.modifiers.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.tools.TinkerModifiers;

/** Condition to check if a held tool has the given modifier */
public record HasModifierLootCondition(ModifierId modifier) implements LootItemCondition {
  /**
   * Codec for this condition.
   * @apiNote  Replaces the {@code Serializer} pair, which 1.21 deleted along with the whole Gson loot serializer
   *           system: a {@link LootItemConditionType} is a record wrapping exactly one {@link MapCodec}. The JSON is
   *           unchanged.
   */
  public static final MapCodec<HasModifierLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    ModifierId.PARSER.codec().fieldOf("modifier").forGetter(HasModifierLootCondition::modifier)
  ).apply(instance, HasModifierLootCondition::new));

  @Override
  public LootItemConditionType getType() {
    return TinkerModifiers.hasModifierLootCondition.get();
  }

  @Override
  public boolean test(LootContext context) {
    ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
    return tool != null && tool.is(TinkerTags.Items.MODIFIABLE) && ModifierUtil.getModifierLevel(tool, modifier) > 0;
  }
}
