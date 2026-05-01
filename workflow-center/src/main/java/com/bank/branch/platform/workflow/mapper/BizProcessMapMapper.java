package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
