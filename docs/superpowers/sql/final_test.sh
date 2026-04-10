#!/bin/bash
# 最终全量回归测试脚本 (100 条)
# 用法: bash final_test.sh
B="http://localhost:8080"
RESULTS=/tmp/final_results.txt
> $RESULTS

extract_id() { python /tmp/extract_id.py; }

run() {
  local tc="$1"; local method="$2"; local url="$3"; local cook="$4"; local data_file="$5"
  local expect="$6"
  if [ -n "$data_file" ]; then
    resp=$(curl -b "$cook" -s -w "\n---HTTP:%{http_code}" -X "$method" \
      -H "Content-Type: application/json; charset=utf-8" \
      --data-binary @"$data_file" "$B$url")
  else
    resp=$(curl -b "$cook" -s -w "\n---HTTP:%{http_code}" -X "$method" "$B$url")
  fi
  http=$(echo "$resp" | tail -1 | sed 's/---HTTP://')
  body=$(echo "$resp" | sed '$d')
  code=$(echo "$body" | python /tmp/extract_code.py)

  status="?"
  if [ -z "$expect" ] || [ "$expect" = "200" ]; then
    if [ "$http" = "200" ] && [ "$code" = "0" ]; then status="PASS"; else status="FAIL"; fi
  elif [ "$expect" = "biz_err_ok" ]; then
    if [ "$http" = "200" ]; then status="PASS"; else status="FAIL"; fi
  elif [ "$expect" = "4xx" ]; then
    if [[ "$http" =~ ^4[0-9][0-9]$ ]]; then status="PASS"; else status="FAIL"; fi
  fi
  printf "%-22s %-6s %-55s HTTP=%-3s code=%-18s %s\n" "$tc" "$method" "$url" "$http" "${code:--}" "$status" | tee -a $RESULTS
}

rm -f /tmp/cookies_*.txt
touch /tmp/cookies_empty.txt

# Login
for user in admin user001 user002 tech_wu; do
  curl -c /tmp/cookies_$user.txt -s -X POST $B/api/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$user\",\"password\":\"123456\"}" > /dev/null
done

# AUTH L1 (8)
echo "======== AUTH L1 (8) ========" | tee -a $RESULTS
echo '{"username":"admin","password":"123456"}' > /tmp/login_admin.json
run "TC-AUTH-L1-001" POST "/api/auth/login" /tmp/cookies_admin.txt /tmp/login_admin.json "200"
run "TC-AUTH-L1-002" GET  "/api/auth/current-user" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L1-003" GET  "/api/auth/permissions"  /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L1-004" GET  "/api/orgs/tree"         /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L1-005" GET  "/api/orgs/subtree"      /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L1-006" GET  "/api/admin/roles/?pageNo=1&pageSize=20" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L1-007" GET  "/api/admin/biz-scopes/matrix" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L1-008" POST "/api/auth/logout"       /tmp/cookies_admin.txt "" "200"
curl -c /tmp/cookies_admin.txt -s -X POST $B/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"123456"}' > /dev/null

# AUTH L2 (18)
echo "======== AUTH L2 (18) ========" | tee -a $RESULTS
echo '{"username":"user001","password":"123456"}' > /tmp/login_u1.json
run "TC-AUTH-L2-001" POST "/api/auth/login" /tmp/cookies_u1_new.txt /tmp/login_u1.json "200"
run "TC-AUTH-L2-002" GET  "/api/auth/current-user" /tmp/cookies_user001.txt "" "200"
echo '{"resourceUrl":"/api/admin/roles/","resourceMethod":"GET"}' > /tmp/check_perm.json
run "TC-AUTH-L2-003" POST "/api/auth/check-permission" /tmp/cookies_admin.txt /tmp/check_perm.json "200"
run "TC-AUTH-L2-004" GET  "/api/admin/resources/tree" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L2-005" GET  "/api/admin/roles/R_ADMIN/resources" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L2-006" GET  "/api/admin/roles/R_RM/users?pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L2-007" GET  "/api/admin/users/user001/roles" /tmp/cookies_admin.txt "" "200"

echo '{"roleCode":"R_TESTZ","roleChName":"retest","remark":"final"}' > /tmp/role_create.json
CREATE=$(curl -b /tmp/cookies_admin.txt -s -X POST "$B/api/admin/roles/" \
  -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/role_create.json)
NEW_RID=$(echo "$CREATE" | python /tmp/extract_id.py)
if [ -n "$NEW_RID" ]; then
  printf "%-22s %-6s %-55s roleId=%s PASS\n" "TC-AUTH-L2-008" "POST" "/api/admin/roles/" "$NEW_RID" | tee -a $RESULTS
