package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.api.dto.CalendarDayRespDTO;
import com.bank.branch.platform.governance.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作日历公共接口
 * 提供无鉴权的公共日历查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sys")
@Tag(name = "工作日历（公共）", description = "无需鉴权的公共日历查询")
public class PublicCalendarController {

    private final CalendarService calendarService;

    /**
     * 获取指定年月的日历数据（公共接口，无鉴权）
     *
     * @param year  年份
     * @param month 月份（1-12）
     * @return 日历天列表
     */
    @GetMapping("/calendar")
    @Operation(summary = "按月查询日历（公共）")
    public ResponseWrapper<List<CalendarDayRespDTO>> getCalendarByMonth(
            @RequestParam(value = "year") int year,
            @RequestParam(value = "month") int month) {
        log.debug("[PublicCalendarController.getCalendarByMonth] year={}, month={}", year, month);
        List<CalendarDayDTO> days = calendarService.getDaysByMonth(year, month);
        List<CalendarDayRespDTO> result = days.stream().map(d -> {
            CalendarDayRespDTO r = new CalendarDayRespDTO();
            r.setDay(d.getDay().toString());
            r.setIsWorkday(d.getIsWorkday() == 1);
            r.setDayOfWeek(d.getDay().getDayOfWeek().getValue());
            r.setRemark(d.getRemark());
            return r;
        }).collect(Collectors.toList());
        return ResponseWrapper.success(result);
    }
}
