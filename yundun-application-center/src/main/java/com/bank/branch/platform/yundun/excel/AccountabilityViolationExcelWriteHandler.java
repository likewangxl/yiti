package com.bank.branch.platform.yundun.excel;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.context.RowWriteHandlerContext;
import com.alibaba.excel.write.style.column.AbstractHeadColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Row;

import java.util.Map;

/** 人员违规模板及导出的列宽、单层表头行高处理器。 */
public final class AccountabilityViolationExcelWriteHandler
        extends AbstractHeadColumnWidthStyleStrategy implements RowWriteHandler {

    private static final int DEFAULT_COLUMN_WIDTH = 14;
    private static final float HEADER_HEIGHT = 36F;

    /**
     * 以字段名而不是列序号配置宽度，保证导入模板排除历史列后宽度仍然对应正确字段。
     */
    private static final Map<String, Integer> COLUMN_WIDTHS = Map.ofEntries(
            Map.entry("accountabilityForViolationsId", 18),
            Map.entry("accountabilityNumber", 18),
            Map.entry("institutionName", 16),
            Map.entry("accountabilitySource", 16),
            Map.entry("specificSource", 16),
            Map.entry("institutionalHierarchy", 16),
            Map.entry("affiliatedInstitution", 16),
            Map.entry("attributionOfViolations", 16),
            Map.entry("name", 16),
            Map.entry("workNumber", 14),
            Map.entry("gender", 10),
            Map.entry("typeOfPerson", 14),
            Map.entry("postAtTheTime", 24),
            Map.entry("accountabilityPositions", 24),
            Map.entry("documentType", 14),
            Map.entry("idNumber", 20),
            Map.entry("highestEducation", 14),
            Map.entry("thePoliticalLandscape", 16),
            Map.entry("isDimission", 12),
            Map.entry("timeOfDeparture", 15),
            Map.entry("typeOfResponsibility", 16),
            Map.entry("violationCharacteristics", 16),
            Map.entry("areasOfViolation", 18),
            Map.entry("violationAreaLevelOne", 18),
            Map.entry("violationAreaLevelTwo", 18),
            Map.entry("factsOfTheViolation", 28),
            Map.entry("processingBasis", 28),
            Map.entry("accountabilityDocumentsAndNumbers", 28),
            Map.entry("penaltyTime", 15),
            Map.entry("generalHandling", 16),
            Map.entry("disciplinaryAction", 16),
            Map.entry("economicTreatment", 20),
            Map.entry("withholdingAmount", 16),
            Map.entry("withholdingInstructions", 28),
            Map.entry("reconsideration", 12),
            Map.entry("penaltyPeriod", 16),
            Map.entry("penaltyReleaseTime", 15),
            Map.entry("whetherToSubmitSupervision", 16),
            Map.entry("submissionTime", 15),
            Map.entry("remark", 28),
            Map.entry("deptName", 16),
            Map.entry("roleClassification", 16),
            Map.entry("businessArea", 16),
            Map.entry("primaryAndSecondaryResponsibility", 18),
            Map.entry("circumstancesOfTheViolation", 24),
            Map.entry("rankAtTheTimeOfTheViolation", 20),
            Map.entry("accountabilityRanks", 20),
            Map.entry("otherAreas", 18),
            Map.entry("partyAttitude", 16));

    @Override
    protected Integer columnWidth(Head head, Integer columnIndex) {
        if (head == null || head.getFieldName() == null) {
            return DEFAULT_COLUMN_WIDTH;
        }
        return COLUMN_WIDTHS.getOrDefault(head.getFieldName(), DEFAULT_COLUMN_WIDTH);
    }

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        if (Boolean.TRUE.equals(context.getHead())) {
            setHeaderHeight(context.getRow());
        }
    }

    private void setHeaderHeight(Row row) {
        row.setHeightInPoints(HEADER_HEIGHT);
    }
}
