package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 「原业绩分配」预览服务（供审批/新增调整申请页面展示）.
 *
 * <p>语义：对给定客户编号，分别取「按规则分配(RULE)」与「按账号分配(ACCOUNT)」两个维度下
 * <b>审批通过(APPROVED)的最后一条</b>分配关系调整申请，关联其调整明细，
 * 按员工工号补全员工名称（username + 中文姓名）与所属机构（机构号 + 机构名称）后返回。
 *
 * <p>与生产分配关系表 {@code cust_alloc_relation} 区别：本视图取的是「最近一次审批通过的调整申请快照」，
 * 不读 cust_alloc_relation，避免审批落地时序导致的展示口径漂移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustPreviewService {

    /** 预览覆盖的两个分配维度（顺序即展示顺序：规则在前、账号在后）. */
    private static final List<String> DIMS = List.of("RULE", "ACCOUNT");

    private final PerfAllocAdjustApplyMapper applyMapper;
    private final PerfAllocAdjustItemMapper itemMapper;

    /**
     * 查询客户「原业绩分配」预览（取审批通过的最后一条申请明细）.
     *
     * <p>维度过滤：
     * <ul>
     *   <li>{@code allocDim=ACCOUNT}（按账号分配）→ 只取 ACCOUNT 维度的最后一条审批通过申请；</li>
     *   <li>{@code allocDim=RULE} 或为空 → 取 RULE + ACCOUNT 两个维度（按规则分配场景沿用并列展示）。</li>
     * </ul>
     *
     * @param custId   客户编号（匹配 apply.cust_id）
     * @param allocDim 当前申请的分配维度（RULE / ACCOUNT / null）
     * @return 预览项列表（可能为空，不会返回 null）
     */
    public List<AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custId, String allocDim) {
        if (!StringUtils.hasText(custId)) {
            return new ArrayList<>();
        }
        // 按用户输入的客户编号(cust_id)匹配 apply（cust_no 字段已并入 cust_id，提交侧 apply.cust_id 恒有值），
        // 与提交去重 countInApprovalByCustAndDim(cust_id) 口径一致。

        // 按账号分配只查 ACCOUNT 维度；规则分配/未指定则取 RULE + ACCOUNT 两者
        List<String> dims = "ACCOUNT".equals(allocDim) ? List.of("ACCOUNT") : DIMS;

        // 收集各维度「最后一条审批通过申请」的明细行（保留维度 + 账号上下文）
        List<RowCtx> rows = new ArrayList<>();
        for (String dim : dims) {
            PerfAllocAdjustApply apply = applyMapper.selectLastApprovedByCustAndDim(custId, dim);
            if (apply == null) {
                continue;
            }
            // 只取新分配明细(NEW)：上次审批通过的「分配」是 NEW 行；
            // 排除该申请当时手工录入的原业绩分配(ORIGIN)，避免混入预览/会签名单。
            List<PerfAllocAdjustItem> items = itemMapper.selectByApplyIdAndKind(apply.getId(), "NEW");
            if (items == null || items.isEmpty()) {
                continue;
            }
            // RULE 维度账号留空；ACCOUNT 维度取申请上的账号
            String accountNo = "ACCOUNT".equals(dim) ? apply.getAccountNo() : null;
            for (PerfAllocAdjustItem it : items) {
                rows.add(new RowCtx(dim, accountNo, it));
            }
        }
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        // 直接读提交时快照在明细行上的员工/部门字段，不再关联 PT_USER/机构表
        List<AllocAdjustPreviewItemDTO> result = new ArrayList<>(rows.size());
        for (RowCtx r : rows) {
            PerfAllocAdjustItem it = r.item;
            AllocAdjustPreviewItemDTO dto = new AllocAdjustPreviewItemDTO();
            dto.setAllocDim(r.allocDim);
            dto.setAccountNo(r.accountNo);
            dto.setEmpId(it.getEmpId());
            // username 快照为空（历史旧数据）时回退展示工号，避免空白
            dto.setUsername(StringUtils.hasText(it.getUsername()) ? it.getUsername() : it.getEmpId());
            dto.setEmpChnName(it.getEmpChnName());
            dto.setOrgCode(it.getOrgCode());
            dto.setOrgName(it.getOrgName());
            dto.setRatio(it.getRatio());
            result.add(dto);
        }
        return result;
    }

    /** 明细行上下文：携带所属维度与账号（不可变内部载体）. */
    private record RowCtx(String allocDim, String accountNo, PerfAllocAdjustItem item) {
    }
}
