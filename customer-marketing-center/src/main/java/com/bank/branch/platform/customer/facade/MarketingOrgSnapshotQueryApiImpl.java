package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.MarketingOrgSnapshotQueryApi;
import com.bank.branch.platform.customer.api.dto.MarketingOrgSnapshotDTO;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingOrgSnapshotRow;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 机构营销快照公共契约实现。
 *
 * <p>查询和状态解释留在客户营销域；外部模块只看到快照 DTO，不能越过本实现访问
 * 客户实体或 Mapper。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingOrgSnapshotQueryApiImpl implements MarketingOrgSnapshotQueryApi {

    private static final int MAX_ORG_COUNT = 500;
    private static final String CURRENT_STATE = "CURRENT_STATE";
    private static final String INVALID_ARGUMENT = "COMMON-40000";

    private final MarketingCustomerClaimMapper claimMapper;

    /**
     * 查询机构当前营销状态，并把每个请求机构补齐为一行。
     */
    @Override
    public List<MarketingOrgSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes, LocalDate asOfDate) {
        LinkedHashSet<String> normalizedCodes = normalizeOrgCodes(orgCodes);
        if (normalizedCodes.isEmpty()) {
            return List.of();
        }
        if (normalizedCodes.size() > MAX_ORG_COUNT) {
            throw new BizException(INVALID_ARGUMENT,
                    "参数超限: orgCodes 去重后最大 500，当前: " + normalizedCodes.size());
        }

        LocalDate sourceAsOfDate = LocalDate.now();
        LocalDate effectiveAsOfDate = asOfDate == null ? sourceAsOfDate : asOfDate;
        if (effectiveAsOfDate.isAfter(sourceAsOfDate)) {
            throw new BizException(INVALID_ARGUMENT, "参数错误: asOfDate 不能晚于当前日期");
        }

        List<String> requestedCodes = new ArrayList<>(normalizedCodes);
        log.debug("[MarketingOrgSnapshotQueryApiImpl.batchQueryOrgSnapshots] orgCodeCount={}, asOfDate={}",
                requestedCodes.size(), effectiveAsOfDate);
        List<MarketingOrgSnapshotRow> rows;
        try {
            rows = claimMapper.selectOrgMarketingSnapshots(requestedCodes);
        } catch (RuntimeException ex) {
            log.error("[MarketingOrgSnapshotQueryApiImpl.batchQueryOrgSnapshots] mapper failed, "
                            + "orgCodeCount={}, asOfDate={}, exceptionType={}",
                    requestedCodes.size(), effectiveAsOfDate, ex.getClass().getName());
            throw aggregateFailure();
        }
        Map<String, MarketingOrgSnapshotRow> rowByOrg = validateAndIndexRows(rows, normalizedCodes);

        List<MarketingOrgSnapshotDTO> result = new ArrayList<>(requestedCodes.size());
        for (String orgCode : requestedCodes) {
            MarketingOrgSnapshotRow row = rowByOrg.get(orgCode);
            result.add(MarketingOrgSnapshotDTO.builder()
                    .orgCode(orgCode)
                    .validCustomerCount(row.getValidCustomerCount())
                    .pendingFollowUpTaskCount(row.getPendingFollowUpTaskCount())
                    .asOfDate(effectiveAsOfDate)
                    .sourceAsOfDate(sourceAsOfDate)
                    .sourceUpdatedAt(row.getSourceUpdatedAt())
                    .sourceMode(CURRENT_STATE)
                    .build());
        }
        return result;
    }

    private LinkedHashSet<String> normalizeOrgCodes(List<String> orgCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (orgCodes == null) {
            return normalized;
        }
        for (String orgCode : orgCodes) {
            if (orgCode == null) {
                continue;
            }
            String trimmed = orgCode.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private Map<String, MarketingOrgSnapshotRow> validateAndIndexRows(List<MarketingOrgSnapshotRow> rows,
                                                                        LinkedHashSet<String> requestedCodes) {
        if (rows == null || rows.size() != requestedCodes.size()) {
            throw aggregateFailure();
        }
        Map<String, MarketingOrgSnapshotRow> indexed = new HashMap<>();
        for (MarketingOrgSnapshotRow row : rows) {
            if (row == null || row.getOrgCode() == null) {
                throw aggregateFailure();
            }
            String orgCode = row.getOrgCode().trim();
            if (orgCode.isEmpty() || !requestedCodes.contains(orgCode)
                    || row.getValidCustomerCount() == null
                    || row.getPendingFollowUpTaskCount() == null
                    || row.getValidCustomerCount() < 0
                    || row.getPendingFollowUpTaskCount() < 0
                    || indexed.containsKey(orgCode)) {
                throw aggregateFailure();
            }
            indexed.put(orgCode, row);
        }
        if (indexed.size() != requestedCodes.size()) {
            throw aggregateFailure();
        }
        return indexed;
    }

    private BizException aggregateFailure() {
        return new BizException(CustomerErrorCode.INTERNAL_ERROR.getCode(),
                "机构营销快照聚合结果异常");
    }
}
