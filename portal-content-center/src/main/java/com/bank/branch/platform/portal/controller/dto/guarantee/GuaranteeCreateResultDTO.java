package com.bank.branch.platform.portal.controller.dto.guarantee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 担保信息新增结果。
 *
 * <p>新增分两种落库来源，前端据此提示不同文案：</p>
 * <ul>
 *   <li>{@code CONTRACT}：客户在 {@code ccms_business_contract} 命中合同记录，按合同批量导入，
 *       前端表单数据被忽略；{@code count} = 导入条数，{@code id} 为 null。</li>
 *   <li>{@code MANUAL}：客户在合同表无记录，落库前端录入的单条数据；{@code count} = 1，
 *       {@code id} = 新记录主键。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
public class GuaranteeCreateResultDTO {

    /** 落库来源：CONTRACT（合同批量导入）/ MANUAL（前端单条录入） */
    private String source;

    /** 实际落库条数 */
    private int count;

    /** 新记录主键（仅 MANUAL 来源有值，CONTRACT 批量导入为 null） */
    private Long id;

    /** 合同批量导入结果。 */
    public static GuaranteeCreateResultDTO fromContract(int count) {
        return GuaranteeCreateResultDTO.builder().source("CONTRACT").count(count).build();
    }

    /** 前端单条录入结果。 */
    public static GuaranteeCreateResultDTO manual(Long id) {
        return GuaranteeCreateResultDTO.builder().source("MANUAL").count(1).id(id).build();
    }
}
