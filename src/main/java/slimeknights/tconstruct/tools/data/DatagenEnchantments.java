package slimeknights.tconstruct.tools.data;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import slimeknights.mantle.data.loadable.Loadables;

/**
 * Resolves an enchantment {@link Holder} for data generation.
 * <p>
 * 1.21 moved enchantments into a datapack registry, so at data generation time there is no {@link Enchantment}
 * instance to name and {@link net.minecraft.world.item.enchantment.Enchantments} holds only {@link ResourceKey}s.
 * Every module and fluid effect that stores an enchantment stores a holder, as that is what
 * {@link Loadables#ENCHANTMENT} reads back at runtime, so the provider has to bridge the two.
 * <p>
 * Writing an enchantment only ever needs its key - {@code DynamicRegistryLoadable} serializes through
 * {@link Holder#unwrapKey()} - so a stand-alone reference carrying the key and no value is enough, and it writes
 * exactly the ID 1.20 wrote. It deliberately has no value: nothing may call {@link Holder#value()} on a holder
 * from here, and doing so throws rather than silently reading a stale enchantment.
 */
public class DatagenEnchantments {
  private DatagenEnchantments() {}

  /** Owner for our stand-alone holders. No registry exists to own them, and none is needed to write their key. */
  private static final HolderOwner<Enchantment> OWNER = new HolderOwner<>() {
    @Override
    public String toString() {
      return "Tinkers' Construct data generation";
    }
  };

  /** Creates a key-only holder for the given enchantment, suitable for serializing but not for reading a value */
  public static Holder<Enchantment> holder(ResourceKey<Enchantment> enchantment) {
    return Holder.Reference.createStandAlone(OWNER, enchantment);
  }
}
