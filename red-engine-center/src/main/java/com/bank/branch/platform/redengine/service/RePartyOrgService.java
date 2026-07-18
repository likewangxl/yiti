package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.RePartyOrgTreeDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 红色引擎-党组织服务。
 * <p>移植自 redengine {@code PartyOrgServiceImpl}（{@code system.service.impl}），
 * 维护党委/党支部树（RE_PARTY_ORG，parentId 自关联）。是 Task 8-10（材料上报/两级审核/驾驶舱）
 * 组织维度统计与展示的基础数据来源。</p>
 * <p>软删语义：MyBatis-Plus {@code @TableLogic} 已在实体 {@code RePartyOrg.deleted} 生效，
 * {@code selectList}/{@code selectById}/{@code deleteById} 自动过滤/软删已删除记录，无需手工拼 SQL 条件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RePartyOrgService {

    private final RePartyOrgMapper rePartyOrgMapper;

    /**
     * 查询党组织完整父子树。
     * <p>照抄源 {@code PartyOrgServiceImpl.getOrgTree} 语义：全量查询未删除组织后，
     * 按 parentId 分组（parentId 为 null 视为顶层，用 -1L 兜底键），再从顶层节点递归挂载下级，
     * 而非直接对数据库做递归 CTE 查询（组织规模为分行党建体量，内存组树足够，避免多次往返数据库）。</p>
     *
     * @return 顶层党组织列表，每个节点的 children 已递归装配下级
     */
    public List<RePartyOrgTreeDTO> getOrgTree() {
        List<RePartyOrg> allOrgs = rePartyOrgMapper.selectList(null);

        Map<Long, List<RePartyOrg>> groupByParent = allOrgs.stream()
                .collect(Collectors.groupingBy(org -> org.getParentId() == null ? -1L : org.getParentId()));

        List<RePartyOrg> rootOrgs = groupByParent.getOrDefault(-1L, Collections.emptyList());
        List<RePartyOrgTreeDTO> tree = new ArrayList<>();
        for (RePartyOrg root : rootOrgs) {
            tree.add(buildTree(root, groupByParent));
        }
        return tree;
    }

    /**
     * 递归组装单节点及其全部下级（内部私有，getOrgTree 专用）。
     */
    private RePartyOrgTreeDTO buildTree(RePartyOrg org, Map<Long, List<RePartyOrg>> groupByParent) {
        RePartyOrgTreeDTO dto = toDto(org);
        List<RePartyOrg> children = groupByParent.getOrDefault(org.getId(), Collections.emptyList());
        for (RePartyOrg child : children) {
            dto.getChildren().add(buildTree(child, groupByParent));
        }
        return dto;
    }

    /**
     * 按ID查询党组织详情。
     *
     * @param id 党组织ID
     * @return 党组织详情（children 为空列表）；不存在时返回 null（与源系统一致，交由调用方决定是否视为异常）
     */
    public RePartyOrgTreeDTO getById(Long id) {
        RePartyOrg org = rePartyOrgMapper.selectById(id);
        return org == null ? null : toDto(org);
    }

    /**
     * 新增党组织。
     *
     * @param org 党组织实体（MyBatis-Plus insert 后自动回填自增主键到入参对象）
     * @return 新增记录的党组织ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long add(RePartyOrg org) {
        rePartyOrgMapper.insert(org);
        log.info("[RePartyOrgService.add] id={}, orgName={}", org.getId(), org.getOrgName());
        return org.getId();
    }

    /**
     * 修改党组织。
     *
     * @param id  党组织ID（以路径参数为准，覆盖 body 中可能携带的 id，防止越权改到别的记录）
     * @param org 党组织更新内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RePartyOrg org) {
        org.setId(id);
        rePartyOrgMapper.updateById(org);
        log.info("[RePartyOrgService.update] id={}", id);
    }

    /**
     * 删除党组织（软删，MyBatis-Plus {@code @TableLogic} 落 deleted=1）。
     * <p><b>新增防呆</b>（源系统 {@code PartyOrgServiceImpl.delete} 无此校验）：删除前先查是否存在
     * parentId=id 的未删除下级党组织，存在则拒绝删除，避免留下断链的孤儿节点（子节点 parentId
     * 指向一个已被删除的组织，getOrgTree 分组时会把这些孤儿静默丢失，长期积累导致组织树数据错乱且难以察觉）。</p>
     *
     * @param id 党组织ID
     * @throws BizException code=RE-40002，存在下级党组织时禁止删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long childCount = rePartyOrgMapper.selectCount(
                new LambdaQueryWrapper<RePartyOrg>().eq(RePartyOrg::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException("RE-40002", "存在下级党组织不可删除");
        }
        rePartyOrgMapper.deleteById(id);
        log.info("[RePartyOrgService.delete] id={}", id);
    }

    /**
     * 实体 -> 树节点 DTO 转换（内部私有，不跨模块暴露实体）。
     */
    private RePartyOrgTreeDTO toDto(RePartyOrg entity) {
        RePartyOrgTreeDTO dto = new RePartyOrgTreeDTO();
        dto.setId(entity.getId());
        dto.setOrgName(entity.getOrgName());
        dto.setParentId(entity.getParentId());
        dto.setOrgLevel(entity.getOrgLevel());
        dto.setOrgCode(entity.getOrgCode());
        dto.setOrgType(entity.getOrgType());
        dto.setPrincipal(entity.getPrincipal());
        dto.setContactPhone(entity.getContactPhone());
        dto.setOrgAddress(entity.getOrgAddress());
        dto.setSecretaryId(entity.getSecretaryId());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
