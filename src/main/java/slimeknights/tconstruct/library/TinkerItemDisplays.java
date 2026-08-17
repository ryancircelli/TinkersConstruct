package slimeknights.tconstruct.library;

import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;

/**
 * Custom transform types used for tinkers item rendering.
 * <p>
 * 1.20 registered these into {@code ForgeRegistries.DISPLAY_CONTEXTS} from a {@code RegisterEvent} listener. There is
 * no such registry in 1.21: {@link ItemDisplayContext} is an <em>extended enum</em>, carrying {@code @IndexedEnum},
 * {@code @NamedEnum(1)} and {@code @NetworkedEnum(CLIENTBOUND)}, and a mod adds values by declaring them in the
 * {@code enumExtensions} JSON that {@code neoforge.mods.toml} names. The values therefore exist before the game
 * starts and there is nothing left to register, which is why {@code init()} and its listener are gone.
 * <p>
 * The values are declared in {@code META-INF/enumextensions.json} against {@link Proxies}, and read back here. They
 * cannot be read back in {@link Proxies} itself: the enum's own class initializer is what consumes the proxies, so a
 * field in that class calling {@link EnumProxy#getValue()} would ask for a constant that is still being built. This
 * class initializes later, on first use by a renderer or a model, by which point the enum is complete.
 */
public class TinkerItemDisplays {
  private TinkerItemDisplays() {}

  /** Used by the melter and smeltery for display of items its melting */
  public static final ItemDisplayContext MELTER = Proxies.MELTER.getValue();
  /** Used by the part builder, crafting station, tinkers station, and tinker anvil */
  public static final ItemDisplayContext TABLE = Proxies.TABLE.getValue();
  /** Used by the casting table for item rendering */
  public static final ItemDisplayContext CASTING_TABLE = Proxies.CASTING_TABLE.getValue();
  /** Used by the casting basin for item rendering */
  public static final ItemDisplayContext CASTING_BASIN = Proxies.CASTING_BASIN.getValue();
  /** Used by the fluid cannon for display of the item in front */
  public static final ItemDisplayContext FLUID_CANNON = Proxies.FLUID_CANNON.getValue();
  /** Used by throwing to allow adjusting the tool position */
  public static final ItemDisplayContext THROWN = Proxies.THROWN.getValue();

  /**
   * Constructor arguments for the six values, named by {@code META-INF/enumextensions.json}.
   * <p>
   * A proxy rather than plain constants in the JSON because the fallback argument is nullable and the JSON constant
   * form has no way to spell null for a string parameter. Passing {@code NONE} instead is not the same thing: a null
   * fallback makes {@code ItemTransforms#getTransform} answer {@code NO_TRANSFORM} directly, where {@code NONE} would
   * additionally let a model's {@code "none"} display entry stand in for these contexts.
   * <p>
   * This class holds nothing but proxies, so its initializer runs safely while the enum is still being built.
   */
  public static class Proxies {
    private Proxies() {}

    public static final EnumProxy<ItemDisplayContext> MELTER = proxy("melter", null);
    public static final EnumProxy<ItemDisplayContext> TABLE = proxy("table", null);
    public static final EnumProxy<ItemDisplayContext> CASTING_TABLE = proxy("casting_table", "FIXED");
    public static final EnumProxy<ItemDisplayContext> CASTING_BASIN = proxy("casting_basin", null);
    public static final EnumProxy<ItemDisplayContext> FLUID_CANNON = proxy("fluid_cannon", "FIXED");
    public static final EnumProxy<ItemDisplayContext> THROWN = proxy("thrown", "FIXED");

    /**
     * Creates the argument list for one added display context.
     * @param name      Path of the context's id. Serialized as {@code tconstruct:<name>}, unchanged from 1.20, so no
     *                  model JSON moves.
     * @param fallback  Name of the vanilla constant to fall back on when a model declares no transform for this
     *                  context, or null for no fallback.
     */
    private static EnumProxy<ItemDisplayContext> proxy(String name, @Nullable String fallback) {
      // -1 is the id: @IndexedEnum means the loader fills in the real ordinal and requires the placeholder be -1
      return new EnumProxy<>(ItemDisplayContext.class, -1, TConstruct.resourceString(name), fallback);
    }
  }
}
