package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberRespDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagRespDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.PersonTagMapper;
import com.bank.branch.platform.governance.mapper.PersonTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务标签服务（全平台通用，原「人员标签」）。
 * <p>标签 CRUD（删除级联删关联）+ 标签成员管理（按维度新增/修改/删除/分页查询）。</p>
 * <p>成员按维度分两类：EMP=员工（存工号 {@code PT_USER.USERNAME}）、ORG=机构（存业务编号
 * {@code EXT_ORG_INFO.DEPT_NO}）。员工维度只展示工号（不再解析姓名）；机构维度按 dept_no 实时解析机构名称。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonTagService {

    private final PersonTagMapper tagMapper;
    private final PersonTagRelMapper relMapper;
    private final UserApi userApi;
    /** 机构维度成员：dept_no 存在性校验 + 名称回显（EXT_ORG_INFO 口径，经 auth OrgApi）. */
    private final OrgApi orgApi;

    /**
     * 分页查询标签列表（含关联成员数，员工+机构合计）。
     *
     * @param keyword  标签名称模糊关键字（可空）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页结果
     */
    public PageResult<PersonTagRespDTO> pageTags(String keyword, int pageNo, int pageSize) {
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        long total = tagMapper.countByKeyword(keyword);
        List<PersonTagRespDTO> records = total == 0
                ? List.of()
                : tagMapper.selectPageWithMemberCount(keyword, (safeNo - 1) * safeSize, safeSize);
        return PageResult.of(safeNo, safeSize, total, records);
    }

    /**
     * 创建标签。
     *
     * @param tagName  标签名称（全局唯一）
     * @param remark   备注
     * @param operator 操作人
     * @return 新建标签
     * @throws BizException GOV-40904 名称已存在
     */
    public PersonTag createTag(String tagName, String remark, String operator) {
        String name = tagName == null ? "" : tagName.trim();
        if (tagMapper.selectByTagName(name) != null) {
            throw new BizException(GovErrorCode.PERSON_TAG_NAME_DUPLICATE.getCode(),
                    GovErrorCode.PERSON_TAG_NAME_DUPLICATE.getMessage());
        }
        PersonTag tag = new PersonTag();
        tag.setTagName(name);
        tag.setRemark(remark);
        tag.setCreateBy(operator);
        tagMapper.insert(tag);
        log.info("[PersonTagService.createTag] operator={}, tagId={}, tagName={}",
                operator, tag.getTagId(), name);
        return tag;
    }

    /**
     * 更新标签（名称/备注）。
     *
     * @param tagId    标签 ID
     * @param tagName  新名称
     * @param remark   新备注
     * @param operator 操作人
     * @throws BizException GOV-40008 标签不存在；GOV-40904 名称与其他标签重复
     */
    public void updateTag(Long tagId, String tagName, String remark, String operator) {
        PersonTag tag = requireTag(tagId);
        String name = tagName == null ? "" : tagName.trim();
        PersonTag sameName = tagMapper.selectByTagName(name);
        if (sameName != null && !sameName.getTagId().equals(tagId)) {
            throw new BizException(GovErrorCode.PERSON_TAG_NAME_DUPLICATE.getCode(),
                    GovErrorCode.PERSON_TAG_NAME_DUPLICATE.getMessage());
        }
        tag.setTagName(name);
        tag.setRemark(remark);
        tag.setUpdateBy(operator);
        tagMapper.updateById(tag);
        log.info("[PersonTagService.updateTag] operator={}, tagId={}, tagName={}", operator, tagId, name);
    }

    /**
     * 删除标签，级联删除其下全部成员关联（不分维度）。
     *
     * @param tagId 标签 ID
     * @throws BizException GOV-40008 标签不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long tagId) {
        requireTag(tagId);
        int relCount = relMapper.deleteByTagId(tagId);
        tagMapper.deleteById(tagId);
        log.info("[PersonTagService.deleteTag] tagId={}, cascadeRelCount={}", tagId, relCount);
    }

    /**
     * 分页查询标签某维度下的成员。
     * <p>EMP 维度：仅工号（不再解析姓名）；ORG 维度：机构编号 + 机构名称（按 dept_no 当页批量实时解析）。</p>
     *
     * @param tagId    标签 ID
     * @param dimType  成员维度（EMP/ORG，空按 EMP）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页结果
     * @throws BizException GOV-40008 标签不存在
     */
    public PageResult<PersonTagMemberRespDTO> pageMembers(Long tagId, String dimType, int pageNo, int pageSize) {
        requireTag(tagId);
        String dim = normalizeDim(dimType);
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        long total = relMapper.countByTagId(tagId, dim);
        if (total == 0) {
            return PageResult.of(safeNo, safeSize, 0, List.of());
        }
        List<PersonTagRel> rels = relMapper.selectPageByTagId(tagId, dim, (safeNo - 1) * safeSize, safeSize);
        List<PersonTagMemberRespDTO> records = PersonTagRel.DIM_ORG.equals(dim)
                ? toOrgMemberDtos(rels)
                : toEmpMemberDtos(rels);
        return PageResult.of(safeNo, safeSize, total, records);
    }

    /** EMP 成员行 → DTO（仅工号，无姓名解析）。 */
    private List<PersonTagMemberRespDTO> toEmpMemberDtos(List<PersonTagRel> rels) {
        List<PersonTagMemberRespDTO> records = new ArrayList<>(rels.size());
        for (PersonTagRel rel : rels) {
            PersonTagMemberRespDTO dto = new PersonTagMemberRespDTO();
            dto.setId(rel.getId());
            dto.setDimType(PersonTagRel.DIM_EMP);
            dto.setUsername(rel.getUsername());
            dto.setCreateTime(rel.getCreateTime());
            records.add(dto);
        }
        return records;
    }

    /** ORG 成员行 → DTO（机构编号 + 按 dept_no 批量解析机构名称）。 */
    private List<PersonTagMemberRespDTO> toOrgMemberDtos(List<PersonTagRel> rels) {
        List<String> deptNos = rels.stream().map(PersonTagRel::getOrgDeptNo).toList();
        Map<String, String> orgNameByDeptNo = new HashMap<>();
        List<OrgDTO> orgs = orgApi.getOrgsByDeptNos(deptNos);
        if (orgs != null) {
            for (OrgDTO o : orgs) {
                if (o != null && o.getDeptNo() != null) {
                    orgNameByDeptNo.putIfAbsent(o.getDeptNo(), o.getOrgName());
                }
            }
        }
        List<PersonTagMemberRespDTO> records = new ArrayList<>(rels.size());
        for (PersonTagRel rel : rels) {
            PersonTagMemberRespDTO dto = new PersonTagMemberRespDTO();
            dto.setId(rel.getId());
            dto.setDimType(PersonTagRel.DIM_ORG);
            dto.setOrgDeptNo(rel.getOrgDeptNo());
            dto.setOrgName(orgNameByDeptNo.get(rel.getOrgDeptNo()));
            dto.setCreateTime(rel.getCreateTime());
            records.add(dto);
        }
        return records;
    }

    /**
     * 向标签批量新增成员（员工工号 + 机构编号可同时提交）；已在标签下的同维度成员跳过。
     * <p>员工工号须全部存在于 PT_USER；机构编号须全部存在于 EXT_ORG_INFO.DEPT_NO。</p>
     *
     * @param tagId      标签 ID
     * @param usernames  员工工号列表（可空）
     * @param orgDeptNos 机构编号列表（可空）
     * @param operator   操作人
     * @return 实际新增条数（员工+机构合计，不含跳过）
     * @throws BizException GOV-40008 标签不存在；GOV-42208 存在无效工号；GOV-42209 存在无效机构编号；
     *                      GOV-42210 员工/机构编号皆空
     */
    @Transactional(rollbackFor = Exception.class)
    public int addMembers(Long tagId, List<String> usernames, List<String> orgDeptNos, String operator) {
        requireTag(tagId);
        List<String> emps = normalize(usernames);
        List<String> orgs = normalize(orgDeptNos);
        if (emps.isEmpty() && orgs.isEmpty()) {
            throw new BizException(GovErrorCode.PERSON_TAG_MEMBER_EMPTY.getCode(),
                    GovErrorCode.PERSON_TAG_MEMBER_EMPTY.getMessage());
        }
        List<PersonTagRel> rows = new ArrayList<>();
        int skipped = 0;
        // 员工维度
        if (!emps.isEmpty()) {
            requireAllUsernamesExist(emps);
            Set<String> existing = new HashSet<>(relMapper.selectUsernamesByTagId(tagId));
            for (String username : emps) {
                if (existing.contains(username)) {
                    skipped++;
                    continue;
                }
                PersonTagRel rel = new PersonTagRel();
                rel.setTagId(tagId);
                rel.setDimType(PersonTagRel.DIM_EMP);
                rel.setUsername(username);
                rel.setCreateBy(operator);
                rows.add(rel);
            }
        }
        // 机构维度
        if (!orgs.isEmpty()) {
            requireAllDeptNosExist(orgs);
            Set<String> existing = new HashSet<>(relMapper.selectDeptNosByTagId(tagId));
            for (String deptNo : orgs) {
                if (existing.contains(deptNo)) {
                    skipped++;
                    continue;
                }
                PersonTagRel rel = new PersonTagRel();
                rel.setTagId(tagId);
                rel.setDimType(PersonTagRel.DIM_ORG);
                rel.setOrgDeptNo(deptNo);
                rel.setCreateBy(operator);
                rows.add(rel);
            }
        }
        if (!rows.isEmpty()) {
            relMapper.insertBatch(rows);
        }
        log.info("[PersonTagService.addMembers] operator={}, tagId={}, addedEmp+Org={}, skipped={}",
                operator, tagId, rows.size(), skipped);
        return rows.size();
    }

    /**
     * 修改成员（把关联行换成另一个同维度成员）。
     * <p>行的维度不可变：EMP 行传新工号、ORG 行传新机构编号。</p>
     *
     * @param tagId       标签 ID
     * @param relId       关联行 ID
     * @param newUsername 新工号（改 EMP 行时用）
     * @param newDeptNo   新机构编号（改 ORG 行时用）
     * @param operator    操作人
     * @throws BizException GOV-40009 关联行不存在或不属于该标签；GOV-42208/42209 成员无效；GOV-40905 已在标签下
     */
    public void updateMember(Long tagId, Long relId, String newUsername, String newDeptNo, String operator) {
        PersonTagRel rel = relMapper.selectById(relId);
        if (rel == null || !rel.getTagId().equals(tagId)) {
            throw new BizException(GovErrorCode.PERSON_TAG_MEMBER_NOT_FOUND.getCode(),
                    GovErrorCode.PERSON_TAG_MEMBER_NOT_FOUND.getMessage());
        }
        if (PersonTagRel.DIM_ORG.equals(rel.getDimType())) {
            String deptNo = newDeptNo == null ? "" : newDeptNo.trim();
            if (deptNo.isEmpty()) {
                throw new BizException(GovErrorCode.PERSON_TAG_DEPTNO_NOT_EXISTS.getCode(),
                        GovErrorCode.PERSON_TAG_DEPTNO_NOT_EXISTS.getMessage());
            }
            if (deptNo.equals(rel.getOrgDeptNo())) {
                return;
            }
            requireAllDeptNosExist(List.of(deptNo));
            if (relMapper.selectDeptNosByTagId(tagId).contains(deptNo)) {
                throw new BizException(GovErrorCode.PERSON_TAG_MEMBER_DUPLICATE.getCode(),
                        GovErrorCode.PERSON_TAG_MEMBER_DUPLICATE.getMessage());
            }
            rel.setOrgDeptNo(deptNo);
            relMapper.updateById(rel);
            log.info("[PersonTagService.updateMember] operator={}, tagId={}, relId={}, dim=ORG, newDeptNo={}",
                    operator, tagId, relId, deptNo);
            return;
        }
        // 默认按员工维度处理（含存量 EMP 行）
        String username = newUsername == null ? "" : newUsername.trim();
        if (username.isEmpty()) {
            throw new BizException(GovErrorCode.PERSON_TAG_USERNAME_NOT_EXISTS.getCode(),
                    GovErrorCode.PERSON_TAG_USERNAME_NOT_EXISTS.getMessage());
        }
        if (username.equals(rel.getUsername())) {
            return;
        }
        requireAllUsernamesExist(List.of(username));
        if (relMapper.selectUsernamesByTagId(tagId).contains(username)) {
            throw new BizException(GovErrorCode.PERSON_TAG_MEMBER_DUPLICATE.getCode(),
                    GovErrorCode.PERSON_TAG_MEMBER_DUPLICATE.getMessage());
        }
        rel.setUsername(username);
        relMapper.updateById(rel);
        log.info("[PersonTagService.updateMember] operator={}, tagId={}, relId={}, dim=EMP, newUsername={}",
                operator, tagId, relId, username);
    }

    /**
     * 删除单个成员关联。
     *
     * @param tagId 标签 ID
     * @param relId 关联行 ID
     * @throws BizException GOV-40009 关联行不存在或不属于该标签
     */
    public void removeMember(Long tagId, Long relId) {
        PersonTagRel rel = relMapper.selectById(relId);
        if (rel == null || !rel.getTagId().equals(tagId)) {
            throw new BizException(GovErrorCode.PERSON_TAG_MEMBER_NOT_FOUND.getCode(),
                    GovErrorCode.PERSON_TAG_MEMBER_NOT_FOUND.getMessage());
        }
        relMapper.deleteById(relId);
        log.info("[PersonTagService.removeMember] tagId={}, relId={}, dim={}, username={}, deptNo={}",
                tagId, relId, rel.getDimType(), rel.getUsername(), rel.getOrgDeptNo());
    }

    /**
     * 取标签，不存在抛 GOV-40008。
     *
     * @param tagId 标签 ID
     * @return 标签
     */
    public PersonTag requireTag(Long tagId) {
        PersonTag tag = tagId == null ? null : tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BizException(GovErrorCode.PERSON_TAG_NOT_FOUND.getCode(),
                    GovErrorCode.PERSON_TAG_NOT_FOUND.getMessage());
        }
        return tag;
    }

    /**
     * 校验工号全部存在于 PT_USER（单次/分片 IN，不逐行查询）。
     *
     * @param usernames 去重后的工号列表
     * @throws BizException GOV-42208 存在无效工号（消息附无效清单）
     */
    private void requireAllUsernamesExist(List<String> usernames) {
        Set<String> existing = new HashSet<>(userApi.filterExistingUsernames(usernames));
        List<String> missing = usernames.stream().filter(u -> !existing.contains(u)).toList();
        if (!missing.isEmpty()) {
            throw new BizException(GovErrorCode.PERSON_TAG_USERNAME_NOT_EXISTS.getCode(),
                    GovErrorCode.PERSON_TAG_USERNAME_NOT_EXISTS.getMessage() + ": " + String.join(",", missing));
        }
    }

    /**
     * 校验机构编号全部存在于 EXT_ORG_INFO.DEPT_NO（单次批量，不逐行查询）。
     *
     * @param deptNos 去重后的机构编号列表
     * @throws BizException GOV-42209 存在无效机构编号（消息附无效清单）
     */
    private void requireAllDeptNosExist(List<String> deptNos) {
        Set<String> existing = new HashSet<>();
        List<OrgDTO> orgs = orgApi.getOrgsByDeptNos(deptNos);
        if (orgs != null) {
            for (OrgDTO o : orgs) {
                if (o != null && o.getDeptNo() != null) {
                    existing.add(o.getDeptNo());
                }
            }
        }
        List<String> missing = deptNos.stream().filter(d -> !existing.contains(d)).toList();
        if (!missing.isEmpty()) {
            throw new BizException(GovErrorCode.PERSON_TAG_DEPTNO_NOT_EXISTS.getCode(),
                    GovErrorCode.PERSON_TAG_DEPTNO_NOT_EXISTS.getMessage() + ": " + String.join(",", missing));
        }
    }

    /** 维度清洗：仅接受 EMP/ORG，空/非法按 EMP。 */
    private static String normalizeDim(String dimType) {
        return PersonTagRel.DIM_ORG.equalsIgnoreCase(dimType) ? PersonTagRel.DIM_ORG : PersonTagRel.DIM_EMP;
    }

    /** 标识列表清洗：去空白、去空项、保序去重。 */
    private static List<String> normalize(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    out.add(v.trim());
                }
            }
        }
        return new ArrayList<>(out);
    }
}
