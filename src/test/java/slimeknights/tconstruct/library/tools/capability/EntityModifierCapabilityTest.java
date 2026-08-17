package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability.EntityModifiers;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability.Mutable;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the payload {@link EntityModifierCapability}'s attachment serializer moves, and the small amount of logic the
 * class kept once the {@code Predicate<Entity>} list went away.
 * <p>
 * The serializer's {@code read} is {@code ModifierNBT.readFromNBT(tag)} into a {@link Mutable} and its {@code write}
 * is {@code serializeToNBT()} behind an {@code isEmpty()} guard, so every expression in it is covered below. That
 * guard is the one thing in this class the port had to add rather than move: an attachment is created on demand, so
 * without it every arrow that was merely asked about its modifiers would gain one in the save.
 * <p>
 * Not covered, for the same reason as {@link PersistentDataCapabilityTest}: {@code MODIFIERS} is a
 * {@code DeferredHolder} into a registry only a real game launch populates, so neither the attachment type nor
 * {@link EntityModifierCapability#getOrEmpty} (which needs an entity to not create an attachment on) is reachable
 * from a unit test.
 */
class EntityModifierCapabilityTest extends BaseMcTest {
  @BeforeAll
  static void before() {
    ModifierFixture.init();
  }

  /** Two modifiers, the payload a fired projectile carries */
  private static ModifierNBT filled() {
    return ModifierNBT.builder()
                      .add(ModifierFixture.TEST_MODIFIER_1, 2)
                      .add(ModifierFixture.TEST_MODIFIER_2, 3)
                      .build();
  }

  @Test
  void write_usesThe120ListShape() {
    // a list of {modifier, level} compounds, which is what the 1.20 capability wrote into ForgeCaps
    ListTag written = filled().serializeToNBT();
    assertThat(written.size()).isEqualTo(2);
    CompoundTag first = written.getCompound(0);
    assertThat(first.getString(ModifierEntry.TAG_MODIFIER)).isEqualTo(ModifierFixture.TEST_1.toString());
    assertThat(first.getInt(ModifierEntry.TAG_LEVEL)).isEqualTo(2);
    CompoundTag second = written.getCompound(1);
    assertThat(second.getString(ModifierEntry.TAG_MODIFIER)).isEqualTo(ModifierFixture.TEST_2.toString());
    assertThat(second.getInt(ModifierEntry.TAG_LEVEL)).isEqualTo(3);
  }

  @Test
  void readWrite_roundTrips() {
    ListTag written = filled().serializeToNBT();

    // the serializer's read half, spelled the way it spells it
    Mutable read = new Mutable();
    read.setModifiers(ModifierNBT.readFromNBT(written));

    assertThat(read.getModifiers().getLevel(ModifierFixture.TEST_1)).isEqualTo(2);
    assertThat(read.getModifiers().getLevel(ModifierFixture.TEST_2)).isEqualTo(3);
    assertThat(read.getModifiers().serializeToNBT())
      .as("re-writing a loaded attachment should be idempotent")
      .isEqualTo(written);
  }

  @Test
  void write_declinesEmptyModifiers() {
    // the guard that keeps every arrow in flight out of the save. A holder that was only read from still has the
    // default value, and the default value must not be worth writing
    assertThat(new Mutable().getModifiers()).isEqualTo(ModifierNBT.EMPTY);
    assertThat(new Mutable().getModifiers().isEmpty()).isTrue();
  }

  @Test
  void write_doesNotDeclineRealModifiers() {
    assertThat(filled().isEmpty()).isFalse();
  }

  @Test
  void read_ofEmptyListGivesEmpty() {
    assertThat(ModifierNBT.readFromNBT(new ListTag())).isEqualTo(ModifierNBT.EMPTY);
  }

  @Test
  void addModifiers_replacesWhenEmpty() {
    // the empty branch hands the argument straight through rather than rebuilding it, so the identity is worth pinning
    Mutable holder = new Mutable();
    ModifierNBT added = filled();
    holder.addModifiers(added);
    assertThat(holder.getModifiers()).isSameAs(added);
  }

  @Test
  void addModifiers_mergesWhenNotEmpty() {
    Mutable holder = new Mutable();
    holder.setModifiers(ModifierNBT.EMPTY.withModifier(ModifierFixture.TEST_1, 2));
    holder.addModifiers(ModifierNBT.EMPTY.withModifier(ModifierFixture.TEST_1, 1).withModifier(ModifierFixture.TEST_2, 4));

    assertThat(holder.getModifiers().getLevel(ModifierFixture.TEST_1)).isEqualTo(3);
    assertThat(holder.getModifiers().getLevel(ModifierFixture.TEST_2)).isEqualTo(4);
  }

  @Test
  void empty_swallowsWrites() {
    // the sentinel getCapability hands back for a holder with nothing on it. It must stay empty, or a caller that
    // writes through it would think it had stored something
    EntityModifiers empty = EntityModifierCapability.EMPTY;
    empty.setModifiers(filled());
    empty.addModifiers(filled());
    assertThat(empty.getModifiers()).isEqualTo(ModifierNBT.EMPTY);
  }
}
