package com.bank.branch.platform.customer.marketing.tag;

import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagImportService;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import com.bank.branch.platform.governance.api.FileApi;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerTagImportServiceTest {
    @Mock private MarketingCustomerTagImportBatchMapper batchMapper;
    @Mock private MarketingCustomerTagImportDetailMapper detailMapper;
    @Mock private MarketingCustomerInfoMapper customerMapper;
    @Mock private MarketingLeadInfoMapper leadMapper;
    @Mock private MarketingCustomerTagService tagService;
    @Mock private FileApi fileApi;
    @InjectMocks private MarketingCustomerTagImportService service;

    @Test
    void previewRejectsUnsupportedImportMode() {
        assertThatThrownBy(() -> service.preview(null, 1L, "MERGE"))
                .hasMessageContaining("APPEND");
    }

    @Test
    void importTemplateMatchesParserHeadersAndDocumentsRequiredIdentityFields() throws Exception {
        byte[] template = service.importTemplate();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(template))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("客户标签导入");
            assertThat(workbook.getSheetAt(0).getRow(0)).isNotNull();
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 6);
            assertThat(workbook.getSheetAt(0).getRow(0).cellIterator()).toIterable()
                    .extracting(cell -> cell.getStringCellValue())
                    .containsExactly("企业名称", "统一社会信用代码", "联系人", "联系电话", "注册地址", "经营地址");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(0);

            assertThat(workbook.getSheet("填写说明")).isNotNull();
            String instructions = IntStream.rangeClosed(0, workbook.getSheet("填写说明").getLastRowNum())
                    .mapToObj(index -> workbook.getSheet("填写说明").getRow(index).getCell(0).getStringCellValue()
                            + workbook.getSheet("填写说明").getRow(index).getCell(1).getStringCellValue()
                            + workbook.getSheet("填写说明").getRow(index).getCell(2).getStringCellValue())
                    .reduce("", String::concat);
            assertThat(instructions).contains("企业名称").contains("统一社会信用代码").contains("必填");
        }
    }
}
