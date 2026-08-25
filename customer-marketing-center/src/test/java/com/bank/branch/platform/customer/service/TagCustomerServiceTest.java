package com.bank.branch.platform.customer.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.TagCustomerImportRow;
import com.bank.branch.platform.customer.dto.resp.TagCustomerImportResultDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TagCustomerService 单元测试（TDD RED 阶段）
 */
@ExtendWith(MockitoExtension.class)
class TagCustomerServiceTest {

    @Mock
    private CustTagMapper tagMapper;

    @Mock
    private CustTagRelMapper tagRelMapper;

    @Mock
    private CustMasterMapper masterMapper;

    @InjectMocks
    private TagCustomerService tagCustomerService;

    // ==================== importCustomers ====================

    @Test
    void importCustomers_shouldDeleteOldAndInsertNew() {
        // given: 标签存在
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setTagName("VIP客户");
        when(tagMapper.selectById("tag-001")).thenReturn(tag);
        when(tagRelMapper.deleteByTagId("tag-001")).thenReturn(2);
        when(tagRelMapper.insertBatch(anyList())).thenReturn(3);

        List<String> custIds = Arrays.asList("C001", "C002", "C003");
        // CUST-42202 校验：mock masterMapper 返回全部 3 个客户，校验通过
        when(masterMapper.selectByIds(custIds))
                .thenReturn(Arrays.asList(buildMaster("C001"), buildMaster("C002"), buildMaster("C003")));

        // when
        tagCustomerService.importCustomers("tag-001", custIds, "E001");

        // then: 先删除旧关联，再批量插入新关联
        verify(tagRelMapper).deleteByTagId("tag-001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CustTagRel>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagRelMapper).insertBatch(captor.capture());

        List<CustTagRel> inserted = captor.getValue();
        assertThat(inserted).hasSize(3);
        // 验证每条关联记录的 tagId 和 createdBy
        inserted.forEach(rel -> {
            assertThat(rel.getTagId()).isEqualTo("tag-001");
            assertThat(rel.getCreatedBy()).isEqualTo("E001");
            assertThat(rel.getId()).isNotNull();
        });
        // 验证三条关联的 custId 集合
        assertThat(inserted.stream().map(CustTagRel::getCustId).toList())
                .containsExactlyInAnyOrder("C001", "C002", "C003");
    }

    @Test
    void importCustomers_shouldDedupCustIdsBeforeValidation() {
        // 重复 ID 不应被误判为"缺失"。input ["C001","C001","C002"] distinct 后 = 2，
        // cust_master 返回 [C001,C002] size=2，应通过校验并继续插入。
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        when(tagMapper.selectById("tag-001")).thenReturn(tag);

        List<String> custIdsWithDup = Arrays.asList("C001", "C001", "C002");
        // selectByIds 接收的是去重后 [C001, C002]
        when(masterMapper.selectByIds(Arrays.asList("C001", "C002")))
                .thenReturn(Arrays.asList(buildMaster("C001"), buildMaster("C002")));
        when(tagRelMapper.deleteByTagId("tag-001")).thenReturn(0);
        when(tagRelMapper.insertBatch(anyList())).thenReturn(3);

        // when: 不应抛异常
        tagCustomerService.importCustomers("tag-001", custIdsWithDup, "E001");

        // then: 删旧 + 插入都执行（输入原样传给 insertBatch，业务侧由 UK 兜底）
        verify(tagRelMapper).deleteByTagId("tag-001");
        verify(tagRelMapper).insertBatch(anyList());
    }

    @Test
    void importCustomers_shouldThrowCust42202WhenAnyCustIdMissing() {
        // given: 标签存在；3 个 custId 但 cust_master 只返回 2 个（C002 缺失）
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        when(tagMapper.selectById("tag-001")).thenReturn(tag);

        List<String> custIds = Arrays.asList("C001", "C002", "C003");
        when(masterMapper.selectByIds(custIds))
                .thenReturn(Arrays.asList(buildMaster("C001"), buildMaster("C003")));

        // when/then: 整批回滚，不应该删除/插入
        assertThatThrownBy(() -> tagCustomerService.importCustomers("tag-001", custIds, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code",
                        CustomerErrorCode.TAG_IMPORT_VALIDATION_FAILED.getCode());

        verify(tagRelMapper, never()).deleteByTagId(any());
        verify(tagRelMapper, never()).insertBatch(anyList());
    }

    @Test
    void importCustomers_shouldThrowWhenTagNotFound() {
        // given: 标签不存在
        when(tagMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> tagCustomerService.importCustomers("not-exist",
                Arrays.asList("C001"), "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NOT_FOUND.getCode());

        verify(tagRelMapper, never()).deleteByTagId(any());
        verify(tagRelMapper, never()).insertBatch(anyList());
    }

    // ==================== listCustomersByTag ====================

    @Test
    void listCustomersByTag_shouldReturnRelations() {
        // given
        CustTagRel rel1 = new CustTagRel();
        rel1.setId("rel-001");
        rel1.setTagId("tag-001");
        rel1.setCustId("C001");

        CustTagRel rel2 = new CustTagRel();
        rel2.setId("rel-002");
        rel2.setTagId("tag-001");
        rel2.setCustId("C002");

        when(tagRelMapper.selectByTagId("tag-001")).thenReturn(Arrays.asList(rel1, rel2));
        CustMaster customer1 = customer("C001", "华夏科技", "91310000123456789A");
        CustMaster customer2 = customer("C002", "华夏贸易", "91310000123456789B");
        when(masterMapper.selectByIds(List.of("C001", "C002")))
                .thenReturn(List.of(customer1, customer2));

        // when
        List<CustTagRel> result = tagCustomerService.listCustomersByTag("tag-001");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustId()).isEqualTo("C001");
        assertThat(result.get(1).getCustId()).isEqualTo("C002");
        assertThat(result.get(0).getUnifiedCreditCode()).isEqualTo("91310000123456789A");
        assertThat(result.get(1).getUnifiedCreditCode()).isEqualTo("91310000123456789B");
        verify(tagRelMapper).selectByTagId("tag-001");
    }

    // ==================== addTagsToCustomer ====================

    @Test
    void addTagsToCustomer_shouldInsertNewTagsAndSkipExisting() {
        // given: tag-002 已存在关联，tag-003 不存在（需要新增）
        CustTagRel existing = new CustTagRel();
        existing.setId("rel-exists");
        existing.setCustId("C001");
        existing.setTagId("tag-002");

        when(tagRelMapper.selectByCustIdAndTagId("C001", "tag-002")).thenReturn(existing);
        when(tagRelMapper.selectByCustIdAndTagId("C001", "tag-003")).thenReturn(null);
        when(tagRelMapper.insert(any(CustTagRel.class))).thenReturn(1);

        List<String> tagIds = Arrays.asList("tag-002", "tag-003");

        // when
        int added = tagCustomerService.addTagsToCustomer("C001", tagIds, "E001");

        // then: 只有 tag-003 被新增，tag-002 已存在跳过
        assertThat(added).isEqualTo(1);
        verify(tagRelMapper, never()).insert(
                argThat((CustTagRel rel) -> rel.getTagId().equals("tag-002")));
        ArgumentCaptor<CustTagRel> captor = ArgumentCaptor.forClass(CustTagRel.class);
        verify(tagRelMapper).insert(captor.capture());
        CustTagRel inserted = captor.getValue();
        assertThat(inserted.getTagId()).isEqualTo("tag-003");
        assertThat(inserted.getCustId()).isEqualTo("C001");
        assertThat(inserted.getCreatedBy()).isEqualTo("E001");
        assertThat(inserted.getId()).isNotNull();
    }

    @Test
    void addTagsToCustomer_shouldInsertAllWhenNoneExist() {
        // given: 两个标签都不存在
        when(tagRelMapper.selectByCustIdAndTagId(eq("C001"), anyString())).thenReturn(null);
        when(tagRelMapper.insert(any(CustTagRel.class))).thenReturn(1);

        List<String> tagIds = Arrays.asList("tag-001", "tag-002");

        // when
        int added = tagCustomerService.addTagsToCustomer("C001", tagIds, "E002");

        // then: 两个都被插入
        assertThat(added).isEqualTo(2);
        verify(tagRelMapper, org.mockito.Mockito.times(2)).insert(any(CustTagRel.class));
    }

    @Test
    void addTagsToCustomer_shouldReturnZeroWhenAllExist() {
        // given: 所有标签都已存在
        CustTagRel existing = new CustTagRel();
        existing.setId("rel-001");
        when(tagRelMapper.selectByCustIdAndTagId(anyString(), anyString())).thenReturn(existing);

        List<String> tagIds = Arrays.asList("tag-001", "tag-002");

        // when
        int added = tagCustomerService.addTagsToCustomer("C001", tagIds, "E001");

        // then: 全部跳过，无新增
        assertThat(added).isEqualTo(0);
        verify(tagRelMapper, never()).insert(any(CustTagRel.class));
    }

    // ==================== removeTagFromCustomer ====================

    @Test
    void removeTagFromCustomer_shouldReturnTrueWhenRelationExists() {
        // given: 关联存在，删除成功
        when(tagRelMapper.deleteByCustIdAndTagId("C001", "tag-001")).thenReturn(1);

        // when
        boolean removed = tagCustomerService.removeTagFromCustomer("C001", "tag-001");

        // then
        assertThat(removed).isTrue();
        verify(tagRelMapper).deleteByCustIdAndTagId("C001", "tag-001");
    }

    @Test
    void removeTagFromCustomer_shouldReturnFalseWhenRelationNotExist() {
        // given: 关联不存在，删除 0 行
        when(tagRelMapper.deleteByCustIdAndTagId("C001", "tag-999")).thenReturn(0);

        // when
        boolean removed = tagCustomerService.removeTagFromCustomer("C001", "tag-999");

        // then
        assertThat(removed).isFalse();
        verify(tagRelMapper).deleteByCustIdAndTagId("C001", "tag-999");
    }

    private CustMaster buildMaster(String custId) {
        CustMaster m = new CustMaster();
        m.setId(custId);
        return m;
    }

    @Test
    void importCustomersFile_shouldAppendResolvedCustomersFromExcel() {
        CustTag tag = approvedTag();
        when(tagMapper.selectById("tag-001")).thenReturn(tag);
        CustMaster customer = customer("C001", "华夏科技有限公司", "91310000123456789A");
        when(masterMapper.selectByUnifiedCreditCodes(List.of("91310000123456789A")))
                .thenReturn(List.of(customer));

        TagCustomerImportResultDTO result = tagCustomerService.importCustomersFile(
                "tag-001", excel(row("华夏科技有限公司", "91310000123456789A", "重点项目")),
                "APPEND", "E001");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(1);
        verify(tagRelMapper, never()).expireActiveByTagId(anyString(), anyString(), any());
        verify(tagRelMapper).insertBatch(argThat(list -> list.size() == 1
                && "C001".equals(list.get(0).getCustId())
                && "tag-001".equals(list.get(0).getTagId())));
    }

    @Test
    void importCustomers_shouldSplitBatchInsertIntoAtMostFiveHundredRowsPerSql() {
        when(tagMapper.selectById("tag-001")).thenReturn(approvedTag());
        List<String> customerIds = java.util.stream.IntStream.rangeClosed(1, 1201)
                .mapToObj(index -> "C" + index).toList();
        when(masterMapper.selectByIds(customerIds))
                .thenReturn(customerIds.stream().map(this::buildMaster).toList());
        when(tagRelMapper.selectByTagIdAndCustIds(eq("tag-001"), anyList()))
                .thenReturn(List.of());

        tagCustomerService.importCustomers("tag-001", customerIds, "APPEND", "E001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CustTagRel>> batches = ArgumentCaptor.forClass(List.class);
        verify(tagRelMapper, org.mockito.Mockito.times(3)).insertBatch(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size)
                .containsExactly(500, 500, 201);
        assertThat(batches.getAllValues()).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> queryBatches = ArgumentCaptor.forClass(List.class);
        verify(tagRelMapper, org.mockito.Mockito.times(3))
                .selectByTagIdAndCustIds(eq("tag-001"), queryBatches.capture());
        assertThat(queryBatches.getAllValues()).extracting(List::size)
                .containsExactly(500, 500, 201);
        verify(tagRelMapper, never()).selectByCustIdAndTagId(anyString(), anyString());
        verify(tagRelMapper, never()).insert(any(CustTagRel.class));
    }

    @Test
    void importCustomers_shouldReactivateExistingRelationsInBatch() {
        when(tagMapper.selectById("tag-001")).thenReturn(approvedTag());
        List<String> customerIds = List.of("C001", "C002");
        when(masterMapper.selectByIds(customerIds))
                .thenReturn(customerIds.stream().map(this::buildMaster).toList());
        CustTagRel first = existingRelation("R001", "C001", 0);
        CustTagRel second = existingRelation("R002", "C002", 1);
        when(tagRelMapper.selectByTagIdAndCustIds("tag-001", customerIds))
                .thenReturn(List.of(first, second));

        tagCustomerService.importCustomers("tag-001", customerIds, "REPLACE", "E001");

        verify(tagRelMapper).expireActiveByTagId(eq("tag-001"), eq("E001"), any());
        verify(tagRelMapper).reactivateBatch(eq(List.of("R001", "R002")), eq("E001"), any());
        verify(tagRelMapper, never()).insertBatch(anyList());
        verify(tagRelMapper, never()).updateById(any(CustTagRel.class));
    }

    @Test
    void importCustomersFile_shouldValidateWholeBatchBeforeReplace() {
        when(tagMapper.selectById("tag-001")).thenReturn(approvedTag());
        CustMaster customer = customer("C001", "库内客户名称", "91310000123456789A");
        when(masterMapper.selectByUnifiedCreditCodes(List.of("91310000123456789A")))
                .thenReturn(List.of(customer));

        TagCustomerImportResultDTO result = tagCustomerService.importCustomersFile(
                "tag-001", excel(row("文件中错误名称", "91310000123456789A", "重点项目")),
                "REPLACE", "E001");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getRow()).isEqualTo(2);
                    assertThat(error.getMessage()).contains("客户名称与系统不一致");
                });
        verify(tagRelMapper, never()).expireActiveByTagId(anyString(), anyString(), any());
        verify(tagRelMapper, never()).insertBatch(anyList());
    }

    private static CustTag approvedTag() {
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setTagName("重点项目");
        tag.setDescription("重点项目客户");
        tag.setApprovalStatus("APPROVED");
        tag.setStatus("ACTIVE");
        return tag;
    }

    private static CustMaster customer(String id, String name, String creditCode) {
        CustMaster customer = new CustMaster();
        customer.setId(id);
        customer.setCustName(name);
        customer.setUnifiedCreditCode(creditCode);
        return customer;
    }

    private static CustTagRel existingRelation(String id, String custId, int active) {
        CustTagRel relation = new CustTagRel();
        relation.setId(id);
        relation.setTagId("tag-001");
        relation.setCustId(custId);
        relation.setActive(active);
        return relation;
    }

    private static TagCustomerImportRow row(String name, String creditCode, String tagName) {
        TagCustomerImportRow row = new TagCustomerImportRow();
        row.setCustName(name);
        row.setUnifiedCreditCode(creditCode);
        row.setTagName(tagName);
        row.setTagDescription("重点项目客户");
        return row;
    }

    private static MockMultipartFile excel(TagCustomerImportRow... rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output, TagCustomerImportRow.class).sheet("导入数据").doWrite(Arrays.asList(rows));
        return new MockMultipartFile("file", "tag-customers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }
}
