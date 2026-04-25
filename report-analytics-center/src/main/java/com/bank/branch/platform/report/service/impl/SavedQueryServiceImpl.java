package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.resp.SavedQueryDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.SavedQuerySummaryDTO;
import com.bank.branch.platform.report.entity.RptSavedQuery;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptSavedQueryMapper;
import com.bank.branch.platform.report.service.SavedQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * SavedQueryService 实现 —— M1.4 列表 + 详情（Green）.
 *
 * <p>M1.5 会在本类追加 saveQuery / updateQuery / deleteQuery 三方法.
 *
 * <p>权限模型：
 * <ul>
 *   <li>列表/详情：empId 严格等值过滤（DataScope SELF）</li>
 *   <li>不存在 → RPT-40001 / 他人 → RPT-40002</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedQueryServiceImpl implements SavedQueryService {

    private final RptSavedQueryMapper mapper;

    private final CurrentUserApi currentUserApi;

    @Override
    public List<SavedQuerySummaryDTO> listMine(String dim) {
        String empId = currentUserApi.getCurrentEmpId();
        // dim 空字符串视同 null（不过滤），避免 Mapper xml 里的 dim != '' 误判
        String normalizedDim = (dim == null || dim.isEmpty()) ? null : dim;
        List<RptSavedQuery> entities = mapper.listByEmpAndDim(empId, normalizedDim);
        return entities.stream().map(this::toSummary).toList();
    }

    @Override
    public SavedQueryDetailRespDTO getDetail(String id) {
        RptSavedQuery e = mapper.selectById(id);
        if (e == null) {
            throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND);
        }
        String empId = currentUserApi.getCurrentEmpId();
        if (!Objects.equals(e.getEmpId(), empId)) {
            log.warn("[SavedQuery.getDetail] 越权读：empId={} 想看 {}（属 {}）", empId, id, e.getEmpId());
            throw new RptException(RptErrorCode.SAVED_QUERY_NO_ACCESS);
        }
        return toDetail(e);
    }

    private SavedQuerySummaryDTO toSummary(RptSavedQuery e) {
        return SavedQuerySummaryDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .dim(e.getDim())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }

    private SavedQueryDetailRespDTO toDetail(RptSavedQuery e) {
        return SavedQueryDetailRespDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .dim(e.getDim())
                .subjectIds(e.getSubjectIds())
                .metricCodes(e.getMetricCodes())
                .version(e.getVersion())
                .createdTime(e.getCreatedTime())
                .updatedTime(e.getUpdatedTime())
                .build();
    }
}
