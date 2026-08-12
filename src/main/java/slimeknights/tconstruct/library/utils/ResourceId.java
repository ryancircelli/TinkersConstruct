package slimeknights.tconstruct.library.utils;

import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Helper for use with our extensions of resource location for some type safety in IDs.
 * Note we left {@link ResourceLocation#withPath(String)} and alike as returning {@link ResourceLocation} as there is not much use extending an ID.
 * <p>
 * 1.21 made {@link ResourceLocation} a final class with a private constructor, so this class only exists because our
 * access transformer reopens both. Extending is still the right shape: an ID is passed to a map, a tag key, a buffer
 * or a loadable as a plain resource location at close to two thousand call sites, and {@link ResourceLocation#equals}
 * compares by namespace and path rather than by class, so an ID still equals the location it names.
 * <p>
 * The other consequence of the reopening is that the constructor we now call is the <em>trusted</em> one - 1.20's
 * third {@code Dummy} parameter distinguished it and no longer exists - so validation is this class's job. The
 * subclass {@code tryParse}/{@code tryBuild} helpers therefore validate twice, once to decide whether to return null
 * and once on the way in. Both checks are short character scans over an ID that is about to be interned in a map, and
 * the alternative is reinventing the marker parameter vanilla just deleted.
 * @see IdParser
 */
public abstract class ResourceId extends ResourceLocation {
  public ResourceId(String namespace, String path) {
    super(validNamespace(namespace, path), validPath(namespace, path));
  }

  public ResourceId(ResourceLocation location) {
    // already validated when the location was built
    super(location.getNamespace(), location.getPath());
  }

  public ResourceId(String location) {
    this(IdParser.decompose(DEFAULT_NAMESPACE, location));
  }

  /** Exists so {@link #ResourceId(String)} can decompose before delegating; a constructor cannot run code first. */
  private ResourceId(String[] decomposed) {
    this(decomposed[0], decomposed[1]);
  }


  /* Validation */

  /** Checks the namespace half of an ID, returning it so it can be passed straight to the super constructor */
  private static String validNamespace(String namespace, String path) {
    if (!isValidNamespace(namespace)) {
      throw new ResourceLocationException("Non [a-z0-9_.-] character in namespace of ID: " + namespace + ':' + path);
    }
    return namespace;
  }

  /** Checks the path half of an ID, returning it so it can be passed straight to the super constructor */
  private static String validPath(String namespace, String path) {
    if (!isValidPath(path)) {
      throw new ResourceLocationException("Non [a-z0-9/._-] character in path of ID: " + namespace + ':' + path);
    }
    return path;
  }


  /* Helpers for static constructors */

  /**
   * Creates a new ID from the given string
   * @param string  String
   * @return  ID, or null if invalid
   */
  @Nullable
  protected static <T extends ResourceLocation> T tryParse(String string, BiFunction<String,String,T> constructor) {
    String[] parts = IdParser.decompose(DEFAULT_NAMESPACE, string);
    return tryBuild(parts[0], parts[1], constructor);
  }

  /**
   * Creates a new ID from the given namespace and path
   * @param namespace  Namespace
   * @param path       Path
   * @return  ID, or null if invalid
   */
  @Nullable
  protected static <T extends ResourceLocation> T tryBuild(String namespace, String path, BiFunction<String,String,T> constructor) {
    if (isValidNamespace(namespace) && isValidPath(path)) {
      return constructor.apply(namespace, path);
    }
    return null;
  }
}
