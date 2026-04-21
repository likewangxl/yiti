package com.bank.branch.platform.bizapp.dto.resp;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 贷款申请详情响应 DTO。
 * <p>
 * V1 新增：custInfo（客户基础信息）和 canOperate（当前用户按钮可见性）。
 * TODO V2: 集成 workflow-center 历史查询，补充 processMap / approvalLogs 字段。
 * </p>
 */
@Data
public class LoanDetailResp {

    private String id;
    private String applyNo;
    private String custId;
    private String sourceTouchTaskId;
    private String projectType;
    private String bizType;
    private String guaranteeType;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String status;
    private String businessKey;
    private String processInstanceId;
    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    /**
     * 客户基础信息（前端详情页展示用）。
     * 若 CustomerQueryApi 返回空则为 null。
     */
    private CustInfoVO custInfo;

    /**
     * 当前用户是否可操作（控制前端按钮可见性）。
     * 规则：DRAFT/IN_APPROVAL 状态下且当前用户为创建人时为 true，否则为 false。
     */
    private Boolean canOperate;

    /**
     * 客户基础信息内嵌 VO。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustInfoVO {

        /** 客户 ID */
        private String custId;

        /** 客户名称 */
        private String custName;

        /**
         * 客户类型（从 CustomerDTO.customerType 映射）。
         * TODO V2: 通过 ClaimApi 补充 ownerEmpId（客户维护人工号）。
         */
        private String custType;
    }

    /**
     * 从实体转换为响应 DTO（基础字段转换，不包含 custInfo 和 canOperate）。
     * <p>
     * 调用方需通过 {@link com.bank.branch.platform.bizapp.service.LoanService#getDetail} 获取富化后的详情。
     * </p>
     *
     * @param entity 贷款申请实体
     * @return 响应 DTO
     */
    public static LoanDetailResp from(LoanApply entity) {
        if (entity == null) {
            return null;
        }
        LoanDetailResp resp = new LoanDetailResp();
        resp.setId(entity.getId());
        resp.setApplyNo(entity.getApplyNo());
        resp.setCustId(entity.getCustId());
        resp.setSourceTouchTaskId(entity.getSourceTouchTaskId());
        resp.setProjectType(entity.getProjectType());
        resp.setBizType(entity.getBizType());
        resp.setGuaranteeType(entity.getGuaranteeType());
        resp.setCreditAmount(entity.getCreditAmount());
        resp.setCreditExposureAmount(entity.getCreditExposureAmount());
        resp.setStatus(entity.getStatus());
        resp.setBusinessKey(entity.getBusinessKey());
        resp.setProcessInstanceId(entity.getProcessInstanceId());
        resp.setOwnerOrgId(entity.getOwnerOrgId());
        resp.setCreatedBy(entity.getCreatedBy());
        resp.setCreatedTime(entity.getCreatedTime());
        resp.setUpdatedBy(entity.getUpdatedBy());
        resp.setUpdatedTime(entity.getUpdatedTime());
        return resp;
    }
}
