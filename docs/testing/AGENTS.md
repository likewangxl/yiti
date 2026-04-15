# docs/testing/AGENTS.md

本目录存放 API 集成测试相关的文件和脚本。

## 文件说明

| 文件 | 用途 |
|------|------|
| `test-data.sql` | API 测试用种子数据（MySQL），包含 auth/governance/workflow 测试数据 |
| `api-test.sh` | API 集成测试脚本（bash/curl） |

## 前置条件

1. **MySQL** 运行中 (`localhost:3306`)
2. **Redis** 运行中 (`localhost:6379`)
3. 数据库 `onepl` 已创建
4. Bootstrap 服务已启动: `mvn spring-boot:run`（在 bootstrap 目录）

## 使用步骤

### 步骤 1: 导入测试数据

```bash
# 初始化测试数据（重复执行安全，ON DUPLICATE KEY UPDATE）
mysql -uroot -p123456 onepl < docs/testing/test-data.sql
```

### 步骤 2: 启动服务

```bash
cd bootstrap
mvn spring-boot:run
```

### 步骤 3: 运行 API 测试

```bash
bash docs/testing/api-test.sh
```

## 测试数据说明

### 测试用户

| USER_ID | USERNAME | 密码 | 角色 | 机构 |
|---------|----------|------|------|------|
| admin | admin | password | ADMIN (R001) | HQ |
| user001 | user001 | password | CUST_MGR (R002) | BJ_CY |
| user002 | user002 | password | BRANCH_HD (R003) | SH_PD |

### 测试密码说明

BCrypt hash: `$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy`
对应明文: `password`

### 测试 API 覆盖

- 认证: 登录/登出/当前用户
- 组织: 树查询/按 Code 查询/子机构
- 角色: 分页查询/按 ID 查询/按 Code 查询
- 字典/配置: 分页/字典项/标签/配置值
- 业务范围: 角色业务范围/分页查询
- 资源: 资源树/详情
- 工作流: 流程查询/待办/已办
- 鉴权链路: 有 Session vs 无 Session 访问

## 数据库连接

dev 环境: `jdbc:mysql://localhost:3306/onepl`
用户名: `root`
密码: `123456`
