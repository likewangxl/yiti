package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 标签客户导入请求 DTO。
 * <p>
 * 覆盖式导入：传入的 custIds 列表将完全替换该标签当前关联的客户列表。
 * </p>
 */
@Data
public class TagCustomerImportReqDTO {

    /** 待导入的客户 ID 列表（覆盖式，不能为空） */
    @NotEmpty(message = "客户ID列表不能为空")
    private List<String> custIds;

    /** 导入模式：APPEND-追加/REPLACE-全量替换；默认 REPLACE。 */
    private String mode = "REPLACE";
}
