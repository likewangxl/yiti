package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业务流程映射 Mapper 接口，操作 biz_process_map 表。
 * <p>
 * 提供业务实体与流程实例之间的映射关系 CRUD 操作。
 * 支持按 businessKey、processInstanceId、bizType+bizId 多维度查询。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code selectById(Serializable)} / {@code updateById(T)} 由 BaseMapper 提供。
 * 自定义 SQL 继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface BizProcessMapMapper extends BaseMapper<BizProcessMap> {

    // insert / selectById / updateById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据业务键查询最新的非取消状态映射记录。
     *
     * @param businessKey 业务键
     * @return 映射实体，不存在时返回 null
     */
    BizProcessMap selectByBusinessKey(String businessKey);

    /**
     * 根据流程实例ID查询映射记录。
     *
     * @param processInstanceId Flowable流程实例ID
     * @return 映射实体，不存在时返回 null
     */
    BizProcessMap selectByProcessInstanceId(String processInstanceId);

    /**
     * 根据业务类型和业务ID查询映射记录。
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 映射实体，不存在时返回 null
     */
    BizProcessMap selectByBizTypeAndBizId(@Param("bizType") String bizType,
                                          @Param("bizId") String bizId);

    /**
     * 判断指定业务键是否存在运行中的流程。
     *
     * @param businessKey 业务键
     * @return 存在运行中流程返回 true，否则返回 false
     */
    boolean existsRunningByBusinessKey(String businessKey);

    /**
     * 按 processInstanceId 列表 + bizType 批量查询映射记录。
     * 用于 TodoQueryApi 把 Flowable Task 反查到 businessKey，避免 N+1。
     *
     * @param processInstanceIds 流程实例 ID 列表（非空）
     * @param bizType            业务类型
     * @return 映射列表（不存在的 piid 不返回）
     */
    List<BizProcessMap> selectByProcessInstanceIdsAndBizType(
            @Param("processInstanceIds") List<String> processInstanceIds,
            @Param("bizType") String bizType);

    /**
     * 审批流监控分页查询（数据范围通过 orgScope 过滤）。
     * <p>
     * {@code orgScope} 为 {@code null} 表示全行不过滤（ALL / 系统管理员）；
     * 非空集合走 {@code EXISTS(WF_PROCESS_ORG ...)} 过滤参与机构。
     * 调用方（Service）保证空集合（非 null）永远不会传入此方法，否则 XML 的
     * {@code IN ()} 在 MySQL 中是语法错误。
     * </p>
     *
     * @param status    流程状态（可空，精确匹配）
     * @param bizType   业务类型（可空，精确匹配）
     * @param keyword   标题关键字（可空，模糊匹配）
     * @param startedBy 发起人工号（可空，精确匹配）
     * @param orgScope  机构编码集合，null=全行不过滤，非空集合=按参与机构过滤
     * @param offset    分页偏移
     * @param size      分页大小
     * @return 映射记录列表
     */
    List<BizProcessMap> selectMonitorPage(@Param("status") String status,
                                           @Param("bizType") String bizType,
                                           @Param("keyword") String keyword,
                                           @Param("startedBy") String startedBy,
                                           @Param("orgScope") java.util.Collection<String> orgScope,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    /**
     * 审批流监控计数（条件与 {@link #selectMonitorPage} 一致）。
     *
     * @param status    流程状态（可空，精确匹配）
     * @param bizType   业务类型（可空，精确匹配）
     * @param keyword   标题关键字（可空，模糊匹配）
     * @param startedBy 发起人工号（可空，精确匹配）
     * @param orgScope  机构编码集合，null=全行不过滤，非空集合=按参与机构过滤
     * @return 总记录数
     */
    long countMonitor(@Param("status") String status,
                       @Param("bizType") String bizType,
                       @Param("keyword") String keyword,
                       @Param("startedBy") String startedBy,
                       @Param("orgScope") java.util.Collection<String> orgScope);
}
