package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportProcessLogDTO;
import com.bank.branch.platform.bizapp.dto.req.CreateSupportProcessLogReq;
import com.bank.branch.platform.bizapp.entity.SupportProcessLog;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.mapper.SupportProcessLogMapper;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 中台支持过程记录服务，统一处理数据范围、幂等和文件关联。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportProcessLogService {

    private static final String FILE_BIZ_TYPE = "SUPPORT_LOG";
    private static final String LOG_TYPE_PROCESS = "PROCESS";
    private static final String LOG_TYPE_RESULT = "RESULT";

    private final SupportProcessLogMapper logMapper;
    private final SupportRequestMapper supportMapper;
    private final FileApi fileApi;
    private final UserApi userApi;

    /** 发起侧只读日志。 */
    @Transactional(readOnly = true)
    public List<SupportProcessLogDTO> listForInitiator(String requestId, String operatorEmpId) {
        SupportRequest request = getRequest(requestId);
        assertInitiatingRead(request, operatorEmpId);
        return toDTOs(logMapper.selectByRequestId(requestId));
    }

    /** 承接侧只读日志。 */
    @Transactional(readOnly = true)
    public List<SupportProcessLogDTO> listForDept(String requestId, String operatorEmpId) {
        SupportRequest request = getRequest(requestId);
        assertReceivingRead(request, operatorEmpId);
        return toDTOs(logMapper.selectByRequestId(requestId));
    }

    /**
     * 新增承接过程记录。场景B仅允许进行中的当前承接人，场景A允许
     * IN_APPROVAL 状态的当前产品负责人写入；clientUuid 在一条申请内幂等。
     */
    @Transactional
    public SupportProcessLogDTO addLog(String requestId, CreateSupportProcessLogReq req,
                                       String operatorEmpId) {
        if (req == null || !StringUtils.hasText(req.getClientUuid())) {
            throw required("clientUuid");
        }
        String logType = normalizeLogType(req.getLogType());
        // RESULT 是完成动作内部生成的结果留痕，不能由普通过程记录接口借此绕过
        // 现场打卡/定位/照片约束。
        if (LOG_TYPE_RESULT.equals(logType)) {
            throw new BizException(BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getCode(),
                    "RESULT日志只能由办理完成动作生成");
        }
        validateProcessLog(req);

        // 锁定主申请，避免并发完成/撤回与过程记录交叉写入。
        SupportRequest request = getRequestForUpdate(requestId);
        assertCanWrite(request, operatorEmpId);

        SupportProcessLog existing = logMapper.selectByRequestAndClientUuid(
                requestId, req.getClientUuid());
        if (existing != null) {
            return toDTO(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        SupportProcessLog entity = new SupportProcessLog();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setSupportRequestId(requestId);
        entity.setClientUuid(req.getClientUuid());
        entity.setLogType(logType);
        entity.setContent(req.getContent());
        entity.setCheckinTime(req.getCheckinTime());
        entity.setLongitude(req.getLongitude());
        entity.setLatitude(req.getLatitude());
        entity.setLocationAddress(req.getLocationAddress());
        entity.setCreatedBy(operatorEmpId);
        entity.setCreatedTime(now);
        entity.setDeleted(0);
        try {
            logMapper.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            // 移动端重试可能在第一次响应丢失后到达；以唯一键记录作为幂等结果。
            SupportProcessLog duplicated = logMapper.selectByRequestAndClientUuid(
                    requestId, req.getClientUuid());
            if (duplicated != null) {
                return toDTO(duplicated);
            }
            throw duplicate;
        }
        bindFiles(entity.getId(), req.getFileIds());
        return toDTO(entity);
    }

    /** 完成动作写入 RESULT 记录，使用稳定幂等键避免重复结果日志。 */
    @Transactional
    public SupportProcessLogDTO appendResultLog(String requestId, String content,
                                                String operatorEmpId, List<String> fileIds) {
        CreateSupportProcessLogReq req = new CreateSupportProcessLogReq();
        req.setClientUuid("RESULT:" + requestId + ":" + operatorEmpId);
        req.setLogType(LOG_TYPE_RESULT);
        req.setContent(content);
        req.setFileIds(fileIds);
        return addInternalResultLog(requestId, req, operatorEmpId);
    }

    /** 兼容常用命名，供 Controller/测试调用。 */
    public SupportProcessLogDTO create(String requestId, CreateSupportProcessLogReq req,
                                       String operatorEmpId) {
        return addLog(requestId, req, operatorEmpId);
    }

    /** 查询过程记录总数，完成前用于最小记录校验。 */
    @Transactional(readOnly = true)
    public long count(String requestId) {
        return logMapper.countByRequestId(requestId);
    }

    /** 查询 PROCESS 记录总数，完成动作不得以 RESULT 记录替代现场过程留痕。 */
    @Transactional(readOnly = true)
    public long countProcess(String requestId) {
        return logMapper.countByRequestIdAndLogType(requestId, LOG_TYPE_PROCESS);
    }

    private SupportRequest getRequest(String requestId) {
        SupportRequest request = supportMapper.selectById(requestId);
        if (request == null) {
            throw error(BizAppErrorCode.APPLY_NOT_FOUND);
        }
        return request;
    }

    private SupportRequest getRequestForUpdate(String requestId) {
        SupportRequest request = supportMapper.selectForUpdate(requestId);
        if (request == null) {
            throw error(BizAppErrorCode.APPLY_NOT_FOUND);
        }
        return request;
    }

    private SupportProcessLogDTO addInternalResultLog(String requestId, CreateSupportProcessLogReq req,
                                                       String operatorEmpId) {
        if (!StringUtils.hasText(req.getContent())) {
            throw required("content");
        }
        SupportRequest request = getRequestForUpdate(requestId);
        assertCanWrite(request, operatorEmpId);

        SupportProcessLog existing = logMapper.selectByRequestAndClientUuid(
                requestId, req.getClientUuid());
        if (existing != null) {
            return toDTO(existing);
        }

        SupportProcessLog entity = new SupportProcessLog();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setSupportRequestId(requestId);
        entity.setClientUuid(req.getClientUuid());
        entity.setLogType(LOG_TYPE_RESULT);
        entity.setContent(req.getContent());
        entity.setCheckinTime(req.getCheckinTime());
        entity.setLongitude(req.getLongitude());
        entity.setLatitude(req.getLatitude());
        entity.setLocationAddress(req.getLocationAddress());
        entity.setCreatedBy(operatorEmpId);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setDeleted(0);
        try {
            logMapper.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            SupportProcessLog duplicated = logMapper.selectByRequestAndClientUuid(
                    requestId, req.getClientUuid());
            if (duplicated != null) {
                return toDTO(duplicated);
            }
            throw duplicate;
        }
        bindFiles(entity.getId(), req.getFileIds());
        return toDTO(entity);
    }

    private String normalizeLogType(String logType) {
        if (!StringUtils.hasText(logType)) {
            return LOG_TYPE_PROCESS;
        }
        String normalized = logType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!LOG_TYPE_PROCESS.equals(normalized) && !LOG_TYPE_RESULT.equals(normalized)) {
            throw new BizException(BizAppErrorCode.INVALID_DICT_VALUE.getCode(),
                    BizAppErrorCode.INVALID_DICT_VALUE.getMessage());
        }
        return normalized;
    }

    private void validateProcessLog(CreateSupportProcessLogReq req) {
        if (!StringUtils.hasText(req.getContent())) {
            throw required("content");
        }
        if (req.getContent().length() > 2000) {
            throw new BizException(BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getCode(),
                    "content长度不能超过2000");
        }
        if (req.getCheckinTime() == null) {
            throw required("checkinTime");
        }
        if (req.getFileIds() == null || req.getFileIds().stream().noneMatch(StringUtils::hasText)) {
            throw required("fileIds");
        }
        if (!hasLocation(req)) {
            throw new BizException(BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getCode(),
                    "请提供locationAddress或完整经纬度");
        }
        validateCoordinates(req);
    }

    private boolean hasLocation(CreateSupportProcessLogReq req) {
        if (StringUtils.hasText(req.getLocationAddress())) {
            return true;
        }
        return req.getLongitude() != null && req.getLatitude() != null;
    }

    private void validateCoordinates(CreateSupportProcessLogReq req) {
        if ((req.getLongitude() == null) != (req.getLatitude() == null)) {
            throw new BizException(BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getCode(),
                    "longitude和latitude必须同时提供");
        }
        if (req.getLongitude() != null
                && (req.getLongitude().compareTo(new java.math.BigDecimal("-180")) < 0
                || req.getLongitude().compareTo(new java.math.BigDecimal("180")) > 0)) {
            throw new BizException(BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getCode(),
                    "longitude超出范围");
        }
        if (req.getLatitude() != null
                && (req.getLatitude().compareTo(new java.math.BigDecimal("-90")) < 0
                || req.getLatitude().compareTo(new java.math.BigDecimal("90")) > 0)) {
            throw new BizException(BizAppErrorCode.NODE_FORM_CONDITION_FAIL.getCode(),
                    "latitude超出范围");
        }
    }

    private void assertCanWrite(SupportRequest request, String operatorEmpId) {
        boolean scenarioA = StringUtils.hasText(request.getProductId())
                && !StringUtils.hasText(request.getSupportDeptId());
        boolean writableStatus = SupportStatus.IN_PROGRESS.getCode().equals(request.getStatus())
                || (scenarioA && SupportStatus.IN_APPROVAL.getCode().equals(request.getStatus()));
        if (!writableStatus || !same(operatorEmpId, request.getAssignedEmpId())) {
            throw error(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER);
        }
        assertReceivingScope(request, operatorEmpId);
    }

    private void assertInitiatingRead(SupportRequest request, String operatorEmpId) {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx == null || ctx.getScope() == null) {
            // Service 也必须做实体级守卫；没有 AOP 数据域时只允许创建人读取。
            if (!same(operatorEmpId, request.getCreatedBy())) {
                throw error(BizAppErrorCode.NOT_APPLY_CREATOR);
            }
            return;
        }
        if (!inInitiatingScope(request, ctx)) {
            throw error(BizAppErrorCode.NOT_APPLY_CREATOR);
        }
    }

    private void assertReceivingRead(SupportRequest request, String operatorEmpId) {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null) {
            if (!inReceivingScope(request, ctx)) {
                throw error(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER);
            }
            return;
        }
        // 当前承接人始终可读；秘书/部门管理员由 UserApi 的机构+角色校验放行。
        if (same(operatorEmpId, request.getAssignedEmpId())) {
            return;
        }
        if (userApi != null && StringUtils.hasText(request.getSupportDeptId())) {
            UserDTO user = userApi.getUserByEmpId(operatorEmpId);
            if (user != null && request.getSupportDeptId().equals(user.getMainOrgCode())) {
                return;
            }
        }
        throw error(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER);
    }

    private void assertReceivingScope(SupportRequest request, String operatorEmpId) {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null && !inReceivingScope(request, ctx)) {
            throw error(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER);
        }
    }

    private boolean inInitiatingScope(SupportRequest request, DataScopeContext ctx) {
        DataScopeType scope = ctx.getScope();
        if (scope == DataScopeType.ALL || scope == DataScopeType.WORKFLOW_PARTICIPANT) {
            return scope == DataScopeType.ALL || same(ctx.getEmpId(), request.getCreatedBy())
                    || same(ctx.getEmpId(), request.getAssignedEmpId());
        }
        if (scope == DataScopeType.SELF_CREATED || scope == DataScopeType.SELF) {
            return same(ctx.getEmpId(), request.getCreatedBy());
        }
        if (scope == DataScopeType.ORG) {
            return same(ctx.getOrgCode(), request.getOwnerOrgId());
        }
        if (scope == DataScopeType.ORG_SUBTREE) {
            return ctx.getOrgSubtreeCodes() != null
                    && ctx.getOrgSubtreeCodes().contains(request.getOwnerOrgId());
        }
        return false;
    }

    private boolean inReceivingScope(SupportRequest request, DataScopeContext ctx) {
        DataScopeType scope = ctx.getScope();
        if (scope == DataScopeType.ALL) {
            return true;
        }
        // 场景A没有 supportDeptId，但已分配的产品负责人仍是明确的流程参与人；
        // 其实体级 assignedEmpId 守卫优先于机构筛选，避免 ORG 数据域误伤产品负责人留痕。
        if (isScenarioA(request) && same(ctx.getEmpId(), request.getAssignedEmpId())) {
            return true;
        }
        if (scope == DataScopeType.SELF_ASSIGNED) {
            return same(ctx.getEmpId(), request.getAssignedEmpId());
        }
        if (scope == DataScopeType.ORG) {
            return same(ctx.getOrgCode(), request.getSupportDeptId());
        }
        if (scope == DataScopeType.ORG_SUBTREE) {
            return ctx.getOrgSubtreeCodes() != null
                    && ctx.getOrgSubtreeCodes().contains(request.getSupportDeptId());
        }
        if (scope == DataScopeType.WORKFLOW_PARTICIPANT) {
            return same(ctx.getEmpId(), request.getAssignedEmpId())
                    || same(ctx.getEmpId(), request.getDispatchEmpId());
        }
        return false;
    }

    private boolean isScenarioA(SupportRequest request) {
        return request != null && StringUtils.hasText(request.getProductId())
                && !StringUtils.hasText(request.getSupportDeptId());
    }

    private List<SupportProcessLogDTO> toDTOs(List<SupportProcessLog> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDTO).toList();
    }

    private SupportProcessLogDTO toDTO(SupportProcessLog entity) {
        SupportProcessLogDTO dto = new SupportProcessLogDTO();
        dto.setId(entity.getId());
        dto.setSupportRequestId(entity.getSupportRequestId());
        dto.setClientUuid(entity.getClientUuid());
        dto.setLogType(entity.getLogType());
        dto.setContent(entity.getContent());
        dto.setCheckinTime(entity.getCheckinTime());
        dto.setLongitude(entity.getLongitude());
        dto.setLatitude(entity.getLatitude());
        dto.setLocationAddress(entity.getLocationAddress());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedTime(entity.getCreatedTime());
        if (fileApi != null && StringUtils.hasText(entity.getId())) {
            // 文件关联以申请 ID 为业务对象，便于发起侧/承接侧共享只读结果。
            List<FileObjectDTO> files = fileApi.listBizFiles(FILE_BIZ_TYPE, entity.getId());
            dto.setFiles(files == null ? Collections.emptyList() : files);
        } else {
            dto.setFiles(Collections.emptyList());
        }
        return dto;
    }

    private void bindFiles(String requestId, List<String> fileIds) {
        if (fileApi == null || fileIds == null) {
            return;
        }
        for (String fileId : fileIds) {
            if (StringUtils.hasText(fileId)) {
                fileApi.bindFile(FILE_BIZ_TYPE, requestId, fileId, "PHOTO");
            }
        }
    }

    private BizException error(BizAppErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }

    private BizException required(String field) {
        return new BizException(BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getCode(),
                field + "不能为空");
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
    }
}
