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
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Lenient scalar conversions equivalent to what fastjson's typed getters did. */
final class TypeUtils {

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private static final String[] DATE_PATTERNS = {"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"};

    private TypeUtils() {
    }

    static String toStr(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            return (String) v;
        }
        if (v instanceof Map || v instanceof List) {
            return JSON.toJSONString(v);
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).toPlainString();
        }
        return v.toString();
    }

    private static String blankToNull(Object v) {
        String s = v.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof Boolean) {
            return (Boolean) v ? 1 : 0;
        }
        String s = blankToNull(v);
        return s == null ? null : new BigDecimal(s).intValue();
    }

    static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof Date) {
            return ((Date) v).getTime();
        }
        String s = blankToNull(v);
        return s == null ? null : new BigDecimal(s).longValue();
    }

    static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        String s = blankToNull(v);
        return s == null ? null : Double.valueOf(s);
    }

    static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof BigInteger) {
            return new BigDecimal((BigInteger) v);
        }
        if (v instanceof Number) {
            return new BigDecimal(v.toString());
        }
        String s = blankToNull(v);
        return s == null ? null : new BigDecimal(s);
    }

    static Boolean toBool(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue() == 1;
        }
        String s = blankToNull(v);
        if (s == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "Y".equalsIgnoreCase(s) || "T".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "N".equalsIgnoreCase(s) || "F".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        throw new JSONException("Cannot cast '" + s + "' to boolean");
    }

    static Date toDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Date) {
            return (Date) v;
        }
        if (v instanceof Number) {
            return new Date(((Number) v).longValue());
        }
        String s = blankToNull(v);
        if (s == null) {
            return null;
        }
        if (INTEGER.matcher(s).matches()) {
            return new Date(Long.parseLong(s));
        }
        for (String p : DATE_PATTERNS) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p);
                f.setLenient(false);
                return f.parse(s);
            } catch (ParseException ignored) {
                // try next pattern
            }
        }
        throw new JSONException("Cannot cast '" + s + "' to Date");
    }
}
