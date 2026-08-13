package slimeknights.tconstruct.common;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.Mantle;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity over the ~390 tag keys {@link TinkerTags} declares.
 * <p>
 * Nothing here asserts what is *in* a tag - that is datapack content and belongs to datagen. What it asserts is the
 * two properties the 1.21 port could silently break across a file this long: that every key names a real registry,
 * and that the common-tag namespace moved from {@code forge} to {@code c}. A tag key with the wrong namespace is a
 * tag that is simply always empty, which is invisible until a recipe quietly stops matching.
 */
class TinkerTagsTest extends BaseMcTest {
  /** Every {@code TagKey} constant declared on {@link TinkerTags} or one of its nested classes, with its field name */
  private static List<Field> allTagFields() {
    List<Field> fields = new ArrayList<>();
    collect(TinkerTags.class, fields);
    for (Class<?> nested : TinkerTags.class.getDeclaredClasses()) {
      collect(nested, fields);
    }
    return fields;
  }

  private static void collect(Class<?> clazz, List<Field> into) {
    for (Field field : clazz.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && TagKey.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        into.add(field);
      }
    }
  }

  private static TagKey<?> read(Field field) {
    try {
      return (TagKey<?>) field.get(null);
    } catch (IllegalAccessException e) {
      throw new AssertionError("could not read " + field, e);
    }
  }

  @Test
  void everyTagIsDeclared() {
    // guards the reflection above: if the nested-class walk stops finding tags, every other test here passes vacuously
    assertThat(allTagFields()).as("TinkerTags declares tag keys").hasSizeGreaterThan(300);
  }

  @Test
  void everyTagNamesAKnownRegistry() {
    for (Field field : allTagFields()) {
      TagKey<?> tag = read(field);
      ResourceKey<? extends Registry<?>> registry = tag.registry();
      assertThat(registry.location().getNamespace())
        .as("%s names registry %s", field.getName(), registry.location())
        .isEqualTo("minecraft");
    }
  }

  @Test
  void everyTagIsTinkersOrCommon() {
    // 1.21 renamed the common tag namespace from `forge` to `c`; Mantle.COMMON is the constant, and a stray `forge`
    // here would be a tag nothing ever fills
    for (Field field : allTagFields()) {
      TagKey<?> tag = read(field);
      assertThat(tag.location().getNamespace())
        .as("%s -> %s", field.getName(), tag.location())
        .isIn(TConstruct.MOD_ID, Mantle.COMMON, "minecraft");
    }
  }

  @Test
  void noTagKeyIsDeclaredTwice() {
    List<TagKey<?>> tags = allTagFields().stream().map(TinkerTagsTest::read).toList();
    assertThat(tags).doesNotHaveDuplicates();
  }

  @Test
  void hiddenFromRecipeViewersIsACommonTag() {
    assertThat(TinkerTags.HIDDEN_FROM_RECIPE_VIEWERS.getNamespace()).isEqualTo(Mantle.COMMON);
    assertThat(TinkerTags.HIDDEN_FROM_RECIPE_VIEWERS.getPath()).isEqualTo("hidden_from_recipe_viewers");
  }
}
