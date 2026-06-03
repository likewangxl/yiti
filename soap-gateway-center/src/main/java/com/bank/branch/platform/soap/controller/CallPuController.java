package com.bank.branch.platform.soap.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.controller.dto.CustInfoData;
import com.bank.branch.platform.soap.controller.dto.PerfListData;
import com.bank.branch.platform.soap.controller.dto.PerfListItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * callpu 渠道接收 controller（手机端绩效审批网关）。
 *
 * <p>统一入口 {@code POST /api/callpu}，按请求体 {@code RuleName} 分发到具体业务方法，
 * 返回手机端约定的 {@code {ReturnCd, RspMsg}} 信封（成功 ReturnCd="0"，失败 ReturnCd="99"，
 * 始终 HTTP 200，前端以 {@code response.ReturnCd == "0"} 判定成功）。</p>
 *
 * <p>已支持 RuleName：
 * <ul>
 *   <li>{@code PERF_LIST} —— 审批列表（{@link PerfApprovalQueryApi}）</li>
 *   <li>{@code PERF_SAVE} —— 新增分配关系调整申请（{@link PerfApprovalCmdApi}）</li>
 *   <li>{@code CASH_GETCUST_INFO} —— 客户号查名（{@link CustomerQueryApi}）</li>
 * </ul>
 *
 * <p>跨模块红线：仅通过对方 {@code *Api} 调用，不直接依赖其 service/mapper/entity。</p>
 *
 * <p>鉴权说明：外部渠道入口，身份认证由上游 callpu/ESB 完成（员工号随报文传入），
 * 故未挂 {@code @BizAuth}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/callpu")
@RequiredArgsConstructor
public class CallPuController {

    /** PERF_LIST 一次性拉取上限（手机端列表不分页，全量返回）。 */
    private static final int PERF_LIST_PAGE_SIZE = 100;

    private static final DateTimeFormatter APPLY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 申请类型（applyType）→ 客户类型（custType）/ bizKind 前缀。 */
    private static final Map<String, String> APPLY_TYPE_TO_CUST_TYPE = Map.of(
            "1", "CORP",
            "2", "RETAIL");

    /** 规则（applyRule）→ 分配维度（allocDim）。 */
    private static final Map<String, String> APPLY_RULE_TO_ALLOC_DIM = Map.of(
            "1", "ACCOUNT",
            "2", "RULE");

    /** 业务类型中文 → bizKind 后缀（对齐 BIZ_KIND 字典；结构性存款暂用 STRUCT_DEPOSIT，待业务确认）。 */
    private static final Map<String, String> BUSINESS_TYPE_TO_BIZ_SUFFIX = Map.of(
            "存款", "DEPOSIT",
            "贷款", "LOAN",
            "中收", "INTERMEDIATE",
            "大额存单", "NCD",
            "结构性存款", "STRUCT_DEPOSIT");

    private final PerfApprovalQueryApi perfApprovalQueryApi;
    private final PerfApprovalCmdApi perfApprovalCmdApi;
    private final CustomerQueryApi customerQueryApi;

    /**
     * callpu 统一分发入口。
     *
     * @param request callpu 请求体（含 RuleName 与业务参数）
     * @return 统一 callpu 响应信封
     */
    @PostMapping
    public CallPuResponse dispatch(@RequestBody CallPuRequest request) {
        String ruleName = request != null ? request.getRuleName() : null;
        log.info("[callpu] dispatch ruleName={}", ruleName);

        CallPuRequest.Parm parm = request != null ? request.getParm() : null;
        try {
            if ("PERF_LIST".equals(ruleName)) {
                return handlePerfList(parm);
            }
            if ("PERF_SAVE".equals(ruleName)) {
                return handlePerfSave(parm);
            }
            if ("CASH_GETCUST_INFO".equals(ruleName)) {
                return handleGetCustInfo(parm);
            }
            if ("PERF_RECALL".equals(ruleName)) {
                return handlePerfRecall(parm);
            }
            log.warn("[callpu] 不支持的 RuleName={}", ruleName);
            return CallPuResponse.fail("不支持的 RuleName: " + ruleName);
        } catch (Exception e) {
            // callpu 协议不抛异常，统一降级为失败信封，避免被全局异常处理改写成平台响应格式
            log.error("[callpu] 处理失败 ruleName={}", ruleName, e);
            return CallPuResponse.fail(e.getMessage());
        }
    }

    /**
     * PERF_LIST：按员工号拉取分配关系调整审批列表并装配为手机端载荷。
     */
    private CallPuResponse handlePerfList(CallPuRequest.Parm parm) {
        String empId = parm != null ? parm.getEmployeeNo() : null;
        if (!StringUtils.hasText(empId)) {
            return CallPuResponse.fail("员工号不能为空");
        }

        PageResult<AllocAdjustApprovalItemDTO> page =
                perfApprovalQueryApi.listAllocAdjustApprovals(empId, 1, PERF_LIST_PAGE_SIZE);

        List<PerfListItem> perfs = page.getRecords().stream()
                .map(this::toListItem)
                .toList();
        return CallPuResponse.ok(new PerfListData(perfs));
    }

