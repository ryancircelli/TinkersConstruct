package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.ComputableDataKey;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.Holder;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link Holder}, which is all of {@link TinkerDataCapability} a unit test can reach. The attachment itself is
 * a {@code DeferredHolder} into a registry only a real game launch populates, and the two accessors are one call
 * each into {@code IAttachmentHolder}, so what is left to test is the map the attachment stores - unchanged by the
 * port, and until now untested.
 * <p>
 * Two of its properties are load bearing for the port. The map is an {@code IdentityHashMap} keyed by key
 * <i>object</i>, which is why {@link TinkerDataKey} carries an id for debugging and not for equality; and
 * {@link Holder#add} removes a key that reaches zero rather than storing a zero, which is what keeps armour's
 * scratch space from accumulating dead entries as pieces are equipped and removed.
 */
class TinkerDataCapabilityTest {
  private static final TinkerDataKey<Float> FLOAT_KEY = TinkerDataKey.of(ResourceLocation.fromNamespaceAndPath("test", "float"));
  private static final TinkerDataKey<String> STRING_KEY = TinkerDataKey.of(ResourceLocation.fromNamespaceAndPath("test", "string"));

  @Test
  void get_missingGivesTheDefault() {
    Holder holder = new Holder();
    assertThat(holder.get(STRING_KEY)).isNull();
    assertThat(holder.get(STRING_KEY, "fallback")).isEqualTo("fallback");
    assertThat(holder.contains(STRING_KEY)).isFalse();
  }

  @Test
  void putGetRemove() {
    Holder holder = new Holder();
    holder.put(STRING_KEY, "value");
    assertThat(holder.contains(STRING_KEY)).isTrue();
    assertThat(holder.get(STRING_KEY)).isEqualTo("value");
    holder.remove(STRING_KEY);
    assertThat(holder.contains(STRING_KEY)).isFalse();
  }

  @Test
  void keysAreComparedByIdentity() {
    // two keys sharing an id are still two keys. Nothing may rely on looking one up by name
    TinkerDataKey<String> other = TinkerDataKey.of(STRING_KEY.getId());
    Holder holder = new Holder();
    holder.put(STRING_KEY, "value");
    assertThat(holder.contains(other)).isFalse();
    assertThat(holder.get(other)).isNull();
  }

  @Test
  void add_accumulates() {
    Holder holder = new Holder();
    holder.add(FLOAT_KEY, 1.5f);
    holder.add(FLOAT_KEY, 2f);
    assertThat(holder.get(FLOAT_KEY, 0f)).isEqualTo(3.5f);
  }

  @Test
  void add_removesTheKeyAtZero() {
    // an equipment change that undoes an earlier one leaves nothing behind, rather than a zero
    Holder holder = new Holder();
    holder.add(FLOAT_KEY, 2f);
    holder.add(FLOAT_KEY, -2f);
    assertThat(holder.contains(FLOAT_KEY)).isFalse();
    assertThat(holder.get(FLOAT_KEY, 0f)).isEqualTo(0f);
  }

  @Test
  void computeIfAbsent_createsOnceThenReuses() {
    ComputableDataKey<List<String>> key = ComputableDataKey.of(ResourceLocation.fromNamespaceAndPath("test", "list"), ArrayList::new);
    Holder holder = new Holder();
    List<String> first = holder.computeIfAbsent(key);
    first.add("entry");
    assertThat(holder.computeIfAbsent(key)).isSameAs(first);
    assertThat(holder.get(key)).containsExactly("entry");
  }
}
