package slimeknights.tconstruct.library.modifiers;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ICondition.IContext;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.json.JsonRedirect;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.utils.GenericTagUtil;
import slimeknights.tconstruct.library.utils.JsonUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Modifier registry and JSON loader */
@Log4j2
public class ModifierManager extends SimpleJsonResourceReloadListener {
  /** Location of dynamic modifiers */
  public static final String FOLDER = "tinkering/modifiers";
  /** Location of modifier tags */
  public static final String TAG_FOLDER = "tinkering/tags/modifiers";

  public static final ResourceLocation ENCHANTMENT_MAP = TConstruct.getResource("tinkering/enchantments_to_modifiers.json");
  /** Registry key to make tag keys */
  public static final ResourceKey<? extends Registry<Modifier>> REGISTRY_KEY = ResourceKey.createRegistryKey(TConstruct.getResource("modifiers"));

  /** GSON instance for loading dynamic modifiers */
  public static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();

  /** @deprecated use {@link ModifierId#EMPTY} */
  @Deprecated
  public static final ModifierId EMPTY = ModifierId.EMPTY;

  /** Singleton instance of the modifier manager */
  public static final ModifierManager INSTANCE = new ModifierManager();

  /** Default modifier to use when a modifier is not found */
  @Getter
  private final Modifier defaultValue;

  /** If true, static modifiers have been registered, so static modifiers can safely be fetched */
  @Getter
  private boolean modifiersRegistered = false;
  /** All modifiers registered directly with the manager */
  @VisibleForTesting
  final Map<ModifierId,Modifier> staticModifiers = new HashMap<>();
  /** Set all modifier types that are expected to load in datapacks */
  private final Set<ModifierId> expectedDynamicModifiers = new HashSet<>();

  /**
   * Modifiers loaded from JSON.
   * <p>
   * Values are suppliers because a modifier received from the server is a block of undecoded bytes until something
   * asks for it; see {@link UpdateModifiersPacket}. Everything this manager loads itself is already in hand and is
   * wrapped by {@link #constant(Modifier)}.
   */
  private Map<ModifierId,Supplier<Modifier>> dynamicModifiers = Collections.emptyMap();
  /** Modifier tags loaded from JSON */
  private Map<TagKey<Modifier>,List<Modifier>> tags = Collections.emptyMap();
  /** Map from modifier to tags on the modifier */
  private Map<ModifierId,Set<TagKey<Modifier>>> reverseTags = Collections.emptyMap();

  /** List of tag to modifier mappings to try */
  private Map<TagKey<Enchantment>,Modifier> enchantmentTagMap = Collections.emptyMap();
  /**
   * Mapping from enchantment to modifiers, for conversions.
   * <p>
   * Keyed by {@link ResourceKey} rather than by the enchantment: enchantments are a datapack registry in 1.21, so
   * there is no enchantment instance to use as a key until a world is loaded, and the instance is replaced on every
   * reload. A key is the stable name of an entry and {@link net.minecraft.core.Holder#is(ResourceKey)} answers a
   * lookup without touching the registry at all, which is what the query side wants.
   */
  private Map<ResourceKey<Enchantment>,Modifier> enchantmentMap = Collections.emptyMap();

  /** If true, dynamic modifiers have been loaded from datapacks, so its safe to fetch dynamic modifiers */
  @Getter
  boolean dynamicModifiersLoaded = false;
  private IContext conditionContext = IContext.EMPTY;
  /**
   * Registries of the reload in progress, used to validate the enchantment map.
   * Null outside a reload, notably in tests, where the map is taken at its word.
   */
  @Nullable
  private RegistryAccess registryAccess;

  private ModifierManager() {
    super(GSON, FOLDER);
    // create the empty modifier
    defaultValue = new EmptyModifier();
    defaultValue.setId(EMPTY);
    staticModifiers.put(EMPTY, defaultValue);
  }

