package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户与产品的负责关系。
 *
 * <p>一个用户可以负责多个产品，一个产品也可以有多个负责人。物理表以
 * {@code (USER_ID, PRODUCT_ID)} 作为复合主键；{@code USER_ID} 上的
 * {@link TableId} 仅用于满足 MyBatis-Plus 的实体元数据要求，关系读写统一使用本类
 * 的自定义 Mapper SQL，避免把复合键误当作单列主键。</p>
 */
@Data
@TableName("PORTAL_USER_PRODUCT_REL")
public class UserProductRelation {

    /** 用户主键，值来自 PT_USER.USER_ID。 */
    @TableId(value = "USER_ID", type = IdType.INPUT)
    private String userId;

    /** 产品主键，值来自 PRODUCT_INFO.ID。 */
    @TableField("PRODUCT_ID")
    private String productId;

    /** 建立负责关系的时间。 */
    @TableField("ASSIGNED_TIME")
    private LocalDateTime assignedTime;

    /** 关系最后更新时间。 */
    @TableField("UPDATED_TIME")
    private LocalDateTime updatedTime;

    /** 最后修改关系的用户。 */
    @TableField("UPDATED_BY")
    private String updatedBy;
}
