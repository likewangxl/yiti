<template>
  <div>
    <div class="page-h">
      <h1>KPI 规则</h1>
      <span class="desc">方案 · 权重 · 公式预览 · 计分上下限</span>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button v-if="isCaizai" type="primary" @click="openCreate">+ 新增KPI方案</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="方案名称">
          <el-input v-model="f.keyword" clearable placeholder="编码 / 名称" style="width:220px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="f.status" clearable placeholder="全部" style="width:160px" @change="reload">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="filteredRows" size="default" empty-text="暂无方案" v-loading="loading">
        <el-table-column label="方案编码" width="130">
          <template #default="{row}"><code class="mono">{{ row.schemeCode }}</code></template>
        </el-table-column>
        <el-table-column label="方案名称" min-width="200">
          <template #default="{row}">
            <a class="link" @click="openEdit(row, true)">{{ row.schemeName }}</a>
          </template>
        </el-table-column>
        <el-table-column label="指标项" width="90" align="center">
          <template #default="{row}">{{ row.itemCount ?? row.items?.length ?? '...' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{row}">{{ row.version || resolveVersion(row) }}</template>
        </el-table-column>
        <el-table-column label="创建人" min-width="140">
          <template #default="{row}">
            <template v-if="row.createdByName || row.createdByUsername || row.createdBy">
              <div>{{ row.createdByName || row.createdByUsername || row.createdBy }}</div>
              <div v-if="row.createdByUsername || row.createdBy" style="color:#909399;font-size:12px;">{{ row.createdByUsername || row.createdBy }}</div>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row, true)">查看</el-button>
            <!-- 复制版本/编辑/删除 仅资财部人员可见可操作 -->
            <template v-if="isCaizai">
              <el-button link type="primary" size="small" :disabled="isDisabled(row)" @click="onCloneVersion(row)">复制版本</el-button>
              <!-- 编辑/删除：仅当前用户创建的方案才显示可操作 -->
              <template v-if="row.createdByMe">
                <el-button link type="primary" size="small" :disabled="isDisabled(row)" @click="openEdit(row, false)">编辑</el-button>
                <el-popconfirm v-if="!isDisabled(row)" :title="`确认删除方案 ${row.schemeName}？`" @confirm="onDelete(row)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
                <el-popconfirm v-else :title="`确认启用方案 ${row.schemeName}？`" @confirm="onEnable(row)">
                  <template #reference>
                    <el-button link type="success" size="small">启用</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="reload"
        />
      </div>
    </div>

    <!-- 编辑/新增/查看 大弹框（合并方案信息 + 指标配置表 + 公式提示） -->
    <el-dialog
      v-model="dlg.show"
      :title="dlgTitle"
      width="1100px" top="5vh"
      :close-on-click-modal="false"
      @closed="onDlgClosed"
    >
      <!-- 头部三字段一行（中文 rules，避免 element-plus 默认英文 required 提示） -->
      <el-form ref="schemeFormRef" :model="dlg.scheme" :rules="schemeRules" label-position="top" size="default">
        <div class="form-grid">
          <el-form-item label="方案编码" prop="schemeCode">
            <el-input v-model="dlg.scheme.schemeCode" :disabled="dlg.readOnly || !!dlg.editingId" placeholder="如 KPI005" />
          </el-form-item>
          <el-form-item label="方案名称" prop="schemeName">
            <el-input v-model="dlg.scheme.schemeName" :disabled="dlg.readOnly" />
          </el-form-item>
          <el-form-item label="是否向员工开放明细" prop="openDetail">
            <el-select v-model="dlg.scheme.openDetail" :disabled="dlg.readOnly" style="width:100%">
              <el-option :value="true" label="是" />
              <el-option :value="false" label="否" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <!-- 指标配置表 -->
      <div class="card-h">
        <div class="title">指标配置</div>
        <span class="weight-sum" :class="{ ok: weightSum === 100 }">
          权重合计：{{ weightSum.toFixed(0) }}% {{ weightSum === 100 ? '✓' : '' }}
        </span>
      </div>
      <!-- default-expand-all：SQL 表达式作为展开行单独占满一行编辑/展示 -->
      <el-table :data="dlg.items" size="default" border default-expand-all>
        <!-- 展开行：SQL 表达式（支持 #{slot} 占位符），单独一行全宽编辑 -->
        <el-table-column type="expand">
          <template #default="{row}">
            <div class="sql-expr-row">
              <span class="sql-expr-label">SQL 表达式 (支持 #{slot} 占位符)</span>
              <el-input
                v-model="row.sqlExpr"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 8 }"
                :disabled="dlg.readOnly"
                :placeholder="dlg.readOnly ? '' : '如 SELECT LEAST(:maxScore, GREATEST(:minScore, :actual / NULLIF(:target,0) * :weight)) AS kpi_value'"
                @focus="onSqlFocus($event, row)"
              />
            </div>
          </template>
        </el-table-column>
        <!-- 维度：默认员工；切换维度动态过滤下方指标下拉内容。编辑时允许修改（改维度会清空指标重选） -->
        <el-table-column label="维度" width="110">
          <template #default="{row}">
            <el-select v-model="row.baseDim" :disabled="dlg.readOnly"
              placeholder="维度" style="width:100%" @change="onDimChange(row)">
              <el-option value="EMP" label="员工" />
              <el-option value="ORG" label="机构" />
              <el-option value="CUST" label="客户" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="指标" min-width="190">
          <template #default="{row}">
            <!-- 编辑方案时允许改指标：若已配置项换了指标，保存时按"删旧+新增"处理 -->
            <el-select v-model="row.metricCode" :disabled="dlg.readOnly" filterable placeholder="选择指标" style="width:100%">
              <!-- 下拉项显示「指标名称 + 指标编号」：名称为主、编号灰字附后；label 含编号便于按编号检索/选中回显 -->
              <el-option v-for="m in metricsByDim(row.baseDim)" :key="m.metricCode"
                :value="m.metricCode" :label="`${m.metricName}（${m.metricCode}）`">
                <span>{{ m.metricName }}</span>
                <span style="color:#8492a6;font-size:12px;margin-left:8px;">{{ m.metricCode }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="权重 %" width="120">
          <template #default="{row}">
            <el-input-number v-model="row.weight" :disabled="dlg.readOnly" :min="0" :max="100" :precision="0" :controls="false" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="计分上限" width="120">
          <template #default="{row}">
            <el-input-number v-model="row.maxScore" :disabled="dlg.readOnly" :min="0" :precision="0" :controls="false" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="计分下限" width="120">
          <template #default="{row}">
            <!-- 计分下限允许负值，不限制最小值 -->
            <el-input-number v-model="row.minScore" :disabled="dlg.readOnly" :precision="0" :controls="false" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column v-if="!dlg.readOnly" label="操作" width="70" align="center" fixed="right">
          <template #default="{$index}">
            <el-button link type="danger" size="small" @click="dlg.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-button v-if="!dlg.readOnly" plain @click="addItemRow" style="margin-top:10px">+ 添加指标</el-button>

      <!-- SQL 可用变量提示（参考指标编辑）：点击占位符插入到 SQL 表达式光标处 -->
      <div class="sql-date-macros" style="margin-top:12px">
        <div class="hint-title">可用变量（点击插入到 SQL 光标处；后端按 dataDate 自动计算注入）</div>
        <table class="hint-table">
          <tr><th style="width:180px">SQL 占位符</th><th>含义</th></tr>
          <tr v-for="m in SQL_MACROS" :key="m.token">
            <td>
              <code class="macro-btn" @mousedown.prevent="insertMacro(m.token)" :title="`点击插入 ${m.token}`">{{ m.token }}</code>
            </td>
            <td>{{ m.desc }}</td>
          </tr>
        </table>
        <div class="hint-foot">结果列只需含 <code>kpi_value</code>(KPI得分)；对象id 由系统按行传入，无需在 SQL 中返回。</div>
      </div>

      <template #footer>
        <el-button @click="dlg.show = false">{{ dlg.readOnly ? '关闭' : '取消' }}</el-button>
        <template v-if="!dlg.readOnly">
          <el-button :loading="dlg.saving" @click="onSave('TRIAL_RUN')">保存为草稿</el-button>
          <el-button type="primary" :loading="dlg.saving" @click="onSave('ACTIVE')">发布</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listKpiRules, getKpiSchemeDetail,
  createKpiScheme, updateKpiScheme, deleteKpiScheme, publishKpiScheme,
  addKpiItem, updateKpiItem, deleteKpiItem,
  listMetrics
} from '@/api/perf';

