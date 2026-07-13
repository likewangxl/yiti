package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流参与机构快照实体，对应 WF_PROCESS_ORG 表。
 * <p>记录某流程实例被哪些机构（通过参与人主机构）接触过，供审批流监控按机构过滤使用。</p>
 */
@Data
@TableName("WF_PROCESS_ORG")
public class WfProcessOrg {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String processInstanceId;
    private String orgCode;
    private String source;
    private LocalDateTime firstSeenTime;
}
