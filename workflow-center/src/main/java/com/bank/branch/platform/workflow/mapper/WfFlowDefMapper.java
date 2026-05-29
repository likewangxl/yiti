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
}
