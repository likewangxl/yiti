package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.SavedQuerySaveReqDTO;
import com.bank.branch.platform.report.dto.req.SavedQueryUpdateReqDTO;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    /** 每用户最多保存 10 条方案（业务规则，超限静默删旧）. */
    private static final int MAX_PER_EMP = 10;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveQuery(SavedQuerySaveReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();

        // 1) 计数 → 超 10 删最旧（单事务）
        int cnt = mapper.countByEmpId(empId);
        if (cnt >= MAX_PER_EMP) {
            String oldestId = mapper.findOldestId(empId);
            if (oldestId != null) {
                mapper.deleteById(oldestId);
                log.info("[SavedQuery.save] 用户 {} 已达 {} 条上限，自动删最旧 {}", empId, MAX_PER_EMP, oldestId);
            }
        }

        // 2) 插新记录
        RptSavedQuery e = new RptSavedQuery();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setEmpId(empId);
        e.setName(req.getName());
        e.setDim(req.getDim());
        e.setSubjectIds(req.getSubjectIds());
        e.setMetricCodes(req.getMetricCodes());
        e.setVersion(0);
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedTime(now);
        e.setUpdatedTime(now);
        mapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuery(String id, SavedQueryUpdateReqDTO req) {
        // 1) 读取并做权限校验：不存在 / 他人
        RptSavedQuery existing = mapper.selectById(id);
        if (existing == null) {
            throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND);
        }
        String empId = currentUserApi.getCurrentEmpId();
        if (!Objects.equals(existing.getEmpId(), empId)) {
            log.warn("[SavedQuery.update] 越权写：empId={} 想改 {}（属 {}）", empId, id, existing.getEmpId());
            throw new RptException(RptErrorCode.SAVED_QUERY_NO_ACCESS);
        }

        // 2) 乐观锁更新（仅当 version 匹配）
        RptSavedQuery patch = new RptSavedQuery();
        patch.setId(id);
        patch.setName(req.getName());
        patch.setDim(req.getDim());
        patch.setSubjectIds(req.getSubjectIds());
        patch.setMetricCodes(req.getMetricCodes());
        int rows = mapper.updateWithOptimisticLock(patch, req.getExpectedVersion());
        if (rows == 0) {
            log.warn("[SavedQuery.update] 乐观锁冲突：id={} expectedVersion={} db={}",
                    id, req.getExpectedVersion(), existing.getVersion());
            // 乐观锁冲突归并到 NO_ACCESS（无独立错误码，前端引导用户重新拉取）
            throw new RptException(RptErrorCode.SAVED_QUERY_NO_ACCESS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuery(String id) {
        RptSavedQuery existing = mapper.selectById(id);
        if (existing == null) {
            throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND);
        }
        String empId = currentUserApi.getCurrentEmpId();
        if (!Objects.equals(existing.getEmpId(), empId)) {
            log.warn("[SavedQuery.delete] 越权删：empId={} 想删 {}（属 {}）", empId, id, existing.getEmpId());
            throw new RptException(RptErrorCode.SAVED_QUERY_NO_ACCESS);
        }
        mapper.deleteById(id);
    }
}
