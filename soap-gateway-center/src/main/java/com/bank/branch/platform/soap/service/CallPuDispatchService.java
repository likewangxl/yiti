package com.bank.branch.platform.soap.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.CustStatQueryApi;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * callpu 分发服务（手机端绩效审批网关业务核心）。
 *
 * <p>按请求体 {@code RuleName} 分发到具体业务方法，返回手机端约定的 {@code {ReturnCd, RspMsg}} 信封
 * （成功 ReturnCd="0"，失败 ReturnCd="99"，{@link #dispatch} 内统一 try-catch，业务异常降级为失败信封）。</p>
 *
 * <p>本 service 同时供两条接入链路复用：
 * <ul>
 *   <li>HTTP 入口 {@code POST /api/callpu}（{@code CallPuController}）</li>
 *   <li>Netty SOAP 端点（{@code AxlryPrsRvrSysSvcEndpoint}，从 SOAP 信封拆出 callpu 载荷后委托本服务）</li>
 * </ul>
 *
 * <p>已支持 RuleName：
 * <ul>
 *   <li>{@code PERF_LIST} —— 审批列表（{@link PerfApprovalQueryApi}）</li>
 *   <li>{@code PERF_SAVE} —— 新增分配关系调整申请（{@link PerfApprovalCmdApi}）</li>
 *   <li>{@code CASH_GETCUST_INFO} —— 客户号查名（{@link CustStatQueryApi}，查 XAN_M98_CUST_STAT_SHOW3）</li>
 *   <li>{@code PERF_RECALL} —— 撤回申请（{@link PerfApprovalCmdApi}）</li>
 *   <li>{@code PERF_APPR} —— 审批申请（通过/驳回，{@link PerfApprovalCmdApi}）</li>
 * </ul>
 *
 * <p>跨模块红线：仅通过对方 {@code *Api} 调用，不直接依赖其 service/mapper/entity。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallPuDispatchService {

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
    private final CustStatQueryApi custStatQueryApi;
    private final UserApi userApi;

    /**
     * callpu 统一分发入口。
     *
     * @param request callpu 请求体（含 RuleName 与业务参数）
     * @return 统一 callpu 响应信封（始终非空，业务异常降级为失败信封）
     */
    public CallPuResponse dispatch(CallPuRequest request) {
        String ruleName = request != null ? request.getRuleName() : null;
        log.info("[callpu] dispatch ruleName={}", ruleName);

        CallPuRequest.Parm parm = request != null ? request.getParm() : null;
        try {
            if ("PERF_LIST".equals(ruleName)) {
                return handlePerfList(parm);
            }
            if ("PERF_MY_LIST".equals(ruleName)) {
                return handleMyList(parm);
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
            if ("PERF_APPR".equals(ruleName)) {
                return handlePerfApprove(parm);
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

        // 报文工号(USERNAME) → perf 所需 USER_ID
        String userId = resolveUserId(empId);
        PageResult<AllocAdjustApprovalItemDTO> page =
                perfApprovalQueryApi.listAllocAdjustApprovals(
                        userId, parm.getQueryStatus(), 1, PERF_LIST_PAGE_SIZE);

        List<PerfListItem> perfs = page.getRecords().stream()
                .map(this::toListItem)
                .toList();
        return CallPuResponse.ok(new PerfListData(perfs));
    }

    /**
     * PERF_MY_LIST：按申请人（createdBy）拉取"我的申请"列表，全状态（含已撤回）。
     */
    private CallPuResponse handleMyList(CallPuRequest.Parm parm) {
        String empId = parm != null ? parm.getEmployeeNo() : null;
        if (!StringUtils.hasText(empId)) {
            return CallPuResponse.fail("员工号不能为空");
        }
        String userId = resolveUserId(empId);
        PageResult<AllocAdjustApprovalItemDTO> page =
                perfApprovalQueryApi.listMyAllocAdjustApplications(userId, 1, PERF_LIST_PAGE_SIZE);
        List<PerfListItem> perfs = page.getRecords().stream().map(this::toListItem).toList();
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
        // 只保留工号/比例齐全的明细行（与原 toSubmitItems 跳过逻辑一致）
        List<CallPuRequest.Allocater> validAllocaters = validAllocaters(parm.getAllocaters());
        if (validAllocaters.isEmpty()) {
            return CallPuResponse.fail("分配明细不能为空");
        }

        // 申请人 + 各 allocater 的工号(USERNAME) 一次性解析为 perf 所需 USER_ID
        Set<String> empNos = new LinkedHashSet<>();
        empNos.add(empId);
        for (CallPuRequest.Allocater a : validAllocaters) {
            empNos.add(a.getUsername());
        }
        Map<String, String> userIdByEmpNo = resolveUserIds(empNos);

        List<AllocAdjustSubmitCmd.Item> items = new ArrayList<>(validAllocaters.size());
        for (CallPuRequest.Allocater a : validAllocaters) {
            items.add(AllocAdjustSubmitCmd.Item.builder()
                    .empId(userIdByEmpNo.get(a.getUsername().trim()))
                    .ratio(new BigDecimal(a.getRatio().trim()))
                    .remark(null)
                    .build());
        }

        AllocAdjustSubmitCmd cmd = AllocAdjustSubmitCmd.builder()
                .custType(custType)
                .custId(parm.getCustId())
                .allocDim(allocDim)
                .bizKind(bizKind)
                .accountNo(parm.getIouNo())
                .reason(parm.getAdjustExplain())
                .applicant(userIdByEmpNo.get(empId.trim()))
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
        // 客户号查名改走 perf 客户维度展示表 XAN_M98_CUST_STAT_SHOW3（CUST_ID → CUST_NAME，LIMIT 1）
        Optional<String> custName = custStatQueryApi.getCustNameByCustId(custNo);
        if (custName.isEmpty()) {
            return CallPuResponse.fail("未查询到客户: " + custNo);
        }
        return CallPuResponse.ok(new CustInfoData(custName.get()));
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
        // 报文工号(USERNAME) → perf 所需 USER_ID；reason 传 null，由 perf Facade 兜底默认文案
        String userId = resolveUserId(empId);
        perfApprovalCmdApi.withdrawAllocAdjust(parm.getPerfAdjustNo(), userId, null);
        return CallPuResponse.ok(null);
    }

    /**
     * PERF_APPR：审批分配关系调整申请（通过 / 驳回）。
     *
     * <p>审批人 empId 随报文传入（上游已认证），<b>审批权限由 perf 侧按审批人候选组 / 角色可见性校验</b>
     * （看不到该待办即无权），不依赖会话登录。apprStatus：1=同意 / 2=拒绝；apprOpinion 可空。
     * 越权 / 无待办 / 状态异常等业务异常统一降级为失败信封（ReturnCd=99）。</p>
     */
    private CallPuResponse handlePerfApprove(CallPuRequest.Parm parm) {
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
        String apprStatus = parm.getApprStatus();
        if (!"1".equals(apprStatus) && !"2".equals(apprStatus)) {
            return CallPuResponse.fail("审批状态不合法: " + apprStatus);
        }
        // 报文工号(USERNAME) → perf 所需 USER_ID
        String userId = resolveUserId(empId);
        perfApprovalCmdApi.approveAllocAdjust(parm.getPerfAdjustNo(), userId, apprStatus, parm.getApprOpinion());
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
        if ("WITHDRAWN".equals(status)) {
            return "3";
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

    /** 过滤出工号与比例齐全的分配明细行（其余跳过，与原 toSubmitItems 跳过逻辑一致）。 */
    private List<CallPuRequest.Allocater> validAllocaters(List<CallPuRequest.Allocater> allocaters) {
        List<CallPuRequest.Allocater> valid = new ArrayList<>();
        if (allocaters == null) {
            return valid;
        }
        for (CallPuRequest.Allocater a : allocaters) {
            if (a != null && StringUtils.hasText(a.getUsername()) && StringUtils.hasText(a.getRatio())) {
                valid.add(a);
            }
        }
        return valid;
    }

    /**
     * 把报文里的"工号"（{@code EmployeeNo} / allocater {@code username}，实为 {@code PT_USER.USERNAME}）
     * 批量解析为 perf 下游所需的 {@code USER_ID}（{@code PT_USER.USER_ID}）。
     *
     * <p>背景：外部渠道下发的是 8 位工号，落在 {@code PT_USER.USERNAME} 列；而 perf 的
     * applicant/operator/approver/empId 等参数物理使用的是 {@code USER_ID}（短代理键）。直接透传会导致
     * 查空 / 越权校验失败 / 工作流认领不到任务，故在网关入口统一转换。</p>
     *
     * <p>一次查库（{@link UserApi#getUsersByUsernames}，按 USERNAME 过滤，返回的 {@code UserDTO.empId}
     * 即 USER_ID）；任一工号在 {@code PT_USER} 查不到即抛 {@link IllegalArgumentException}，
     * 由 {@link #dispatch} 兜底为失败信封（ReturnCd=99）。</p>
     *
     * @param employeeNos 报文工号集合（可含空白/重复，内部过滤去重）
     * @return 工号 → USER_ID 映射（值非空）
     */
    private Map<String, String> resolveUserIds(Collection<String> employeeNos) {
        List<String> distinct = employeeNos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<UserDTO> users = userApi.getUsersByUsernames(distinct);
        Map<String, String> userIdByEmpNo = new HashMap<>();
        if (users != null) {
            for (UserDTO u : users) {
                if (u != null && StringUtils.hasText(u.getUsername()) && StringUtils.hasText(u.getEmpId())) {
                    // UserDTO.username = PT_USER.USERNAME(工号)，UserDTO.empId = PT_USER.USER_ID
                    userIdByEmpNo.put(u.getUsername(), u.getEmpId());
                }
            }
        }
        for (String no : distinct) {
            if (!userIdByEmpNo.containsKey(no)) {
                throw new IllegalArgumentException("未知员工号: " + no);
            }
        }
        return userIdByEmpNo;
    }

    /** 单个工号解析为 {@code USER_ID}（查不到抛异常，由 {@link #dispatch} 兜底为失败信封）。 */
    private String resolveUserId(String employeeNo) {
        return resolveUserIds(List.of(employeeNo)).get(employeeNo.trim());
    }
}
