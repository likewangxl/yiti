package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PERF_LIST 业务的成功载荷，对应手机端 {@code response.RspMsg.perfs}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerfListData {

    /** 审批列表（待审批 + 已审批合并）。 */
    private List<PerfListItem> perfs;
}
