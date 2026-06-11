package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SYS_DICT_ITEMS 业务的成功载荷，对应手机端 {@code response.RspMsg.items}。
 *
 * <p>仅回传前端选项所需的最小字段（code + label），与 PC 管理端
 * {@code listDictItems} → {@code {label: dictLabel, value: dictCode}} 口径一致。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictItemData {

    /** 字典项列表（已是 ACTIVE 且按 sort_order 升序）。 */
    private List<Item> items;

    /** 单个字典项：dictCode 用于提交/存储，dictLabel 用于展示。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /** 字典项编码（提交/存储值，如 CORP_DEPOSIT）。 */
        private String dictCode;

        /** 字典标签（前端展示，如 存款）。 */
        private String dictLabel;
    }
}
