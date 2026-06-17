// 员工 API —— 对接 yiti `/api/employees`（AddressBookController / 通讯录）
//
// 后端 ResponseWrapper.page(PageResult<EmployeeDetailDTO>)，DTO 字段：
//   empId / empName / mobile / email / orgCode / orgName / position / positionDesc /
//   status / responsibleProductIds / canEdit ...
//
// 前端统一形态（动态指标查询「对象选择」期望）：
//   { id, name, org, orgCode, role, status }
//
// 真接口失败 / 表为空 时回退 mock employeesList（已是前端形态）。
import { call } from './http';
import { employeesList } from '@/mock';

function toFront(e) {
  if (!e) return e;
  // mock 已是前端形态（无 empId 字段）→ 原样返回
  if (e.empId == null) return e;
  return {
    id: e.empId,
    name: e.empName,
    org: e.orgName || '',
    orgCode: e.orgCode || '',
    role: e.position || e.positionDesc || '',
    status: e.status
  };
}

// GET /api/employees —— 员工分页列表
// view 期望数组：unwrap PageResult.records 后字段适配
export async function listEmployees(params = {}) {
  const r = await call('get', '/employees', { params }, employeesList);
  if (Array.isArray(r)) return r.map(toFront);
  if (Array.isArray(r?.records)) return r.records.map(toFront);
  return [];
}

// GET /api/employees/{empId}
export async function getEmployee(id) {
  const r = await call('get', `/employees/${id}`, {},
    () => employeesList.find(e => e.id === id) || employeesList[0]);
  return toFront(r);
}

// GET /api/employees/search?keyword=...&limit=20
//   后端单独的关键字搜索端点，返回 List<EmployeeSearchDTO>（无分页包装）
export async function searchEmployees(keyword, limit = 20) {
  const r = await call('get', '/employees/search', { params: { keyword, limit } }, []);
  return Array.isArray(r) ? r.map(toFront) : [];
}

// ============================================================
// 通讯录页专用：保留原始 EmployeeDetailDTO（含 mobile/email/responsibleProducts/selfDesc/updatedTime/canEdit）
// ============================================================

// GET /api/employees —— 原始分页（EmployeeQueryReqDTO: keyword/orgCode/position/status/pageNo/pageSize）
export function pageEmployees(params = {}) {
  return call('get', '/employees', { params }, { records: [], total: 0 });
}

// PUT /api/employees/{empId} —— 编辑（EmployeeUpdateReqDTO: mobile/email/position/selfDesc/responsibleProductIds）
export function updateEmployee(empId, data) {
  return call('put', `/employees/${empId}`, { data }, { ok: true });
}