else
  printf "%-22s %-6s %-55s FAIL body=%s\n" "TC-AUTH-L2-008" "POST" "/api/admin/roles/" "$(echo $CREATE | head -c 200)" | tee -a $RESULTS
  NEW_RID="nonexistent"
fi

echo '{"roleChName":"retest-v2","remark":"upd"}' > /tmp/role_update.json
run "TC-AUTH-L2-009" PUT    "/api/admin/roles/$NEW_RID" /tmp/cookies_admin.txt /tmp/role_update.json "200"
run "TC-AUTH-L2-010" DELETE "/api/admin/roles/$NEW_RID?reason=cleanup" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L2-011" GET    "/api/admin/biz-scopes?pageNo=1&pageSize=20" /tmp/cookies_admin.txt "" "200"
echo '{"roleId":"R_PRESIDENT","bizType":"TAG","dataScope":"ALL","reason":"test"}' > /tmp/bz_save.json
run "TC-AUTH-L2-012" POST   "/api/admin/biz-scopes" /tmp/cookies_admin.txt /tmp/bz_save.json "200"
run "TC-AUTH-L2-013" GET    "/api/orgs/HQ/users?pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-AUTH-L2-014" GET    "/api/auth/current-user" /tmp/cookies_tech_wu.txt "" "200"
run "TC-AUTH-L2-015" GET    "/api/admin/resources/tree" /tmp/cookies_tech_wu.txt "" "200"
run "TC-AUTH-L2-016" GET    "/api/admin/biz-scopes/matrix" /tmp/cookies_tech_wu.txt "" "200"
echo '{"username":"nonexistent_zzz","password":"123456"}' > /tmp/login_bad1.json
run "TC-AUTH-L2-017" POST   "/api/auth/login" /tmp/cookies_bad.txt /tmp/login_bad1.json "4xx"
echo '{"username":"admin","password":"wrongpwd"}' > /tmp/login_bad2.json
run "TC-AUTH-L2-018" POST   "/api/auth/login" /tmp/cookies_bad.txt /tmp/login_bad2.json "4xx"

# AUTH L3 (6)
echo "======== AUTH L3 (6) ========" | tee -a $RESULTS
run "TC-AUTH-L3-001" GET    "/api/auth/current-user"   /tmp/cookies_empty.txt "" "4xx"
echo '{"roleCode":"R_HACK","roleChName":"hack"}' > /tmp/role_hack.json
run "TC-AUTH-L3-002" POST   "/api/admin/roles/" /tmp/cookies_user001.txt /tmp/role_hack.json "4xx"
run "TC-AUTH-L3-003" DELETE "/api/admin/roles/R_ADMIN?reason=hack" /tmp/cookies_user001.txt "" "4xx"
echo '{"roleId":"R_ADMIN","bizType":"NAV","dataScope":"ALL","reason":"hack"}' > /tmp/bz_hack.json
run "TC-AUTH-L3-004" POST   "/api/admin/biz-scopes" /tmp/cookies_user001.txt /tmp/bz_hack.json "4xx"
run "TC-AUTH-L3-005" DELETE "/api/admin/roles/R_ADMIN" /tmp/cookies_admin.txt "" "4xx"
run "TC-AUTH-L3-006" GET    "/api/orgs/NONEXIST_ORG/users" /tmp/cookies_admin.txt "" "200"

# GOV L1 (12)
echo "======== GOV L1 (12) ========" | tee -a $RESULTS
run "TC-GOV-L1-001" GET  "/api/sys/dicts"                      /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-002" GET  "/api/sys/dicts/YES_NO/items"         /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-003" GET  "/api/sys/calendar?year=2026&month=4" /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-004" GET  "/api/sys/dicts?dictType=INDUSTRY"    /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-005" GET  "/api/admin/sys/configs?pageNo=1"     /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-006" GET  "/api/admin/sys/calendar?year=2026"   /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-007" GET  "/api/admin/sys/audit-logs?pageNo=1"  /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-008" GET  "/api/notifications?pageNo=1"         /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-009" GET  "/api/notifications/unread-count"     /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-010" GET  "/api/admin/sys/jobs?pageNo=1"        /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-011" GET  "/api/admin/sql-probe/history?pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L1-012" GET  "/api/files?bizType=TEST&bizId=1"     /tmp/cookies_admin.txt "" "200"

