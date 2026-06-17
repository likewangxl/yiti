// 用户通讯录 API —— 对接 auth `/api/users/directory`
// （UserDirectoryController：PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查）
//
// 替代原 `@/api/employees`（portal AddressBookController / ADDRBOOK_EMPLOYEE）作为
// 员工选择器/审批人选择的数据源。原 employees.js 保持不动。
//
// 后端 ResponseWrapper，DTO 字段：empId / empName / orgCode / orgName / position / status
//   - empId = PT_USER.USER_ID（代理键，全系统 empId 规范取值，与会签 assignee 一致），非工号
//   - empName = USERCHNNAME（中文姓名）
//
// 前端统一形态（与原 employees.js 完全一致，保证展示效果不变）：
//   { id, name, org, orgCode, role, status }
import { call } from './http';

function toFront(e) {
  if (!e) return e;
  return {
    id: e.empId,
    name: e.empName,
    org: e.orgName || '',
    orgCode: e.orgCode || '',
    role: e.position || '',
    status: e.status
  };
}

// GET /api/users/directory/{empId}
export async function getEmployee(id) {
  const r = await call('get', `/users/directory/${id}`, {}, null);
  return toFront(r);
}

// GET /api/users/directory/search?keyword=...&limit=20
//   返回 List<UserDirectoryDTO>（无分页包装）
export async function searchEmployees(keyword, limit = 20) {
  const r = await call('get', '/users/directory/search', { params: { keyword, limit } }, []);
  return Array.isArray(r) ? r.map(toFront) : [];
}
