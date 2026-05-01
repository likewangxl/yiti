package com.bank.branch.platform.portal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.portal.entity.PortalShortcut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作台快捷入口 Mapper 接口，操作 portal_shortcut 表。
 * <p>
 * 本表无 deleted 列，删除操作为物理删除。
 * 系统级快捷入口（shortcut_type='SYSTEM'）全员可见，
 * 自定义快捷入口（shortcut_type='CUSTOM'）仅所属员工可见。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} 由 BaseMapper 提供。
 * 自定义 SQL（按员工/系统查询、批量插入、物理删除）继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface PortalShortcutMapper extends BaseMapper<PortalShortcut> {

    /**
     * 插入单条快捷入口。
     *
     * @param entity 快捷入口实体
     * @return 受影响行数
     */
    int insert(PortalShortcut entity);

    /**
     * 查询员工可见的快捷入口（系统级 + 该员工自定义）。
     * <p>
     * 返回 status='ACTIVE' 的系统级快捷入口和指定员工的自定义快捷入口，
     * 按 shortcut_type DESC（SYSTEM 在前）、sort_order ASC 排序。
     * </p>
     *
     * @param empId 员工工号
     * @return 可见的快捷入口列表
     */
    List<PortalShortcut> listByEmpIdOrSystem(@Param("empId") String empId);

    /**
     * 删除指定员工的所有自定义快捷入口。
     *
     * @param empId 员工工号
     * @return 受影响行数
     */
    int deleteCustomByEmpId(@Param("empId") String empId);

    /**
     * 按员工工号查询快捷入口（仅该员工的，不含系统级），按 sort_order 升序排列。
     *
     * @param empId 员工工号
     * @return 该员工的快捷入口列表
     */
    List<PortalShortcut> listByEmpId(@Param("empId") String empId);

    /**
     * 批量插入快捷入口（与 batchInsert 功能相同，命名符合规范）。
     *
     * @param list 快捷入口实体列表
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<PortalShortcut> list);

    /**
     * 统计指定员工的自定义快捷入口数量。
     *
     * @param empId 员工工号
     * @return 自定义快捷入口数量
     */
    int countCustomByEmpId(@Param("empId") String empId);

    /**
     * 查询所有系统级快捷入口（shortcut_type='SYSTEM' 且 status='ACTIVE'），按 sort_order 升序。
     *
     * @return 系统级快捷入口列表
     */
    List<PortalShortcut> listSystemOnly();
}
