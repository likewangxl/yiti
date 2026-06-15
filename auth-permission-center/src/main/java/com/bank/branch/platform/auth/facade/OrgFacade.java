package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 组织机构 Facade 实现
 * 实现 OrgApi，全部委托给 OrgService，不包含额外编排逻辑。
 * OrgService 已通过 PermissionCacheService 对子树查询结果进行缓存，
 * Facade 层无需重复缓存处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgFacade implements OrgApi {

    private final OrgService orgService;

    /**
     * 根据机构编码查询机构信息
     *
     * @param orgCode 机构编码
     * @return 机构DTO，不存在时返回 null
     */
    @Override
    public OrgDTO getOrg(String orgCode) {
        return orgService.getOrg(orgCode);
    }

    @Override
    public OrgDTO getOrgByDeptNo(String deptNo) {
        return orgService.getOrgByDeptNo(deptNo);
    }

    /**
     * 按机构编码集合批量查询机构信息（委托 OrgService，替代逐个 getOrg 的 N+1）。
     *
     * @param orgCodes 机构编码集合（可空）
     * @return 命中的机构 DTO 列表
     */
    @Override
    public List<OrgDTO> getOrgsByCodes(java.util.Collection<String> orgCodes) {
        return orgService.getOrgsByCodes(orgCodes);
    }

    /**
     * 查询指定机构及其所有下属机构（含自身）
     * 递归遍历机构树，结果包含根节点本身
     *
     * @param orgCode 根机构编码
     * @return 机构子树列表（含根节点）
     */
    @Override
    public List<OrgDTO> getOrgSubtree(String orgCode) {
        return orgService.getOrgSubtree(orgCode);
    }

    /**
     * 查询指定机构及其所有下属机构编码集合（含自身）
     * 性能优于 getOrgSubtree，仅返回编码，适用于数据权限过滤场景
     *
     * @param orgCode 根机构编码
     * @return 机构编码集合
     */
    @Override
    public Set<String> getOrgSubtreeCodes(String orgCode) {
        return orgService.getOrgSubtreeCodes(orgCode);
    }

    /**
     * 查询指定员工的主机构信息
     * 员工无机构归属记录时抛出 BizException(AUTH-40403)
     *
     * @param empId 员工ID
     * @return 主机构 DTO
     */
    @Override
    public OrgDTO getUserMainOrg(String empId) {
        return orgService.getUserMainOrg(empId);
    }

    /**
     * 根据关键字模糊搜索机构
     * 实时查库，不走缓存，适用于管理端低频搜索场景
     *
     * @param keyword 搜索关键字（机构名称或编码）
     * @param limit   最大返回数量
     * @return 匹配的机构列表
     */
    @Override
    public List<OrgDTO> searchOrgs(String keyword, int limit) {
        return orgService.searchOrgs(keyword, limit);
    }
}
