package com.bank.branch.platform.yundun.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.yundun.dto.CreditViolationDTO;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.bank.branch.platform.yundun.enums.YundunErrorCode;
import com.bank.branch.platform.yundun.mapper.CreditViolationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 信贷风险责任认定信息应用服务。 */
@Service
@RequiredArgsConstructor
public class CreditViolationService {

    private static final int EXPORT_LIMIT = 5000;
    private final CreditViolationMapper mapper;

    /** 按全部可搜索字段分页查询。 */
    public IPage<CreditViolationDTO> page(CreditViolationQuery query) {
        IPage<CreditViolation> page = mapper.selectPage(
                new Page<>(query.normalizedPageNo(), query.normalizedPageSize()),
                ViolationQueryBuilder.credit(query));
        Map<String, String> clientGroupKeys = new LinkedHashMap<>();
        return page.convert(entity -> toListDto(entity, clientGroupKeys));
    }

    /** 查询单条信贷风险记录。 */
    public CreditViolationDTO get(Long id) {
        return toDto(required(id));
    }

    /** 新增信贷风险记录。 */
    @Transactional(rollbackFor = Exception.class)
    public CreditViolationDTO create(CreditViolationSaveReq req) {
        CreditViolation entity = toEntity(req);
        entity.setInUse(1);
        mapper.insert(entity);
        return toDto(mapper.selectById(entity.getId()));
    }

    /** 修改信贷风险记录。 */
    @Transactional(rollbackFor = Exception.class)
    public CreditViolationDTO update(Long id, CreditViolationSaveReq req) {
        CreditViolation entity = required(id);
        BeanUtils.copyProperties(req, entity, "inUse");
        entity.setId(id);
        mapper.updateById(entity);
        return toDto(mapper.selectById(id));
    }

    /** 批量软删除信贷风险记录（将是否可用改为否）。 */
    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        ids.forEach(this::required);
        return mapper.deleteBatchIds(ids);
    }

    /** 获取同步导出行，超过上限时拒绝导出。 */
    public List<CreditViolationSaveReq> exportRows(CreditViolationQuery query, List<Long> ids) {
        List<CreditViolation> rows;
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > EXPORT_LIMIT) {
                throw error(YundunErrorCode.EXPORT_LIMIT);
            }
            // 选中导出同样由服务端限定可用记录，不能依赖前端传入状态。
            QueryWrapper<CreditViolation> wrapper = new QueryWrapper<>();
            wrapper.in("id", ids).eq("in_use", 1);
            rows = mapper.selectList(wrapper);
        } else {
            QueryWrapper<CreditViolation> wrapper = ViolationQueryBuilder.credit(query)
                    .eq("in_use", 1);
            wrapper.last("LIMIT " + (EXPORT_LIMIT + 1));
            rows = mapper.selectList(wrapper);
        }
        List<CreditViolation> availableRows = rows.stream()
                .filter(row -> Objects.equals(row.getInUse(), 1))
                .toList();
        if (availableRows.size() > EXPORT_LIMIT) {
            throw error(YundunErrorCode.EXPORT_LIMIT);
        }
        return availableRows.stream().map(this::toMaskedSaveReq).toList();
    }

    /** 导入信贷风险 Excel 行，先校验总量，再按最多 500 条一批写入。 */
    @Transactional(rollbackFor = Exception.class)
    public int importRows(List<CreditViolationSaveReq> rows) {
        validateImportRows(rows);
        List<CreditViolation> entities = rows.stream()
                .map(this::toImportEntity)
                .toList();
        for (int fromIndex = 0; fromIndex < entities.size(); fromIndex += ViolationImportBatchPolicy.INSERT_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + ViolationImportBatchPolicy.INSERT_BATCH_SIZE, entities.size());
            mapper.insertBatch(entities.subList(fromIndex, toIndex));
        }
        return rows.size();
    }

    private void validateImportRows(List<CreditViolationSaveReq> rows) {
        if (rows == null || rows.isEmpty()) {
            throw error(YundunErrorCode.IMPORT_INVALID);
        }
        if (rows.size() > ViolationImportBatchPolicy.IMPORT_LIMIT) {
            throw error(YundunErrorCode.IMPORT_LIMIT);
        }
        if (rows.stream().anyMatch(row -> row == null
                || row.getEmployeeNumber() == null || row.getEmployeeNumber().isBlank())) {
            throw error(YundunErrorCode.IMPORT_INVALID);
        }
    }

    private CreditViolation toImportEntity(CreditViolationSaveReq req) {
        CreditViolation entity = toEntity(req);
        entity.setInUse(1);
        return entity;
    }

    private CreditViolation required(Long id) {
        CreditViolation entity = mapper.selectById(id);
        if (entity == null) {
            throw error(YundunErrorCode.CREDIT_NOT_FOUND);
        }
        return entity;
    }

    private CreditViolation toEntity(CreditViolationSaveReq req) {
        CreditViolation entity = new CreditViolation();
        BeanUtils.copyProperties(req, entity);
        return entity;
    }

    private CreditViolationDTO toDto(CreditViolation entity) {
        CreditViolationDTO dto = new CreditViolationDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private CreditViolationDTO toListDto(CreditViolation entity, Map<String, String> clientGroupKeys) {
        CreditViolationDTO dto = toDto(entity);
        String rawClientName = dto.getClientName();
        String normalizedClientName = rawClientName == null ? "" : rawClientName.trim();
        if (!normalizedClientName.isEmpty()) {
            dto.setClientNameGroupKey(clientGroupKeys.computeIfAbsent(normalizedClientName,
                    ignored -> "customer-" + (clientGroupKeys.size() + 1)));
        }
        dto.setResponsiblePersonName(ViolationDataMasker.maskName(dto.getResponsiblePersonName()));
        dto.setClientName(ViolationDataMasker.maskChineseName(rawClientName));
        return dto;
    }

    private CreditViolationSaveReq toMaskedSaveReq(CreditViolation entity) {
        CreditViolationSaveReq req = new CreditViolationSaveReq();
        BeanUtils.copyProperties(entity, req);
        req.setResponsiblePersonName(ViolationDataMasker.maskName(req.getResponsiblePersonName()));
        req.setClientName(ViolationDataMasker.maskChineseName(req.getClientName()));
        return req;
    }

    private BizException error(YundunErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }
}
