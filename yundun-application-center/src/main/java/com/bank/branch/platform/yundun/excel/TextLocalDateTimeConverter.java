package com.bank.branch.platform.yundun.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 将 Excel 文本单元格中的日期转换为 {@link LocalDateTime}。
 *
 * <p>业务导入既要兼容 Excel 原生日期单元格，也要兼容业务人员按文本录入的日期。
 * 本转换器只注册为 {@link CellDataTypeEnum#STRING}，不会替代 EasyExcel 对原生日期数值的默认处理。
 * 纯日期按当天零点保存，避免因缺少时分秒而导致 {@code LocalDateTime} 解析失败。</p>
 */
public class TextLocalDateTimeConverter implements Converter<LocalDateTime> {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"));

    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDateTime.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public LocalDateTime convertToJavaData(ReadConverterContext<?> context) {
        String value = context.getReadCellData().getStringValue();
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其余允许的格式。
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 继续尝试其余允许的格式。
            }
        }
        throw new IllegalArgumentException("日期格式不正确，仅支持 yyyy-MM-dd、yyyy/MM/dd、yyyy.MM.dd 及带时分秒的格式");
    }
}
