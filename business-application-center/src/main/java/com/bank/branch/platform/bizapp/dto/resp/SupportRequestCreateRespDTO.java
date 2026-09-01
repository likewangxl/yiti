package com.bank.branch.platform.bizapp.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 创建中台支持申请响应 DTO。
 * <p>
 * 返回同批拆单的所有申请记录及共享的 submitGroupId，
 * 消费方可通过 submitGroupId 做同批追溯查询。
 * </p>
 */
@Data
@Builder
public class SupportRequestCreateRespDTO {

    /**
     * 同批提交分组ID（UUID32，多产品拆单时所有记录共享同一值）。
     */
    private String submitGroupId;

    /**
     * 本次创建的申请数量（场景A >= 1，场景B = 1）。
     */
    private int productCount;

    /**
     * 本次创建的申请明细列表。
     */
    private List<CreatedItem> requests;

    /**
     * 单条申请明细。
     */
    @Data
    @Builder
    public static class CreatedItem {

        /** 申请ID（UUID32）。 */
        private String id;

        /** 申请编号（SR+yyyyMMdd+6位序号）。 */
        private String requestNo;

        /** 产品ID（场景A有值，场景B为null）。 */
        private String productId;

        /**
         * 创建场景：A（产品直达）或 B（部门承接）。
         */
        private String scenario;

        /**
         * 工作流实例ID（草稿阶段尚未提交工作流，此处为 null；
         * 提交后由 submit 接口触发工作流，通过详情接口查询获得）。
         */
        private String processInstanceId;
    }
}
