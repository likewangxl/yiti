# 业绩调整新开户维度页面验收证据

验收时间：2026-08-25（Asia/Shanghai）

## 验收边界

- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 前端进程：PID 292267，工作目录 `/home/djdev/lijh/yiti/xanzc_frontend`
- 工具：项目本地官方 `playwright-cli`，命名会话 `alloc-new`
- 页面与前端源码为当前 checkout 的真实 Vite 运行态。
- 后端业务接口使用下列显式 mock。原因是当前物理 `yiti.PERF_ALLOC_ADJUST_ITEM` 尚无目标字段 `item_kind`，本次未获授权执行 DDL/DML，不能做真实草稿写入联调。

## Mock 路由清单

`playwright-cli -s=alloc-new route-list`：

1. `**/api/sys/dicts/PERF_ALLOC_DIM/items`
2. `**/api/perf/alloc-adjust/my-applies**`
3. `**/api/perf/alloc-adjust/DRAFT-1`
4. `**/api/perf/alloc-adjust/emp-suggest**`
5. `**/api/perf/alloc-adjust/save-draft`

另由同一页面会话的限定路由处理器提供 `PERF_BIZ_KIND`、空列表和其他只读页面依赖，所有 mock 响应均为 `ResponseWrapper(code=0)` 格式。

## 验收结果

1. 分配维度下拉从 `PERF_ALLOC_DIM` 读取到 `RULE / ACCOUNT / NEW`，页面展示字典标签“新客户”。
2. 选择 `NEW` 后“原业绩分配”区域消失，页面不要求原分配。
3. 客户类型切换为“零售客户”后，分配维度自动切为“按账户分配”；重新展开维度下拉仍可选择“按规则分配 / 按账户分配 / 新客户”。
4. 原分配员工选择 `staff2（员工二）` 后，所属机构自动回填为 `ORG-002（二支行）`。
5. 草稿详情中的 `ORIGIN` 行以可编辑控件展示；将员工改为 `staff2（员工二）` 后保存，POST 请求体包含：

```json
{
  "id": "DRAFT-1",
  "originalAllocList": [
    {
      "acctNo": "ACC-OLD",
      "empId": "staff2",
      "username": "staff2",
      "empChnName": "员工二",
      "orgCode": "ORG-002",
      "orgName": "二支行",
      "ratio": 100
    }
  ]
}
```

保存请求为 `POST /api/perf/alloc-adjust/save-draft`，响应 200。最终页面控制台 0 error；4 条 warning 是截屏前主动关闭空白新建表单触发的 Element Plus 必填校验提示，不是运行异常。

## 截图

- `new-dimension-no-origin.png`：NEW（字典标签“新客户”）已选中，原业绩分配区域隐藏。
- `draft-origin-editable.png`：保存后的草稿再次编辑，ORIGIN 原分配行可修改。
