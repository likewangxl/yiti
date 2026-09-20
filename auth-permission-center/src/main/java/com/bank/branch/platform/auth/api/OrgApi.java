package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;

import java.util.Collection;
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
     * 根据机构编号（EXT_ORG_INFO.DEPT_NO）查询机构信息.
     *
     * <p>部门编号(dept_no) 是来自 xanpd sys_dept 的机构编号，与 org_code 不同口径；
     * 业务侧（如指标/KPI 结果导入）的"机构对象"按部门编号校验存在性时使用。
     *
     * @param deptNo 机构编号（DEPT_NO）
     * @return 机构DTO，不存在时返回 null
     */
    OrgDTO getOrgByDeptNo(String deptNo);

    /**
     * 按机构编号（DEPT_NO）集合批量查询机构信息（一次取数，替代逐个 {@link #getOrgByDeptNo} 的 N+1）。
     *
     * <p>入参为空/全空白返回空列表；未命中的编号不在返回列表中（不补 null），
     * 调用方按 {@link OrgDTO#getDeptNo()} 自行建映射/做存在性校验。dept_no 可能非唯一，含重号全部返回。</p>
     *
     * @param deptNos 机构编号集合（可空）
     * @return 命中的机构 DTO 列表（含重号）
     */
    List<OrgDTO> getOrgsByDeptNos(java.util.Collection<String> deptNos);

    /**
     * 按机构编码集合批量查询机构信息（一次取数，替代逐个 {@link #getOrg} 的 N+1）。
     *
     * <p>入参为空/全空白返回空列表；未命中的编码不在返回列表中（不补 null），
     * 调用方按 {@link OrgDTO#getOrgCode()} 自行建映射。</p>
     *
     * @param orgCodes 机构编码集合（可空）
     * @return 命中的机构 DTO 列表
     */
    List<OrgDTO> getOrgsByCodes(java.util.Collection<String> orgCodes);

    /**
     * 按机构名称集合批量查询机构信息（通讯录导入按机构名称反查编码用）。
     *
     * <p>名称可能重复，返回所有同名机构；调用方需自行处理重名歧义（命中 &gt;1 视为歧义）。</p>
     *
     * @param orgNames 机构名称集合（可空）
     * @return 命中的机构 DTO 列表（含重名）
     */
    List<OrgDTO> getOrgsByNames(java.util.Collection<String> orgNames);

    /**
     * 查询全局唯一机构归属且机构编码属于授权集合的员工映射。
     *
     * <p>仅返回在 {@code EXT_USER_ORG} 中恰好归属一个机构、且该唯一机构命中
     * {@code orgCodes} 的员工；结果不代表全部员工。空或全空白入参返回空列表。</p>
     *
     * @param orgCodes 调用方已授权的机构编码集合
     * @return 员工工号到唯一机构编码的映射
     */
    List<UniqueUserOrgDTO> listUniqueUserOrgs(Collection<String> orgCodes);

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
