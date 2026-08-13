package slimeknights.tconstruct.library.client.book;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.data.content.PageContent;
import slimeknights.tconstruct.library.client.book.content.AmmoMaterialContent;
import slimeknights.tconstruct.library.client.book.content.ArmorMaterialContent;
import slimeknights.tconstruct.library.client.book.content.ContentMaterialSkull;
import slimeknights.tconstruct.library.client.book.content.ContentModifier;
import slimeknights.tconstruct.library.client.book.content.ContentTool;
import slimeknights.tconstruct.library.client.book.content.FluidEffectContent;
import slimeknights.tconstruct.library.client.book.content.MeleeHarvestMaterialContent;
import slimeknights.tconstruct.library.client.book.content.RangedMaterialContent;
import slimeknights.tconstruct.library.client.book.content.TooltipShowcaseContent;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the page type table {@link TinkerBook#initBook()} builds.
 * <p>
 * Nothing here parses a book: a page's content comes out of Gson against the class the id is registered to, so the id
 * to class pairing decides which content type a page in a resource pack actually becomes, and it is the one thing in
 * that method a compiler cannot check. It is also the thing that was wrong -
 * {@code tconstruct:ammo_material} was registered to {@link ArmorMaterialContent}, so every ammo material page in the
 * book was built as an armour page, reading plating stats off a material that has arrow stats.
 */
class TinkerBookTest extends BaseMcTest {
  @BeforeAll
  static void initBook() {
    TinkerBook.initBook();
  }

  /** Asserts a page id resolves to the class that answers with that same id */
  private static void assertPageType(ResourceLocation id, Class<? extends PageContent> expected) {
    assertThat(BookLoader.getPageType(id)).as("page type for %s", id).isEqualTo(expected);
  }

  @Test
  void pageTypes_registerToTheirOwnClass() {
    assertPageType(MeleeHarvestMaterialContent.ID, MeleeHarvestMaterialContent.class);
    assertPageType(RangedMaterialContent.ID, RangedMaterialContent.class);
    assertPageType(ArmorMaterialContent.ID, ArmorMaterialContent.class);
    assertPageType(AmmoMaterialContent.ID, AmmoMaterialContent.class);
    assertPageType(ContentTool.ID, ContentTool.class);
    assertPageType(ContentModifier.ID, ContentModifier.class);
    assertPageType(TooltipShowcaseContent.ID, TooltipShowcaseContent.class);
    assertPageType(FluidEffectContent.ID, FluidEffectContent.class);
  }

  @Test
  void materialContent_reportsItsOwnId() {
    // the other half of the pairing: a content type has to answer with the id it was registered under, since that is
    // what the section transformers write into a generated page
    MaterialVariantId material = MaterialVariantId.tryParse("tconstruct:test");
    assertThat(material).isNotNull();
    assertThat(new MeleeHarvestMaterialContent(material, false).getId()).isEqualTo(MeleeHarvestMaterialContent.ID);
    assertThat(new RangedMaterialContent(material, false).getId()).isEqualTo(RangedMaterialContent.ID);
    assertThat(new ArmorMaterialContent(material, false).getId()).isEqualTo(ArmorMaterialContent.ID);
    assertThat(new AmmoMaterialContent(material, false).getId()).isEqualTo(AmmoMaterialContent.ID);
    assertThat(new ContentMaterialSkull(material, false).getId()).isEqualTo(ContentMaterialSkull.ID);
  }

  @Test
  void allBooks_areRegistered() {
    // TinkerBook's static initializer is what registers the six books; if one of them stopped being registered the
    // failure would be a missing book at runtime rather than anything visible here
    assertThat(BookLoader.getBook(slimeknights.tconstruct.library.TinkerBookIDs.MATERIALS_BOOK_ID)).isSameAs(TinkerBook.MATERIALS_AND_YOU);
    assertThat(BookLoader.getBook(slimeknights.tconstruct.library.TinkerBookIDs.PUNY_SMELTING_ID)).isSameAs(TinkerBook.PUNY_SMELTING);
    assertThat(BookLoader.getBook(slimeknights.tconstruct.library.TinkerBookIDs.MIGHTY_SMELTING_ID)).isSameAs(TinkerBook.MIGHTY_SMELTING);
    assertThat(BookLoader.getBook(slimeknights.tconstruct.library.TinkerBookIDs.TINKERS_GADGETRY_ID)).isSameAs(TinkerBook.TINKERS_GADGETRY);
    assertThat(BookLoader.getBook(slimeknights.tconstruct.library.TinkerBookIDs.FANTASTIC_FOUNDRY_ID)).isSameAs(TinkerBook.FANTASTIC_FOUNDRY);
    assertThat(BookLoader.getBook(slimeknights.tconstruct.library.TinkerBookIDs.ENCYCLOPEDIA_ID)).isSameAs(TinkerBook.ENCYCLOPEDIA);
  }
}
