package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审批流程定义 Mapper 接口，操作 WF_FLOW_DEF 表。
 * <p>
 * MyBatis-Plus BaseMapper 提供：insert / selectById / updateById / deleteById。
 * 自定义方法：按 flow_key 精确查询（flow_key 有唯一索引，返回单条）。
 * </p>
 */
@Mapper
public interface WfFlowDefMapper extends BaseMapper<WfFlowDef> {

    // insert / selectById / updateById / deleteById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据流程唯一标识键查询流程定义。
     *
     * @param flowKey 流程唯一标识键
     * @return 流程定义，若不存在返回 null
     */
    WfFlowDef selectByFlowKey(@Param("flowKey") String flowKey);

    /**
     * 根据已部署的 Flowable 流程定义 KEY（deployed_proc_def_key，如
     * {@code DSN_alloc_corp_designer}）反查设计器流程定义。
     * <p>供运行期由「当前任务的 processDefinitionKey」反查其设计器图，
     * 计算审批节点的命名出边选项。未命中返回 null。</p>
     *
     * @param deployedProcDefKey 已部署流程定义 KEY
     * @return 流程定义，未命中返回 null
     */
    default WfFlowDef selectByDeployedProcDefKey(String deployedProcDefKey) {
        return selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<WfFlowDef>lambdaQuery()
                .eq(WfFlowDef::getDeployedProcDefKey, deployedProcDefKey)
                .last("LIMIT 1"));
    }
}
