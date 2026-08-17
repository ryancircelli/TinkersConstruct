package slimeknights.tconstruct.library.json.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.util.RegistryHelper;

/** @deprecated use {@link slimeknights.mantle.recipe.condition.TagFilledCondition} */
@Deprecated(forRemoval = true)
@RequiredArgsConstructor
public class TagNotEmptyCondition<T> implements LootItemCondition, ICondition {
  /**
   * Codec used both as the {@link ICondition} codec and as the body of {@link #LOOT_TYPE}.
   * @apiNote  1.21 collapsed Forge's {@code IConditionSerializer} and the loot {@code Serializer} into one
   *           {@link MapCodec}: a {@link LootItemConditionType} is a record wrapping exactly one. The JSON is
   *           unchanged, so the shipped book index files still load.
   */
  public static final MapCodec<TagNotEmptyCondition<?>> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    ResourceLocation.CODEC.fieldOf("registry").forGetter(condition -> condition.tag.registry().location()),
    ResourceLocation.CODEC.fieldOf("tag").forGetter(condition -> condition.tag.location())
  ).apply(instance, TagNotEmptyCondition::of));
  /** Loot condition type, registered by {@code TinkerCommons} */
  public static final LootItemConditionType LOOT_TYPE = new LootItemConditionType(CODEC);

  private final TagKey<T> tag;

  /** Helper to deal with generics, as the codec cannot name the registry's type */
  private static <T> TagNotEmptyCondition<T> of(ResourceLocation registry, ResourceLocation tag) {
    ResourceKey<? extends Registry<T>> key = ResourceKey.createRegistryKey(registry);
    return new TagNotEmptyCondition<>(TagKey.create(key, tag));
  }

  @Override
  public MapCodec<? extends ICondition> codec() {
    return CODEC;
  }

  @Override
  public LootItemConditionType getType() {
    return LOOT_TYPE;
  }

  @Override
  public boolean test(IContext context) {
    return !context.getTag(tag).isEmpty();
  }

  @Override
  public boolean test(LootContext context) {
    Registry<T> registry = RegistryHelper.getRegistry(tag.registry());
    return registry != null && registry.getTagOrEmpty(tag).iterator().hasNext();
  }
}
