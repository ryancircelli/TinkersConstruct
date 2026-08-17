package slimeknights.tconstruct.library.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Static helpers for generic tag loading */
public class GenericTagUtil {
  private GenericTagUtil() {}

  /** Converts the results of the loader into a map from tag keys to lists */
  public static <T> Map<TagKey<T>,List<T>> mapLoaderResults(ResourceKey<? extends Registry<T>> registry, Map<ResourceLocation,Collection<T>> map) {
    return map.entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> TagKey.create(registry, entry.getKey()), entry -> List.copyOf(entry.getValue())));
  }

  /** Creates a map of reverse tags for the given map of tags */
  public static <T, I extends ResourceLocation> Map<I,Set<TagKey<T>>> reverseTags(Function<T,I> keyMapper, Map<TagKey<T>,? extends Collection<T>> tags) {
    Map<I,ImmutableSet.Builder<TagKey<T>>> reverseTags = new HashMap<>();
    Function<I,Builder<TagKey<T>>> makeSet = id -> ImmutableSet.builder();
    for (Entry<TagKey<T>,? extends Collection<T>> entry : tags.entrySet()) {
      TagKey<T> key = entry.getKey();
      for (T value : entry.getValue()) {
        reverseTags.computeIfAbsent(keyMapper.apply(value), makeSet).add(key);
      }
    }
    return reverseTags.entrySet().stream()
                      .collect(Collectors.toMap(Entry::getKey, entry->entry.getValue().build()));
  }

  /**
   * Decodes a map of tags from the packet.
   * <p>
   * A tag entry the value getter does not know is dropped rather than being an error. It is not a network problem: the
   * server writes tag contents and the values they name as two independent lists, so a value that was skipped by a
   * condition or resolved through a redirect is legitimately in one and not the other. Adding null to the list, which
   * is what this did in 1.20, turns that into a decoder exception and a disconnect.
   * @param valueGetter  Looks a value up by ID, returning null if it has none
   * @param <T>  Type the tags are keyed for
   * @param <V>  Type the contents are read as, which is {@code T} for a packet that resolves its values and the value's
   *             ID type for one that does not. The wire format is the same either way: a tag carries names.
   */
  public static <T, V> Map<TagKey<T>,List<V>> decodeTags(RegistryFriendlyByteBuf buf, ResourceKey<? extends Registry<T>> registry, Function<ResourceLocation,V> valueGetter) {
    ImmutableMap.Builder<TagKey<T>,List<V>> builder = ImmutableMap.builder();
    int mapSize = buf.readVarInt();
    for (int i = 0; i < mapSize; i++) {
      ResourceLocation tagId = buf.readResourceLocation();
      int tagSize = buf.readVarInt();
      ImmutableList.Builder<V> tagBuilder = ImmutableList.builder();
      for (int j = 0; j < tagSize; j++) {
        V value = valueGetter.apply(buf.readResourceLocation());
        if (value != null) {
          tagBuilder.add(value);
        }
      }
      builder.put(TagKey.create(registry, tagId), tagBuilder.build());
    }
    return builder.build();
  }

  /** Writes a map of tags to a packet. See {@link #decodeTags} for why the key type and the content type are separate. */
  public static <T, V> void encodeTags(RegistryFriendlyByteBuf buf, Function<V,ResourceLocation> keyGetter, Map<TagKey<T>,? extends Collection<V>> tags) {
    buf.writeVarInt(tags.size());
    for (Entry<TagKey<T>,? extends Collection<V>> entry : tags.entrySet()) {
      buf.writeResourceLocation(entry.getKey().location());
      Collection<V> values = entry.getValue();
      buf.writeVarInt(values.size());
      for (V value : values) {
        buf.writeResourceLocation(keyGetter.apply(value));
      }
    }
  }
}
