package slimeknights.tconstruct.tools.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.sources.LazyLoadedImage;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map.Entry;

/** Sprite source creating modifier textures for banners using shield banner textures */
public record ShieldBannerModifierSpriteSource(int cropX, int cropY, int cropWidth, int cropHeight, ResourceLocation destinationPrefix, int offsetX, int offsetY, int outSize) implements SpriteSource {
  private static final Codec<Integer> NON_NEGATIVE = ExtraCodecs.intRange(0, Integer.MAX_VALUE);
  private static final Codec<Integer> SHIELD_SIZE = ExtraCodecs.intRange(0, 64);
  /**
   * Every shield pattern texture is {@code entity/shield/<asset id>}, which is exactly the path
   * {@code Sheets#getShieldMaterial} builds from a pattern's {@link BannerPattern#assetId()}.
   */
  private static final FileToIdConverter SHIELD_TEXTURES = new FileToIdConverter("textures/entity/shield", ".png");
  /**
   * A {@link SpriteSourceType} holds a {@link MapCodec} in 1.21 rather than a {@link Codec}, which is also why the
   * source's fields sit beside {@code "type"} in an atlas file instead of nested under a {@code "value"} object.
   * {@code ExtraCodecs.validate} is gone; {@link MapCodec#validate} is the same thing as a method.
   */
  public static final MapCodec<ShieldBannerModifierSpriteSource> CODEC = RecordCodecBuilder.<ShieldBannerModifierSpriteSource>mapCodec(inst -> inst.group(
    SHIELD_SIZE.fieldOf("crop_x").forGetter(ShieldBannerModifierSpriteSource::cropX),
    SHIELD_SIZE.fieldOf("crop_y").forGetter(ShieldBannerModifierSpriteSource::cropY),
    SHIELD_SIZE.fieldOf("crop_width").forGetter(ShieldBannerModifierSpriteSource::cropWidth),
    SHIELD_SIZE.fieldOf("crop_height").forGetter(ShieldBannerModifierSpriteSource::cropHeight),
    ResourceLocation.CODEC.fieldOf("destination_prefix").forGetter(ShieldBannerModifierSpriteSource::destinationPrefix),
    NON_NEGATIVE.fieldOf("offset_x").forGetter(ShieldBannerModifierSpriteSource::offsetX),
    NON_NEGATIVE.fieldOf("offset_y").forGetter(ShieldBannerModifierSpriteSource::offsetY),
    NON_NEGATIVE.fieldOf("output_size").forGetter(ShieldBannerModifierSpriteSource::outSize)
  ).apply(inst, ShieldBannerModifierSpriteSource::new)).validate(source -> {
    if (source.cropX + source.cropWidth >= 64 || source.cropY + source.cropHeight >= 64) {
      return DataResult.error(() -> "Invalid banner shield modifier sprite source: crop region must be within 64 by 64");
    } else if (source.offsetX + source.cropWidth >= source.outSize || source.offsetY + source.cropHeight >= source.outSize) {
      return DataResult.error(() -> "Invalid banner shield modifier sprite source: crop result must be placed within output size " + source.outSize);
    }
    return DataResult.success(source);
  });
  /** Registered type set on init */
  private static SpriteSourceType TYPE = null;

  /**
   * Registers this sprite source.
   * @apiNote  {@code SpriteSources#register} is private in 1.21; NeoForge's {@link RegisterSpriteSourceTypesEvent} is
   * the supported way in, and it keys on a {@link ResourceLocation} rather than its string form.
   */
  @Internal
  public static void register(RegisterSpriteSourceTypesEvent event) {
    if (TYPE == null) {
      TYPE = event.register(TConstruct.getResource("shield_banner_to_modifier"), CODEC);
    }
  }

  /**
   * @implNote  1.20 walked {@code Sheets.SHIELD_MATERIALS}, a map keyed by {@code ResourceKey<BannerPattern>}. That
   * field is private in 1.21, but access is not the real problem: it became a {@code computeIfAbsent} cache keyed on
   * {@link BannerPattern#assetId()}, filled as patterns are first rendered, so it is empty when the atlas is stitched
   * and widening it would enumerate nothing. Banner patterns are datapack content now, exactly as this method's own
   * 1.21 TODO predicted, so the durable enumeration is the textures themselves - which are resource pack content
   * whatever the datapack says, and live at the one path {@code Sheets#getShieldMaterial} builds.
   */
  @Override
  public void run(ResourceManager manager, Output output) {
    for (Entry<ResourceLocation,Resource> entry : SHIELD_TEXTURES.listMatchingResources(manager).entrySet()) {
      // listMatchingResources is ResourceManager#listResources, so it is keyed by the file and not by the id the
      // converter names. fileToId is the converter's own way back, and the file is what the image loads from.
      ResourceLocation file = entry.getKey();
      ResourceLocation assetId = SHIELD_TEXTURES.fileToId(file);
      LazyLoadedImage image = new LazyLoadedImage(file, entry.getValue(), 1);
      ResourceLocation destination = destinationPrefix.withSuffix(MaterialRenderInfo.getSuffix(assetId));
      output.add(destination, new BannerModifierSpriteSupplier(image, file, destination));
    }
  }

  @Override
  public SpriteSourceType type() {
    return TYPE;
  }

  /** Generates a cropped sprite lazily */
  @RequiredArgsConstructor
  private class BannerModifierSpriteSupplier implements SpriteSupplier {
    private final LazyLoadedImage original;
    private final ResourceLocation input, output;

    /**
     * @implNote  {@code SpriteSupplier} extends {@code Function<SpriteResourceLoader,SpriteContents>} in 1.21, so the
     * no-argument {@code get()} is {@code apply(loader)}. Nothing here reads the loader: the image is cropped from one
     * already loaded through {@link LazyLoadedImage}, not decoded from a pack resource.
     */
    @Nullable
    @Override
    public SpriteContents apply(SpriteResourceLoader loader) {
      try {
        // its possible the original is bigger than we expect due to HD pack, if so scale it accordingly
        // we only support scaling if it is a multiple of width
        NativeImage original = this.original.get();
        int scale = original.getWidth() / 64;
        if (scale == 0) {
          TConstruct.LOG.warn("Unable to crop {} to produce {} as texture size is less than 64", input, output);
        } else {
          NativeImage generated = new NativeImage(outSize * scale, outSize * scale, true);
          original.copyRect(generated, cropX * scale, cropY * scale, offsetX * scale, offsetY * scale, cropWidth * scale, cropHeight * scale, false, false);
          // the animation section and the trailing forge metadata collapsed into one ResourceMetadata
          return new SpriteContents(this.output, new FrameSize(generated.getWidth(), generated.getHeight()), generated, ResourceMetadata.EMPTY);
        }
      } catch (IllegalArgumentException | IOException ex) {
        TConstruct.LOG.warn("Unable to crop {} to produce {}", this.input, this.output, ex);
      } finally {
        this.original.release();
      }
      return null;
    }
  }
}
