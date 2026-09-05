# 官方 playwright-cli 验收命令

以下命令均在 `/home/djdev/leid/yiti` 执行。登录密码、Cookie、token、Authorization 及登录请求体未归档。

## 启动

```bash
mvn clean install -DskipTests

env REDENGINE_TASK_DB_USERNAME='<redacted>' \
  REDENGINE_TASK_DB_PASSWORD='<redacted>' \
  REDENGINE_TASK_SERVER_PORT=18092 \
  SPRING_PROFILES_ACTIVE=redengine-task-e2e \
  java -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar

env VITE_DEV_HOST=127.0.0.1 \
  VITE_DEV_PORT=8094 \
  VITE_DEV_STRICT_PORT=true \
  VITE_PROXY_TARGET=http://127.0.0.1:18092 \
  VITE_USE_MOCK=false \
  npm --prefix xanzc_frontend run dev
```

## 浏览器基线

```bash
export PWTEST_DAEMON_SESSION_DIR=/tmp/redengine-pw-daemon-20260905b
export PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright
export PWCLI=xanzc_frontend/node_modules/.bin/playwright-cli

$PWCLI -s=redengine-reporter-proof open \
  'http://127.0.0.1:8094/#/login?normal' --browser=chromium
$PWCLI -s=redengine-secretary-proof open \
  'http://127.0.0.1:8094/#/login?normal' --browser=chromium
```

两个会话分别在浏览器中输入报送员 `rm_zhang` 和支部书记 `wangw67` 的凭据。随后使用官方 CLI 的 `goto`、`click`、`run-code`、`snapshot`、`screenshot`、`requests`、`response-body`、`console` 和 `route-list` 命令。

核心页面：

```text
http://127.0.0.1:8094/#/redengine/records
http://127.0.0.1:8094/#/redengine/branch-review
http://127.0.0.1:8094/#/redengine/task-entry?taskId=4&tab=rejected&assignmentId=4&status=REJECTED_BY_ORG&taskInstanceId=4
```

截图命令示例：

```bash
$PWCLI -s=redengine-reporter-proof screenshot \
  '[data-test=review-history]' \
  --filename=docs/reports/redengine-test-problems-20260905/screenshots/reporter-review-history-panel.png

$PWCLI -s=redengine-secretary-proof screenshot \
  --filename=docs/reports/redengine-test-problems-20260905/screenshots/secretary-rejected-task-tab.png \
  --full-page
```

## 自动化验证

```bash
mvn -pl red-engine-center test
bash scripts/check-contract-docs.sh

npm --prefix xanzc_frontend run test -- --run \
  src/views/redengine/branch-review/__tests__/BranchReviewView.spec.js \
  src/views/redengine/records/__tests__/RecordsView.spec.js \
  src/views/redengine/records/__tests__/TemporaryTaskEntryView.spec.js

npm --prefix xanzc_frontend run build
git diff --check
```
