package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.DifferenceIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tools.recipe.ExtractModifierRecipe;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of a real JSON round-trip instability in Mantle's generic predicate inversion wrapper
 * ({@code mantle:inverted}, {@link slimeknights.mantle.data.predicate.PredicateRegistry}), reproduced directly
 * against the real fixture that first surfaced it:
 * {@code characterization/recipes/extract_modifier____tools__modifiers__worktable__extract__ability.json}, whose
 * {@code modifier_predicate} is {@code {"type":"mantle:and","predicates":[{"type":"mantle:inverted",
 * "inverted_type":"tconstruct:tag",...}, {"type":"tconstruct:slot_type",...}]}}.
 * <p>
 * Serializing the parsed predicate, re-parsing that serialized form, and serializing again does NOT reliably
 * reproduce the same JSON: the nested {@code mantle:inverted} wrapper can be silently dropped (the predicate
 * becomes its own un-negated form) or spuriously retained/added depending on what else has run earlier in the
 * same JVM - this test observed both directions depending on test execution order while this suite was being
 * written. That in itself is a real and important characterization finding: the generic predicate registry
 * machinery appears to involve shared, order-dependent state (plausibly a mutable reverse type-lookup cache
 * keyed loosely enough to collide across unrelated predicate instances), not just a one-off normalization bug.
 * <p>
 * Not fixed here per this PR's characterization-only scope - the bug lives in Mantle's generic
 * {@code PredicateRegistry}/{@code GenericLoaderRegistry}, not in any TConstruct-specific predicate type.
 * {@code RecipeCharacterizationTest} skips its idempotency assertion for any fixture using {@code mantle:inverted}
 * and points here instead.
 */
class PredicateInversionJsonAsymmetryTest extends BaseMcTest {
  private static final String FIXTURE = "characterization/recipes/extract_modifier____tools__modifiers__worktable__extract__ability.json";

  @BeforeAll
  static void registerPredicateTypes() {
    ModuleTypeRegistrations.ensureRegistered();
    try {
      CraftingHelper.register(new ResourceLocation("minecraft", "item"), net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE);
    } catch (Exception ignored) {
      // already registered - fine
    }
    try {
      CraftingHelper.register(new ResourceLocation("forge", "difference"), DifferenceIngredient.Serializer.INSTANCE);
    } catch (Exception ignored) {
      // already registered - fine
    }
    RealItemStubs.ensureRegistered("characterization/recipes");
  }

  @Test
  void invertedPredicateNestedInAnd_doesNotReliablySurviveAParseSerializeCycle() throws Exception {
    JsonObject raw;
    try (InputStream stream = PredicateInversionJsonAsymmetryTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
      raw = JsonHelper.DEFAULT_GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    }
    ResourceLocation id = TConstruct.getResource("characterization_test/predicate_inversion");
    TypedMap context = TypedMapBuilder.builder().put(ContextKey.ID, id).build();

    ExtractModifierRecipe first = ExtractModifierRecipe.LOADER.deserialize(raw, context);
    JsonElement json1 = ExtractModifierRecipe.LOADER.serialize(first);

    ExtractModifierRecipe second = ExtractModifierRecipe.LOADER.deserialize(json1.getAsJsonObject(), context);
    JsonElement json2 = ExtractModifierRecipe.LOADER.serialize(second);

    // BUG (pinned, not fixed here): re-parsing a previously-serialized form and serializing again should be a
    // stable fixed point (that is the whole premise this suite's other round-trip assertions rely on) - here it
    // is not. See the class javadoc: which direction it changes in has been observed to depend on unrelated
    // earlier test execution in the same JVM, which is itself the noteworthy part of this finding.
    assertThat(json2).as("re-parsing a serialized nested mantle:inverted predicate is not a stable round trip").isNotEqualTo(json1);
  }
}
