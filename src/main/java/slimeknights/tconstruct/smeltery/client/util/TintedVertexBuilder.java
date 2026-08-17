package slimeknights.tconstruct.smeltery.client.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import lombok.RequiredArgsConstructor;

/**
 * Vertex builder wrapper that tints all quads passed in.
 * <p>
 * 1.21 renamed every {@link VertexConsumer} method to a {@code set}/{@code add} prefix and, more importantly, deleted
 * {@code endVertex}, {@code defaultColor} and {@code unsetDefaultColor}: a vertex is finished by starting the next one
 * now, and the default color those two managed has no counterpart. Only {@link #setColor} ever did any work here, so
 * losing the default-color pair costs this wrapper nothing - the TODO on it asking whether tinting the default color
 * was even correct is answered by the API's removal.
 */
@RequiredArgsConstructor
public class TintedVertexBuilder implements VertexConsumer {
  /** Base vertex builder */
  private final VertexConsumer inner;
  /** Tint color from 0-255 */
  private final int tintRed, tintGreen, tintBlue, tintAlpha;

  @Override
  public VertexConsumer addVertex(float x, float y, float z) {
    return inner.addVertex(x, y, z);
  }

  @Override
  public VertexConsumer setColor(int red, int green, int blue, int alpha) {
    return inner.setColor((red * tintRed) / 0xFF, (green * tintGreen) / 0xFF, (blue * tintBlue) / 0xFF, (alpha * tintAlpha) / 0xFF);
  }

  @Override
  public VertexConsumer setUv(float u, float v) {
    return inner.setUv(u, v);
  }

  @Override
  public VertexConsumer setUv1(int u, int v) {
    return inner.setUv1(u, v);
  }

  @Override
  public VertexConsumer setUv2(int u, int v) {
    return inner.setUv2(u, v);
  }

  @Override
  public VertexConsumer setNormal(float x, float y, float z) {
    return inner.setNormal(x, y, z);
  }
}
