package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.service.cmd.AddKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiItemCmd;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * KPI 方案项服务（骨架，实现在 Step 2 填充）.
 */
@Service
@RequiredArgsConstructor
public class KpiItemService {

    private final PerfKpiItemMapper itemMapper;

    /**
     * 新增方案项（校验 schemeId + metricCode 在同方案内唯一）.
     *
     * @param cmd 新增命令
     * @return 新建方案项
     */
    public PerfKpiItem addItem(AddKpiItemCmd cmd) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 按主键选择性更新.
     *
     * @param id  项ID
     * @param cmd 更新命令
     * @return 更新后的方案项
     */
    public PerfKpiItem updateItem(String id, UpdateKpiItemCmd cmd) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 删除方案项（高危，reason 必填）.
     *
     * @param id       项ID
     * @param reason   删除原因
     * @param operator 操作人
     */
    public void deleteItem(String id, String reason, String operator) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 查询方案下所有项.
     *
     * @param schemeId 方案ID
     * @return 项列表
     */
    public List<PerfKpiItem> listBySchemeId(String schemeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 按主键查询（不存在抛异常）.
     *
     * @param id 主键
     * @return 方案项
     */
    public PerfKpiItem getById(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
