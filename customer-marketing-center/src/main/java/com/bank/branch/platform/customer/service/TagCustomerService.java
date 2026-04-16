package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 标签-客户关联业务服务。
 * <p>
 * 负责标签客户的覆盖式导入（先删后批量插入）和标签关联客户列表查询。
 * importCustomers 采用覆盖式策略：先删除该标签下所有旧关联，再批量插入新关联。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagCustomerService {

    private final CustTagMapper tagMapper;
    private final CustTagRelMapper tagRelMapper;

    /**
     * 覆盖式导入标签客户列表。
     * <p>
     * 策略：先 deleteByTagId 删除该标签所有旧关联，再 insertBatch 批量插入新关联。
     * 此操作是写操作，应在调用方开启事务（Controller 层可视需要使用 @Transactional）。
     * </p>
     *
     * @param tagId         标签 ID
     * @param custIds       客户 ID 列表
     * @param operatorEmpId 操作人员工工号
     */
    public void importCustomers(String tagId, List<String> custIds, String operatorEmpId) {
        log.info("[TagCustomerService.importCustomers] tagId={}, custCount={}, operator={}",
                tagId, custIds.size(), operatorEmpId);

        // 先确认标签存在
        CustTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                    CustomerErrorCode.TAG_NOT_FOUND.getMessage());
        }

        // 覆盖式：先删除该标签所有旧的客户关联
        int deleted = tagRelMapper.deleteByTagId(tagId);
        log.debug("[TagCustomerService.importCustomers] deleted {} old relations for tagId={}", deleted, tagId);

        if (custIds == null || custIds.isEmpty()) {
            log.info("[TagCustomerService.importCustomers] custIds is empty, skip insertBatch");
            return;
        }

        // 批量构建关联实体
        LocalDateTime now = LocalDateTime.now();
        List<CustTagRel> relList = custIds.stream().map(custId -> {
            CustTagRel rel = new CustTagRel();
            rel.setId(UUID.randomUUID().toString().replace("-", ""));
            rel.setCustId(custId);
            rel.setTagId(tagId);
            rel.setCreatedBy(operatorEmpId);
            rel.setCreatedTime(now);
            return rel;
        }).collect(Collectors.toList());

        tagRelMapper.insertBatch(relList);
        log.info("[TagCustomerService.importCustomers] inserted {} relations for tagId={}", relList.size(), tagId);
    }

    /**
     * 查询标签关联的客户列表。
     *
     * @param tagId 标签 ID
     * @return 该标签关联的客户关联记录列表
     */
    public List<CustTagRel> listCustomersByTag(String tagId) {
        log.debug("[TagCustomerService.listCustomersByTag] tagId={}", tagId);
        return tagRelMapper.selectByTagId(tagId);
    }
}
