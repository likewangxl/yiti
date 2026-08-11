# Controlled batch manifest trust root

本仓库**故意不提交**以下两把公钥、任何签名 manifest、审批 receipt 或部署根。因此当前
`2026-08-12-controlled-sql-batch-runner.sh --execute` 必须 fail-close，且不会启动 mysql。

```text
/opt/bank/controlled-sql-batch-runner/release/
├── approved-manifests/<受控名称>.json
├── approved-manifests/<受控名称>.json.sig
├── approval-receipts/<receipt UUID>.json
├── approval-receipts/<receipt UUID>.json.sig
├── credentials/mysql-client.cnf
├── trust/mysql-ca.pem
└── docs/superpowers/sql/controlled-batch-manifest-trust/
    ├── controlled-manifest-public.pem
    └── approval-service-receipt-public.pem
```

部署根、每一级父目录、上述文件及 `/var/empty/controlled-sql-runner` 都必须由 root 所有，且
组/其他用户无写权限；runner 也拒绝以信任根 owner 身份执行。运行时以 `openat` +
`O_NOFOLLOW` + `fstat` 打开并只读取一次：manifest 的同一份 bytes 用于 detached signature、
SHA-256、严格 JSON 解析和 schema 校验；三份 SQL 的同一份已审 bytes 直接进入单一 mysql stdin，
不会再通过文件路径重载。

两把公钥的 SHA-256 必须作为获审 release 的编译期固定值。当前代码中的全零 fingerprint 是
“未配发”哨兵，不能被任何实际 key 满足；不得通过环境变量、CLI、网络下载或测试开关提供 key。
manifest 签名与审批服务 receipt 签名必须由不同的固定 pin 验证。

`mysql-client.cnf` 只能有唯一 `[client]`，并且恰好有 `user`、`password`、
`ssl-mode=VERIFY_IDENTITY`、固定 `ssl-ca` 四项。runner 拒绝 include、所有其他 section 和所有
未授权 option（包括 `init-command`、`execute`、`host`、`port`、`database`、`reconnect`、`force`
与 login-path）。密码不进入 manifest、CLI、日志或错误文本。

## MySQL option-file 边界

本机 MySQL 8.0.33 的随包 `mysql(1)` 明确说明：

- `--defaults-extra-file` 会在 global option files 之后读取，故不能使用；
- `--no-defaults` 与 `--defaults-file` 都仍会读取 `.mylogin.cnf`；
- 本机 `mysql --help` 没有 `--no-login-paths`。

因此执行 argv 使用获审 `--defaults-file=<固定文件>` 作为第一个 client option，以排除 global/
user option files；同时 runner 要求当前执行账户在 passwd 数据库中的真实 home 恰为 root 管理且
为空的 `/var/empty/controlled-sql-runner`，并用完全替换的 child environment 固定 `HOME` 到同一
目录，不继承 `MYSQL_HOME`、`MYSQL_TEST_LOGIN_FILE` 或其他操作者环境。任一事实无法证明即
fail-close，绝不改用 `--defaults-extra-file` 或测试环境变量。

## receipt 与可证明边界

runner 可本地证明签名、固定 key pin、短 TTL、not-before、manifest/receipt/A2 hash 绑定、受信路径
权限与 SQL bytes 一致性。它**不能**仅靠本地文件证明 nonce 在审批服务端只使用一次，也不能在离线
时查询 receipt 后续是否撤销。因此 `--execute` 必须有独立审批服务签发且以第二把固定公钥验签的
receipt；receipt 必须绑定 manifest SHA-256、change ticket、nonce、短生效窗口、`single_use` policy、
revocation epoch，以及 A2 恢复点的 `signed_proof_sha256`。缺少 receipt、key pin、签名或这些绑定时
一律不得执行；文档描述不能替代该外部信任证明。

`../2026-08-12-controlled-sql-batch-manifest.example.json` 的所有标识、时间、hash 均为占位内容，
没有签名，不能作为验证或执行输入。
