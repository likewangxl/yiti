package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 中场支持申请多产品拆单服务（场景A专用）。
 * <p>
 * 对每个 productId 独立创建一条 DRAFT 状态的 SupportRequest。
 * 同批次的记录共享 submitGroupId，方便后续批量操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportProductSplitService {

    private final SupportRequestMapper supportMapper;
    private final BizNoGenerator bizNoGenerator;
    private final ProductApi productApi;

    /**
     * 为每个产品创建独立的 SupportRequest 记录。
     *
     * @param productIds        产品ID列表
     * @param custId            客户ID
     * @param sourceTouchTaskId 来源触达任务ID（可选）
     * @param operatorEmpId     操作人
     * @param orgCode           归属机构
     * @return 创建的 SupportRequest 列表
     * @throws BizException BIZ-40901 if product not support-available
     * @throws BizException BIZ-40906 if duplicate active request for same cust+product (>= 2)
     */
    public List<SupportRequest> splitByProducts(List<String> productIds, String custId,
                                                 String sourceTouchTaskId, String operatorEmpId,
                                                 String orgCode) {
        log.info("[SupportProductSplitService.splitByProducts] custId={}, productIds={}, operator={}",
                custId, productIds, operatorEmpId);

        // 所有同批次拆单记录共享同一个 submitGroupId
        String submitGroupId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        List<SupportRequest> results = new ArrayList<>();

        for (String productId : productIds) {
            // 1. 校验产品存在且支持中场支持
            Optional<ProductDTO> productOpt = productApi.getProduct(productId);
            if (productOpt.isEmpty() || !Boolean.TRUE.equals(productOpt.get().getSupportForSupportRequest())) {
                throw new BizException(
                        BizAppErrorCode.PRODUCT_NOT_SUPPORT_AVAILABLE.getCode(),
                        BizAppErrorCode.PRODUCT_NOT_SUPPORT_AVAILABLE.getMessage()
                );
            }

            // 2. 校验同客户同产品活跃申请数 < 2
            long activeCount = supportMapper.countActiveByCustomerAndProduct(custId, productId);
            if (activeCount >= 2) {
                throw new BizException(
                        BizAppErrorCode.DUPLICATE_SUPPORT_REQUEST.getCode(),
                        BizAppErrorCode.DUPLICATE_SUPPORT_REQUEST.getMessage()
                );
            }

            // 3. 查询产品负责人，取第一个
            List<String> responsibleEmpIds = productApi.getProductResponsibleEmpIds(productId);
            String assignedEmpId = (responsibleEmpIds != null && !responsibleEmpIds.isEmpty())
                    ? responsibleEmpIds.get(0) : null;

            // 4. 构建实体
            SupportRequest entity = new SupportRequest();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setRequestNo(bizNoGenerator.generateSupportNo());
            entity.setSubmitGroupId(submitGroupId);
            entity.setCustId(custId);
            entity.setSourceTouchTaskId(sourceTouchTaskId);
            entity.setProductId(productId);
            entity.setAssignedEmpId(assignedEmpId);
            entity.setStatus(SupportStatus.DRAFT.getCode());
            // 统一 businessKey 格式 "SUPPORT:{id}"，与 BizStateMachine / SupportService 保持一致
            String businessKey = "SUPPORT:" + entity.getId();
            entity.setBusinessKey(businessKey);
            entity.setOwnerOrgId(orgCode);
            entity.setCreatedBy(operatorEmpId);
            entity.setCreatedTime(now);
            entity.setUpdatedBy(operatorEmpId);
            entity.setUpdatedTime(now);
            entity.setDeleted(0);

            // 5. 插入
            supportMapper.insert(entity);
            results.add(entity);

            log.info("[SupportProductSplitService] 创建 SupportRequest，id={}, productId={}",
                    entity.getId(), productId);
        }

        return results;
    }
}
