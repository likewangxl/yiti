package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品信息实体，对应 product_info 表。
 * <p>
 * 产品代码 product_code 全局唯一，用于业务层唯一性预检。
 * status 字段使用字符串枚举：ACTIVE-启用，DISABLED-禁用。
 * responsible_emp_ids 存储 JSON 数组字符串，通过 JsonStringListTypeHandler 自动转换。
 * deleted 字段实现逻辑删除：0-未删除，1-已删除。
 * </p>
 */
@Data
@TableName("product_info")
public class ProductInfo {

    /** 产品ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 产品代码（全局唯一），对应 product_code */
    private String productCode;

    /** 产品名称，对应 product_name */
    private String productName;

    /** 产品类别，对应 product_category */
    private String productCategory;

    /** 产品描述，对应 description */
    private String description;

    /** 是否支持中场支持，对应 support_for_support_request */
    private Boolean supportForSupportRequest;

    /** 归属组织(维护组织)，对应 owner_org_id */
    private String ownerOrgId;

    /** 产品部门ORG_CODE，对应 product_dept_org_code */
    private String productDeptOrgCode;

    /** 主附件文件ID，对应 file_object_id */
    private String fileObjectId;

    /** 负责人列表(JSON数组，反向关联通讯录)，对应 responsible_emp_ids */
    private List<String> responsibleEmpIds;

    /** 产品状态：ACTIVE-启用，DISABLED-禁用，对应 status */
    private String status;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 更新人，对应 updated_by */
    private String updatedBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 删除标记(0-未删除，1-已删除)，对应 deleted */
    private Integer deleted;
}
