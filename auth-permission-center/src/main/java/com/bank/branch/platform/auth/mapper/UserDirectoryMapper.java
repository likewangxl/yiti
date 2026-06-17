package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.entity.PtUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户通讯录查询 Mapper —— PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查。
 *
 * <p>承载原 portal {@code ADDRBOOK_EMPLOYEE} 员工查询的替代数据源。
 * WHERE 条件仿照 addrbook：在职过滤（status='ACTIVE' → ISENABLED=0）、关键字模糊匹配。
 * 自定义 JOIN SQL 落在 {@code mapper/auth/UserDirectoryMapper.xml}。</p>
 *
 * <p>继承 {@link BaseMapper} 仅为遵循 MyBatis-Plus 规范，本类只用自定义 SQL。</p>
 */
public interface UserDirectoryMapper extends BaseMapper<PtUser> {

    /**
     * 关键字模糊搜索在职员工（对应原 /api/employees/search）。
     *
     * <p>匹配 USERCHNNAME(姓名) 或 USERNAME(工号)；仅 ISENABLED=0（在职）。
     * 一人多机构时取其主机构一条，按姓名升序，最多返回 limit 条。</p>
     *
     * @param keyword 关键字（姓名/工号），为空时不加模糊条件
     * @param limit   最大返回条数
     * @return 通讯录视图列表（empId=USER_ID）
     */
    List<UserDirectoryDTO> searchByKeyword(@Param("keyword") String keyword,
                                           @Param("limit") int limit);

    /**
     * 按 empId（= USER_ID 代理键）查询单个员工详情（对应原 /api/employees/{empId}）。
     *
     * <p>与 addrbook selectByEmpId 口径一致：不做在职过滤，按主键直查。</p>
     *
     * @param empId 员工标识（PT_USER.USER_ID）
     * @return 通讯录视图，不存在时返回 null
     */
    UserDirectoryDTO selectByEmpId(@Param("empId") String empId);
}
