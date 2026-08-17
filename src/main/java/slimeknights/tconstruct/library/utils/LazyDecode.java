package slimeknights.tconstruct.library.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A value carried in a packet whose decode is postponed until something asks for it.
 * <p>
 * This exists for one failure and its whole family. A datapack sync packet is decoded the moment its bytes arrive,
 * which on login is before the other datapack sync packets have been handled. If that decode resolves anything the
 * other packets are what populate - a modifier, a material, a stat type - it throws, and a throw inside a decoder is
 * not a missing field, it is a disconnect. The station slot layout packet hit this: its icons and slot filters carry
 * item stacks, {@code Item#verifyComponentsAfterLoad} runs on <em>every</em> item stack construction in 1.21 rather
 * than only on load, and for a Tinkers tool that hook rebuilds stats, which asks the modifier registry, which is
 * loaded by a packet that has not been handled yet.
 * <p>
 * The general rule the four sync packets follow is that a decode reads identifiers and primitives and resolves
 * nothing. Where a field is an identifier - a modifier entry, a material ID, a stat type ID - that is free, and
 * {@code ModifierEntry.LOADABLE} has always worked this way. Where a field is a whole object whose construction is
 * the thing that reaches out, the only way to read it without resolving it is not to read it: this class takes the
 * length prefixed block of bytes off the buffer and keeps it, with the connection's registries, until the first
 * {@link #get()}.
 * <p>
 * The cost is one varint per deferred field, one retained byte array until the value is first used, and a wire format
 * that a 1.20 client could not read - which is moot, as none of these packets kept their framing across the port.
 */
public final class LazyDecode<T> implements Supplier<T> {
  private final StreamCodec<RegistryFriendlyByteBuf,T> codec;
  /** Undecoded payload; null once {@link #value} is filled */
  @Nullable
  private byte[] bytes;
  /** Registries of the connection the bytes arrived on, needed to decode them; null once {@link #value} is filled */
  @Nullable
  private RegistryAccess registries;
  /** Connection type of the connection the bytes arrived on; null once {@link #value} is filled */
  @Nullable
  private ConnectionType connectionType;
  /** Decoded value, filled by the first {@link #get()} */
  @Nullable
  private T value;

  private LazyDecode(StreamCodec<RegistryFriendlyByteBuf,T> codec, @Nullable byte[] bytes, @Nullable RegistryAccess registries, @Nullable ConnectionType connectionType, @Nullable T value) {
    this.codec = codec;
    this.bytes = bytes;
    this.registries = registries;
    this.connectionType = connectionType;
    this.value = value;
  }

  /** Wraps a value that is already in hand, such as one parsed from JSON on the server */
  public static <T> LazyDecode<T> of(StreamCodec<RegistryFriendlyByteBuf,T> codec, T value) {
    return new LazyDecode<>(codec, null, null, null, Objects.requireNonNull(value, "value"));
  }

  /** Takes the block {@link #write(RegistryFriendlyByteBuf)} produced off the buffer without decoding it */
  public static <T> LazyDecode<T> read(RegistryFriendlyByteBuf buffer, StreamCodec<RegistryFriendlyByteBuf,T> codec) {
    byte[] bytes = new byte[buffer.readVarInt()];
    buffer.readBytes(bytes);
    return new LazyDecode<>(codec, bytes, buffer.registryAccess(), buffer.getConnectionType(), null);
  }

  /**
   * Writes the value as a length prefixed block.
   * A value that has not been decoded yet is copied through as the bytes it arrived as, so a proxy neither decodes it
   * nor has to be able to.
   */
  public void write(RegistryFriendlyByteBuf buffer) {
    if (bytes != null) {
      buffer.writeVarInt(bytes.length);
      buffer.writeBytes(bytes);
      return;
    }
    // the length has to be known before the block is written, so the block is written first, to somewhere else
    ByteBuf scratch = Unpooled.buffer();
    try {
      codec.encode(new RegistryFriendlyByteBuf(scratch, buffer.registryAccess(), buffer.getConnectionType()), get());
      buffer.writeVarInt(scratch.readableBytes());
      buffer.writeBytes(scratch);
    } finally {
      scratch.release();
    }
  }

  /**
   * Decodes the value, if it has not been decoded already.
   * Call this from the code that uses the value rather than from the code that received it.
   */
  @Override
  public T get() {
    if (value == null) {
      T decoded = codec.decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(Objects.requireNonNull(bytes)), Objects.requireNonNull(registries), Objects.requireNonNull(connectionType)));
      this.value = decoded;
      this.bytes = null;
      this.registries = null;
      this.connectionType = null;
    }
    return value;
  }

  /** If true, the value has not been decoded yet. Exists for tests, which are the only thing that should care */
  public boolean isPending() {
    return value == null;
  }
}