// === 字典 ===
// 状态从 SYS_DICT.dict_type='KPI_SCHEME_STATUS' 拉，不再写死中英文映射
import { useDict } from '@/composables/useDict';
import { getOrgTree } from '@/api/orgs';
import { useUserStore } from '@/stores/user';

// 资财部人员（角色 资财部经办人 R_BACK_FINANCE / 资财部负责人 R_FIN_LEAD）才可见新增/复制/编辑/删除
const userStore = useUserStore();
const isCaizai = computed(() => {
  const roles = userStore.user?.roles || [];
  // ROLE_ID 已对齐内网数字：资财部经办人=238 / 资财部负责人=129
  return roles.some(r => r.roleId === '238' || r.roleId === '129');
});
// labelOf 名称冲突：保留下面行业版的 statusLabel（含 DISABLED → 已删除映射），useDict 只取 options
const { options: STATUS_OPTIONS } = useDict('KPI_SCHEME_STATUS');
// 适用周期 UI 已按需求移除（新增方案默认 cycleType=YEARLY，仍随提交透传给后端）

const statusCls = (s) => ({ ACTIVE: 'tag-success', TRIAL_RUN: 'tag-warning', DRAFT: 'tag-info', INACTIVE: 'tag-info', DISABLED: 'tag-info' }[s] || 'tag-info');
const statusLabel = (s) => ({ ACTIVE: '启用', TRIAL_RUN: '试运行', DRAFT: '草稿', INACTIVE: '已删除', DISABLED: '已删除' }[s] || s || '-');
// 已删除（停用）方案：按钮失效
const isDisabled = (row) => row.status === 'INACTIVE' || row.status === 'DISABLED';

