<template>
  <main class="bp-crud target-values-page" aria-labelledby="target-values-page-title">
    <header class="page-h">
      <PageTitle id="target-values-page-title">
        <span class="sub" v-if="currentPlan">方案：<em>{{ currentPlanLabel }}</em></span>
        <span class="sub" v-else>维护单条、批量导入与修正后的目标值</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="目标值操作">
        <el-button @click="backToTargets">返回目标管理</el-button>
        <el-button :disabled="!f.planId" @click="downloadTpl">下载模板</el-button>
        <el-tooltip :disabled="canManageValues" content="只有该目标方案的创建人可以导入或新增目标值" placement="top">
          <span>
            <el-button :loading="importingValues" :disabled="!canManageValues || importingValues" @click="triggerImportFile">导入目标值</el-button>
          </span>
        </el-tooltip>
        <el-tooltip :disabled="canManageValues" content="只有该目标方案的创建人可以新增目标值" placement="top">
          <span>
            <el-button type="primary" :disabled="!canManageValues" @click="openCreateRow">新增目标值</el-button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <input ref="importFileRef" class="file-input" type="file" accept=".xlsx,.xls" aria-label="导入目标值文件" @change="onImportFileSelected" />

    <section class="card-section filter-bar" aria-label="目标值筛选">
      <el-form class="filter-form" inline aria-label="目标值筛选">
        <el-form-item label="维度">
          <el-select v-model="f.subjectType" clearable placeholder="全部" aria-label="按对象维度筛选" @change="onFilterChange">
            <el-option value="EMP" label="员工" />
            <el-option value="ORG" label="机构" />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段名称">
          <el-select v-model="f.stageName" clearable filterable placeholder="全部" aria-label="按阶段名称筛选" @change="onFilterChange">
            <el-option v-for="s in stageNameOptions" :key="s" :value="s" :label="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象">
          <el-select v-model="f.subjectId" clearable filterable :loading="loadingSubjects" placeholder="全部" aria-label="按对象筛选" @change="loadValues">
            <el-option v-for="s in subjectOptions" :key="s.subjectId" :value="s.subjectId" :label="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标">
          <el-select v-model="f.metricCode" clearable filterable placeholder="全部" aria-label="按指标筛选" @change="loadValues">
            <el-option v-for="m in filteredMetricOptions" :key="m.metricCode" :value="m.metricCode" :label="m.metricName" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效状态">
          <el-select v-model="f.approvalStatus" clearable placeholder="全部" aria-label="按生效状态筛选" @change="onFilterChange">
            <el-option value="NORMAL" label="已生效" />
            <el-option value="ADJUSTING" label="修正中" />
            <el-option value="REJECTED" label="已驳回" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadValues">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-label="目标值列表" aria-describedby="target-values-table-state" :aria-busy="loadingValues ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="target-values-table-heading" class="section-title">目标值列表</h2>
          <p class="hint">目标修正必须填写原因；保存后按既有流程更新并刷新当前列表。</p>
        </div>
        <p id="target-values-table-state" class="table-state" role="status" aria-live="polite">{{ targetValuesState }}</p>
      </div>
      <div v-if="valuesError" class="table-error" role="alert">
        <span>{{ valuesError }}</span>
        <el-button link type="primary" @click="loadValues">重新加载</el-button>
      </div>
      <el-table :data="pagedRows" size="default" :empty-text="valuesError ? '加载失败，请重新加载' : '暂无目标值数据'" v-loading="loadingValues" aria-labelledby="target-values-table-heading" aria-describedby="target-values-table-state">
        <el-table-column label="维度" width="90">
          <template #default="{ row }">{{ subjectTypeLabel(row.subjectType) || '-' }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.subjectName || '-' }}</div>
            <div class="cell-meta">{{ row.subjectDisplayId || row.subjectId || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="指标" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.metricName || row.metricCode || '-' }}</div>
            <div class="cell-meta">{{ row.metricCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="阶段名称" min-width="120" prop="stageName" />
        <el-table-column label="起止日期" min-width="210">
          <template #default="{ row }">
            <span v-if="row.startDate || row.endDate">{{ row.startDate || '未设' }} 至 {{ row.endDate || '未设' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="目标值" width="140" align="right">
          <template #default="{ row }">{{ fmtNum(row.targetValue) }}</template>
        </el-table-column>
        <el-table-column label="基础值" width="140" align="right">
          <template #default="{ row }">{{ row.baseValue != null ? fmtNum(row.baseValue) : '-' }}</template>
        </el-table-column>
        <el-table-column label="生效状态" width="110">
          <template #default="{ row }">
            <el-tag :class="apprCls(row.approvalStatus)" effect="plain" size="small">{{ apprLabel(row.approvalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canTargetAdjust" link type="primary" size="small" @click="openAdjust(row)">调整</el-button>
            <el-button link type="primary" size="small" @click="openEditRow(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <nav class="pager" aria-label="目标值列表分页">
        <el-pagination v-model:current-page="pager.pageNo" v-model:page-size="pager.pageSize" :page-sizes="[10, 20, 50, 100]" :total="filteredRows.length" background layout="total, sizes, prev, pager, next, jumper" />
      </nav>
    </section>

    <el-dialog v-model="adjDlg.show" class="bp-crud-dialog" :title="adjTitle" width="540px" :close-on-click-modal="false" :close-on-press-escape="!adjDlg.saving" aria-label="目标值调整确认">
      <el-form ref="adjFormRef" :model="adjDlg.form" :rules="adjRules" label-position="top" size="default">
        <el-form-item label="当前目标"><el-input :model-value="fmtNum(adjDlg.row?.targetValue)" disabled /></el-form-item>
        <el-form-item label="调整后目标" prop="newValue" required><el-input-number v-model="adjDlg.form.newValue" class="field-control" :precision="2" :controls="false" /></el-form-item>
        <el-form-item label="当前基础值"><el-input :model-value="adjDlg.row?.baseValue != null ? fmtNum(adjDlg.row?.baseValue) : '-'" disabled /></el-form-item>
        <el-form-item label="调整后基础值"><el-input-number v-model="adjDlg.form.newBaseValue" class="field-control" :precision="2" :controls="false" placeholder="可空：不改基础值则留空" /></el-form-item>
        <el-form-item label="调整原因" prop="reason" required><el-input v-model="adjDlg.form.reason" type="textarea" :rows="3" placeholder="请说明调整原因，将记入审批记录" /></el-form-item>
      </el-form>
      <el-alert class="dialog-alert" type="warning" :closable="false" show-icon title="确认保存后将按当前业务流程更新目标值，并触发 KPI 历史回算。" />
      <template #footer>
        <el-button :disabled="adjDlg.saving" @click="closeAdjust">取消</el-button>
        <el-button type="primary" :loading="adjDlg.saving" :disabled="adjDlg.saving" @click="onSubmitAdjust">确认保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="valDlg.show" class="bp-crud-dialog" :title="valDlg.editing ? '修改目标值' : '新增目标值'" width="500px" :close-on-click-modal="false" :close-on-press-escape="!valDlg.saving" aria-label="目标值编辑">
      <el-form ref="valFormRef" :model="valDlg.form" :rules="valRules" label-width="100px" size="default">
        <el-form-item label="阶段名称" prop="stageName"><el-input v-model="valDlg.form.stageName" maxlength="100" clearable placeholder="如 一阶段" /></el-form-item>
        <el-form-item label="起始日期" prop="startDate"><el-date-picker v-model="valDlg.form.startDate" class="field-control" type="date" value-format="YYYY-MM-DD" placeholder="请选择起始日期" /></el-form-item>
        <el-form-item label="截止日期" prop="endDate"><el-date-picker v-model="valDlg.form.endDate" class="field-control" type="date" value-format="YYYY-MM-DD" placeholder="请选择截止日期" /></el-form-item>
        <el-form-item label="维度" prop="subjectType">
          <el-select v-model="valDlg.form.subjectType" class="field-control" :disabled="valDlg.editing" @change="onDimChange"><el-option value="EMP" label="员工" /><el-option value="ORG" label="机构" /></el-select>
        </el-form-item>
        <el-form-item label="对象" prop="subjectId">
          <el-autocomplete v-model="valDlg.form.subjectDisplay" class="field-control" :disabled="valDlg.editing" :fetch-suggestions="querySubjectSuggestions" :placeholder="valDlg.form.subjectType === 'ORG' ? '输入部门编号或机构名搜索' : '输入用户名或中文名搜索'" clearable highlight-first-item @select="onSubjectSelect" @clear="onSubjectClear">
            <template #default="{ item }"><div class="suggestion-row"><span class="mono">{{ item.display || item.value }}</span><span class="cell-meta">{{ item.label }}</span></div></template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item label="指标" prop="metricCode"><el-select v-model="valDlg.form.metricCode" class="field-control" filterable :disabled="valDlg.editing" :placeholder="metricsForDim.length ? '请选择指标' : '所选 KPI 方案暂无可选指标'"><el-option v-for="m in metricsForDim" :key="m.metricCode" :value="m.metricCode" :label="`${m.metricCode} · ${m.metricName}`" /></el-select></el-form-item>
        <el-form-item label="目标值" prop="targetValue"><el-input-number v-model="valDlg.form.targetValue" class="field-control" :precision="2" :controls="false" /></el-form-item>
        <el-form-item label="基础值"><el-input-number v-model="valDlg.form.baseValue" class="field-control" :precision="2" :controls="false" placeholder="可空" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="valDlg.saving" @click="closeValueDialog">取消</el-button>
        <el-button type="primary" :loading="valDlg.saving" :disabled="valDlg.saving" @click="onSaveValue">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus';
import {
  listTargets, listTargetValues, listTargetValueSubjects, listTargetValueStageNames, upsertTargetValue, batchUpsertTargetValues,
  submitTargetAdjust, listMetrics, deleteTargetValue,
  listKpiRules, getKpiSchemeDetail, searchPerfEmployees
} from '@/api/perf';
import { getOrgTree } from '@/api/orgs';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const route    = useRoute();
const router   = useRouter();

// 仅资财部经办 (BACK_FINANCE) 可发起目标修正；资财部负责人 (FINANCE_LEADER) 是审批人不发起；
// SYS_ADMIN 兜底放行
const canTargetAdjust = computed(() => {
  const roles = userStore.user?.roles || [];
  const codes = roles.map(r => (typeof r === 'string' ? r : (r.roleId || r.roleCode)));
  return codes.some(c => c === '238' || c === 'BACK_FINANCE'   // role_id：资财部经办人=238
                       || c === '1' || c === 'SYS_ADMIN');       // role_id：系统管理员=1
});

// 返回上一级（目标管理主页）；用 router 不直接 location.href，保持 SPA 路由栈
function backToTargets() { router.push({ name: 'PerfTargets' }); }

// KPI 方案 id → "code · name" 映射（用于 H1 副标题"关联 KPI"显示，仅显名称不显 ID）
const kpiMap = ref(new Map());
async function loadKpiMap() {
  try {
    const r = await listKpiRules({ pageSize: 100 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    const m = new Map();
    for (const s of arr) {
      const code = s.schemeCode || s.code || '';
      const name = s.schemeName || s.name || '';
      m.set(s.id, `${code} · ${name}`.replace(/^ · /, '').replace(/ · $/, ''));
    }
    kpiMap.value = m;
  } catch { /* 副标题降级显示 '-' */ }
}

// === 员工 / 机构 / 指标 三套 id→name 映射缓存 ===
// 后端 PERF_TARGET_VALUE 表只有 subject_id（员工号或机构编码），不返机构名/员工名/指标名
// → 列表渲染时用这 3 个 map 把 ID 翻译成可读名称
const orgMap    = ref(new Map()); // orgCode(内部机构编码) → orgName
const orgDeptMap = ref(new Map()); // orgCode(内部机构编码) → deptNo(业务机构部门编号)
const orgDeptToCode = ref(new Map()); // deptNo(EXT_ORG_INFO.DEPT_NO 业务机构部门编号) → orgCode(内部机构编码)
const metricMap = ref(new Map()); // metricCode → metricName

// 2026-07-21：原 loadEmpMap() 走管理员接口 /api/admin/users 拉全量用户建「工号→姓名」映射，
// 已删除。该接口资源 A_USER_LIST 仅授予角色 1/3/4/131/169，资财部经办人(238)等进本页必得 403，
// http.js 响应拦截器随即弹「没有权限」（页面内 catch 只能防崩，拦不住这个提示）。
// 替代方案：
//   - 列表回显员工姓名 → 后端 TargetValueService#fillEmpSubjectNames 已填 subjectName；
//   - 新增/编辑时的输入建议 → 取方案内已有对象 subjectOptions（/target-values/subjects，本角色有权限）；
//   - 工号存在性 → 2026-06-15 起本就由后端校验，不依赖前端缓存。
// 不要为了补全建议把 /api/admin/users 加回来。
async function loadOrgMap() {
  try {
    const tree = await getOrgTree();
    const m = new Map();
    const dm = new Map();
    const d2c = new Map();
    // getOrgTree 节点形态 { code:内部机构编码, name:机构名, deptNo:业务机构部门编号(EXT_ORG_INFO.DEPT_NO) }
    const walk = (n) => {
      if (!n) return;
      if (n.code) {
        m.set(n.code, n.name);
        if (n.deptNo != null && n.deptNo !== '') {
          dm.set(n.code, n.deptNo);
          d2c.set(String(n.deptNo).trim(), n.code); // 反查：业务机构部门编号 → 内部机构编码
        }
      }
      (n.children || []).forEach(walk);
    };
    (Array.isArray(tree) ? tree : []).forEach(walk);
    orgMap.value = m;
    orgDeptMap.value = dm;
    orgDeptToCode.value = d2c;
  } catch {}
}

// el-autocomplete 数据源：
//   EMP → 远程搜 /perf/employees/search（PT_USER 全表，返回工号），覆盖方案内尚未出现过的新员工；
//   ORG → 本地 orgMap（机构树已一次性加载，无需远程）。
// EMP：value=工号（PT_USER.username，即 subjectId 应写入的值），label=姓名；
// ORG：value=机构号（EXT_ORG_INFO.org_code），label=机构名。
async function querySubjectSuggestions(query, cb) {
  const dim = valDlg.form.subjectType;
  const q = (query || '').toLowerCase().trim();
  const out = [];
  if (dim === 'EMP') {
    // 远程搜索而非前端缓存全量：前者曾走管理员接口 /api/admin/users 导致业务角色 403，
    // 后者（方案内已有对象）搜不到首次录入的新员工。两个问题一并由本端点解决。
    try {
      const list = await searchPerfEmployees(query || '', 50);
      for (const e of (Array.isArray(list) ? list : [])) {
        if (!e.username) continue;
        out.push({ value: e.username, label: e.displayName || e.username, display: e.orgName || '' });
      }
    } catch { /* 搜索失败不阻塞输入：用户仍可直接键入工号，存在性由后端校验 */ }
    cb(out);
    return;
  }
  if (dim === 'ORG') {
    // 下拉内容展示「部门编号 + 机构名称」；入库 value 仍为机构编号（内部 org_code）
    for (const [code, name] of orgMap.value) {
      const nm = name || '';
      const deptNo = String(orgDeptMap.value.get(code) ?? '');
      if (!q || code.toLowerCase().includes(q) || nm.toLowerCase().includes(q)
          || deptNo.toLowerCase().includes(q)) {
        out.push({ value: code, label: nm, display: deptNo || code });
        if (out.length >= 50) break;
      }
    }
  }
  cb(out);
}
function rebuildMetricMap() {
  const m = new Map();
  for (const x of metricOptions.value) m.set(x.metricCode, x.metricName);
  metricMap.value = m;
}

// 后端 UpsertTargetValueReqDTO @Pattern(^(EMP|ORG)$) 仅允许 EMP / ORG
const subjectTypeLabel = (t) => ({ EMP: '员工', ORG: '机构' }[t] || '');
const fmtNum = (v) => (v == null || v === '') ? '-' : Number(v).toLocaleString();

const apprCls = (s) => ({
  NORMAL:    'tag-success',
  APPROVED:  'tag-success',
  ADJUSTING: 'tag-warning',
  IN_APPROVAL: 'tag-warning',
  PENDING:   'tag-warning',
  DRAFT:     'tag-info',
  REJECTED:  'tag-danger'
}[s] || 'tag-success');
// 没走审批流的目标值 status 为空/NORMAL，业务上即"已生效"；fallback 同样显示"已生效"，
// 而非误导性的占位符 "-"
const apprLabel = (s) => ({
  NORMAL:    '已生效',
  APPROVED:  '已审批',
  ADJUSTING: '修正中',
  IN_APPROVAL: '修正中',
  PENDING:   '待审批',
  DRAFT:     '草稿',
  REJECTED:  '已驳回'
}[s] || '已生效');

// === 方案 ===
const plans = ref([]);
async function loadPlans() {
  // 只用后端真实方案；后端无方案时不再注入 mock TGT-2026Q2（曾导致 upsert 报"方案不存在"）
  try {
    const r = await listTargets({ pageSize: 100 });
    // listTargets 走 unwrapPage：分页响应返回 { records, total } 形态；
    // 不兼容会导致 plans.value=[] → currentPlan 永远 null → 新增按钮永远 disabled
    plans.value = Array.isArray(r) ? r : (r?.records || []);
    if (!f.planId && plans.value.length) {
      f.planId = plans.value[0].id || plans.value[0].planCode;
    }
  } catch { plans.value = []; }
}

// === 指标下拉 ===
const metricOptions = ref([]);
async function loadMetricOptions() {
  try {
    const r = await listMetrics({ pageSize: 100 });
    if (Array.isArray(r)) {
      metricOptions.value = r;
      rebuildMetricMap();
    }
  } catch {}
}

// 筛选下拉：从当前方案已有的目标值中提取唯一指标选项
const filteredMetricOptions = computed(() => {
  const seen = new Map();
  for (const v of values.value) {
    if (v.metricCode && !seen.has(v.metricCode)) {
      seen.set(v.metricCode, { metricCode: v.metricCode, metricName: v.metricName || v.metricCode });
    }
  }
  return [...seen.values()];
});

// === 目标值 ===
const f = reactive({ planId: '', subjectType: '', subjectId: '', metricCode: '', stageName: '', approvalStatus: '' });
const values = ref([]);
const loadingValues = ref(false);
const valuesError = ref('');

// === 对象下拉（方案内目标值去重；EMP→工号+姓名，ORG→部门编号+机构名称）===
const subjectOptions = ref([]);
const loadingSubjects = ref(false);
async function loadSubjects() {
  if (!f.planId) { subjectOptions.value = []; return; }
  loadingSubjects.value = true;
  try {
    const r = await listTargetValueSubjects(f.planId);
    subjectOptions.value = Array.isArray(r) ? r : (r?.records || []);
  } catch { subjectOptions.value = []; } finally { loadingSubjects.value = false; }
}
// 阶段名称下拉：方案内所有目标值阶段名称去重（来自后端 distinct 端点）
const stageNameOptions = ref([]);
async function loadStageNames() {
  if (!f.planId) { stageNameOptions.value = []; return; }
  try {
    const r = await listTargetValueStageNames(f.planId);
    stageNameOptions.value = Array.isArray(r) ? r : (r?.records || []);
  } catch { stageNameOptions.value = []; }
}
// 方案切换：重载对象/阶段名称下拉并清空已选过滤（避免跨方案残留）
watch(() => f.planId, () => {
  f.subjectId = ''; f.stageName = '';
  loadSubjects(); loadStageNames();
}, { immediate: true });

// === 当前传入方案上下文（H1 副标题用，仅显示名称，不显示方案/KPI 的 ID） ===
const samePlan = (plan, planId) => String(plan?.id ?? plan?.planCode ?? '') === String(planId ?? '');
const currentPlan = computed(() => plans.value.find(p => samePlan(p, f.planId)) || null);
const canManageValues = computed(() => {
  const createdBy = currentPlan.value?.createdBy;
  const empId = userStore.user?.empId;
  return createdBy != null && empId != null && String(createdBy) === String(empId);
});
const currentPlanLabel = computed(() => {
  const p = currentPlan.value;
  if (!p) return '-';
  return `${p.planCode || ''} · ${p.planName || ''}`.replace(/^ · /, '').replace(/ · $/, '') || '-';
});
const currentKpiLabel  = computed(() => {
  const p = currentPlan.value;
  if (!p || !p.kpiSchemeId) return '-';
  return kpiMap.value.get(p.kpiSchemeId) || '-';
});
const currentDimLabel  = computed(() => subjectTypeLabel(currentPlan.value?.targetDim) || '-');

// 当前目标方案关联 KPI 方案里定义的指标 code 集合（新增目标值的指标下拉按此过滤：
// 只显示目标方案所选 KPI 中定义的指标项）。
const kpiMetricCodes = ref(new Set());
async function loadKpiMetricCodes() {
  const id = currentPlan.value?.kpiSchemeId;
  if (!id) { kpiMetricCodes.value = new Set(); return; }
  try {
    const d = await getKpiSchemeDetail(id);
    kpiMetricCodes.value = new Set((d?.items || []).map(it => it.metricCode).filter(Boolean));
  } catch { kpiMetricCodes.value = new Set(); }
}
watch(() => currentPlan.value?.kpiSchemeId, () => { loadKpiMetricCodes(); }, { immediate: true });

async function loadValues() {
  if (!f.planId) {
    values.value = [];
    valuesError.value = '';
    pager.pageNo = 1;
    return;
  }
  pager.pageNo = 1;
  loadingValues.value = true;
  valuesError.value = '';
  try {
    // 目标修正取消审批后「提交即生效」，直接写 PERF_TARGET_VALUE，
    // 列表不再 join PERF_TARGET_ADJUST_APPLY，直接展示主表的目标值/基础值。
    const r = await listTargetValues({
      planId: f.planId, planCode: f.planId,
      subjectType: f.subjectType || undefined,
      subjectId: f.subjectId || undefined,
      metricCode: f.metricCode || undefined,
      pageSize: 100
    });
    // listTargetValues 走 unwrapPage：分页响应 r = { records, total }，先拍平再判断。
    const rows = Array.isArray(r) ? r : (r?.records || []);
    values.value = rows.map(x => {
      let subjectName = x.subjectName || '';
      let orgName     = x.orgName     || '';
      let orgCode     = x.orgCode     || '';
      if (x.subjectType === 'EMP') {
        // subjectName 由后端 fillEmpSubjectNames 按工号解析；查不到用户时后端留空，此处兜底显示工号
        if (!subjectName) subjectName = x.subjectId;
      } else if (x.subjectType === 'ORG') {
        orgCode ||= x.subjectId;
        orgName ||= orgMap.value.get(x.subjectId) || x.subjectId;
        subjectName ||= orgName;
      }
      const metricName = x.metricName || metricMap.value.get(x.metricCode) || x.metricCode;
      // 对象列展示编号：EMP→员工号(subject_id)；ORG→业务机构部门编号(dept_no)，无映射回退内部编码
      const subjectDisplayId = x.subjectType === 'ORG'
        ? (orgDeptMap.value.get(x.subjectId) || x.subjectId)
        : x.subjectId;
      return { ...x, subjectName, orgName, orgCode, metricName, subjectDisplayId };
    });
  } catch {
    values.value = [];
    valuesError.value = '目标值加载失败，请检查筛选条件后重新加载。';
  } finally { loadingValues.value = false; }
}

const filteredRows = computed(() => {
  let arr = values.value;
  if (f.approvalStatus) arr = arr.filter(r => r.approvalStatus === f.approvalStatus);
  if (f.subjectType)    arr = arr.filter(r => r.subjectType === f.subjectType);
  if (f.subjectId)      arr = arr.filter(r => r.subjectId === f.subjectId);
  if (f.metricCode)     arr = arr.filter(r => r.metricCode === f.metricCode);
  if (f.stageName)      arr = arr.filter(r => r.stageName === f.stageName);
  return arr;
});

// === 分页（前端 client-side：filteredRows → slice 给表格） ===
const pager = reactive({ pageNo: 1, pageSize: 10 });
const pagedRows = computed(() => {
  const start = (pager.pageNo - 1) * pager.pageSize;
  return filteredRows.value.slice(start, start + pager.pageSize);
});
const targetValuesState = computed(() => {
  if (loadingValues.value) return '目标值列表加载中';
  if (valuesError.value) return '目标值列表加载失败';
  return filteredRows.value.length ? `共 ${filteredRows.value.length} 条目标值` : '暂无目标值数据';
});

function onFilterChange() {
  pager.pageNo = 1;
}
function resetFilters() {
  f.subjectType = '';
  f.subjectId = '';
  f.metricCode = '';
  f.stageName = '';
  f.approvalStatus = '';
  loadValues();
}

// === 修正弹框 ===
const adjFormRef = ref(null);
const adjDlg = reactive({
  show: false, saving: false, row: null,
  form: { newValue: 0, newBaseValue: null, cycleKey: '', ownerOrgId: '', reason: '' }
});
const adjustSubmitting = ref(false);
const adjTitle = computed(() => {
  if (!adjDlg.row) return '目标修正';
  return `目标修正 · ${subjectTypeLabel(adjDlg.row.subjectType)}${adjDlg.row.subjectName} · ${adjDlg.row.metricName || adjDlg.row.metricCode}`;
});
// 仅必填校验（按要求不做 pattern 正则）
const adjRules = {
  newValue: [{ required: true, message: '请填写修正后的目标值' }],
  reason:   [{ required: true, message: '请填写修正原因（将记入审批日志）', trigger: 'blur' }]
};
// 删除目标值：二次确认 → 物理删除 → 刷新列表
async function onDelete(row) {
  if (!row?.id) return ElMessage.warning('该行缺少 id，无法删除');
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.subjectName || row.subjectId || ''} · ${row.metricCode || ''}」的目标值吗？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    );
  } catch { return; } // 用户取消
  try {
    await deleteTargetValue(row.id);
    ElMessage.success('已删除');
    loadValues();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '删除失败');
  }
}
function openAdjust(row) {
  if (!canTargetAdjust.value) {
    return ElMessage.warning('当前角色无权发起目标调整');
  }
  // 防御：未选目标方案时提交会报"planId 必填"
  if (!f.planId) {
    return ElMessage.warning('请先在顶部「方案」筛选中选择一个目标方案，再发起修正');
  }
  adjDlg.row = row;
  adjDlg.form.newValue = Number(row.currentTarget ?? row.targetValue ?? 0);
  // 修正后基础值预填当前基础值（null 时留空，提交时不改 base_value）
  adjDlg.form.newBaseValue = row.baseValue != null ? Number(row.baseValue) : null;
  adjDlg.form.reason   = row.adjustReason || '';
  adjDlg.show = true;
}
async function onSubmitAdjust() {
  if (adjDlg.saving || adjustSubmitting.value) return;
  if (!canTargetAdjust.value) return ElMessage.warning('当前角色无权发起目标调整');
  // 校验本身异步，单独的同步锁覆盖「尚未进入 saving 状态」的窗口，避免快速双击绕过按钮禁用。
  adjustSubmitting.value = true;
  try {
    try { await adjFormRef.value.validate(); } catch { return; }
    // 当前目标值与修正后目标值相同时不允许提交
    const curVal = adjDlg.row?.approvalStatus === 'APPROVED'
      ? Number(adjDlg.row?.currentTarget ?? adjDlg.row?.targetValue ?? 0)
      : Number(adjDlg.row?.targetValue ?? adjDlg.row?.originalTarget ?? 0);
    const curBase = adjDlg.row?.baseValue != null ? Number(adjDlg.row.baseValue) : null;
    const newBase = adjDlg.form.newBaseValue != null ? Number(adjDlg.form.newBaseValue) : null;
    const targetChanged = Number(adjDlg.form.newValue) !== curVal;
    const baseChanged = newBase !== curBase;
    if (!targetChanged && !baseChanged) {
      return ElMessage.warning('修正后目标值/基础值均与当前值相同，无需修正');
    }
    // 后端必填的 cycleKey / ownerOrgId 在这里自动兜底（UI 不再暴露）
    const plan = plans.value.find(p => samePlan(p, f.planId));
    const cycleKey = adjDlg.row.cycleKey || inferCycleKey();
    const ownerOrgId = adjDlg.row.ownerOrgId
      || adjDlg.row.orgCode
      || plan?.ownerOrgId
      || plan?.ownerOrgCode
      || userStore.user?.mainOrgCode
      || userStore.user?.orgCode
      || '';
    if (!ownerOrgId) {
      return ElMessage.warning('当前用户没有归属机构，无法发起目标修正。请联系管理员设置主机构。');
    }
    adjDlg.saving = true;
    try {
      await submitTargetAdjust({
        planId: f.planId,
        subjectType: adjDlg.row.subjectType,
        subjectId: adjDlg.row.subjectId || adjDlg.row.subjectName,
        cycleKey,
        ownerOrgId,
        reason: adjDlg.form.reason,
        metricCode: adjDlg.row.metricCode,
        oldValue: Number(adjDlg.row.currentTarget ?? adjDlg.row.targetValue ?? 0),
        newValue: Number(adjDlg.form.newValue),
        oldBaseValue: curBase,
        newBaseValue: newBase
      });
      ElMessage.success('已保存，目标值已生效');
      adjDlg.show = false;
      // 直接生效后重新拉取主表，刷新「当前目标 / 基础值」。
      loadValues();
    } catch (err) {
      ElMessage.error(err?.bizMsg || err?.message || '提交失败');
    } finally {
      adjDlg.saving = false;
    }
  } finally {
    adjustSubmitting.value = false;
  }
}
function closeAdjust() {
  if (!adjDlg.saving) adjDlg.show = false;
}
// 从筛选方案 / 当前日期推导 cycleKey（YEARLY: yyyy / QUARTERLY: yyyyQn / MONTHLY: yyyyMM）
function inferCycleKey() {
  const plan = plans.value.find(p => samePlan(p, f.planId));
  const cycle = (plan?.cycleType || plan?.cycle || 'QUARTERLY').toUpperCase();
  const d = new Date();
  const y = d.getFullYear();
  if (cycle === 'YEARLY')    return `${y}`;
  if (cycle === 'MONTHLY')   return `${y}${String(d.getMonth() + 1).padStart(2, '0')}`;
  // 默认 QUARTERLY
  return `${y}Q${Math.floor(d.getMonth() / 3) + 1}`;
}

// === 新增目标值 ===
// 后端 UpsertTargetValueReqDTO 字段（7 个）：planId / subjectType / subjectId /
//   cycleKey / metricCode / targetValue / baseValue?
// 现在 UI 仅暴露 4 项（subjectType / subjectId / metricCode / targetValue + 可选 baseValue），
// planId 用页面当前 f.planId、cycleKey 由 inferCycleKey() 在 onSaveValue 自动派生
const valFormRef = ref(null);
const valDlg = reactive({
  show: false, saving: false,
  // editing=true 时复用本弹框做「修改」：反显行数据、锁定身份字段(维度/对象/指标/周期键)、保存走同 UK upsert 更新
  editing: false, editCycleKey: '',
  // subjectDisplay 仅用于输入框展示（ORG=部门编号+机构名称），subjectId 才是入库值（机构编号/工号）
  form: { stageName: '', startDate: '', endDate: '', subjectType: 'EMP', subjectId: '', subjectDisplay: '', metricCode: '', targetValue: 0, baseValue: null }
});
// subjectType 不再在 UI 暴露：openCreateRow 从 currentPlan.targetDim 自动赋值
const valRules = {
  stageName:   [{ required: true, message: '请填写阶段名称' }],
  startDate:   [{ required: true, message: '请选择起始日期' }],
  endDate:     [{ required: true, message: '请选择截止日期' }],
  subjectType: [{ required: true, message: '请选择维度' }],
  subjectId:   [{ required: true, message: '请填写对象编号（员工号 / 机构编码）' }],
  metricCode:  [{ required: true, message: '请选择指标' }],
  targetValue: [{ required: true, message: '请填写目标值' }]
};

// 新增弹框指标：仅 ACTIVE + 维度匹配 + 只在「目标方案所选 KPI 方案定义的指标」范围内。
// 方案关联了 KPI(kpiSchemeId) 时按该 KPI 的指标项过滤；无关联 KPI 时退回仅维度过滤。
const metricsForDim = computed(() => {
  const kpiId = currentPlan.value?.kpiSchemeId;
  return metricOptions.value.filter(m =>
    m.status === 'ACTIVE'
    && (!m.baseDim || m.baseDim === valDlg.form.subjectType)
    && (!kpiId || kpiMetricCodes.value.has(m.metricCode))
  );
});

async function openCreateRow() {
  if (!f.planId) {
    return ElMessage.warning('请先选择目标方案再新增目标值');
  }
  if (!canManageValues.value) {
    return ElMessage.warning('只有该目标方案的创建人可以新增目标值');
  }
  // 维度默认选中「员工」(EMP)，用户可在对话框中切换
  valDlg.editing = false;
  valDlg.editCycleKey = '';
  Object.assign(valDlg.form, {
    stageName: '', startDate: '', endDate: '',
    subjectType: 'EMP', subjectId: '', subjectDisplay: '',
    metricCode: '', targetValue: 0, baseValue: null
  });
  // 确保指标下拉已按当前方案的 KPI 指标项过滤就绪
  await loadKpiMetricCodes();
  valDlg.show = true;
}

/**
 * 修改：复用「新增目标值」弹框做编辑。反显行数据 + 锁定身份字段（维度/对象/指标），
 * 保存时按相同 UK (plan+维度+对象+周期键+指标) 走 upsert → ON DUPLICATE KEY UPDATE 更新原行。
 */
async function openEditRow(row) {
  if (!f.planId) {
    return ElMessage.warning('请先在顶部「方案」筛选中选择一个目标方案，再修改目标值');
  }
  await loadKpiMetricCodes();
  // 对象输入框展示：ORG=「部门编号 机构名」，EMP=工号
  const subjectDisplay = row.subjectType === 'ORG'
    ? `${row.subjectDisplayId || row.subjectId} ${row.subjectName || ''}`.trim()
    : row.subjectId;
  Object.assign(valDlg.form, {
    stageName: row.stageName || '',
    startDate: row.startDate || '',
    endDate: row.endDate || '',
    subjectType: row.subjectType || 'EMP',
    subjectId: row.subjectId || '',
    subjectDisplay,
    metricCode: row.metricCode || '',
    targetValue: Number(row.targetValue) || 0,
    baseValue: row.baseValue != null ? Number(row.baseValue) : null
  });
  valDlg.editing = true;
  // 修改走原行周期键（保证命中同一 UK），不再用 inferCycleKey 派生
  valDlg.editCycleKey = row.cycleKey || '';
  valDlg.show = true;
}
// 对象输入框：展示文本(subjectDisplay) 与 入库值(subjectId) 解耦
let subjectSelecting = false;
/** 从下拉选中：入库 subjectId=机构编号/工号；ORG 输入框展示「部门编号 机构名称」，EMP 展示工号 */
function onSubjectSelect(item) {
  subjectSelecting = true;
  valDlg.form.subjectId = item.value;
  valDlg.form.subjectDisplay = valDlg.form.subjectType === 'ORG'
    ? `${item.display || item.value} ${item.label}`.trim()
    : item.value;
  nextTick(() => { subjectSelecting = false; });
}
function onSubjectClear() {
  valDlg.form.subjectId = '';
  valDlg.form.subjectDisplay = '';
}
// 维度切换：清空已选「对象」与「指标」，使两者下拉按新维度重新取值（对象走 querySubjectSuggestions、指标走 metricsForDim）
function onDimChange() {
  valDlg.form.subjectId = '';
  valDlg.form.subjectDisplay = '';
  valDlg.form.metricCode = '';
}
// 手动键入（非下拉选择）：EMP 直接作为工号；ORG 必须从下拉选择，键入仅作筛选文本 → 清空已选编号
watch(() => valDlg.form.subjectDisplay, (val) => {
  if (subjectSelecting) return;
  valDlg.form.subjectId = valDlg.form.subjectType === 'EMP' ? (val || '').trim() : '';
});

async function onSaveValue() {
  if (valDlg.saving) return;
  try { await valFormRef.value.validate(); } catch { return; }
  if (!f.planId) {
    return ElMessage.warning('当前没有选中目标方案，无法保存目标值');
  }
  const dim = valDlg.form.subjectType;
  const sid = valDlg.form.subjectId;
  // 2026-06-15：员工工号/机构部门编号的存在性校验已下沉后端（直连 PT_USER / EXT_ORG_INFO 校验）。
  // 前端不再用 empMap/orgMap 缓存判存在性（缓存受 pageSize 上限截断会误判），
  // 不存在时后端抛 VALIDATION_FAILED，下方 catch 用 err.bizMsg 展示。
  // 同一方案下 维度+对象+指标+阶段名称 不能重复（对齐唯一索引 uk_plan_subject_metric_stage）
  // 仅新增时校验；修改模式本就是更新原行，跳过
  if (!valDlg.editing) {
    const stageName = (valDlg.form.stageName || '').trim();
    const dup = values.value.find(v =>
      v.subjectType === dim &&
      v.subjectId === sid &&
      v.metricCode === valDlg.form.metricCode &&
      (v.stageName || '').trim() === stageName);
    if (dup) {
      const stagePart = stageName ? `+阶段名称「${stageName}」` : '（未填阶段名称）';
      return ElMessage.error(
        `对象「${sid}」+指标「${valDlg.form.metricCode}」${stagePart}在当前方案中已存在`);
    }
  }
  valDlg.saving = true;
  try {
    const payload = {
      planId:      f.planId,
      subjectType: valDlg.form.subjectType,
      subjectId:   valDlg.form.subjectId,
      // 修改用原行周期键命中同一 UK → 更新；新增用 inferCycleKey 派生
      cycleKey:    valDlg.editing ? valDlg.editCycleKey : inferCycleKey(),
      metricCode:  valDlg.form.metricCode,
      targetValue: valDlg.form.targetValue
    };
    if (valDlg.form.baseValue != null && valDlg.form.baseValue !== '') {
      payload.baseValue = valDlg.form.baseValue;
    }
    // 阶段名称 / 起止日期：可空，仅在填写时提交
    if (valDlg.form.stageName) payload.stageName = valDlg.form.stageName;
    if (valDlg.form.startDate) payload.startDate = valDlg.form.startDate;
    if (valDlg.form.endDate)   payload.endDate = valDlg.form.endDate;
    await upsertTargetValue(payload);
    ElMessage.success('已保存');
    valDlg.show = false;
    loadValues();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '保存失败');
  } finally { valDlg.saving = false; }
}
function closeValueDialog() {
  if (!valDlg.saving) valDlg.show = false;
}

// === 导入目标值 / 下载模板 ===
const importFileRef = ref(null);
const importingValues = ref(false);
function triggerImportFile() {
  if (!f.planId) return ElMessage.warning('请先选择目标方案');
  if (!canManageValues.value) return ElMessage.warning('只有该目标方案的创建人可以导入目标值');
  if (importingValues.value) return;
  importFileRef.value?.click();
}
async function onImportFileSelected(e) {
  if (importingValues.value) return;
  const file = e.target?.files?.[0];
  if (!file) return;
  if (!canManageValues.value) {
    ElMessage.warning('只有该目标方案的创建人可以导入目标值');
    if (importFileRef.value) importFileRef.value.value = '';
    return;
  }
  const plan = currentPlan.value;
  if (!plan) { ElMessage.warning('当前方案信息缺失'); return; }
  importingValues.value = true;
  // 全屏加载遮罩：解析 Excel、校验及批量入库期间保留明确的处理中状态。
  const loading = ElLoading.service({ lock: true, text: '正在导入目标值，请稍候…' });
  try {
    const XLSX = await import('xlsx');
    const ab = await file.arrayBuffer();
    const wb = XLSX.read(ab, { type: 'array' });
    const ws = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(ws, { header: 1 });
    if (rows.length < 2) { ElMessage.warning('文件无数据行'); return; }
    // 跳过表头，解析数据行：序号 / 工号·机构对象 / 指标名称 / 目标值 / 基础值
    const dim = plan.targetDim || 'EMP';
    const cycleKey = inferCycleKey();
    const items = [];
    const errors = [];
    for (let i = 1; i < rows.length; i++) {
      const r = rows[i];
      if (!r || !r[1]) continue;
      let subjectId = String(r[1]).trim();
      const metricNameRaw = String(r[2] || '').trim();
      const targetValue = Number(r[3]);
      const baseValue = r[4] != null && r[4] !== '' ? Number(r[4]) : null;
      if (!subjectId || !metricNameRaw || isNaN(targetValue)) {
        errors.push(`第${i + 1}行: 数据不完整`);
        continue;
      }
      // 2026-06-15：员工工号/机构部门编号的存在性校验下沉后端（直连 PT_USER / EXT_ORG_INFO）。
      // 前端不再用 empMap/orgDeptToCode 缓存判存在性与做 deptNo→编码转换（缓存受 pageSize 截断会误判）；
      // EMP 直接发工号、ORG 直接发部门编号(DEPT_NO)，由后端 upsertBatch 校验并把 ORG 归一为内部机构编码入库。
      // 按指标名称反查 metricCode（仅匹配 ACTIVE 状态 + 维度匹配）
      const m = metricOptions.value.find(x => x.metricName === metricNameRaw && x.status === 'ACTIVE' && (!x.baseDim || x.baseDim === dim));
      if (!m) { errors.push(`第${i + 1}行: 指标「${metricNameRaw}」未找到`); continue; }
      if (!/^[A-Z][A-Z0-9_]*$/.test(m.metricCode)) {
        errors.push(`第${i + 1}行: 指标编码「${m.metricCode}」格式不合规，跳过`); continue;
      }
      const item = {
        planId: f.planId, subjectType: dim, subjectId,
        cycleKey, metricCode: m.metricCode, targetValue
      };
      if (baseValue != null) item.baseValue = baseValue;
      items.push(item);
    }
    // 文件内去重校验：同一方案下 对象+指标 只能有一条
    const seen = new Map();
    for (let j = 0; j < items.length; j++) {
      const it = items[j];
      const key = `${it.subjectId}:${it.metricCode}`;
      if (seen.has(key)) {
        errors.push(`对象「${it.subjectId}」+指标「${it.metricCode}」在文件中重复（行${seen.get(key)} 与 行${j + 2}）`);
      } else {
        seen.set(key, j + 2);
      }
    }
    if (errors.length) {
      ElMessage.error(`校验不通过，${errors.length} 行有错误，全部取消入库：\n${errors.slice(0, 5).join('；')}`);
      return;
    }
    if (!items.length) { ElMessage.warning('无有效数据可导入'); return; }
    await batchUpsertTargetValues(items);
    ElMessage.success(`成功导入 ${items.length} 条目标值`);
    loadValues();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '导入失败');
  } finally {
    loading.close();
    importingValues.value = false;
    if (importFileRef.value) importFileRef.value.value = '';
  }
}
function downloadTpl() {
  window.open('/templates/目标值上传模板.xlsx', '_blank');
}

onMounted(async () => {
  // 从 Targets 主页跳过来时带 ?planId=xxx；优先用 URL 上的 planId 预选
  // loadPlans 内 if (!f.planId && plans.value.length) 会让该值生效（不被首条覆盖）
  if (route.query.planId) {
    f.planId = String(route.query.planId);
  }
  // 5 个基础数据并发：方案 / 指标库 / 员工映射 / 机构映射 / KPI 方案映射（用于副标题翻译）
  await Promise.all([loadPlans(), loadMetricOptions(), loadOrgMap(), loadKpiMap()]);
  loadValues();
});
</script>

<style lang="scss" scoped>
.target-values-page {
  min-width: 0;
}

.page-h .sub em {
  color: var(--color-text-strong);
  font-style: normal;
  font-weight: 500;
}

.file-input {
  block-size: 1px;
  clip: rect(0 0 0 0);
  inline-size: 1px;
  overflow: hidden;
  position: absolute;
  white-space: nowrap;
}

.cell-meta {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.field-control {
  width: 100%;
}

.suggestion-row {
  align-items: center;
  display: flex;
  gap: var(--space-3);
  justify-content: space-between;
}

.table-error {
  align-items: center;
  background: var(--color-danger-bg);
  border: 1px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  display: flex;
  gap: var(--space-3);
  justify-content: space-between;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}

.dialog-alert {
  margin-top: var(--space-3);
}
</style>
