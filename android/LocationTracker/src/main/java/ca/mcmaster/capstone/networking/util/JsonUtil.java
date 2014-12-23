package ca.mcmaster.capstone.networking.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

import ca.mcmaster.capstone.monitoralgorithm.BooleanExpressionTree;

public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(BooleanExpressionTree.Node.class, new InterfaceAdapter<BooleanExpressionTree.Node>())
            .create();

    public static <T> T fromJson(final String json, final TypeToken<T> type) {
        return fromJson(json, type.getType());
    }

    public static <T> T fromJson(final String json, final Type type) {
        return GSON.fromJson(json, type);
    }

    public static String asJson(final Object data) {
        return GSON.toJson(data);
    }

}
