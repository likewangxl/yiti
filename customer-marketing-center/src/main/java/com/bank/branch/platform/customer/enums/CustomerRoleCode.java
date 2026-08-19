package com.bank.branch.platform.customer.enums;

import java.util.Set;

/** 客户营销域使用的业务角色编码。 */
public final class CustomerRoleCode {

    /** 新增的专用客户营销客户经理角色。 */
    public static final String CUSTOMER_MARKETING_MANAGER = "CUST_MARKETING_MANAGER";

    /** 历史客户经理角色，保留兼容既有用户和流程数据。 */
    public static final String LEGACY_RELATIONSHIP_MANAGER = "R_RM";

    private CustomerRoleCode() {
    }

    /** 判断角色集合是否包含新专用角色或历史客户经理角色。 */
    public static boolean isCustomerManager(Set<String> roleCodes) {
        return roleCodes != null && (roleCodes.contains(CUSTOMER_MARKETING_MANAGER)
                || roleCodes.contains(LEGACY_RELATIONSHIP_MANAGER));
    }
}
