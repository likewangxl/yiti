# 验收命令

以下命令均在 `/home/djdev/leid/yiti` 或其 `xanzc_frontend` 子目录执行。登录口令未写入归档。

```bash
# 核验当前运行实例
ss -ltnp '( sport = :8093 or sport = :18091 )'
ps -eo pid,ppid,args

# 前端定向测试
cd xanzc_frontend
npm test -- --run \
  src/views/redengine/__tests__/RedEngineFeatureMatrix.spec.js \
  src/views/redengine/__tests__/RedEngineMenuFilter.spec.js \
  src/views/redengine/__tests__/RedEngineRoutes.spec.js \
  src/views/redengine/records/__tests__/RecordsView.spec.js \
  src/views/redengine/branch-review/__tests__/BranchReviewView.spec.js \
  src/views/redengine/report/__tests__/JointView.spec.js
npm run build

# 官方 CLI 创建独立真实浏览器会话
xanzc_frontend/node_modules/.bin/playwright-cli \
  -s=task-processing-reporter-20260903 open \
  'http://127.0.0.1:8093/#/login?normal' --browser=chromium
xanzc_frontend/node_modules/.bin/playwright-cli \
  -s=task-processing-secretary-20260903 open \
  'http://127.0.0.1:8093/#/login?normal' --browser=chromium

# 每个会话执行 snapshot、click、fill、screenshot、route-list、console 和 requests
xanzc_frontend/node_modules/.bin/playwright-cli \
  -s=<session> requests --filter '/api/re/'
```

页面路径：

- 报送员任务处理：`http://127.0.0.1:8093/#/redengine/records`
- 四大维度上报：`http://127.0.0.1:8093/#/redengine/report?taskId=6&assignmentId=8&taskInstanceId=6`
- 书记任务处理：`http://127.0.0.1:8093/#/redengine/branch-review`
