package slimeknights.tconstruct.library.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import lombok.Data;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;

/** Represents a redirect in a material or modifier JSON */
@SuppressWarnings("ClassCanBeRecord") // GSON does not support records
@Data
public class JsonRedirect {
  private final ResourceLocation id;
  @Nullable
  private final ICondition condition;

  /** Serializes this to JSON */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("id", id.toString());
    if (condition != null) {
      // 1.21 replaced CraftingHelper's serializer registry with ICondition#CODEC, which dispatches on the same "type"
      // key through neoforge:condition_codecs. The registry is static, so plain JsonOps is enough here.
      json.add("condition", ICondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow(JsonSyntaxException::new));
    }
    return json;
  }

  /** Deserializes this to JSON */
  public static JsonRedirect fromJson(JsonObject json) {
    ResourceLocation id = JsonHelper.getResourceLocation(json, "id");
    ICondition condition = null;
    // note this reads the "condition" field, which is where toJson writes it; the 1.20 version passed the whole
    // redirect object to CraftingHelper#getCondition, so it could never read back anything it had written.
    JsonElement conditionJson = json.get("condition");
    if (conditionJson != null && !conditionJson.isJsonNull()) {
      condition = ICondition.CODEC.parse(JsonOps.INSTANCE, conditionJson).getOrThrow(JsonSyntaxException::new);
    }
    return new JsonRedirect(id, condition);
  }
}
