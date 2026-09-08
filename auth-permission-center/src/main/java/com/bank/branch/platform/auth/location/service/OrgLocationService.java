package com.bank.branch.platform.auth.location.service;

import com.bank.branch.platform.auth.api.dto.OrgLocationCapabilitiesDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationGeocodeCandidateDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationGeocodePreviewReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.PtOrgProfile;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.location.OrgLocationProperties;
import com.bank.branch.platform.auth.location.audit.OrgLocationAuditService;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocodeCandidate;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocoder;
import com.bank.branch.platform.auth.location.persistence.OrgLocationStore;
import com.bank.branch.platform.auth.location.persistence.PtOrgLocation;
import com.bank.branch.platform.auth.location.security.OrgLocationCandidateTokenService;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 机构详细地址和坐标管理服务。
 *
 * <p>所有管理读写在服务层再次校验 DataScopeContext 和外部机构有效性。运行时补充
 * 只读位置表并且只返回已确认的坐标；位置存储关闭或缺表时不影响旧画像链路。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgLocationService {

    public static final String COORD_SYS_GCJ02 = "GCJ02";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_UNLOCATED = "UNLOCATED";
    public static final String STATUS_UNMAINTAINED = "UNMAINTAINED";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_GEOCODE_VERIFIED = "GEOCODE_VERIFIED";
    public static final String SOURCE_PROFILE = "PROFILE";
    public static final String PROVIDER_MANUAL = "MANUAL";
    public static final String PROVIDER_AMAP = "AMAP";

    private static final String ADDRESS_SOURCE_MANUAL = "MANUAL";
    private static final String ADDRESS_SOURCE_GEOCODE = "GEOCODE";
    private static final int MAX_ORG_CODE_LENGTH = 64;
    private static final int MAX_CITY_CODE_LENGTH = 12;
    private static final Set<String> PRECISE_MATCH_LEVELS = Set.of(
            "门牌号", "门址", "兴趣点", "POI", "HOUSE", "BUILDING", "STREET_NUMBER", "ENTRANCE");
    private static final Set<String> VERIFIED_SOURCES = Set.of(SOURCE_MANUAL, SOURCE_GEOCODE_VERIFIED);

    private final OrgLocationStore store;
    private final OrgLocationGeocoder geocoder;
    private final OrgLocationAuditService auditService;
    private final com.bank.branch.platform.auth.mapper.OrgMapper orgMapper;
    private final com.bank.branch.platform.auth.mapper.OrgProfileMapper orgProfileMapper;
    private final OrgLocationProperties properties;
    private final OrgLocationCandidateTokenService tokenService;
    private final Map<String, RateWindow> previewRateWindows = new ConcurrentHashMap<>();

    /** 返回位置存储和地址解析能力；返回体绝不包含密钥。 */
    public OrgLocationCapabilitiesDTO capabilities() {
        OrgLocationCapabilitiesDTO result = new OrgLocationCapabilitiesDTO();
        result.setStorageEnabled(properties.isStorageEnabled());
        if (!properties.isStorageEnabled()) {
            result.setStorageAvailable(false);
            result.setStorageReason("位置存储未启用");
        } else {
            boolean available = false;
            try {
                available = store != null && store.isAvailable();
            } catch (RuntimeException ex) {
                log.warn("[OrgLocationService.capabilities] storage probe failed", ex);
            }
            result.setStorageAvailable(available);
            result.setStorageReason(available ? null : "PT_ORG_LOCATION 表或仓储不可用");
        }

        result.setGeocodingEnabled(properties.isGeocodingEnabled());
        if (!properties.isGeocodingEnabled()) {
            result.setGeocodingAvailable(false);
            result.setGeocodingReason("地址解析未启用");
        } else if (!result.isStorageAvailable()) {
            result.setGeocodingAvailable(false);
            result.setGeocodingReason("位置存储能力不可用");
        } else if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            result.setGeocodingAvailable(false);
            result.setGeocodingReason("服务Key未配置");
        } else if (tokenService == null || !tokenService.isAvailable()) {
            result.setGeocodingAvailable(false);
            result.setGeocodingReason("候选签名密钥未配置");
        } else {
            boolean available = false;
            try {
                available = geocoder != null && geocoder.isAvailable();
            } catch (RuntimeException ex) {
                log.warn("[OrgLocationService.capabilities] geocoder probe failed", ex);
            }
            result.setGeocodingAvailable(available);
            result.setGeocodingReason(available ? null : "地址解析服务不可用");
        }
        return result;
    }

    /** 查询单个机构位置；schema 可用但无记录时返回明确的未维护对象。 */
    public OrgLocationDTO getLocation(String orgCode) {
        String code = normalizeOrgCode(orgCode);
        requireTargetAccess(code);
        ensureStorageAvailable();
        try {
            PtOrgLocation location = store.findByOrgCode(code);
            return location == null ? unmaintained(code) : toDto(location);
        } catch (RuntimeException ex) {
            throw storageUnavailable(ex);
        }
    }

    /**
     * 覆盖保存机构地址/坐标。人工坐标必须显式确认；候选确认只接受服务端签名令牌。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgLocationDTO updateLocation(String orgCode, OrgLocationUpdateReqDTO req) {
        String code = normalizeOrgCode(orgCode);
        requireTargetAccess(code);
        ensureStorageAvailable();
        if (req == null) {
            throw invalid("机构位置请求不能为空");
        }
        requireReason(req.getReason());
        rejectClientControlledFields(req);
        if (req.getVersion() == null || req.getVersion() < 0) {
            throw new BizException(AuthErrorCode.ORG_CONFIG_VERSION_REQUIRED.getCode(), "版本不能为空且不能为负数");
        }
        PtOrgLocation existing;
        try {
            existing = store.findByOrgCode(code);
        } catch (RuntimeException ex) {
            throw storageUnavailable(ex);
        }
        int currentVersion = existing == null || existing.getVersion() == null ? 0 : existing.getVersion();
        if (!Objects.equals(req.getVersion(), currentVersion)) {
            throw versionConflict();
        }

        String operator = currentOperator();
        CandidateSelection selection = selectCandidate(code, req, operator);
        String address = req.getAddress() == null
                ? existing == null ? null : existing.getAddress() : normalize(req.getAddress());
        String cityCode = req.getCityCode() == null
                ? existing == null ? null : normalize(existing.getCityCode()) : normalize(req.getCityCode());
        if (selection.tokenPayload() != null) {
            address = selection.tokenPayload().address();
            cityCode = selection.tokenPayload().cityCode();
        }
        if (hasText(address) && !hasText(cityCode)) {
            throw invalid("填写地址时必须提供城市编码");
        }
        if (hasText(cityCode) && cityCode.length() > MAX_CITY_CODE_LENGTH) {
            throw invalid("城市编码长度不能超过12");
        }
        if (hasText(cityCode) && !isStandardCityCode(cityCode)) {
            throw invalid("城市编码必须为6位行政区划编码");
        }
        PtOrgProfile profile = selection.requiresProfile() || hasText(address) || hasText(cityCode)
                ? activeProfile(code) : null;
        if (profile != null && hasText(cityCode) && hasText(profile.getCityCode())
                && !cityCode.equals(normalize(profile.getCityCode()))) {
            throw invalid("城市编码必须与有效机构画像一致");
        }
        if (selection.requiresProfile() || hasText(address) || hasText(cityCode)) {
            requireProfileCity(profile, cityCode);
        }

        PtOrgLocation entity = buildEntity(code, existing, req, selection, address, cityCode, operator);
        PtOrgLocation before = copy(existing);
        try {
            if (existing == null) {
                if (currentVersion != 0) {
                    throw versionConflict();
                }
                PtOrgLocation inserted = store.insert(entity);
                if (inserted == null) {
                    inserted = entity;
                }
                entity = inserted;
            } else if (!store.updateWithVersion(entity, currentVersion)) {
                throw versionConflict();
            }
        } catch (BizException ex) {
            throw ex;
        } catch (DuplicateKeyException ex) {
            // version=0 的并发首次插入与 CAS 更新一样表示版本竞争，而非位置存储失效。
            throw versionConflict();
        } catch (RuntimeException ex) {
            throw storageUnavailable(ex);
        }

        auditLocationChange(before, entity, req.getReason());
        return toDto(entity);
    }

    /**
     * 预览地址解析候选。审计记录成功后才允许外呼，预览本身不写位置表。
     */
    public List<OrgLocationGeocodeCandidateDTO> previewGeocode(
            String orgCode, OrgLocationGeocodePreviewReqDTO req) {
        String code = normalizeOrgCode(orgCode);
        requireTargetAccess(code);
        if (req == null) {
            throw invalid("地址解析预览请求不能为空");
        }
        requireReason(req.getReason());
        String address = normalize(req.getAddress());
        String cityCode = normalize(req.getCityCode());
        if (!hasText(address) || address.length() > 255) {
            throw invalid("地址不能为空且长度不能超过255");
        }
        if (!hasText(cityCode) || cityCode.length() > MAX_CITY_CODE_LENGTH) {
            throw invalid("城市编码不能为空且长度不能超过12");
        }
        if (!isStandardCityCode(cityCode)) {
            throw invalid("城市编码必须为6位行政区划编码");
        }
        PtOrgProfile profile = activeProfile(code);
        requireProfileCity(profile, cityCode);
        ensureStorageAvailable();
        ensureGeocodingAvailable();
        checkPreviewRateLimit(currentOperator(), code);

        auditGeocodePreview(code, address, cityCode, req.getReason());
        List<OrgLocationGeocodeCandidate> candidates;
        try {
            candidates = geocoder.geocode(address, cityCode);
        } catch (RuntimeException ex) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getCode(),
                    AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getMessage(), ex);
        }
        return toCandidateDtos(code, address, cityCode, candidates);
    }

    /**
     * 批量获取运行时可用位置。调用方传入的机构目录和有效画像是硬边界，避免越权或 N+1。
     */
    public Map<String, PtOrgLocation> findRuntimeLocations(Collection<String> orgCodes,
                                                            Map<String, OrgProfileDTO> activeProfiles) {
        if (!properties.isStorageEnabled() || store == null || activeProfiles == null
                || activeProfiles.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> requested = normalizeCodes(orgCodes);
        if (requested.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> eligible = requested.stream()
                .filter(activeProfiles::containsKey)
                .filter(code -> {
                    OrgProfileDTO profile = activeProfiles.get(code);
                    return profile != null && "ACTIVE".equalsIgnoreCase(profile.getStatus())
                            && same(code, profile.getOrgCode())
                            && isStandardCityCode(profile.getCityCode());
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (eligible.isEmpty()) {
            return Map.of();
        }
        try {
            if (!store.isAvailable()) {
                return Map.of();
            }
            List<PtOrgLocation> rows = store.findByOrgCodes(eligible);
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            Map<String, PtOrgLocation> result = new LinkedHashMap<>();
            for (PtOrgLocation row : rows) {
                if (row == null || !eligible.contains(row.getOrgCode())) {
                    continue;
                }
                OrgProfileDTO profile = activeProfiles.get(row.getOrgCode());
                if (validRuntimeLocation(row, profile)) {
                    // 返回独立副本，防止调用方意外修改存储对象。
                    PtOrgLocation runtime = copy(row);
                    // 运行时跨模块契约只携带坐标；详细地址不得从位置表进入画像链路。
                    runtime.setAddress(null);
                    runtime.setAddressSource(null);
                    runtime.setProvider(null);
                    runtime.setMatchLevel(null);
                    runtime.setCreatedBy(null);
                    runtime.setCreatedTime(null);
                    runtime.setUpdatedBy(null);
                    runtime.setUpdatedTime(null);
                    result.put(row.getOrgCode(), runtime);
                }
            }
            return result;
        } catch (RuntimeException ex) {
            log.warn("[OrgLocationService.findRuntimeLocations] fail close size={}", eligible.size(), ex);
            return Map.of();
        }
    }

    private List<OrgLocationGeocodeCandidateDTO> toCandidateDtos(String orgCode, String address,
                                                                  String cityCode,
                                                                  List<OrgLocationGeocodeCandidate> candidates) {
        List<OrgLocationGeocodeCandidateDTO> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        int max = Math.max(1, properties.getMaxCandidates());
        Set<String> seen = new LinkedHashSet<>();
        for (OrgLocationGeocodeCandidate candidate : candidates) {
            if (candidate == null || result.size() >= max || candidate.cityCode() == null
                    || !PROVIDER_AMAP.equalsIgnoreCase(candidate.provider())
                    || !cityCode.equals(normalize(candidate.cityCode()))
                    || !validCoordinates(candidate.lng(), candidate.lat())
                    || !COORD_SYS_GCJ02.equalsIgnoreCase(candidate.coordSys())) {
                continue;
            }
            String key = decimal(candidate.lng()) + "," + decimal(candidate.lat()) + "|"
                    + normalize(candidate.matchLevel());
            if (!seen.add(key)) {
                continue;
            }
            boolean precise = isPreciseMatchLevel(candidate.matchLevel());
            OrgLocationGeocodeCandidateDTO dto = new OrgLocationGeocodeCandidateDTO();
            dto.setAddress(address);
            dto.setFormattedAddress(candidate.formattedAddress());
            dto.setCityCode(cityCode);
            dto.setLng(candidate.lng());
            dto.setLat(candidate.lat());
            dto.setCoordSys(COORD_SYS_GCJ02);
            dto.setProvider(PROVIDER_AMAP);
            dto.setMatchLevel(candidate.matchLevel());
            dto.setVerificationAllowed(precise);
            dto.setVerificationReason(precise ? null : "匹配精度不足，不能确认 VERIFIED");
            if (precise && tokenService != null && tokenService.isAvailable()) {
                dto.setCandidateToken(tokenService.issue(currentOperator(), orgCode, address,
                        cityCode, candidate));
            }
            result.add(dto);
        }
        return result;
    }

    private CandidateSelection selectCandidate(String orgCode, OrgLocationUpdateReqDTO req,
                                               String operator) {
        String token = normalize(req.getCandidateToken());
        if (token != null) {
            if (tokenService == null) {
                throw candidateInvalid();
            }
            var payload = tokenService.verify(token, operator, orgCode)
                    .orElseThrow(OrgLocationService::candidateInvalid);
            OrgLocationGeocodeCandidate candidate = payload.candidate();
            if (!isPreciseMatchLevel(candidate.matchLevel())
                    || !validCoordinates(candidate.lng(), candidate.lat())
                    || !COORD_SYS_GCJ02.equalsIgnoreCase(candidate.coordSys())
                    || !PROVIDER_AMAP.equalsIgnoreCase(candidate.provider())) {
                throw candidateInvalid();
            }
            if (!same(candidate.cityCode(), payload.cityCode())) {
                throw candidateInvalid();
            }
            if (hasText(req.getAddress()) && !same(req.getAddress(), payload.address())) {
                throw candidateInvalid();
            }
            if (hasText(req.getCityCode()) && !same(req.getCityCode(), payload.cityCode())) {
                throw candidateInvalid();
            }
            return new CandidateSelection(candidate, payload, true, false);
        }
        boolean coordinatesProvided = req.getLng() != null || req.getLat() != null;
        if (coordinatesProvided) {
            if (!Boolean.TRUE.equals(req.getManualConfirmed())) {
                throw invalid("人工坐标必须明确确认");
            }
            if (!validCoordinates(req.getLng(), req.getLat())
                    || !COORD_SYS_GCJ02.equalsIgnoreCase(normalize(req.getCoordSys()))) {
                throw invalid("人工坐标必须是成对有效的 GCJ02 经纬度");
            }
            return new CandidateSelection(new OrgLocationGeocodeCandidate(
                    PROVIDER_MANUAL, normalize(req.getAddress()), normalize(req.getCityCode()),
                    req.getLng(), req.getLat(), COORD_SYS_GCJ02, "MANUAL"), null, true, true);
        }
        if (Boolean.TRUE.equals(req.getManualConfirmed())) {
            throw invalid("人工确认必须同时提供成对坐标");
        }
        return new CandidateSelection(null, null, false, false);
    }

    private PtOrgLocation buildEntity(String orgCode, PtOrgLocation existing,
                                      OrgLocationUpdateReqDTO req, CandidateSelection selection,
                                      String address, String cityCode, String operator) {
        PtOrgLocation entity = new PtOrgLocation();
        entity.setOrgCode(orgCode);
        entity.setAddress(address);
        entity.setCityCode(cityCode);
        entity.setVersion((existing == null || existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
        entity.setUpdatedBy(operator);
        entity.setUpdatedTime(LocalDateTime.now());
        if (existing != null) {
            entity.setCreatedBy(existing.getCreatedBy());
            entity.setCreatedTime(existing.getCreatedTime());
        } else {
            entity.setCreatedBy(operator);
            entity.setCreatedTime(entity.getUpdatedTime());
        }

        if (selection.candidate() != null && selection.fromToken()) {
            OrgLocationGeocodeCandidate c = selection.candidate();
            entity.setAddressSource(ADDRESS_SOURCE_GEOCODE);
            entity.setLng(c.lng());
            entity.setLat(c.lat());
            entity.setCoordSys(COORD_SYS_GCJ02);
            entity.setProvider(PROVIDER_AMAP);
            entity.setMatchLevel(c.matchLevel());
            entity.setStatus(STATUS_VERIFIED);
            entity.setLocationSource(SOURCE_GEOCODE_VERIFIED);
            return entity;
        }
        if (selection.candidate() != null && selection.fromManual()) {
            entity.setAddressSource(ADDRESS_SOURCE_MANUAL);
            entity.setLng(selection.candidate().lng());
            entity.setLat(selection.candidate().lat());
            entity.setCoordSys(COORD_SYS_GCJ02);
            entity.setProvider(PROVIDER_MANUAL);
            entity.setMatchLevel("MANUAL");
            entity.setStatus(STATUS_VERIFIED);
            entity.setLocationSource(SOURCE_MANUAL);
            return entity;
        }

        boolean addressChanged = existing != null
                && (!same(existing.getAddress(), address) || !same(existing.getCityCode(), cityCode));
        boolean clear = Boolean.TRUE.equals(req.getClearLocation())
                || (addressChanged && existing != null);
        if (!clear && existing != null) {
            entity.setAddressSource(existing.getAddressSource());
            entity.setLng(existing.getLng());
            entity.setLat(existing.getLat());
            entity.setCoordSys(existing.getCoordSys());
            entity.setProvider(existing.getProvider());
            entity.setMatchLevel(existing.getMatchLevel());
            entity.setStatus(existing.getStatus());
            entity.setLocationSource(existing.getLocationSource());
        } else {
            entity.setAddressSource(hasText(address) ? ADDRESS_SOURCE_MANUAL : null);
            entity.setStatus(STATUS_UNLOCATED);
            entity.setLocationSource(null);
        }
        return entity;
    }

    private void requireTargetAccess(String orgCode) {
        DataScopeContext context = DataScopeContext.current();
        if (context == null || !hasText(context.getEmpId())) {
            throw permissionDenied();
        }
        DataScopeType scope = context.getScope();
        boolean allowed = scope == DataScopeType.ALL
                || (scope == DataScopeType.ORG && same(context.getOrgCode(), orgCode))
                || (scope == DataScopeType.ORG_SUBTREE
                && (same(context.getOrgCode(), orgCode)
                || containsNormalized(context.getOrgSubtreeCodes(), orgCode)));
        if (!allowed) {
            throw permissionDenied();
        }
        requireActiveExternalOrg(orgCode);
    }

    private ExtOrgInfo requireActiveExternalOrg(String orgCode) {
        ExtOrgInfo external;
        try {
            external = orgMapper.selectByOrgCode(orgCode);
        } catch (RuntimeException ex) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getCode(),
                    AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getMessage(), ex);
        }
        if (external == null || !Integer.valueOf(0).equals(external.getOrganState())) {
            throw new BizException(AuthErrorCode.ORG_NOT_FOUND.getCode(), "机构不存在或已停用: " + orgCode);
        }
        return external;
    }

    private PtOrgProfile activeProfile(String orgCode) {
        PtOrgProfile profile;
        try {
            profile = orgProfileMapper.selectById(orgCode);
        } catch (RuntimeException ex) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getCode(),
                    AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getMessage(), ex);
        }
        if (profile == null || !"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
            throw invalid("机构有效画像不存在或未启用");
        }
        return profile;
    }

    private void requireProfileCity(PtOrgProfile profile, String cityCode) {
        if (profile == null || !hasText(profile.getCityCode())) {
            throw invalid("有效机构画像缺少城市编码，不能确认位置");
        }
        String profileCity = normalize(profile.getCityCode());
        if (!isStandardCityCode(profileCity) || !isStandardCityCode(cityCode)) {
            throw invalid("有效机构画像城市编码无效，不能确认位置");
        }
        if (!same(profileCity, cityCode)) {
            throw invalid("候选城市与有效机构画像不一致");
        }
    }

    private void ensureStorageAvailable() {
        if (!properties.isStorageEnabled()) {
            throw storageUnavailable(null);
        }
        if (store == null) {
            throw storageUnavailable(null);
        }
        try {
            if (!store.isAvailable()) {
                throw storageUnavailable(null);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw storageUnavailable(ex);
        }
    }

    private void ensureGeocodingAvailable() {
        if (!properties.isGeocodingEnabled()) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getCode(),
                    AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getMessage());
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || tokenService == null || !tokenService.isAvailable()
                || geocoder == null || !geocoder.isAvailable()) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getCode(),
                    AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getMessage());
        }
    }

    private void checkPreviewRateLimit(String operator, String orgCode) {
        int limit = Math.max(1, properties.getMaxPreviewRequestsPerMinute());
        long minute = System.currentTimeMillis() / 60000L;
        String key = operator + "|" + orgCode;
        RateWindow window = previewRateWindows.compute(key, (ignored, old) -> {
            if (old == null || old.minute() != minute) {
                return new RateWindow(minute, new AtomicInteger(1));
            }
            old.count().incrementAndGet();
            return old;
        });
        if (window.count().get() > limit) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_GEOCODING_UNAVAILABLE.getCode(),
                    "地址解析请求过于频繁，请稍后重试");
        }
    }

    private void rejectClientControlledFields(OrgLocationUpdateReqDTO req) {
        if (hasText(req.getAddressSource()) || hasText(req.getProvider())
                || hasText(req.getMatchLevel()) || hasText(req.getStatus())
                || hasText(req.getLocationSource())) {
            throw invalid("地址来源、供应商、匹配精度、状态和定位来源由服务端决定");
        }
    }

    private void auditLocationChange(PtOrgLocation before, PtOrgLocation after, String reason) {
        try {
            auditService.locationChanged(before, after, reason);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getCode(),
                    AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getMessage(), ex);
        }
    }

    private void auditGeocodePreview(String orgCode, String address, String cityCode, String reason) {
        try {
            auditService.geocodePreviewRequested(orgCode, address, cityCode, reason);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getCode(),
                    AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getMessage(), ex);
        }
    }

    private boolean validRuntimeLocation(PtOrgLocation row, OrgProfileDTO profile) {
        return row != null && profile != null && same(row.getOrgCode(), profile.getOrgCode())
                && isStandardCityCode(profile.getCityCode())
                && STATUS_VERIFIED.equalsIgnoreCase(row.getStatus())
                && row.getLocationSource() != null
                && VERIFIED_SOURCES.contains(row.getLocationSource().toUpperCase(Locale.ROOT))
                && isStandardCityCode(row.getCityCode())
                && same(row.getCityCode(), profile.getCityCode())
                && validCoordinates(row.getLng(), row.getLat())
                && COORD_SYS_GCJ02.equalsIgnoreCase(row.getCoordSys());
    }

    private static boolean isPreciseMatchLevel(String matchLevel) {
        if (matchLevel == null || matchLevel.isBlank()) {
            return false;
        }
        String normalized = matchLevel.trim();
        return PRECISE_MATCH_LEVELS.contains(normalized)
                || PRECISE_MATCH_LEVELS.contains(normalized.toUpperCase(Locale.ROOT));
    }

    private static boolean validCoordinates(BigDecimal lng, BigDecimal lat) {
        return lng != null && lat != null
                && lng.compareTo(BigDecimal.valueOf(-180)) >= 0
                && lng.compareTo(BigDecimal.valueOf(180)) <= 0
                && lat.compareTo(BigDecimal.valueOf(-90)) >= 0
                && lat.compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    private OrgLocationDTO toDto(PtOrgLocation entity) {
        OrgLocationDTO dto = new OrgLocationDTO();
        dto.setOrgCode(entity.getOrgCode());
        dto.setAddress(entity.getAddress());
        dto.setAddressSource(entity.getAddressSource());
        dto.setCityCode(entity.getCityCode());
        dto.setLng(entity.getLng());
        dto.setLat(entity.getLat());
        dto.setCoordSys(entity.getCoordSys());
        dto.setProvider(entity.getProvider());
        dto.setMatchLevel(entity.getMatchLevel());
        dto.setStatus(entity.getStatus());
        dto.setVersion(entity.getVersion());
        dto.setUpdatedTime(entity.getUpdatedTime());
        dto.setLocationSource(entity.getLocationSource());
        return dto;
    }

    private OrgLocationDTO unmaintained(String orgCode) {
        OrgLocationDTO dto = new OrgLocationDTO();
        dto.setOrgCode(orgCode);
        dto.setStatus(STATUS_UNMAINTAINED);
        dto.setVersion(0);
        return dto;
    }

    private static PtOrgLocation copy(PtOrgLocation source) {
        if (source == null) {
            return null;
        }
        PtOrgLocation copy = new PtOrgLocation();
        copy.setOrgCode(source.getOrgCode());
        copy.setAddress(source.getAddress());
        copy.setAddressSource(source.getAddressSource());
        copy.setCityCode(source.getCityCode());
        copy.setLng(source.getLng());
        copy.setLat(source.getLat());
        copy.setCoordSys(source.getCoordSys());
        copy.setProvider(source.getProvider());
        copy.setMatchLevel(source.getMatchLevel());
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setLocationSource(source.getLocationSource());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedTime(source.getCreatedTime());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedTime(source.getUpdatedTime());
        return copy;
    }

    private String normalizeOrgCode(String value) {
        String normalized = normalize(value);
        if (!hasText(normalized) || normalized.length() > MAX_ORG_CODE_LENGTH) {
            throw invalid("机构编码不能为空且长度不能超过64");
        }
        return normalized;
    }

    private static LinkedHashSet<String> normalizeCodes(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 位置补充和确认只接受标准六位行政区划编码，不按城市名称猜测污染值。 */
    private static boolean isStandardCityCode(String value) {
        return value != null && value.length() == 6
                && value.chars().allMatch(ch -> ch >= '0' && ch <= '9');
    }

    private static boolean containsNormalized(Collection<String> values, String expected) {
        if (values == null || expected == null) {
            return false;
        }
        for (String value : values) {
            if (same(value, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean same(String left, String right) {
        return Objects.equals(normalize(left), normalize(right));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String currentOperator() {
        DataScopeContext context = DataScopeContext.current();
        if (context == null || !hasText(context.getEmpId())) {
            throw permissionDenied();
        }
        return context.getEmpId().trim();
    }

    private static void requireReason(String reason) {
        if (!hasText(reason) || reason.trim().length() > 500) {
            throw new BizException(AuthErrorCode.HIGH_RISK_ACTION_MISSING_REASON.getCode(),
                    "操作原因不能为空且长度不能超过500字");
        }
    }

    private static BizException invalid(String message) {
        return new BizException(AuthErrorCode.ORG_LOCATION_INVALID.getCode(), message);
    }

    private static BizException permissionDenied() {
        return new BizException(AuthErrorCode.ORG_LOCATION_PERMISSION_DENIED.getCode(),
                AuthErrorCode.ORG_LOCATION_PERMISSION_DENIED.getMessage());
    }

    private static BizException candidateInvalid() {
        return new BizException(AuthErrorCode.ORG_LOCATION_CANDIDATE_INVALID.getCode(),
                AuthErrorCode.ORG_LOCATION_CANDIDATE_INVALID.getMessage());
    }

    private static BizException versionConflict() {
        return new BizException(AuthErrorCode.ORG_LOCATION_VERSION_CONFLICT.getCode(),
                AuthErrorCode.ORG_LOCATION_VERSION_CONFLICT.getMessage());
    }

    private static BizException storageUnavailable(Throwable cause) {
        return cause == null
                ? new BizException(AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getCode(),
                AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getMessage())
                : new BizException(AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getCode(),
                AuthErrorCode.ORG_LOCATION_STORAGE_UNAVAILABLE.getMessage(), cause);
    }

    private record CandidateSelection(OrgLocationGeocodeCandidate candidate,
                                      OrgLocationCandidateTokenService.OrgLocationTokenPayload tokenPayload,
                                      boolean requiresProfile, boolean fromManual) {
        private boolean fromToken() {
            return tokenPayload != null;
        }
    }

    private record RateWindow(long minute, AtomicInteger count) {
    }
}
