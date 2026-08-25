package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建标签请求 DTO
 */
@Data
public class TagCreateReqDTO {

    /** 标签名称（唯一，不能为空） */
    @NotBlank(message = "标签名称不能为空")
    private String tagName;

    /** 标签描述 */
    private String description;

    /** 标签分类（如：价值类/行业类/风险类） */
    private String tagCategory;

    /** 标签优先级（数字越大优先级越高） */
    private Integer tagPriority;

    /** 标签类型：PROJECT/CERTIFICATION。 */
    private String tagType;

    /** 失效日期，空表示长期有效。 */
    private LocalDate expiresAt;
}
