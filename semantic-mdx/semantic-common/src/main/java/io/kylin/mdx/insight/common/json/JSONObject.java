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

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Jackson-backed, insertion-ordered JSON object with fastjson-compatible accessors. */
public class JSONObject extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public JSONObject() {
        super();
    }

    public JSONObject(Map<String, ?> map) {
        super();
        if (map != null) {
            putAll(map);
        }
    }

    public static Object parse(String text) {
        return JSON.parse(text);
    }

    public static JSONObject parseObject(String text) {
        return JSON.parseObject(text);
    }

    public static <T> T parseObject(String text, Class<T> clazz) {
        return JSON.parseObject(text, clazz);
    }

    public static JSONArray parseArray(String text) {
        return JSON.parseArray(text);
    }

    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        return JSON.parseArray(text, clazz);
    }

    public static String toJSONString(Object o) {
        return JSON.toJSONString(o);
    }

    public JSONObject fluentPut(String key, Object value) {
        put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getJSONObject(String key) {
        Object v = get(key);
        if (v == null || v instanceof JSONObject) {
            return (JSONObject) v;
        }
        if (v instanceof Map) {
            return new JSONObject((Map<String, ?>) v);
        }
        if (v instanceof String) {
            return JSON.parseObject((String) v);
        }
        return (JSONObject) JSON.toJSON(v);
    }

    public JSONArray getJSONArray(String key) {
        Object v = get(key);
        if (v == null || v instanceof JSONArray) {
            return (JSONArray) v;
        }
        if (v instanceof List) {
            return new JSONArray((List<?>) v);
        }
        if (v instanceof String) {
            return JSON.parseArray((String) v);
        }
        return (JSONArray) JSON.toJSON(v);
    }

    public String getString(String key) {
        return TypeUtils.toStr(get(key));
    }

    public Integer getInteger(String key) {
        return TypeUtils.toInt(get(key));
    }

    public int getIntValue(String key) {
        Integer v = getInteger(key);
        return v == null ? 0 : v;
    }

    public Long getLong(String key) {
        return TypeUtils.toLong(get(key));
    }

    public long getLongValue(String key) {
        Long v = getLong(key);
        return v == null ? 0L : v;
    }

    public Double getDouble(String key) {
        return TypeUtils.toDouble(get(key));
    }

    public double getDoubleValue(String key) {
        Double v = getDouble(key);
        return v == null ? 0D : v;
    }

    public BigDecimal getBigDecimal(String key) {
        return TypeUtils.toBigDecimal(get(key));
    }

    public Boolean getBoolean(String key) {
        return TypeUtils.toBool(get(key));
    }

    public boolean getBooleanValue(String key) {
        Boolean v = getBoolean(key);
        return v != null && v;
    }

    public Date getDate(String key) {
        return TypeUtils.toDate(get(key));
    }

    public <T> T getObject(String key, Class<T> clazz) {
        return JSON.toJavaObject(get(key), clazz);
    }

    public <T> T toJavaObject(Class<T> clazz) {
        return JSON.toJavaObject(this, clazz);
    }

    public String toJSONString() {
        return JSON.toJSONString(this);
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
