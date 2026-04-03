package com.bank.branch.platform.workflow.mapper;

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
 */
@Mapper
public interface NodeCandidateConfMapper {

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
     * 新增候选人配置记录。
     *
     * @param conf 候选人配置实体
     * @return 受影响行数
     */
    int insert(WfNodeCandidateConf conf);

    /**
     * 按主键更新候选人配置，使用动态 SET 仅更新非 null 字段。
     *
     * @param conf 包含 id 及待更新字段的候选人配置实体
     * @return 受影响行数
     */
    int updateById(WfNodeCandidateConf conf);

    /**
     * 按主键删除候选人配置。
     *
     * @param id 配置ID
     * @return 受影响行数
     */
    int deleteById(String id);
}
