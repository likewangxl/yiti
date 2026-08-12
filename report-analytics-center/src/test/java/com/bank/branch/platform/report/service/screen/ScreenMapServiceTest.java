package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 复合地图发布落位、GCJ02 与旧 schema 兼容测试。 */
class ScreenMapServiceTest {

    private final ScreenMapService service = new ScreenMapService();

    private String canvas(String schemaVersion, String satellites) {
        return "{\"components\":[{\"component\":\"MapCenter\",\"propValue\":{"
                + "\"schemaVersion\":" + schemaVersion + ",\"mode\":\"XIAN_COMPOSITE\","
                + "\"baseRegion\":\"XIAN_OUTLINE\",\"localSelector\":{"
                + "\"cityCode\":\"610100\",\"operatingLevel\":\"PRIMARY\"},"
                + "\"satelliteNodes\":" + satellites + ","
                + "\"disclaimer\":\"组织分布示意，非地理比例\"}}]}";
    }

    private Map<String, OrgProfileDTO> profiles() {
        Map<String, OrgProfileDTO> profiles = new LinkedHashMap<>();
        profiles.put("LOCAL", profile("LOCAL", "610100", "108.950000", "34.260000", "GCJ02"));
        profiles.put("128", profile("128", "710000", "107.000000", "35.000000", "GCJ02"));
        profiles.put("191", profile("191", "710001", "107.100000", "35.100000", "GCJ02"));
        profiles.put("169", profile("169", "710002", "107.200000", "35.200000", "GCJ02"));
        profiles.put("129", profile("129", "710003", "107.300000", "35.300000", "GCJ02"));
        return profiles;
    }

    private OrgProfileDTO profile(String code, String city, String lng, String lat, String coordSys) {
        OrgProfileDTO profile = new OrgProfileDTO();
        profile.setOrgCode(code);
        profile.setOrgName(code);
        profile.setStatus("ACTIVE");
        profile.setOperatingLevel("PRIMARY");
        profile.setCityCode(city);
        profile.setLng(new BigDecimal(lng));
        profile.setLat(new BigDecimal(lat));
        profile.setCoordSys(coordSys);
        return profile;
    }

    private String fourSatellites() {
        return "[{\"orgCode\":\"128\",\"anchor\":\"LEFT\"},"
                + "{\"orgCode\":\"191\",\"anchor\":\"RIGHT\"},"
                + "{\"orgCode\":\"169\",\"anchor\":\"TOP\"},"
                + "{\"orgCode\":\"129\",\"anchor\":\"FAR_TOP\"}]";
    }

    private String mapNode(String schemaVersion, String satellites) {
        String root = canvas(schemaVersion, satellites);
        return root.substring(root.indexOf('[') + 1, root.lastIndexOf(']'));
    }

    @Test
    void publishAndRender_placesEveryPrimaryExactlyOnce_andKeepsGcj02() {
        String json = canvas("2", fourSatellites());
        Map<String, OrgProfileDTO> profiles = profiles();
        Set<String> members = profiles.keySet();

        service.validateForPublish(json, members, profiles);
        var packageDto = service.render(service.findV2MapConfig(json), members, profiles);

        assertThat(packageDto.getSchemaVersion()).isEqualTo(2);
        assertThat(packageDto.getLocalPoints()).extracting("orgCode").containsExactly("LOCAL");
        assertThat(packageDto.getLocalPoints().get(0).getCoordSys()).isEqualTo("GCJ02");
        assertThat(packageDto.getSatelliteNodes()).extracting("anchor")
                .containsExactlyInAnyOrder("LEFT", "RIGHT", "TOP", "FAR_TOP");
    }

    @Test
    void publish_duplicateAnchorOrUnplacedPrimary_failsClosed() {
        String duplicateAnchor = "[{\"orgCode\":\"128\",\"anchor\":\"LEFT\"},"
                + "{\"orgCode\":\"191\",\"anchor\":\"LEFT\"},"
                + "{\"orgCode\":\"169\",\"anchor\":\"TOP\"},"
                + "{\"orgCode\":\"129\",\"anchor\":\"FAR_TOP\"}]";
        assertThatThrownBy(() -> service.validateForPublish(canvas("2", duplicateAnchor),
                profiles().keySet(), profiles()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID.getCode());

        String missingPrimary = "[{\"orgCode\":\"128\",\"anchor\":\"LEFT\"},"
                + "{\"orgCode\":\"191\",\"anchor\":\"RIGHT\"},"
                + "{\"orgCode\":\"169\",\"anchor\":\"TOP\"},"
                + "{\"orgCode\":\"129\",\"anchor\":\"FAR_TOP\"}]";
        Map<String, OrgProfileDTO> withUnplaced = profiles();
        withUnplaced.put("LOCAL_2", profile("LOCAL_2", "620000", "108.960000", "34.270000", "GCJ02"));
        assertThatThrownBy(() -> service.validateForPublish(canvas("2", missingPrimary),
                withUnplaced.keySet(), withUnplaced))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID.getCode());
    }

    @Test
    void render_invalidLocalCoordinateDoesNotSilentlyOmitPrimary() {
        Map<String, OrgProfileDTO> profiles = profiles();
        profiles.get("LOCAL").setCoordSys("WGS84");
        String json = canvas("2", fourSatellites());

        assertThatThrownBy(() -> service.render(service.findV2MapConfig(json), profiles.keySet(), profiles))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_MAP_INVALID.getCode());
    }

    @Test
    void schemaVersionOneRemainsCompatible_andUnknownVersionIsRejected() {
        String v1 = "{\"components\":[{\"component\":\"MapCenter\",\"propValue\":{"
                + "\"schemaVersion\":1}}]}";
        assertThat(service.hasV2Map(v1)).isFalse();
        assertThat(service.findV2MapConfig(v1).isMissingNode()).isTrue();

        String v3 = canvas("3", fourSatellites());
        assertThatThrownBy(() -> service.validateDraftStructure(v3))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_MAP_INVALID.getCode());
    }

    @Test
    void validatesEveryMapCenterAndRequiresFixedBusinessCodeToAnchorMapping() {
        String firstValidSecondUnknown = "{\"components\":["
                + mapNode("2", fourSatellites()) + ","
                + "{\"component\":\"MapCenter\",\"propValue\":{\"schemaVersion\":99}}]}";

        assertThatThrownBy(() -> service.validateDraftStructure(firstValidSecondUnknown))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_MAP_INVALID.getCode());

        String swapped = "[{\"orgCode\":\"128\",\"anchor\":\"RIGHT\"},"
                + "{\"orgCode\":\"191\",\"anchor\":\"LEFT\"},"
                + "{\"orgCode\":\"169\",\"anchor\":\"TOP\"},"
                + "{\"orgCode\":\"129\",\"anchor\":\"FAR_TOP\"}]";
        assertThatThrownBy(() -> service.validateForPublish(canvas("2", swapped), profiles().keySet(), profiles()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_MAP_PLACEMENT_INVALID.getCode());
    }
}
