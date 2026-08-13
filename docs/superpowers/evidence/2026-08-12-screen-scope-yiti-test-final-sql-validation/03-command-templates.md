# 脱敏真实命令模板

下列模板对应本次实际直接执行方式。因用户已要求停止复杂 runner/checker，本轮没有新建或继续开发 runner/checker。密码已脱敏；实际目标始终为 `127.0.0.1:3306/yiti_test`。

```bash
env MYSQL_PWD='***' mysql --protocol=TCP -h127.0.0.1 -P3306 -uroot \
  --database=yiti_test --default-character-set=utf8mb4 --batch --raw --show-warnings <<'SQL'
SELECT DATABASE() AS database_name, @@server_uuid AS server_uuid, @@hostname AS hostname, @@port AS port;
SET @approved_target_server_uuid = '<same-session-server_uuid>';
SET @approved_target_hostname = '<same-session-hostname>';
SET @approved_target_port = '<same-session-port-as-decimal-text>';
SET @approved_target_schema = 'yiti_test';
SET @approved_change_ticket = 'LOCAL-YITI-TEST-SCREEN-SCOPE-20260812-01';
SET @approved_manifest_sha256 = '36561c45410287fe9981a563bd081116a38c049dc1eb517c3758b89a28c381a5';
SOURCE docs/superpowers/sql/<auth-or-align-or-seed>.sql;
SQL
```

首次 seed 之前另以同一连接参数执行最小只读盘点；各次 `SOURCE` 均在单一 mysql 会话中先核验身份，再设置 guard 变量。`00-local-manifest.txt` 是本地可审计清单，不是外部工单，也不代表任何生产授权。
