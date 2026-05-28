package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.api.dto.CalendarImportRespDTO;
import com.bank.branch.platform.governance.entity.SysCalendarDay;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.CalendarMapper;
import com.bank.branch.platform.governance.config.MemoryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作日历管理服务
 * <p>
 * 负责 sys_calendar_day 表的业务操作，集成 Redis 缓存实现 cache-aside 模式。
 * 缓存 key 格式：gov:calendar:{year}，TTL 为 24 小时。
 * 所有写操作完成后自动清除对应年份的缓存，保证数据一致性。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final MemoryCacheService memoryCacheService;
    private final CalendarMapper calendarMapper;

    /** 缓存 key 前缀 */
    private static final String CACHE_PREFIX = "gov:calendar:";

    /** 缓存过期时间 */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * 判断指定日期是否为工作日。
     *
     * @param date 日期
     * @return true=工作日, false=休息日
     */
    public boolean isWorkingDay(LocalDate date) {
        log.debug("[CalendarService.isWorkingDay] date={}", date);
        SysCalendarDay day = calendarMapper.selectByDay(date);
        if (day == null) {
            // 日历中无记录时，根据星期几默认判断：周一到周五为工作日
            DayOfWeek dow = date.getDayOfWeek();
            return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        }
        return day.getIsWorkday() == 1;
    }

    /**
     * 计算两个日期之间的工作日天数。
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 工作日天数
     */
    public int countWorkingDays(LocalDate from, LocalDate to) {
        log.debug("[CalendarService.countWorkingDays] from={}, to={}", from, to);
        return calendarMapper.countWorkingDays(from, to);
    }

    /**
     * 获取指定年份的全部日历数据（cache-aside 模式）。
     * <p>
     * 优先从 Redis 缓存读取，缓存未命中时查询数据库并回填缓存。
     * </p>
     *
     * @param year 年份
     * @return 日历天 DTO 列表
     */
    @SuppressWarnings("unchecked")
    public List<CalendarDayDTO> getWorkingDays(int year) {
        String cacheKey = CACHE_PREFIX + year;
        Object cached = memoryCacheService.get(cacheKey);
        if (cached != null) {
            log.debug("[CalendarService.getWorkingDays] 缓存命中 year={}", year);
            return (List<CalendarDayDTO>) cached;
        }
        // 缓存未命中，从数据库加载
        log.debug("[CalendarService.getWorkingDays] 缓存未命中，查询数据库 year={}", year);
        List<SysCalendarDay> days = calendarMapper.selectByYear(year);
        List<CalendarDayDTO> dtoList = days.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        memoryCacheService.put(cacheKey, dtoList, CACHE_TTL);
        return dtoList;
    }

    /**
     * 获取指定年月的日历数据（无缓存，按需查询）
     *
     * @param year  年份
     * @param month 月份（1-12）
     * @return 日历天DTO列表
     */
    public List<CalendarDayDTO> getDaysByMonth(int year, int month) {
        log.debug("[CalendarService.getDaysByMonth] year={}, month={}", year, month);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<SysCalendarDay> days = calendarMapper.selectByDateRange(start, end);
        return days.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 从指定日期开始，推算 N 个工作日后的日期。
     * <p>
     * 从起始日期的下一天开始计数，跳过非工作日，直到累计够指定的工作日天数。
     * 若日历数据不足，自动扩展查询范围。
     * </p>
     *
     * @param from 起始日期
     * @param days 要推算的工作日天数（正数向后）
     * @return 推算后的日期
     */
    public LocalDate addWorkingDays(LocalDate from, int days) {
        log.debug("[CalendarService.addWorkingDays] from={}, days={}", from, days);
        // 加载一段合理范围的日历数据，构建日期→是否工作日的映射
        LocalDate rangeEnd = from.plusDays((long) days * 3);
        List<SysCalendarDay> rangeData = calendarMapper.selectByDateRange(from, rangeEnd);
        Map<LocalDate, Integer> calendarMap = rangeData.stream()
                .collect(Collectors.toMap(SysCalendarDay::getDay, SysCalendarDay::getIsWorkday));

        int counted = 0;
        LocalDate current = from;
        while (counted < days) {
            current = current.plusDays(1);
            // 若日历中有记录则用记录，否则按星期默认判断
            Integer isWorkday = calendarMap.get(current);
            boolean working;
            if (isWorkday != null) {
                working = isWorkday == 1;
            } else {
                DayOfWeek dow = current.getDayOfWeek();
                working = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            }
            if (working) {
                counted++;
            }
        }
        return current;
    }

    /**
     * 切换指定日期的工作日/休息日状态。
     * <p>
     * 将 isWorkday 从 0 翻转为 1，或从 1 翻转为 0。
     * 过去日期不允许修改，抛出 GOV-40301。
     * 若日期不存在则自动创建。
     * </p>
     *
     * @param date 日期
     * @throws BizException GOV-40301 过去日期不可修改
     */
    @Transactional
    public void toggleWorkday(LocalDate date) {
        log.info("[CalendarService.toggleWorkday] date={}", date);
        // 过去日期不允许修改
        if (date.isBefore(LocalDate.now())) {
            throw new BizException(GovErrorCode.PAST_DATE_NOT_MODIFIABLE.getCode(),
                    GovErrorCode.PAST_DATE_NOT_MODIFIABLE.getMessage());
        }
        SysCalendarDay existing = calendarMapper.selectByDay(date);
        if (existing == null) {
            // 日期不存在，先根据星期默认创建
            existing = new SysCalendarDay();
            existing.setDay(date);
            DayOfWeek dow = date.getDayOfWeek();
            int defaultWorkday = (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) ? 1 : 0;
            // 翻转后插入
            existing.setIsWorkday(defaultWorkday == 1 ? 0 : 1);
            existing.setCreatedTime(LocalDateTime.now());
            existing.setUpdatedTime(LocalDateTime.now());
            calendarMapper.insert(existing);
        } else {
            // 翻转 isWorkday: 1→0, 0→1
            existing.setIsWorkday(existing.getIsWorkday() == 1 ? 0 : 1);
            existing.setUpdatedTime(LocalDateTime.now());
            calendarMapper.updateById(existing);
        }
        // 清除该年份缓存
        memoryCacheService.evict(CACHE_PREFIX + date.getYear());
        log.info("[CalendarService.toggleWorkday] 已切换 date={}, newIsWorkday={}", date, existing.getIsWorkday());
    }

    /**
     * 设置指定日期的工作日/休息日状态。
     * <p>
     * 将指定日期设置为工作日或休息日（而非翻转）。
     * 过去日期不允许修改，抛出 GOV-40301。
     * 若日期不存在则自动创建。
     * </p>
     *
     * @param date     日期
     * @param isWorkday 是否工作日（true=工作日，false=休息日）
     * @param remark   备注
     * @throws BizException GOV-40301 过去日期不可修改
     */
    @Transactional
    public void setWorkday(LocalDate date, boolean isWorkday, String remark) {
        log.info("[CalendarService.setWorkday] date={}, isWorkday={}, remark={}", date, isWorkday, remark);
        // 过去日期不允许修改
        if (date.isBefore(LocalDate.now())) {
            throw new BizException(GovErrorCode.PAST_DATE_NOT_MODIFIABLE.getCode(),
                    GovErrorCode.PAST_DATE_NOT_MODIFIABLE.getMessage());
        }
        SysCalendarDay existing = calendarMapper.selectByDay(date);
        if (existing == null) {
            existing = new SysCalendarDay();
            existing.setDay(date);
            existing.setIsWorkday(isWorkday ? 1 : 0);
            existing.setRemark(remark);
            existing.setCreatedTime(LocalDateTime.now());
            existing.setUpdatedTime(LocalDateTime.now());
            calendarMapper.insert(existing);
        } else {
            existing.setIsWorkday(isWorkday ? 1 : 0);
            existing.setRemark(remark);
            existing.setUpdatedTime(LocalDateTime.now());
            calendarMapper.updateById(existing);
        }
        // 清除该年份缓存
        memoryCacheService.evict(CACHE_PREFIX + date.getYear());
        log.info("[CalendarService.setWorkday] 已设置 date={}, isWorkday={}", date, isWorkday);
    }

    /**
     * 初始化指定年份的日历数据。
     * <p>
     * 幂等操作：对每一天，若不存在则插入（周一至周五默认工作日=1，周六日=0）。
     * 已存在的日期跳过不覆盖。
     * </p>
     *
     * @param year 年份
     */
    @Transactional
    public void initYear(int year) {
        log.info("[CalendarService.initYear] year={}", year);
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        // 先清空该年度全部数据再重建（重置模式），确保可重复执行
        calendarMapper.deleteByRange(start, end);
        LocalDate current = start;
        int inserted = 0;
        while (!current.isAfter(end)) {
            SysCalendarDay day = new SysCalendarDay();
            day.setDay(current);
            DayOfWeek dow = current.getDayOfWeek();
            day.setIsWorkday((dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) ? 1 : 0);
            day.setCreatedTime(LocalDateTime.now());
            day.setUpdatedTime(LocalDateTime.now());
            calendarMapper.insert(day);
            inserted++;
            current = current.plusDays(1);
        }
        // 清除缓存
        memoryCacheService.evict(CACHE_PREFIX + year);
        log.info("[CalendarService.initYear] 初始化完成 year={}, inserted={}", year, inserted);
    }

    /**
     * 批量导入日历数据。
     * <p>
     * 仅接受未来日期，过去日期自动跳过。
     * 已存在的日期执行更新，不存在的执行插入。
     * 导入完成后清除受影响年份的缓存。
     * </p>
     *
     * @param days 日历天 DTO 列表
     */
    @Transactional
    public void batchImport(List<CalendarDayDTO> days) {
        log.info("[CalendarService.batchImport] count={}", days.size());
        LocalDate today = LocalDate.now();
        // 收集受影响的年份，用于缓存清除
        java.util.Set<Integer> affectedYears = new java.util.HashSet<>();
        for (CalendarDayDTO dto : days) {
            // 只接受未来日期
            if (dto.getDay().isBefore(today)) {
                log.debug("[CalendarService.batchImport] 跳过过去日期 day={}", dto.getDay());
                continue;
            }
            affectedYears.add(dto.getDay().getYear());
            if (calendarMapper.existsByDay(dto.getDay())) {
                // 更新
                SysCalendarDay entity = new SysCalendarDay();
                entity.setDay(dto.getDay());
                entity.setIsWorkday(dto.getIsWorkday());
                entity.setRemark(dto.getRemark());
                entity.setUpdatedTime(LocalDateTime.now());
                calendarMapper.updateById(entity);
            } else {
                // 插入
                SysCalendarDay entity = new SysCalendarDay();
                entity.setDay(dto.getDay());
                entity.setIsWorkday(dto.getIsWorkday());
                entity.setRemark(dto.getRemark());
                entity.setCreatedTime(LocalDateTime.now());
                entity.setUpdatedTime(LocalDateTime.now());
                calendarMapper.insert(entity);
            }
        }
        // 清除受影响年份的缓存
        for (Integer year : affectedYears) {
            memoryCacheService.evict(CACHE_PREFIX + year);
        }
        log.info("[CalendarService.batchImport] 导入完成，受影响年份={}", affectedYears);
    }

    /**
     * 批量导入节假日（Excel格式）
     * <p>
     * Excel格式：日期(yyyy-MM-dd), 是否工作日(1/0), 备注
     * 只处理未来日期，过去日期自动跳过
     * </p>
     *
     * @param file Excel文件
     * @return 导入结果
     */
    public CalendarImportRespDTO importFromExcel(MultipartFile file) {
        log.info("[CalendarService.importFromExcel] fileName={}", file.getOriginalFilename());
        List<CalendarDayDTO> toImport = new ArrayList<>();
        int skipped = 0;
        LocalDate today = LocalDate.now();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = row.getCell(0);
                Cell workdayCell = row.getCell(1);
                Cell remarkCell = row.getCell(2);

                if (dateCell == null || workdayCell == null) continue;

                // 解析日期
                LocalDate date;
                if (dateCell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                    date = dateCell.getLocalDateTimeCellValue().toLocalDate();
                } else {
                    date = LocalDate.parse(dateCell.getStringCellValue());
                }

                if (date.isBefore(today)) {
                    skipped++;
                    continue;
                }

                CalendarDayDTO dto = new CalendarDayDTO();
                dto.setDay(date);
                dto.setIsWorkday((int) workdayCell.getNumericCellValue());
                dto.setRemark(remarkCell != null ? remarkCell.getStringCellValue() : null);
                toImport.add(dto);
            }
        } catch (Exception e) {
            log.error("[CalendarService.importFromExcel] Excel解析失败", e);
            throw new BizException(GovErrorCode.FILE_FORMAT_INVALID.getCode(),
                    "Excel解析失败: " + e.getMessage());
        }

        batchImport(toImport);

        CalendarImportRespDTO resp = new CalendarImportRespDTO();
        resp.setTotalRows(toImport.size() + skipped);
        resp.setSuccessRows(toImport.size());
        resp.setSkippedRows(skipped);
        return resp;
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将 SysCalendarDay 实体转换为 CalendarDayDTO
     *
     * @param entity 日历实体
     * @return CalendarDayDTO
     */
    private CalendarDayDTO toDTO(SysCalendarDay entity) {
        CalendarDayDTO dto = new CalendarDayDTO();
        dto.setDay(entity.getDay());
        dto.setIsWorkday(entity.getIsWorkday());
        dto.setRemark(entity.getRemark());
        return dto;
    }
}
