package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReUserPartyMapDTO;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final UserApi userApi;

    /**
     * 查询指定员工必须绑定的党组织ID。
     * <p>供上报/审核/驾驶舱等业务场景在写操作前确定"归属党组织"，无映射时视为配置缺失，
     * 直接拒绝并提示联系管理员补配，而非静默放行或落到默认组织（避免数据归属错乱）。</p>
     *
     * @param userId 平台用户ID（PT_USER.USER_ID）
     * @return 党组织ID
     * @throws BizException code=RE-40001，当前用户未绑定党组织
     */
    public Long getRequiredPartyOrgId(String userId) {
        ReUserPartyMap map = reUserPartyMapMapper.selectOne(
                new LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, userId));
        if (map == null) {
            throw new BizException("RE-40001", "当前用户未绑定党组织，请联系管理员");
        }
        return map.getPartyOrgId();
    }

    /**
     * 绑定/更新用户党组织映射（管理端高危操作）。
     * <p>uk_user 唯一约束：先按 userId 查是否已存在映射，命中则原地更新党组织与角色（保留原 id/创建时间），
     * 未命中则新增。</p>
     * <p>写入前通过 {@link UserApi#getUserByEmpId} 校验 userId 必须对应真实平台用户；数据库只保存
     * {@code PT_USER.USER_ID}，不接受前端把展示用 {@code PT_USER.USERNAME} 当作主键提交。</p>
     * <p><b>并发窗口兜底</b>：检查(selectOne)与写入(insert)之间存在经典的先查后插竞态——两个请求同时对
     * 同一个此前从未绑定过的 userId 调用 bind()，都会在 selectOne 阶段查到 null，随后都执行 insert，
     * 后完成的一个必然撞上 uk_user 唯一键抛出 {@link DuplicateKeyException}。bind() 语义是幂等 upsert
     * （调用方只关心"最终这个用户绑定到了哪个党组织"，不关心谁先谁后），因此这里不把并发冲突当系统异常
     * 抛给前端（避免不必要的 SYS_500 与 ERROR 级别堆栈噪音），而是重新查询命中赢家记录后收敛为 update，
     * 与"先查到已存在"分支的最终效果完全一致。参照平台既有惯例（如
     * performance-engine-center.TargetPlanService、customer-marketing-center.ClaimService 对
     * DuplicateKeyException 的显式 catch 处理）。</p>
     *
     * @param userId     平台用户ID（PT_USER.USER_ID）
     * @param partyOrgId 党组织ID
     * @param partyRole  党内角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(String userId, Long partyOrgId, String partyRole) {
        // 前端下拉只是交互约束，服务端仍须通过 auth 权威 API 校验 USER_ID，
        // 防止绕过页面直接提交不存在或误把 USERNAME 当 USER_ID 的值。
        if (userApi.getUserByEmpId(userId) == null) {
            throw new BizException("RE-40009", "平台用户不存在，无法绑定党组织");
        }
        ReUserPartyMap existing = selectByUserId(userId);
        if (existing != null) {
            // 已存在映射：按主键更新，不重新 insert，避免触发 uk_user 唯一键冲突
            updateMapping(existing, partyOrgId, partyRole);
            log.info("[ReUserPartyMapService.bind] update userId={}, partyOrgId={}, partyRole={}", userId, partyOrgId, partyRole);
            return;
        }

        ReUserPartyMap entity = new ReUserPartyMap();
        entity.setUserId(userId);
        entity.setPartyOrgId(partyOrgId);
        entity.setPartyRole(partyRole);
        try {
            reUserPartyMapMapper.insert(entity);
            log.info("[ReUserPartyMapService.bind] insert userId={}, partyOrgId={}, partyRole={}", userId, partyOrgId, partyRole);
        } catch (DuplicateKeyException e) {
            // 并发窗口命中：insert 撞 uk_user 唯一键，说明另一个并发请求已抢先落库。
            // 重新查询该赢家记录并收敛为 update，使本次 bind() 调用最终生效（幂等）。
            log.warn("[ReUserPartyMapService.bind] insert 撞 uk_user 唯一键(并发冲突)，收敛为重查+update, userId={}", userId, e);
            ReUserPartyMap winner = selectByUserId(userId);
            if (winner == null) {
                // 理论上不会出现：唯一键冲突即代表记录已存在。重查仍未命中意味着出现了更极端的
                // 数据异常（例如冲突记录被并发删除），此时不静默吞掉，原样抛出保留现场供排查。
                throw e;
            }
            updateMapping(winner, partyOrgId, partyRole);
            log.info("[ReUserPartyMapService.bind] concurrent-conflict converge to update userId={}, partyOrgId={}, partyRole={}",
                    userId, partyOrgId, partyRole);
        }
    }

    /**
     * 按平台用户ID查询映射（bind 入口预查与并发冲突后重查共用）。
     */
    private ReUserPartyMap selectByUserId(String userId) {
        return reUserPartyMapMapper.selectOne(
                new LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, userId));
    }

    /**
     * 对已存在的映射记录原地更新党组织与角色（保留原 id/创建时间）。
     */
    private void updateMapping(ReUserPartyMap existing, Long partyOrgId, String partyRole) {
        existing.setPartyOrgId(partyOrgId);
        existing.setPartyRole(partyRole);
        reUserPartyMapMapper.updateById(existing);
    }

    /**
     * 查询全部用户党组织映射列表（管理端使用，未做分页，规模为运营人员级别体量）。
     * 通过 auth {@link UserApi#mapEmpIdsToUsername} 一次性把持久化 USER_ID 转为展示用 USERNAME，避免逐行查询。
     */
    public List<ReUserPartyMapDTO> list() {
        List<ReUserPartyMap> mappings = reUserPartyMapMapper.selectList(null);
        List<String> userIds = mappings.stream()
                .map(ReUserPartyMap::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<String, String> usernameByUserId = userIds.isEmpty()
                ? Map.of()
                : userApi.mapEmpIdsToUsername(userIds);
        return mappings.stream()
                .map(entity -> toDto(entity, usernameByUserId.get(entity.getUserId()), null))
                .toList();
    }

    /**
     * 按用户工号、姓名、党组织和党内角色分页查询映射。
     * <p>工号/姓名属于 auth 域，必须先通过 {@link UserApi#findUsersByUsernameAndDisplayName}
     * 形成候选 USER_ID，再把候选集合放入本域 MyBatis-Plus 分页条件；因此过滤发生在分页之前，
     * total 与 records 使用同一组条件。候选为空时直接返回空页，避免生成空 IN 或误查全表。</p>
     *
     * @param pageNo      页码，最小 1
     * @param pageSize    每页条数，默认 10，最大 100
     * @param username    登录工号模糊条件（PT_USER.USERNAME）
     * @param displayName 中文姓名模糊条件（PT_USER.USERCHNNAME）
     * @param partyOrgId  党组织精确条件
     * @param partyRole   党内角色精确条件
     * @return 公共分页结果
     */
    public PageResult<ReUserPartyMapDTO> page(int pageNo, int pageSize,
                                               String username, String displayName,
                                               Long partyOrgId, String partyRole) {
        int normalizedPageNo = pageNo < 1 ? 1 : pageNo;
        int normalizedPageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        String normalizedUsername = normalizeText(username);
        String normalizedDisplayName = normalizeText(displayName);
        String normalizedPartyRole = normalizeText(partyRole);

        List<String> candidateUserIds = null;
        if (normalizedUsername != null || normalizedDisplayName != null) {
            List<UserDTO> matchedUsers = userApi.findUsersByUsernameAndDisplayName(
                    normalizedUsername, normalizedDisplayName);
            candidateUserIds = matchedUsers == null ? List.of() : matchedUsers.stream()
                    .map(UserDTO::getEmpId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            if (candidateUserIds.isEmpty()) {
                return PageResult.of(normalizedPageNo, normalizedPageSize, 0L, List.of());
            }
        }

        LambdaQueryWrapper<ReUserPartyMap> wrapper = new LambdaQueryWrapper<ReUserPartyMap>()
                .in(candidateUserIds != null, ReUserPartyMap::getUserId, candidateUserIds)
                .eq(partyOrgId != null, ReUserPartyMap::getPartyOrgId, partyOrgId)
                .eq(normalizedPartyRole != null, ReUserPartyMap::getPartyRole, normalizedPartyRole)
                .orderByDesc(ReUserPartyMap::getCreateTime)
                .orderByDesc(ReUserPartyMap::getId);
        IPage<ReUserPartyMap> mappingPage = reUserPartyMapMapper.selectPage(
                new Page<>(normalizedPageNo, normalizedPageSize), wrapper);

        List<String> pageUserIds = mappingPage.getRecords().stream()
                .map(ReUserPartyMap::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<String, UserDTO> userById = pageUserIds.isEmpty()
                ? Map.of()
                : userApi.getUserByEmpIds(pageUserIds).stream()
                .filter(user -> user.getEmpId() != null)
                .collect(Collectors.toMap(UserDTO::getEmpId, Function.identity(), (first, ignored) -> first));
        List<ReUserPartyMapDTO> records = mappingPage.getRecords().stream()
                .map(entity -> {
                    UserDTO user = userById.get(entity.getUserId());
                    return toDto(entity,
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getDisplayName());
                })
                .toList();
        return PageResult.of(normalizedPageNo, normalizedPageSize, mappingPage.getTotal(), records);
    }

    /**
     * 实体 -> 对外 DTO 转换（内部私有，不跨模块暴露实体）。
     */
    private ReUserPartyMapDTO toDto(ReUserPartyMap entity, String username, String displayName) {
        ReUserPartyMapDTO dto = new ReUserPartyMapDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setUsername(username);
        dto.setDisplayName(displayName);
        dto.setPartyOrgId(entity.getPartyOrgId());
        dto.setPartyRole(entity.getPartyRole());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /** 将查询文本去除首尾空白，空白串按未传处理。 */
    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
