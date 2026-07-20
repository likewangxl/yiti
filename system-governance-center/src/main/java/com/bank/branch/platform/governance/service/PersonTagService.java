package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
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
 * 人员标签服务（全平台通用）。
 * <p>标签 CRUD（删除级联删关联）+ 标签成员管理（新增/修改/删除/分页查询）。</p>
 * <p>成员的姓名/机构不落库，展示时按工号经 {@link UserApi#getUsersByUsernames} 批量实时解析，
 * 避免冗余存储过期（工号是 PT_USER.USERNAME 口径，不是 USER_ID 代理键）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonTagService {

    private final PersonTagMapper tagMapper;
    private final PersonTagRelMapper relMapper;
    private final UserApi userApi;

    /**
     * 分页查询标签列表（含关联人数）。
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
     * 删除标签，级联删除其下全部人员关联。
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
     * 分页查询标签成员（工号/姓名/机构），姓名与机构按当前页工号批量实时解析。
     *
     * @param tagId    标签 ID
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页结果
     * @throws BizException GOV-40008 标签不存在
     */
    public PageResult<PersonTagMemberRespDTO> pageMembers(Long tagId, int pageNo, int pageSize) {
        requireTag(tagId);
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        long total = relMapper.countByTagId(tagId);
        if (total == 0) {
            return PageResult.of(safeNo, safeSize, 0, List.of());
        }
        List<PersonTagRel> rels = relMapper.selectPageByTagId(tagId, (safeNo - 1) * safeSize, safeSize);
        List<String> usernames = rels.stream().map(PersonTagRel::getUsername).toList();
        // 单次批量按工号解析姓名/机构；已被删除的用户解析不到，行内展示空
        Map<String, UserDTO> userByUsername = new HashMap<>();
        for (UserDTO u : userApi.getUsersByUsernames(usernames)) {
            userByUsername.put(u.getUsername(), u);
        }
        List<PersonTagMemberRespDTO> records = new ArrayList<>(rels.size());
        for (PersonTagRel rel : rels) {
            PersonTagMemberRespDTO dto = new PersonTagMemberRespDTO();
            dto.setId(rel.getId());
            dto.setUsername(rel.getUsername());
            dto.setCreateTime(rel.getCreateTime());
            UserDTO u = userByUsername.get(rel.getUsername());
            if (u != null) {
                dto.setDisplayName(u.getDisplayName());
                dto.setOrgCode(u.getMainOrgCode());
                dto.setOrgName(u.getMainOrgName());
            }
            records.add(dto);
        }
        return PageResult.of(safeNo, safeSize, total, records);
    }

    /**
     * 向标签批量新增成员；工号必须全部存在于 PT_USER，已在标签下的工号跳过。
     *
     * @param tagId     标签 ID
     * @param usernames 工号列表
     * @param operator  操作人
     * @return 实际新增条数（不含跳过）
     * @throws BizException GOV-40008 标签不存在；GOV-42208 存在无效工号
     */
    @Transactional(rollbackFor = Exception.class)
    public int addMembers(Long tagId, List<String> usernames, String operator) {
        requireTag(tagId);
        List<String> distinct = normalize(usernames);
        if (distinct.isEmpty()) {
            return 0;
        }
        requireAllUsernamesExist(distinct);
        Set<String> existing = new HashSet<>(relMapper.selectUsernamesByTagId(tagId));
        List<PersonTagRel> rows = new ArrayList<>();
        for (String username : distinct) {
            if (existing.contains(username)) {
                continue;
            }
            PersonTagRel rel = new PersonTagRel();
            rel.setTagId(tagId);
            rel.setUsername(username);
            rel.setCreateBy(operator);
            rows.add(rel);
        }
        if (!rows.isEmpty()) {
            relMapper.insertBatch(rows);
        }
        log.info("[PersonTagService.addMembers] operator={}, tagId={}, added={}, skipped={}",
                operator, tagId, rows.size(), distinct.size() - rows.size());
        return rows.size();
    }

    /**
     * 修改成员（把关联行换成另一个工号）。
     *
     * @param tagId       标签 ID
     * @param relId       关联行 ID
     * @param newUsername 新工号
     * @param operator    操作人
     * @throws BizException GOV-40009 关联行不存在或不属于该标签；GOV-42208 工号无效；GOV-40905 新工号已在标签下
     */
    public void updateMember(Long tagId, Long relId, String newUsername, String operator) {
        PersonTagRel rel = relMapper.selectById(relId);
        if (rel == null || !rel.getTagId().equals(tagId)) {
            throw new BizException(GovErrorCode.PERSON_TAG_MEMBER_NOT_FOUND.getCode(),
                    GovErrorCode.PERSON_TAG_MEMBER_NOT_FOUND.getMessage());
        }
        String username = newUsername == null ? "" : newUsername.trim();
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
        log.info("[PersonTagService.updateMember] operator={}, tagId={}, relId={}, newUsername={}",
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
        log.info("[PersonTagService.removeMember] tagId={}, relId={}, username={}",
                tagId, relId, rel.getUsername());
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

    /** 工号列表清洗：去空白、去空项、保序去重。 */
    private static List<String> normalize(List<String> usernames) {
        Set<String> out = new LinkedHashSet<>();
        if (usernames != null) {
            for (String u : usernames) {
                if (u != null && !u.isBlank()) {
                    out.add(u.trim());
                }
            }
        }
        return new ArrayList<>(out);
    }
}
