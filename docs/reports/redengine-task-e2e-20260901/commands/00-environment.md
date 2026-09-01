# 环境与隔离检查

## 现场命令

```text
ss -ltnp
ss -ltnp 'sport = :3306'
mysqladmin -h127.0.0.1 -P3306 -uroot --password='[REDACTED]' ping
mysql -h127.0.0.1 -P3306 -uroot --password='[REDACTED]' -N -B -e "SELECT @@hostname, @@port, DATABASE(); SHOW DATABASES LIKE 'yit_test'; SHOW DATABASES LIKE 'yiti'; SHOW DATABASES LIKE 'yiti_test';"
git status --short
git log -8 --oneline --decorate
```

## 脱敏结果摘要

- `mysqld is alive`。
- MySQL 主机返回 `ubuntu`，端口 `3306`；`yit_test`、`yiti`、`yiti_test` 均存在。
- `18091`、`8093` 当时没有监听；`8091` 有其他 Node 进程，未复用。3306 监听进程在当前沙箱的 `ss` 输出中未展示 PID，不能据此确定 systemd 所属服务。
- 任务相关表只读计数：`RE_TASK`、`RE_TASK_INSTANCE`、`RE_TASK_TARGET`、`RE_TASK_BRANCH_ASSIGNMENT`、`RE_TASK_TODO`、`RE_TASK_SUBMISSION`、`RE_TASK_SUBMISSION_FILE`、`RE_TASK_EXPORT_TASK`、`RE_TASK_STATUS_HISTORY` 均为 0。
- 候选账号只读确认：`admin` 有 `SYS_ADMIN`；`E10001` 有 `R_RE_REPORT`；`E20001`、`E50001`、`U_6CBCAC37`、`wangw67` 有启用的 `R_RE_SECR`；`E50002` 有启用的 `R_RE_ORGREV`。历史停用的 `R_RE_BRREV` 仍出现在部分账号的角色关联中，但不作为验收角色。
- 党组织映射：`E10001` 在党组织 4；`U_6CBCAC37`、`wangw67` 在党组织 4；`E20001`、`E50001`、`E50002` 在党组织 2。实际完整链路必须选择同一支部的报送员与支部书记，并由验收负责人私下确认凭据。
- 当前工作树已有其他代理/用户改动；本验收未回退、暂存或修改这些文件。

命令中的数据库密码仅用于现场连接，不应进入提交的证据文件；本摘要不记录密码、哈希或 Cookie。
