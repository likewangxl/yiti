package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大屏级查看角色白名单。
 *
 * <p>角色编码只作为 auth 模块的业务契约引用，不在 report 模块建立跨模块外键。</p>
 */
@Data
@TableName("RPT_SCREEN_ACCESS_ROLE")
public class RptScreenAccessRole {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属大屏。 */
    private Long screenId;

    /** PT_ROLE.ROLE_CODE。 */
    private String roleCode;

    /** ACTIVE / DISABLED。 */
    private String status;

    private String createdBy;

    private LocalDateTime createdTime;

    /** 最近覆盖保存白名单的操作人。 */
    private String updatedBy;

    private LocalDateTime updatedTime;
}
