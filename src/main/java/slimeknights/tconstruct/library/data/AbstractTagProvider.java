package slimeknights.tconstruct.library.data;

import com.google.common.collect.Maps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import slimeknights.mantle.data.GenericDataProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic class for generating tags at any location even for non-registries.
 * TODO: make updates based on {@link net.minecraft.data.tags.TagsProvider} changes, if any.
 */
public abstract class AbstractTagProvider<T> extends GenericDataProvider {
  /** Mod ID for the tags */
  private final String modId;
  /** Predicate to validate non-optional values. If the contents only exist in datapacks, they should be defined as optional */
  private final Predicate<ResourceLocation> staticValuePredicate;
  /** Function to get a key from a value */
  private final Function<T,ResourceLocation> keyGetter;
  /**
   * Checks for tags in other datapacks.
   * @apiNote  Nullable as of 1.21: {@link net.minecraft.data.tags.TagsProvider} made its own helper nullable and the
   *           NeoForge datagen event hands out a disabled helper rather than none, so a provider must cope with it
   *           being absent instead of assuming validation is always available.
   */
  @Nullable
  protected final ExistingFileHelper existingFileHelper;
  /** Resource type for the existing file helper */
  private final ExistingFileHelper.IResourceType resourceType;

  protected final Map<ResourceLocation, TagBuilder> builders = Maps.newLinkedHashMap();

  protected AbstractTagProvider(PackOutput packOutput, String modId, String folder, Function<T,ResourceLocation> keyGetter, Predicate<ResourceLocation> staticValuePredicate, @Nullable ExistingFileHelper existingFileHelper) {
    super(packOutput, Target.DATA_PACK, folder);
    this.modId = modId;
    this.keyGetter = keyGetter;
    this.staticValuePredicate = staticValuePredicate;
    this.existingFileHelper = existingFileHelper;
    this.resourceType = new ExistingFileHelper.ResourceType(net.minecraft.server.packs.PackType.SERVER_DATA, ".json", folder);
  }

