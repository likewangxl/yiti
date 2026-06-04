package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PERF_ORIG_ALLOC 成功载荷：客户原分配关系列表，字段对齐前端 applyAdd.vue flexList。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrigAllocData {

    /** 原分配明细（前端预填 flexList）。 */
    private List<OrigAllocItem> allocaters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrigAllocItem {
        /** 工号（PT_USER.USERNAME，由 USER_ID 反查）。 */
        private String username;
        /** 姓名。 */
        private String fullname;
        /** 比例（字符串）。 */
        private String ratio;
        /** 原始分配标记，固定 1（前端只读底色）。 */
        private Integer isOriginal;
    }
}
