package com.bank.branch.platform.yundun.excel;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.WorkbookWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.handler.context.RowWriteHandlerContext;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import com.alibaba.excel.write.handler.context.WorkbookWriteHandlerContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.List;

/**
 * 信贷风险 Excel 的参考模板版式。
 *
 * <p>严格复刻 {@code 信贷风险信息_模板.xlsx} 的 40 列宽度、四行表头行高、
 * 分组合并和灰色表头样式；导入模板仅输出前 31 个当前导入列。</p>
 */
public final class CreditViolationExcelWriteHandler
        implements SheetWriteHandler, RowWriteHandler, CellWriteHandler, WorkbookWriteHandler {

    private static final double[] COLUMN_WIDTHS = {
            12.75D, 12.875D, 12.5D, 12.25D, 12.375D, 11.75D, 12.125D, 14D,
            12.5D, 10.875D, 13.625D, 10.875D,
            11.625D, 11.625D, 11.625D, 11.625D, 11.625D, 11.625D, 11.625D, 11.625D,
            18.875D, 14.5D, 25D, 26.75D, 20.25D, 13.25D, 12.125D, 18D, 16D, 26D,
            23.5D, 10.875D, 13.375D, 13.25D, 12.75D, 12.625D, 18D, 13.5D, 17.375D, 14D
    };
    private static final float[] HEADER_HEIGHTS = {18F, 18.75F, 21F, 39F};
    /** Excel 行、列均从 0 开始：第 5 行起为数据，A-H 与 AE 为同一客户判定字段。 */
    private static final int DATA_START_ROW = 4;
    private static final int DOCUMENT_COLUMN = 30;

    private final boolean includeLegacyColumns;
    private CellStyle headerStyle;

    public CreditViolationExcelWriteHandler(boolean includeLegacyColumns) {
        this.includeLegacyColumns = includeLegacyColumns;
    }

    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        var sheet = context.getWriteSheetHolder().getSheet();
        int columnCount = includeLegacyColumns ? 40 : 31;
        for (int column = 0; column < columnCount; column++) {
            sheet.setColumnWidth(column, (int) Math.round(COLUMN_WIDTHS[column] * 256D));
        }

        addMerged(sheet, 0, 0, 1, columnCount - 1);
        addMerged(sheet, 1, 1, 12, 14);
        addMerged(sheet, 1, 1, 15, 20);
        addMerged(sheet, 1, 1, 21, 24);
        addMerged(sheet, 1, 1, 25, 30);
        addMerged(sheet, 2, 2, 27, 29);
        for (int column = 0; column <= 11; column++) {
            addMerged(sheet, 1, 3, column, column);
        }
        for (int column = 12; column <= 26; column++) {
            addMerged(sheet, 2, 3, column, column);
        }
        addMerged(sheet, 2, 3, 30, 30);
        if (includeLegacyColumns) {
            for (int column = 31; column < 40; column++) {
                addMerged(sheet, 1, 3, column, column);
            }
        }
    }

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        if (!Boolean.TRUE.equals(context.getHead())) {
            return;
        }
        int rowIndex = context.getRelativeRowIndex();
        if (rowIndex >= 0 && rowIndex < HEADER_HEIGHTS.length) {
            Row row = context.getRow();
            row.setHeightInPoints(HEADER_HEIGHTS[rowIndex]);
            if (rowIndex == 0) {
                // 参考文件 A1 为不换行空格，B1:AN1 为换行空白；视觉留空但保留原始表头结构。
                row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("\u00A0");
                row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("\n");
            }
        }
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        if (Boolean.TRUE.equals(context.getHead())) {
            context.getCell().setCellStyle(headerStyle(context.getWriteWorkbookHolder().getWorkbook()));
        }
    }

    /**
     * 在所有数据行写完后按客户判定字段合并连续记录。
     *
     * <p>不能在逐行写出时立即合并，因为只有读到下一行才知道当前客户记录是否结束。</p>
     */
    @Override
    public void afterWorkbookDispose(WorkbookWriteHandlerContext context) {
        DataFormatter formatter = new DataFormatter();
        for (org.apache.poi.ss.usermodel.Sheet sheet : context.getWriteWorkbookHolder().getWorkbook()) {
            int groupStart = DATA_START_ROW;
            CustomerKey previous = customerKey(sheet.getRow(groupStart), formatter);
            for (int rowIndex = DATA_START_ROW + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                CustomerKey current = customerKey(sheet.getRow(rowIndex), formatter);
                if (previous.isMergeable() && previous.equals(current)) {
                    continue;
                }
                mergeCustomerGroup(sheet, groupStart, rowIndex - 1, previous);
                groupStart = rowIndex;
                previous = current;
            }
            mergeCustomerGroup(sheet, groupStart, sheet.getLastRowNum(), previous);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        if (headerStyle != null) {
            return headerStyle;
        }
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        style.setFont(font);
        headerStyle = style;
        return style;
    }

    private static void addMerged(org.apache.poi.ss.usermodel.Sheet sheet,
                                  int firstRow, int lastRow, int firstColumn, int lastColumn) {
        sheet.addMergedRegionUnsafe(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
    }

    private static CustomerKey customerKey(Row row, DataFormatter formatter) {
        String[] values = new String[9];
        for (int column = 0; column <= 7; column++) {
            values[column] = cellText(row, column, formatter);
        }
        values[8] = cellText(row, DOCUMENT_COLUMN, formatter);
        return new CustomerKey(List.of(values));
    }

    private static String cellText(Row row, int column, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private static void mergeCustomerGroup(org.apache.poi.ss.usermodel.Sheet sheet,
                                           int firstRow, int lastRow, CustomerKey key) {
        if (!key.isMergeable() || lastRow <= firstRow) {
            return;
        }
        for (int column = 0; column <= 7; column++) {
            addMerged(sheet, firstRow, lastRow, column, column);
        }
        addMerged(sheet, firstRow, lastRow, DOCUMENT_COLUMN, DOCUMENT_COLUMN);
    }

    private record CustomerKey(List<String> values) {
        boolean isMergeable() {
            for (String value : values) {
                if (!value.isBlank()) {
                    return true;
                }
            }
            return false;
        }
    }
}
