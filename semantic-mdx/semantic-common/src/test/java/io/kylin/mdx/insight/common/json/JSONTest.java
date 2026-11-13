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

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class JSONTest {

    public static class Bean {
        private String name;
        private Integer size;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @Test
    public void roundTripBeanOmitsNulls() {
        Bean b = new Bean();
        b.setName("ds");
        b.setTags(Arrays.asList("a", "b"));
        String json = JSON.toJSONString(b);
        Assert.assertFalse(json.contains("size"));
        Bean back = JSON.parseObject(json, Bean.class);
        Assert.assertEquals("ds", back.getName());
        Assert.assertEquals(2, back.getTags().size());
    }

    @Test
    public void nestedAccessors() {
        JSONObject o = JSON.parseObject("{\"code\":\"000\",\"data\":{\"list\":[{\"n\":\"1\",\"b\":\"true\"}],"
                + "\"ts\":1700000000000,\"dec\":1.5}}");
        JSONObject data = o.getJSONObject("data");
        Assert.assertEquals(1, data.getJSONArray("list").size());
        Assert.assertEquals(1, data.getJSONArray("list").getJSONObject(0).getIntValue("n"));
        Assert.assertTrue(data.getJSONArray("list").getJSONObject(0).getBooleanValue("b"));
        Assert.assertEquals(1700000000000L, data.getDate("ts").getTime());
        Assert.assertEquals("1.5", data.getString("dec"));
        Assert.assertEquals(0, data.getIntValue("missing"));
        Assert.assertNull(data.getString("missing"));
    }

    @Test
    public void caseInsensitiveAndUnknownProperties() {
        Bean b = JSON.parseObject("{\"NAME\":\"x\",\"unknown\":1}", Bean.class);
        Assert.assertEquals("x", b.getName());
    }

    @Test
    public void autoTypeIsIgnored() {
        // fastjson auto-type payloads must never instantiate arbitrary classes
        String payload = "{\"@type\":\"com.sun.rowset.JdbcRowSetImpl\",\"dataSourceName\":\"ldap://x\",\"autoCommit\":true}";
        Object parsed = JSON.parse(payload);
        Assert.assertTrue(parsed instanceof JSONObject);
        Assert.assertEquals("com.sun.rowset.JdbcRowSetImpl", ((JSONObject) parsed).getString("@type"));
        Bean b = JSON.parseObject(payload, Bean.class);
        Assert.assertNull(b.getName());
    }

    @Test
    public void toJavaListAndToJavaObject() {
        JSONArray arr = JSON.parseArray("[{\"name\":\"a\"},{\"name\":\"b\"}]");
        List<Bean> beans = arr.toJavaList(Bean.class);
        Assert.assertEquals("b", beans.get(1).getName());
        Assert.assertEquals("a", arr.getJSONObject(0).toJavaObject(Bean.class).getName());
        Assert.assertEquals(2, JSON.parseArray("[{\"name\":\"a\"},{\"name\":\"b\"}]", Bean.class).size());
    }
}
