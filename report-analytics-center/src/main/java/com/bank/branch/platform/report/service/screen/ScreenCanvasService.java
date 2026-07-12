package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;

/**
 * 大屏画布双态服务(加载/保存草稿;发布/回滚/放弃草稿见 Task 3).
 */
public interface ScreenCanvasService {

    /** 编辑器加载:styleJson + draftJson + blocks 行 + 版本/发布态 */
    ScreenCanvasEditorRespDTO loadCanvas(Long id);

    /** 保存草稿(乐观锁、白名单、归属、上限、范围;blocks 增删改单事务),返回新版本 + resolved draft */
    ScreenCanvasSaveRespDTO saveCanvas(ScreenCanvasSaveReqDTO req);
}
