package slimeknights.tconstruct.shared.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

import java.util.Locale;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class ClearStainedGlassBlock extends TransparentBlock {
  public static final MapCodec<ClearStainedGlassBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    propertiesCodec(),
    GlassColor.CODEC.fieldOf("color").forGetter(block -> block.glassColor)
  ).apply(instance, ClearStainedGlassBlock::new));

  private final GlassColor glassColor;
  public ClearStainedGlassBlock(Properties properties, GlassColor glassColor) {
    super(properties);
    this.glassColor = glassColor;
  }

  @Override
  public MapCodec<? extends ClearStainedGlassBlock> codec() {
    return CODEC;
  }

  @Override
  public Integer getBeaconColorMultiplier(BlockState state, LevelReader world, BlockPos pos, BlockPos beaconPos) {
    return this.glassColor.getBeaconColor();
  }

  /** Enum used for registration of this and the pane block */
  public enum GlassColor implements StringRepresentable {
    WHITE(0xffffff, DyeColor.WHITE),
    ORANGE(0xd87f33, DyeColor.ORANGE),
    MAGENTA(0xb24cd8, DyeColor.MAGENTA),
    LIGHT_BLUE(0x6699d8, DyeColor.LIGHT_BLUE),
    YELLOW(0xe5e533, DyeColor.YELLOW),
    LIME(0x7fcc19, DyeColor.LIME),
    PINK(0xf27fa5, DyeColor.PINK),
    GRAY(0x4c4c4c, DyeColor.GRAY),
    LIGHT_GRAY(0x999999, DyeColor.LIGHT_GRAY),
    CYAN(0x4c7f99, DyeColor.CYAN),
    PURPLE(0x7f3fb2, DyeColor.PURPLE),
    BLUE(0x334cb2, DyeColor.BLUE),
    BROWN(0x664c33, DyeColor.BROWN),
    GREEN(0x667f33, DyeColor.GREEN),
    RED(0x993333, DyeColor.RED),
    BLACK(0x191919, DyeColor.BLACK);

    /** Codec for the color, used by the block codecs */
    public static final StringRepresentable.EnumCodec<GlassColor> CODEC = StringRepresentable.fromEnum(GlassColor::values);

    private final int color;
    private final DyeColor dye;
    private final String name;

    GlassColor(int color, DyeColor dye) {
      this.color = color;
      this.dye = dye;
      this.name = this.name().toLowerCase(Locale.US);
    }

    /**
     * Variant color to reduce number of models
     * @return  Variant color for BlockColors and ItemColors
     */
    public int getColor() {
      return this.color;
    }

    /** Gets the vanilla dye color associated with this color */
    public DyeColor getDye() {
      return dye;
    }

    /**
     * Gets the color for the beacon beam.
     * <p>
     * 1.20 answered the beam with a {@code float[]} of RGB components; 1.21 replaced that hook with
     * {@link net.neoforged.neoforge.common.extensions.IBlockExtension#getBeaconColorMultiplier} which wants a packed
     * ARGB integer, matching {@link DyeColor#getTextureDiffuseColor()}.
     * @return  Opaque ARGB color for the beacon beam
     */
    public int getBeaconColor() {
      return 0xFF000000 | this.color;
    }

    @Override
    public String getSerializedName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
