package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDataNBTTest extends BaseMcTest {
  private static final ResourceLocation testKey = ResourceLocation.parse("test");
  private static final ResourceLocation testKey2 = ResourceLocation.parse("test2");
  private static final ResourceLocation testKey3 = ResourceLocation.parse("test3");

  @Test
  void empty() {
    for (SlotType type : SlotType.getAllSlotTypes()) {
      assertThat(IModDataView.EMPTY.getSlots(type)).isEqualTo(0);
    }

    CompoundTag nbt = IModDataView.EMPTY.getCompound(testKey);
    nbt.putInt("test", 1);
    nbt = IModDataView.EMPTY.getCompound(testKey);
    assertThat(nbt.contains("test")).overridingErrorMessage("NBT not saved in empty").isFalse();
  }

  @Test
  void defaults() {
    ToolDataNBT nbt = new ToolDataNBT();

    for (SlotType type : SlotType.getAllSlotTypes()) {
      assertThat(IModDataView.EMPTY.getSlots(type)).isEqualTo(0);
    }
    assertThat(nbt.getData().isEmpty()).isTrue();
  }

  @Test
  void serialize() {
    ToolDataNBT modData = new ToolDataNBT();
    modData.setSlots(SlotType.UPGRADE, 2);
    modData.setSlots(SlotType.ABILITY, 3);
    modData.setSlots(SlotType.SOUL, 4);
    modData.putInt(testKey, 1);
    modData.put(testKey2, new CompoundTag());

    CompoundTag nbt = modData.getData();
    assertThat(nbt.getInt(SlotType.UPGRADE.getName())).isEqualTo(2);
    assertThat(nbt.getInt(SlotType.ABILITY.getName())).isEqualTo(3);
    assertThat(nbt.getInt(SlotType.SOUL.getName())).isEqualTo(4);
    assertThat(nbt.getInt(testKey.toString())).isEqualTo(1);
    assertThat(nbt.contains(testKey2.toString(), Tag.TAG_COMPOUND)).isTrue();
  }

  @Test
  void deserialize() {
    CompoundTag nbt = new CompoundTag();
    nbt.putInt(SlotType.UPGRADE.getName(), 4);
    nbt.putInt(SlotType.ABILITY.getName(), 5);
    nbt.putInt(SlotType.SOUL.getName(), 6);
    nbt.putString(testKey.toString(), "Not sure why you need strings");
    CompoundTag tag = new CompoundTag();
    tag.putInt("test", 1);
    nbt.put(testKey2.toString(), tag);

    ToolDataNBT modData = ToolDataNBT.readFromNBT(nbt);
    assertThat(modData.getSlots(SlotType.UPGRADE)).isEqualTo(4);
    assertThat(modData.getSlots(SlotType.ABILITY)).isEqualTo(5);
    assertThat(modData.getSlots(SlotType.SOUL)).isEqualTo(6);
    assertThat(modData.getString(testKey)).isEqualTo("Not sure why you need strings");

    tag = modData.getCompound(testKey2);
    assertThat(tag.isEmpty()).isFalse();
    assertThat(tag.contains("test", Tag.TAG_ANY_NUMERIC)).isTrue();
    assertThat(tag.getInt("test")).isEqualTo(1);
  }


  /* Value semantics: a read hands out a copy, only put stores */

  /** Data with a compound and a list of compounds under the two test keys */
  private static ToolDataNBT filledData() {
    ToolDataNBT modData = new ToolDataNBT();
    CompoundTag compound = new CompoundTag();
    compound.putInt("value", 1);
    modData.put(testKey, compound);
    ListTag list = new ListTag();
    list.add(compound.copy());
    modData.put(testKey2, list);
    return modData;
  }

  @Test
  void getCompound_returnsACopy() {
    ToolDataNBT modData = filledData();
    CompoundTag read = modData.getCompound(testKey);
    read.putInt("value", 2);
    read.putString("added", "nope");
    assertThat(modData.getCompound(testKey).getInt("value")).isEqualTo(1);
    assertThat(modData.getCompound(testKey).contains("added")).isFalse();
  }

  @Test
  void getCompound_putStoresTheEdit() {
    ToolDataNBT modData = filledData();
    CompoundTag read = modData.getCompound(testKey);
    read.putInt("value", 2);
    modData.put(testKey, read);
    assertThat(modData.getCompound(testKey).getInt("value")).isEqualTo(2);
    assertThat(modData.getData().getCompound(testKey.toString()).getInt("value")).isEqualTo(2);
  }

  @Test
  void getList_returnsACopy() {
    ToolDataNBT modData = filledData();
    ListTag read = modData.getList(testKey2, Tag.TAG_COMPOUND);
    read.add(new CompoundTag());
    read.getCompound(0).putInt("value", 2);
    assertThat(modData.getList(testKey2, Tag.TAG_COMPOUND)).hasSize(1);
    assertThat(modData.getList(testKey2, Tag.TAG_COMPOUND).getCompound(0).getInt("value")).isEqualTo(1);
  }

  @Test
  void getList_putStoresTheEdit() {
    ToolDataNBT modData = filledData();
    ListTag read = modData.getList(testKey2, Tag.TAG_COMPOUND);
    read.remove(0);
    modData.put(testKey2, read);
    assertThat(modData.getList(testKey2, Tag.TAG_COMPOUND)).isEmpty();
    assertThat(modData.getData().getList(testKey2.toString(), Tag.TAG_COMPOUND)).isEmpty();
  }

  @Test
  void get_returnsACopyOfTheRawTag() {
    ToolDataNBT modData = filledData();
    Tag read = modData.get(testKey);
    assertThat(read).isInstanceOf(CompoundTag.class);
    ((CompoundTag)read).putInt("value", 2);
    assertThat(modData.getCompound(testKey).getInt("value")).isEqualTo(1);
  }

  @Test
  void get_functionResultIsACopy() {
    ToolDataNBT modData = filledData();
    // the shape used by the inventory modules, a getter handing back the stored list
    ListTag read = modData.get(testKey2, (nbt, key) -> nbt.getList(key, Tag.TAG_COMPOUND));
    read.clear();
    assertThat(modData.getList(testKey2, Tag.TAG_COMPOUND)).hasSize(1);
  }

  @Test
  void get_primitivesAreUnaffected() {
    ToolDataNBT modData = new ToolDataNBT();
    modData.putInt(testKey, 3);
    modData.putString(testKey2, "value");
    modData.putBoolean(testKey3, true);
    assertThat(modData.getInt(testKey)).isEqualTo(3);
    assertThat(modData.getFloat(testKey)).isEqualTo(3f);
    assertThat(modData.getString(testKey2)).isEqualTo("value");
    assertThat(modData.getBoolean(testKey3)).isTrue();
  }

  @Test
  void get_absentKeyStillReadsAsEmpty() {
    ToolDataNBT modData = new ToolDataNBT();
    assertThat(modData.get(testKey)).isNull();
    assertThat(modData.getCompound(testKey).isEmpty()).isTrue();
    assertThat(modData.getList(testKey, Tag.TAG_COMPOUND).isEmpty()).isTrue();
    // editing what we read must not create the key either
    modData.getCompound(testKey).putInt("value", 1);
    assertThat(modData.contains(testKey)).isFalse();
  }

  @Test
  void get_emptyStoredListIsACopy() {
    ToolDataNBT modData = new ToolDataNBT();
    modData.put(testKey, new ListTag());
    ListTag read = modData.getList(testKey, Tag.TAG_COMPOUND);
    read.add(new CompoundTag());
    assertThat(modData.getList(testKey, Tag.TAG_COMPOUND)).isEmpty();
  }
}