// 中文 rules（仅必填，按要求不加 pattern 校验）
const schemeFormRef = ref(null);
const schemeRules = {
  schemeCode: [{ required: true, message: '请填写方案编码', trigger: 'blur' }],
  schemeName: [{ required: true, message: '请填写方案名称', trigger: 'blur' }]
};

// 刚创建的 schemeId / schemeCode 集合 → reload 时排在最前；2 分钟后自动清理
// 解决：后端 list 不返回 createdTime 时，新增方案在列表里位置乱跑的问题
const recentlyCreated = new Set();
function markRecentlyCreated(idOrCode) {
  if (!idOrCode) return;
  recentlyCreated.add(idOrCode);
  setTimeout(() => recentlyCreated.delete(idOrCode), 120000);
}

// === 列表 ===
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);
const f = reactive({ keyword: '', status: '', cycleType: '', dateRange: null });

// 重置查询条件：清空所有筛选项 + 回到第 1 页后重新查询
function resetFilters() {
  f.keyword = '';
  f.status = '';
  f.cycleType = '';
  f.dateRange = null;
  pageNo.value = 1;
  reload();
}

async function reload() {
  loading.value = true;
  try {
    const raw = await listKpiRules({
      pageNo: pageNo.value, pageSize: pageSize.value,
      keyword: f.keyword || undefined,
      status: f.status || undefined,
      cycleType: f.cycleType || undefined
    });
    // raw 可能是 PageResult{records,total} 或数组（mock/unwrapPage 兼容）
    const r = Array.isArray(raw) ? raw : (raw?.records || []);
    const serverTotal = raw?.total ?? raw?.totalCount ?? r.length;
    if (r.length || serverTotal === 0) {
      const isDel = (x) => x.status === 'INACTIVE' || x.status === 'DISABLED';
      const isNew = (x) => recentlyCreated.has(x.id) || recentlyCreated.has(x.schemeCode);
      rows.value = r.slice().sort((a, b) => {
        const na = isNew(a), nb = isNew(b);
        if (na !== nb) return na ? -1 : 1;
        const da = isDel(a), db = isDel(b);
        if (da !== db) return da ? 1 : -1;
        const ta = a.createdTime || a.createTime || a.updatedTime || '';
        const tb = b.createdTime || b.createTime || b.updatedTime || '';
        if (ta || tb) return tb > ta ? 1 : (tb < ta ? -1 : 0);
        return 0;
      });
      total.value = serverTotal;
      // list 不返回 items；并发拉详情补 itemCount + 缓存到 row 上避免编辑时再拉
      Promise.all(rows.value.map(row =>
        getKpiSchemeDetail(row.id || row.schemeCode)
          .then(d => {
            row.itemCount = (d?.items || []).length;
            row._detailCache = d;
          })
          .catch(() => {})
      ));
    }
  } catch {} finally { loading.value = false; }
}

