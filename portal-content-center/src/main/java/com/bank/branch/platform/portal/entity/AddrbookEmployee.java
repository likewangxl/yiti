package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通讯录员工实体，对应 addrbook_employee 表。
 * <p>
 * 主键为 emp_id（员工工号），非自增 UUID。
 * responsible_product_ids 存储 JSON 数组字符串，通过 JsonStringListTypeHandler 自动转换。
 * status 字段使用字符串枚举：ACTIVE-在职，RESIGNED-离职。
 * deleted 字段实现逻辑删除：0-未删除，1-已删除。
 * </p>
 */
@Data
@TableName("addrbook_employee")
public class AddrbookEmployee {

    /** 员工工号（主键），对应 emp_id */
    @TableId(value = "emp_id", type = IdType.INPUT)
    private String empId;

    /** 员工姓名，对应 emp_name */
    private String empName;

    /** 手机号，对应 mobile */
    private String mobile;

    /** 邮箱，对应 email */
    private String email;

    /** 所属机构代码，对应 org_code */
    private String orgCode;

    /** 所属机构名称，对应 org_name */
    private String orgName;

    /** 岗位，对应 position */
    private String position;

    /** 自我描述，对应 self_desc */
    private String selfDesc;

    /** 负责产品ID列表(JSON数组，反向关联产品库)，对应 responsible_product_ids */
    private List<String> responsibleProductIds;

    /** 状态：ACTIVE-在职，RESIGNED-离职，对应 status */
    private String status;

    /** 维护人工号，对应 maintainer_emp_id */
    private String maintainerEmpId;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 删除标记(0-未删除，1-已删除)，对应 deleted */
    private Integer deleted;
}