  /**
   * For internal use only
   * @param modBus  Mod event bus, which 1.21 hands to the mod constructor rather than exposing through a static
   *                context: {@code FMLJavaModLoadingContext} is gone and a bus now belongs to a {@code ModContainer}.
   */
  public void init(IEventBus modBus) {
    modBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, e -> e.enqueueWork(this::fireRegistryEvent));
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AddReloadListenerEvent.class, this::addDataPackListeners);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDatapackSyncEvent.class, e -> JsonUtils.syncPackets(e, getUpdatePacket()));
  }

  /** Fires the modifier registry event */
  private void fireRegistryEvent() {
    // ModLoader is a static utility in 1.21; there is no instance to get()
    ModLoader.runEventGenerator(ModifierRegistrationEvent::new);
    modifiersRegistered = true;
  }

  /** Adds the managers as datapack listeners */
  private void addDataPackListeners(final AddReloadListenerEvent event) {
    event.addListener(this);
    conditionContext = event.getConditionContext();
    // both built in and datapack registries are loaded and frozen by the time this event fires, which is the only
    // point in a reload where the enchantment registry can be seen. See loadEnchantmentMap.
    registryAccess = event.getRegistryAccess();
  }

  @Override
  protected void apply(Map<ResourceLocation,JsonElement> splashList, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
    long time = System.nanoTime();

    // load modifiers from JSON
    Map<ModifierId,ModifierId> redirects = new HashMap<>();
    Map<ModifierId,Supplier<Modifier>> dynamicModifiers = splashList.entrySet().stream()
                                                                   .map(entry -> loadModifier(entry.getKey(), entry.getValue().getAsJsonObject(), redirects))
                                                                   .filter(Objects::nonNull)
                                                                   .collect(Collectors.toMap(Modifier::getId, ModifierManager::constant));
    this.dynamicModifiers = dynamicModifiers;

    // process redirects
    Map<ModifierId,Supplier<Modifier>> resolvedRedirects = new HashMap<>(); // handled as a separate map to prevent redirects depending on order (no double redirects)
    for (Entry<ModifierId, ModifierId> redirect : redirects.entrySet()) {
      ModifierId from = redirect.getKey();
      ModifierId to = redirect.getValue();
      if (!contains(to)) {
        log.error("Invalid modifier redirect {} as modifier {} does not exist", from, to);
      } else {
        resolvedRedirects.put(from, constant(get(to)));
      }
    }
    int modifierSize = dynamicModifiers.size();
    dynamicModifiers.putAll(resolvedRedirects);

    // validate required modifiers
    for (ModifierId id : expectedDynamicModifiers) {
      if (!dynamicModifiers.containsKey(id)) {
        log.error("Missing expected modifier '{}'", id);
      }
    }
    for (ModifierId id : staticModifiers.keySet()) {
      if (dynamicModifiers.containsKey(id)) {
        if (FMLLoader.isProduction()) {
          log.warn("Dynamic modifier {} is replacing static modifier with the same ID. The ability to do this may be removed in a future version, so if this is intentional please open an issue report with reasoning..", id);
        } else {
          log.error("Dynamic modifier {} is replacing static modifier with the same ID. This is likely a bug with your mod, but on the chance its intentional this error does become just a warning at runtime.", id);
        }
      }
    }

    // TODO: this should be set back to false at some point
    dynamicModifiersLoaded = true;
    long timeStep = System.nanoTime();
    log.info("Loaded {} dynamic modifiers and {} modifier redirects in {} ms", modifierSize, redirects.size(), (timeStep - time) / 1000000f);
    time = timeStep;

    // load modifier tags
    TagLoader<Modifier> tagLoader = new TagLoader<>(id -> {
      Modifier modifier = ModifierManager.getValue(new ModifierId(id));
      // only allow the default modifier if it's explicitly set to empty
      if (modifier == defaultValue && !id.equals(EMPTY)) {
        return Optional.empty();
      }
      return Optional.of(modifier);
    }, TAG_FOLDER);
    this.tags = GenericTagUtil.mapLoaderResults(REGISTRY_KEY, tagLoader.loadAndBuild(pResourceManager));
    this.reverseTags = GenericTagUtil.reverseTags(Modifier::getId, tags);
    timeStep = System.nanoTime();
    log.info("Loaded {} modifier tags for {} modifiers in {} ms", tags.size(), this.reverseTags.size(), (timeStep - time) / 1000000f);

    // load modifier to enchantment mapping
    loadEnchantmentMap(pResourceManager);
    log.info("Loaded {} enchantment to modifier mappings in {} ms", enchantmentMap.size() + enchantmentTagMap.size(), (System.nanoTime() - timeStep) / 1000000f);

    NeoForge.EVENT_BUS.post(new ModifiersLoadedEvent());
  }

  /**
   * Reads {@link #ENCHANTMENT_MAP}, a JSON object of enchantment or enchantment tag to modifier ID.
   * <p>
   * An enchantment is named by {@link ResourceKey} rather than resolved to an instance, so the map does not depend on
   * a registry to exist and survives the reload that rebuilds one. The registry is still consulted, when a reload
   * supplied one, for the single purpose the {@code ?} suffix asks for: telling "this pack names an enchantment from a
   * mod you do not have" apart from "this pack has a typo".
   */
  private void loadEnchantmentMap(ResourceManager manager) {
    Map<ResourceKey<Enchantment>,Modifier> enchantmentMap = new HashMap<>();
    Map<TagKey<Enchantment>,Modifier> enchantmentTagMap = new LinkedHashMap<>();
    RegistryLookup<Enchantment> enchantments = registryAccess == null ? null : registryAccess.lookup(Registries.ENCHANTMENT).orElse(null);
    for (Resource resource : manager.getResourceStack(ENCHANTMENT_MAP)) {
      JsonObject enchantmentJson = JsonHelper.getJson(resource, ENCHANTMENT_MAP);
      if (enchantmentJson != null) {
        for (Entry<String,JsonElement> entry : enchantmentJson.entrySet()) {
          try {
            // parse the modifier first, its the same in both cases
            String key = entry.getKey();

            // if the modifier ends with a ?, its optional, so suppress errors if missing
            String modifierStr = GsonHelper.convertToString(entry.getValue(), key);
            boolean optional = modifierStr.charAt(modifierStr.length() - 1) == '?';
            if (optional) {
              modifierStr = modifierStr.substring(0, modifierStr.length() - 1);
            }
            ModifierId modifierId = ModifierId.PARSER.parseString(modifierStr, key);
            Modifier modifier = get(modifierId);
            if (modifier == defaultValue) {
              if (optional) {
                TConstruct.LOG.debug("Skipping unknown optional modifier " + modifierId + " for enchantment " + key);
                continue;
              }
              throw new JsonSyntaxException("Unknown modifier " + modifierId + " for enchantment " + key);
            }

            // if it starts with #, it's a tag
            if (key.charAt(0) == '#') {
              ResourceLocation tagId = ResourceLocation.tryParse(key.substring(1));
              if (tagId == null) {
                throw new JsonSyntaxException("Invalid enchantment tag ID " + key.substring(1));
              }
              enchantmentTagMap.put(TagKey.create(Registries.ENCHANTMENT, tagId), modifier);
            } else {
              // if it ends with a ?, its an optional enchantment, so suppress errors on missing
              optional = key.charAt(key.length() - 1) == '?';
              if (optional) {
                key = key.substring(0, key.length() - 1);
              }
              ResourceLocation enchantmentId = ResourceLocation.tryParse(key);
              if (enchantmentId == null) {
                throw new JsonSyntaxException("Invalid enchantment ID " + key + " for modifier " + modifierId);
              }
              ResourceKey<Enchantment> enchantment = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
              // an entry naming an enchantment that does not exist is harmless, no holder will ever match it. It is
              // still worth reporting, as the pack meant something by it, unless it said it was optional.
              if (enchantments != null && enchantments.get(enchantment).isEmpty()) {
                if (optional) {
                  TConstruct.LOG.debug("Skipping modifier " + modifierId + " due to unknown optional enchantment " + key);
                  continue;
                }
                throw new JsonSyntaxException("Invalid enchantment ID " + key + " for modifier " + modifierId);
              }
              enchantmentMap.put(enchantment, modifier);
            }
          } catch (RuntimeException e) {
            log.info("Invalid enchantment to modifier mapping", e);
          }
        }
      }
    }
    this.enchantmentMap = enchantmentMap;
    this.enchantmentTagMap = enchantmentTagMap;
  }

  /** Creates context for modifier parsing */
  public static TypedMapBuilder contextBuilder(ResourceLocation modifier) {
    return TypedMapBuilder.builder().put(ContextKey.ID, modifier).put(ContextKey.DEBUG, "Modifier " + modifier);
  }

  /** @deprecated use {@link #contextBuilder(ResourceLocation)} */
  @Deprecated(forRemoval = true)
  public static TypedMap createContext(ResourceLocation modifier) {
    return contextBuilder(modifier).build();
  }

  /** Wraps a modifier that is already in hand as a value of {@link #dynamicModifiers} */
  private static Supplier<Modifier> constant(Modifier modifier) {
    return () -> modifier;
  }

  /** Loads a modifier from JSON */
  @Nullable
  private Modifier loadModifier(ResourceLocation key, JsonElement element, Map<ModifierId, ModifierId> redirects) {
    try {
      JsonObject json = GsonHelper.convertToJsonObject(element, "modifier");

      // processed first so a modifier can both conditionally redirect and fallback to a conditional modifier
      if (json.has("redirects")) {
        for (JsonRedirect redirect : JsonHelper.parseList(json, "redirects", JsonRedirect::fromJson)) {
          ICondition redirectCondition = redirect.getCondition();
          if (redirectCondition == null || redirectCondition.test(conditionContext)) {
            ModifierId redirectTarget = new ModifierId(redirect.getId());
            log.debug("Redirecting modifier {} to {}", key, redirectTarget);
            redirects.put(new ModifierId(key), redirectTarget);
            return null;
          }
        }
      }

      // conditions
      // 1.21 replaced CraftingHelper's condition serializer registry with ICondition#CODEC, which dispatches on the
      // same "type" key through neoforge:condition_codecs. That registry is static, so plain ops are enough here.
      if (json.has("condition") && !ICondition.CODEC.parse(JsonOps.INSTANCE, json.get("condition")).getOrThrow(JsonSyntaxException::new).test(conditionContext)) {
        return null;
      }

      // fallback to actual modifier
      Modifier modifier = ComposableModifier.LOADER.deserialize(json, contextBuilder(key).put(ContextKey.CONDITION_CONTEXT, conditionContext).build());
      modifier.setId(new ModifierId(key));
      return modifier;
    } catch (JsonSyntaxException e) {
      log.error("Failed to load modifier {}", key, e);
      return null;
    }
  }


  /* Syncing */

  /**
   * Gets the packet to send on player login
   * @return  Packet object
   */
  public UpdateModifiersPacket getUpdatePacket() {
    return new UpdateModifiersPacket(dynamicModifiers, tags, enchantmentMap, enchantmentTagMap);
  }

  /**
   * Updates the modifiers from the server.
   * <p>
   * Order matters here and is the whole reason the packet hands over IDs and undecoded blocks rather than modifiers:
   * the map and the loaded flag are installed <em>first</em>, so that when a modifier payload is finally decoded and
   * turns out to contain an item stack - which for a Tinkers tool asks this registry through
   * {@code ToolStack#verifyComponents} - the registry can answer. A packet that decoded its modifiers eagerly would
   * be asking itself a question it has not finished answering.
   */
  void updateModifiersFromServer(UpdateModifiersPacket packet) {
    Map<ModifierId,Supplier<Modifier>> modifiers = new HashMap<>();
    packet.getModifiers().forEach((id, lazy) -> modifiers.put(id, lazy::get));
    this.dynamicModifiers = modifiers;
    this.dynamicModifiersLoaded = true;
    // a redirect shares the target's supplier rather than resolving it, so a redirect to a modifier nobody has asked
    // for still does not decode one
    packet.getRedirects().forEach((from, to) -> {
      Supplier<Modifier> target = modifiers.get(to);
      if (target == null) {
        Modifier statik = staticModifiers.get(to);
        if (statik == null) {
          log.error("Received modifier redirect {} to unknown modifier {}, ignoring", from, to);
          return;
        }
        target = constant(statik);
      }
      modifiers.put(from, target);
    });
    this.tags = resolveTags(packet.getTags());
    this.reverseTags = GenericTagUtil.reverseTags(Modifier::getId, tags);
    this.enchantmentMap = resolveValues(packet.getEnchantmentMap());
    this.enchantmentTagMap = resolveValues(packet.getEnchantmentTagMap());
    NeoForge.EVENT_BUS.post(new ModifiersLoadedEvent());
  }

  /** Resolves a map of modifier IDs into modifiers, dropping any ID this manager does not know */
  private <K> Map<K,Modifier> resolveValues(Map<K,ModifierId> map) {
    Map<K,Modifier> resolved = new LinkedHashMap<>(map.size());
    for (Entry<K,ModifierId> entry : map.entrySet()) {
      ModifierId id = entry.getValue();
      Modifier modifier = get(id);
      if (modifier == defaultValue && !EMPTY.equals(id)) {
        log.error("Received unknown modifier {} for {}, ignoring", id, entry.getKey());
      } else {
        resolved.put(entry.getKey(), modifier);
      }
    }
    return resolved;
  }

  /** Resolves the tag contents of a sync packet, dropping any ID this manager does not know */
  private Map<TagKey<Modifier>,List<Modifier>> resolveTags(Map<TagKey<Modifier>,List<ModifierId>> tags) {
    Map<TagKey<Modifier>,List<Modifier>> resolved = new HashMap<>(tags.size());
    for (Entry<TagKey<Modifier>,List<ModifierId>> entry : tags.entrySet()) {
      // an ID in a tag that the packet did not carry is dropped rather than being an error, matching the material
      // tags: the server writes tag contents and modifiers as two independent lists, so a modifier skipped by a
      // condition or resolved through a redirect is legitimately in one and not the other.
      // the empty modifier is kept when a tag names it, which is what the tag loader on the other side allows; the
      // 1.20 decoder had no way to say that and threw a DecoderException, which is to say it disconnected.
      resolved.put(entry.getKey(), entry.getValue().stream()
                                        .filter(id -> contains(id) || EMPTY.equals(id))
                                        .map(this::get)
                                        .toList());
    }
    return resolved;
  }


  /* Query the registry */

  /** Fetches a static modifier by ID, only use if you need access to modifiers before the world loads*/
  public Modifier getStatic(ModifierId id) {
    return staticModifiers.getOrDefault(id, defaultValue);
  }

  /** Checks if the given static modifier exists */
  public boolean containsStatic(ModifierId id) {
    return staticModifiers.containsKey(id) || expectedDynamicModifiers.contains(id);
  }

  /** Checks if the registry contains the given modifier */
  public boolean contains(ModifierId id) {
    return staticModifiers.containsKey(id) || dynamicModifiers.containsKey(id);
  }

  /** Gets the modifier for the given ID */
  public Modifier get(ModifierId id) {
    // highest priority is static modifiers, cannot be replaced
    Modifier modifier = staticModifiers.get(id);
    if (modifier != null) {
      return modifier;
    }
    // second priority is dynamic modifiers, fallback to the default
    Supplier<Modifier> dynamic = dynamicModifiers.get(id);
    return dynamic != null ? dynamic.get() : defaultValue;
  }

  /**
   * Gets the modifier for a given enchantment
   * @param enchantment  Enchantment holder, as received from an item's enchantment component
   * @return Closest modifier to the enchantment, or null if no match
   */
  @Nullable
  public Modifier get(Holder<Enchantment> enchantment) {
    // if we saw it before, return the last value. A holder knows its own key and its own tags, so neither lookup
    // needs the registry the holder came from
    Optional<ResourceKey<Enchantment>> key = enchantment.unwrapKey();
    if (key.isPresent()) {
      Modifier modifier = enchantmentMap.get(key.get());
      if (modifier != null) {
        return modifier;
      }
    }
    // did not find, check the tags
    for (Entry<TagKey<Enchantment>,Modifier> mapping : enchantmentTagMap.entrySet()) {
      if (enchantment.is(mapping.getKey())) {
        return mapping.getValue();
      }
    }
    return null;
  }

  /** Checks if the given modifier has an enchantment equivelent */
  public boolean hasEnchantment(Modifier modifier) {
    return enchantmentMap.containsValue(modifier) || enchantmentTagMap.containsValue(modifier);
  }

  /**
   * Gets a stream of all enchantments that match the given modifiers
   * @param registries  Registries to resolve names and tags against, as enchantments are a datapack registry
   * @param modifiers   Predicate matching the modifier of an enchantment
   * @return  Stream of matched enchantments, sorted by name
   */
  public Stream<Holder<Enchantment>> getEquivalentEnchantments(HolderLookup.Provider registries, Predicate<ModifierId> modifiers) {
    RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
    Predicate<Entry<?,Modifier>> predicate = entry -> modifiers.test(entry.getValue().getId());
    return Stream.<Holder<Enchantment>>concat(
      // an entry naming an enchantment this world does not have contributes nothing, same as it does in get(Holder)
      enchantmentMap.entrySet().stream().filter(predicate).flatMap(entry -> lookup.get(entry.getKey()).stream()),
      enchantmentTagMap.entrySet().stream().filter(predicate).flatMap(entry -> lookup.get(entry.getKey()).stream().flatMap(HolderSet::stream))
    ).distinct().sorted(Comparator.comparing(enchantment -> enchantment.unwrapKey().orElseThrow().location()));
  }

  /** Gets a list of all modifier IDs */
  public Stream<ResourceLocation> getAllLocations() {
    // filter out redirects (redirects are any modifiers where the ID does not match the key
    return Stream.concat(
      staticModifiers.entrySet().stream().filter(entry -> entry.getKey().equals(entry.getValue().getId())).map(Entry::getKey),
      dynamicModifiers.entrySet().stream().filter(entry -> entry.getKey().equals(entry.getValue().get().getId())).map(Entry::getKey));
  }

  /** Gets a stream of all modifier values */
  public Stream<Modifier> getAllValues() {
    return Stream.concat(staticModifiers.values().stream(), dynamicModifiers.values().stream().map(Supplier::get)).distinct();
  }


  /* Helpers */

  /** Gets the modifier for the given ID */
  public static Modifier getValue(ModifierId name) {
    return INSTANCE.get(name);
  }


  /* Tags */

  /** Creates a tag key for a modifier */
  public static TagKey<Modifier> getTag(ResourceLocation id) {
    return TagKey.create(REGISTRY_KEY, id);
  }

  /** Gets the set of tags on a modifier */
  public static Stream<TagKey<Modifier>> getTagKeys(ModifierId modifier) {
    return INSTANCE.reverseTags.getOrDefault(modifier, Set.of()).stream();
  }

  /**
   * Checks if the given modifier is in the given tag
   * @return  True if the modifier is in the tag
   */
  public static boolean isInTag(ModifierId modifier, TagKey<Modifier> tag) {
    return INSTANCE.reverseTags.getOrDefault(modifier, Set.of()).contains(tag);
  }

  /**
   * Gets all values contained in the given tag
   * @param tag  Tag instance
   * @return  Contained values, or null if the tag is absent
   */
  @Nullable
  public static List<Modifier> getTagOrNull(TagKey<Modifier> tag) {
    return INSTANCE.tags.get(tag);
  }

  /**
   * Gets all values contained in the given tag
   * @param tag  Tag instance
   * @return  Contained values
   */
  public static List<Modifier> getTagValues(TagKey<Modifier> tag) {
    return INSTANCE.tags.getOrDefault(tag, List.of());
  }

  /** Gets a stream of all tag ID to tag value mappings */
  public static Stream<Entry<TagKey<Modifier>,List<Modifier>>> getAllTags() {
    return INSTANCE.tags.entrySet().stream();
  }


  /* Events */

  /** Event for registering modifiers */
  @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
  public class ModifierRegistrationEvent extends Event implements IModBusEvent {
    /** Container receiving this event */
    private final ModContainer container;

    /** Validates the namespace of the container registering */
    private void checkModNamespace(ResourceLocation name) {
      // check mod container, should be the active mod
      // don't want mods registering stuff in Tinkers namespace, or Minecraft
      String activeMod = container.getNamespace();
      if (!name.getNamespace().equals(activeMod)) {
        TConstruct.LOG.warn("Potentially Dangerous alternative prefix for name `{}`, expected `{}`. This could be a intended override, but in most cases indicates a broken mod.", name, activeMod);
      }
    }

    /**
     * Registers a static modifier with the manager. Static modifiers cannot be configured by datapacks, so its generally encouraged to use dynamic modifiers
     * @param name      Modifier name
     * @param modifier  Modifier instance
     */
    public void registerStatic(ModifierId name, Modifier modifier) {
      checkModNamespace(name);

      // should not include under both types
      if (expectedDynamicModifiers.contains(name)) {
        throw new IllegalArgumentException(name + " is already expected as a dynamic modifier");
      }

      // set the name and register it
      modifier.setId(name);
      Modifier existing = staticModifiers.putIfAbsent(name, modifier);
      if (existing != null) {
        throw new IllegalArgumentException("Attempting to register a duplicate static modifier, this is not supported. Original value " + existing);
      }
    }

    /**
     * Registers that the given modifier is expected to be loaded in datapacks
     * @param name  Modifier name
     */
    public void registerExpected(ModifierId name) {
      checkModNamespace(name);

      // should not include under both types
      if (staticModifiers.containsKey(name)) {
        throw new IllegalArgumentException(name + " is already registered as a static modifier");
      }
      // register it
      expectedDynamicModifiers.add(name);
    }
  }

  /** Event fired when modifiers reload */
  public static class ModifiersLoadedEvent extends Event {}

  /** Class for the empty modifier instance, mods should not need to extend this class */
  private static class EmptyModifier extends Modifier {
    @Override
    public boolean shouldDisplay(boolean advanced) {
      return false;
    }
  }
}
