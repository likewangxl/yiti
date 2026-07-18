package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReUserPartyMapDTO;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 红色引擎-用户党组织映射服务。
 * <p>维护平台用户(PT_USER.USER_ID)与党组织(RE_PARTY_ORG)的绑定关系（RE_USER_PARTY_MAP，uk_user 唯一）。
 * 是 Task 7-10（党组织管理/材料上报/两级审核/驾驶舱）判定"当前用户归属哪个党组织"的唯一权威来源。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReUserPartyMapService {

    private final ReUserPartyMapMapper reUserPartyMapMapper;

    /**
     * 查询指定员工必须绑定的党组织ID。
     * <p>供上报/审核/驾驶舱等业务场景在写操作前确定"归属党组织"，无映射时视为配置缺失，
     * 直接拒绝并提示联系管理员补配，而非静默放行或落到默认组织（避免数据归属错乱）。</p>
     *
     * @param empId 平台用户工号
     * @return 党组织ID
     * @throws BizException code=RE-40001，当前用户未绑定党组织
     */
    public Long getRequiredPartyOrgId(String empId) {
        ReUserPartyMap map = reUserPartyMapMapper.selectOne(
                new LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, empId));
        if (map == null) {
            throw new BizException("RE-40001", "当前用户未绑定党组织，请联系管理员");
        }
        return map.getPartyOrgId();
    }

    /**
     * 绑定/更新用户党组织映射（管理端高危操作）。
     * <p>uk_user 唯一约束：先按 userId 查是否已存在映射，命中则原地更新党组织与角色（保留原 id/创建时间），
     * 未命中则新增，避免唯一键冲突。</p>
     *
     * @param userId     平台用户工号
     * @param partyOrgId 党组织ID
     * @param partyRole  党内角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(String userId, Long partyOrgId, String partyRole) {
        ReUserPartyMap existing = reUserPartyMapMapper.selectOne(
                new LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, userId));
        if (existing != null) {
            // 已存在映射：按主键更新，不重新 insert，避免触发 uk_user 唯一键冲突
            existing.setPartyOrgId(partyOrgId);
            existing.setPartyRole(partyRole);
            reUserPartyMapMapper.updateById(existing);
            log.info("[ReUserPartyMapService.bind] update userId={}, partyOrgId={}, partyRole={}", userId, partyOrgId, partyRole);
        } else {
            ReUserPartyMap entity = new ReUserPartyMap();
            entity.setUserId(userId);
            entity.setPartyOrgId(partyOrgId);
            entity.setPartyRole(partyRole);
            reUserPartyMapMapper.insert(entity);
            log.info("[ReUserPartyMapService.bind] insert userId={}, partyOrgId={}, partyRole={}", userId, partyOrgId, partyRole);
        }
    }

    /**
     * 查询全部用户党组织映射列表（管理端使用，未做分页，规模为运营人员级别体量）。
     */
    public List<ReUserPartyMapDTO> list() {
        return reUserPartyMapMapper.selectList(null).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 实体 -> 对外 DTO 转换（内部私有，不跨模块暴露实体）。
     */
    private ReUserPartyMapDTO toDto(ReUserPartyMap entity) {
        ReUserPartyMapDTO dto = new ReUserPartyMapDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setPartyOrgId(entity.getPartyOrgId());
        dto.setPartyRole(entity.getPartyRole());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
