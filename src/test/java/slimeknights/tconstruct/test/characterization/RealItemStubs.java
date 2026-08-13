package slimeknights.tconstruct.test.characterization;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.part.IToolPart;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * These headless unit tests never fire the mod loading lifecycle (see {@code BaseMcTest}), so TConstruct's
 * own items/blocks (registered via {@code DeferredRegister}, populated only by a live {@code RegisterEvent})
 * never actually exist in {@link BuiltInRegistries#ITEM}/{@link BuiltInRegistries#BLOCK} - only vanilla's do (via
 * {@code Bootstrap.bootStrap()}). Real recipe/tool-definition/station-layout JSON constantly references real
 * TConstruct items directly by id (not always via tag), and Mantle's loadables resolve those through the
 * registry eagerly at parse time, throwing {@code JsonSyntaxException} for anything missing.
 * <p>
 * Rather than mocking out item resolution everywhere, this scans the characterization fixture corpus for every
 * plausible {@code namespace:path} id referenced anywhere in the JSON and stubs in a minimal placeholder
 * {@link Item}/{@link Block} for any id not already present - mirroring what {@code MaterialItemFixture} already
 * does for a handful of fixed ids, just generically over the whole corpus. This is a workaround for a genuine
 * environment limitation (no live registry in unit tests), not a production behavior change.
 */
public final class RealItemStubs {
  private RealItemStubs() {}

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
  private static final Set<String> scannedFolders = new HashSet<>();
  private static boolean unfrozen = false;

  /**
   * Scans the given classpath folders (if not already scanned) and stubs any item/block/attribute id referenced
   * in them that isn't already registered. Safe and cheap to call repeatedly with different folders from
   * multiple test classes' {@code @BeforeAll} - only newly-seen folders are rescanned.
   */
  public static synchronized void ensureRegistered(String... classpathFolders) {
    if (!unfrozen) {
      unfrozen = true;
      // these headless tests never run a full game/datapack load, so BuiltInRegistries.ITEM/BLOCK/ATTRIBUTE are
      // frozen as soon as Bootstrap.bootStrap() runs (see BaseMcTest); unfreeze the same way MaterialItemFixture does
      unfreeze(BuiltInRegistries.ITEM);
      unfreeze(BuiltInRegistries.BLOCK);
      unfreeze(BuiltInRegistries.ATTRIBUTE);
      unfreeze(BuiltInRegistries.MOB_EFFECT);
      unfreeze(BuiltInRegistries.PARTICLE_TYPE);
    }
    Set<String> ids = new HashSet<>();
    for (String folder : classpathFolders) {
      if (scannedFolders.add(folder)) {
        collectIds(folder, ids);
      }
    }
    for (String id : ids) {
      ResourceLocation rl;
      try {
        rl = ResourceLocation.parse(id);
      } catch (Exception e) {
        continue;
      }
      stubItem(rl);
      stubBlock(rl);
      stubAttribute(rl);
      stubMobEffect(rl);
      stubParticleType(rl);
    }
  }

  @SuppressWarnings("unchecked")
  private static void unfreeze(net.minecraft.core.Registry<?> registry) {
    // yes, this is bad, but this is testing so we do bad things sometimes (mirrors MaterialItemFixture)
    ((MappedRegistry<Object>) registry).unfreeze();
  }

  private static void stubItem(ResourceLocation rl) {
    if (!BuiltInRegistries.ITEM.containsKey(rl)) {
      try {
        // Real fixtures reference item ids in many different typed roles (plain item, IModifiable tool,
        // IMaterialItem/IToolPart part item), and Mantle's field-typed loadables do an instanceof check at
        // parse time. A single stub class implementing all three marker interfaces satisfies every role at
        // once without needing to classify each id ahead of time.
        Registry.register(BuiltInRegistries.ITEM, rl, new StubToolPartItem());
      } catch (Exception ignored) {
        // not a valid item id (e.g. shape of a fluid/block/tag id) - fine, best effort
      }
    }
  }

  /** Minimal stand-in item implementing every marker interface real fixture item ids are commonly required to satisfy. */
  private static class StubToolPartItem extends Item implements IModifiable, IToolPart {
    private static final MaterialStatsId STUB_STAT_TYPE = new MaterialStatsId(TConstruct.MOD_ID, "characterization_stub");

    StubToolPartItem() {
      super(new Item.Properties());
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.EMPTY;
    }

    @Override
    public MaterialVariantId getMaterial(ItemStack stack) {
      return IMaterial.UNKNOWN_ID;
    }

    @Override
    public MaterialStatsId getStatType() {
      return STUB_STAT_TYPE;
    }
  }

  private static void stubAttribute(ResourceLocation rl) {
    if (!BuiltInRegistries.ATTRIBUTE.containsKey(rl)) {
      try {
        Registry.register(BuiltInRegistries.ATTRIBUTE, rl, new RangedAttribute(rl.toString(), 0, -1000, 1000));
      } catch (Exception ignored) {
        // fine, best effort
      }
    }
  }

  private static void stubMobEffect(ResourceLocation rl) {
    if (!BuiltInRegistries.MOB_EFFECT.containsKey(rl)) {
      try {
        Registry.register(BuiltInRegistries.MOB_EFFECT, rl, new MobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {});
      } catch (Exception ignored) {
        // fine, best effort
      }
    }
  }

  private static void stubParticleType(ResourceLocation rl) {
    if (!BuiltInRegistries.PARTICLE_TYPE.containsKey(rl)) {
      try {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, rl, new SimpleParticleType(false));
      } catch (Exception ignored) {
        // fine, best effort
      }
    }
  }

  private static void stubBlock(ResourceLocation rl) {
    if (!BuiltInRegistries.BLOCK.containsKey(rl)) {
      try {
        Registry.register(BuiltInRegistries.BLOCK, rl, new Block(BlockBehaviour.Properties.of()));
      } catch (Exception ignored) {
        // fine, best effort
      }
    }
  }

  private static void collectIds(String classpathFolder, Set<String> ids) {
    for (String fileName : FixtureFiles.listJsonFileNames(classpathFolder)) {
      java.io.File dir = resolveDirectory(classpathFolder);
      if (dir == null) {
        continue;
      }
      File file = new File(dir, fileName);
      try {
        String content = Files.readString(file.toPath());
        collectFromRawText(content, ids);
      } catch (IOException ignored) {
        // skip unreadable file
      }
    }
  }

  /** Simple regex scan over the raw JSON text for quoted resource-location-shaped strings; good enough as a best-effort stub source. */
  private static void collectFromRawText(String content, Set<String> ids) {
    Matcher m = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"").matcher(content);
    while (m.find()) {
      String candidate = m.group(1);
      if (ID_PATTERN.matcher(candidate).matches()) {
        ids.add(candidate);
      }
    }
  }

  private static File resolveDirectory(String folder) {
    java.net.URL url = RealItemStubs.class.getClassLoader().getResource(folder);
    if (url == null) {
      return null;
    }
    try {
      return new File(url.toURI());
    } catch (Exception e) {
      return new File(url.getPath());
    }
  }
}
