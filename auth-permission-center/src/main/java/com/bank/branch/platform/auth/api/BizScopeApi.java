package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;

import java.util.Map;

/**
 * BizType数据范围对外API
 * 提供员工业务数据范围解析、写权限校验等能力
 * 实现基于角色BizScope配置，结合员工机构关系进行范围推导
 */
public interface BizScopeApi {

    /**
     * 解析指定员工对某业务类型拥有的最大数据范围
     *
     * @param empId   员工ID
     * @param bizType 业务类型
     * @return 数据范围类型（取该员工所有角色中该bizType范围的最大值）
     */
    DataScopeType resolveScope(String empId, BizType bizType);

    /**
     * 构建完整的数据范围上下文，供数据过滤使用
     *
     * @param empId   员工ID
     * @param bizType 业务类型
     * @param action  业务操作
     * @return 包含范围类型、机构子树等完整信息的上下文对象
     */
    DataScopeContext buildScopeContext(String empId, BizType bizType, BizAction action);

    /**
     * 校验员工对指定实体是否具有写权限
     * 根据员工数据范围与实体归属关系进行判断
     *
     * @param empId             员工ID
     * @param bizType           业务类型
     * @param entityOwnerOrgId  实体所属机构编码
     * @param entityCreatedBy   实体创建人员工ID
     * @return true 表示有写权限
     */
    boolean checkWritePermission(String empId, BizType bizType, String entityOwnerOrgId, String entityCreatedBy);

    /**
     * 批量获取员工所有业务类型的数据范围映射
     *
     * @param empId 员工ID
     * @return bizType -> DataScopeType 的映射
     */
    Map<BizType, DataScopeType> getUserBizScopes(String empId);
}
