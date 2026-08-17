package slimeknights.tconstruct.test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import slimeknights.mantle.data.listener.MergingJsonDataLoader;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import static org.mockito.Mockito.mock;

/**
 * Extension of {@link JsonFileLoader} with extra functionality to mock multiple data packs.
 * <p>
 * The five members of {@link MergingJsonDataLoader} this drives - {@code gson}, {@code folder},
 * {@code builderConstructor}, {@code parse} and {@code finishLoad} - are {@code protected} and marked
 * {@link com.google.common.annotations.VisibleForTesting} by Mantle, which is an invitation to reach them from a
 * test. Until 1.21 this class took that invitation by living in {@code slimeknights.mantle.data.listener} and
 * borrowing package access. NeoForge runs mods as named modules in a real module layer, and a split package
 * between two of them is fatal before a single test class loads:
 * <pre>
 * java.lang.module.ResolutionException: Module mantle contains package slimeknights.mantle.data.listener,
 * module tconstruct exports package slimeknights.mantle.data.listener to mantle
 * </pre>
 * ModLauncher raises that while building the transforming class loader, so it takes down the whole suite rather
 * than the two tests that use this helper. 1.20's flat classpath had no modules and no such constraint.
 * <p>
 * The class therefore moves into TConstruct's own test package and reads the same five members reflectively.
 * That keeps the behaviour byte-identical - in particular this still lets a parse failure propagate, where
 * {@link MergingJsonDataLoader#onResourceManagerReload} logs and swallows it - which driving the public reload
 * path instead would have quietly changed.
 * @param <B>  Builder type
 */
public class MergingJsonFileLoader<B> extends JsonFileLoader {
  private final MergingJsonDataLoader<B> dataLoader;
  private final Function<ResourceLocation,B> builderConstructor;
  private final Method parse;
  private final Method finishLoad;

  public MergingJsonFileLoader(MergingJsonDataLoader<B> dataLoader) {
    super(gson(dataLoader), folder(dataLoader));
    this.dataLoader = dataLoader;
    this.builderConstructor = field(dataLoader, "builderConstructor");
    this.parse = method("parse", Object.class, ResourceLocation.class, JsonElement.class);
    this.finishLoad = method("finishLoad", Map.class, ResourceManager.class);
  }

  /**
   * Loads and parses the relevant files into the data loader
   * @param mergeFolder  If nonnull, subfolder to load as a "second datapack", for testing merging behavior. If null, skips the merging
   * @param files  List of files
   */
  public void loadAndParseFiles(@Nullable String mergeFolder, ResourceLocation... files) {
    Map<ResourceLocation,B> parsedMap = new HashMap<>();
    for (Entry<ResourceLocation, JsonElement> entry : loadFilesAsSplashlist(files).entrySet()) {
      ResourceLocation id = entry.getKey();
      parse(parsedMap.computeIfAbsent(id, builderConstructor), id, entry.getValue());
    }
    if (mergeFolder != null) {
      JsonFileLoader fakeSecondDataPack = new JsonFileLoader(gson(dataLoader), folder(dataLoader) + "/" + mergeFolder);
      for (Entry<ResourceLocation, JsonElement> entry : fakeSecondDataPack.loadFilesAsSplashlist(files).entrySet()) {
        ResourceLocation id = entry.getKey();
        parse(parsedMap.computeIfAbsent(id, builderConstructor), id, entry.getValue());
      }
    }
    invoke(finishLoad, parsedMap, mock(ResourceManager.class));
  }

  /* Reflective access to the @VisibleForTesting members; see the class javadoc for why it is not plain access */

  private void parse(B builder, ResourceLocation id, JsonElement element) {
    invoke(parse, builder, id, element);
  }

  private void invoke(Method method, Object... args) {
    try {
      method.invoke(dataLoader, args);
    } catch (ReflectiveOperationException e) {
      // unwrap so a JsonSyntaxException from parse still reaches the test as itself
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("Failed to invoke " + method.getName() + " on " + dataLoader.getClass(), e);
    }
  }

  private static Method method(String name, Class<?>... parameters) {
    try {
      Method method = MergingJsonDataLoader.class.getDeclaredMethod(name, parameters);
      method.setAccessible(true);
      return method;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("MergingJsonDataLoader#" + name + " is not where this expects it", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(MergingJsonDataLoader<?> dataLoader, String name) {
    try {
      Field field = MergingJsonDataLoader.class.getDeclaredField(name);
      field.setAccessible(true);
      return (T) field.get(dataLoader);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("MergingJsonDataLoader#" + name + " is not where this expects it", e);
    }
  }

  private static Gson gson(MergingJsonDataLoader<?> dataLoader) {
    return field(dataLoader, "gson");
  }

  private static String folder(MergingJsonDataLoader<?> dataLoader) {
    return field(dataLoader, "folder");
  }
}
