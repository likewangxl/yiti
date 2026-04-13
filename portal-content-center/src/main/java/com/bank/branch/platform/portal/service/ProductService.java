package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductDetailDTO;
import com.bank.branch.platform.portal.api.dto.ProductListReqDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.convert.ProductConverter;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.dto.ProductListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品资料库 Service
 * <p>D.1-D.7 的业务逻辑实现入口</p>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductInfoMapper productInfoMapper;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    private final FileApi fileApi;
    private final AddrbookQueryService addrbookQueryService;
    private final CurrentUserApi currentUserApi;

    /**
     * D.3 查询所有支持中场支持的产品（active + 未删除）
     * <p>V1 不实现 5min Redis 缓存（spec 附录 B2 登记的 V2 待办）</p>
     *
     * @return 支持中场支持的产品简要列表
     */
    public List<ProductSimpleDTO> listSupportAvailable() {
        return productInfoMapper.listSupportAvailable().stream()
                .map(ProductConverter::toSimple)
                .collect(Collectors.toList());
    }

    /**
     * D.1 分页查询产品列表
     *
     * @param req   查询请求参数
     * @param scope 数据权限范围（common-security POJO）
     * @return 分页结果
     */
    public PageResult<ProductDTO> listProducts(ProductListReqDTO req, DataScopeContext scope) {
        ProductListQuery q = ProductListQuery.builder()
                .keyword(req.getKeyword())
                .category(req.getCategory())
                .status(req.getStatus() != null ? req.getStatus() : "ACTIVE")
                .productDeptOrgCode(req.getProductDeptOrgCode())
                .supportForSupportRequest(req.getSupportForSupportRequest())
                .offset(req.getOffset())
                .limit(req.getPageSize())
                .dataScope(scope)
                .build();

        long total = productInfoMapper.countProducts(q);
        if (total == 0) {
            return PageResult.of(req.getPageNo(), req.getPageSize(), 0L, Collections.emptyList());
        }
        List<ProductInfo> entities = productInfoMapper.listProducts(q);
        return PageResult.of(req.getPageNo(), req.getPageSize(), total,
                entities.stream().map(ProductConverter::toListItem).collect(Collectors.toList()));
    }

    /**
     * D.2 查询产品详情（含跨模块聚合：字典翻译/机构名/附件链接/负责人脱敏）
     *
     * @param id 产品ID
     * @return 产品详情 DTO
     * @throws BizException PORTAL-40003 产品不存在
     */
    public ProductDetailDTO getProduct(String id) {
        ProductInfo entity = productInfoMapper.selectById(id);
        if (entity == null) {
            throw new BizException(PortalErrorCode.PRODUCT_NOT_FOUND.getCode(),
                    PortalErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }

        // 字典翻译
        String categoryDesc = dictApi.getDictLabel("PRODUCT_CATEGORY", entity.getProductCategory());

        // 机构名
        String orgName = null;
        if (entity.getProductDeptOrgCode() != null) {
            try {
                OrgDTO org = orgApi.getOrg(entity.getProductDeptOrgCode());
                orgName = org != null ? org.getOrgName() : null;
            } catch (Exception e) {
                // org not found, ignore
            }
        }

        // 附件下载链接
        String fileDownloadUrl = null;
        if (entity.getFileObjectId() != null) {
            try {
                fileDownloadUrl = fileApi.getDownloadUrl(entity.getFileObjectId());
            } catch (Exception e) {
                // file deleted or unavailable, product still viewable
            }
        }

        // 负责人列表（含脱敏）
        List<ResponsibleEmpDTO> responsibleEmps = addrbookQueryService
                .listResponsibleEmps(entity.getResponsibleEmpIds());

        // 判断当前用户是否可编辑（同机构）
        String currentOrgCode = currentUserApi.getCurrentOrgCode();
        boolean canEdit = entity.getProductDeptOrgCode() != null
                && entity.getProductDeptOrgCode().equals(currentOrgCode);

        return ProductConverter.toDetail(entity, categoryDesc, orgName,
                fileDownloadUrl, responsibleEmps, canEdit);
    }
}
