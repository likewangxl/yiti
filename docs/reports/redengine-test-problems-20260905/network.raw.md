# playwright-cli 请求与响应摘要

## 报送员会话

`requests` 原始相关条目：

```text
50. [POST] http://127.0.0.1:8094/api/auth/login => [200] OK
78. [GET] http://127.0.0.1:8094/api/re/tasks/my-assignments?pageNo=1&pageSize=10&tab=PENDING => [200] OK
79. [GET] http://127.0.0.1:8094/api/re/tasks/my-assignments?pageNo=1&pageSize=1&tab=REVIEWING => [200] OK
80. [GET] http://127.0.0.1:8094/api/re/tasks/my-assignments?pageNo=1&pageSize=1&tab=PASSED => [200] OK
81. [GET] http://127.0.0.1:8094/api/re/tasks/my-assignments?pageNo=1&pageSize=1&tab=REJECTED => [200] OK
82. [GET] http://127.0.0.1:8094/api/re/tasks/my-assignments?pageNo=1&pageSize=10&tab=REJECTED => [200] OK
88. [GET] http://127.0.0.1:8094/api/re/tasks/assignments/4 => [200] OK
```

`response-body 88` 关键字段原文（省略与本问题无关字段）：

```json
{
  "code": "0",
  "data": {
    "assignmentId": 4,
    "taskTitle": "E2E-TEMP-D-REJECT-20260901",
    "status": "REJECTED_BY_ORG",
    "reviewFeedback": "组织驳回：填报说明仍不完整，请报送员重新补充",
    "reviewHistory": [
      {"actionCode":"REJECT_BRANCH","operatorId":"wangw67","operatorName":"wangw67","occurredAt":"2026-09-01T13:51:58","opinion":"支部驳回：请补充临时任务说明"},
      {"actionCode":"APPROVE_BRANCH","operatorId":"wangw67","operatorName":"wangw67","occurredAt":"2026-09-01T14:02:02","opinion":"支部复核通过，提交组织审核"},
      {"actionCode":"SUBMIT_TO_ORG","operatorId":"wangw67","operatorName":"wangw67","occurredAt":"2026-09-01T14:02:12","opinion":"支部复核通过，提交组织审核"},
      {"actionCode":"REJECT_ORG","operatorId":"yangdb","operatorName":"yangdb","occurredAt":"2026-09-01T14:03:24","opinion":"组织驳回：填报说明仍不完整，请报送员重新补充"}
    ]
  }
}
```

页面运行态测量原文：

```json
{"cards":[{"width":101.1875,"height":56,"text":"3\n待处理"},{"width":101.1875,"height":56,"text":"1\n审核中"},{"width":101.1875,"height":56,"text":"2\n已通过"},{"width":101.1875,"height":56,"text":"1\n已驳回"}],"num":{"height":"36px","fontSize":"24px","lineHeight":"36px"}}
```

## 支部书记会话

`requests` 原始相关条目：

```text
78. [GET] http://127.0.0.1:8094/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=20&tab=PENDING => [200] OK
79. [GET] http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=20&tab=PENDING => [403] Forbidden
80. [GET] http://127.0.0.1:8094/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=1&tab=REVIEWING => [200] OK
81. [GET] http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REVIEWING => [403] Forbidden
82. [GET] http://127.0.0.1:8094/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=1&tab=PASSED => [200] OK
83. [GET] http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=PASSED => [403] Forbidden
84. [GET] http://127.0.0.1:8094/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=1&tab=REJECTED => [200] OK
85. [GET] http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REJECTED => [403] Forbidden
86. [GET] http://127.0.0.1:8094/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=20&tab=REJECTED => [200] OK
87. [GET] http://127.0.0.1:8094/api/re/reviews/queue?pageNo=1&pageSize=20&tab=REJECTED => [403] Forbidden
```

`response-body 86` 关键字段原文：

```json
{"code":"0","page":{"pageNo":1,"pageSize":20,"total":1,"records":[{"taskId":4,"taskTitle":"E2E-TEMP-D-REJECT-20260901","assignmentId":4,"branchName":"曲江支行党支部","status":"REJECTED_BY_ORG","submitterName":"张客户经理","submittedAt":"2026-09-01T13:53:03"}],"totalPages":1}}
```

页面运行态核对原文：

```json
{"taskPanel":{"x":240,"y":433.1875,"width":1020,"height":283},"materialPanel":{"x":240,"y":433.1875,"width":1020,"height":148},"rejectedTaskVisible":true,"pendingTaskVisible":false,"tabPanels":1}
```

## mock 路由

两个会话分别执行 `route-list`，原始结果相同：

```text
No active routes
```
