package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.OrgDTO;

import java.util.List;
import java.util.Set;

/**
 * 组织机构对外API
 * 提供机构信息查询、子树遍历、用户主机构查询等能力
 * 结果有缓存，常规查询开销可控
 */
public interface OrgApi {

    /**
     * 根据机构编码查询机构信息
     *
     * @param orgCode 机构编码
     * @return 机构DTO，不存在时返回 null
     */
    OrgDTO getOrg(String orgCode);

    /**
     * 查询指定机构及其所有下属机构（含自身）
     *
     * @param orgCode 根机构编码
     * @return 机构子树列表（含根节点）
     */
    List<OrgDTO> getOrgSubtree(String orgCode);

    /**
     * 查询指定机构及其所有下属机构编码集合（含自身）
     * 性能优于 getOrgSubtree，仅返回编码
     *
     * @param orgCode 根机构编码
     * @return 机构编码集合
     */
    Set<String> getOrgSubtreeCodes(String orgCode);

    /**
     * 查询指定员工的主机构信息
     *
     * @param empId 员工ID
     * @return 主机构DTO
     */
    OrgDTO getUserMainOrg(String empId);

    /**
     * 根据关键字模糊搜索机构
     *
     * @param keyword 搜索关键字（机构名称或编码）
     * @param limit   最大返回数量
     * @return 匹配的机构列表
     */
    List<OrgDTO> searchOrgs(String keyword, int limit);
}
