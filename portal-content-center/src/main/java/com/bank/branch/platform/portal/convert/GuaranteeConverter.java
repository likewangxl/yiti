package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeDTO;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;

import java.time.format.DateTimeFormatter;

/**
 * 担保信息 Entity ↔ DTO 转换器。
 *
 * <p>金额列库内按「元」原值存储，读写均不做单位换算（合同导入、定时同步与前端录入口径统一为「元」）。</p>
 */
public final class GuaranteeConverter {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private GuaranteeConverter() {
    }

    /**
     * 实体转响应 DTO（列表/详情/编辑反显共用）。金额按「元」原值透出，不做换算。
     */
    public static GuaranteeDTO toDTO(ZhGuaranteeInfo e) {
        if (e == null) {
            return null;
        }
        return GuaranteeDTO.builder()
                .id(e.getId())
                .clientNo(e.getClientNo())
                .clientName(e.getClientName())
                .notionalAmount(e.getNotionalAmount())
                .occupyNotionalAmount(e.getOccupyNotionalAmount())
                .usableNominalSum(e.getUsableNominalSum())
                .lastExpire(e.getLastExpire())
                .userName(e.getUserName())
                .createTime(e.getCreateTime() != null ? e.getCreateTime().format(DT_FMT) : "")
                .updateTime(e.getUpdateTime() != null ? e.getUpdateTime() : "")
                .build();
    }
}
