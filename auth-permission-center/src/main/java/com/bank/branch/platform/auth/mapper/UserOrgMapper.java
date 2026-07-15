package com.bank.branch.platform.auth.mapper;

import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户机构关联 Mapper 接口，操作 EXT_USER_ORG 表。
 * <p>
 * V1 阶段每个用户只有一个主机构归属，selectByUserId 返回单条记录。
 * selectOrgsByUserId 通过 JOIN EXT_ORG_INFO 返回完整机构信息，
 * 用于数据权限范围计算（如 ORG / ORG_SUBTREE 场景）。
 * </p>
 */
@Mapper
public interface UserOrgMapper {

    /**
     * 根据用户ID查询用户机构关联记录（V1 单主机构）。
     *
     * @param userId 用户ID
     * @return 用户机构关联实体，不存在时返回 null
     */
    ExtUserOrg selectByUserId(String userId);

    /**
     * 按用户ID列表批量查询用户机构关联记录（V1 单主机构），替代逐人 {@link #selectByUserId} 的 N+1。
     * <p>
     * 每个用户仅一条主机构记录，调用方按 USER_ID 取首条即可（无 LIMIT，因批量场景无法逐用户限量，
     * 调用方汇总为 Map 时同一 userId 只保留首条命中的记录，等价于单条查询的 LIMIT 1 语义）。
     * </p>
     *
     * @param userIds 用户ID列表（调用方保证非空）
     * @return 用户机构关联实体列表（无命中返回空列表）
     */
    List<ExtUserOrg> selectByUserIds(@Param("userIds") List<String> userIds);

    /**
     * 根据用户ID查询用户所属机构的完整信息列表（JOIN EXT_ORG_INFO）。
     * 用于构建数据权限过滤条件。
     *
     * @param userId 用户ID
     * @return 机构信息列表
     */
    List<ExtOrgInfo> selectOrgsByUserId(String userId);

    /**
     * 根据机构编码分页查询用户列表，JOIN PT_USER 获取用户信息。
     *
     * @param orgCode  机构编码
     * @param keyword  关键字（工号/姓名模糊），为 null 时不过滤
     * @param offset   分页偏移量
     * @param limit    每页记录数
     * @return 用户列表
     */
    List<PtUser> selectUsersByOrgCode(@Param("orgCode") String orgCode,
                                      @Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 统计指定机构下的用户数量。
     *
     * @param orgCode  机构编码
     * @param keyword  关键字（工号/姓名模糊），为 null 时不过滤
     * @return 用户总数
     */
    long countUsersByOrgCode(@Param("orgCode") String orgCode,
                             @Param("keyword") String keyword);

    /**
     * 查询指定机构下全部用户工号（USER_ID），供「机构角色」无角色场景（该机构任一角色均可审批）。
     *
     * @param orgCode 机构编码
     * @return 工号列表
     */
    List<String> selectEmpIdsByOrgCode(@Param("orgCode") String orgCode);

    /** 新增用户机构关联（V1 单主机构）。 */
    int insert(ExtUserOrg userOrg);

    /** 按 userId 更新主机构（V1 单主机构语义，行存在时改 ORG_CODE）。 */
    int updateOrgCodeByUserId(@Param("userId") String userId, @Param("orgCode") String orgCode);

    /** 按 userId 清空机构关联（用户删除时调用，或 update 改机构前先清旧）。 */
    int deleteByUserId(String userId);
}
