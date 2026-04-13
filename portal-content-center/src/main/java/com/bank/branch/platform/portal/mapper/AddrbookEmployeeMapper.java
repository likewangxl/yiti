package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通讯录员工 Mapper 接口，操作 addrbook_employee 表。
 * <p>
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * responsible_product_ids 字段通过 JsonStringListTypeHandler 自动转换 List&lt;String&gt; <-> JSON 字符串。
 * </p>
 */
@Mapper
public interface AddrbookEmployeeMapper {

    /**
     * 插入新员工。
     *
     * @param entity 员工实体
     * @return 受影响行数
     */
    int insert(AddrbookEmployee entity);

    /**
     * 按员工工号查询（含逻辑删除过滤）。
     *
     * @param empId 员工工号
     * @return 员工实体，不存在或已删除时返回 null
     */
    AddrbookEmployee selectByEmpId(@Param("empId") String empId);

    /**
     * D.2 详情用：批量按 empId 查员工，含 mobile 等字段。
     *
     * @param empIds 员工工号列表
     * @return 匹配的员工列表
     */
    List<AddrbookEmployee> listByEmpIds(@Param("empIds") List<String> empIds);

    /**
     * D.6 前置引用检查：统计仍把 productId 列为负责产品的员工数。
     *
     * @param productId 产品ID
     * @return 引用该产品的在职且未删除员工数量
     */
    int countEmployeesReferringProduct(@Param("productId") String productId);

    /**
     * D.4/D.5/D.6 双向同步：更新单个员工的 responsible_product_ids（乐观锁）。
     *
     * @param empId                 员工工号
     * @param responsibleProductIds 新的负责产品ID列表
     * @param expectedUpdatedTime   预期的更新时间（乐观锁）
     * @param operatorEmpId         操作人工号
     * @return 受影响行数（0 表示乐观锁冲突）
     */
    int updateResponsibleProductsWithOptimisticLock(
            @Param("empId") String empId,
            @Param("responsibleProductIds") List<String> responsibleProductIds,
            @Param("expectedUpdatedTime") LocalDateTime expectedUpdatedTime,
            @Param("operatorEmpId") String operatorEmpId);

    /**
     * D.4/D.5 校验 empId 是否存在且 ACTIVE。
     *
     * @param empIds 员工工号列表
     * @return 满足条件的员工数量
     */
    int countActiveByEmpIds(@Param("empIds") List<String> empIds);
}