const filteredRows = computed(() => {
  if (!f.dateRange || f.dateRange.length !== 2) return rows.value;
  const [s, e] = f.dateRange;
  return rows.value.filter(r => {
    const t = r.updatedTime || r.createdTime;
    return t && t.slice(0, 10) >= s && t.slice(0, 10) <= e;
  });
});

// === 适用范围/版本（前端 localStorage 持久化，等后端字段联调） ===
const LS_KEY = 'kpiRules:meta:v1';
function loadMeta() {
  try { return JSON.parse(localStorage.getItem(LS_KEY) || '{}'); } catch { return {}; }
}
function saveMeta(meta) {
  try { localStorage.setItem(LS_KEY, JSON.stringify(meta)); } catch {}
}
function getApplyScope(row) {
  const meta = loadMeta();
  return meta[row.schemeCode]?.applyScope || row.applyScope || '';
}
function getItemFormula(schemeCode, metricCode) {
  const meta = loadMeta();
  return meta[schemeCode]?.formulas?.[metricCode] || '';
}
function persistMeta(schemeCode, applyScope, items) {
  const meta = loadMeta();
  if (!meta[schemeCode]) meta[schemeCode] = { formulas: {} };
  meta[schemeCode].applyScope = applyScope;
  meta[schemeCode].formulas = {};
  for (const it of items) {
    if (it.metricCode && it.formula) {
      meta[schemeCode].formulas[it.metricCode] = it.formula;
    }
  }
  saveMeta(meta);
}

function resolveVersion(row) {
  return 'v' + ((row.id || row.schemeCode || '').slice(-1).match(/\d/) ? Math.max(1, parseInt((row.id || '1').replace(/\D/g, '').slice(-1)) || 1) : 1);
}

// === 大弹框（合并方案 + 指标项配置） ===
const dlg = reactive({
  show: false,
  readOnly: false,
  editingId: null,
  saving: false,
  scheme: { schemeCode: '', schemeName: '', cycleType: 'YEARLY', openDetail: true, applyScope: '' },
  items: [],
  // 缓存原 items（用于增删 diff）
  origItemMap: new Map()
});
const dlgTitle = computed(() => {
  if (dlg.readOnly) return `查看KPI方案 · ${dlg.scheme.schemeName || ''}`;
  if (dlg.editingId) return `编辑KPI方案 · ${dlg.scheme.schemeName || ''}`;
  return '新增KPI方案';
});
const weightSum = computed(() => dlg.items.reduce((s, x) => s + (Number(x.weight) || 0), 0));

