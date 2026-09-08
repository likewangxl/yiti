package com.bank.branch.platform.auth.location.audit;

import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.location.persistence.PtOrgLocation;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.common.web.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 机构位置独立结构化审计服务。审计失败由调用事务感知并回滚业务写入。 */
@Service
@RequiredArgsConstructor
public class OrgLocationAuditService {

    private static final String BIZ_TYPE_SYS_CONFIG = "SYS_CONFIG";
    private static final String TARGET = "PT_ORG_LOCATION";

    private final AuditLogHandler auditLogHandler;
    private final ObjectMapper objectMapper;

    /** 记录位置表写入前后快照。 */
    public void locationChanged(PtOrgLocation before, PtOrgLocation after, String reason) {
        String orgCode = after != null ? after.getOrgCode() : before == null ? null : before.getOrgCode();
        persist("ORG_LOCATION_CHANGE", orgCode, "/api/admin/org-locations/" + orgCode, "PUT",
                snapshot(before == null ? null : locationSnapshot(before)),
                snapshot(after == null ? null : locationSnapshot(after)), reason);
    }

    /** 记录外呼前的地址解析申请；不包含服务 Key 或完整请求 URL。 */
    public void geocodePreviewRequested(String orgCode, String address, String cityCode, String reason) {
        persist("ORG_LOCATION_GEOCODE_PREVIEW", orgCode,
                "/api/admin/org-locations/" + orgCode + "/geocode-preview", "POST",
                null, snapshot(new PreviewSnapshot(orgCode, address, cityCode)), reason);
    }

    private void persist(String action, String orgCode, String url, String method,
                         String before, String after, String reason) {
        if (!auditLogHandler.isPersistent()) {
            throw auditFailed(null);
        }
        DataScopeContext context = DataScopeContext.current();
        String operator = context == null ? null : context.getEmpId();
        String operatorOrg = context == null ? null : context.getOrgCode();
        if (operator == null || operator.isBlank()) {
            throw auditFailed(null);
        }
        try {
            auditLogHandler.handle(new AuditLogEvent(
                    action, TARGET, orgCode, orgCode,
                    operator, operatorOrg, Instant.now(),
                    null, null, before, after, reason,
                    BIZ_TYPE_SYS_CONFIG, url, method, null,
                    0L, traceId(), 200, null,
                    TARGET, orgCode, null, null));
        } catch (RuntimeException ex) {
            throw auditFailed(ex);
        }
    }

    private String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw auditFailed(ex);
        }
    }

    private static String traceId() {
        String traceId = MdcUtils.getTraceId();
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    private static BizException auditFailed(Throwable cause) {
        return cause == null
                ? new BizException(AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getCode(),
                AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getMessage())
                : new BizException(AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getCode(),
                AuthErrorCode.ORG_LOCATION_AUDIT_FAILED.getMessage(), cause);
    }

    private static LocationSnapshot locationSnapshot(PtOrgLocation location) {
        return new LocationSnapshot(location.getOrgCode(), location.getAddress(), location.getAddressSource(),
                location.getCityCode(), location.getLng(), location.getLat(), location.getCoordSys(),
                location.getProvider(), location.getMatchLevel(), location.getStatus(), location.getVersion(),
                location.getLocationSource());
    }

    private record LocationSnapshot(String orgCode, String address, String addressSource, String cityCode,
                                    BigDecimal lng, BigDecimal lat, String coordSys, String provider,
                                    String matchLevel, String status, Integer version, String locationSource) {
    }

    private record PreviewSnapshot(String orgCode, String address, String cityCode) {
    }
}
