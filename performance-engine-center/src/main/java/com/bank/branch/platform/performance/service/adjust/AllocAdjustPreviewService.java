package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final CustomerQueryApi customerQueryApi;
    private final UserApi userApi;

    /**
     * 查询客户「原业绩分配」预览（RULE + ACCOUNT 各取审批通过的最后一条申请明细）.
     *
     * @param custNo 客户编号（业务编号；内部按编号解析为客户主键后匹配 apply.cust_id）
     * @return 预览项列表（可能为空，不会返回 null）
     */
    public List<AllocAdjustPreviewItemDTO> getLastApprovedAllocPreview(String custNo) {
        if (!StringUtils.hasText(custNo)) {
            return new ArrayList<>();
        }
        // 与提交入口同语义：custNo → 内部客户主键（解析不到时回退为 custNo），保证能命中 apply.cust_id
        String internalCustId = customerQueryApi.getCustomerByCustNo(custNo)
                .map(CustomerDTO::getId)
                .orElse(custNo);

        // 收集两个维度「最后一条审批通过申请」的明细行（保留维度 + 账号上下文）
        List<RowCtx> rows = new ArrayList<>();
        for (String dim : DIMS) {
            PerfAllocAdjustApply apply = applyMapper.selectLastApprovedByCustAndDim(internalCustId, dim);
            if (apply == null) {
                continue;
            }
            List<PerfAllocAdjustItem> items = itemMapper.selectByApplyId(apply.getId());
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

        // 批量解析员工（工号 → username/中文名/机构号/机构名），规避逐行 DB 往返
        Map<String, UserDTO> userMap = resolveUsers(rows);

        List<AllocAdjustPreviewItemDTO> result = new ArrayList<>(rows.size());
        for (RowCtx r : rows) {
            String empId = r.item.getEmpId();
            UserDTO u = (empId != null) ? userMap.get(empId) : null;

            AllocAdjustPreviewItemDTO dto = new AllocAdjustPreviewItemDTO();
            dto.setAllocDim(r.allocDim);
            dto.setAccountNo(r.accountNo);
            dto.setEmpId(empId);
            // username 解析不到时回退展示工号，避免空白
            dto.setUsername((u != null && StringUtils.hasText(u.getUsername())) ? u.getUsername() : empId);
            dto.setEmpChnName(u != null ? u.getDisplayName() : null);
            dto.setOrgCode(u != null ? u.getMainOrgCode() : null);
            dto.setOrgName(u != null ? u.getMainOrgName() : null);
            dto.setRatio(r.item.getRatio());
            result.add(dto);
        }
        return result;
    }

    /**
     * 批量解析员工信息，Key=原始 emp_id 取值（可能是工号，也可能是登录名）.
     *
     * <p>{@code item.emp_id} 的历史取值并不统一：既有工号（PT_USER.USER_ID，如 E10001），
     * 也有登录名（PT_USER.USERNAME，如 rm_zhang）。因此先按工号解析，未命中的 token 再按登录名兜底，
     * 保证两种存法都能拿到 username / 中文姓名 / 机构号 / 机构名称。
     */
    private Map<String, UserDTO> resolveUsers(List<RowCtx> rows) {
        // 去重保序的 emp_id 集合
        Map<String, Boolean> distinct = new LinkedHashMap<>();
        for (RowCtx r : rows) {
            if (StringUtils.hasText(r.item.getEmpId())) {
                distinct.putIfAbsent(r.item.getEmpId(), Boolean.TRUE);
            }
        }
        Map<String, UserDTO> userMap = new HashMap<>();
        if (distinct.isEmpty()) {
            return userMap;
        }

        // 1) 按工号(USER_ID)解析
        List<UserDTO> byEmpId = userApi.getUserByEmpIds(new ArrayList<>(distinct.keySet()));
        if (byEmpId != null) {
            for (UserDTO u : byEmpId) {
                if (u != null && u.getEmpId() != null) {
                    userMap.put(u.getEmpId(), u);
                }
            }
        }

        // 2) 工号未命中的 token，再按登录名(USERNAME)兜底解析
        List<String> remaining = new ArrayList<>();
        for (String token : distinct.keySet()) {
            if (!userMap.containsKey(token)) {
                remaining.add(token);
            }
        }
        if (!remaining.isEmpty()) {
            List<UserDTO> byUsername = userApi.getUsersByUsernames(remaining);
            if (byUsername != null) {
                for (UserDTO u : byUsername) {
                    if (u != null && u.getUsername() != null) {
                        // 按登录名回填（emp_id 存的就是登录名时命中）
                        userMap.putIfAbsent(u.getUsername(), u);
                    }
                }
            }
        }
        return userMap;
    }

    /** 明细行上下文：携带所属维度与账号（不可变内部载体）. */
    private record RowCtx(String allocDim, String accountNo, PerfAllocAdjustItem item) {
    }
}
