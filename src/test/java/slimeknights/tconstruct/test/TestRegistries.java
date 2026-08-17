package slimeknights.tconstruct.test;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;

/**
 * The datapack registries a headless test needs, built once per JVM.
 * <p>
 * {@link net.minecraft.server.Bootstrap#bootStrap()} - all {@code BaseMcTest} runs - fills the <em>static</em>
 * registries only. 1.21 moved several things a fixture may name into <em>datapack</em> registries, enchantments
 * chief among them, and Mantle's {@code DynamicRegistryLoadable} refuses to guess: it needs either a
 * {@code RegistryOps} or a {@link HolderLookup.Provider} in the loadable context, and on the network it reads the
 * buffer's {@link RegistryAccess}. With neither, ten modifier fixtures naming {@code minecraft:fortune} and
 * friends failed with "the datapack registry minecraft:enchantment is unavailable".
 * <p>
 * {@link VanillaRegistries#createLookup()} is the provider vanilla's own data generators run on: it layers the
 * datapack registries over {@code RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)}, so the
 * result carries both halves and every entry is the real one rather than a stub. Building it is not cheap - it
 * bootstraps worldgen too - so both accessors memoise.
 */
public final class TestRegistries {
  private TestRegistries() {}

  /**
   * Datapack registries materialised into {@link #access()}. Only what a fixture actually names is listed: each
   * one is copied entry by entry out of the lookup, and there is no reason to pay for biomes and features.
   */
  private static final List<ResourceKey<? extends Registry<?>>> NETWORK_REGISTRIES = List.of(Registries.ENCHANTMENT);

  private static HolderLookup.Provider lookup;
  private static RegistryAccess.Frozen access;

  /**
   * Provider covering the static registries and every vanilla datapack registry.
   * @see slimeknights.mantle.data.loadable.field.ContextKey#REGISTRY_ACCESS
   */
  public static synchronized HolderLookup.Provider lookup() {
    if (lookup == null) {
      lookup = VanillaRegistries.createLookup();
    }
    return lookup;
  }

  /**
   * Registry access for a {@link net.minecraft.network.RegistryFriendlyByteBuf}, covering the static registries
   * plus {@link #NETWORK_REGISTRIES}.
   * @apiNote {@link RegistryAccess} wants real {@link Registry} instances and {@link #lookup()} only exposes
   * {@link HolderLookup.RegistryLookup}s, so the datapack ones are copied into a {@link MappedRegistry} here.
   */
  public static synchronized RegistryAccess.Frozen access() {
    if (access == null) {
      List<Registry<?>> registries = new ArrayList<>();
      BuiltInRegistries.REGISTRY.forEach(registries::add);
      for (ResourceKey<? extends Registry<?>> key : NETWORK_REGISTRIES) {
        registries.add(materialise(key));
      }
      access = new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
    }
    return access;
  }

  /**
   * Unfreezes the built-in registries a headless test writes to.
   * <p>
   * {@link net.minecraft.server.Bootstrap#bootStrap()} freezes them, and 1.21 made that bite in two new ways: an
   * {@link net.minecraft.world.item.Item} constructor now asks the registry for an intrusive holder, so merely
   * constructing one throws; and anything that builds a {@link HolderLookup} over the built-ins re-freezes them,
   * so this is safe and expected to be called more than once.
   * @apiNote Yes, this is bad, but this is testing so we do bad things sometimes.
   */
  public static void unfreezeBuiltIns() {
    unfreeze(BuiltInRegistries.ITEM);
    unfreeze(BuiltInRegistries.BLOCK);
    unfreeze(BuiltInRegistries.ATTRIBUTE);
    unfreeze(BuiltInRegistries.MOB_EFFECT);
    unfreeze(BuiltInRegistries.PARTICLE_TYPE);
  }

  @SuppressWarnings("unchecked")
  private static void unfreeze(Registry<?> registry) {
    ((MappedRegistry<Object>) registry).unfreeze();
  }

  @SuppressWarnings("unchecked")
  private static <T> Registry<T> materialise(ResourceKey<? extends Registry<?>> key) {
    ResourceKey<Registry<T>> typed = (ResourceKey<Registry<T>>) key;
    MappedRegistry<T> registry = new MappedRegistry<>(typed, Lifecycle.stable());
    lookup().lookupOrThrow(typed).listElements().forEach(holder -> Registry.register(registry, holder.key(), holder.value()));
    registry.freeze();
    return registry;
  }
}
