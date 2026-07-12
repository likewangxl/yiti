package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;

import java.util.List;

/**
 * 大屏运行时整屏配置响应.
 */
@Data
public class ScreenViewRespDTO {

    /** 屏信息（blocks 置 null，区块在外层） */
    private ScreenDetailRespDTO screen;

    private List<ScreenBlockDTO> blocks;

    /** 仅 PROVINCE 屏返回（其余为空列表） */
    private List<MapPointDTO> mapPoints;
}
