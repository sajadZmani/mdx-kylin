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

import io.kylin.mdx.ErrorCode;
import io.kylin.mdx.insight.common.SemanticConfig;
import io.kylin.mdx.insight.common.SemanticConstants;
import io.kylin.mdx.insight.common.SemanticException;
import io.kylin.mdx.insight.common.http.HttpUtil;
import io.kylin.mdx.insight.common.json.JSON;
import io.kylin.mdx.insight.common.json.JSONArray;
import io.kylin.mdx.insight.common.json.JSONObject;
import io.kylin.mdx.insight.common.util.Utils;
import io.kylin.mdx.insight.core.model.acl.AclProjectModel;
import io.kylin.mdx.insight.core.model.acl.AclTableModel;
import io.kylin.mdx.insight.core.model.generic.KylinGenericModel;
import io.kylin.mdx.insight.core.model.generic.KylinUserInfo;
import io.kylin.mdx.insight.core.model.kylin.KylinBeanWrapper;
import io.kylin.mdx.insight.core.model.kylin.KylinColumnDesc;
import io.kylin.mdx.insight.core.model.kylin.KylinCubeDesc;
import io.kylin.mdx.insight.core.model.kylin.KylinDataModelDesc;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Kylin 5 metadata client.
 * <p>
 * Kylin 5 has no cubes: a model plus its index plan is the unit of precomputation. Kylin 5 still ships the
 * "v2" REST controllers (selected with {@code Accept: application/vnd.apache.kylin-v2+json}) that render a
 * model in the Kylin 3/4 shape, including {@code /api/cube_desc/{project}/{model}} which is built from the
 * model's aggregation groups. One Kylin 5 model therefore maps to one former "cube" here, and the generic
 * model building in {@link KylinConvertor#convert(KylinBeanWrapper)} is reused unchanged.
 * <p>
 * All Kylin-5-specific endpoints and response parsing live in this class; select it with
 * {@code insight.kylin.version=5} (the default).
 */
@Slf4j(topic = "http.call")
public class Kylin5Convertor extends AbstractConvertor<KylinBeanWrapper> {

    public static final String ACCEPT_V2 = "application/vnd.apache.kylin-v2+json";
    public static final String ACCEPT_V4 = "application/vnd.apache.kylin-v4+json";
    public static final String ACCEPT_JSON = "application/json";

    static final String API_PROJECTS = "/kylin/api/projects?pageOffset=0&pageSize={size}";
    static final String API_MODELS = "/kylin/api/models?projectName={project}&exactMatch=false&pageOffset=0&pageSize={size}";
    static final String API_MODEL_DESC = "/kylin/api/models/{project}/{model}";
    static final String API_CUBE_DESC = "/kylin/api/cube_desc/{project}/{model}";
    static final String API_TABLES_AND_COLUMNS = "/kylin/api/tables_and_columns?project={project}";
    static final String API_TABLES_EXT = "/kylin/api/tables?project={project}&ext=true&page_offset=0&page_size={size}";
    static final String API_USERS = "/kylin/api/kap/user/users?pageOffset=0&pageSize={size}";
    static final String API_USER_BY_NAME = "/kylin/api/kap/user/users?pageOffset=0&pageSize={size}&isCaseSensitive=false&name={name}";
    static final String API_GROUPS = "/kylin/api/user_group/groups?page_offset=0&page_size={size}";
    static final String API_PROJECT_ACCESS = "/kylin/api/access/ProjectInstance/{project}?pageOffset=0&pageSize={size}";
    static final String API_PROJECT_PERMISSION = "/kylin/api/access/permission/project_permission?project={project}";

    /** Kylin 5 model states that can serve queries. WARNING = has segment holes but is still online. */
    private static final Set<String> QUERYABLE_STATUS = new TreeSet<>(java.util.Arrays.asList("ONLINE", "WARNING"));

    private final KylinConvertor genericConvertor = new KylinConvertor();

    @Override
    public KylinGenericModel convert(KylinBeanWrapper sourceModel) {
        return genericConvertor.convert(sourceModel);
    }

    // ------------------------------------------------------------------ http

    @Override
    public String doHttpCall(String url, byte[] auth) {
        return get(url, auth, ACCEPT_V2);
    }

    protected String get(String url, byte[] auth, String accept) {
        CloseableHttpResponse response = null;
        try {
            response = HttpUtil.httpGetWithAccept(url, auth, accept);
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SemanticException("The http calling invoke exception.", e);
        } finally {
            IOUtils.closeQuietly(response);
        }
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(Objects.requireNonNull(s), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int pageSize() {
        return Math.max(Integer.parseInt(SemanticConfig.getInstance().getProjectPageSize().trim()), 1000);
    }

    private static int userPageSize() {
        return Math.max(Integer.parseInt(SemanticConfig.getInstance().getUserPageSize().trim()), 1000);
    }

    private static JSONObject parseEnvelope(String content) {
        JSONObject json = JSON.parseObject(content);
        if (json == null) {
            throw new SemanticException("Empty response from Kylin");
        }
        validateRespSuccess(json);
        return json;
    }

    // ------------------------------------------------------------- projects

    @Override
    public Set<String> getActualProjectSet(ConnectionInfo connInfo) throws SemanticException {
        byte[] auth = auth(connInfo);
        String content = doHttpCall(API_PROJECTS.replace("{size}", String.valueOf(pageSize())), auth);
        JSONObject json;
        try {
            json = parseEnvelope(content);
        } catch (SemanticException e) {
            log.warn("To get project lists has exception, {}", content);
            throw new SemanticException("To get project lists has exception", ErrorCode.FETCH_KYLIN_PROJECT_LIST_ERROR);
        }
        Set<String> projects = new TreeSet<>();
        for (JSONObject p : objects(listOf(json.get(SemanticConstants.SUC_DATA), "projects"))) {
            projects.add(p.getString("name"));
        }
        return projects;
    }

    @Override
    public String getAccessInfo(ConnectionInfo connInfo) throws SemanticException {
        String url = API_PROJECT_PERMISSION.replace("{project}", enc(connInfo.getProject()));
        String content = get(url, auth(connInfo), ACCEPT_V4);
        JSONObject json = JSON.parseObject(content);
        if (json == null || !SemanticConstants.STATUS_SUC.equals(json.getString(SemanticConstants.STATUS_KEY))) {
            log.warn("To get project access information has exception, {}", content);
            throw new SemanticException("To get project access information has exception", ErrorCode.FETCH_KYLIN_ACCESS_INFO_ERROR);
        }
        return json.getString(SemanticConstants.SUC_DATA);
    }

    // ----------------------------------------------------------- models

    /** Raw v2 model list of a project (each entry is a Kylin 5 NDataModelResponse incl. old params). */
    protected List<JSONObject> listModels(String project, byte[] auth) {
        String url = API_MODELS.replace("{project}", enc(project)).replace("{size}", String.valueOf(pageSize()));
        JSONObject json = parseEnvelope(doHttpCall(url, auth));
        return objects(listOf(json.get(SemanticConstants.SUC_DATA), "models"));
    }

    static String modelName(JSONObject model) {
        String alias = model.getString("alias");
        return alias != null ? alias : model.getString("name");
    }

    static boolean isQueryable(JSONObject model) {
        String status = model.getString("status");
        return status == null || QUERYABLE_STATUS.contains(status.toUpperCase());
    }

    @Override
    public List<String> getCubeNames(ConnectionInfo connInfo) throws SemanticException {
        return listModels(connInfo.getProject(), auth(connInfo)).stream()
                .filter(Kylin5Convertor::isQueryable)
                .map(Kylin5Convertor::modelName)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getSegments(ConnectionInfo connInfo) throws SemanticException {
        List<String> segments = new ArrayList<>();
        for (JSONObject model : listModels(connInfo.getProject(), auth(connInfo))) {
            if (!isQueryable(model)) {
                continue;
            }
            for (JSONObject seg : objects(model.getJSONArray("segments"))) {
                String id = seg.getString("id") != null ? seg.getString("id") : seg.getString("uuid");
                if (id != null) {
                    segments.add(id);
                }
            }
        }
        return segments;
    }

    @Override
    public List<KylinBeanWrapper> buildDatasourceModel(ConnectionInfo connInfo) {
        List<KylinBeanWrapper> result = new LinkedList<>();
        byte[] auth = auth(connInfo);
        String project = connInfo.getProject();
        try {
            KylinColumnDesc columnDesc = getColumnDesc(project, auth);
            for (String model : getCubeNames(connInfo)) {
                try {
                    KylinCubeDesc cubeDesc = getCubeDesc(project, model, auth);
                    KylinDataModelDesc modelDesc = getModelDesc(project, model, auth, cubeDesc);
                    result.add(new KylinBeanWrapper(columnDesc, modelDesc, cubeDesc));
                } catch (Exception e) {
                    log.error("Skip Kylin 5 model [{}] of project [{}]: cannot load its metadata", model, project, e);
                }
            }
        } catch (Exception e) {
            log.error("The mdx-service inits KYLIN 5 datasource to get exception", e);
        }
        return result;
    }

    KylinCubeDesc getCubeDesc(String project, String model, byte[] auth) {
        String url = API_CUBE_DESC.replace("{project}", enc(project)).replace("{model}", enc(model));
        String content = doHttpCall(url, auth);
        try {
            return parseCubeDesc(content);
        } catch (Exception e) {
            log.error("The http call KYLIN 5 API [cube_desc] get exception, response body:{}", content);
            throw new SemanticException("Http: getKylinCubeDesc get exception", e, ErrorCode.FETCH_KYLIN_MODEL_INFO_ERROR);
        }
    }

    static KylinCubeDesc parseCubeDesc(String content) {
        JSONObject data = parseEnvelope(content).getJSONObject(SemanticConstants.SUC_DATA);
        JSONObject cube = data.containsKey("cube") ? data.getJSONObject("cube") : data;
        KylinCubeDesc desc = cube.toJavaObject(KylinCubeDesc.class);
        if (desc.getModel_name() == null) {
            desc.setModel_name(desc.getName());
        }
        if (desc.getDimensions() == null) {
            desc.setDimensions(new ArrayList<>());
        }
        if (desc.getMeasures() == null) {
            desc.setMeasures(new ArrayList<>());
        }
        if (desc.getAggregation_groups() == null) {
            desc.setAggregation_groups(new ArrayList<>());
        }
        for (KylinCubeDesc.AggregationGroup g : desc.getAggregation_groups()) {
            if (g.getSelect_rule() == null) {
                g.setSelect_rule(new KylinCubeDesc.AggregationGroup.SelectRule());
            }
            if (g.getSelect_rule().getHierarchy_dims() == null) {
                g.getSelect_rule().setHierarchy_dims(new ArrayList<>());
            }
        }
        if (desc.getSignature() == null) {
            // Kylin 5 has no cube signature: derive a stable one from the structure so changes are detected.
            desc.setSignature(Integer.toHexString(JSON.toJSONString(cube.get("dimensions")).hashCode())
                    + Integer.toHexString(JSON.toJSONString(cube.get("measures")).hashCode()));
        }
        return desc;
    }

    KylinDataModelDesc getModelDesc(String project, String model, byte[] auth, KylinCubeDesc cubeDesc) {
        String url = API_MODEL_DESC.replace("{project}", enc(project)).replace("{model}", enc(model));
        String content = doHttpCall(url, auth);
        try {
            return parseModelDesc(content, cubeDesc);
        } catch (Exception e) {
            log.error("The http call KYLIN 5 API [model desc] get exception, response body:{}", content);
            throw new SemanticException("Http: getModelDescByHttp get exception", e, ErrorCode.FETCH_KYLIN_MODEL_INFO_ERROR);
        }
    }

    static KylinDataModelDesc parseModelDesc(String content, KylinCubeDesc cubeDesc) {
        JSONObject data = parseEnvelope(content).getJSONObject(SemanticConstants.SUC_DATA);
        JSONObject model = data.containsKey("model") ? data.getJSONObject("model") : data;
        // Kylin 5 renames: alias -> name; join_tables are exposed as "lookups" by the v2 old params.
        if (model.get("name") == null) {
            model.put("name", model.getString("alias"));
        }
        if (model.get("lookups") == null && model.get("join_tables") != null) {
            model.put("lookups", model.get("join_tables"));
        }
        KylinDataModelDesc.DataModel dm = model.toJavaObject(KylinDataModelDesc.DataModel.class);
        if (dm.getLookups() == null) {
            dm.setLookups(new ArrayList<>());
        }
        if (dm.getDimensions() == null) {
            dm.setDimensions(new ArrayList<>());
        }
        if (dm.getMetrics() == null || dm.getMetrics().isEmpty()) {
            // Kylin 5 models have no "metrics" list; derive it from the columns referenced by measures.
            Set<String> metrics = new LinkedHashSet<>();
            if (cubeDesc != null) {
                for (KylinCubeDesc.Measure m : cubeDesc.getMeasures()) {
                    KylinCubeDesc.Measure.Function f = m.getFunction();
                    if (f != null && f.getParameter() != null && f.getParameter().getValue() != null
                            && !SemanticConstants.FUNCTION_CONSTANT_TYPE.equals(f.getParameter().getType())
                            && f.getParameter().getValue().contains(".")) {
                        metrics.add(f.getParameter().getValue());
                    }
                }
            }
            dm.setMetrics(new ArrayList<>(metrics));
        }
        if (cubeDesc != null && cubeDesc.getLast_modified() == null) {
            cubeDesc.setLast_modified(dm.getLast_modified());
        }
        KylinDataModelDesc desc = new KylinDataModelDesc();
        desc.setModels(Collections.singletonList(dm));
        return desc;
    }

    KylinColumnDesc getColumnDesc(String project, byte[] auth) {
        String url = API_TABLES_AND_COLUMNS.replace("{project}", enc(project));
        String content = get(url, auth, ACCEPT_JSON);
        try {
            return parseColumnDesc(content);
        } catch (Exception e) {
            log.error("The http call KYLIN 5 API [tables_and_columns] get exception, response body:{}", content);
            throw new SemanticException("Http: getColumnDescByHttp get exception", e, ErrorCode.FETCH_KYLIN_DIMS_ERROR);
        }
    }

    static KylinColumnDesc parseColumnDesc(String content) {
        Object parsed = JSON.parse(content);
        JSONObject wrapper;
        if (parsed instanceof JSONArray) {
            // Kylin 5 /api/tables_and_columns returns a bare list (no envelope)
            wrapper = new JSONObject().fluentPut("data", parsed);
        } else {
            wrapper = (JSONObject) parsed;
            validateRespSuccess(wrapper);
        }
        return wrapper.toJavaObject(KylinColumnDesc.class);
    }

    @Override
    public Map<String, Long> getHighCardinalityDimension(ConnectionInfo connInfo) throws SemanticException {
        Map<String, Long> result = new ConcurrentHashMap<>();
        String url = API_TABLES_EXT.replace("{project}", enc(connInfo.getProject()))
                .replace("{size}", String.valueOf(pageSize()));
        String content = doHttpCall(url, auth(connInfo));
        try {
            JSONObject json = parseEnvelope(content);
            for (JSONObject table : objects(listOf(json.get(SemanticConstants.SUC_DATA), "tables"))) {
                String db = table.getString("database");
                String name = table.getString("name");
                JSONObject cardinality = table.getJSONObject("cardinality");
                if (cardinality == null) {
                    continue;
                }
                for (String col : cardinality.keySet()) {
                    Long v = cardinality.getLong(col);
                    result.put("[" + db + "].[" + name + "].[" + col + "]", v == null ? 0L : v);
                }
            }
        } catch (Exception e) {
            // cardinality only feeds the optional high-cardinality query rejecter rule
            log.warn("Cannot read table cardinality from Kylin 5, high cardinality rule disabled. body:{}", content, e);
        }
        return result;
    }

    // ---------------------------------------------------- users & groups

    @Override
    public List<KylinUserInfo> getUsers(ConnectionInfo connInfo) throws SemanticException {
        String content = doHttpCall(API_USERS.replace("{size}", String.valueOf(userPageSize())), auth(connInfo));
        try {
            JSONArray users = parseEnvelope(content).getJSONObject(SemanticConstants.SUC_DATA).getJSONArray("users");
            return users == null ? Collections.emptyList() : users.toJavaList(KylinUserInfo.class);
        } catch (Exception e) {
            log.error("The http call KYLIN 5 API [users] get exception, response body:{}", content);
            throw new SemanticException("Http: getKylinUsers get exception", ErrorCode.FETCH_KYLIN_USER_LIST_ERROR);
        }
    }

    @Override
    public List<String> getGroups(ConnectionInfo connInfo) throws SemanticException {
        String content = get(API_GROUPS.replace("{size}", String.valueOf(userPageSize())), auth(connInfo), ACCEPT_V4);
        JSONObject json = JSON.parseObject(content);
        if (json == null || json.get(SemanticConstants.SUC_DATA) == null) {
            return Collections.emptyList();
        }
        Object data = json.get(SemanticConstants.SUC_DATA);
        List<String> groups = new ArrayList<>();
        for (Object o : listOf(data, "value")) {
            groups.add(o instanceof Map ? String.valueOf(((Map<?, ?>) o).get("group_name")) : String.valueOf(o));
        }
        return groups;
    }

    @Override
    public List<String> getUserAuthorities(ConnectionInfo connInfo, String username) throws SemanticException {
        String url = API_USER_BY_NAME.replace("{size}", String.valueOf(userPageSize())).replace("{name}", enc(username));
        String content = doHttpCall(url, auth(connInfo));
        List<String> authorities = new LinkedList<>();
        try {
            JSONArray users = parseEnvelope(content).getJSONObject(SemanticConstants.SUC_DATA).getJSONArray("users");
            for (JSONObject user : objects(users)) {
                if (!username.equalsIgnoreCase(user.getString("username"))) {
                    continue;
                }
                for (JSONObject authority : objects(user.getJSONArray("authorities"))) {
                    authorities.add(authority.getString("authority"));
                }
            }
        } catch (Exception e) {
            log.error("The http call KYLIN 5 API [user authorities] get exception, response body:{}", content, e);
            throw new SemanticException("Http: getKylinUsrAuthority get exception", e, ErrorCode.FETCH_KYLIN_USER_INFO_ERROR);
        }
        return authorities;
    }

    @Override
    public List<String> getGroupsByProject(ConnectionInfo connInfo) {
        return projectSids(connInfo, "grantedAuthority");
    }

    @Override
    public List<String> getUsersByProject(ConnectionInfo connInfo) {
        Set<String> result = new LinkedHashSet<>();
        // members of ROLE_ADMIN can access every project
        for (KylinUserInfo user : getUsers(connInfo)) {
            if (user.getAuthorities() != null && user.getAuthorities().stream()
                    .anyMatch(a -> SemanticConstants.ROLE_ADMIN.equalsIgnoreCase(a.getAuthority()))) {
                result.add(user.getUsername().toUpperCase());
            }
        }
        for (String u : projectSids(connInfo, "principal")) {
            result.add(u.toUpperCase());
        }
        return new ArrayList<>(result);
    }

    private List<String> projectSids(ConnectionInfo connInfo, String type) {
        String url = API_PROJECT_ACCESS.replace("{project}", enc(connInfo.getProject()))
                .replace("{size}", String.valueOf(userPageSize()));
        String content = doHttpCall(url, auth(connInfo));
        try {
            JSONObject json = parseEnvelope(content);
            List<String> names = new ArrayList<>();
            for (JSONObject entry : objects(listOf(json.get(SemanticConstants.SUC_DATA), "sids"))) {
                JSONObject sid = entry.getJSONObject("sid");
                if (sid != null && sid.get(type) != null) {
                    names.add(sid.getString(type));
                }
            }
            return names;
        } catch (Exception e) {
            log.error("The http call KYLIN 5 API [project access] get exception, response body:{}", content);
            throw new SemanticException("Http: getProjectInstance get exception", ErrorCode.FETCH_KYLIN_ACCESS_INFO_ERROR);
        }
    }

    /**
     * Kylin 5 table/column/row ACLs (AclTCR) are not synchronised yet: every table of the dataset is visible in
     * the MDX schema. Data-level ACL is still enforced by Kylin when the query runs as the end user.
     * See PLAN.md "ACL gaps".
     */
    @Override
    public AclProjectModel getAclProjectModel(ConnectionInfo connInfo, String type, String name, List<String> tables) {
        AclProjectModel projectModel = new AclProjectModel(type, name, Objects.requireNonNull(connInfo.getProject()));
        for (String table : tables) {
            projectModel.setModel(table, new AclTableModel(table));
        }
        return projectModel;
    }

    // ------------------------------------------------------------ helpers

    private static byte[] auth(ConnectionInfo connInfo) {
        return Utils.encodeAuthentication(connInfo.getUser(), connInfo.getPassword());
    }

    /** Accepts either a bare list or an object wrapping the list under {@code key}. */
    static List<Object> listOf(Object data, String key) {
        if (data instanceof List) {
            return new JSONArray((List<?>) data);
        }
        if (data instanceof Map) {
            Object v = ((Map<?, ?>) data).get(key);
            if (v instanceof List) {
                return new JSONArray((List<?>) v);
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    static List<JSONObject> objects(List<?> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<JSONObject> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof JSONObject) {
                out.add((JSONObject) o);
            } else if (o instanceof Map) {
                out.add(new JSONObject((Map<String, ?>) o));
            }
        }
        return out;
    }
}
