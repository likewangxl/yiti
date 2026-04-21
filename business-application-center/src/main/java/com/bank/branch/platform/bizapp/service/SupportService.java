package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportScenario;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportSubmittedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 中场支持申请服务（发起侧视图）。
 * <p>
 * 提供创建（含自动拆单）、提交、撤回、删除草稿、查询等发起侧操作。
 * 场景A（产品直达）通过 SupportProductSplitService 拆单；
 * 场景B（部门承接）创建单条记录，需指定 supportDeptId。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportRequestMapper supportMapper;
    private final SupportScenarioRouter scenarioRouter;
    private final SupportProductSplitService splitService;
    private final BizStateMachine bizStateMachine;
    private final BizNoGenerator bizNoGenerator;
    private final WorkflowApi workflowApi;
    private final CustomerQueryApi customerQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final SupportRequestDTOConverter supportRequestDTOConverter;

    /**
     * 创建中场支持申请（含自动拆单逻辑）。
     * <p>
     * 场景A：调用 splitService.splitByProducts() 创建多条 DRAFT 记录；
     * 场景B：创建单条 DRAFT 记录，supportDeptId 必填。
     * </p>
     *
     * @return 创建的记录列表（A 可能多条，B 固定一条）
     */
    @Transactional
    public List<SupportRequest> create(List<String> productIds, String custId,
                                        String sourceTouchTaskId, String otherDemand,
                                        String supportDeptId, String operatorEmpId, String orgCode) {
        log.info("[SupportService.create] custId={}, operator={}", custId, operatorEmpId);

        // 1. 校验客户有效性
        if (!customerQueryApi.isValidCustomer(custId)) {
            throw new BizException(
                    BizAppErrorCode.CUSTOMER_NOT_VALID.getCode(),
                    BizAppErrorCode.CUSTOMER_NOT_VALID.getMessage()
            );
        }

        // 2. 场景路由
        SupportScenario scenario = scenarioRouter.route(productIds, otherDemand, supportDeptId);

        // 3. 按场景创建
        if (scenario == SupportScenario.A) {
            return splitService.splitByProducts(productIds, custId, sourceTouchTaskId, operatorEmpId, orgCode);
        } else {
            // 场景B：创建单条记录
            LocalDateTime now = LocalDateTime.now();
            SupportRequest entity = new SupportRequest();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setRequestNo(bizNoGenerator.generateSupportNo());
            entity.setCustId(custId);
            entity.setSourceTouchTaskId(sourceTouchTaskId);
            entity.setOtherDemand(otherDemand);
            entity.setSupportDeptId(supportDeptId);
            entity.setStatus(SupportStatus.DRAFT.getCode());
            entity.setBusinessKey("SUPPORT:" + entity.getId());
            entity.setOwnerOrgId(orgCode);
            entity.setCreatedBy(operatorEmpId);
            entity.setCreatedTime(now);
            entity.setUpdatedBy(operatorEmpId);
            entity.setUpdatedTime(now);
            entity.setDeleted(0);

            supportMapper.insert(entity);
            log.info("[SupportService.create] 场景B，创建 SupportRequest id={}", entity.getId());
            return Collections.singletonList(entity);
        }
    }

    /**
     * 提交草稿（SELECT FOR UPDATE，validate DRAFT -> IN_APPROVAL，启动工作流）。
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人
     * @param orgCode       归属机构
     */
    @Transactional
    public void submit(String id, String operatorEmpId, String orgCode) {
        log.info("[SupportService.submit] id={}, operator={}", id, operatorEmpId);

        // SELECT FOR UPDATE 防并发
        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        // 权限校验：只有创建人可以提交
        if (!operatorEmpId.equals(request.getCreatedBy())) {
            throw new BizException(
                    BizAppErrorCode.NOT_APPLY_CREATOR.getCode(),
                    BizAppErrorCode.NOT_APPLY_CREATOR.getMessage()
            );
        }

        // 状态迁移校验：DRAFT -> IN_APPROVAL
        bizStateMachine.validateSupportTransition(request.getStatus(), SupportStatus.IN_APPROVAL.getCode());

        // 确定场景（场景A：有productId且无supportDeptId；场景B：有supportDeptId）
        boolean isScenarioA = StringUtils.hasText(request.getProductId())
                && !StringUtils.hasText(request.getSupportDeptId());
        String processDefinitionKey = isScenarioA
                ? SupportScenario.A.getProcessDefinitionKey()
                : SupportScenario.B.getProcessDefinitionKey();

        // 启动工作流
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("SUPPORT");
        cmd.setBizId(id);
        cmd.setBusinessKey("SUPPORT:" + id);
        cmd.setProcessDefinitionKey(processDefinitionKey);
        cmd.setStartUser(operatorEmpId);
        cmd.setStartOrgId(orgCode);
        cmd.setTitle("中场支持申请-" + request.getRequestNo());

        WorkflowLaunchResp resp = workflowApi.startProcess(cmd);

        // 更新申请状态
        request.setStatus(SupportStatus.IN_APPROVAL.getCode());
        request.setProcessInstanceId(resp.getProcessInstanceId());
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        // 发布提交事件
        eventPublisher.publishEvent(new SupportSubmittedEvent(
                id, request.getRequestNo(), request.getCustId(),
                request.getProductId(), request.getOwnerOrgId(), operatorEmpId
        ));

        log.info("[SupportService.submit] 申请 {} 已提交工作流，processInstanceId={}",
                id, resp.getProcessInstanceId());
    }

    /**
     * 撤回申请（IN_APPROVAL 或 IN_PROGRESS -> CANCELLED）。
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void cancel(String id, String operatorEmpId) {
        log.info("[SupportService.cancel] id={}, operator={}", id, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        bizStateMachine.validateSupportTransition(request.getStatus(), SupportStatus.CANCELLED.getCode());

        request.setStatus(SupportStatus.CANCELLED.getCode());
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        log.info("[SupportService.cancel] 申请 {} 已撤回", id);
    }

    /**
     * 删除草稿（软删除）。
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void deleteDraft(String id, String operatorEmpId) {
        log.info("[SupportService.deleteDraft] id={}, operator={}", id, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        // 只允许草稿状态删除
        if (!SupportStatus.DRAFT.getCode().equals(request.getStatus())) {
            throw new BizException(
                    BizAppErrorCode.NOT_DRAFT_STATUS.getCode(),
                    BizAppErrorCode.NOT_DRAFT_STATUS.getMessage()
            );
        }

        request.setDeleted(1);
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        log.info("[SupportService.deleteDraft] 申请 {} 已删除", id);
    }

    /**
     * 按ID查询申请。
     *
     * @param id 申请ID
     * @return 申请实体
     */
    public SupportRequest getById(String id) {
        SupportRequest request = supportMapper.selectById(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }
        return request;
    }

    /**
     * 发起侧分页查询（返回 Entity，供内部使用）。
     *
     * @param keyword    关键词
     * @param status     状态筛选
     * @param ownerOrgId 归属机构
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return 分页结果（Entity）
     */
    public PageResult<SupportRequest> listPage(String keyword, String status,
                                                String ownerOrgId, int pageNo, int pageSize) {
        log.info("[SupportService.listPage] ownerOrgId={}, pageNo={}, pageSize={}", ownerOrgId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<SupportRequest> records = supportMapper.selectPageForSupport(keyword, status, ownerOrgId, offset, pageSize);
        long total = supportMapper.countPageForSupport(keyword, status, ownerOrgId);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 发起侧分页查询（返回 ListItemDTO，供 REST 层使用）。
     * <p>
     * 通过 {@link SupportRequestDTOConverter#toListItems} 批量转换并填充展示字段，避免 N+1 查询。
     * REST 层禁止直接暴露 Entity，统一通过此方法获取列表数据。
     * </p>
     *
     * @param keyword    关键词
     * @param status     状态筛选
     * @param ownerOrgId 归属机构
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return 分页结果（ListItemDTO，不含 deleted 等内部字段）
     */
    public PageResult<SupportRequestListItemDTO> listPageAsDTO(String keyword, String status,
                                                               String ownerOrgId, int pageNo, int pageSize) {
        PageResult<SupportRequest> page = listPage(keyword, status, ownerOrgId, pageNo, pageSize);
        List<SupportRequestListItemDTO> items = supportRequestDTOConverter.toListItems(page.getRecords());
        return PageResult.of(pageNo, pageSize, page.getTotal(), items);
    }

    /**
     * 按ID查询申请并转为 DTO（供 REST 层使用）。
     * <p>
     * REST 层禁止直接暴露 Entity，通过此方法获取详情数据，不含 deleted 等内部字段。
     * </p>
     *
     * @param id 申请ID
     * @return 申请 DTO（含 custName/productName/supportDeptName 冗余字段）
     * @throws BizException BIZ-40401 如果不存在
     */
    public SupportRequestDTO getByIdAsDTO(String id) {
        SupportRequest entity = getById(id);
        return supportRequestDTOConverter.toDTO(entity);
    }
}
