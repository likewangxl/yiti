package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 「原业绩分配」预览服务（供审批/新增/编辑/查看 调整申请页面展示）.
 *
 * <p>语义：对给定客户编号，取 {@code cust_alloc_relation} 中
 * <b>当前生效分配（is_original='2'）</b>作为「原业绩分配」反显数据。
 * 姓名 / 部门直接读分配关系表快照列（fullname / dept_no / dept_name），不再关联 PT_USER / 机构表。
 *
 * <p>维度过滤：
 * <ul>
 *   <li>{@code allocDim=ACCOUNT}（按账号分配）→ 只取 ACCOUNT 维度；</li>
 *   <li>{@code allocDim=RULE} 或为空 → 取 RULE + ACCOUNT 两个维度（RULE 在前）。</li>
 * </ul>
 *
 * <p>无当前生效分配时返回空列表（前端据此切到手工录入）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustPreviewService {

    private final CustAllocRelationMapper allocRelationMapper;

    /**
     * 查询客户「原业绩分配」预览（取 cust_alloc_relation 当前生效分配 is_original='2'）.
     *
     * @param custId   客户编号（匹配 cust_alloc_relation.cust_id）
     * @param allocDim 当前申请的分配维度（RULE / ACCOUNT / null）
     * @return 预览项列表（可能为空，不会返回 null）
     */
    public List<AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custId, String allocDim) {
        if (!StringUtils.hasText(custId)) {
            return new ArrayList<>();
        }
        List<CustAllocRelation> rows = allocRelationMapper.selectCurrentOriginalByCust(custId, allocDim);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<AllocAdjustPreviewItemDTO> result = new ArrayList<>(rows.size());
        for (CustAllocRelation rel : rows) {
            AllocAdjustPreviewItemDTO dto = new AllocAdjustPreviewItemDTO();
            dto.setAllocDim(rel.getAllocDim());
            dto.setAccountNo(rel.getAccountNo());
            dto.setEmpId(rel.getEmpId());
            // cust_alloc_relation 不单独存登录名，username 回退展示工号(emp_id)避免空白
            dto.setUsername(rel.getEmpId());
            dto.setEmpChnName(rel.getFullname());
            dto.setOrgCode(rel.getDeptNo());
            dto.setOrgName(rel.getDeptName());
            dto.setRatio(rel.getRatio());
            result.add(dto);
        }
        return result;
    }
}
