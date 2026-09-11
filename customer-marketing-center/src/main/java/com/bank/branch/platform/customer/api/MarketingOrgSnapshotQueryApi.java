package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.MarketingOrgSnapshotDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 分行大屏使用的机构营销快照只读契约。
 *
 * <p>调用方只依赖本接口和 {@link MarketingOrgSnapshotDTO}，机构客户和触达任务的
 * 聚合、去重及状态口径均由客户营销域内部完成。</p>
 */
public interface MarketingOrgSnapshotQueryApi {

    /**
     * 批量查询机构营销快照。
     *
     * <p>机构编码会在域内先去空、去重并保持首次出现顺序，最多接受 500 个有效编码。
     * 当前真实表没有历史版本快照字段，因此 asOfDate 只作为请求截至日返回，统计值取
     * 当前状态；未来日期会被拒绝，返回 DTO 的 sourceMode 固定为 CURRENT_STATE。</p>
     *
     * @param orgCodes 机构编码集合，可为 null；空集合返回空集合
     * @param asOfDate 请求截至日期，为 null 时使用当前日期
     * @return 每个去重后的输入机构恰好一行，包含零值机构
     */
    List<MarketingOrgSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes, LocalDate asOfDate);
}