// === 机构树（适用范围下拉，只显示正常状态，label=机构名(机构号)） ===
const orgTreeData = ref([]);
async function loadOrgTree() {
  try {
    const tree = await getOrgTree();
    const convert = (nodes) => (Array.isArray(nodes) ? nodes : [])
      .filter(n => n.organState === 0 || n.organState == null)
      .map(n => ({
        code: n.code || n.orgCode,
        label: `${n.name || n.orgName || ''}(${n.code || n.orgCode || ''})`,
        children: convert(n.children)
      }));
    orgTreeData.value = convert(tree);
  } catch { orgTreeData.value = []; }
}

// SQL 表达式可用变量：数据日期（后端按 dataDate 注入）+ 当前 KPI 指标项配置值（权重/计分上下限/目标值/基础值）
const SQL_MACROS = [
  { token: ':dataDate', desc: '数据日期（由调度/计算传入）' },
  { token: ':weight',   desc: '权重' },
  { token: ':maxScore', desc: '计分上限' },
  { token: ':minScore', desc: '计分下限' },
  { token: ':actual',   desc: '实际值' },
  { token: ':target',   desc: '目标值' },
  { token: ':base',     desc: '基础值' }
];

// 当前聚焦的 SQL 表达式 textarea 与所属行（点击占位符时定位插入点）
let activeSqlTa = null;
let activeSqlRow = null;
/** 记录聚焦的 SQL 表达式输入框（@focus 的 target 即内部 textarea） */
function onSqlFocus(e, row) {
  activeSqlTa = e?.target || null;
  activeSqlRow = row;
}
/** 把占位符插到当前 SQL 表达式光标处；未聚焦时提示先点输入框 */
function insertMacro(token) {
  if (dlg.readOnly) return;
  if (!activeSqlRow) { ElMessage.info('请先点击要插入的 SQL 表达式输入框'); return; }
  const cur = activeSqlRow.sqlExpr || '';
  const ta = activeSqlTa;
  const start = ta?.selectionStart ?? cur.length;
  const end = ta?.selectionEnd ?? start;
  activeSqlRow.sqlExpr = cur.slice(0, start) + token + cur.slice(end);
  nextTick(() => {
    if (!ta) return;
    ta.focus();
    const pos = start + token.length;
    ta.setSelectionRange(pos, pos);
  });
}

const metricOptions = ref([]);
async function ensureMetrics() {
  if (metricOptions.value.length) return;
  try {
    const ms = await listMetrics({ pageSize: 100 });
    if (Array.isArray(ms)) metricOptions.value = ms.filter(m => m.status === 'ACTIVE');
  } catch {}
}

/** 按维度(EMP/ORG/CUST)过滤指标下拉内容并按指标名称排序；维度为空时默认按员工 */
function metricsByDim(dim) {
  const d = dim || 'EMP';
  return metricOptions.value
    .filter(m => (m.baseDim || 'EMP') === d)
    .slice()
    .sort((a, b) => (a.metricName || '').localeCompare(b.metricName || '', 'zh-Hans-CN'));
}
/** 查指标的维度（编辑回显时回填行维度，使已选指标落在过滤列表内） */
function metricBaseDim(code) {
  return metricOptions.value.find(m => m.metricCode === code)?.baseDim || 'EMP';
}
/** 切换维度时清空已选指标（指标下拉内容随维度变化，旧指标可能不在新列表中） */
function onDimChange(row) {
  row.metricCode = '';
}

function defaultItem() {
  // 默认维度为员工，指标下拉默认展示员工指标
  return { id: null, baseDim: 'EMP', metricCode: '', weight: 10, multiplier: 1, minScore: 0, maxScore: 120, formula: '', sqlExpr: '' };
}
function addItemRow() {
  dlg.items.push(defaultItem());
}

async function openCreate() {
  await ensureMetrics();
  dlg.readOnly = false;
  dlg.editingId = null;
  dlg.scheme = { schemeCode: '', schemeName: '', cycleType: 'YEARLY', openDetail: true, applyScope: '' };
  dlg.items = [defaultItem()];
  dlg.origItemMap = new Map();
  dlg.show = true;
}

