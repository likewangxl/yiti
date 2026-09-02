package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 PT_USER 表。
 * <p>
 * 全局开启了 map-underscore-to-camel-case，MyBatis 会自动将
 * PASS_WRONG_COUNT → passWrongCount、PWD_UPDATE_TIME → pwdUpdateTime 等字段完成映射。
 * PT_USER 的创建/更新操作人字段使用 CREATE_AUTHOR / UPDATE_AUTHOR（非 CREATE_USER / UPDATE_USER），
 * 请注意与其他表的区别。
 * </p>
 */
@Data
@TableName("PT_USER")
public class PtUser {

    /** 用户ID（工号），对应 USER_ID；业务赋值（工号字符串），非自增 */
    @TableId(value = "USER_ID", type = IdType.INPUT)
    private String userId;

    /** 用户姓名（登录名），对应 USERNAME */
    private String username;

    /** 用户中文姓名，对应 USERCHNNAME */
    private String userchnname;

    /** 密码（BCrypt 加密），对应 PWD */
    private String pwd;

    /** 邮箱，对应 EMAIL */
    private String email;

    /** 电话号码，对应 MOBILE；由用户本人通过通讯录自助维护 */
    @TableField("MOBILE")
    private String mobile;

    /** 用户类型（字典 USER_TYPE：1-员工 / 2-虚拟员工），对应 USER_TYPE */
    @TableField("USER_TYPE")
    private String userType;

    /** 账号是否过期：0-未过期，1-已过期，对应 ISEXPIRED（DB 列名无下划线） */
    @TableField("ISEXPIRED")
    private Integer isExpired;

    /** 账号是否锁定：0-未锁定，1-已锁定，对应 ISLOCKED（DB 列名无下划线） */
    @TableField("ISLOCKED")
    private Integer isLocked;

    /** 密码错误次数，对应 PASS_WRONG_COUNT */
    private Integer passWrongCount;

    /** 账号是否启用：0-启用，1-未启用，对应 ISENABLED（DB 列名无下划线） */
    @TableField("ISENABLED")
    private Integer isEnabled;

    /** 创建时间，对应 CREATE_TIME */
    private LocalDateTime createTime;

    /** 创建人，对应 CREATE_AUTHOR（注意：不是 CREATE_USER） */
    private String createAuthor;

    /** 更新时间，对应 UPDATE_TIME */
    private LocalDateTime updateTime;

    /** 更新人，对应 UPDATE_AUTHOR（注意：不是 UPDATE_USER） */
    private String updateAuthor;

    /** 备注，对应 REMARK */
    private String remark;

    /** 密码最后更新时间，对应 PWD_UPDATE_TIME */
    private LocalDateTime pwdUpdateTime;
}
