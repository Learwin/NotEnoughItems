package codechicken.nei;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import forestry.core.recipes.nei.PositionedFluidTank;
import gregtech.api.util.GTRecipe;
import tconstruct.plugins.nei.RecipeHandlerBase;
// import forestry.core.recipes.nei.PositionedFluidTank;
// import gregtech.api.util.GTRecipe;
// import tconstruct.plugins.nei.RecipeHandlerBase;

public class HandlerMapping {

    public static HashMap<String, String> missingTypes = new HashMap<>();

    @SerializedName("format")
    private List<HandlerDefinition> format;

    public List<HandlerDefinition> getFormat() {
        return format;
    }

    public static JsonObject getMappedRecipesJson(ICraftingHandler handler, int index, HandlerMapping mapping) {
        var json = new JsonObject();
        Field field = null;
        try {
            var recipe = getRecipeForHandler(handler, index);
            json.addProperty("handler", handler.getClass().getName());
            for (var f : recipe.getClass().getDeclaredFields()) {
                field = f;
                f.setAccessible(true);
                var fieldName = f.getName();
                if (f.isSynthetic()) {
                    continue;
                }

                var inMapping = mapping.getFormat().stream()
                        .anyMatch(handlerDefinition -> handlerDefinition.field().equals(fieldName));

                if (!inMapping) {
                    continue;
                }

                var fieldValue = f.get(recipe);
                if (fieldValue == null) {
                    continue;
                }
                if (fieldValue instanceof List<?>list) {
                    var jsonArr = new JsonArray();
                    for (var element : list) {
                        jsonArr.add(getJsonForField(element));
                    }
                    json.add(fieldName, jsonArr);
                } else if (f.getType().isArray()) {
                    var jsonArr = new JsonArray();
                    var arrLength = Array.getLength(fieldValue);
                    for (int i = 0; i < arrLength; i++) {
                        var element = Array.get(fieldValue, i);
                        var jsonElement = getJsonForField(element);
                        jsonArr.add(jsonElement);
                    }
                    json.add(fieldName, jsonArr);
                } else {
                    json.add(fieldName, getJsonForField(fieldValue));
                }
            }
        } catch (Exception e) {
            var fieldStr = "NO FIELD PRESENT !";
            if (field != null) {
                fieldStr = field.toString();
            }

            NEIClientConfig.logger.error(
                    "MAPPEDEXPORT Failed to convert Recipe to Json Class: {},Field: {}, Index:{}, E: {}",
                    handler.getClass().getSimpleName(),
                    fieldStr,
                    index,
                    e);
        }

        return json;
    }

    private static JsonElement getJsonForField(Object fieldValue) {
        if (fieldValue instanceof Number num) {
            return new JsonPrimitive(num);
        } else if (fieldValue instanceof String str) {
            return new JsonPrimitive(str);
        } else if (fieldValue instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        } else if (fieldValue instanceof FluidStack fluidStack) {
            return fluidStackToJson(fluidStack);
        } else if (fieldValue instanceof ItemStack itemStack) {
            return NBTJson.toJsonObject(StackInfo.itemStackToNBT(itemStack));
        } else if (fieldValue instanceof PositionedStack posStack) {
            return NBTJson.toJsonObject(StackInfo.itemStackToNBT(posStack.item));
        } else if (fieldValue instanceof GTRecipe recipe) {
            return getJsonForGTRecipe(recipe);
        } else if (fieldValue instanceof NBTBase nbt) {
            return NBTJson.toJsonObject(nbt);
        } else if (fieldValue instanceof RecipeHandlerBase.FluidTankElement tankElement) {
            return fluidStackToJson(tankElement.fluid);
        } else if (fieldValue instanceof PositionedFluidTank tank) {
            return fluidStackToJson(tank.tank.getFluid());
        }

        if (!missingTypes.containsKey(fieldValue.getClass().getSimpleName())) {
            missingTypes.put(fieldValue.getClass().getSimpleName(), "");
            NEIClientConfig.logger
                    .error("MAPPEDEXPORT Can't get Json for fieldtype: {}", fieldValue.getClass().getSimpleName());
        }
        return JsonNull.INSTANCE;
    }

    private static JsonElement getJsonForGTRecipe(GTRecipe recipe) {
        var gtRecipe = new JsonObject();
        gtRecipe.add("recipeType", new JsonPrimitive(recipe.getRecipeCategory().unlocalizedName));
        gtRecipe.add("mInputs", convertItemsToJsonArray(recipe.mInputs));
        gtRecipe.add("mOutputs", convertItemsToJsonArray(recipe.mOutputs));

        gtRecipe.add("mFluidInputs", convertFluidStacksToJsonArray(recipe.mFluidInputs));
        gtRecipe.add("mFluidOutputs", convertFluidStacksToJsonArray(recipe.mFluidOutputs));

        if (recipe.mOutputChances != null) {
            gtRecipe.add(
                    "chances",
                    Arrays.stream(recipe.mOutputChances).mapToObj(JsonPrimitive::new)
                            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
        }
        gtRecipe.add("mDuration", new JsonPrimitive(recipe.mDuration));
        gtRecipe.add("mEUt", new JsonPrimitive(recipe.mEUt));
        return gtRecipe;
    }

    private static JsonElement fluidStackToJson(FluidStack fluidStack) {
        var nbt = new NBTTagCompound();
        fluidStack.writeToNBT(nbt);
        return NBTJson.toJsonObject(nbt);
    }

    private static JsonArray convertItemsToJsonArray(ItemStack[] items) {
        return convertItemsToJsonArray(Arrays.asList(items));
    }

    private static JsonArray convertItemsToJsonArray(List<ItemStack> items) {
        final JsonArray arr = new JsonArray();

        for (ItemStack stack : items) {
            if (stack == null) {
                arr.add(JsonNull.INSTANCE);
            } else {
                arr.add(NBTJson.toJsonObject(StackInfo.itemStackToNBT(stack)));
            }
        }

        return arr;
    }

    private static JsonArray convertFluidStacksToJsonArray(FluidStack[] fluidStacks) {
        return convertFluidStacksToJsonArray(Arrays.asList(fluidStacks));
    }

    private static JsonArray convertFluidStacksToJsonArray(List<FluidStack> fluidStacks) {
        return fluidStacks.stream().map(HandlerMapping::fluidStackToJson)
                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }

    private static Object getRecipeForHandler(ICraftingHandler handler, int index)
            throws NoSuchFieldException, IllegalAccessException {
        var recipeField = handler.getClass().getField("arecipes");
        recipeField.setAccessible(true);
        var value = recipeField.get(handler);

        if (!(value instanceof ArrayList<?>recipes)) {
            throw new IllegalArgumentException("arecipes is not an ArrayList");
        }

        if (index < 0 || index >= recipes.size()) {
            throw new IndexOutOfBoundsException("Invalid recipe index: " + index);
        }

        return recipes.get(index);
    }
}
