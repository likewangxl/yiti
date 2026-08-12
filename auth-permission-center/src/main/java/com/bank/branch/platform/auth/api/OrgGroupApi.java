package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.OrgGroupDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupRoleCheckDTO;
import com.bank.branch.platform.auth.api.dto.OrgGroupScopeDTO;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 命名机构组与机构本地画像对外 API。
 *
 * <p>报表模块只能依赖本接口及 DTO，不得访问 auth 的 Mapper、Entity 或 Service。
 * 运行时授权强制校验“当前有效角色 ∩ 屏级角色白名单 ∩ 机构组绑定角色”。</p>
 */
public interface OrgGroupApi {

    /** 查询命名机构组详情；组不存在时返回 null。 */
    OrgGroupDTO getGroup(String groupCode);

    /** 查询组内有效的直接成员机构编码。 */
    Set<String> listActiveMemberCodes(String groupCode);

    /**
     * 解析员工对指定机构组的授权范围。
     *
     * @param empId 员工工号
     * @param groupCode 机构组编码
     * @param allowedRoleCodes 屏级角色白名单，由服务端屏配置读取，不接受前端直传
     */
    OrgGroupScopeDTO resolveAuthorizedScope(String empId, String groupCode,
                                            Collection<String> allowedRoleCodes);

    /** 校验屏级角色白名单中哪些角色有效且已绑定指定机构组。 */
    OrgGroupRoleCheckDTO checkRoleBindings(String groupCode, Collection<String> roleCodes);

    /** 批量获取有效的机构画像；返回值按 orgCode 建立索引。 */
    Map<String, OrgProfileDTO> getActiveProfiles(Collection<String> orgCodes);
}
