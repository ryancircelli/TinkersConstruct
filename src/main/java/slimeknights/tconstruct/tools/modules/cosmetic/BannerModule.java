package slimeknights.tconstruct.tools.modules.cosmetic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.DisplayNameModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.utils.TinkerTooltipFlags;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Module for banner pattern tooltips */
public enum BannerModule implements ModifierModule, DisplayNameModifierHook, TooltipModifierHook {
  INSTANCE;

  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<BannerModule>defaultHooks(ModifierHooks.DISPLAY_NAME, ModifierHooks.TOOLTIP);
  public static final RecordLoadable<BannerModule> LOADER = new SingletonLoader<>(INSTANCE);
  /** Key for a dye color, stored as its ID */
  public static final String KEY_DYE = "dye";
  /** Key for a pattern color, as a 24 bit integer */
  public static final String KEY_COLOR = "color";
  /**
   * Key for the banner pattern, stored as its registry ID.
   * @apiNote  1.20 stored the short hash {@code BannerPattern#getHashname()} produced. Banner patterns are a datapack
   * registry in 1.21 and both that method and the hash scheme are gone, so the layer is named by its registry ID.
   * This is a <b>tool NBT format change</b>: a shield decorated in 1.20 holds hashes this cannot read back.
   */
  public static final String KEY_PATTERN = "pattern";
  /** Tooltip key saying hold shift for patterns */
  private static final Component HOLD_SHIFT = TConstruct.makeTranslation("modifier", "banner.hold_shift").withStyle(ChatFormatting.GRAY);

  @Override
  public RecordLoadable<? extends ModifierModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public Component getDisplayName(IToolStackView tool, ModifierEntry entry, Component name, @Nullable RegistryAccess access) {
    // color the tooltip the color of the first pattern
    ListTag patterns = tool.getPersistentData().getList(patternKey(entry.getId()), ListTag.TAG_COMPOUND);
    if (!patterns.isEmpty()) {
      return name.copy().withStyle(name.getStyle().withColor(DyeColor.byId(patterns.getCompound(0).getInt(KEY_DYE)).getTextColor()));
    }
    return name;
  }

  @Override
  public void addTooltip(IToolStackView tool, ModifierEntry modifier, @Nullable Player player, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    // add all patterns in a tinker station when holding
    if (tooltipFlag == TinkerTooltipFlags.TINKER_STATION) {
      if (tooltipKey == TooltipKey.SHIFT) {
        ListTag patterns = tool.getPersistentData().getList(patternKey(modifier.getId()), ListTag.TAG_COMPOUND);
        for (int i = 0; i < patterns.size(); i++) {
          CompoundTag tag = patterns.getCompound(i);
          DyeColor dye = DyeColor.byId(tag.getInt(KEY_DYE));
          // 1.21 keeps the translation key on the pattern itself rather than deriving it from the ID, so the
          // datapack registry has to be consulted; vanilla patterns produce the same key 1.20 built by hand.
          getPattern(tag.getString(KEY_PATTERN)).ifPresent(pattern ->
            tooltip.add(Component.translatable(pattern.translationKey() + '.' + dye.getName()).withStyle(ChatFormatting.GRAY)));
        }
      } else {
        tooltip.add(HOLD_SHIFT);
      }
    }
  }

  /** Gets the key for the cache used in the model */
  public static ResourceLocation cacheKey(ModifierId modifier) {
    return modifier.withSuffix("_cache");
  }

  /** Gets the key for the pattern list in NBT */
  public static ResourceLocation patternKey(ModifierId modifier) {
    return modifier.withSuffix("_patterns");
  }

  /** Looks up a stored pattern ID in the banner pattern registry, empty if the ID is unparsable or unregistered */
  private static Optional<BannerPattern> getPattern(String id) {
    ResourceLocation location = ResourceLocation.tryParse(id);
    if (location == null) {
      return Optional.empty();
    }
    return Util.registryAccess().lookup(Registries.BANNER_PATTERN)
      .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.BANNER_PATTERN, location)))
      .map(Holder::value);
  }

  /**
   * Copies the given banner's layers to the tool's NBT.
   * @apiNote  Takes the {@link BannerPatternLayers} component 1.21 stores on a banner rather than 1.20's {@code
   * Patterns} list; a layer already carries its pattern holder and dye, so nothing has to be re-parsed here. The base
   * layer no longer needs a registry lookup either, as {@link BannerPatterns#BASE} is a key and the ID is all that
   * gets stored.
   */
  public static void copyPatterns(ModDataNBT data, ModifierId id, DyeColor dye, BannerPatternLayers banner) {
    int baseColor = Util.getColor(dye);
    ListTag patterns = new ListTag();

    // add in the base pattern, it only exists on shields and we copy from banners
    CompoundTag basePattern = new CompoundTag();
    basePattern.putString(KEY_PATTERN, BannerPatterns.BASE.location().toString());
    basePattern.putInt(KEY_DYE, dye.getId());
    basePattern.putInt(KEY_COLOR, baseColor);
    patterns.add(basePattern);

    // need a cache key, but it's just going to get hashed anyway, so store its hash
    int hashCode = baseColor;

    // add in all other patterns
    for (BannerPatternLayers.Layer layer : banner.layers()) {
      // an unregistered pattern has no ID to store and would not render either, so skip it
      Optional<ResourceKey<BannerPattern>> key = layer.pattern().unwrapKey();
      if (key.isEmpty()) {
        continue;
      }
      CompoundTag copy = new CompoundTag();
      // the pattern is stored by ID, which is what the model's texture suffix wants
      String pattern = key.get().location().toString();
      copy.putString(KEY_PATTERN, pattern);
      // convert the color from a dye color to an integer
      DyeColor layerDye = layer.color();
      int color = Util.getColor(layerDye);
      copy.putInt(KEY_DYE, layerDye.getId()); // dye for the tooltip
      copy.putInt(KEY_COLOR, color); // color for the model
      // add the values
      patterns.add(copy);
      // update the hash code with the new information
      hashCode = 31 * (31 * hashCode + color) + pattern.hashCode();
    }

    // add to tool NBT
    data.put(patternKey(id), patterns);
    data.putInt(cacheKey(id), hashCode);
  }
}