  /** Creates all tag instances */
  protected abstract void addTags();

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    this.builders.clear();
    this.addTags();
    return allOf(this.builders.entrySet().stream().map(entry -> {
      List<TagEntry> tagEntries = entry.getValue().build();
      List<TagEntry> invalidEntries = tagEntries.stream()
                                                .filter((value) -> !value.verifyIfPresent(staticValuePredicate, this.builders::containsKey))
                                                .filter(this::missing)
                                                .toList();
      ResourceLocation id = entry.getKey();
      if (!invalidEntries.isEmpty()) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(String.format("Couldn't define tag %s as it is missing following references: %s", id, invalidEntries.stream().map(Objects::toString).collect(Collectors.joining(",")))));
      } else {
        // 1.21 moved removals out of the Forge-only "remove" JSON key into a third TagFile component. Passing the
        // builder's remove entries through is what makes TagAppender#remove reach the file at all; the two argument
        // constructor still compiles but would silently drop every removal this provider was asked to write.
        return saveJson(cache, id, TagFile.CODEC, new TagFile(tagEntries, entry.getValue().isReplace(), entry.getValue().getRemoveEntries().toList()));
      }
    }));
  }

  /** Checks if a given reference exists in another data pack */
  private boolean missing(TagEntry reference) {
    if (reference.isRequired()) {
      // forge has a separate element resource type here to allow generating tags to non-static values. We don't currently handle non-static tag value validation but its worth considering
      return existingFileHelper == null || !existingFileHelper.exists(reference.getId(), resourceType);
    }
    return false;
  }


  /* Make builders */

  /** Prepares a tag builder */
  protected TagAppender<T> tag(TagKey<T> pTag) {
    return new TagAppender<>(modId, this.getOrCreateRawBuilder(pTag), keyGetter);
  }

  /** Raw method to make a builder */
  protected TagBuilder getOrCreateRawBuilder(TagKey<T> pTag) {
    return this.builders.computeIfAbsent(pTag.location(), location -> {
      if (existingFileHelper != null) {
        existingFileHelper.trackGenerated(location, resourceType);
      }
      return TagBuilder.create();
    });
  }

  /**
   * Vanillas tag appender does not let us easily replace the key getter, so replace it
   * @param modID  Unused as of 1.21. NeoForge dropped the source mod ID from every removal method as it was only ever
   *               used for a log message; the component is kept so subclasses and their constructors do not change.
   */
  @SuppressWarnings({"UnusedReturnValue", "unused"})  // API
  public record TagAppender<T>(String modID, TagBuilder internalBuilder, Function<T,ResourceLocation> keyGetter) {
    /** Adds a value to the tag */
    public TagAppender<T> add(T value) {
      this.internalBuilder.addElement(keyGetter.apply(value));
      return this;
    }

    /** Adds a list of values to the tag */
    @SafeVarargs
    public final TagAppender<T> add(T... values) {
      Stream.of(values).map(keyGetter).forEach(this.internalBuilder::addElement);
      return this;
    }

    /** Adds a resource location to the tag */
    public TagAppender<T> add(ResourceLocation... ids) {
      for (ResourceLocation id : ids) {
        this.internalBuilder.addElement(id);
      }
      return this;
    }

    /** Adds an optional ID to the tag */
    public TagAppender<T> addOptional(ResourceLocation... ids) {
      for (ResourceLocation id : ids) {
        this.internalBuilder.addOptionalElement(id);
      }
      return this;
    }

    /** Adds an tag to the tag */
    @SafeVarargs
    public final TagAppender<T> addTag(TagKey<T>... tags) {
      for (TagKey<T> tag : tags) {
        this.internalBuilder.addTag(tag.location());
      }
      return this;
    }

    /** Adds an optional tag to the tag */
    public TagAppender<T> addOptionalTag(ResourceLocation... tags) {
      for (ResourceLocation tag : tags) {
        this.internalBuilder.addOptionalTag(tag);
      }
      return this;
    }


    /* Forge methods */

    /** Sets the tag to replace */
    public TagAppender<T> replace() {
      return replace(true);
    }

    /** Sets the tag to replace */
    public TagAppender<T> replace(boolean value) {
      internalBuilder.replace(value);
      return this;
    }

    /**
     * Adds a registry entry to the tag json's remove list. Callable during datageneration.
     * @param entry The entry to remove
     * @return The builder for chaining
     */
    public TagAppender<T> remove(final T entry) {
      return remove(keyGetter.apply(entry));
    }

    /**
     * Adds multiple registry entries to the tag json's remove list. Callable during datageneration.
     * @param entries The entries to remove
     * @return The builder for chaining
     */
    @SafeVarargs
    public final TagAppender<T> remove(T first, T... entries) {
      this.remove(first);
      for (T entry : entries) {
        this.remove(entry);
      }
      return this;
    }

    /**
     * Adds a single element's ID to the tag json's remove list. Callable during datageneration.
     * @param location The ID of the element to remove
     * @return The builder for chaining
     */
    public TagAppender<T> remove(ResourceLocation location) {
      internalBuilder.removeElement(location);
      return this;
    }

    /**
     * Adds multiple elements' IDs to the tag json's remove list. Callable during datageneration.
     * @param locations The IDs of the elements to remove
     * @return The builder for chaining
     */
    public TagAppender<T> remove(ResourceLocation first, ResourceLocation... locations) {
      this.remove(first);
      for (ResourceLocation location : locations) {
        this.remove(location);
      }
      return this;
    }

    /**
     * Adds a tag to the tag json's remove list. Callable during datageneration.
     * @param tag The ID of the tag to remove
     * @return The builder for chaining
     */
    public TagAppender<T> remove(TagKey<T> tag) {
      internalBuilder.removeTag(tag.location());
      return this;
    }

    /**
     * Adds multiple tags to the tag json's remove list. Callable during datageneration.
     * @param tags The IDs of the tags to remove
     * @return The builder for chaining
     */
    @SafeVarargs
    public final TagAppender<T> remove(TagKey<T> first, TagKey<T>... tags) {
      this.remove(first);
      for (TagKey<T> tag : tags) {
        this.remove(tag);
      }
      return this;
    }
  }
}
