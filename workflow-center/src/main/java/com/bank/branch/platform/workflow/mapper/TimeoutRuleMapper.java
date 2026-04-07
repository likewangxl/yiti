package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程超时规则 Mapper 接口，操作 wf_timeout_rule 表。
 * <p>
 * 提供按流程定义KEY和节点KEY查询超时规则的能力。
 * 定时任务每30分钟扫描运行中的任务，根据此规则计算红绿灯状态。
 * </p>
 */
@Mapper
public interface TimeoutRuleMapper {

    /**
     * 根据流程定义KEY和节点KEY查询超时规则。
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @return 超时规则实体，不存在时返回 null
     */
    WfTimeoutRule selectByProcessDefKeyAndNodeKey(
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("nodeKey") String nodeKey);

    /**
     * 根据流程定义KEY查询所有节点的超时规则。
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 超时规则列表
     */
    List<WfTimeoutRule> selectByProcessDefKey(String processDefinitionKey);

    /**
     * 根据主键ID查询超时规则。
     *
     * @param id 超时规则ID
     * @return 超时规则实体，不存在时返回 null
     */
    WfTimeoutRule selectById(@Param("id") String id);

    /**
     * 新增超时规则记录。
     *
     * @param rule 超时规则实体
     * @return 受影响行数
     */
    int insert(WfTimeoutRule rule);

    /**
     * 按主键更新超时规则，使用动态 SET 仅更新非 null 字段。
     *
     * @param rule 包含 id 及待更新字段的超时规则实体
     * @return 受影响行数
     */
    int updateById(WfTimeoutRule rule);
}
