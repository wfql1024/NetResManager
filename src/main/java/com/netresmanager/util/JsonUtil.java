package com.netresmanager.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Thin wrapper around Gson for JSON serialization/deserialization.
 * All JsBridge methods use this to marshal data to/from JS.
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    private JsonUtil() {}

    /**
     * Serialize an object to JSON string.
     */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        return GSON.toJson(obj);
    }

    /**
     * Deserialize a JSON string to an object of the given type.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /**
     * Deserialize a JSON string to an object with a generic type.
     * Usage: fromJson(json, new TypeToken<List<Project>>(){})
     */
    public static <T> T fromJson(String json, java.lang.reflect.Type type) {
        return GSON.fromJson(json, type);
    }

    /**
     * Wraps data in a success envelope: {"success":true, "data":..., "error":null}
     */
    public static String successResponse(Object data) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("success", true);
        envelope.add("data", data == null ? null : JsonParser.parseString(toJson(data)));
        envelope.add("error", null);
        return toJson(envelope);
    }

    /**
     * Wraps an error in a failure envelope: {"success":false, "data":null, "error":{...}}
     */
    public static String errorResponse(String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject envelope = new JsonObject();
        envelope.addProperty("success", false);
        envelope.add("data", null);
        envelope.add("error", error);
        return toJson(envelope);
    }

    /**
     * Generic JSON envelope used by JS bridge to parse results.
     * JS side does: var result = JSON.parse(javaResult); if (result.success) { ... }
     */
    public static Gson getGson() {
        return GSON;
    }
}
