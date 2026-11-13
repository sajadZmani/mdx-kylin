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

package io.kylin.mdx.insight.core.meta;

import io.kylin.mdx.insight.common.json.JSON;
import io.kylin.mdx.insight.common.json.JSONObject;
import io.kylin.mdx.insight.core.model.kylin.KylinColumnDesc;
import io.kylin.mdx.insight.core.model.kylin.KylinCubeDesc;
import io.kylin.mdx.insight.core.model.kylin.KylinDataModelDesc;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Parsing of Kylin 5 v2-compatible REST responses (shapes taken from the kylin5 branch controllers). */
public class Kylin5ConvertorTest {

    @BeforeClass
    public static void setUp() {
        System.setProperty("MDX_HOME", "src/test/resources/kylin/");
    }

    @AfterClass
    public static void tearDown() {
        System.clearProperty("MDX_HOME");
    }

    private static final String CUBE_DESC = "{\"code\":\"000\",\"msg\":\"\",\"data\":{\"cube\":{"
            + "\"uuid\":\"u1\",\"name\":\"ssb_model\","
            + "\"dimensions\":[{\"name\":\"D_YEAR\",\"table\":\"DATES\",\"column\":\"D_YEAR\",\"derived\":null},"
            + "{\"name\":\"C_REGION\",\"table\":\"CUSTOMER\",\"column\":\"C_REGION\"}],"
            + "\"measures\":[{\"name\":\"COUNT_ALL\",\"function\":{\"expression\":\"COUNT\","
            + "\"parameter\":{\"type\":\"constant\",\"value\":\"1\"},\"returntype\":\"bigint\"}},"
            + "{\"name\":\"SUM_REVENUE\",\"function\":{\"expression\":\"SUM\","
            + "\"parameter\":{\"type\":\"column\",\"value\":\"P_LINEORDER.LO_REVENUE\"},\"returntype\":\"bigint\"}}],"
            + "\"aggregation_groups\":[{\"includes\":[\"DATES.D_YEAR\"],\"select_rule\":{"
            + "\"hierarchy_dims\":[[\"DATES.D_YEAR\",\"DATES.D_MONTH\"]],\"mandatory_dims\":[],\"joint_dims\":[]}}]}}}";

    private static final String MODEL_DESC = "{\"code\":\"000\",\"msg\":\"\",\"data\":{\"model\":{"
            + "\"uuid\":\"u1\",\"alias\":\"ssb_model\",\"last_modified\":1700000000000,"
            + "\"fact_table\":\"SSB.P_LINEORDER\",\"status\":\"ONLINE\","
            + "\"lookups\":[{\"table\":\"SSB.DATES\",\"alias\":\"DATES\",\"kind\":\"LOOKUP\",\"join\":{\"type\":\"INNER\","
            + "\"primary_key\":[\"DATES.D_DATEKEY\"],\"foreign_key\":[\"P_LINEORDER.LO_ORDERDATE\"]}}],"
            + "\"dimensions\":[{\"table\":\"DATES\",\"columns\":[\"D_YEAR\"]}]}}}";

    @Test
    public void parseCubeDesc() {
        KylinCubeDesc desc = Kylin5Convertor.parseCubeDesc(CUBE_DESC);
        Assert.assertEquals("ssb_model", desc.getName());
        Assert.assertEquals("ssb_model", desc.getModel_name());
        Assert.assertEquals(2, desc.getDimensions().size());
        Assert.assertEquals("SUM", desc.getMeasures().get(1).getFunction().getExpression());
        Assert.assertEquals("P_LINEORDER.LO_REVENUE", desc.getMeasures().get(1).getFunction().getParameter().getValue());
        Assert.assertEquals(2, desc.getAggregation_groups().get(0).getSelect_rule().getHierarchy_dims().get(0).size());
        Assert.assertNotNull(desc.getSignature());
    }

    @Test
    public void parseModelDesc() {
        KylinCubeDesc cube = Kylin5Convertor.parseCubeDesc(CUBE_DESC);
        KylinDataModelDesc desc = Kylin5Convertor.parseModelDesc(MODEL_DESC, cube);
        KylinDataModelDesc.DataModel m = desc.getModels().get(0);
        Assert.assertEquals("ssb_model", m.getName());
        Assert.assertEquals("SSB.P_LINEORDER", m.getFact_table());
        Assert.assertEquals(1, m.getLookups().size());
        Assert.assertEquals("DATES", m.getLookups().get(0).getAlias());
        Assert.assertEquals("P_LINEORDER.LO_ORDERDATE", m.getLookups().get(0).getJoin().getForeign_key().get(0));
        Assert.assertEquals(1, m.getMetrics().size());
        Assert.assertEquals(Long.valueOf(1700000000000L), cube.getLast_modified());
    }

    @Test
    public void parseBareColumnList() {
        String body = "[{\"table_CAT\":\"defaultCatalog\",\"table_SCHEM\":\"SSB\",\"table_NAME\":\"DATES\","
                + "\"columns\":[{\"column_NAME\":\"D_YEAR\",\"data_TYPE\":4,\"type_NAME\":\"INTEGER\"}]}]";
        KylinColumnDesc desc = Kylin5Convertor.parseColumnDesc(body);
        Assert.assertEquals(1, desc.getData().size());
        Assert.assertEquals("SSB", desc.getData().get(0).getTable_SCHEM());
        Assert.assertEquals("D_YEAR", desc.getData().get(0).getColumns().get(0).getColumn_NAME());
    }

    @Test
    public void listOfAcceptsWrappedAndBare() {
        JSONObject wrapped = JSON.parseObject("{\"projects\":[{\"name\":\"a\"}],\"size\":1}");
        Assert.assertEquals(1, Kylin5Convertor.listOf(wrapped, "projects").size());
        Assert.assertEquals(1, Kylin5Convertor.listOf(JSON.parseArray("[1]"), "x").size());
        Assert.assertTrue(Kylin5Convertor.listOf(null, "x").isEmpty());
    }

    @Test
    public void encodesPathSegments() {
        Assert.assertEquals("my%20model", Kylin5Convertor.enc("my model"));
    }

    @Test(expected = RuntimeException.class)
    public void failedEnvelopeThrows() {
        Kylin5Convertor.parseCubeDesc("{\"code\":\"999\",\"msg\":\"model not found\",\"data\":null}");
    }
}
