package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 网址导航传输对象（不可变）
 *
 * <p>跨模块 API 返回类型，由 NavApi 对外提供。
 * 同时也用于本模块 Controller 层的导航列表返回。</p>
 */
@Value
@Builder
public class NavDTO {

    /** 导航ID */
    String id;

    /** 名称 */
    String navName;

    /** URL */
    String navUrl;

    /** 图标 */
    String navIcon;

    /** 分类 */
    String navCategory;

    /** 排序号 */
    Integer sortOrder;

    /** 状态 ACTIVE/DISABLED */
    String status;
}
