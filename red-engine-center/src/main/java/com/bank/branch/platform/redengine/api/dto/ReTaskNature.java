package com.bank.branch.platform.redengine.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 任务是否按周期生成。 */
public enum ReTaskNature {

    /** 定时任务，数据库存储值为 SCHEDULED。 */
    SCHEDULED("SCHEDULED"),
    /** 临时任务。 */
    TEMPORARY("TEMPORARY");

    private final String value;

    ReTaskNature(String value) {
        this.value = value;
    }

    /** 返回与数据库/接口契约一致的值。 */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 兼容原型提交的 PERIODIC 别名，并归一化为数据库的 SCHEDULED。
     *
     * @param value 请求中的任务性质
     * @return 归一化后的任务性质
     */
    @JsonCreator
    public static ReTaskNature fromValue(String value) {
        if (value == null) {
            return null;
        }
        if ("PERIODIC".equalsIgnoreCase(value) || "SCHEDULED".equalsIgnoreCase(value)) {
            return SCHEDULED;
        }
        if ("TEMPORARY".equalsIgnoreCase(value)) {
            return TEMPORARY;
        }
        throw new IllegalArgumentException("不支持的任务性质: " + value);
    }
}