async function openEdit(row, readOnly = false) {
  await ensureMetrics();
  dlg.readOnly = readOnly;
  dlg.editingId = row.id || row.schemeCode;
  dlg.scheme = {
    schemeCode: row.schemeCode || '',
    schemeName: row.schemeName || '',
    cycleType:  row.cycleType  || 'YEARLY',
    openDetail: !!row.openDetail,
    applyScope: getApplyScope(row)
  };
  // 优先用列表 reload 时已 prefetch 的 _detailCache
  let detail = row._detailCache;
  if (!detail) {
    try { detail = await getKpiSchemeDetail(row.id || row.schemeCode); } catch {}
  }
  const items = (detail?.items || row.items || []).map(it => ({
    id: it.id,
    // 维度优先用后端固化值，缺失时再按所选指标回推，保证已选指标落在过滤后的下拉列表内
    baseDim: it.baseDim || metricBaseDim(it.metricCode),
    metricCode: it.metricCode,
    // 权重入库为小数（占比，sum=1），页面按百分比展示 → 读取 ×100
    weight: Math.round((Number(it.weight) || 0) * 100),
    multiplier: Number(it.multiplier) || 1,
    minScore: Number(it.minScore) || 0,
    maxScore: Number(it.maxScore) || 120,
    // 计分公式以后端持久化值为准；留空就保持空（不再兜底默认串），由必输校验拦截
    formula: it.formula || '',
    // SQL 表达式（支持 #{slot} 占位符）：后端固化后回显，当前以返回值为准
    sqlExpr: it.sqlExpr || ''
  }));
  dlg.items = items;
  dlg.origItemMap = new Map(items.map(it => [it.id, { ...it }]));
  dlg.show = true;
}

function onDlgClosed() {
  dlg.editingId = null; dlg.readOnly = false;
}

