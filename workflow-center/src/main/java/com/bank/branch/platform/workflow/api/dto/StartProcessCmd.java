package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 流程启动命令
 * <p>
 * 由业务模块构建，传递给 WorkflowApi.startProcess()。
 * 业务模块在调用前必须已完成所有业务校验和主表保存。
 * </p>
 */
@Data
public class StartProcessCmd {

    /** 业务类型，枚举值：LEAD / LOAN / SUPPORT / TOUCH / TARGET_ADJUST / ALLOC_ADJUST */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    /** 业务对象ID，如线索ID、资产投放申请ID等 */
    @NotBlank(message = "业务ID不能为空")
    private String bizId;

    /** 业务键，格式：BIZ_TYPE:{id}，在 biz_process_map 中必须唯一 */
    @NotBlank(message = "业务键不能为空")
    @Size(max = 100, message = "业务键长度不能超过100")
    private String businessKey;

    /** 流程定义 KEY，必须是 Flowable 中已部署且已激活的流程定义 */
    @NotBlank(message = "流程定义KEY不能为空")
    private String processDefinitionKey;

    /** 发起人工号，必须是有效的系统用户工号 */
    @NotBlank(message = "发起人不能为空")
    private String startUser;

    /** 发起人机构代码，用于候选组解析中的组织边界判定 */
    @NotBlank(message = "发起人机构不能为空")
    private String startOrgId;

    /** 流程标题，展示在待办列表中 */
    @NotBlank(message = "流程标题不能为空")
    @Size(max = 200, message = "流程标题长度不能超过200")
    private String title;

    /** 额外流程变量，业务模块可传入流程中需要使用的变量 */
    private Map<String, Object> variables;
}
