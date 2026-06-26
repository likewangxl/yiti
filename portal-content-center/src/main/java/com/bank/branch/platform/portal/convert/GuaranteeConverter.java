package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeDTO;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * 担保信息 Entity ↔ DTO 转换器。
 *
 * <p>金额口径：库内统一按「元」存储，展示/录入按「万元」。读出 {@link #toWan}（元 ÷10000）、
 * 前端录入保存前 {@link #toYuan}（万元 ×10000）读写对称。合同导入/定时同步的金额来自 ccms/clms
 * 的「元」原值，落库不换算，读取时同样 ÷10000 显示为万元，与手工录入口径一致。</p>
 */
public final class GuaranteeConverter {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 万元换算除数（库内金额按元存储，展示按万元）。 */
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    private GuaranteeConverter() {
    }

    /**
     * 实体转响应 DTO（列表/详情/编辑反显共用）。三个金额「元 → 万元」（÷10000）透出。
     */
    public static GuaranteeDTO toDTO(ZhGuaranteeInfo e) {
        if (e == null) {
            return null;
        }
        return GuaranteeDTO.builder()
                .id(e.getId())
                .clientNo(e.getClientNo())
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

    /** 金额「元 → 万元」：÷10000 保留 2 位小数（HALF_UP）。空/非数值原样返回。 */
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
     * 金额「万元 → 元」：×10000 保留 2 位小数（HALF_UP）。空/非数值原样返回。
     * <p>前端录入（ccms 无数据的单条新增/编辑）保存前调用，把表单「万元」值落库为「元」。</p>
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
