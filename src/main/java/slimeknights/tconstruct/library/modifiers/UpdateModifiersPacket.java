package slimeknights.tconstruct.library.modifiers;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.utils.GenericTagUtil;
import slimeknights.tconstruct.library.utils.LazyDecode;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Packet to sync modifiers.
 * <p>
 * Its decode reads identifiers and undecoded blocks and resolves nothing, which is what lets it be handled in any
 * order against the other datapack sync packets - and, uniquely among them, against itself. A modifier's modules may
 * contain item stacks ({@code EdibleModule}'s representative item, {@code InfinityModule}'s ammo), constructing an
 * item stack runs {@code Item#verifyComponentsAfterLoad} on every stack in 1.21, and for a Tinkers tool that asks the
 * modifier registry. So a packet that decoded its modifiers eagerly would be asking for the modifiers it is itself
 * carrying, which no amount of packet ordering can fix. Each modifier therefore travels as a length prefixed block
 * that is decoded on first use, after {@link ModifierManager#updateModifiersFromServer} has installed the registry.
 * @see LazyDecode
 */
@Getter(AccessLevel.PACKAGE)
public class UpdateModifiersPacket implements IPacket.Threadsafe {
  /** Modifiers by ID, each an undecoded payload until something asks for it */
  private final Map<ModifierId,LazyDecode<ComposableModifier>> modifiers;
  /** Map of modifier redirect ID pairs, kept apart from the modifiers as the target may be a static modifier */
  private final Map<ModifierId,ModifierId> redirects;
  /** Map of all modifier tags, as IDs */
  private final Map<TagKey<Modifier>,List<ModifierId>> tags;
  /** Map of enchantment to modifier ID */
  private final Map<ResourceKey<Enchantment>,ModifierId> enchantmentMap;
  /** Map of enchantment tag to modifier ID */
  private final Map<TagKey<Enchantment>,ModifierId> enchantmentTagMap;

  /**
   * Creates a packet from the manager's state.
   * @param allModifiers  All dynamic modifiers, including redirects (entries whose value has a different ID)
   */
  UpdateModifiersPacket(Map<ModifierId,Supplier<Modifier>> allModifiers, Map<TagKey<Modifier>,List<Modifier>> tags,
                        Map<ResourceKey<Enchantment>,Modifier> enchantmentMap, Map<TagKey<Enchantment>,Modifier> enchantmentTagMap) {
    ImmutableMap.Builder<ModifierId,LazyDecode<ComposableModifier>> modifiers = ImmutableMap.builder();
    ImmutableMap.Builder<ModifierId,ModifierId> redirects = ImmutableMap.builder();
    for (Entry<ModifierId,Supplier<Modifier>> entry : allModifiers.entrySet()) {
      ModifierId id = entry.getKey();
      Modifier value = entry.getValue().get();
      ModifierId actual = value.getId();
      if (id.equals(actual)) {
        // we can't sync anything that is not composable
        if (value instanceof ComposableModifier composable) {
          modifiers.put(id, LazyDecode.of(codec(id), composable));
        } else {
          TConstruct.LOG.warn("Unable to sync modifier {} as its not ComposableModifier; got class {}", id, value.getClass().getName());
        }
      } else {
        redirects.put(id, actual);
      }
    }
    this.modifiers = modifiers.build();
    this.redirects = redirects.build();
    this.tags = mapValues(tags, list -> list.stream().map(Modifier::getId).toList());
    this.enchantmentMap = mapValues(enchantmentMap, Modifier::getId);
    this.enchantmentTagMap = mapValues(enchantmentTagMap, Modifier::getId);
  }

  public UpdateModifiersPacket(RegistryFriendlyByteBuf buffer) {
    // read in modifiers
    int size = buffer.readVarInt();
    ImmutableMap.Builder<ModifierId,LazyDecode<ComposableModifier>> modifiers = ImmutableMap.builder();
    for (int i = 0; i < size; i++) {
      ModifierId id = new ModifierId(buffer.readResourceLocation());
      modifiers.put(id, LazyDecode.read(buffer, codec(id)));
    }
    this.modifiers = modifiers.build();
    // read in redirects
    size = buffer.readVarInt();
    ImmutableMap.Builder<ModifierId,ModifierId> redirects = ImmutableMap.builder();
    for (int i = 0; i < size; i++) {
      redirects.put(new ModifierId(buffer.readResourceLocation()), new ModifierId(buffer.readResourceLocation()));
    }
    this.redirects = redirects.build();
    this.tags = GenericTagUtil.decodeTags(buffer, ModifierManager.REGISTRY_KEY, ModifierId::new);

    // read in enchantment to modifier mapping
    ImmutableMap.Builder<ResourceKey<Enchantment>,ModifierId> enchantmentMap = ImmutableMap.builder();
    size = buffer.readVarInt();
    for (int i = 0; i < size; i++) {
      enchantmentMap.put(ResourceKey.create(Registries.ENCHANTMENT, buffer.readResourceLocation()), new ModifierId(buffer.readResourceLocation()));
    }
    this.enchantmentMap = enchantmentMap.build();
    ImmutableMap.Builder<TagKey<Enchantment>,ModifierId> enchantmentTagMap = ImmutableMap.builder();
    size = buffer.readVarInt();
    for (int i = 0; i < size; i++) {
      enchantmentTagMap.put(TagKey.create(Registries.ENCHANTMENT, buffer.readResourceLocation()), new ModifierId(buffer.readResourceLocation()));
    }
    this.enchantmentTagMap = enchantmentTagMap.build();
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    // write modifiers
    buffer.writeVarInt(modifiers.size());
    for (Entry<ModifierId,LazyDecode<ComposableModifier>> entry : modifiers.entrySet()) {
      ModifierId id = entry.getKey();
      buffer.writeResourceLocation(id);
      try {
        // a modifier nothing asked for is copied through as the bytes it arrived as, so a proxy neither decodes it nor has to be able to
        entry.getValue().write(buffer);
      } catch (RuntimeException e) {
        // improve error logging
        TConstruct.LOG.error("Failed to encode modifier with ID {}", id, e);
        throw e;
      }
    }
    // write redirects
    buffer.writeVarInt(redirects.size());
    for (Entry<ModifierId,ModifierId> entry : redirects.entrySet()) {
      buffer.writeResourceLocation(entry.getKey());
      buffer.writeResourceLocation(entry.getValue());
    }
    GenericTagUtil.encodeTags(buffer, id -> id, this.tags);

    // enchantment mapping
    buffer.writeVarInt(enchantmentMap.size());
    for (Entry<ResourceKey<Enchantment>,ModifierId> entry : enchantmentMap.entrySet()) {
      buffer.writeResourceLocation(entry.getKey().location());
      buffer.writeResourceLocation(entry.getValue());
    }
    buffer.writeVarInt(enchantmentTagMap.size());
    for (Entry<TagKey<Enchantment>,ModifierId> entry : enchantmentTagMap.entrySet()) {
      buffer.writeResourceLocation(entry.getKey().location());
      buffer.writeResourceLocation(entry.getValue());
    }
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    ModifierManager.INSTANCE.updateModifiersFromServer(this);
  }


  /* Helpers */

  /** Copies a map, mapping the values */
  private static <K, F, T> Map<K,T> mapValues(Map<K,F> map, Function<F,T> mapper) {
    ImmutableMap.Builder<K,T> builder = ImmutableMap.builder();
    map.forEach((key, value) -> builder.put(key, mapper.apply(value)));
    return builder.build();
  }

  /**
   * Codec for a single modifier's payload.
   * The ID is not part of the payload, so it is baked into the codec: it is both the parsing context every module
   * loadable expects and the value {@link Modifier#setId(ModifierId)} needs on the way out.
   */
  private static StreamCodec<RegistryFriendlyByteBuf,ComposableModifier> codec(ModifierId id) {
    return StreamCodec.of(
      ComposableModifier.LOADER::encode,
      buffer -> withId(ComposableModifier.LOADER.decode(buffer, ModifierManager.contextBuilder(id).build()), id));
  }

  /**
   * Names a freshly decoded modifier.
   * @apiNote  {@link Modifier#setId(ModifierId)} is package private, and a package private member is not inherited
   *           into a subclass in another package, so it cannot be called through {@link ComposableModifier}.
   */
  private static <T extends Modifier> T withId(T modifier, ModifierId id) {
    Modifier asModifier = modifier;
    asModifier.setId(id);
    return modifier;
  }
}
