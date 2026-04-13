package com.bank.branch.platform.portal.service.dto;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import lombok.Builder;
import lombok.Data;

/**
 * 产品列表查询参数（Service/Mapper 内部使用）
 */
@Data
@Builder
public class ProductListQuery {
    /** 关键字（产品名/编码模糊搜索） */
    private String keyword;
    /** 产品类别过滤 */
    private String category;
    /** 状态过滤，默认 ACTIVE */
    private String status;
    /** 产品部门机构编码过滤 */
    private String productDeptOrgCode;
    /** 是否支持中场支持过滤 */
    private Boolean supportForSupportRequest;
    /** 分页偏移量 */
    private Integer offset;
    /** 每页大小 */
    private Integer limit;
    /** 数据权限范围（common-security POJO，字段名 scope 不是 scopeType） */
    private DataScopeContext dataScope;
}
