package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.report.dto.resp.ScreenMapLocalPointDTO;
import com.bank.branch.platform.report.dto.resp.ScreenMapRenderPackageDTO;
import com.bank.branch.platform.report.dto.resp.ScreenMapSatelliteNodeDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 复合地图结构校验与渲染包生成器。
 *
 * <p>schemaVersion=1 只读取历史 RPT_SCREEN_MAP_POINT；schemaVersion=2 的真实点位和机构
 * 成员完全来自 auth 画像/命名机构组，组件 JSON 只能声明模式、筛选器和示意锚点。</p>
 */
@Slf4j
@Component
public class ScreenMapService {

    public static final String XIAN_COMPOSITE = "XIAN_COMPOSITE";
    public static final String XIAN_OUTLINE = "XIAN_OUTLINE";
    private static final String GCJ02 = "GCJ02";
    private static final Set<String> ANCHORS = Set.of("LEFT", "RIGHT", "TOP", "FAR_TOP");
    /** 业务示意图不是可任意拖换的导航：机构编码与锚点保持一一固定。 */
    private static final Map<String, String> FIXED_SATELLITE_ANCHORS = Map.of(
            "128", "LEFT", "191", "RIGHT", "169", "TOP", "129", "FAR_TOP");
    private static final Set<String> SUBJECT_CITY = Set.of("610100");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 结构化扫描画布中 schemaVersion=2 的 MapCenter 配置。
     * 调用方渲染只消费唯一的一份配置，但扫描阶段会遍历所有组件，未知 schema 绝不回退为 v1。
     */
    public JsonNode findV2MapConfig(String canvasJson) {
        try {
            JsonNode root = objectMapper.readTree(canvasJson == null ? "{}" : canvasJson);
            List<JsonNode> configs = scanV2MapConfigs(root.path("components"));
            if (configs.size() > 1) {
                throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
            }
            return configs.isEmpty() ? MissingNode.getInstance() : configs.get(0);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_MAP_INVALID, e);
        }
    }

    /** 保存阶段校验地图结构；坐标可暂缺，但锚点必须唯一且免责声明不可隐藏。 */
    public void validateDraftStructure(String canvasJson) {
        for (JsonNode config : scanV2MapConfigs(readComponents(canvasJson))) {
            validateConfigShape(config, false);
        }
    }

    /**
     * 发布阶段检查组内所有有效 PRIMARY 机构唯一落位，并验证本地节点坐标/四个异地节点。
     */
    public void validateForPublish(String canvasJson,
                                   Set<String> memberCodes,
                                   Map<String, OrgProfileDTO> profiles) {
        List<JsonNode> configs = scanV2MapConfigs(readComponents(canvasJson));
        if (configs.isEmpty()) {
            return;
        }
        if (configs.size() != 1) {
            throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
        }
        JsonNode config = configs.get(0);
        validateConfigShape(config, true);
        if (memberCodes == null || memberCodes.isEmpty() || profiles == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }

        Set<String> satelliteCodes = new LinkedHashSet<>();
        for (JsonNode node : config.path("satelliteNodes")) {
            String code = text(node, "orgCode");
            if (!memberCodes.contains(code) || !satelliteCodes.add(code)) {
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
        }
        int primaryCount = 0;
        for (String code : memberCodes) {
            OrgProfileDTO profile = profiles.get(code);
            if (profile == null || !"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            if (satelliteCodes.contains(code)
                    && !"PRIMARY".equalsIgnoreCase(profile.getOperatingLevel())) {
                // 异地导航节点只能落在一级经营机构；不能用下属网点冒充分行节点。
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
            if (!"PRIMARY".equalsIgnoreCase(profile.getOperatingLevel())) {
                continue;
            }
            primaryCount++;
            boolean satellite = satelliteCodes.contains(code);
            boolean local = isXianLocal(profile, config);
            if (satellite == local) {
                // true/true = 重复落位；false/false = 未落位。
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
            if (local && !validCoordinate(profile)) {
                throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
            }
        }
        if (primaryCount == 0) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        // 当前业务约定四个异地导航位均需配置；不允许发布“半张示意图”。
        Set<String> anchors = new HashSet<>();
        for (JsonNode node : config.path("satelliteNodes")) {
            anchors.add(text(node, "anchor"));
        }
        if (!anchors.equals(ANCHORS)) {
            throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
        }
    }

    /** 依据授权机构画像生成服务端地图包。 */
    public ScreenMapRenderPackageDTO render(JsonNode config,
                                            Set<String> memberCodes,
                                            Map<String, OrgProfileDTO> profiles) {
        if (config == null || config.isMissingNode()) {
            return null;
        }
        validateConfigShape(config, true);
        ScreenMapRenderPackageDTO result = new ScreenMapRenderPackageDTO();
        validateRuntimeProfiles(config, memberCodes, profiles);
        result.setMode(textOr(config, "mode", XIAN_COMPOSITE));
        result.setBaseRegion(textOr(config, "baseRegion", XIAN_OUTLINE));
        result.setDisclaimer(text(config, "disclaimer"));

        JsonNode selector = config.path("localSelector");
        String cityCode = textOr(selector, "cityCode", "610100");
        String operatingLevel = textOr(selector, "operatingLevel", "PRIMARY");
        List<ScreenMapLocalPointDTO> localPoints = new ArrayList<>();
        for (String code : memberCodes) {
            OrgProfileDTO profile = profiles.get(code);
            if (profile == null || !"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            if (operatingLevel.equalsIgnoreCase(profile.getOperatingLevel())
                    && cityCode.equals(profile.getCityCode())) {
                ScreenMapLocalPointDTO point = new ScreenMapLocalPointDTO();
                point.setOrgCode(profile.getOrgCode());
                point.setOrgName(profile.getOrgName());
                point.setLng(profile.getLng());
                point.setLat(profile.getLat());
                point.setCoordSys(profile.getCoordSys());
                point.setTargetScreenCode(targetScreenForCode(config, code));
                localPoints.add(point);
            }
        }
        result.setLocalPoints(localPoints);

        List<ScreenMapSatelliteNodeDTO> satellites = new ArrayList<>();
        for (JsonNode node : config.path("satelliteNodes")) {
            String code = text(node, "orgCode");
            OrgProfileDTO profile = profiles.get(code);
            if (profile == null || !memberCodes.contains(code)
                    || !"PRIMARY".equalsIgnoreCase(profile.getOperatingLevel())) {
                throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
            }
            ScreenMapSatelliteNodeDTO dto = new ScreenMapSatelliteNodeDTO();
            dto.setOrgCode(code);
            dto.setOrgName(profile.getOrgName());
            dto.setAnchor(text(node, "anchor"));
            dto.setTargetScreenCode(targetScreen(node, code));
            satellites.add(dto);
        }
        result.setSatelliteNodes(satellites);
        return result;
    }

    /** 画布是否含 schemaVersion=2 MapCenter。 */
    public boolean hasV2Map(String canvasJson) {
        JsonNode n = findV2MapConfig(canvasJson);
        return n != null && !n.isMissingNode();
    }

    private JsonNode readComponents(String canvasJson) {
        try {
            return objectMapper.readTree(canvasJson == null ? "{}" : canvasJson).path("components");
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_MAP_INVALID, e);
        }
    }

    /** 递归扫描所有 MapCenter，而不是在第一个 v2 节点处提前返回。 */
    private List<JsonNode> scanV2MapConfigs(JsonNode components) {
        List<JsonNode> result = new ArrayList<>();
        collectV2MapConfigs(components, result);
        return result;
    }

    private void collectV2MapConfigs(JsonNode components, List<JsonNode> result) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode node : components) {
            if ("MapCenter".equals(node.path("component").asText())) {
                JsonNode prop = node.path("propValue");
                JsonNode version = prop.path("schemaVersion");
                // 没有版本号才是历史 v1；显式给了未知/非数字版本均为非法，不能静默降级。
                if (!version.isMissingNode() && !version.isNull()) {
                    if (!version.canConvertToInt() || (version.asInt() != 1 && version.asInt() != 2)) {
                        throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
                    }
                    if (version.asInt() == 2) {
                        result.add(prop);
                    }
                }
            }
            collectV2MapConfigs(node.path("children"), result);
        }
    }

    private void validateConfigShape(JsonNode config, boolean publish) {
        if (config == null || !config.isObject()
                || config.path("schemaVersion").asInt(-1) != 2
                || !XIAN_COMPOSITE.equals(text(config, "mode"))
                || !XIAN_OUTLINE.equals(text(config, "baseRegion"))
                || text(config, "disclaimer").isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
        }
        JsonNode local = config.path("localSelector");
        if (!local.isObject() || !"610100".equals(textOr(local, "cityCode", ""))
                || !"PRIMARY".equalsIgnoreCase(textOr(local, "operatingLevel", ""))) {
            throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
        }
        JsonNode satellites = config.path("satelliteNodes");
        if (!satellites.isArray()) {
            throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
        }
        Set<String> seenAnchor = new HashSet<>();
        Map<String, String> fixedAssignments = new LinkedHashMap<>();
        for (JsonNode node : satellites) {
            String anchor = text(node, "anchor");
            String code = text(node, "orgCode");
            if (!ANCHORS.contains(anchor) || !seenAnchor.add(anchor)
                    || code.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
            String expectedAnchor = FIXED_SATELLITE_ANCHORS.get(code);
            if (expectedAnchor != null && !expectedAnchor.equals(anchor)) {
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
            fixedAssignments.put(code, anchor);
        }
        if (publish && (!seenAnchor.equals(ANCHORS) || !fixedAssignments.equals(FIXED_SATELLITE_ANCHORS))) {
            throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
        }
    }

    private boolean isXianLocal(OrgProfileDTO profile, JsonNode config) {
        JsonNode selector = config.path("localSelector");
        return textOr(selector, "cityCode", "610100").equals(profile.getCityCode())
                && textOr(selector, "operatingLevel", "PRIMARY")
                .equalsIgnoreCase(profile.getOperatingLevel());
    }

    private boolean validCoordinate(OrgProfileDTO profile) {
        BigDecimal lng = profile.getLng();
        BigDecimal lat = profile.getLat();
        return lng != null && lat != null
                && lng.compareTo(BigDecimal.valueOf(-180)) >= 0
                && lng.compareTo(BigDecimal.valueOf(180)) <= 0
                && lat.compareTo(BigDecimal.valueOf(-90)) >= 0
                && lat.compareTo(BigDecimal.valueOf(90)) <= 0
                && GCJ02.equalsIgnoreCase(profile.getCoordSys());
    }

    /**
     * 运行时再次校验画像，不能因为发布后机构被停用或坐标系被改动而静默少画节点。
     * 发布校验与运行时校验都必须保证每个 PRIMARY 成员恰好落在本地或异地节点之一。
     */
    private void validateRuntimeProfiles(JsonNode config,
                                         Set<String> memberCodes,
                                         Map<String, OrgProfileDTO> profiles) {
        if (memberCodes == null || memberCodes.isEmpty() || profiles == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        Set<String> satelliteCodes = new LinkedHashSet<>();
        for (JsonNode node : config.path("satelliteNodes")) {
            String code = text(node, "orgCode");
            if (!memberCodes.contains(code) || !satelliteCodes.add(code)) {
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
        }
        int primaryCount = 0;
        for (String code : memberCodes) {
            OrgProfileDTO profile = profiles.get(code);
            if (profile == null || !"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            if (!"PRIMARY".equalsIgnoreCase(profile.getOperatingLevel())) {
                if (satelliteCodes.contains(code)) {
                    throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
                }
                continue;
            }
            primaryCount++;
            boolean satellite = satelliteCodes.contains(code);
            boolean local = isXianLocal(profile, config);
            if (satellite == local) {
                throw new RptException(RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID);
            }
            if (local && !validCoordinate(profile)) {
                throw new RptException(RptErrorCode.SCREEN_MAP_INVALID);
            }
        }
        if (primaryCount == 0) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
    }

    private String targetScreen(JsonNode node, String code) {
        return textOr(node, "targetScreenCode", "SCR_BRANCH");
    }

    private String targetScreenForCode(JsonNode config, String code) {
        for (JsonNode node : config.path("satelliteNodes")) {
            if (code.equals(text(node, "orgCode"))) {
                return targetScreen(node, code);
            }
        }
        return "SCR_BRANCH";
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("").trim();
    }

    private String textOr(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value.isBlank() ? fallback : value;
    }
}
