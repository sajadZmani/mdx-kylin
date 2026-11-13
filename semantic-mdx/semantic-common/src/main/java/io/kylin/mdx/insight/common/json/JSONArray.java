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

import com.fasterxml.jackson.databind.type.CollectionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Jackson-backed JSON array with fastjson-compatible accessors. */
public class JSONArray extends ArrayList<Object> {

    private static final long serialVersionUID = 1L;

    public JSONArray() {
        super();
    }

    public JSONArray(Collection<?> c) {
        super(c == null ? new ArrayList<>() : c);
    }

    public static JSONArray parseArray(String text) {
        return JSON.parseArray(text);
    }

    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        return JSON.parseArray(text, clazz);
    }

    public static JSONObject parseObject(String text) {
        return JSON.parseObject(text);
    }

    public static String toJSONString(Object o) {
        return JSON.toJSONString(o);
    }

    public JSONArray fluentAdd(Object o) {
        add(o);
        return this;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getJSONObject(int index) {
        Object v = get(index);
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

    public JSONArray getJSONArray(int index) {
        Object v = get(index);
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

    public String getString(int index) {
        return TypeUtils.toStr(get(index));
    }

    public Integer getInteger(int index) {
        return TypeUtils.toInt(get(index));
    }

    public int getIntValue(int index) {
        Integer v = getInteger(index);
        return v == null ? 0 : v;
    }

    public Long getLong(int index) {
        return TypeUtils.toLong(get(index));
    }

    public long getLongValue(int index) {
        Long v = getLong(index);
        return v == null ? 0L : v;
    }

    public Double getDouble(int index) {
        return TypeUtils.toDouble(get(index));
    }

    public BigDecimal getBigDecimal(int index) {
        return TypeUtils.toBigDecimal(get(index));
    }

    public Boolean getBoolean(int index) {
        return TypeUtils.toBool(get(index));
    }

    public boolean getBooleanValue(int index) {
        Boolean v = getBoolean(index);
        return v != null && v;
    }

    public Date getDate(int index) {
        return TypeUtils.toDate(get(index));
    }

    public <T> T getObject(int index, Class<T> clazz) {
        return JSON.toJavaObject(get(index), clazz);
    }

    public <T> List<T> toJavaList(Class<T> clazz) {
        CollectionType type = JSON.MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
        return JSON.MAPPER.convertValue(this, type);
    }

    public String toJSONString() {
        return JSON.toJSONString(this);
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
