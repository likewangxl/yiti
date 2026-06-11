package com.bank.branch.platform.soap.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * callpu 渠道（手机端/ICPS 网关）分发请求体。
 *
 * <p>字段名对齐手机端 {@code list.vue} 约定的 callpu 内层载荷：
 * {@code RuleName}（业务方法名，如 {@code PERF_LIST}）+ {@code Parm}（业务参数）。</p>
 *
 * <p>注：真实 ESB（新 Call 浦）的外层信封格式以应用方反馈为准，本 DTO 仅承载分发所需的内层字段，
 * 后续如需适配外层包裹，由网关层做一次拆包再委托本 controller。</p>
 */
@Data
public class CallPuRequest {

    /** 业务方法名（分发键），如 {@code PERF_LIST}。 */
    @JsonProperty("RuleName")
    private String ruleName;

    /** 调用类型（get/post 等，当前仅透传，不参与分发）。 */
    @JsonProperty("IntType")
    private String intType;

    /** 业务参数。 */
    @JsonProperty("Parm")
    private Parm parm;

    /** 时间戳（callpu 外层透传，当前不参与分发/校验）。 */
    @JsonProperty("timestamp")
    private Long timestamp;

    /** 报文签名 MD5（callpu 外层透传，当前不参与分发/校验）。 */
    @JsonProperty("md5")
    private String md5;

    /** callpu 业务参数对象。 */
    @Data
    public static class Parm {

        /** 员工号（外部渠道传入；上游 callpu 已完成身份认证）。 */
        @JsonProperty("EmployeeNo")
        private String employeeNo;

        /** 客户编号（业务号，CASH_GETCUST_INFO/PERF_SAVE 用；对应后端 custNo）。 */
        private String custId;

        /** 客户名称（前端展示用，后端不依赖，按 custNo 复核）。 */
        private String custName;

        /** 申请类型：1=公司业绩调整 / 2=零售业绩调整（→ custType CORP/RETAIL）。 */
        private String applyType;

        /** 规则：1=账号调整 / 2=规则调整（→ allocDim ACCOUNT/RULE）。 */
        private String applyRule;

        /** 账号/借据号（→ accountNo）。 */
        private String iouNo;

        /** 业务类型（PERF_BIZ_KIND 字典码，可多选逗号串，如 CORP_DEPOSIT,CORP_LOAN；直接落 bizKind）。 */
        private String businessType;

        /** 字典类型编码（SYS_DICT_ITEMS 用，如 PERF_BIZ_KIND）。 */
        private String dictType;

        /** 调整理由（→ reason）。 */
        private String adjustExplain;

        /** 业绩调整审批编号（PERF_INFO 详情 / PERF_RECALL / PERF_APPR 用）。 */
        private String perfAdjustNo;

        /** 审批结论（PERF_APPR 用）：1=同意 / 2=拒绝。 */
        private String apprStatus;

        /** 列表状态域（PERF_LIST 用）：PENDING=待审批 / DONE=已审批；空=合并。 */
        private String queryStatus;

        /** 审批意见（PERF_APPR 用，可空）。 */
        private String apprOpinion;

        /** 分配明细（→ items）。 */
        private List<Allocater> allocaters;
    }

    /** callpu 分配明细项。 */
    @Data
    public static class Allocater {

        /** 工号（→ item.empId）。 */
        private String username;

        /** 姓名（前端展示用）。 */
        private String fullname;

        /** 比例（字符串，→ item.ratio）。 */
        private String ratio;

        /** 是否原始分配（前端标记，后端不消费）。 */
        private Integer isOriginal;
    }
}
