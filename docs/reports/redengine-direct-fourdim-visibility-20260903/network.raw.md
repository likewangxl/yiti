# Playwright CLI 请求与路由原始摘要

命令：`playwright-cli -s=direct-fourdim-evidence route-list`

```text
No active routes
```

结论：浏览器未注册 mock/拦截路由。

命令：`playwright-cli -s=direct-fourdim-evidence requests --filter '/api/re/'`

```text
7.  [GET] http://127.0.0.1:8091/api/re/home/summary => [200] OK
15. [GET] http://127.0.0.1:8091/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=20&tab=PENDING => [200] OK
16. [GET] http://127.0.0.1:8091/api/re/reviews/queue?pageNo=1&pageSize=20&tab=PENDING => [200] OK
17. [GET] http://127.0.0.1:8091/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=1&tab=REVIEWING => [200] OK
18. [GET] http://127.0.0.1:8091/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REVIEWING => [200] OK
19. [GET] http://127.0.0.1:8091/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=1&tab=PASSED => [200] OK
20. [GET] http://127.0.0.1:8091/api/re/reviews/queue?pageNo=1&pageSize=1&tab=PASSED => [200] OK
21. [GET] http://127.0.0.1:8091/api/re/reviews/tasks/branch/queue?pageNo=1&pageSize=1&tab=REJECTED => [200] OK
22. [GET] http://127.0.0.1:8091/api/re/reviews/queue?pageNo=1&pageSize=1&tab=REJECTED => [200] OK
24. [GET] http://127.0.0.1:8091/api/re/reviews/4/preview => [200] OK
```

请求均通过当前前端 `8091` 的真实代理到后端；未读取或归档 Cookie、Session、Authorization、登录请求体或响应体。
