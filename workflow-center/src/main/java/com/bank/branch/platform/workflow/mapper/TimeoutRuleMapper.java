package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code selectById(Serializable)} / {@code updateById(T)} 由 BaseMapper 提供。
 * 自定义 SQL 继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface TimeoutRuleMapper extends BaseMapper<WfTimeoutRule> {

    // insert / selectById / updateById 由 MyBatis-Plus BaseMapper 提供

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
}
