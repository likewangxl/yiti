package com.bank.branch.platform.redengine.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 党组织树节点 DTO。
 * <p>按源 redengine {@code PartyOrgServiceImpl.getOrgTree} 返回结构设计：字段与
 * {@code RePartyOrg} 实体一一对应，额外携带 {@link #children} 承载下级党组织，
 * 供 {@code RePartyOrgService.getOrgTree} 组装父子树、{@code getById} 复用作单节点详情。</p>
 */
@Data
public class RePartyOrgTreeDTO {

    /** 党组织ID */
    private Long id;
    /** 党组织名称 */
    private String orgName;
    /** 上级党组织ID，顶层为 null */
    private Long parentId;
    /** 组织层级（1=党委，2=支部...） */
    private Integer orgLevel;
    /** 组织编码 */
    private String orgCode;
    /** 组织类型（经营单位/营销部室/中后台部门） */
    private String orgType;
    /** 负责人姓名 */
    private String principal;
    /** 联系电话 */
    private String contactPhone;
    /** 组织地址 */
    private String orgAddress;
    /** 支部书记平台用户工号 */
    private String secretaryId;
    /** 备注 */
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 下级党组织列表，无下级时为空列表（不返回 null） */
    private List<RePartyOrgTreeDTO> children = new ArrayList<>();
}
