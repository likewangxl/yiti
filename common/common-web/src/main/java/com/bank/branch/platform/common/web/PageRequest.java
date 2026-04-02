package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.Set;

/**
 * 分页请求参数
 * 默认 pageNo=1, pageSize=20, sortBy=createdTime, sortDir=desc
 */
@Data
public class PageRequest {
    @Min(value = 1, message = "页码最小为 1")
    private int pageNo = 1;

    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private int pageSize = 20;

    private String sortBy = "createdTime";

    @Pattern(regexp = "^(asc|desc)$", message = "排序方向只能为 asc 或 desc")
    private String sortDir = "desc";

    /**
     * 计算数据库查询偏移量
     *
     * @return 偏移量
     */
    public int getOffset() {
        return (pageNo - 1) * pageSize;
    }

    /**
     * 校验排序字段是否在允许范围内，防止 SQL 注入
     *
     * @param allowedFields 允许的排序字段集合
     */
    public void validateSortBy(Set<String> allowedFields) {
        if (sortBy != null && !allowedFields.contains(sortBy)) {
            throw new BizException("PAGE_001",
                String.format("不允许的排序字段: %s，允许的字段: %s", sortBy, allowedFields));
        }
    }
}