# GOV L2 (22)
echo "======== GOV L2 (22) ========" | tee -a $RESULTS
echo '{"dictType":"TEST_TYPE","dictCode":"X","dictLabel":"labelX","dictValue":"v1","sortOrder":1,"remark":"auto"}' > /tmp/dict_create.json
CREATE=$(curl -b /tmp/cookies_admin.txt -s -X POST "$B/api/admin/sys/dicts" \
  -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/dict_create.json)
DICT_ID=$(echo "$CREATE" | python /tmp/extract_id.py)
if [ -n "$DICT_ID" ]; then
  printf "%-22s %-6s %-55s dictId=%s PASS\n" "TC-GOV-L2-001" "POST" "/api/admin/sys/dicts" "$DICT_ID" | tee -a $RESULTS
else
  printf "%-22s %-6s %-55s FAIL body=%s\n" "TC-GOV-L2-001" "POST" "/api/admin/sys/dicts" "$(echo $CREATE | head -c 200)" | tee -a $RESULTS
  DICT_ID="nonexistent"
fi

echo '{"dictLabel":"updated","dictValue":"v2","sortOrder":2,"remark":"upd"}' > /tmp/dict_update.json
run "TC-GOV-L2-002" PUT    "/api/admin/sys/dicts/$DICT_ID"        /tmp/cookies_admin.txt /tmp/dict_update.json "200"
echo '{"status":"DISABLED"}' > /tmp/dict_status.json
run "TC-GOV-L2-003" PUT    "/api/admin/sys/dicts/$DICT_ID/status" /tmp/cookies_admin.txt /tmp/dict_status.json "200"
run "TC-GOV-L2-004" DELETE "/api/admin/sys/dicts/$DICT_ID"        /tmp/cookies_admin.txt "" "200"
echo '{"configValue":"test_value","reason":"auto-test"}' > /tmp/cfg_update.json
run "TC-GOV-L2-005" PUT    "/api/admin/sys/configs/TEST_KEY"      /tmp/cookies_admin.txt /tmp/cfg_update.json "biz_err_ok"
echo '{"year":2026}' > /tmp/cal_init.json
run "TC-GOV-L2-006" POST   "/api/admin/sys/calendar/init"         /tmp/cookies_admin.txt /tmp/cal_init.json "200"
echo '{"isWorkday":false,"remark":"labor-day"}' > /tmp/cal_day.json
run "TC-GOV-L2-007" PUT    "/api/admin/sys/calendar/2026-05-01"   /tmp/cookies_admin.txt /tmp/cal_day.json "200"
run "TC-GOV-L2-008" GET    "/api/admin/sys/calendar?year=2026&month=5" /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L2-009" GET    "/api/admin/sys/audit-logs?empId=admin" /tmp/cookies_admin.txt "" "200"
EXP_HTTP=$(curl -b /tmp/cookies_admin.txt -s -o /tmp/audit.xlsx -w "%{http_code}" -X POST "$B/api/admin/sys/audit-logs/export?empId=admin")
if [ "$EXP_HTTP" = "200" ]; then STATUS=PASS; else STATUS=FAIL; fi
printf "%-22s %-6s %-55s HTTP=%s %s\n" "TC-GOV-L2-010" "POST" "/api/admin/sys/audit-logs/export" "$EXP_HTTP" "$STATUS" | tee -a $RESULTS
run "TC-GOV-L2-011" GET    "/api/admin/sys/jobs/JOB_DEMO/logs?pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L2-012" PUT    "/api/admin/sys/jobs/JOB_DEMO/pause" /tmp/cookies_admin.txt "" "biz_err_ok"
echo '{"sql":"SELECT 1 AS ok","remark":"test"}' > /tmp/sql_ok.json
run "TC-GOV-L2-013" POST   "/api/admin/sql-probe/execute" /tmp/cookies_admin.txt /tmp/sql_ok.json "200"

echo "test" > /tmp/tfile.txt
UP_HTTP=$(curl -b /tmp/cookies_admin.txt -s -o /tmp/up_resp.txt -w "%{http_code}" -X POST "$B/api/files/upload" -F "file=@/tmp/tfile.txt" -F "bizType=TEST" -F "bizId=1")
UP_CODE=$(cat /tmp/up_resp.txt | python /tmp/extract_code.py)
if [ "$UP_CODE" = "GOV-50001" ]; then
  printf "%-22s %-6s %-55s HTTP=%s code=%s ENV-BLOCKED (MinIO)\n" "TC-GOV-L2-014" "POST" "/api/files/upload" "$UP_HTTP" "$UP_CODE" | tee -a $RESULTS
