package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;

import java.util.List;

/**
 * 大屏布局/区块/地图点位配置服务.
 */
public interface ScreenConfigService {

    /** 屏列表（概要，不含区块） */
    List<ScreenDetailRespDTO> listScreens();

    /** 屏详情（含区块） */
    ScreenDetailRespDTO getScreen(Long id);

    /** 新建仅含元数据的空大屏，画布区块随后通过 canvas/save 建立。 */
    Long createScreen(ScreenCreateReqDTO req);

    /** 更新屏元数据与机构范围；不触碰任何 block。 */
    Long updateScreenMetadata(Long id, ScreenMetadataUpdateReqDTO req);

    /**
     * 已废弃的 Java 兼容入口。HTTP 端点不再使用，且 DTO 已无 blocks，不能再成为区块写入口。
     */
    @Deprecated(since = "2026-08-11", forRemoval = false)
    Long saveScreen(ScreenSaveReqDTO req);

    /** 删除（屏逻辑删 + 区块物理删） */
    void deleteScreen(Long id);

    /** 点位列表 */
    List<MapPointDTO> listMapPoints();

    /** 点位整表覆盖保存 */
    void saveMapPoints(List<MapPointDTO> points);

    /** 运行时读取整屏配置（ACTIVE；PROVINCE 附 ACTIVE 点位） */
    ScreenViewRespDTO getViewByCode(String screenCode);

    /** 运行时渲染包读取:state=published(默认) 读 PUBLISHED_JSON,draft 读草稿合成包 */
    com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO getRenderByCode(String screenCode, String state);

    /** 屏级角色白名单查询。 */
    List<String> listAccessRoleCodes(Long screenId);

    /** 屏级角色白名单覆盖保存。 */
    void saveAccessRoleCodes(Long screenId, List<String> roleCodes, String reason, Integer expectedVersion);
}
