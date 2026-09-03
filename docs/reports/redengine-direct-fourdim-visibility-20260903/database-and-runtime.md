# 数据库与运行态验收

## 数据库门禁与执行结果

先在 `yiti_test` 做只读盘点、目标行备份、首次执行和幂等复跑；三项授权最终均为 `3/3`。随后取得用户对生产 `yiti` 执行的明确确认，才执行同一白名单 DML 脚本：

`docs/superpowers/sql/2026-09-03-redengine-secretary-material-review-grant.sql`

该脚本仅包含 `START TRANSACTION`、`INSERT ... SELECT`、`COMMIT`，不含 DDL、`DELETE`、临时表或独立查询。

受控备份（位于本机 `/tmp`，未提交仓库）：

```text
c432ed509be705f7e878e19df1887616a9cb251f87732ed2491519c0235c03aa  /tmp/yiti_test-redengine-secretary-review-before-20260903.sql
7085690aaba3bf68cc412dc9038b2370c9a66e7dd8a45faf7ca2052306452018  /tmp/yiti-redengine-secretary-review-before-20260903.sql
```

生产执行后只读验收：

```text
RE_SUBMIT:
4  E10001  2  dim1  1.1  1  2026-09-03 17:16:33

RE_ROLE_3 目标授权计数 / 去重资源计数:
3  3

P_RE_REVIEW_APPR  RE
P_RE_REVIEW_Q     RE
P_RE_REVIEW_REJ   RE
```

其中状态 `1` 表示材料仍为已提交；本次只读页面验收没有改变材料状态。

## 当前目录服务

```text
8091  LISTEN  node PID 4067265
cwd: /home/djdev/leid/yiti/xanzc_frontend
cmd: node /home/djdev/leid/yiti/xanzc_frontend/node_modules/.bin/vite
HTTP / => 200

18081 LISTEN  java PID 4067426
cwd: /home/djdev/leid/yiti
cmd: java -Xms256m -Xmx768m -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar
HTTP /api/auth/me (未登录) => 401，符合认证预期
```

后端启动时因目标配置不存在，向 `yiti.SYS_JOB_CONF` 注册了 `RED_ENGINE_TASK_WINDOW` 调度配置；这是服务启动副作用，不属于本次材料状态变更。
