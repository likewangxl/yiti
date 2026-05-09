<template>
  <div>
    <div class="page-h">
      <h1>目标管理 <span class="sub">单条 / 批量 / 修正 <em>(修正会触发回算)</em></span></h1>
      <div class="actions">
        <el-button @click="openImport">📥 导入目标矩阵</el-button>
        <el-button @click="downloadTpl">下载模板</el-button>
        <el-button type="primary" :disabled="!plans.length" @click="openCreateRow">+ 新增目标</el-button>
      </div>
    </div>

    <!-- 筛选栏（4 列） -->
    <div class="card-section filter-grid">
      <div>
        <div class="lab">方案</div>
        <el-select v-model="f.planId" filterable @change="loadValues" placeholder="选择方案" style="width:100%">
          <el-option v-for="p in plans" :key="p.id || p.planCode"
            :value="p.id || p.planCode" :label="`${p.planCode || p.code || ''} ${p.planName || p.name || ''}`.trim()" />
        </el-select>
      </div>
      <div>
        <div class="lab">对象类型</div>
        <el-select v-model="f.subjectType" clearable @change="loadValues" placeholder="全部" style="width:100%">
          <el-option v-for="o in BASE_DIMS" :key="o.v" :value="o.v" :label="o.l" />
        </el-select>
      </div>
      <div>
        <div class="lab">指标</div>
        <el-select v-model="f.metricCode" clearable filterable @change="loadValues" placeholder="全部" style="width:100%">
          <el-option v-for="m in metricOptions" :key="m.metricCode"
            :value="m.metricCode" :label="m.metricName" />
        </el-select>
      </div>
      <div>
        <div class="lab">状态</div>
        <el-select v-model="f.approvalStatus" clearable @change="loadValues" placeholder="全部" style="width:100%">
          <el-option label="已审批" value="APPROVED" />
          <el-option label="修正中" value="ADJUSTING" />
          <el-option label="待审批" value="PENDING" />
          <el-option label="草稿" value="DRAFT" />
        </el-select>
      </div>
    </div>

    <!-- 主表 -->
    <div class="card-section table">
      <el-table :data="filteredRows" size="default" empty-text="暂无目标值" v-loading="loadingValues">
        <el-table-column label="对象" min-width="160">
          <template #default="{row}">
            <span class="subject">
              <span class="ava" :style="{ background: avaColor(row.subjectName) }">{{ avaChar(row.subjectName) }}</span>
              {{ subjectTypeLabel(row.subjectType) }}{{ row.subjectName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="机构" width="130">
          <template #default="{row}">{{ row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column label="指标" min-width="180">
          <template #default="{row}">{{ row.metricName || row.metricCode }}</template>
        </el-table-column>
        <el-table-column label="原目标" width="100" align="right">
          <template #default="{row}">{{ fmtNum(row.originalTarget ?? row.targetValue) }}</template>
        </el-table-column>
        <el-table-column label="当前目标" width="140" align="right">
          <template #default="{row}">
            <strong>{{ fmtNum(row.currentTarget ?? row.targetValue) }}</strong>
            <span v-if="hasAdjust(row)" class="adj-tag">↑修正</span>
          </template>
        </el-table-column>
        <el-table-column label="累计完成" width="110" align="right">
          <template #default="{row}">{{ fmtNum(row.cumulativeActual ?? row.actual) }}</template>
        </el-table-column>
        <el-table-column label="完成率" width="100" align="right">
          <template #default="{row}">
            <span :class="rateCls(row.completeRate)">{{ fmtRate(row.completeRate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="apprCls(row.approvalStatus)" effect="plain">{{ apprLabel(row.approvalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openAdjust(row)">修正</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert type="info" :closable="false" show-icon style="margin-top:14px"
        title="目标修正审批通过后将触发 KPI 历史回算（生成新批次 CALC-YYMMDD-xxx）。" />
    </div>

    <!-- 修正弹框：截图 180 -->
    <el-dialog v-model="adjDlg.show" :title="adjTitle" width="540px" :close-on-click-modal="false">
      <el-form ref="adjFormRef" :model="adjDlg.form" :rules="adjRules" label-position="top" size="default">
        <el-form-item label="原目标">
          <el-input :model-value="fmtNum(adjDlg.row?.originalTarget ?? adjDlg.row?.targetValue)" disabled />
        </el-form-item>
        <el-form-item label="当前目标">
          <el-input :model-value="fmtNum(adjDlg.row?.currentTarget ?? adjDlg.row?.targetValue)" disabled />
        </el-form-item>
        <el-form-item label="修正后目标" prop="newValue" required>
          <el-input-number v-model="adjDlg.form.newValue" :precision="2" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="修正原因" prop="reason" required>
          <el-input v-model="adjDlg.form.reason" type="textarea" :rows="3"
            placeholder="请说明修正原因，将记入审批日志" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon
        title="提交后将进入 1 级审批（直属上级）。审批通过将触发 KPI 历史回算。" />
      <template #footer>
        <el-button @click="adjDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="adjDlg.saving" @click="onSubmitAdjust">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 新增目标值（保留入口） -->
    <el-dialog v-model="valDlg.show" title="新增目标值" width="500px">
      <el-form ref="valFormRef" :model="valDlg.form" :rules="valRules" label-width="100px" size="default">
        <el-form-item label="方案" prop="planId">
          <el-select v-model="valDlg.form.planId" filterable style="width:100%">
            <el-option v-for="p in plans" :key="p.id || p.planCode"
              :value="p.id || p.planCode" :label="`${p.planCode || p.code} ${p.planName || p.name || ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象维度" prop="subjectType">
          <el-select v-model="valDlg.form.subjectType" style="width:100%">
            <el-option v-for="o in BASE_DIMS" :key="o.v" :value="o.v" :label="o.l" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象主键" prop="subjectId"><el-input v-model="valDlg.form.subjectId" /></el-form-item>
        <el-form-item label="指标编码" prop="metricCode"><el-input v-model="valDlg.form.metricCode" /></el-form-item>
        <el-form-item label="目标值" prop="targetValue">
          <el-input-number v-model="valDlg.form.targetValue" :precision="2" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="周期键" prop="cycleKey" required>
          <el-input v-model="valDlg.form.cycleKey" placeholder="如 2026 / 2026Q2 / 202604" />
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

    <!-- 导入提示 -->
    <el-dialog v-model="importTip" title="导入目标矩阵" width="440px">
      <p style="line-height:1.7">请使用左侧菜单「数据导入 → 目标值导入」上传 Excel；上传后该方案会刷新此表。</p>
      <template #footer><el-button type="primary" @click="importTip = false">知道了</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listTargets, listTargetValues, upsertTargetValue,
  submitTargetAdjust, listMetrics
} from '@/api/perf';
import { listEmployees } from '@/api/employees';
import { getOrgTree } from '@/api/orgs';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();

// === 员工 / 机构 / 指标 三套 id→name 映射缓存 ===
// 后端 PERF_TARGET_VALUE 表只有 subject_id（员工号或机构编码），不返机构名/员工名/指标名
// → 列表渲染时用这 3 个 map 把 ID 翻译成可读名称
const empMap    = ref(new Map()); // empId → { name, orgName, orgCode }
const orgMap    = ref(new Map()); // orgCode → orgName
const metricMap = ref(new Map()); // metricCode → metricName

async function loadEmpMap() {
  try {
    const list = await listEmployees({ pageSize: 500 });
    if (Array.isArray(list)) {
      const m = new Map();
      for (const e of list) m.set(e.id, { name: e.name, orgName: e.org, orgCode: e.orgCode });
      empMap.value = m;
    }
  } catch {}
}
async function loadOrgMap() {
  try {
    const tree = await getOrgTree();
    const m = new Map();
    const walk = (n) => { if (!n) return; if (n.code) m.set(n.code, n.name); (n.children || []).forEach(walk); };
    (Array.isArray(tree) ? tree : []).forEach(walk);
    orgMap.value = m;
  } catch {}
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
  APPROVED:  'tag-success',
  ADJUSTING: 'tag-warning',
  PENDING:   'tag-warning',
  DRAFT:     'tag-info'
}[s] || 'tag-success');
const apprLabel = (s) => ({
  APPROVED:  '已审批',
  ADJUSTING: '修正中',
  PENDING:   '待审批',
  DRAFT:     '草稿'
}[s] || '已审批');
const rateCls = (r) => {
  const n = Number(r);
  if (n >= 90) return 'rate-ok';
  if (n >= 70) return 'rate-mid';
  return 'rate-low';
};
const hasAdjust = (row) => row.approvalStatus === 'ADJUSTING'
  || (row.originalTarget != null && row.currentTarget != null && row.originalTarget !== row.currentTarget);
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
    plans.value = Array.isArray(r) ? r : [];
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

// === 目标值 ===
const f = reactive({ planId: '', subjectType: '', metricCode: '', approvalStatus: '' });
const values = ref([]);
const loadingValues = ref(false);

// 截图 179 风格的 mock 行（后端无数据时兜底，便于 UI 验证）
const MOCK_ROWS = [
  { id:'r1', subjectType:'EMP', subjectName:'张三', orgName:'南山支行', metricCode:'M0001', metricName:'存款日均增量（万）', originalTarget:8000, currentTarget:8000, cumulativeActual:6420, completeRate:80.3, approvalStatus:'APPROVED' },
  { id:'r2', subjectType:'EMP', subjectName:'张三', orgName:'南山支行', metricCode:'M0003', metricName:'贷款余额增量（万）', originalTarget:12000, currentTarget:13500, cumulativeActual:9100, completeRate:67.4, approvalStatus:'ADJUSTING' },
  { id:'r3', subjectType:'EMP', subjectName:'张三', orgName:'南山支行', metricCode:'M1001', metricName:'新增有效客户数', originalTarget:30, currentTarget:30, cumulativeActual:22, completeRate:73.3, approvalStatus:'APPROVED' },
  { id:'r4', subjectType:'EMP', subjectName:'李四', orgName:'福田支行', metricCode:'M0001', metricName:'存款日均增量（万）', originalTarget:6000, currentTarget:6000, cumulativeActual:5520, completeRate:92.0, approvalStatus:'APPROVED' },
  { id:'r5', subjectType:'EMP', subjectName:'李四', orgName:'福田支行', metricCode:'M0003', metricName:'贷款余额增量（万）', originalTarget:9000, currentTarget:9000, cumulativeActual:7800, completeRate:86.7, approvalStatus:'APPROVED' },
  { id:'r6', subjectType:'EMP', subjectName:'孙七', orgName:'罗湖支行', metricCode:'M0001', metricName:'存款日均增量（万）', originalTarget:7000, currentTarget:7000, cumulativeActual:4280, completeRate:61.1, approvalStatus:'APPROVED' }
];

async function loadValues() {
  if (!f.planId) { values.value = MOCK_ROWS; return; }
  loadingValues.value = true;
  try {
    const r = await listTargetValues({
      planId: f.planId, planCode: f.planId,
      subjectType: f.subjectType || undefined,
      metricCode: f.metricCode || undefined,
      pageSize: 100
    });
    if (Array.isArray(r) && r.length) {
      values.value = r.map(x => {
        // 用 empMap / orgMap 翻译 subjectId → 显示名 + 所属机构
        let subjectName = x.subjectName || '';
        let orgName     = x.orgName     || '';
        let orgCode     = x.orgCode     || '';
        if (x.subjectType === 'EMP') {
          const e = empMap.value.get(x.subjectId);
          if (e) { subjectName ||= e.name; orgName ||= e.orgName; orgCode ||= e.orgCode; }
          if (!subjectName) subjectName = x.subjectId;  // 兜底用 ID
        } else if (x.subjectType === 'ORG') {
          orgCode ||= x.subjectId;
          orgName ||= orgMap.value.get(x.subjectId) || x.subjectId;
          subjectName ||= orgName;
        }
        // 指标名翻译
        const metricName = x.metricName || metricMap.value.get(x.metricCode) || x.metricCode;
        return {
          ...x,
          subjectName, orgName, orgCode, metricName,
          currentTarget:  x.currentTarget  ?? x.targetValue,
          originalTarget: x.originalTarget ?? x.targetValue,
          completeRate:   x.completeRate ?? (x.cumulativeActual && x.targetValue ? (x.cumulativeActual / x.targetValue * 100) : null),
          approvalStatus: x.approvalStatus || 'APPROVED'
        };
      });
    } else {
      values.value = MOCK_ROWS;
    }
  } catch { values.value = MOCK_ROWS; } finally { loadingValues.value = false; }
}

const filteredRows = computed(() => {
  let arr = values.value;
  if (f.approvalStatus) arr = arr.filter(r => r.approvalStatus === f.approvalStatus);
  if (f.subjectType)    arr = arr.filter(r => r.subjectType === f.subjectType);
  if (f.metricCode)     arr = arr.filter(r => r.metricCode === f.metricCode);
  return arr;
});

// === 修正弹框 ===
const adjFormRef = ref(null);
const adjDlg = reactive({
  show: false, saving: false, row: null,
  form: { newValue: 0, cycleKey: '', ownerOrgId: '', reason: '' }
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
  adjDlg.form.reason   = '';
  adjDlg.show = true;
}
async function onSubmitAdjust() {
  try { await adjFormRef.value.validate(); } catch { return; }
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
      newValue:    Number(adjDlg.form.newValue)
    });
    ElMessage.success('已提交审批');
    adjDlg.show = false;
    adjDlg.row.approvalStatus = 'ADJUSTING';
    adjDlg.row.currentTarget  = adjDlg.form.newValue;
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
// 后端 UpsertTargetValueReqDTO 字段（7 个）：
//   planId / subjectType(EMP|ORG) / subjectId / cycleKey(2026|2026Q1|202604) /
//   metricCode(^[A-Z][A-Z0-9_]*$) / targetValue / baseValue?
const valFormRef = ref(null);
const valDlg = reactive({
  show: false, saving: false,
  form: { planId: '', subjectType: 'EMP', subjectId: '', metricCode: '', targetValue: 0, cycleKey: '', baseValue: null }
});
// 仅必填校验（按要求不做 pattern 正则，留给后端兜底）
const valRules = {
  planId:      [{ required: true, message: '请选择目标方案' }],
  subjectType: [{ required: true, message: '请选择对象类型（员工 / 机构）' }],
  subjectId:   [{ required: true, message: '请填写对象编号（员工号 / 机构编码）' }],
  metricCode:  [{ required: true, message: '请填写指标编码' }],
  targetValue: [{ required: true, message: '请填写目标值' }],
  cycleKey:    [{ required: true, message: '请填写周期键' }]
};
function openCreateRow() {
  Object.assign(valDlg.form, {
    planId: f.planId, subjectType: 'EMP', subjectId: '',
    metricCode: '', targetValue: 0,
    cycleKey: inferCycleKey(),  // 默认按方案推导当前周期
    baseValue: null
  });
  valDlg.show = true;
}
async function onSaveValue() {
  try { await valFormRef.value.validate(); } catch { return; }
  // 后端 metricCode @Pattern(^[A-Z][A-Z0-9_]*$) 是强约束，前端无法砍 @Pattern。
  // 折中：提交前自动转大写 + 替换非法字符为 _，并保证首字符是字母 —— 用户输小写/横线/中文都能落库
  let mc = String(valDlg.form.metricCode || '').toUpperCase()
    .replace(/[^A-Z0-9_]/g, '_')
    .replace(/^[^A-Z]+/, '');
  if (!mc) {
    ElMessage.warning('指标编码无法解析，请重新填写');
    return;
  }
  valDlg.form.metricCode = mc;  // 同步回 UI

  valDlg.saving = true;
  try {
    // 严格按后端 7 个字段提交，避免 Jackson FAIL_ON_UNKNOWN_PROPERTIES 报 "Unrecognized field"
    const payload = {
      planId:      valDlg.form.planId,
      subjectType: valDlg.form.subjectType,
      subjectId:   valDlg.form.subjectId,
      cycleKey:    valDlg.form.cycleKey,
      metricCode:  mc,
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

// === 导入 / 模板 ===
const importTip = ref(false);
function openImport() { importTip.value = true; }
function downloadTpl() {
  ElMessage.info('模板下载（占位）：请联系运维提供 target_template.xlsx');
}

onMounted(async () => {
  // 4 个基础数据并发：方案 / 指标库 / 员工映射 / 机构映射
  await Promise.all([loadPlans(), loadMetricOptions(), loadEmpMap(), loadOrgMap()]);
  loadValues();
});
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400;
  em { color: $text-4; font-style: normal; }
}
.filter-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
  .lab { font-size: 13px; color: $text-2; margin-bottom: 6px; }
}
.table { padding: 14px 16px 12px; }

.subject {
  display: inline-flex; align-items: center; gap: 8px; font-size: 13px;
  .ava {
    width: 26px; height: 26px; border-radius: 50%; color: #fff;
    display: inline-flex; align-items: center; justify-content: center;
    font-size: 12px; font-weight: 600;
  }
}

.adj-tag {
  margin-left: 6px; font-size: 12px; color: #f59e0b; font-weight: 600;
}

.rate-ok  { color: #16a34a; font-weight: 600; }
.rate-mid { color: #f59e0b; font-weight: 600; }
.rate-low { color: #dc2626; font-weight: 600; }
</style>
