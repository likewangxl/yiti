package com.bank.branch.platform.soap.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustDetailDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.controller.dto.CustInfoData;
import com.bank.branch.platform.soap.controller.dto.DictItemData;
import com.bank.branch.platform.soap.controller.dto.OrigAllocData;
import com.bank.branch.platform.soap.controller.dto.PerfDetailData;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 *   <li>{@code CASH_GETCUST_INFO} —— 客户号查名（{@link CustStatQueryApi}，查客户主档 CUST_MASTER）</li>
 *   <li>{@code PERF_RECALL} —— 撤回申请（{@link PerfApprovalCmdApi}）</li>
 *   <li>{@code PERF_APPR} —— 审批申请（通过/驳回，{@link PerfApprovalCmdApi}）</li>
 *   <li>{@code SYS_DICT_ITEMS} —— 按字典类型查启用字典项（{@link DictApi}，业务类型选项 PERF_BIZ_KIND）</li>
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

    /** 业务类型字典编码（与 PC 管理端 PERF_BIZ_KIND 同源）。 */
    private static final String DICT_PERF_BIZ_KIND = "PERF_BIZ_KIND";

    private final PerfApprovalQueryApi perfApprovalQueryApi;
    private final PerfApprovalCmdApi perfApprovalCmdApi;
    private final CustStatQueryApi custStatQueryApi;
    private final UserApi userApi;
    private final AllocApi allocApi;
    private final DictApi dictApi;

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
            if ("PERF_ORIG_ALLOC".equals(ruleName)) {
                return handleOrigAlloc(parm);
            }
            if ("PERF_INFO".equals(ruleName)) {
                return handlePerfInfo(parm);
            }
            if ("SYS_DICT_ITEMS".equals(ruleName)) {
                return handleDictItems(parm);
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
        // 业务类型可多选：前端已传 PERF_BIZ_KIND 字典码逗号串（对齐 PC 存储口径），逐项校验合法后原样落库
        String bizKind = validateBizKindCsv(parm.getBusinessType());
        // 只保留工号/比例齐全的明细行（与原 toSubmitItems 跳过逻辑一致）
        List<CallPuRequest.Allocater> validAllocaters = validAllocaters(parm.getAllocaters());
        // 按 isOriginal 拆分：1=原业绩分配（→ originalAllocList / item_kind=ORIGIN），
        // 其余（2/缺省）=调整后新分配（→ items / item_kind=NEW），口径对齐管理端。
        List<CallPuRequest.Allocater> newAllocaters = new ArrayList<>();
        List<CallPuRequest.Allocater> origAllocaters = new ArrayList<>();
        for (CallPuRequest.Allocater a : validAllocaters) {
            if (a.getIsOriginal() != null && a.getIsOriginal() == 1) {
                origAllocaters.add(a);
            } else {
                newAllocaters.add(a);
            }
        }
        if (newAllocaters.isEmpty()) {
            return CallPuResponse.fail("分配明细不能为空");
        }

        // 申请人工号(USERNAME) → perf 所需 USER_ID（applicant/created_by 物理用短代理键）
        String applicantUserId = resolveUserId(empId);

        // 新分配 → items（NEW）。empId 直接落工号(USERNAME)，与 PC 管理端一致：
        // 审批通过后该工号原样写入 cust_alloc_relation.emp_id（perf 侧仅按工号补全姓名/部门，不改值）。
        List<AllocAdjustSubmitCmd.Item> items = new ArrayList<>(newAllocaters.size());
        for (CallPuRequest.Allocater a : newAllocaters) {
            items.add(AllocAdjustSubmitCmd.Item.builder()
                    .empId(a.getUsername().trim())
                    .ratio(new BigDecimal(a.getRatio().trim()))
                    .remark(null)
                    .build());
        }

        // 原分配 → originalAllocList（ORIGIN）；手机端只采集 工号/姓名/比例，
        // empId/username 均落工号(USERNAME)（seedOriginalAllocRelations 原样写 cust_alloc_relation.emp_id，
        // 口径对齐 PC 管理端）；fullname 存中文名快照，机构/账号留空由 perf 兜底。
        List<AllocAdjustSubmitCmd.OriginalItem> originalAllocList = new ArrayList<>(origAllocaters.size());
        for (CallPuRequest.Allocater a : origAllocaters) {
            originalAllocList.add(AllocAdjustSubmitCmd.OriginalItem.builder()
                    .empId(a.getUsername().trim())
                    .username(a.getUsername().trim())
                    .empChnName(a.getFullname())
                    .ratio(new BigDecimal(a.getRatio().trim()))
                    .build());
        }

        AllocAdjustSubmitCmd cmd = AllocAdjustSubmitCmd.builder()
                .custType(custType)
                .custId(parm.getCustId())
                .custName(parm.getCustName())
                .allocDim(allocDim)
                .bizKind(bizKind)
                .accountNo(parm.getIouNo())
                .reason(parm.getAdjustExplain())
                .applicant(applicantUserId)
                .items(items)
                .originalAllocList(originalAllocList)
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
        // 客户号查名改走客户主档 CUST_MASTER（cust_no → custName），与 PC 管理端
        // StatShowController#getCustMasterName 同口径；不再查旧统计表 XAN_M98_CUST_STAT_SHOW3
        Optional<String> custName = custStatQueryApi.getCustNameFromMaster(custNo);
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
        // routeTo：经办节点「下一步审批」选择（biz_dept_review→LEADER/OWNER；finance_review→LEADER/END），
        // 非经办节点 / 驳回时透传 null，由 perf Facade 按当前节点忽略或兜底默认链路。
        perfApprovalCmdApi.approveAllocAdjust(parm.getPerfAdjustNo(), userId, apprStatus,
                parm.getApprOpinion(), parm.getRouteTo());
        return CallPuResponse.ok(null);
    }

    /**
     * PERF_ORIG_ALLOC：只按客户号反查「最近一次审批通过」的原业绩分配关系，供新增页回显。
     *
     * <p>口径与 PC/管理端（report 模块 {@code AllocPreviewService}）一致：委托
     * {@link AllocApi#getLastApprovedAllocPreview(String, String)}，{@code allocDim=null}
     * 表示纯按 {@code cust_id} 跨 RULE + ACCOUNT 两个维度各取最后一条 status=APPROVED 申请并合并，
     * 不再依赖申请类型 / 业务类型。预览项的 {@code username} 即提交时快照的工号（PT_USER.USERNAME），
     * 无需再经 USER_ID 反查。</p>
     */
    private CallPuResponse handleOrigAlloc(CallPuRequest.Parm parm) {
        if (parm == null) {
            return CallPuResponse.fail("参数不能为空");
        }
        if (!StringUtils.hasText(parm.getCustId())) {
            return CallPuResponse.fail("客户号不能为空");
        }

        // allocDim 传 null：纯按客户号取两维度的「最近一次审批通过」分配关系
        List<AllocAdjustPreviewItemDTO> previewItems =
                allocApi.getLastApprovedAllocPreview(parm.getCustId(), null);
        if (previewItems == null || previewItems.isEmpty()) {
            return CallPuResponse.ok(new OrigAllocData(new ArrayList<>()));
        }

        List<OrigAllocData.OrigAllocItem> items = new ArrayList<>(previewItems.size());
        for (AllocAdjustPreviewItemDTO p : previewItems) {
            items.add(OrigAllocData.OrigAllocItem.builder()
                    .username(p.getUsername())
                    .fullname(p.getEmpChnName())
                    .ratio(p.getRatio() == null ? null : p.getRatio().toPlainString())
                    .isOriginal(1)
                    .build());
        }
        return CallPuResponse.ok(new OrigAllocData(items));
    }

    /**
     * PERF_INFO：单据详情，翻译为手机端 applyInfo.vue 的 dataForm 形态。
     */
    private CallPuResponse handlePerfInfo(CallPuRequest.Parm parm) {
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
        String userId = resolveUserId(empId);
        AllocAdjustDetailDTO detail = perfApprovalQueryApi.getAllocAdjustDetail(parm.getPerfAdjustNo(), userId);
        if (detail == null) {
            return CallPuResponse.fail("未查询到单据: " + parm.getPerfAdjustNo());
        }
        List<PerfDetailData.PerfAllocItem> allocaters = new ArrayList<>();
        if (detail.getAllocaters() != null) {
            for (AllocAdjustDetailDTO.AllocItem a : detail.getAllocaters()) {
                allocaters.add(PerfDetailData.PerfAllocItem.builder()
                        .username(a.getUsername())
                        .fullname(a.getFullname())
                        .ratio(a.getRatio())
                        .isOriginal(a.getIsOriginal())
                        .build());
            }
        }
        PerfDetailData data = PerfDetailData.builder()
                .perfAdjustNo(detail.getPerfAdjustNo())
                .applyFullname(detail.getApplyFullname())
                .custId(detail.getCustId())
                .custName(detail.getCustName())
                .applyType("RETAIL".equals(detail.getCustType()) ? "2" : "1")
                .applyRule("RULE".equals(detail.getAllocDim()) ? "2" : "1")
                .iouNo(detail.getAccountNo())
                .businessType(detail.getBizKind())
                .adjustExplain(detail.getReason())
                .apprStatus(toApprStatus(detail.getStatus()))
                .isCanAppr(detail.isCanApprove() ? 1 : 0)
                .isCanDelete(detail.isCanDelete() ? 1 : 0)
                .currentNode(detail.getCurrentNode())
                .currentNodeKey(detail.getCurrentNodeKey())
                .nextNode(detail.getNextNode())
                .allocaters(allocaters)
                .build();
        return CallPuResponse.ok(data);
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
     * SYS_DICT_ITEMS：按字典类型查启用字典项，回传手机端选项所需的 {code,label}。
     *
     * <p>口径与 PC 管理端 {@code listDictItems} 一致：{@link DictApi#getDictItems} 已只返 ACTIVE 且按
     * sort_order 升序。dictType 为空即失败信封；查无字典项回传空 items（前端选项为空，不报错）。</p>
     */
    private CallPuResponse handleDictItems(CallPuRequest.Parm parm) {
        String dictType = parm != null ? parm.getDictType() : null;
        if (!StringUtils.hasText(dictType)) {
            return CallPuResponse.fail("字典类型不能为空");
        }
        List<DictItemDTO> items = dictApi.getDictItems(dictType);
        List<DictItemData.Item> result = new ArrayList<>(items == null ? 0 : items.size());
        if (items != null) {
            for (DictItemDTO d : items) {
                result.add(DictItemData.Item.builder()
                        .dictCode(d.getDictCode())
                        .dictLabel(d.getDictLabel())
                        .build());
            }
        }
        return CallPuResponse.ok(new DictItemData(result));
    }

    /**
     * 校验并规整多选业务类型的 bizKind 逗号串（前端已传 PERF_BIZ_KIND 字典码，与 PC 存储口径一致）。
     *
     * <p>逐项 trim、跳过空白项，用 {@link DictApi#isValidDictValue} 校验每个码在 PERF_BIZ_KIND 下存在且启用；
     * 任一非空项非法即抛 {@link IllegalArgumentException}（由 {@link #dispatch} 兜底为失败信封）。
     * 校验通过则原样逗号拼接落 perf {@code biz_kind}（审批流按码前缀路由，与 PC 完全一致）。</p>
     *
     * @param businessType 前端传入的业务类型字典码逗号串（如 {@code CORP_DEPOSIT,CORP_LOAN}）
     * @return 去空后原样拼接的字典码串（非空）
     */
    private String validateBizKindCsv(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        List<String> codes = new ArrayList<>();
        for (String part : businessType.split(",")) {
            String code = part.trim();
            if (code.isEmpty()) {
                continue;
            }
            if (!dictApi.isValidDictValue(DICT_PERF_BIZ_KIND, code)) {
                throw new IllegalArgumentException("业务类型不合法: " + code);
            }
            codes.add(code);
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        return String.join(",", codes);
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
