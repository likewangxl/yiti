# 业绩调整机构负责人下一步走向修复验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 身份：宝鸡分行负责人 `bjfzr` 的既有有效会话
- 网络：真实前端 8090 代理真实后端 18080
- Mock：未注册任何 route，非 mock 验收

## 验收结果

1. 对公、按规则分配申请 `AA2026082657620BFA` 在机构负责人节点打开审批弹窗。
2. `GET /api/workflow/tasks/{taskId}` 返回 `nodeKey=branch_approve_l2`、`outgoingBranches=[]`。
3. 页面审批弹窗不再展示“资财部负责人审批/审批结束”错误选项。
4. 浏览器 console：0 errors、0 warnings。
5. 负责人随后在另一真实会话审批后，流程实际进入 `biz_dept_review（公司部经办审批）`，未跳到资财部。
6. 公司部经办节点仍返回“部门负责人审批/原业绩所属机构负责人审批”两项，原有无条件网关选项未受影响。

## 文件

- `branch-approval-without-wrong-routing.png`：负责人审批弹窗页面截图。
- `commands.txt`：官方 playwright-cli 验收命令摘要（会话 Cookie 已脱敏）。
- `console.txt`：浏览器控制台结果。
- `route-list.txt`：浏览器路由拦截清单。
- `requests.txt`：关键真实请求。
- `task-detail-response.json`：负责人节点任务详情关键响应。
