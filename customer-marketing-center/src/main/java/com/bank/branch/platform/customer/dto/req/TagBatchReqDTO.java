package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 客户标签批量操作请求。 */
@Data
public class TagBatchReqDTO {

    /** 一次最多操作 100 个标签。 */
    @NotEmpty(message = "请选择至少一个标签")
    @Size(max = 100, message = "一次最多操作100个标签")
    private List<@NotBlank(message = "标签ID不能为空") String> ids;
}
