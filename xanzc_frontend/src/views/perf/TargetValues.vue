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
        <el-button @click="openImport">📥 导入目标矩阵</el-button>
        <el-button @click="downloadTpl">下载模板</el-button>
        <el-button type="primary" :disabled="!plans.length" @click="openCreateRow">+ 新增目标值</el-button>
      </div>
    </div>

    <!-- 筛选栏（3 列）。方案不再可在此切换：planId 由 URL ?planId= 传入并锁定，H1 副标题显示 -->
    <div class="card-section filter-grid">
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
      <el-table :data="pagedRows" size="default" empty-text="暂无目标值" v-loading="loadingValues">
        <el-table-column label="维度" width="80">
          <template #default="{row}">
            <el-tag class="tag-info" effect="plain">{{ subjectTypeLabel(row.subjectType) || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="200">
          <template #default="{row}">
            <span class="subject">
              <span class="ava" :style="{ background: avaColor(row.subjectName) }">{{ avaChar(row.subjectName) }}</span>
              <span class="subject-text">
                <div>{{ row.subjectName || '-' }}</div>
                <!-- EMP 维度：副行显示员工所属机构；ORG 维度时机构本身就是 subjectName，无需重复 -->
                <div v-if="row.subjectType === 'EMP' && row.orgName" class="subject-sub">{{ row.orgName }}</div>
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="指标" min-width="180">
          <template #default="{row}">{{ row.metricName || row.metricCode }}</template>
        </el-table-column>
        <el-table-column label="当前目标" width="100" align="right">
          <template #default="{row}">{{ fmtNum(row.targetValue) }}</template>
        </el-table-column>
        <el-table-column label="修正目标" width="140" align="right">
          <template #default="{row}">
            <template v-if="row.approvalStatus === 'ADJUSTING' || row.approvalStatus === 'IN_APPROVAL' || row.approvalStatus === 'APPROVED' || row.approvalStatus === 'REJECTED'">
              <strong>{{ fmtNum(row.currentTarget) }}</strong>
              <span v-if="hasAdjust(row)" class="adj-tag">↑修正</span>
            </template>
            <span v-else>-</span>
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
            <!-- 仅资财部经办 (BACK_FINANCE) / 系统管理员可发起目标修正；
                 资财部负责人 (FINANCE_LEADER) 是审批人，不发起 -->
            <el-button v-if="canTargetAdjust" link type="primary" size="small" @click="openAdjust(row)">修正</el-button>
            <span v-else>-</span>
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
        title="目标修正审批通过后将触发 KPI 历史回算（生成新批次 CALC-YYMMDD-xxx）。" />
    </div>

    <!-- 修正弹框：截图 180 -->
    <el-dialog v-model="adjDlg.show" :title="adjTitle" width="540px" :close-on-click-modal="false">
      <el-form ref="adjFormRef" :model="adjDlg.form" :rules="adjRules" label-position="top" size="default">
        <el-form-item label="当前目标">
          <!-- 修正中/驳回 → 原目标值；审批通过 → 新目标值（已生效） -->
          <el-input :model-value="fmtNum(adjDlg.row?.approvalStatus === 'APPROVED' ? (adjDlg.row?.currentTarget ?? adjDlg.row?.targetValue) : (adjDlg.row?.targetValue ?? adjDlg.row?.originalTarget))" disabled />
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
        <el-form-item label="对象主键" prop="subjectId"><el-input v-model="valDlg.form.subjectId" /></el-form-item>
        <el-form-item label="指标" prop="metricCode">
          <el-select v-model="valDlg.form.metricCode" filterable
                     :placeholder="metricsForDim.length ? '请选择指标' : '所选维度暂无指标'"
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

    <!-- 导入提示 -->
    <el-dialog v-model="importTip" title="导入目标矩阵" width="440px">
      <p style="line-height:1.7">请使用左侧菜单「数据导入 → 目标值导入」上传 Excel；上传后该方案会刷新此表。</p>
      <template #footer><el-button type="primary" @click="importTip = false">知道了</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  listTargets, listTargetValues, upsertTargetValue,
  submitTargetAdjust, listMetrics,
  listKpiRules, listTargetAdjusts
} from '@/api/perf';
import { listEmployees } from '@/api/employees';
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
  IN_APPROVAL: 'tag-warning',
  PENDING:   'tag-warning',
  DRAFT:     'tag-info',
  REJECTED:  'tag-danger'
}[s] || 'tag-info');
const apprLabel = (s) => ({
  APPROVED:  '已审批',
  ADJUSTING: '修正中',
  IN_APPROVAL: '修正中',
  PENDING:   '待审批',
  DRAFT:     '草稿',
  REJECTED:  '已驳回'
}[s] || '-');
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
  if (!f.planId) { values.value = MOCK_ROWS; pager.pageNo = 1; return; }
  pager.pageNo = 1;
  loadingValues.value = true;
  try {
    // 1. 拉目标值 + 并行拉当前方案的全量修正申请（含 IN_APPROVAL / APPROVED / REJECTED）
    const [r, adjusts] = await Promise.all([
      listTargetValues({
        planId: f.planId, planCode: f.planId,
        subjectType: f.subjectType || undefined,
        metricCode: f.metricCode || undefined,
        pageSize: 100
      }),
      listTargetAdjusts({ planId: f.planId, pageSize: 100 }).catch(() => [])
    ]);

    // 2. 构建修正 lookup：key = "subjectType:subjectId:metricCode" → 最新一条申请的 { newValue, oldValue, status }
    //    同一 key 可能有多条申请（如先驳回再重新提交），取 createdTime 最新的那条
    const adjustMap = new Map();
    for (const adj of (Array.isArray(adjusts) ? adjusts : [])) {
      try {
        const remark = typeof adj.remark === 'string' ? JSON.parse(adj.remark) : adj.remark;
        const items = remark?.adjustments || [];
        for (const item of items) {
          const key = `${adj.subjectType}:${adj.subjectId}:${item.metricCode}`;
          const existing = adjustMap.get(key);
          const adjTime = new Date(adj.createdTime || 0).getTime();
          if (!existing || adjTime > existing._time) {
            adjustMap.set(key, {
              newValue: item.newValue, oldValue: item.oldValue,
              status: adj.status, reason: remark.reason || '', _time: adjTime
            });
          }
        }
      } catch { /* remark 解析失败跳过 */ }
    }

    if (Array.isArray(r) && r.length) {
      values.value = r.map(x => {
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

        // 3. join 修正申请：按最新申请的 status 决定行显示
        //    IN_APPROVAL → 修正中 + currentTarget=newValue
        //    APPROVED    → 通过（currentTarget=主表值，即已落库的 newValue）
        //    REJECTED    → 已驳回 + currentTarget=原目标值（数值不变）
        const adjKey = `${x.subjectType}:${x.subjectId}:${x.metricCode}`;
        const adj = adjustMap.get(adjKey);
        let approvalStatus = x.approvalStatus || '';
        let currentTarget  = x.currentTarget ?? x.targetValue;
        if (adj) {
          if (adj.status === 'IN_APPROVAL') {
            approvalStatus = 'ADJUSTING';
            currentTarget  = adj.newValue ?? currentTarget;
          } else if (adj.status === 'APPROVED') {
            approvalStatus = 'APPROVED';
            currentTarget  = adj.newValue ?? currentTarget;
          } else if (adj.status === 'REJECTED') {
            approvalStatus = 'REJECTED';
            currentTarget  = x.targetValue;
          }
        }

        return {
          ...x,
          subjectName, orgName, orgCode, metricName,
          currentTarget,
          adjustReason: adj?.reason || '',
          originalTarget: x.originalTarget ?? x.targetValue,
          completeRate:   x.completeRate ?? (x.cumulativeActual && x.targetValue ? (x.cumulativeActual / x.targetValue * 100) : null),
          approvalStatus
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
  adjDlg.form.reason   = row.adjustReason || '';
  adjDlg.show = true;
}
async function onSubmitAdjust() {
  try { await adjFormRef.value.validate(); } catch { return; }
  // 当前目标值与修正后目标值相同时不允许提交
  const curVal = adjDlg.row?.approvalStatus === 'APPROVED'
    ? Number(adjDlg.row?.currentTarget ?? adjDlg.row?.targetValue ?? 0)
    : Number(adjDlg.row?.targetValue ?? adjDlg.row?.originalTarget ?? 0);
  if (Number(adjDlg.form.newValue) === curVal) {
    return ElMessage.warning('修正后目标值与当前目标值相同，无需修正');
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

// 指标随对象维度过滤：MetricDefDTO.baseDim ∈ {EMP, ORG, CUST}；
// 维度由目标方案传入（valDlg.form.subjectType），按 baseDim === subjectType 匹配
const metricsForDim = computed(() =>
  metricOptions.value.filter(m => !m.baseDim || m.baseDim === valDlg.form.subjectType)
);

function openCreateRow() {
  if (!f.planId) {
    return ElMessage.warning('请先选择目标方案再新增目标值');
  }
  // 维度从当前方案（H1 副标题展示的那条）的 targetDim 取，兜底 EMP
  const dim = currentPlan.value?.targetDim || 'EMP';
  Object.assign(valDlg.form, {
    subjectType: dim, subjectId: '',
    metricCode: '', targetValue: 0, baseValue: null
  });
  valDlg.show = true;
}
async function onSaveValue() {
  try { await valFormRef.value.validate(); } catch { return; }
  if (!f.planId) {
    return ElMessage.warning('当前没有选中目标方案，无法保存目标值');
  }
  valDlg.saving = true;
  try {
    // 严格按后端字段提交；planId 用当前选中方案，cycleKey 按方案周期类型自动派生
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

// === 导入 / 模板 ===
const importTip = ref(false);
function openImport() { importTip.value = true; }
function downloadTpl() {
  ElMessage.info('模板下载（占位）：请联系运维提供 target_template.xlsx');
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
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px;
  .lab { font-size: 13px; color: $text-2; margin-bottom: 6px; }
}
.table { padding: 14px 16px 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
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
