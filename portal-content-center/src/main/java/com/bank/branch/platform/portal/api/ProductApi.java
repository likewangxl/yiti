package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.ProductDTO;

import java.util.List;
import java.util.Optional;

/**
 * 产品资料对外接口。
 * 被 customer-marketing-center 和 business-application-center 依赖。
 *
 * <p>所有方法均为只读查询，不提供写操作。
 * 单条查询返回 {@code Optional<T>}，多条查询返回 {@code List<T>}，避免 null 判断。</p>
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface ProductApi {

    /**
     * 获取产品详情。
     * 用于线索、触达任务、业务申请等关联产品展示。
     *
     * @param productId 产品ID
     * @return 产品详情，不存在时返回 Optional.empty()
     */
    Optional<ProductDTO> getProduct(String productId);

    /**
     * 批量获取产品信息。
     * 用于列表页展示多个产品的简要信息，避免 N+1 查询。
     *
     * @param productIds 产品ID列表
     * @return 产品DTO列表（顺序不保证与入参一致，不存在的产品不返回）
     */
    List<ProductDTO> getProducts(List<String> productIds);

    /**
     * 查询支持中场支持的产品列表。
     * 供 business-application-center 的"中场支持申请"页面调用。
     * 本方法高频调用，建议缓存 TTL 5 分钟。
     *
     * @return 所有 status=ACTIVE 且 support_for_support_request=true 的产品列表
     */
    List<ProductDTO> listSupportAvailableProducts();

    /**
     * 按部门查询产品。
     * 用于显示某机构维护的全部产品。
     *
     * @param productDeptOrgCode 产品部门机构编码
     * @return 该部门维护的全部产品列表（不含已删除）
     */
    List<ProductDTO> listProductsByDept(String productDeptOrgCode);

    /**
     * 查询产品负责人工号列表。
     * 从 PORTAL_USER_PRODUCT_REL 关系表查询负责人 ID。
     *
     * @param productId 产品ID
     * @return 负责人工号列表（可能为空）
     */
    List<String> getProductResponsibleEmpIds(String productId);
}
