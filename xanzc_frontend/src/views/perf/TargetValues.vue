<template>
  <div>
    <div class="page-h">
      <h1>目标值管理
        <span class="sub" v-if="currentPlan">
          方案：<em>{{ currentPlanLabel }}</em>
          <span class="dot">·</span>
          关联 KPI：<em>{{ currentKpiLabel }}</em>
          <span class="dot">·</span>
          维度：<em>{{ currentDimLabel }}</em>
        </span>
        <span class="sub" v-else>单条 / 批量 / 修正 <em>(修正会触发回算)</em></span>
      </h1>
      <div class="actions">
        <el-button link type="primary" @click="backToTargets">← 返回目标管理</el-button>
        <el-button @click="triggerImportFile">📥 导入目标值</el-button>
        <el-button @click="downloadTpl">📄 下载模板</el-button>
        <input ref="importFileRef" type="file" accept=".xlsx,.xls" style="display:none" @change="onImportFileSelected" />
        <!-- 仅方案创建人可新增目标值；非创建人时按钮置灰并通过 tooltip 解释原因 -->
        <el-tooltip
          :disabled="!plans.length || currentPlan?.createdBy === userStore.user?.empId"
          content="只有该目标方案的创建人可以新增目标值"
          placement="top"
        >
          <span>
            <el-button
              type="primary"
              :disabled="!plans.length || currentPlan?.createdBy !== userStore.user?.empId"
              @click="openCreateRow"
            >+ 新增目标值</el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <!-- 筛选栏。方案/维度由 URL 传入锁定 -->
    <div class="card-section filter-grid">
      <div>
        <div class="lab">指标</div>
        <el-select v-model="f.metricCode" clearable filterable @change="loadValues" placeholder="全部" style="width:100%">
          <el-option v-for="m in filteredMetricOptions" :key="m.metricCode"
            :value="m.metricCode" :label="m.metricName" />
        </el-select>
      </div>
    </div>

    <!-- 主表 -->
    <div class="card-section table">
      <el-table :data="pagedRows" size="default" empty-text="暂无目标值" v-loading="loadingValues">
        <el-table-column label="对象" min-width="200">
          <template #default="{row}">
            <!-- EMP: 员工号-中文姓名；ORG: 机构部门编号(dept_no)-机构名 -->
            <span class="subject-text">{{ row.subjectDisplayId || row.subjectId || '-' }} - {{ row.subjectName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="指标" min-width="200">
          <template #default="{row}">{{ row.metricCode }}{{ row.metricName ? ' - ' + row.metricName : '' }}</template>
        </el-table-column>
        <el-table-column label="目标值" width="160" align="right">
          <template #default="{row}">{{ fmtNum(row.targetValue) }}</template>
        </el-table-column>
        <el-table-column label="基础值" width="160" align="right">
          <template #default="{row}">{{ row.baseValue != null ? fmtNum(row.baseValue) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openAdjust(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pager.pageNo"
          v-model:page-size="pager.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredRows.length"
          background
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>

      <el-alert type="info" :closable="false" show-icon style="margin-top:14px"
        title="目标修正保存后立即生效，并触发 KPI 历史回算（生成新批次 CALC-YYMMDD-xxx）。" />
    </div>

    <!-- 修正弹框：截图 180 -->
    <el-dialog v-model="adjDlg.show" :title="adjTitle" width="540px" :close-on-click-modal="false">
      <el-form ref="adjFormRef" :model="adjDlg.form" :rules="adjRules" label-position="top" size="default">
        <el-form-item label="当前目标">
          <el-input :model-value="fmtNum(adjDlg.row?.targetValue)" disabled />
        </el-form-item>
        <el-form-item label="修正后目标" prop="newValue" required>
          <el-input-number v-model="adjDlg.form.newValue" :precision="2" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="当前基础值">
          <el-input :model-value="adjDlg.row?.baseValue != null ? fmtNum(adjDlg.row?.baseValue) : '-'" disabled />
        </el-form-item>
        <el-form-item label="修正后基础值">
          <el-input-number v-model="adjDlg.form.newBaseValue" :precision="2" :controls="false" style="width:100%"
            placeholder="可空：不改基础值则留空" />
        </el-form-item>
        <el-form-item label="修正原因" prop="reason" required>
          <el-input v-model="adjDlg.form.reason" type="textarea" :rows="3"
            placeholder="请说明修正原因，将记入修正记录" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon
        title="保存后目标值立即生效，并触发 KPI 历史回算。" />
      <template #footer>
        <el-button @click="adjDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="adjDlg.saving" @click="onSubmitAdjust">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增目标值 -->
    <!-- 方案 / 周期键 / 对象维度 已隐藏：
         - planId 用当前选中的 f.planId
         - cycleKey 由 inferCycleKey() 自动派生
         - subjectType 用 currentPlan.targetDim（目标管理主页传入方案的维度） -->
    <el-dialog v-model="valDlg.show" title="新增目标值" width="500px">
      <!-- 维度横幅：从目标管理传入，置于所有输入项之上、不允许修改 -->
      <div class="dim-banner">
        当前对象维度：<el-tag class="tag-info" effect="dark">{{ subjectTypeLabel(valDlg.form.subjectType) || '-' }}</el-tag>
        <span class="dim-hint">（由目标方案确定，下方指标按此维度过滤）</span>
      </div>
      <el-form ref="valFormRef" :model="valDlg.form" :rules="valRules" label-width="100px" size="default">
        <el-form-item label="对象" prop="subjectId">
          <el-autocomplete
            v-model="valDlg.form.subjectId"
            :fetch-suggestions="querySubjectSuggestions"
            :placeholder="valDlg.form.subjectType === 'ORG'
              ? '输入机构号或机构名搜索（如 02974000 / 资金财务部）'
              : '输入用户名或中文名搜索（如 finance_zhou / 周八）'"
            clearable
            highlight-first-item
            style="width:100%"
          >
            <template #default="{ item }">
              <div style="display:flex; justify-content:space-between; gap:12px;">
                <span style="font-family: ui-monospace, monospace;">{{ item.value }}</span>
                <span style="color:#999;">{{ item.label }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item label="指标" prop="metricCode">
          <el-select v-model="valDlg.form.metricCode" filterable
                     :placeholder="metricsForDim.length ? '请选择指标' : '所选 KPI 方案暂无可选指标'"
                     style="width:100%">
            <el-option v-for="m in metricsForDim" :key="m.metricCode"
                       :value="m.metricCode"
                       :label="`${m.metricCode} · ${m.metricName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值" prop="targetValue">
          <el-input-number v-model="valDlg.form.targetValue" :precision="2" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="基础值">
          <el-input-number v-model="valDlg.form.baseValue" :precision="2" :controls="false" style="width:100%" placeholder="可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="valDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="valDlg.saving" @click="onSaveValue">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  listTargets, listTargetValues, upsertTargetValue, batchUpsertTargetValues,
  submitTargetAdjust, listMetrics,
  listKpiRules, getKpiSchemeDetail
} from '@/api/perf';
import { listUsers } from '@/api/users';
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
  return codes.some(c => c === 'R_BACK_FINANCE' || c === 'BACK_FINANCE'
                       || c === 'R_ADMIN' || c === 'SYS_ADMIN');
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
const empMap    = ref(new Map()); // empId → { name, orgName, orgCode }
const orgMap    = ref(new Map()); // orgCode(内部机构编码) → orgName
const orgDeptMap = ref(new Map()); // orgCode(内部机构编码) → deptNo(业务机构部门编号)
const metricMap = ref(new Map()); // metricCode → metricName

async function loadEmpMap() {
  try {
    const list = await listUsers({ pageSize: 200 });
    // listUsers 走 unwrapPage：分页响应 { records, total }，不拍平导致 empMap 永远空，
    // 任何 subjectId 都会被前端校验为"员工不存在"，且后端日志无任何错误记录。
    const arr = Array.isArray(list) ? list : (list?.records || []);
    const m = new Map();
    for (const e of arr) {
      // 0 = ENABLED；isEnabled 缺失（不同后端返回字段差异）时也保留，避免漏录
      if (e.isEnabled === 0 || e.isEnabled == null) {
        m.set(e.username, { name: e.userchnname || e.username, orgName: '', orgCode: '' });
      }
    }
    empMap.value = m;
  } catch (e) {
    console.warn('[loadEmpMap] 用户列表加载失败，empMap 为空，前端校验将拦截所有目标值新增', e);
  }
}
async function loadOrgMap() {
  try {
    const tree = await getOrgTree();
    const m = new Map();
    const dm = new Map();
    // getOrgTree 节点形态 { code:内部机构编码, name:机构名, deptNo:业务机构部门编号 }
    const walk = (n) => {
      if (!n) return;
      if (n.code) {
        m.set(n.code, n.name);
        if (n.deptNo != null && n.deptNo !== '') dm.set(n.code, n.deptNo);
      }
      (n.children || []).forEach(walk);
    };
    (Array.isArray(tree) ? tree : []).forEach(walk);
    orgMap.value = m;
    orgDeptMap.value = dm;
  } catch {}
}

// el-autocomplete 数据源：根据当前对象维度从 empMap / orgMap 取候选，模糊匹配 key 或 name。
// EMP：value=username（PT_USER.username），label=中文名（PT_USER.userchnname）；
// ORG：value=机构号（EXT_ORG_INFO.org_code），label=机构名。
function querySubjectSuggestions(query, cb) {
  const dim = valDlg.form.subjectType;
  const q = (query || '').toLowerCase().trim();
  const out = [];
  if (dim === 'EMP') {
    for (const [username, info] of empMap.value) {
      const cn = info?.name || '';
      if (!q || username.toLowerCase().includes(q) || cn.toLowerCase().includes(q)) {
        out.push({ value: username, label: cn });
        if (out.length >= 50) break;
      }
    }
  } else if (dim === 'ORG') {
    for (const [code, name] of orgMap.value) {
      const nm = name || '';
      if (!q || code.toLowerCase().includes(q) || nm.toLowerCase().includes(q)) {
        out.push({ value: code, label: nm });
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
const BASE_DIMS = [
  { v: 'EMP', l: '员工' },
  { v: 'ORG', l: '机构' }
];
const subjectTypeLabel = (t) => ({ EMP: '员工', ORG: '机构' }[t] || '');
const fmtNum = (v) => (v == null || v === '') ? '-' : Number(v).toLocaleString();
const fmtRate = (v) => (v == null) ? '-' : `${Number(v).toFixed(1)}%`;

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
const rateCls = (r) => {
  const n = Number(r);
  if (n >= 90) return 'rate-ok';
  if (n >= 70) return 'rate-mid';
  return 'rate-low';
};
const hasAdjust = (row) => row.approvalStatus === 'ADJUSTING' || row.approvalStatus === 'IN_APPROVAL';
const avaChar = (n) => (n || '').slice(-2, -1) || '员';
const avaColor = (n) => {
  const COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#A855F7', '#EC4899'];
  let h = 0; for (const c of (n || '')) h = (h * 31 + c.charCodeAt(0)) % COLORS.length;
  return COLORS[h];
};

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
const f = reactive({ planId: '', subjectType: '', metricCode: '', approvalStatus: '' });
const values = ref([]);
const loadingValues = ref(false);

// === 当前传入方案上下文（H1 副标题用，仅显示名称，不显示方案/KPI 的 ID） ===
const currentPlan      = computed(() =>
  plans.value.find(p => (p.id || p.planCode) === f.planId) || null
);
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
  if (!f.planId) { values.value = []; pager.pageNo = 1; return; }
  pager.pageNo = 1;
  loadingValues.value = true;
  try {
    // 目标修正取消审批后「提交即生效」，直接写 PERF_TARGET_VALUE，
    // 列表不再 join PERF_TARGET_ADJUST_APPLY，直接展示主表的目标值/基础值。
    const r = await listTargetValues({
      planId: f.planId, planCode: f.planId,
      subjectType: f.subjectType || undefined,
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
        const e = empMap.value.get(x.subjectId);
        if (e) { subjectName ||= e.name; orgName ||= e.orgName; orgCode ||= e.orgCode; }
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
  } catch { values.value = []; } finally { loadingValues.value = false; }
}

const filteredRows = computed(() => {
  let arr = values.value;
  if (f.approvalStatus) arr = arr.filter(r => r.approvalStatus === f.approvalStatus);
  if (f.subjectType)    arr = arr.filter(r => r.subjectType === f.subjectType);
  if (f.metricCode)     arr = arr.filter(r => r.metricCode === f.metricCode);
  return arr;
});

// === 分页（前端 client-side：filteredRows → slice 给表格） ===
const pager = reactive({ pageNo: 1, pageSize: 10 });
const pagedRows = computed(() => {
  const start = (pager.pageNo - 1) * pager.pageSize;
  return filteredRows.value.slice(start, start + pager.pageSize);
});

// === 修正弹框 ===
const adjFormRef = ref(null);
const adjDlg = reactive({
  show: false, saving: false, row: null,
  form: { newValue: 0, newBaseValue: null, cycleKey: '', ownerOrgId: '', reason: '' }
});
const adjTitle = computed(() => {
  if (!adjDlg.row) return '目标修正';
  return `目标修正 · ${subjectTypeLabel(adjDlg.row.subjectType)}${adjDlg.row.subjectName} · ${adjDlg.row.metricName || adjDlg.row.metricCode}`;
});
// 仅必填校验（按要求不做 pattern 正则）
const adjRules = {
  newValue: [{ required: true, message: '请填写修正后的目标值' }],
  reason:   [{ required: true, message: '请填写修正原因（将记入审批日志）', trigger: 'blur' }]
};
function openAdjust(row) {
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
  try { await adjFormRef.value.validate(); } catch { return; }
  // 当前目标值与修正后目标值相同时不允许提交
  const curVal = adjDlg.row?.approvalStatus === 'APPROVED'
    ? Number(adjDlg.row?.currentTarget ?? adjDlg.row?.targetValue ?? 0)
    : Number(adjDlg.row?.targetValue ?? adjDlg.row?.originalTarget ?? 0);
  const curBase = adjDlg.row?.baseValue != null ? Number(adjDlg.row.baseValue) : null;
  const newBase = adjDlg.form.newBaseValue != null ? Number(adjDlg.form.newBaseValue) : null;
  const targetChanged = Number(adjDlg.form.newValue) !== curVal;
  const baseChanged   = newBase !== curBase;
  // 目标值和基础值都没变 → 无需修正
  if (!targetChanged && !baseChanged) {
    return ElMessage.warning('修正后目标值/基础值均与当前值相同，无需修正');
  }
  // 后端必填的 cycleKey / ownerOrgId 在这里自动兜底（UI 不再暴露）
  const plan = plans.value.find(p => (p.id || p.planCode) === f.planId);
  const cycleKey = adjDlg.row.cycleKey || inferCycleKey();
  // 归属机构兜底链：行 → 方案 → 当前登录用户主机构（替代之前硬编码 'ROOT'）
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
      planId:      f.planId,
      subjectType: adjDlg.row.subjectType,
      subjectId:   adjDlg.row.subjectId || adjDlg.row.subjectName,
      cycleKey,
      ownerOrgId,
      reason:      adjDlg.form.reason,
      metricCode:  adjDlg.row.metricCode,
      oldValue:    Number(adjDlg.row.currentTarget ?? adjDlg.row.targetValue ?? 0),
      newValue:    Number(adjDlg.form.newValue),
      oldBaseValue: curBase,
      newBaseValue: newBase
    });
    ElMessage.success('已保存，目标值已生效');
    adjDlg.show = false;
    // 直接生效后重新拉取主表，刷新「当前目标 / 基础值」
    loadValues();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '提交失败');
  } finally { adjDlg.saving = false; }
}
// 从筛选方案 / 当前日期推导 cycleKey（YEARLY: yyyy / QUARTERLY: yyyyQn / MONTHLY: yyyyMM）
function inferCycleKey() {
  const plan = plans.value.find(p => (p.id || p.planCode) === f.planId);
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
  form: { subjectType: 'EMP', subjectId: '', metricCode: '', targetValue: 0, baseValue: null }
});
// subjectType 不再在 UI 暴露：openCreateRow 从 currentPlan.targetDim 自动赋值
const valRules = {
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
  // 维度从当前方案（H1 副标题展示的那条）的 targetDim 取，兜底 EMP
  const dim = currentPlan.value?.targetDim || 'EMP';
  Object.assign(valDlg.form, {
    subjectType: dim, subjectId: '',
    metricCode: '', targetValue: 0, baseValue: null
  });
  // 确保指标下拉已按当前方案的 KPI 指标项过滤就绪
  await loadKpiMetricCodes();
  valDlg.show = true;
}
async function onSaveValue() {
  try { await valFormRef.value.validate(); } catch { return; }
  if (!f.planId) {
    return ElMessage.warning('当前没有选中目标方案，无法保存目标值');
  }
  const dim = valDlg.form.subjectType;
  const sid = valDlg.form.subjectId;
  // 员工/机构存在性校验（同时打到浏览器 console，便于 F12 留痕排查；
  // 前端校验失败 HTTP 请求不会发出，所以后端 boot.log 看不到这条错误）
  if (dim === 'EMP' && !empMap.value.has(sid)) {
    console.warn('[onSaveValue] EMP 不存在', { sid, empMapSize: empMap.value.size, planId: f.planId });
    return ElMessage.error(`员工「${sid}」在系统中不存在`);
  }
  if (dim === 'ORG' && !orgMap.value.has(sid)) {
    console.warn('[onSaveValue] ORG 不存在', { sid, orgMapSize: orgMap.value.size, planId: f.planId });
    return ElMessage.error(`机构「${sid}」在系统中不存在`);
  }
  // 同一方案下 对象+指标 不能重复（检查已有数据）
  const dup = values.value.find(v => v.subjectId === sid && v.metricCode === valDlg.form.metricCode);
  if (dup) {
    return ElMessage.error(`对象「${sid}」+指标「${valDlg.form.metricCode}」在当前方案中已存在`);
  }
  valDlg.saving = true;
  try {
    const payload = {
      planId:      f.planId,
      subjectType: valDlg.form.subjectType,
      subjectId:   valDlg.form.subjectId,
      cycleKey:    inferCycleKey(),
      metricCode:  valDlg.form.metricCode,
      targetValue: valDlg.form.targetValue
    };
    if (valDlg.form.baseValue != null && valDlg.form.baseValue !== '') {
      payload.baseValue = valDlg.form.baseValue;
    }
    await upsertTargetValue(payload);
    ElMessage.success('已保存');
    valDlg.show = false;
    loadValues();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '保存失败');
  } finally { valDlg.saving = false; }
}

// === 导入目标值 / 下载模板 ===
const importFileRef = ref(null);
function triggerImportFile() {
  if (!f.planId) return ElMessage.warning('请先选择目标方案');
  importFileRef.value?.click();
}
async function onImportFileSelected(e) {
  const file = e.target?.files?.[0];
  if (!file) return;
  const plan = currentPlan.value;
  if (!plan) { ElMessage.warning('当前方案信息缺失'); return; }
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
      const subjectId = String(r[1]).trim();
      const metricNameRaw = String(r[2] || '').trim();
      const targetValue = Number(r[3]);
      const baseValue = r[4] != null && r[4] !== '' ? Number(r[4]) : null;
      if (!subjectId || !metricNameRaw || isNaN(targetValue)) {
        errors.push(`第${i + 1}行: 数据不完整`);
        continue;
      }
      // 按维度校验员工/机构对象是否存在
      if (dim === 'EMP' && !empMap.value.has(subjectId)) {
        errors.push(`第${i + 1}行: 员工「${subjectId}」在系统中不存在`); continue;
      }
      if (dim === 'ORG' && !orgMap.value.has(subjectId)) {
        errors.push(`第${i + 1}行: 机构「${subjectId}」在系统中不存在`); continue;
      }
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
    importFileRef.value.value = '';
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
  await Promise.all([loadPlans(), loadMetricOptions(), loadEmpMap(), loadOrgMap(), loadKpiMap()]);
  loadValues();
});
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400;
  em { color: $text-1; font-style: normal; font-weight: 500; }
  .dot { margin: 0 6px; color: $text-4; }
}
.filter-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;
  .lab { font-size: 13px; color: $text-2; margin-bottom: 6px; }
}
.table { padding: 14px 16px 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.dim-banner {
  margin: 0 0 16px; padding: 10px 14px; border-radius: 4px;
  background: rgba(64, 158, 255, 0.08);
  border-left: 3px solid #409eff;
  font-size: 14px; color: $text-2;
}
.dim-hint { margin-left: 8px; font-size: 12px; color: $text-4; }

.subject {
  display: inline-flex; align-items: center; gap: 8px; font-size: 13px;
  .ava {
    width: 26px; height: 26px; border-radius: 50%; color: #fff;
    display: inline-flex; align-items: center; justify-content: center;
    font-size: 12px; font-weight: 600;
    flex-shrink: 0;
  }
  .subject-text {
    display: inline-flex; flex-direction: column; line-height: 1.35;
  }
  .subject-sub {
    font-size: 12px; color: $text-3;
  }
}

.adj-tag {
  margin-left: 6px; font-size: 12px; color: #f59e0b; font-weight: 600;
}

.rate-ok  { color: #16a34a; font-weight: 600; }
.rate-mid { color: #f59e0b; font-weight: 600; }
.rate-low { color: #dc2626; font-weight: 600; }
</style>
