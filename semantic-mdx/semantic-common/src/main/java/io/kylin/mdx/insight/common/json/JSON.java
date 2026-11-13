/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kylin.mdx.insight.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Jackson-backed replacement for the subset of the fastjson API used by this project
 * (fastjson was removed because of CVE-2022-25845 and related auto-type RCEs).
 * <p>
 * Security: no polymorphic / default typing is ever enabled, so untrusted JSON can only be
 * bound to the concrete target types requested by the caller (no "@type" auto-type gadgets).
 * Behaviour mirrors fastjson defaults the code relied on: null fields are omitted on write,
 * unknown properties are ignored and property names match case-insensitively on read.
 */
public class JSON {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        MAPPER.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        MAPPER.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        MAPPER.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    protected JSON() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static Object parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return fromNode(MAPPER.readTree(text));
        } catch (IOException e) {
            throw new JSONException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    public static JSONObject parseObject(String text) {
        Object o = parse(text);
        if (o == null) {
            return null;
        }
        if (o instanceof JSONObject) {
            return (JSONObject) o;
        }
        throw new JSONException("JSON text is not an object");
    }

    public static <T> T parseObject(String text, Class<T> clazz) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(text, clazz);
        } catch (IOException e) {
            throw new JSONException("Cannot parse JSON to " + clazz.getName() + ": " + e.getMessage(), e);
        }
    }

    public static JSONArray parseArray(String text) {
        Object o = parse(text);
        if (o == null) {
            return null;
        }
        if (o instanceof JSONArray) {
            return (JSONArray) o;
        }
        throw new JSONException("JSON text is not an array");
    }

    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            CollectionType type = MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
            return MAPPER.readValue(text, type);
        } catch (IOException e) {
            throw new JSONException("Cannot parse JSON array of " + clazz.getName() + ": " + e.getMessage(), e);
        }
    }

    public static String toJSONString(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(object);
        } catch (IOException e) {
            throw new JSONException("Cannot serialize " + object.getClass().getName(), e);
        }
    }

    public static String toJSONString(Object object, boolean prettyFormat) {
        if (!prettyFormat || object == null) {
            return toJSONString(object);
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (IOException e) {
            throw new JSONException("Cannot serialize " + object.getClass().getName(), e);
        }
    }

    public static Object toJSON(Object javaObject) {
        if (javaObject == null || javaObject instanceof JSONObject || javaObject instanceof JSONArray) {
            return javaObject;
        }
        return fromNode(MAPPER.valueToTree(javaObject));
    }

    public static <T> T toJavaObject(Object json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        if (clazz.isInstance(json)) {
            return clazz.cast(json);
        }
        if (json instanceof String && !String.class.equals(clazz)) {
            String s = (String) json;
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                return parseObject(s, clazz);
            }
        }
        return MAPPER.convertValue(json, clazz);
    }

    static Object fromNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            JSONObject obj = new JSONObject();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                obj.put(e.getKey(), fromNode(e.getValue()));
            }
            return obj;
        }
        if (node.isArray()) {
            JSONArray arr = new JSONArray();
            for (JsonNode n : node) {
                arr.add(fromNode(n));
            }
            return arr;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt()) {
            return node.intValue();
        }
        if (node.isLong()) {
            return node.longValue();
        }
        if (node.isBigInteger()) {
            return node.bigIntegerValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node.asText();
    }
}
