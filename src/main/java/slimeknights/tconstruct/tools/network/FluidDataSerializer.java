package slimeknights.tconstruct.tools.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Serializer for fluid stack data in entities.
 * <p>
 * 1.21 replaced the serializer's read/write pair with a {@link StreamCodec}, and NeoForge ships one for a possibly
 * empty fluid stack, so the class is now the codec plus the copy that a serializer still owes.
 */
public class FluidDataSerializer implements EntityDataSerializer<FluidStack> {
  @Override
  public StreamCodec<? super RegistryFriendlyByteBuf,FluidStack> codec() {
    return FluidStack.OPTIONAL_STREAM_CODEC;
  }

  @Override
  public FluidStack copy(FluidStack stack) {
    return stack.copy();
  }
}
