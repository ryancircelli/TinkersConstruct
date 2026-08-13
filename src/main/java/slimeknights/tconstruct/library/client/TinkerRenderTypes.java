package slimeknights.tconstruct.library.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import slimeknights.mantle.client.render.MantleRenderTypes;
import slimeknights.tconstruct.TConstruct;

import java.util.OptionalDouble;

/**
 * Class for render types defined by Tinkers.
 * <p>
 * 1.20 extended {@link RenderType} purely to inherit {@link RenderStateShard}'s protected constants; they are
 * {@code public static final} in 1.21, and {@link RenderType#create} is public, so this is a plain utility class.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TinkerRenderTypes {
  /** Render type for the error block that is seen through everything, mostly based on {@link RenderType#LINES} */
  public static final RenderType ERROR_BLOCK = RenderType.create(
    TConstruct.resourceString("lines"), DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
    RenderType.CompositeState.builder()
                             .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                             .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                             .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                             .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                             .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                             .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                             .setCullState(RenderStateShard.NO_CULL)
                             .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                             .createCompositeState(false));

  /** Render type for fluids, like {@link slimeknights.mantle.client.render.MantleRenderTypes#FLUID}, but disables cull so both sides show */
  public static final RenderType SMELTERY_FLUID = RenderType.create(
    TConstruct.resourceString("smeltery_fluid"), DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
    RenderType.CompositeState.builder()
                             .setLightmapState(RenderStateShard.LIGHTMAP)
                             .setShaderState(MantleRenderTypes.FLUID_SHADER)
                             .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                             .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                             .setCullState(RenderStateShard.NO_CULL)
                             .createCompositeState(false));
}
