package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeDTO;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * 担保信息 Entity ↔ DTO 转换器。
 */
public final class GuaranteeConverter {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private GuaranteeConverter() {
    }

    /** 万元换算除数（库内金额按元存储，展示按万元）。 */
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /**
     * 实体转响应 DTO（列表/详情/编辑反显共用）。
     * <p>三个金额库内按"元"存储，读出统一"元 → 万元"换算（÷10000，保留 2 位小数）；
     * 与保存路径 {@link #toYuan}（万元 ×10000 落库）读写对称，编辑反显→再保存值稳定。</p>
     */
    public static GuaranteeDTO toDTO(ZhGuaranteeInfo e) {
        if (e == null) {
            return null;
        }
        return GuaranteeDTO.builder()
                .id(e.getId())
                .clientName(e.getClientName())
                .notionalAmount(toWan(e.getNotionalAmount()))
                .occupyNotionalAmount(toWan(e.getOccupyNotionalAmount()))
                .usableNominalSum(toWan(e.getUsableNominalSum()))
                .lastExpire(e.getLastExpire())
                .userName(e.getUserName())
                .createTime(e.getCreateTime() != null ? e.getCreateTime().format(DT_FMT) : "")
                .updateTime(e.getUpdateTime() != null ? e.getUpdateTime() : "")
                .build();
    }

    /**
     * 金额"元 → 万元"换算：÷10000 后保留 2 位小数（HALF_UP）。空/非数值原样返回。
     */
    private static String toWan(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            return new BigDecimal(raw.trim())
                    .divide(TEN_THOUSAND, 2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    /**
     * 金额"万元 → 元"换算：×10000 后保留 2 位小数（HALF_UP）。空/非数值原样返回。
     * <p>新增/编辑保存前调用，把表单"万元"值落库为"元"。</p>
     */
    public static String toYuan(String wan) {
        if (wan == null || wan.isBlank()) {
            return wan;
        }
        try {
            return new BigDecimal(wan.trim())
                    .multiply(TEN_THOUSAND)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (NumberFormatException ex) {
            return wan;
        }
    }
}
