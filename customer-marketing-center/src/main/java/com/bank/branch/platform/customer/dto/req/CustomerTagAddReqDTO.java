package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 客户追加打标请求 DTO。
 * <p>
 * 用于 POST /api/customers/{id}/tags 端点，支持一次追加多个标签。
 * 追加为幂等操作：已存在的标签关联会被跳过，不会重复插入。
 * </p>
 */
@Data
public class CustomerTagAddReqDTO {

    /**
     * 标签 ID 列表，不可为空，最多 50 个。
     */
    @NotEmpty(message = "tagIds 不能为空")
    @Size(max = 50, message = "单次最多追加 50 个标签")
    private List<String> tagIds;
}
