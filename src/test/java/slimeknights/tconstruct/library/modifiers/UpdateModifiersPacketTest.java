package slimeknights.tconstruct.library.modifiers;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.utils.LazyDecode;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Object -> encode -> decode round trip for the modifier sync packet, and the three tests pinning that its decode
 * resolves nothing.
 */
class UpdateModifiersPacketTest extends BaseMcTest {
  private static final ModifierId MODIFIER = new ModifierId("test", "modifier");
  private static final ModifierId REDIRECT = new ModifierId("test", "redirect");
  private static final TagKey<Modifier> TAG = ModifierManager.getTag(ResourceLocation.fromNamespaceAndPath("test", "tag"));
  private static final ResourceKey<Enchantment> ENCHANTMENT = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("sharpness"));
  private static final TagKey<Enchantment> ENCHANTMENT_TAG = TagKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("damage_exclusive"));

  private static RegistryFriendlyByteBuf buffer() {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  /**
   * Stands in for the part of a modifier's payload that reaches out of the packet while it is being read.
   * <p>
   * The real one is an item stack: a module may carry one ({@code EdibleModule}'s representative item,
   * {@code InfinityModule}'s ammo), constructing a stack runs {@code Item#verifyComponentsAfterLoad} in 1.21, and for a
   * Tinkers tool that rebuilds stats, which asks the modifier registry. This packet is what fills that registry in, so
   * unlike the other sync packets it cannot be fixed by handling the packets in a better order - the answer it needs is
   * the one it is carrying. What is pinned here is the property that makes the question never get asked: decoding the
   * packet must decode no modifier payload at all.
   * <p>
   * A level display is used rather than a module because modules live behind the frontier; the mechanism is the same,
   * as both are read by the modifier's own loadable.
   */
  record CountingDisplay(int value) implements ModifierLevelDisplay {
    /** Whether the registries this payload needs are loaded. Static, as the loadable has no other way in */
    static boolean ready = true;
    /** Number of times a payload was decoded, so a test can tell "did not throw" from "was never decoded" */
    static int decodes = 0;
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "counting_display");
    static final RecordLoadable<CountingDisplay> LOADER = RecordLoadable.create(
      IntLoadable.ANY_FULL.requiredField("value", CountingDisplay::value),
      value -> {
        decodes++;
        if (!ready) {
          throw new IllegalStateException("Attempted to load a modifier before dynamic modifiers are loaded");
        }
        return new CountingDisplay(value);
      });

    @Override
    public Component nameForLevel(Modifier modifier, int level) {
      return Component.empty();
    }

    @Override
    public RecordLoadable<CountingDisplay> getLoader() {
      return LOADER;
    }
  }

  /** Whether {@link CountingDisplay} was registered, as the registry rejects a duplicate and JUnit shares the JVM */
  private static boolean registered = false;

  @BeforeAll
  static void registerDisplay() {
    if (!registered) {
      ModifierLevelDisplay.LOADER.register(CountingDisplay.ID, CountingDisplay.LOADER);
      registered = true;
    }
  }

  @BeforeEach
  void resetDisplay() {
    CountingDisplay.ready = true;
    CountingDisplay.decodes = 0;
  }

  @AfterEach
  void releaseDisplay() {
    CountingDisplay.ready = true;
  }

  /** Builds a modifier with the given ID, carrying a payload the tests can watch being decoded */
  private static Modifier modifier(ModifierId id, int value) {
    Modifier modifier = ComposableModifier.builder().levelDisplay(new CountingDisplay(value)).build();
    modifier.setId(id);
    return modifier;
  }

  /** Builds the packet the manager would send, with one modifier, one redirect, one tag and both enchantment maps */
  private static UpdateModifiersPacket packet() {
    Modifier modifier = modifier(MODIFIER, 7);
    return new UpdateModifiersPacket(
      Map.of(MODIFIER, () -> modifier, REDIRECT, () -> modifier),
      Map.of(TAG, List.of(modifier)),
      Map.of(ENCHANTMENT, modifier),
      Map.of(ENCHANTMENT_TAG, modifier));
  }

  @Test
  void packetReadWrite() {
    RegistryFriendlyByteBuf buffer = buffer();
    packet().encode(buffer);
    UpdateModifiersPacket decoded = new UpdateModifiersPacket(buffer);
    assertThat(buffer.readableBytes()).isZero();

    // the modifier is carried by ID, its second name is a redirect to that ID
    assertThat(decoded.getModifiers()).containsOnlyKeys(MODIFIER);
    assertThat(decoded.getRedirects()).containsExactly(Map.entry(REDIRECT, MODIFIER));
    // tags and both enchantment maps name modifiers rather than holding them
    assertThat(decoded.getTags()).containsExactly(Map.entry(TAG, List.of(MODIFIER)));
    assertThat(decoded.getEnchantmentMap()).containsExactly(Map.entry(ENCHANTMENT, MODIFIER));
    assertThat(decoded.getEnchantmentTagMap()).containsExactly(Map.entry(ENCHANTMENT_TAG, MODIFIER));

    // the modifier itself is right once asked for
    Modifier value = decoded.getModifiers().get(MODIFIER).get();
    assertThat(value.getId()).isEqualTo(MODIFIER);
    assertThat(value).isInstanceOf(ComposableModifier.class);
  }

  /**
   * An enchantment is named by key, so the map survives a client with no enchantment registry.
   * That is the whole reason it is not keyed by the enchantment: this test could not exist if it were.
   */
  @Test
  void enchantmentsAreNamedNotResolved() {
    RegistryFriendlyByteBuf buffer = buffer();
    packet().encode(buffer);
    UpdateModifiersPacket decoded = new UpdateModifiersPacket(buffer);
    assertThat(decoded.getEnchantmentMap().keySet().iterator().next().location())
      .isEqualTo(ResourceLocation.withDefaultNamespace("sharpness"));
  }

  /** The premise the other two rest on: this stand-in really does throw when its payload is decoded too early */
  @Test
  void theStandInPayloadThrowsWhenNotReady() {
    RegistryFriendlyByteBuf buffer = buffer();
    ModifierLevelDisplay.LOADER.encode(buffer, new CountingDisplay(7));
    CountingDisplay.ready = false;
    // the loadable is free to wrap this in a DecoderException, so the assertion is on the trace rather than the type
    assertThatThrownBy(() -> ModifierLevelDisplay.LOADER.decode(buffer))
      .hasStackTraceContaining("dynamic modifiers");
  }

  /** Encodes on the server side, where everything is loaded, then puts the receiver back in the early state */
  private static RegistryFriendlyByteBuf encodeThenGoEarly() {
    RegistryFriendlyByteBuf buffer = buffer();
    packet().encode(buffer);
    CountingDisplay.ready = false;
    CountingDisplay.decodes = 0;
    return buffer;
  }

  @Test
  void decodesBeforeModifiersAreLoaded() {
    RegistryFriendlyByteBuf buffer = encodeThenGoEarly();

    // the whole packet decodes with the payloads deliberately not decodable
    UpdateModifiersPacket[] decoded = new UpdateModifiersPacket[1];
    assertThatCode(() -> decoded[0] = new UpdateModifiersPacket(buffer)).doesNotThrowAnyException();
    assertThat(buffer.readableBytes()).isZero();
    // and it resolved nothing: not one modifier payload was decoded
    assertThat(CountingDisplay.decodes).isZero();
    LazyDecode<ComposableModifier> lazy = decoded[0].getModifiers().get(MODIFIER);
    assertThat(lazy.isPending()).isTrue();

    // the ID half of the packet is available immediately, which is everything the manager installs first
    assertThat(decoded[0].getRedirects()).containsExactly(Map.entry(REDIRECT, MODIFIER));
    assertThat(decoded[0].getTags()).containsExactly(Map.entry(TAG, List.of(MODIFIER)));

    // once the registry is ready, the payload decodes, and to the right value
    CountingDisplay.ready = true;
    Modifier value = lazy.get();
    assertThat(CountingDisplay.decodes).isOne();
    assertThat(value.getId()).isEqualTo(MODIFIER);
    assertThat(lazy.isPending()).isFalse();
  }

  /** A packet decoded early and forwarded on without ever being read must not decode in order to forward */
  @Test
  void reEncodesWithoutDecoding() {
    RegistryFriendlyByteBuf buffer = encodeThenGoEarly();
    byte[] original = new byte[buffer.readableBytes()];
    buffer.getBytes(buffer.readerIndex(), original);

    UpdateModifiersPacket decoded = new UpdateModifiersPacket(buffer);
    RegistryFriendlyByteBuf rewritten = buffer();
    assertThatCode(() -> decoded.encode(rewritten)).doesNotThrowAnyException();

    byte[] copy = new byte[rewritten.readableBytes()];
    rewritten.getBytes(rewritten.readerIndex(), copy);
    assertThat(copy).isEqualTo(original);
    assertThat(CountingDisplay.decodes).isZero();
  }
}