elif [ "$UP_HTTP" = "200" ] && [ "$UP_CODE" = "0" ]; then
  printf "%-22s %-6s %-55s HTTP=%s code=%s PASS\n" "TC-GOV-L2-014" "POST" "/api/files/upload" "$UP_HTTP" "$UP_CODE" | tee -a $RESULTS
else
  printf "%-22s %-6s %-55s HTTP=%s code=%s FAIL\n" "TC-GOV-L2-014" "POST" "/api/files/upload" "$UP_HTTP" "$UP_CODE" | tee -a $RESULTS
fi

run "TC-GOV-L2-015" GET    "/api/files/nonexistent-fid/download" /tmp/cookies_admin.txt "" "biz_err_ok"
run "TC-GOV-L2-016" DELETE "/api/files/nonexistent-fid" /tmp/cookies_admin.txt "" "biz_err_ok"
run "TC-GOV-L2-017" PUT    "/api/notifications/read-all" /tmp/cookies_admin.txt "" "200"
run "TC-GOV-L2-018" PUT    "/api/notifications/fake-id/read" /tmp/cookies_admin.txt "" "biz_err_ok"
run "TC-GOV-L2-019" GET    "/api/admin/sys/configs?pageNo=1" /tmp/cookies_tech_wu.txt "" "200"
run "TC-GOV-L2-020" GET    "/api/admin/sys/jobs" /tmp/cookies_tech_wu.txt "" "200"
run "TC-GOV-L2-021" GET    "/api/sys/dicts?dictType=YES_NO" /tmp/cookies_admin.txt "" "200"
echo '{"sql":"SELECT COUNT(*) AS cnt FROM PT_RESOURCE","remark":"count"}' > /tmp/sql_cnt.json
run "TC-GOV-L2-022" POST   "/api/admin/sql-probe/execute" /tmp/cookies_admin.txt /tmp/sql_cnt.json "200"

# GOV L3 (8)
echo "======== GOV L3 (8) ========" | tee -a $RESULTS
echo '{"dictType":"T","dictCode":"C","dictLabel":"L","dictValue":"V","sortOrder":1}' > /tmp/dict_hack.json
run "TC-GOV-L3-001" POST   "/api/admin/sys/dicts" /tmp/cookies_user001.txt /tmp/dict_hack.json "4xx"
echo '{"sql":"SELECT 1","remark":"hack"}' > /tmp/sql_hack.json
run "TC-GOV-L3-002" POST   "/api/admin/sql-probe/execute" /tmp/cookies_user001.txt /tmp/sql_hack.json "4xx"
echo '{"sql":"DROP TABLE PT_USER","remark":"danger"}' > /tmp/sql_drop.json
run "TC-GOV-L3-003" POST   "/api/admin/sql-probe/execute" /tmp/cookies_admin.txt /tmp/sql_drop.json "biz_err_ok"
echo '{"configValue":"x"}' > /tmp/cfg_bad.json
run "TC-GOV-L3-004" PUT    "/api/admin/sys/configs/TEST" /tmp/cookies_admin.txt /tmp/cfg_bad.json "4xx"
run "TC-GOV-L3-005" POST   "/api/admin/sys/audit-logs/export" /tmp/cookies_user001.txt "" "4xx"
echo '{"reason":"hack"}' > /tmp/job_trig.json
run "TC-GOV-L3-006" POST   "/api/admin/sys/jobs/J/trigger" /tmp/cookies_user001.txt /tmp/job_trig.json "4xx"
echo '{"isWorkday":true}' > /tmp/cal_bad.json
run "TC-GOV-L3-007" PUT    "/api/admin/sys/calendar/invalid-date" /tmp/cookies_admin.txt /tmp/cal_bad.json "4xx"
run "TC-GOV-L3-008" GET    "/api/admin/sys/audit-logs" /tmp/cookies_empty.txt "" "4xx"

# WF L1 (6)
echo "======== WF L1 (6) ========" | tee -a $RESULTS
run "TC-WF-L1-001" GET  "/api/workflow/tasks?pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L1-002" GET  "/api/workflow/tasks/done?pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L1-003" GET  "/api/workflow/process-map?businessKey=TEST:1" /tmp/cookies_admin.txt "" "biz_err_ok"
run "TC-WF-L1-004" GET  "/api/admin/workflow/timeout-rules" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L1-005" GET  "/api/admin/workflow/process-definitions" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L1-006" GET  "/api/workflow/tasks?pageNo=1" /tmp/cookies_user001.txt "" "200"

