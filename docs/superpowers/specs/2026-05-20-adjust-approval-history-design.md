# 调整申请审批流记录展示设计

- 日期：2026-05-20
- 范围：performance-engine-center（后端聚合端点） + xanzc_frontend（前端时间线展示）

## 背景与目标

业绩调整申请页面（`xanzc_frontend/src/views/perf/Adjust.vue` 查看弹框）目前只展示申请内容，没有审批流过程记录。用户在查看 / 待审批场景下无法看到流程到了哪一步、谁审批了、机构是哪个、什么时候发生的。

目标：在查看弹框底部增加按时间倒序（最新在上）的审批流时间线，展示「环节 / 审核人 / 审核结果 / 审核人机构 / 审批意见 / 时间」。

## 现状

- 后端 workflow-center 已有 `GET /api/workflow/processes/{processInstanceId}/history` → `List<ApprovalLogDTO>`
  - `ApprovalLogDTO` 字段：`nodeKey` / `nodeName` / `operator` / `operatorName` / `operatorOrgName` / `action` / `opinion` / `operateTime`
  - `action` 取值：SUBMIT / APPROVE / REJECT / CLAIM / TRANSFER
- 后端 perf 已通过 `WorkflowQueryApi` 依赖 workflow-center（`PerfScopeHelper`）
- `AllocAdjustRespDTO` / `TargetAdjustRespDTO` 详情都返回 `processInstanceId`
- 前端 `Adjust.vue` 查看弹框已通过 `getAdjustDetail(id)` 拿到详情数据

## 方案（A · 后端聚合端点）

### 后端改造

`performance-engine-center` 在 alloc-adjust / target-adjust 两个控制器各新增一个查询端点：

| 方法 + 路径 | @BizAuth.action | 行为 |
|---|---|---|
| `GET /api/perf/alloc-adjust/{id}/approval-history` | `READ` | 返回审批流记录（时间倒序） |
| `GET /api/perf/target-adjust/{id}/approval-history` | `READ` | 返回审批流记录（时间倒序） |

实现要点（两端点对称）：

1. Controller 注入 `WorkflowQueryApi`
2. 先取 `getByIdDto(id)` 拿到 `processInstanceId`
   - `id` 不存在 → 服务层抛 `PERF-404xx`（既有行为）
3. `processInstanceId` 为空（DRAFT 状态尚未启动流程）→ 返回空数组
4. 否则调 `workflowQueryApi.getProcessHistory(processInstanceId)` → 按 `operateTime DESC` 排序（null 时间排最末）→ 返回
5. 返回类型 `ResponseWrapper<List<ApprovalLogDTO>>`
6. 不新增 PT_RESOURCE（与 V1.2 创建/查询端点一致，留待 Q8 资源激活批次统一补齐）

不写到 service 层：仅是 Controller 层的两次跨模块调用 + sort，没有独立业务逻辑，写到 service 反而拉链路。

### 前端改造

`xanzc_frontend/src/api/perf.js` 新增：

```js
export function getAdjustApprovalHistory(id) {
  return call('get', `/perf/alloc-adjust/${id}/approval-history`, {}, []);
}
export function getTargetAdjustApprovalHistory(id) {
  return call('get', `/perf/target-adjust/${id}/approval-history`, {}, []);
}
```

`xanzc_frontend/src/views/perf/Adjust.vue` 改造：

1. `dlg` 状态新增 `approvalLogs: []` + `approvalLoading: false` + `processInstanceId: ''`
2. `openView` 和 `openTodoDetail` 拉详情后，若 `d.processInstanceId` 非空 → 异步拉 `getAdjustApprovalHistory(id)` 填充 `approvalLogs`
3. 弹框只读模式底部新增一节 `card-h "审批流记录"` + `el-timeline`：
   - 每条 `el-timeline-item`：
     - timestamp = `operateTime`（fmt）
     - 节点：`nodeName`（缺失时回退 nodeKey）
     - 操作：`action` 中文化映射 + 颜色 tag（SUBMIT/APPROVE/REJECT/CLAIM/TRANSFER → 提交/通过/驳回/签收/转办）
     - 审核人：`operatorName`（缺失时 operator），后缀 `(operator)` 工号
     - 机构：`operatorOrgName`（缺失显示 `-`）
     - 审批意见：`opinion`（折叠为单独行；空时不渲染）
4. 数据为空 / processInstanceId 空 → 显示 `el-empty` "暂无审批记录"

> Target 调整审批页（如果存在或未来加）按相同 API 模式接入；本次只动 alloc-adjust（前端 Adjust.vue 当前仅服务 alloc-adjust）。

## 数据流

```
Adjust.vue.openView(id)
  → GET /perf/alloc-adjust/{id}       (existing, returns processInstanceId)
  → GET /perf/alloc-adjust/{id}/approval-history   (NEW)
        ↓ (Controller)
        AllocAdjustService.getByIdDto(id)   // existing, gets processInstanceId
        WorkflowQueryApi.getProcessHistory(processInstanceId)   // existing
        sort by operateTime DESC
        ↓
        ResponseWrapper<List<ApprovalLogDTO>>
```

## 边界

- DRAFT 状态：processInstanceId 为空 → 后端返回 `[]`，前端展示 `el-empty`
- 申请不存在：后端 `getByIdDto` 抛 PERF-404xx，前端 `ElMessage.error`
- workflow-center 查询异常：后端透传 BizException（不另作包装）

## 测试

后端：
- `AllocAdjustControllerIT` 新增 1-2 case：DRAFT 返回空 / 已启动流程返回排序后的记录
- `TargetAdjustControllerIT` 同样

前端：无单测体系，手动开 dev server 检查。

## 范围外（YAGNI）

- 不做流程图 PNG 嵌入（已有独立 `/processes/{id}/diagram` 端点，本次不接）
- 不做候选人列表显示（ApprovalLogDTO 已经记录实际操作人）
- 不做 portal-content-center / customer-marketing-center 类似申请的审批流展示（用户本次只要求 perf）
