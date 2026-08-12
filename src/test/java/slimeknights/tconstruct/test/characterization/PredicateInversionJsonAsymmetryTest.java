package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.crafting.CraftingHelper;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
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
 * Regression test for a JSON round-trip instability in Mantle's generic predicate inversion wrapper
 * ({@code mantle:inverted}, {@link slimeknights.mantle.data.predicate.PredicateRegistry}), reproduced against the
 * real fixture that first surfaced it:
 * {@code characterization/recipes/extract_modifier____tools__modifiers__worktable__extract__ability.json}, whose
 * {@code modifier_predicate} is {@code {"type":"mantle:and","predicates":[{"type":"mantle:inverted",
 * "inverted_type":"tconstruct:tag",...}, {"type":"tconstruct:slot_type",...}]}}.
 * <p>
 * This test used to pin the bug with an inverted assertion: serializing the parsed predicate, re-parsing that
 * form and serializing again did not reliably reproduce the same JSON - the nested {@code mantle:inverted} wrapper
 * could be silently dropped or spuriously retained depending on what else had run earlier in the same JVM.
 * <p>
 * The cause was Mantle side and is fixed. A record loadable serializes its fields in declaration order into a
 * single shared document, and a field may read, complete or drop what the fields before it wrote; the dynamic ops
 * serialization path had been handing each field a fresh empty document instead, because a
 * {@code com.mojang.serialization.RecordBuilder} is write only. {@code mantle:inverted} nests through that
 * machinery, and so did the {@code tconstruct:} predicates it wraps, which is where the wrapper went missing.
 * Mantle now runs every record's builder through {@code OpsHelper.sharedBuilder}, so both paths see the same
 * document and the round trip is a fixed point. The assertion below is therefore the ordinary stability one, and
 * it is what proves the Mantle-side contract holds for a real nested predicate rather than only for Mantle's own
 * unit fixtures.
 * <p>
 * Note this asserts against whichever Mantle is on the classpath: it fails on a Mantle without that fix, which is
 * the point.
 */
class PredicateInversionJsonAsymmetryTest extends BaseMcTest {
  private static final String FIXTURE = "characterization/recipes/extract_modifier____tools__modifiers__worktable__extract__ability.json";

  @BeforeAll
  static void registerPredicateTypes() {
    ModuleTypeRegistrations.ensureRegistered();
    try {
      CraftingHelper.register(ResourceLocation.fromNamespaceAndPath("minecraft", "item"), net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE);
    } catch (Exception ignored) {
      // already registered - fine
    }
    try {
      CraftingHelper.register(ResourceLocation.fromNamespaceAndPath("forge", "difference"), DifferenceIngredient.Serializer.INSTANCE);
    } catch (Exception ignored) {
      // already registered - fine
    }
    RealItemStubs.ensureRegistered("characterization/recipes");
  }

  @Test
  void invertedPredicateNestedInAnd_survivesAParseSerializeCycle() throws Exception {
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

    // stability is only worth anything if the wrapper is still there to be stable about: dropping it on both
    // passes would negate the predicate and still compare equal
    assertThat(json1.toString()).as("serializing the parsed predicate dropped the mantle:inverted wrapper").contains("mantle:inverted");
    // re-parsing a previously serialized form and serializing again must be a stable fixed point, which is the
    // premise every other round trip assertion in this suite relies on
    assertThat(json2).as("re-parsing a serialized nested mantle:inverted predicate is not a stable round trip").isEqualTo(json1);
  }
}
