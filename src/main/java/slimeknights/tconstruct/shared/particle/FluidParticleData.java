package slimeknights.tconstruct.shared.particle;

import com.mojang.serialization.MapCodec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Particle data for a fluid particle.
 * <p>
 * 1.21 dropped {@code ParticleOptions.Deserializer} along with {@code writeToNetwork} and {@code writeToString}; a
 * particle type now supplies a {@link MapCodec} and a {@link StreamCodec} and the game derives command parsing and
 * network sync from those.
 */
@RequiredArgsConstructor
public class FluidParticleData implements ParticleOptions {
  @Getter
  private final ParticleType<FluidParticleData> type;
  @Getter
  private final FluidStack fluid;

  /** Particle type for a fluid particle */
  public static class Type extends ParticleType<FluidParticleData> {
    private final MapCodec<FluidParticleData> codec = FluidStack.CODEC.fieldOf("fluid").xmap(fluid -> new FluidParticleData(this, fluid), data -> data.fluid);
    private final StreamCodec<RegistryFriendlyByteBuf,FluidParticleData> streamCodec = FluidStack.STREAM_CODEC.map(fluid -> new FluidParticleData(this, fluid), data -> data.fluid);

    public Type() {
      super(false);
    }

    @Override
    public MapCodec<FluidParticleData> codec() {
      return codec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf,FluidParticleData> streamCodec() {
      return streamCodec;
    }
  }
}
