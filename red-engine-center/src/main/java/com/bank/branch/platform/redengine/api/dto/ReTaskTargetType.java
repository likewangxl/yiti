package com.bank.branch.platform.redengine.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 任务对象类型。 */
public enum ReTaskTargetType {

    /** 全部党支部，范围由任务定义的 audienceType 表达。 */
    ALL_BRANCH("ALL_BRANCH", "ALL_BRANCHES"),
    /** 指定党支部，目标表存储 BRANCH。 */
    SPECIFIED_BRANCH("BRANCH", "SPECIFIED_BRANCHES"),
    /** 指定员工，目标表存储 EMPLOYEE。 */
    SPECIFIED_EMPLOYEE("EMPLOYEE", "SPECIFIED_EMPLOYEES");

    private final String storageType;
    private final String audienceType;

    ReTaskTargetType(String storageType, String audienceType) {
        this.storageType = storageType;
        this.audienceType = audienceType;
    }

    /** 返回 RE_TASK_TARGET.TARGET_TYPE 的存储值。 */
    public String getStorageType() {
        return storageType;
    }

    /** 返回 RE_TASK.AUDIENCE_TYPE 的存储值；全部党支部不写目标行。 */
    public String getAudienceType() {
        return audienceType;
    }

    /** 兼容前端单数值和数据库复数值的查询/详情响应。 */
    @JsonCreator
    public static ReTaskTargetType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ReTaskTargetType type : values()) {
            if (type.name().equalsIgnoreCase(value)
                    || type.storageType.equalsIgnoreCase(value)
                    || type.audienceType.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的任务对象类型: " + value);
    }
}
