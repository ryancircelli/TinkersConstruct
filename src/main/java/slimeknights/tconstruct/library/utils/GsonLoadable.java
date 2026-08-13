package slimeknights.tconstruct.library.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.util.typed.TypedMap;

/** Simple loadable mapping GSON to loadable. Uses NBT for networking */
public record GsonLoadable<T>(Gson gson, Class<T> classType) implements Loadable<T> {
  @Override
  public T convert(JsonElement json, String s, TypedMap context) {
    return gson.fromJson(json, classType);
  }

  @Override
  public JsonElement serialize(T object) {
    return gson.toJsonTree(object, classType);
  }

  /**
   * @implNote  {@code readAnySizeNbt} is gone in 1.21; {@link RegistryFriendlyByteBuf#readNbt(NbtAccounter)} with
   *            {@link NbtAccounter#unlimitedHeap()} is the same thing spelled out. It returns any {@link Tag} rather
   *            than only a {@link net.minecraft.nbt.CompoundTag}, which is what lets {@link #encode} below drop the
   *            compound-only restriction. Note the buffer is unbounded on purpose, matching 1.20: this loadable is
   *            only used for datapack contents the server already trusts.
   */
  @Override
  public T decode(RegistryFriendlyByteBuf buffer, TypedMap context) {
    Tag tag = buffer.readNbt(NbtAccounter.unlimitedHeap());
    if (tag != null) {
      return gson.fromJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag), classType);
    }
    throw new DecoderException("Failed to decode: " + classType.getSimpleName());
  }

  /**
   * @implNote  1.20 could only write a compound here as its reader could only read one, so a GSON type serializing to a
   *            list or a primitive threw at encode time. 1.21's tag reader is not compound specific, so any tag round
   *            trips and the restriction (and its {@code EncoderException}) is gone.
   */
  @Override
  public void encode(RegistryFriendlyByteBuf buffer, T object) {
    buffer.writeNbt(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, gson.toJsonTree(object, classType)));
  }
}
