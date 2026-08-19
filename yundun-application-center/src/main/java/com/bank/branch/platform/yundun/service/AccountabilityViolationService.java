package com.bank.branch.platform.yundun.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationDTO;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.entity.AccountabilityViolation;
import com.bank.branch.platform.yundun.enums.YundunErrorCode;
import com.bank.branch.platform.yundun.mapper.AccountabilityViolationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 人员违规问责信息应用服务。 */
@Service
@RequiredArgsConstructor
public class AccountabilityViolationService {

    private static final int EXPORT_LIMIT = 5000;
    private final AccountabilityViolationMapper mapper;

    /** 按全部可搜索字段分页查询。 */
    public IPage<AccountabilityViolationDTO> page(AccountabilityViolationQuery query) {
        IPage<AccountabilityViolation> page = mapper.selectPage(
                new Page<>(query.normalizedPageNo(), query.normalizedPageSize()),
                ViolationQueryBuilder.accountability(query));
        return page.convert(entity -> toDto(entity, true));
    }

    /** 查询单条人员违规记录。 */
    public AccountabilityViolationDTO get(Long id) {
        return toDto(required(id), false);
    }

    /** 新增人员违规记录。 */
    @Transactional(rollbackFor = Exception.class)
    public AccountabilityViolationDTO create(AccountabilityViolationSaveReq req) {
        AccountabilityViolation entity = toEntity(req);
        entity.setInUse(1);
        mapper.insert(entity);
        return toDto(mapper.selectById(entity.getId()), false);
    }

    /** 修改人员违规记录。 */
    @Transactional(rollbackFor = Exception.class)
    public AccountabilityViolationDTO update(Long id, AccountabilityViolationSaveReq req) {
        AccountabilityViolation entity = required(id);
        BeanUtils.copyProperties(req, entity, "inUse");
        entity.setId(id);
        mapper.updateById(entity);
        return toDto(mapper.selectById(id), false);
    }

    /** 批量软删除人员违规记录（将是否可用改为否）。 */
    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        ids.forEach(this::required);
        return mapper.deleteBatchIds(ids);
    }

    /** 获取同步导出行，超过上限时拒绝导出。 */
    public List<AccountabilityViolationSaveReq> exportRows(AccountabilityViolationQuery query, List<Long> ids) {
        List<AccountabilityViolation> rows;
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > EXPORT_LIMIT) {
                throw error(YundunErrorCode.EXPORT_LIMIT);
            }
            // 选中导出同样由服务端限定可用记录，不能依赖前端传入状态。
            QueryWrapper<AccountabilityViolation> wrapper = new QueryWrapper<>();
            wrapper.in("id", ids).eq("in_use", 1);
            rows = mapper.selectList(wrapper);
        } else {
            QueryWrapper<AccountabilityViolation> wrapper = ViolationQueryBuilder.accountability(query)
                    .eq("in_use", 1);
            wrapper.last("LIMIT " + (EXPORT_LIMIT + 1));
            rows = mapper.selectList(wrapper);
        }
        List<AccountabilityViolation> availableRows = rows.stream()
                .filter(row -> Objects.equals(row.getInUse(), 1))
                .toList();
        if (availableRows.size() > EXPORT_LIMIT) {
            throw error(YundunErrorCode.EXPORT_LIMIT);
        }
        return availableRows.stream().map(this::toMaskedSaveReq).toList();
    }

    /** 导入人员违规 Excel 行，先校验总量，再按最多 500 条一批写入。 */
    @Transactional(rollbackFor = Exception.class)
    public int importRows(List<AccountabilityViolationSaveReq> rows) {
        validateImportRows(rows);
        List<AccountabilityViolation> entities = rows.stream()
                .map(this::toImportEntity)
                .toList();
        for (int fromIndex = 0; fromIndex < entities.size(); fromIndex += ViolationImportBatchPolicy.INSERT_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + ViolationImportBatchPolicy.INSERT_BATCH_SIZE, entities.size());
            mapper.insertBatch(entities.subList(fromIndex, toIndex));
        }
        return rows.size();
    }

    private void validateImportRows(List<AccountabilityViolationSaveReq> rows) {
        if (rows == null || rows.isEmpty()) {
            throw error(YundunErrorCode.IMPORT_INVALID);
        }
        if (rows.size() > ViolationImportBatchPolicy.IMPORT_LIMIT) {
            throw error(YundunErrorCode.IMPORT_LIMIT);
        }
        if (rows.stream().anyMatch(row -> row == null
                || row.getWorkNumber() == null || row.getWorkNumber().isBlank())) {
            throw error(YundunErrorCode.IMPORT_INVALID);
        }
    }

    private AccountabilityViolation toImportEntity(AccountabilityViolationSaveReq req) {
        AccountabilityViolation entity = toEntity(req);
        entity.setInUse(1);
        return entity;
    }

    private AccountabilityViolation required(Long id) {
        AccountabilityViolation entity = mapper.selectById(id);
        if (entity == null) {
            throw error(YundunErrorCode.ACCOUNTABILITY_NOT_FOUND);
        }
        return entity;
    }

    private AccountabilityViolation toEntity(AccountabilityViolationSaveReq req) {
        AccountabilityViolation entity = new AccountabilityViolation();
        BeanUtils.copyProperties(req, entity);
        return entity;
    }

    private AccountabilityViolationDTO toDto(AccountabilityViolation entity, boolean maskDisplayedFields) {
        AccountabilityViolationDTO dto = new AccountabilityViolationDTO();
        BeanUtils.copyProperties(entity, dto);
        if (maskDisplayedFields) {
            dto.setName(ViolationDataMasker.maskName(dto.getName()));
        }
        return dto;
    }

    private AccountabilityViolationSaveReq toMaskedSaveReq(AccountabilityViolation entity) {
        AccountabilityViolationSaveReq req = new AccountabilityViolationSaveReq();
        BeanUtils.copyProperties(entity, req);
        req.setName(ViolationDataMasker.maskName(req.getName()));
        req.setIdNumber(ViolationDataMasker.maskIdNumber(req.getIdNumber()));
        return req;
    }

    private BizException error(YundunErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }
}
