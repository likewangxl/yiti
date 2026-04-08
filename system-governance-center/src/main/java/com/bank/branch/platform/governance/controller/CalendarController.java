package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.api.dto.CalendarDayUpdateReqDTO;
import com.bank.branch.platform.governance.api.dto.CalendarImportRespDTO;
import com.bank.branch.platform.governance.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历控制器
 * 提供工作日历的查询、切换和初始化接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sys/calendar")
@Tag(name = "工作日历", description = "工作日历管理")
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 获取指定年份的全部日历数据
     *
     * @param year 年份
     * @return 日历天列表
     */
    @GetMapping
    @Operation(summary = "获取指定年份日历数据")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<CalendarDayDTO>> getWorkingDays(
            @RequestParam(value = "year") int year) {
        log.debug("[CalendarController.getWorkingDays] year={}", year);
        List<CalendarDayDTO> days = calendarService.getWorkingDays(year);
        return ResponseWrapper.success(days);
    }

    /**
     * 设置指定日期的工作日/休息日状态
     *
     * @param date 日期（yyyy-MM-dd 格式路径参数）
     * @param req 请求体
     * @return 成功响应
     */
    @PutMapping("/{date}")
    @Operation(summary = "设置工作日/休息日状态")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> setWorkday(
            @PathVariable(value = "date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @Valid @RequestBody CalendarDayUpdateReqDTO req) {
        log.info("[CalendarController.setWorkday] date={}, isWorkday={}", date, req.getIsWorkday());
        calendarService.setWorkday(date, req.getIsWorkday(), req.getRemark());
        return ResponseWrapper.success();
    }

    /**
     * 初始化指定年份的日历数据
     *
     * @param year 年份
     * @return 成功响应
     */
    @PostMapping("/init")
    @Operation(summary = "初始化年份日历数据")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> initYear(
            @RequestParam(value = "year") int year) {
        log.info("[CalendarController.initYear] year={}", year);
        calendarService.initYear(year);
        return ResponseWrapper.success();
    }

    /**
     * 批量导入节假日（Excel格式）
     *
     * @param file Excel文件
     * @return 导入结果
     */
    @PostMapping("/import")
    @Operation(summary = "批量导入节假日")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.IMPORT)
    public ResponseWrapper<CalendarImportRespDTO> importCalendar(
            @RequestParam(value = "file") MultipartFile file) {
        log.info("[CalendarController.importCalendar] fileName={}", file.getOriginalFilename());
        CalendarImportRespDTO result = calendarService.importFromExcel(file);
        return ResponseWrapper.success(result);
    }
}
