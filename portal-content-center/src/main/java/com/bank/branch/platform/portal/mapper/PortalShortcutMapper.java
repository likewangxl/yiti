package com.bank.branch.platform.portal.mapper;

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
 */
@Mapper
public interface PortalShortcutMapper {

    /**
     * 插入单条快捷入口。
     *
     * @param entity 快捷入口实体
     * @return 受影响行数
     */
    int insert(PortalShortcut entity);

    /**
     * 批量插入快捷入口（A.3 批量保存用）。
     *
     * @param list 快捷入口实体列表
     * @return 受影响行数
     */
    int batchInsert(@Param("list") List<PortalShortcut> list);

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
}
