package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点候选人配置 Mapper 接口，操作 wf_node_candidate_conf 表。
 * <p>
 * 提供按流程定义KEY和节点KEY查询候选人配置的能力。
 * TaskListener 在任务创建时通过此 Mapper 读取配置，动态设置候选人。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code selectById(Serializable)} / {@code updateById(T)} / {@code deleteById(Serializable)}
 * 由 BaseMapper 提供。自定义 SQL 继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface NodeCandidateConfMapper extends BaseMapper<WfNodeCandidateConf> {

    // insert / selectById / updateById / deleteById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据流程定义KEY和节点KEY查询所有候选人配置。
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @return 候选人配置列表
     */
    List<WfNodeCandidateConf> selectByProcessDefKeyAndNodeKey(
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("nodeKey") String nodeKey);

    /**
     * 根据流程定义KEY查询所有节点的候选人配置。
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 候选人配置列表
     */
    List<WfNodeCandidateConf> selectByProcessDefKey(String processDefinitionKey);

    /**
     * 根据流程定义KEY删除该流程的全部候选人配置。
     * <p>
     * 发布流程图时先清理同一影子 KEY 下的旧候选配置，再按最新审批人规则重新插入，
     * 保证候选配置与已发布流程图一致（先删后插的整图替换语义）。
     * </p>
     *
     * @param processDefinitionKey 流程定义KEY（影子 KEY）
     * @return 受影响行数
     */
    int deleteByProcessDefinitionKey(@Param("processDefinitionKey") String processDefinitionKey);
}
