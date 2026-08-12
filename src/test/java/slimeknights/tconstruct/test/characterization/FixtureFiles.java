package slimeknights.tconstruct.test.characterization;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Lists JSON fixture files copied under {@code src/test/resources/characterization/...} for parameterized tests. */
public final class FixtureFiles {
  private FixtureFiles() {}

  /**
   * Lists the names (not full paths) of every {@code .json} file directly inside the given classpath folder,
   * sorted for deterministic test ordering.
   * @param folder  Folder relative to the test resources root, e.g. {@code "characterization/recipes"}
   */
  public static List<String> listJsonFileNames(String folder) {
    File dir = resolveDirectory(folder);
    if (dir == null) {
      return List.of();
    }
    File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
    if (files == null) {
      return List.of();
    }
    return Arrays.stream(files).map(File::getName).sorted().toList();
  }

  /** Same as {@link #listJsonFileNames(String)} but as a stream, convenient for {@code @MethodSource}. */
  public static Stream<String> streamJsonFileNames(String folder) {
    return listJsonFileNames(folder).stream();
  }

  private static File resolveDirectory(String folder) {
    URL url = FixtureFiles.class.getClassLoader().getResource(folder);
    if (url == null) {
      return null;
    }
    try {
      return new File(url.toURI());
    } catch (URISyntaxException e) {
      return new File(url.getPath());
    }
  }
}
