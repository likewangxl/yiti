package com.bank.branch.platform.yundun.service;

import com.bank.branch.platform.yundun.dto.AccountabilityViolationDTO;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.dto.CreditViolationDTO;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.entity.AccountabilityViolation;
import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.bank.branch.platform.yundun.mapper.AccountabilityViolationMapper;
import com.bank.branch.platform.yundun.mapper.CreditViolationMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 云盾分页、详情和导出的隐私字段契约测试。 */
@ExtendWith(MockitoExtension.class)
class ViolationPrivacyMaskingTest {

    @Mock
    private AccountabilityViolationMapper accountabilityMapper;

    @Mock
    private CreditViolationMapper creditMapper;

    @Test
    void accountabilityPageMasksOnlyNameAndDoesNotMutateEntity() {
        AccountabilityViolation entity = accountability("张三丰", "11010519491231002X");
        Page<AccountabilityViolation> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(accountabilityMapper.selectPage(any(), any())).thenReturn(page);

        AccountabilityViolationDTO result = new AccountabilityViolationService(accountabilityMapper)
                .page(new AccountabilityViolationQuery()).getRecords().get(0);

        assertThat(result.getName()).isEqualTo("张*丰");
        assertThat(result.getIdNumber()).isEqualTo("11010519491231002X");
        assertThat(entity.getName()).isEqualTo("张三丰");
        assertThat(entity.getIdNumber()).isEqualTo("11010519491231002X");
    }

    @Test
    void accountabilityGetKeepsAllFieldsPlaintextAfterMaskedPage() {
        AccountabilityViolation entity = accountability("张三丰", "11010519491231002X");
        Page<AccountabilityViolation> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        when(accountabilityMapper.selectPage(any(), any())).thenReturn(page);
        when(accountabilityMapper.selectById(1L)).thenReturn(entity);
        AccountabilityViolationService service = new AccountabilityViolationService(accountabilityMapper);

        assertThat(service.page(new AccountabilityViolationQuery()).getRecords().get(0).getName())
                .isEqualTo("张*丰");
        AccountabilityViolationDTO detail = service.get(1L);

        assertThat(detail.getName()).isEqualTo("张三丰");
        assertThat(detail.getIdNumber()).isEqualTo("11010519491231002X");
    }

    @Test
    void accountabilityExportMasksNameAndLastSixIdCharactersWithoutMutatingEntity() {
        AccountabilityViolation entity = accountability("张三丰", "11010519491231002X");
        entity.setInUse(1);
        when(accountabilityMapper.selectList(any())).thenReturn(List.of(entity));

        AccountabilityViolationSaveReq result = new AccountabilityViolationService(accountabilityMapper)
                .exportRows(new AccountabilityViolationQuery(), null).get(0);

        assertThat(result.getName()).isEqualTo("张*丰");
        assertThat(result.getIdNumber()).isEqualTo("110105194912******");
        assertThat(entity.getName()).isEqualTo("张三丰");
        assertThat(entity.getIdNumber()).isEqualTo("11010519491231002X");
    }

    @Test
    void creditPageMasksResponsiblePersonAndOnlyOneToFourHanCharacterClientNames() {
        CreditViolation shortHanClient = credit("中国银行", "李四");
        CreditViolation longHanClient = credit("中国银行股", "赵六");
        CreditViolation mixedClient = credit("中国银行A", "王五");
        Page<CreditViolation> page = new Page<>(1, 10);
        page.setRecords(List.of(shortHanClient, longHanClient, mixedClient));
        page.setTotal(3);
        when(creditMapper.selectPage(any(), any())).thenReturn(page);

        List<CreditViolationDTO> results = new CreditViolationService(creditMapper)
                .page(new CreditViolationQuery()).getRecords();

        assertThat(results).extracting(CreditViolationDTO::getResponsiblePersonName)
                .containsExactly("李*", "赵*", "王*");
        assertThat(results).extracting(CreditViolationDTO::getClientName)
                .containsExactly("中**行", "中国银行股", "中国银行A");
        assertThat(shortHanClient.getClientName()).isEqualTo("中国银行");
        assertThat(shortHanClient.getResponsiblePersonName()).isEqualTo("李四");
        assertThat(longHanClient.getClientName()).isEqualTo("中国银行股");
        assertThat(mixedClient.getClientName()).isEqualTo("中国银行A");
    }

    @Test
    void creditPageUsesOpaqueRawNameGroupingKeyToAvoidMaskedNameCollisions() throws Exception {
        CreditViolation firstZhang = credit("张三", "李四");
        CreditViolation secondZhang = credit("张四", "王五");
        CreditViolation sameAsFirst = credit("张三", "赵六");
        Page<CreditViolation> page = new Page<>(1, 10);
        page.setRecords(List.of(firstZhang, secondZhang, sameAsFirst));
        page.setTotal(3);
        when(creditMapper.selectPage(any(), any())).thenReturn(page);

        List<CreditViolationDTO> results = new CreditViolationService(creditMapper)
                .page(new CreditViolationQuery()).getRecords();

        assertThat(results).extracting(CreditViolationDTO::getClientName)
                .containsExactly("张*", "张*", "张*");
        Object firstGroupKey = fieldValue(results.get(0), "clientNameGroupKey");
        Object secondGroupKey = fieldValue(results.get(1), "clientNameGroupKey");
        Object thirdGroupKey = fieldValue(results.get(2), "clientNameGroupKey");
        assertThat(firstGroupKey).isNotNull().isEqualTo(thirdGroupKey);
        assertThat(secondGroupKey).isNotNull().isNotEqualTo(firstGroupKey);
        assertThat(firstGroupKey).isNotEqualTo("张三");
        assertThat(secondGroupKey).isNotEqualTo("张四");
    }

    @Test
    void creditGetKeepsResponsiblePersonAndClientPlaintext() {
        CreditViolation entity = credit("中国银行", "李四");
        when(creditMapper.selectById(2L)).thenReturn(entity);

        CreditViolationDTO result = new CreditViolationService(creditMapper).get(2L);

        assertThat(result.getClientName()).isEqualTo("中国银行");
        assertThat(result.getResponsiblePersonName()).isEqualTo("李四");
    }

    @Test
    void creditExportMasksEligibleFieldsWithoutMutatingEntity() {
        CreditViolation person = credit("中国银行", "李四");
        CreditViolation company = credit("中国银行股", "王五");
        person.setInUse(1);
        company.setInUse(1);
        when(creditMapper.selectList(any())).thenReturn(List.of(person, company));

        List<CreditViolationSaveReq> results = new CreditViolationService(creditMapper)
                .exportRows(new CreditViolationQuery(), null);

        assertThat(results).extracting(CreditViolationSaveReq::getResponsiblePersonName)
                .containsExactly("李*", "王*");
        assertThat(results).extracting(CreditViolationSaveReq::getClientName)
                .containsExactly("中**行", "中国银行股");
        assertThat(person.getResponsiblePersonName()).isEqualTo("李四");
        assertThat(person.getClientName()).isEqualTo("中国银行");
        assertThat(company.getClientName()).isEqualTo("中国银行股");
    }

    private AccountabilityViolation accountability(String name, String idNumber) {
        AccountabilityViolation entity = new AccountabilityViolation();
        entity.setName(name);
        entity.setIdNumber(idNumber);
        return entity;
    }

    private CreditViolation credit(String clientName, String responsiblePersonName) {
        CreditViolation entity = new CreditViolation();
        entity.setClientName(clientName);
        entity.setResponsiblePersonName(responsiblePersonName);
        return entity;
    }

    private Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