async function onSave(targetStatus) {
  // 仅做必填校验（按要求暂不做正则/范围校验，留给后端兜底）
  if (!dlg.scheme.schemeCode) return ElMessage.warning('请填写方案编码');
  if (!dlg.scheme.schemeName) return ElMessage.warning('请填写方案名称');
  if (!dlg.items.length) return ElMessage.warning('至少添加 1 个指标');
  for (let i = 0; i < dlg.items.length; i++) {
    if (!dlg.items[i].metricCode) return ElMessage.warning(`第 ${i + 1} 行：请选择指标`);
  }
  // 走一次 el-form 的中文必填校验（schemeFormRef）
  try { await schemeFormRef.value?.validate(); } catch { return; }

  // 后端已砍 schemeCode 格式校验，仅做 trim 兜底
  if (!dlg.editingId) {
    dlg.scheme.schemeCode = String(dlg.scheme.schemeCode || '').trim();
  }

  dlg.saving = true;
  try {
    let schemeId;
    if (dlg.editingId) {
      // update 方案
      await updateKpiScheme(dlg.editingId, {
        schemeName: dlg.scheme.schemeName,
        cycleType:  dlg.scheme.cycleType,
        openDetail: dlg.scheme.openDetail
      });
      schemeId = dlg.editingId;
      // 同步 items：新增 / 更新 / 删除（按 id 比对）
      const newIds = new Set(dlg.items.filter(x => x.id).map(x => x.id));
      // 删除原有但被移除的
      for (const [oid] of dlg.origItemMap) {
        if (oid && !newIds.has(oid)) {
          try { await deleteKpiItem(schemeId, oid, '编辑移除'); } catch {}
        }
      }
      // 新增 / 更新 / 换指标
      // updateKpiItem 不能改 metricCode；已配置项若改了维度/指标 → 删旧 + 新增（指标是项的身份）
      for (const it of dlg.items) {
        const orig = it.id ? dlg.origItemMap.get(it.id) : null;
        const metricChanged = orig && orig.metricCode !== it.metricCode;
        if (it.id && !metricChanged) {
          await updateKpiItem(schemeId, it.id, {
            weight: it.weight / 100, multiplier: it.multiplier || 1,
            minScore: it.minScore, maxScore: it.maxScore,
            formula: it.formula,
            sqlExpr: it.sqlExpr
          });
        } else {
          // 已存在项换了指标：先删旧项（新指标 = 新项身份），再按新指标新增
          if (it.id && metricChanged) {
            try { await deleteKpiItem(schemeId, it.id, '编辑换指标'); } catch {}
          }
          await addKpiItem(schemeId, {
            metricCode: it.metricCode,
            baseDim: it.baseDim,
            weight: it.weight / 100, multiplier: it.multiplier || 1,
            minScore: it.minScore, maxScore: it.maxScore,
            formula: it.formula,
            sqlExpr: it.sqlExpr
          });
        }
      }
    } else {
      // 创建方案 + 逐项添加
      const created = await createKpiScheme({
        schemeCode: dlg.scheme.schemeCode,
        schemeName: dlg.scheme.schemeName,
        cycleType:  dlg.scheme.cycleType,
        openDetail: dlg.scheme.openDetail
      });
      schemeId = created?.id || dlg.scheme.schemeCode;
      // 标记为"刚创建"，下次 reload 时排在第一行
      markRecentlyCreated(schemeId);
      markRecentlyCreated(dlg.scheme.schemeCode);
      for (const it of dlg.items) {
        try {
          await addKpiItem(schemeId, {
            metricCode: it.metricCode,
            baseDim: it.baseDim,
            weight: it.weight / 100, multiplier: it.multiplier || 1,
            minScore: it.minScore, maxScore: it.maxScore,
            formula: it.formula,
            sqlExpr: it.sqlExpr
          });
        } catch {}
      }
    }
    // 发布时调 publishKpiScheme
    if (targetStatus === 'ACTIVE' && schemeId) {
      try { await publishKpiScheme(schemeId, '前端发布'); } catch (e) {
        ElMessage.warning('方案已保存但发布失败：' + (e?.message || ''));
      }
    }
    // 持久化 applyScope + 各 item formula 到 localStorage（后端字段联调前的临时方案）
    persistMeta(dlg.scheme.schemeCode, dlg.scheme.applyScope, dlg.items);
    ElMessage.success(targetStatus === 'ACTIVE' ? '已发布' : '已保存为草稿');
    dlg.show = false;
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || '未知错误'));
  } finally {
    dlg.saving = false;
    reload();
  }
}

// === 复制版本：基于现方案 items 创建一个 _vN 副本 ===
async function onCloneVersion(row) {
  const newCode = await ElMessageBox.prompt('新方案编码', '复制版本', {
    inputValue: row.schemeCode + '_v' + Date.now().toString().slice(-4),
    inputPattern: /^[A-Za-z0-9_-]{2,40}$/,
    inputErrorMessage: '只能字母/数字/下划线/横线，2-40位'
  }).catch(() => null);
  if (!newCode) return;
  try {
    const detail = await getKpiSchemeDetail(row.id || row.schemeCode);
    const items = detail?.items || row.items || [];
    const created = await createKpiScheme({
      schemeCode: newCode.value,
      schemeName: row.schemeName + '（副本）',
      cycleType:  row.cycleType,
      openDetail: !!row.openDetail
    });
    const newId = created?.id || newCode.value;
    for (const it of items) {
      try {
        await addKpiItem(newId, {
          metricCode: it.metricCode,
          baseDim: it.baseDim,
          weight: it.weight, multiplier: it.multiplier,
          minScore: it.minScore, maxScore: it.maxScore,
          formula: it.formula,
          sqlExpr: it.sqlExpr
        });
      } catch {}
    }
    ElMessage.success(`已复制为 ${newCode.value}，含 ${items.length} 个指标项`);
    reload();
  } catch {
    ElMessage.error('复制失败');
  }
}

