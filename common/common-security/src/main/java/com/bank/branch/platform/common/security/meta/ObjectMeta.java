package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import java.util.Set;

/**
 * 业务对象元数据
 * 描述一个业务对象的数据权限配置信息，包括表名、列映射和支持的数据范围
 *
 * @param objectKey       业务对象标识
 * @param tableName       对应数据库表名
 * @param ownerOrgCol     所属机构列名
 * @param createdByCol    创建人列名
 * @param assigneeCol     分配人列名
 * @param selfCol         本人相关列名
 * @param businessKeyCol  业务主键列名
 * @param joinPolicyKey   关联策略标识
 * @param supportedScopes 支持的数据范围类型集合
 * @param viewBizType     查看关联的业务类型
 */
public record ObjectMeta(
    String objectKey, String tableName, String ownerOrgCol, String createdByCol,
    String assigneeCol, String selfCol, String businessKeyCol, String joinPolicyKey,
    Set<DataScopeType> supportedScopes, String viewBizType
) {
    /**
     * 校验指定的数据范围是否被当前业务对象支持
     *
     * @param scope 待校验的数据范围类型
     * @throws PermissionDeniedException 当数据范围不被支持时抛出
     */
    public void validateScope(DataScopeType scope) {
        if (!supportedScopes.contains(scope)) {
            throw new PermissionDeniedException("SCOPE_001",
                String.format("业务对象 [%s] 不支持数据范围 [%s]", objectKey, scope.getCode()));
        }
    }
}
