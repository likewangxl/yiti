package com.bank.branch.platform.customer.dto.marketing.tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 修改营销客户标签属性请求。 */
@Data
public class TagUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String tagName;

    @Size(max = 50)
    private String tagCategory;

    @Size(max = 30)
    private String tagType;

    @Min(0)
    @Max(1000)
    private Integer tagPriority;

    @Size(max = 500)
    private String description;

    private LocalDate expiresAt;

    private Integer lockVersion;
}