    /**
     * PERF_SAVE：新增分配关系调整申请。
     *
     * <p>把 callpu 中文/数字码翻译为后端 enum，委托 {@link PerfApprovalCmdApi#submitAllocAdjust}。</p>
     */
    private CallPuResponse handlePerfSave(CallPuRequest.Parm parm) {
        if (parm == null) {
            return CallPuResponse.fail("参数不能为空");
        }
        String empId = parm.getEmployeeNo();
        if (!StringUtils.hasText(empId)) {
            return CallPuResponse.fail("员工号不能为空");
        }
        if (!StringUtils.hasText(parm.getCustId())) {
            return CallPuResponse.fail("客户号不能为空");
        }
        String custType = APPLY_TYPE_TO_CUST_TYPE.get(parm.getApplyType());
        if (custType == null) {
            return CallPuResponse.fail("申请类型不合法: " + parm.getApplyType());
        }
        String allocDim = APPLY_RULE_TO_ALLOC_DIM.get(parm.getApplyRule());
        if (allocDim == null) {
            return CallPuResponse.fail("规则不合法: " + parm.getApplyRule());
        }
        String bizKind = toBizKind(custType, parm.getBusinessType());
        if (bizKind == null) {
            return CallPuResponse.fail("业务类型不合法: " + parm.getBusinessType());
        }
        List<AllocAdjustSubmitCmd.Item> items = toSubmitItems(parm.getAllocaters());
        if (items.isEmpty()) {
            return CallPuResponse.fail("分配明细不能为空");
        }

        AllocAdjustSubmitCmd cmd = AllocAdjustSubmitCmd.builder()
                .custType(custType)
                .custId(parm.getCustId())
                .allocDim(allocDim)
                .bizKind(bizKind)
                .accountNo(parm.getIouNo())
                .reason(parm.getAdjustExplain())
                .applicant(empId)
                .items(items)
                .build();

        String applyId = perfApprovalCmdApi.submitAllocAdjust(cmd);
        // 成功载荷回传新申请编号（前端成功分支不读取，仅兜底）
        return CallPuResponse.ok(Map.of("perfAdjustNo", applyId == null ? "" : applyId));
    }

    /**
     * CASH_GETCUST_INFO：按客户编号查客户名称。
     */
    private CallPuResponse handleGetCustInfo(CallPuRequest.Parm parm) {
        String custNo = parm != null ? parm.getCustId() : null;
        if (!StringUtils.hasText(custNo)) {
            return CallPuResponse.fail("客户号不能为空");
        }
        Optional<CustomerDTO> customer = customerQueryApi.getCustomerByCustNo(custNo);
        if (customer.isEmpty()) {
            return CallPuResponse.fail("未查询到客户: " + custNo);
        }
        return CallPuResponse.ok(new CustInfoData(customer.get().getCustName()));
    }

    /**
     * PERF_RECALL：撤回分配关系调整申请（仅本人，带越权校验）。
     *
     * <p>operator 取报文 EmployeeNo，perfAdjustNo 即申请主键 id；撤回原因由后端兜底默认文案。
     * 越权 / 状态不可撤回等业务异常统一降级为失败信封（ReturnCd=99）。</p>
     */
    private CallPuResponse handlePerfRecall(CallPuRequest.Parm parm) {
        if (parm == null) {
            return CallPuResponse.fail("参数不能为空");
        }
        String empId = parm.getEmployeeNo();
        if (!StringUtils.hasText(empId)) {
            return CallPuResponse.fail("员工号不能为空");
        }
        if (!StringUtils.hasText(parm.getPerfAdjustNo())) {
            return CallPuResponse.fail("审批编号不能为空");
        }
        // reason 传 null，由 perf Facade 兜底默认文案
        perfApprovalCmdApi.withdrawAllocAdjust(parm.getPerfAdjustNo(), empId, null);
        return CallPuResponse.ok(null);
    }

    /** 将 perf 渠道 DTO 映射为手机端列表项（含时间格式化与状态码翻译）。 */
    private PerfListItem toListItem(AllocAdjustApprovalItemDTO dto) {
        return PerfListItem.builder()
                .perfAdjustNo(dto.getPerfAdjustNo())
                .applyFullname(dto.getApplyFullname())
                .custName(dto.getCustName())
                .applyTime(dto.getApplyTime() == null ? null : dto.getApplyTime().format(APPLY_TIME_FORMAT))
                .apprStatus(toApprStatus(dto.getStatus()))
                .build();
    }

    /**
     * 申请单状态 → 手机端审批状态码：
     * APPROVED→"1"、REJECTED→"2"、其余（IN_APPROVAL/DRAFT）→"0"。
     */
    private String toApprStatus(String status) {
        if ("APPROVED".equals(status)) {
            return "1";
        }
        if ("REJECTED".equals(status)) {
            return "2";
        }
        return "0";
    }

    /**
     * 组装 bizKind = 前缀(custType) + "_" + 业务类型后缀。
     *
     * <p>businessType 单选；历史可能为逗号串，取首项。无法识别返回 null。</p>
     */
    private String toBizKind(String custType, String businessType) {
        if (!StringUtils.hasText(businessType)) {
            return null;
        }
        String first = businessType.split(",")[0].trim();
        String suffix = BUSINESS_TYPE_TO_BIZ_SUFFIX.get(first);
        return suffix == null ? null : custType + "_" + suffix;
    }

    /** callpu allocaters → 提交 Item（工号/比例必填的行才纳入，比例非法抛出由 dispatch 兜底为失败）。 */
    private List<AllocAdjustSubmitCmd.Item> toSubmitItems(List<CallPuRequest.Allocater> allocaters) {
        List<AllocAdjustSubmitCmd.Item> items = new ArrayList<>();
        if (allocaters == null) {
            return items;
        }
        for (CallPuRequest.Allocater a : allocaters) {
            if (a == null || !StringUtils.hasText(a.getUsername()) || !StringUtils.hasText(a.getRatio())) {
                continue;
            }
            items.add(AllocAdjustSubmitCmd.Item.builder()
                    .empId(a.getUsername().trim())
                    .ratio(new BigDecimal(a.getRatio().trim()))
                    .remark(null)
                    .build());
        }
        return items;
    }
}
