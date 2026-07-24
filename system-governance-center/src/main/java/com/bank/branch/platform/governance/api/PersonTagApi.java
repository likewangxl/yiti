package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.PersonTagDTO;

import java.util.List;

/**
 * 业务标签对外 API（原「人员标签」，现支持员工/机构两维成员）。
 * <p>供其他业务模块按标签圈定成员范围（如 KPI 方案的「标签范围」），
 * 以及回显/校验标签名称。跨模块只能经本接口，禁止直连 governance 的 mapper/entity。</p>
 */
public interface PersonTagApi {

    /**
     * 按标签 ID 集合取其下全部<b>员工</b>工号（EMP 维度，多标签取并集、去重）。
     *
     * <p>工号为 {@code PT_USER.USERNAME} 口径（不是 USER_ID 代理键）。仅返回 EMP 维度成员，
     * ORG（机构）维度成员不在此列。入参为空 → 返回空列表；标签不存在或无员工成员 → 该标签不贡献任何工号。</p>
     *
     * @param tagIds 标签 ID 集合
     * @return 去重工号列表
     */
    List<String> getUsernamesByTagIds(List<Long> tagIds);

    /**
     * 按标签 ID 集合取其下全部<b>机构</b>业务编号（ORG 维度，多标签取并集、去重）。
     *
     * <p>机构编号为 {@code EXT_ORG_INFO.DEPT_NO} 口径（不是内部 org_code）。仅返回 ORG 维度成员。
     * 入参为空 → 返回空列表；标签不存在或无机构成员 → 该标签不贡献任何机构编号。</p>
     *
     * @param tagIds 标签 ID 集合
     * @return 去重机构编号（dept_no）列表
     */
    List<String> getDeptNosByTagIds(List<Long> tagIds);

    /**
     * 按标签 ID 集合查标签（用于存在性校验与名称回显）。
     *
     * <p>返回中缺失的 ID 即为「已不存在的标签」，调用方可据此提示范围失效。</p>
     *
     * @param tagIds 标签 ID 集合
     * @return 命中的标签列表（入参为空时返回空列表）
     */
    List<PersonTagDTO> getTagsByIds(List<Long> tagIds);

    /**
     * 按标签名称集合查标签（Excel 导入按名称解析 ID 用）。
     *
     * @param tagNames 标签名称集合
     * @return 命中的标签列表（入参为空时返回空列表）
     */
    List<PersonTagDTO> getTagsByNames(List<String> tagNames);
}
