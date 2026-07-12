package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;

/**
 * 大屏画布双态服务(加载/保存草稿 + 发布/回滚/放弃草稿).
 */
public interface ScreenCanvasService {

    /** 编辑器加载:styleJson + draftJson + blocks 行 + 版本/发布态 */
    ScreenCanvasEditorRespDTO loadCanvas(Long id);

    /** 保存草稿(乐观锁、白名单、归属、上限、范围;blocks 增删改单事务),返回新版本 + resolved draft */
    ScreenCanvasSaveRespDTO saveCanvas(ScreenCanvasSaveReqDTO req);

    /** 发布:结构化解析草稿→blockId 一致性校验→合成渲染包→写 PUBLISHED+归档+状态机+审计 */
    void publishCanvas(com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO req);

    /** 从归档回滚指定一次发布到 PUBLISHED_JSON */
    void rollbackCanvas(com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO req);

    /** 放弃草稿:发布态组件树覆盖 DRAFT_JSON */
    void discardDraft(Long screenId);

    /** 发布归档列表(回滚选择用) */
    java.util.List<com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO> listPublishLogs(Long screenId);
}
