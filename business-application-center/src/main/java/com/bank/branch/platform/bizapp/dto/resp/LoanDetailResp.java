package com.bank.branch.platform.bizapp.dto.resp;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 贷款申请详情响应 DTO。
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
     * 从实体转换为响应 DTO。
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
