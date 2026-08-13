# Controlled batch execution trust root

本仓库故意不提交任何生产公钥、私钥、签名 manifest、A2 proof、在线 claim helper、systemd
安装实例或 /opt/bank/controlled-sql-batch-runner/release。普通 shell 的 execute 永久
fail-close；直接 Python execute 也会因缺少 root-managed launcher proof fail-close，且两条
路径都不得启动 mysql。

生产唯一启动链如下：

root-owned systemd unit
  -> /usr/bin/python3.10 -I root-managed launcher
  -> root launcher signs sealed PID/starttime-bound proof then drops to controlled-sql-runner
  -> runner validates release, manifest and launcher proof
  -> root-owned absolute online claim helper atomically consumes receipt
  -> runner verifies independently signed claim token and A2 proof
  -> exactly one mysql process whose stdin contains exactly two approved rounds

## Root deployment contract

安装时，root 必须将 service 模板安装为
/etc/systemd/system/bank-controlled-sql-batch-runner@.service，并把 release 及每一级父目录设为
root 所有、组/其他用户不可写。unit 必须保持：

- 固定 ExecStart 使用 /usr/bin/python3.10 -I 和 root-managed launcher，且无 shell；
- 在解释器启动前 UnsetEnvironment 清除 BASH_ENV、ENV、批准清单中的 LD 系列、PYTHON 系列、
  locale、HOME/PATH/XDG 变量，再显式建立最小 allowlist 环境；
- launcher 起始为 root 仅用于读取 root-only attestation key，随后 setgroups/setgid/setuid 到
  controlled-sql-runner；该账户 home 必须是 root-owned 空的
  /var/empty/controlled-sql-runner，shell 必须是 /usr/sbin/nologin；
- 保持 NoNewPrivileges=yes、ProtectSystem=strict、ReadOnlyPaths、ProtectHome=yes、
  PrivateTmp=yes、设备/内核/namespace 限制等模板硬化项。

### Attestation 私钥安装验证

`launcher-attestation-private.pem` 是唯一比 release 更严格的本地秘密：它必须是 `root:root` 的
常规文件，权限只能为 `0400` 或 `0600` 风格，即 `mode & 0o077 == 0`。从 `/` 根锚点之后直到
私钥父目录的每一级都必须 `root:root` 且同样满足 `mode & 0o077 == 0`；这既禁止组/其他用户写入，
也禁止 service account 对私钥目录读取或遍历。任何符号链接（包括祖先目录和最终私钥）都不属于
受信安装，launcher 会以逐级 `O_NOFOLLOW` 打开后 fail-close。

安装验证是 root 发布流程中的只读步骤，不启动 unit、不读取或输出私钥内容，也不授权 execute。发布人
应逐级检查 `namei -l /opt/bank/controlled-sql-batch-runner/release/root-only/launcher-attestation-private.pem`，
并以 `stat -c '%U:%G %a %F %n'` 核验所有路径段和最终文件；任意非 `root:root`、任意 group/other
权限、非 regular file 或符号链接均必须停止发布。runner 进程不会从环境、普通 release 可读性或 unit
只读挂载推断这些私钥条件。

runner 不把环境变量、argv、当前工作目录或普通文件视为 launcher 证明。它只接受 root launcher
用 root-only 私钥签名、通过固定 198/199 号 sealed memfd 继承的 proof，并核对当前 PID 与
/proc/self/stat starttime、service UID、manifest hash、runner/launcher/unit/helper hash 和短
时间窗。单靠应用代码无法证明 root 部署已正确完成；proof、key、pin、密封、cgroup 或部署缺失均是
明确的外部信任边界，必须 fail-close。

## Online receipt claim

不再有本地 approval-receipts 文件可作为执行资格。manifest 只引用 receipt id，runner 只调用固定
绝对路径、root-owned 且 SHA-256 绑定的 helper：

/usr/local/libexec/bank-controlled-sql-receipt-claim-helper --protocol=v1 --claim-once

该 helper 是仓库外的特权组件，必须在线连接审批服务并在同一原子事务中检查撤销状态、消费 receipt
及 nonce，然后仅返回审批服务独立私钥签名的短时 claim token。helper 不存在、断线、超时、已消费、
撤销、非零退出、响应重复键、签名/pin/token/时间/manifest/launcher 绑定任一失败均拒绝执行；仓库
绝不实现伪在线服务，也不接受可重放的本地 nonce_policy 或 revocation_epoch 字段。

协议 schema 与非执行 fixture 位于以下位置：

- receipt-claim-token.schema.json
- a2-recovery-proof.schema.json
- scripts/tests/fixtures/controlled-sql-receipt-claim-token.fixture.json
- scripts/tests/fixtures/controlled-sql-a2-recovery-proof.fixture.json

## A2 和 mysql 边界

A2 proof 位于 root-managed a2-recovery-proofs/proof UUID 文件和 detached signature。runner 在同一
打开 fd 的 bytes 上依次 hash、验独立 key、严格 JSON/schema，并要求独立 issuer、目标四元组
server_uuid/server_hostname/connect_port/schema、恢复状态 VERIFIED、recovery point hash、短
issued/expiry、manifest hash、claim id 与 raw claim-token hash 全部精确绑定。

mysql 不再运行版本或 help 探测。固定 mysql 文件的 SHA-256、版本、option-file 契约以及
SBOM/manpage hash 由独立签名 mysql release contract 冻结；`option_contract` 必须精确为
`fixed-defaults-file-tcp-verify-identity-binary-mode`，并由已签名 contract 与 manifest 的
`artifact_sha256.mysql_release_contract` 共同冻结。因此 runner 运行时只 hash 固定 mysql 文件，且唯一
mysql argv 必含一次 `--binary-mode`。通过所有前置边界后才启动一次 mysql 连接进程，输入内恰好两轮，
不重连、不重试。

所有 example 中的时间、ID、hash 都是占位符，且没有签名或 helper 服务，不能用于 validate 或
execute。
