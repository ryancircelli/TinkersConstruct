package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the payload {@link PersistentDataCapability}'s attachment serializer moves, which is the half of the
 * capability-to-attachment conversion that a port can get wrong in silence. Its javadoc claims the bytes are "byte
 * for byte what 1.20 wrote - it is {@link ModDataNBT}'s own compound either way - so only the envelope moves"; these
 * are the assertions that claim has to pass.
 * <p>
 * The serializer's {@code read} is {@code ModDataNBT.readFromNBT(tag)} and its {@code write} is
 * {@code attachment.getCopy()} behind an {@code isEmpty()} guard, so every expression in it is covered below. What
 * is not covered is the guard itself and the {@link net.neoforged.neoforge.attachment.AttachmentType} it hangs off:
 * {@code PERSISTENT_DATA} is a {@code DeferredHolder} into the attachment type registry, which only a real game
 * launch populates, and NeoForge exposes no accessor for an attachment type's serializer in any case. The
 * {@code copyOnDeath}, {@code sync} and registration wiring is game behaviour for the same reason - a game test, not
 * a unit test.
 */
class PersistentDataCapabilityTest {
  private static final ResourceLocation INT_KEY = ResourceLocation.fromNamespaceAndPath("test", "int");
  private static final ResourceLocation BOOLEAN_KEY = ResourceLocation.fromNamespaceAndPath("test", "boolean");
  private static final ResourceLocation FLOAT_KEY = ResourceLocation.fromNamespaceAndPath("test", "float");
  private static final ResourceLocation STRING_KEY = ResourceLocation.fromNamespaceAndPath("test", "string");
  private static final ResourceLocation LIST_KEY = ResourceLocation.fromNamespaceAndPath("other", "list");

  /** Builds data holding one of each thing a modifier stores */
  private static ModDataNBT filled() {
    ModDataNBT data = new ModDataNBT();
    data.putInt(INT_KEY, 5);
    data.putBoolean(BOOLEAN_KEY, true);
    data.putFloat(FLOAT_KEY, 1.5f);
    data.putString(STRING_KEY, "value");
    ListTag list = new ListTag();
    list.add(StringTag.valueOf("entry"));
    data.put(LIST_KEY, list);
    return data;
  }

  @Test
  void write_usesThe120Shape() {
    // the payload is the modifier data compound itself: namespaced string keys straight on it, no envelope of its
    // own. The envelope that did move - ForgeCaps to neoforge:attachments - is NeoForge's to write, not ours
    CompoundTag written = filled().getCopy();
    assertThat(written.getAllKeys()).containsExactlyInAnyOrder("test:int", "test:boolean", "test:float", "test:string", "other:list");
    assertThat(written.getInt("test:int")).isEqualTo(5);
    assertThat(written.getBoolean("test:boolean")).isTrue();
    assertThat(written.getFloat("test:float")).isEqualTo(1.5f);
    assertThat(written.getString("test:string")).isEqualTo("value");
    assertThat(written.getList("other:list", Tag.TAG_STRING).getString(0)).isEqualTo("entry");
  }

  @Test
  void readWrite_roundTrips() {
    ModDataNBT original = filled();
    CompoundTag written = original.getCopy();
    ModDataNBT read = ModDataNBT.readFromNBT(written);

    assertThat(read.getInt(INT_KEY)).isEqualTo(5);
    assertThat(read.getBoolean(BOOLEAN_KEY)).isTrue();
    assertThat(read.getFloat(FLOAT_KEY)).isEqualTo(1.5f);
    assertThat(read.getString(STRING_KEY)).isEqualTo("value");
    assertThat(read.getList(LIST_KEY, Tag.TAG_STRING).getString(0)).isEqualTo("entry");
    assertThat(read).isEqualTo(original);
    assertThat(read.getCopy())
      .as("re-writing a loaded attachment should be idempotent")
      .isEqualTo(written);
  }

  @Test
  void write_handsOutACopy() {
    // the serializer's return value is owned by whoever saves it. If it were the live compound, an edit made
    // after the save was queued would reach disk, and a load would share its tag with the file it came from
    ModDataNBT data = filled();
    CompoundTag written = data.getCopy();
    written.putInt("test:int", 7);
    assertThat(data.getInt(INT_KEY)).isEqualTo(5);
    data.putInt(INT_KEY, 9);
    assertThat(written.getInt("test:int")).isEqualTo(7);
  }

  @Test
  void write_isEmptyForEmptyData() {
    // the condition the serializer returns null on, so an entity that never gained modifier data does not grow an
    // empty compound in every save. IAttachmentSerializer#write reads null as "nothing to store"
    assertThat(new ModDataNBT().getCopy()).isEqualTo(new CompoundTag());
    assertThat(new ModDataNBT().getCopy().isEmpty()).isTrue();
  }

  @Test
  void write_isNotEmptyOnceWritten() {
    // and the other side of the guard: a single key is enough to be worth saving, including one storing a false
    ModDataNBT data = new ModDataNBT();
    data.putBoolean(BOOLEAN_KEY, false);
    assertThat(data.getCopy().isEmpty()).isFalse();
  }

  @Test
  void read_ofEmptyGivesEmptyData() {
    // the load side of the same case: NeoForge only calls read when it stored something, but a hand written or
    // half migrated save can still hand over an empty compound
    ModDataNBT read = ModDataNBT.readFromNBT(new CompoundTag());
    assertThat(read.getCopy().isEmpty()).isTrue();
    assertThat(read.contains(INT_KEY)).isFalse();
    assertThat(read).isEqualTo(new ModDataNBT());
  }
}