// 启用已删除方案：等价于重新发布回 ACTIVE（后端 publish 已放开 DISABLED→ACTIVE，仍校验引用指标均启用）
async function onEnable(row) {
  try {
    await publishKpiScheme(row.id || row.schemeCode, '启用已删除方案');
    ElMessage.success('已启用');
    row.status = 'ACTIVE'; // 前端先行回显启用态
    reload();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '启用失败');
  }
}

async function onDelete(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt(`请填写删除原因`, '高危',
      { type: 'warning', inputPattern: /\S+/, inputErrorMessage: '原因必填' });
    reason = r.value;
  } catch { return; }
  try {
    await deleteKpiScheme(row.id || row.schemeCode, reason);
    ElMessage.success('删除成功');
    // 前端先行回显「已删除」状态，让按钮立即失效
    row.status = 'INACTIVE';
    reload();
  } catch (err) {
    // 后端 PERF_KPI_SCHEME_DISABLED 等 "方案已禁用" 类错误 → 视作已删除（幂等）
    const msg = err?.bizMsg || err?.message || '';
    if (/已禁用|已停用|已删除|disabled|inactive/i.test(msg)) {
      ElMessage.success('删除成功');
      row.status = 'INACTIVE';
      reload();
    } else {
      ElMessage.error(msg || '删除失败');
    }
  }
}

onMounted(() => { reload(); loadOrgTree(); });
</script>

<style lang="scss" scoped>
.table { padding: 0; padding-bottom: 12px; }
.link { color: $primary; cursor: pointer; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 16px;
  margin-bottom: 8px;
  :deep(.el-form-item) { margin-bottom: 16px; }
}

.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 10px;
  border-bottom: 1px solid $border-1;
  margin: 8px 0 14px;
  .title { font-size: 15px; font-weight: 600; flex: 1; color: $text-1; }
  .weight-sum {
    font-size: 13px; color: $text-3; font-weight: 500;
    &.ok { color: $success; font-weight: 700; }
  }
}

.formula-hint {
  margin-top: 14px; padding: 10px 14px;
  background: #ecfeff; border: 1px solid #bae6fd; border-radius: 4px;
  font-size: 13px; color: $text-2; line-height: 1.7;
  code {
    font-family: ui-monospace, monospace; background: rgba(255,255,255,.7);
    padding: 1px 6px; border-radius: 3px; color: $primary; margin: 0 2px;
  }
  .dim { color: $text-3; }
}

/* SQL 表达式展开行：标签 + 全宽文本框，单独一行 */
.sql-expr-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 4px 12px 8px;
}
.sql-expr-label {
  flex-shrink: 0;
  padding-top: 6px;
  font-size: 13px;
  color: $text-2;
  white-space: nowrap;
}
.sql-expr-row :deep(.el-textarea) { flex: 1; }

/* SQL 可用变量提示（参考指标编辑 Metrics.vue 的占位符/含义表） */
.sql-date-macros {
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
}
.sql-date-macros .hint-title { font-weight: 600; color: #303133; margin-bottom: 6px; }
.sql-date-macros .hint-table { border-collapse: collapse; width: 100%; }
.sql-date-macros .hint-table th,
.sql-date-macros .hint-table td {
  border: 1px solid #ebeef5;
  padding: 4px 8px;
  text-align: left;
  vertical-align: top;
}
.sql-date-macros .hint-table th { background: #fafafa; color: #606266; font-weight: 500; }
.sql-date-macros code { background: #fff5e6; color: #b87600; padding: 0 4px; border-radius: 2px; }
.sql-date-macros code.macro-btn {
  cursor: pointer;
  user-select: none;
  transition: background .15s, color .15s, box-shadow .15s;
}
.sql-date-macros code.macro-btn:hover { background: #ffd591; color: #874d00; box-shadow: 0 0 0 1px #fa8c16; }
.sql-date-macros code.macro-btn:active { background: #fa8c16; color: #fff; }
.sql-date-macros .hint-foot { margin-top: 8px; color: #909399; }
</style>
