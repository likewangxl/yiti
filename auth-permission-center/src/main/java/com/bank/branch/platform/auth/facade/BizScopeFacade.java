package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.service.BizScopeService;
import com.bank.branch.platform.auth.service.OrgService;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 业务数据范围 Facade 实现
 * 实现 BizScopeApi，编排 BizScopeService、OrgService 和 UserOrgMapper，
 * 提供完整的数据范围上下文构建和写权限校验能力。
 * 相比 BizScopeService，Facade 负责补全机构信息（orgCode/orgSubtreeCodes），
 * 使调用方无需关心机构查询细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizScopeFacade implements BizScopeApi {

    private final BizScopeService bizScopeService;
    private final OrgService orgService;
    private final UserOrgMapper userOrgMapper;

    /**
     * 解析指定员工对某业务类型拥有的最大数据范围
     * 委托给 BizScopeService，按多角色并集策略合并
     *
     * @param empId   员工ID
     * @param bizType 业务类型
     * @return 合并后的数据范围类型
     */
    @Override
    public DataScopeType resolveScope(String empId, BizType bizType) {
        return bizScopeService.resolveScope(empId, bizType);
    }

    /**
     * 构建完整的数据范围上下文，供数据过滤层使用
     * 自动补全员工主机构及机构子树信息：
     * - scopeType=ORG_SUBTREE 时，额外查询完整子树编码集合
     * - 其他 scopeType 时，orgSubtreeCodes 为空集合
     *
     * @param empId   员工ID
     * @param bizType 业务类型
     * @param action  业务操作
     * @return 包含范围类型、机构子树等完整信息的上下文对象
     */
    @Override
    public DataScopeContext buildScopeContext(String empId, BizType bizType, BizAction action) {
        DataScopeType scopeType = bizScopeService.resolveScope(empId, bizType);

        // 查询员工主机构，用于 ORG / ORG_SUBTREE 场景
        String orgCode = null;
        Set<String> orgSubtreeCodes = Set.of();

        ExtUserOrg userOrg = userOrgMapper.selectByUserId(empId);
        if (userOrg != null) {
            orgCode = userOrg.getOrgCode();
            // ORG_SUBTREE 需要完整子树编码，提前查询并缓存到上下文，避免后续多次查库
            if (scopeType == DataScopeType.ORG_SUBTREE) {
                orgSubtreeCodes = orgService.getOrgSubtreeCodes(orgCode);
            }
        }

        return new DataScopeContext(scopeType, empId, orgCode, orgSubtreeCodes, bizType, action);
    }

    /**
     * 校验员工对指定实体是否具有写权限
     * 基于员工的数据范围类型与实体归属关系进行完整判断：
     * - ALL：无限制，直接放行
     * - SELF_CREATED：仅允许创建者操作
     * - ORG：仅允许同机构操作
     * - ORG_SUBTREE：允许本机构及下属机构操作
     * - 其他范围类型默认拒绝写操作
     *
     * @param empId            员工ID
     * @param bizType          业务类型
     * @param entityOwnerOrgId 实体所属机构编码
     * @param entityCreatedBy  实体创建人员工ID
     * @return true 表示有写权限
     */
    @Override
    public boolean checkWritePermission(String empId, BizType bizType,
                                        String entityOwnerOrgId, String entityCreatedBy) {
        DataScopeType scopeType;
        try {
            scopeType = bizScopeService.resolveScope(empId, bizType);
        } catch (Exception e) {
            // 角色未配置该 BizType 或其他异常，遵循 Fail Close 原则，拒绝写操作
            log.warn("[BizScopeFacade.checkWritePermission] 无法解析数据范围，拒绝写操作: empId={}, bizType={}, error={}",
                    empId, bizType, e.getMessage());
            return false;
        }

        return switch (scopeType) {
            case ALL -> true;
            case SELF_CREATED -> empId.equals(entityCreatedBy);
            case ORG -> {
                // 获取员工主机构编码，与实体归属机构比对
                String orgCode = getUserOrgCode(empId);
                yield orgCode != null && orgCode.equals(entityOwnerOrgId);
            }
            case ORG_SUBTREE -> {
                // 获取员工机构子树，检查实体归属机构是否在子树范围内
                String orgCode = getUserOrgCode(empId);
                if (orgCode == null) yield false;
                Set<String> subtree = orgService.getOrgSubtreeCodes(orgCode);
                yield subtree.contains(entityOwnerOrgId);
            }
            // SELF / SELF_ASSIGNED / WORKFLOW_PARTICIPANT 等范围类型不适用写操作校验，默认拒绝
            default -> false;
        };
    }

    /**
     * 批量获取员工所有业务类型的数据范围映射
     * 委托给 BizScopeService，多角色并集后返回
     *
     * @param empId 员工ID
     * @return bizType -> DataScopeType 的映射
     */
    @Override
    public Map<BizType, DataScopeType> getUserBizScopes(String empId) {
        return bizScopeService.getUserBizScopes(empId);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 查询员工主机构编码
     * 用于 ORG / ORG_SUBTREE 写权限校验场景
     *
     * @param empId 员工ID
     * @return 主机构编码，无归属记录时返回 null
     */
    private String getUserOrgCode(String empId) {
        ExtUserOrg userOrg = userOrgMapper.selectByUserId(empId);
        return userOrg != null ? userOrg.getOrgCode() : null;
    }
}
