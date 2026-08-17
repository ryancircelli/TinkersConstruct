package slimeknights.tconstruct.library.json.math;

import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.floats.FloatStack;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Stack operation which pushes a variable from the context */
record PushVariableOperation(int index) implements StackOperation {
  @Override
  public void perform(FloatStack stack, float[] variables) {
    // indices are validated during parsing, so this should never fail
    stack.push(variables[index]);
  }

  @Override
  public JsonPrimitive serialize(String[] variableNames) {
    return new JsonPrimitive('$' + variableNames[index]);
  }

  @Override
  public void toNetwork(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(PostFixOperator.VARIABLE_INDEX);
    buffer.writeVarInt(index);
  }
}
