package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeDTO;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;

import java.time.format.DateTimeFormatter;

/**
 * 担保信息 Entity ↔ DTO 转换器。
 */
public final class GuaranteeConverter {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private GuaranteeConverter() {
    }

    /** 实体转响应 DTO。 */
    public static GuaranteeDTO toDTO(ZhGuaranteeInfo e) {
        if (e == null) {
            return null;
        }
        return GuaranteeDTO.builder()
                .id(e.getId())
                .clientName(e.getClientName())
                .amountManage(e.getAmountManage())
                .usableExposureSum(e.getUsableExposureSum())
                .exposureAmount(e.getExposureAmount())
                .lastExpire(e.getLastExpire())
                .operator(e.getOperator())
                .createTime(e.getCreateTime() != null ? e.getCreateTime().format(DT_FMT) : "")
                .updateTime(e.getUpdateTime() != null ? e.getUpdateTime() : "")
                .build();
    }
}