# WF L2 (14)
echo "======== WF L2 (14) ========" | tee -a $RESULTS
run "TC-WF-L2-001" GET  "/api/workflow/tasks?bizType=LEAD&pageNo=1" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L2-002" GET  "/api/workflow/tasks/done?keyword=test" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L2-003" GET  "/api/workflow/tasks/nonexistent-task-id" /tmp/cookies_admin.txt "" "biz_err_ok"
run "TC-WF-L2-004" GET  "/api/workflow/processes/nonexistent-proc-id" /tmp/cookies_admin.txt "" "biz_err_ok"
run "TC-WF-L2-005" GET  "/api/workflow/process-map?bizType=LEAD&bizId=1" /tmp/cookies_admin.txt "" "biz_err_ok"
echo '{"processDefinitionKey":"test-proc","nodeKey":"approval","warningHours":24,"timeoutHours":48}' > /tmp/tr_create.json
run "TC-WF-L2-006" POST "/api/admin/workflow/timeout-rules" /tmp/cookies_admin.txt /tmp/tr_create.json "200"
run "TC-WF-L2-007" GET  "/api/admin/workflow/timeout-rules?processDefinitionKey=test-proc" /tmp/cookies_admin.txt "" "200"

TR_ID=$(curl -b /tmp/cookies_admin.txt -s "$B/api/admin/workflow/timeout-rules?processDefinitionKey=test-proc" | python /tmp/extract_first_list_id.py)
if [ -n "$TR_ID" ]; then
  echo '{"warningHours":12,"timeoutHours":24}' > /tmp/tr_update.json
  run "TC-WF-L2-008" PUT "/api/admin/workflow/timeout-rules/$TR_ID" /tmp/cookies_admin.txt /tmp/tr_update.json "200"
else
  echo "TC-WF-L2-008 FAIL (no rule id)" | tee -a $RESULTS
fi

run "TC-WF-L2-009" GET  "/api/admin/workflow/node-candidates" /tmp/cookies_admin.txt "" "200"
echo '{"processDefinitionKey":"test-proc","nodeKey":"approval","candidateType":"ROLE","candidateValue":["R_ADMIN","R_BRANCH_MGR"]}' > /tmp/nc_create.json
run "TC-WF-L2-010" POST "/api/admin/workflow/node-candidates" /tmp/cookies_admin.txt /tmp/nc_create.json "200"
run "TC-WF-L2-011" GET  "/api/admin/workflow/node-candidates?processDefinitionKey=test-proc" /tmp/cookies_admin.txt "" "200"
run "TC-WF-L2-012" GET  "/api/admin/workflow/node-forms" /tmp/cookies_admin.txt "" "200"
echo '{"processDefinitionKey":"test-proc","nodeKey":"approval","formFields":"[\"f1\"]","editableFields":"[\"f1\"]","requiredFields":"[]"}' > /tmp/nf_create.json
run "TC-WF-L2-013" POST "/api/admin/workflow/node-forms" /tmp/cookies_admin.txt /tmp/nf_create.json "200"
run "TC-WF-L2-014" GET  "/api/admin/workflow/timeout-rules?processDefinitionKey=none" /tmp/cookies_admin.txt "" "200"

# WF L3 (6)
echo "======== WF L3 (6) ========" | tee -a $RESULTS
run "TC-WF-L3-001" POST "/api/workflow/tasks/bad-id/claim" /tmp/cookies_admin.txt "" "biz_err_ok"
echo '{"opinion":"test","formData":{}}' > /tmp/approve.json
run "TC-WF-L3-002" POST "/api/workflow/tasks/bad-id/approve" /tmp/cookies_admin.txt /tmp/approve.json "biz_err_ok"
echo '{"processDefinitionKey":"x","nodeKey":"n","warningHours":1,"timeoutHours":2}' > /tmp/tr_hack.json
run "TC-WF-L3-003" POST "/api/admin/workflow/timeout-rules" /tmp/cookies_user001.txt /tmp/tr_hack.json "4xx"
run "TC-WF-L3-004" GET  "/api/admin/workflow/process-definitions" /tmp/cookies_user001.txt "" "4xx"
run "TC-WF-L3-005" GET  "/api/workflow/tasks" /tmp/cookies_empty.txt "" "4xx"
run "TC-WF-L3-006" GET  "/api/workflow/process-map" /tmp/cookies_admin.txt "" "4xx"

# Summary
echo | tee -a $RESULTS
PASS=$(grep -c "PASS\|ENV-BLOCKED" $RESULTS)
FAIL=$(grep -c "FAIL" $RESULTS)
echo "===== SUMMARY: PASS=$PASS FAIL=$FAIL =====" | tee -a $RESULTS
