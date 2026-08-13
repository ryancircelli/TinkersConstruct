package slimeknights.tconstruct.test.characterization;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lists JSON fixture files copied under {@code src/test/resources/characterization/...} for parameterized tests.
 * <p>
 * The directory is resolved as a {@link Path} rather than a {@link java.io.File}. 1.20 ran the suite on a plain
 * classpath, where a test resource folder is always a {@code file:} URL and {@code new File(url.toURI())} works.
 * Since T13 the suite runs under ModLauncher, which serves mod and test resources out of its own union filesystem,
 * so the folder arrives as a {@code union:} URL and the {@code File} constructor throws
 * {@code IllegalArgumentException: URI scheme is not "file"} - taking every characterization class with it before a
 * single fixture is read. {@link Paths#get(URI)} dispatches on the scheme to whichever
 * {@link java.nio.file.spi.FileSystemProvider} is installed, so it handles {@code file:} and {@code union:}
 * identically, and {@code jar:} too once the filesystem is open.
 */
public final class FixtureFiles {
  private FixtureFiles() {}

  /**
   * Lists the names (not full paths) of every {@code .json} file directly inside the given classpath folder,
   * sorted for deterministic test ordering.
   * @param folder  Folder relative to the test resources root, e.g. {@code "characterization/recipes"}
   */
  public static List<String> listJsonFileNames(String folder) {
    Path dir = resolveDirectory(folder);
    if (dir == null || !Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(dir)) {
      return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list fixture folder " + folder, e);
    }
  }

  /** Same as {@link #listJsonFileNames(String)} but as a stream, convenient for {@code @MethodSource}. */
  public static Stream<String> streamJsonFileNames(String folder) {
    return listJsonFileNames(folder).stream();
  }

  /**
   * Reads one fixture file's raw text.
   * @param folder    Folder relative to the test resources root
   * @param fileName  File name as returned by {@link #listJsonFileNames(String)}
   * @return  File contents, or null if it could not be read
   */
  @Nullable
  public static String readFixture(String folder, String fileName) {
    Path dir = resolveDirectory(folder);
    if (dir == null) {
      return null;
    }
    try {
      return Files.readString(dir.resolve(fileName));
    } catch (IOException e) {
      return null;
    }
  }

  private static Path resolveDirectory(String folder) {
    URL url = FixtureFiles.class.getClassLoader().getResource(folder);
    if (url == null) {
      return null;
    }
    URI uri;
    try {
      uri = url.toURI();
    } catch (URISyntaxException e) {
      return null;
    }
    try {
      return Paths.get(uri);
    } catch (FileSystemNotFoundException e) {
      // a jar: URI has no filesystem until someone opens one; union: and file: never reach this
      try {
        FileSystems.newFileSystem(uri, Map.of());
        return Paths.get(uri);
      } catch (IOException io) {
        throw new UncheckedIOException("Failed to open a filesystem for fixture folder " + folder, io);
      }
    }
  }
}
