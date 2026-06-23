package com.bank.branch.platform.portal.controller.dto.guarantee;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 担保信息批量删除入参（支持多选删除）。
 */
@Data
public class GuaranteeBatchDeleteReqDTO {

    /** 待删除主键列表（至少一个） */
    @NotEmpty(message = "请至少选择一条记录")
    private List<Long> ids;
}
