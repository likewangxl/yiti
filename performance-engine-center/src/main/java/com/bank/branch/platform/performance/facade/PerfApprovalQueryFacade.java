package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PerfApprovalQueryApi} 实现：合并「我的待审批」+「本人已审批」两个列表，
 * 补齐申请人姓名 / 客户名称，按申请时间倒序后内存分页。
 *
 * <p>权限逻辑零改动、零漂移：直接委托项目内已有的
 * {@link AllocAdjustTodoService#listMyTodos} 与 {@link AllocAdjustDoneService#listMyDones}，
 * empId 透传（外部渠道身份由上游 callpu 认证）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfApprovalQueryFacade implements PerfApprovalQueryApi {

    /** 默认每页大小（与手机端约定一致）。 */
    private static final int DEFAULT_PAGE_SIZE = 15;

    /**
     * 单用户待办 / 已办各自的拉取上限。
     * <p>因合并 + 排序需在内存完成，先各取上限再分页；超过上限会被截断并打 warn 日志。
     * 单个审批人的在办 + 已办分配调整量级通常远小于此值。</p>
     */
    private static final int FETCH_CAP = 500;

    private final AllocAdjustTodoService allocAdjustTodoService;
    private final AllocAdjustDoneService allocAdjustDoneService;
    private final AddressBookApi addressBookApi;
    private final CustomerQueryApi customerQueryApi;

    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(String empId, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        log.info("[PerfApprovalQueryFacade.listAllocAdjustApprovals] empId={}, pageNo={}, pageSize={}",
                empId, safePageNo, safePageSize);

        // 1. 拉取待办 + 已办（各自上限 FETCH_CAP，参数过滤全空 = 不限关键字/维度/日期）
        PageResult<AdjustTodoRespDTO> todoPage = allocAdjustTodoService.listMyTodos(
                empId, null, null, null, null, null, 1, FETCH_CAP);
        PageResult<AdjustTodoRespDTO> donePage = allocAdjustDoneService.listMyDones(
                empId, null, null, null, null, null, 1, FETCH_CAP);

        if (todoPage.getTotal() > FETCH_CAP || donePage.getTotal() > FETCH_CAP) {
            log.warn("[PerfApprovalQueryFacade] empId={} 审批记录超过拉取上限 {}，合并列表可能被截断"
                            + "（todoTotal={}, doneTotal={}）",
                    empId, FETCH_CAP, todoPage.getTotal(), donePage.getTotal());
        }

        // 2. 合并去重（同一申请若既在待办又在已办，待办优先：仍可操作，显示为待审批）+ 补齐名称
        Map<String, String> empNameCache = new HashMap<>();
        Map<String, String> custNameCache = new HashMap<>();
        Map<String, AllocAdjustApprovalItemDTO> byId = new LinkedHashMap<>();
        for (AdjustTodoRespDTO t : todoPage.getRecords()) {
            byId.put(t.getId(), toItem(t, "TODO", empNameCache, custNameCache));
        }
        for (AdjustTodoRespDTO d : donePage.getRecords()) {
            byId.putIfAbsent(d.getId(), toItem(d, "DONE", empNameCache, custNameCache));
        }

        // 3. 按申请时间倒序
        List<AllocAdjustApprovalItemDTO> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(AllocAdjustApprovalItemDTO::getApplyTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 4. 内存分页
        long total = merged.size();
        int from = (safePageNo - 1) * safePageSize;
        if (from >= merged.size()) {
            return PageResult.of(safePageNo, safePageSize, total, Collections.emptyList());
        }
        int to = Math.min(merged.size(), from + safePageSize);
        return PageResult.of(safePageNo, safePageSize, total, new ArrayList<>(merged.subList(from, to)));
    }

    /** 把待办/已办的内部 DTO 转为对外审批项，并补齐申请人姓名 / 客户名称。 */
    private AllocAdjustApprovalItemDTO toItem(AdjustTodoRespDTO src,
                                             String category,
                                             Map<String, String> empNameCache,
                                             Map<String, String> custNameCache) {
        return AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo(src.getId())
                .applyNo(src.getApplyNo())
                .custId(src.getCustId())
                .custName(resolveCustName(src.getCustId(), custNameCache))
                .createdBy(src.getCreatedBy())
                .applyFullname(resolveEmpName(src.getCreatedBy(), empNameCache))
                .applyTime(src.getCreatedTime())
                .status(src.getStatus())
                .category(category)
                .build();
    }

    /** 员工号 → 姓名（通讯录），按 empId 缓存（含查不到的 null，避免重复 RPC）。 */
    private String resolveEmpName(String empId, Map<String, String> cache) {
        if (empId == null || empId.isBlank()) {
            return null;
        }
        if (cache.containsKey(empId)) {
            return cache.get(empId);
        }
        String name = addressBookApi.getEmployee(empId).map(EmployeeDTO::getEmpName).orElse(null);
        cache.put(empId, name);
        return name;
    }

    /** 客户ID → 客户名称，按 custId 缓存（含查不到的 null，避免重复 RPC）。 */
    private String resolveCustName(String custId, Map<String, String> cache) {
        if (custId == null || custId.isBlank()) {
            return null;
        }
        if (cache.containsKey(custId)) {
            return cache.get(custId);
        }
        String name = customerQueryApi.getCustomer(custId).map(CustomerDTO::getCustName).orElse(null);
        cache.put(custId, name);
        return name;
    }
}
