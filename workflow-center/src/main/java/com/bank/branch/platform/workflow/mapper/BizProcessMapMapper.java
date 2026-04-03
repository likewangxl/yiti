package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.BizProcessMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 业务流程映射 Mapper 接口，操作 biz_process_map 表。
 * <p>
 * 提供业务实体与流程实例之间的映射关系 CRUD 操作。
 * 支持按 businessKey、processInstanceId、bizType+bizId 多维度查询。
 * </p>
 */
@Mapper
public interface BizProcessMapMapper {

    /**
     * 新增业务流程映射记录。
     *
     * @param map 业务流程映射实体
     * @return 受影响行数
     */
    int insert(BizProcessMap map);

    /**
     * 根据主键查询映射记录。
     *
     * @param id 映射ID
     * @return 映射实体，不存在时返回 null
     */
    BizProcessMap selectById(String id);

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
     * 按主键更新映射信息，使用动态 SET 仅更新非 null 字段。
     *
     * @param map 包含 id 及待更新字段的映射实体
     * @return 受影响行数
     */
    int updateById(BizProcessMap map);

    /**
     * 判断指定业务键是否存在运行中的流程。
     *
     * @param businessKey 业务键
     * @return 存在运行中流程返回 true，否则返回 false
     */
    boolean existsRunningByBusinessKey(String businessKey);
}
