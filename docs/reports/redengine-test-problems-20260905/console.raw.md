# playwright-cli 浏览器控制台

## 报送员会话

```text
Total messages: 7 (Errors: 0, Warnings: 5)
```

警告为既有 Vue Router `<router-view>`/`transition` 兼容提示，以及 Element Plus `el-radio label` 将弃用提示；任务请求和页面渲染无 console error。

## 支部书记会话

`console error` 原始输出：

```text
Total messages: 15 (Errors: 8, Warnings: 5)
Returning 8 messages for level "error"

[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=20&tab=PENDING:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REVIEWING:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=PASSED:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REJECTED:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=20&tab=REJECTED:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=PENDING:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REVIEWING:0
[ERROR] Failed to load resource: the server responded with a status of 403 (Forbidden) @ http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=PASSED:0
```

这些 403 均来自直接四维材料旧队列；同页任务队列 `/api/re/reviews/tasks/branch/queue` 均为 `200 OK`。只读数据库盘点确认根因是 `yit_test` 中 `R_RE_SECR` 对三条材料审核资源的授权计数均为 0，本轮未做数据库写入。
