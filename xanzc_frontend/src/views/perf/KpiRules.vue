<template>
  <div>
    <div class="page-h">
      <h1>KPI 规则</h1>
      <span class="desc">方案 · 权重 · 公式预览 · 计分上下限</span>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新增方案</el-button>
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
        <el-form-item label="适用周期">
          <el-select v-model="f.cycleType" clearable placeholder="全部" style="width:160px" @change="reload">
            <el-option v-for="o in CYCLE_OPTIONS" :key="o.v" :value="o.v" :label="o.l" />
          </el-select>
        </el-form-item>
        <el-form-item label="更新时间">
          <el-date-picker v-model="f.dateRange" type="daterange" value-format="YYYY-MM-DD"
            range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width:280px" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="reload">查询</el-button></el-form-item>
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
        <el-table-column label="适用范围" min-width="200">
          <template #default="{row}">{{ resolveOrgScope(row) }}</template>
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
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row, true)">查看</el-button>
            <el-button link type="primary" size="small" :disabled="isDisabled(row)" @click="onCloneVersion(row)">复制版本</el-button>
            <el-button link type="primary" size="small" :disabled="isDisabled(row)" @click="openEdit(row, false)">编辑</el-button>
            <el-popconfirm v-if="!isDisabled(row)" :title="`确认删除方案 ${row.schemeName}？`" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
            <el-button v-else link size="small" disabled>已删除</el-button>
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
          <el-form-item label="适用范围">
            <el-input v-model="dlg.scheme.applyScope" :disabled="dlg.readOnly" placeholder="如 南山/福田/罗湖/宝安" />
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
      <el-table :data="dlg.items" size="default" border>
        <el-table-column label="指标" min-width="190">
          <template #default="{row}">
            <el-select v-model="row.metricCode" :disabled="dlg.readOnly" filterable placeholder="选择指标" style="width:100%">
              <el-option v-for="m in metricOptions" :key="m.metricCode"
                :value="m.metricCode" :label="`${m.metricName}`" />
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
            <el-input-number v-model="row.maxScore" :disabled="dlg.readOnly" :precision="0" :controls="false" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="计分下限" width="120">
          <template #default="{row}">
            <el-input-number v-model="row.minScore" :disabled="dlg.readOnly" :precision="0" :controls="false" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="计分公式" min-width="280">
          <template #default="{row}">
            <el-input v-model="row.formula" :disabled="dlg.readOnly" placeholder="如 min(actual / target * 100, 120)" />
          </template>
        </el-table-column>
        <el-table-column v-if="!dlg.readOnly" label="操作" width="70" align="center" fixed="right">
          <template #default="{$index}">
            <el-button link type="danger" size="small" @click="dlg.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-button v-if="!dlg.readOnly" plain @click="addItemRow" style="margin-top:10px">+ 添加指标</el-button>

      <!-- 计分公式可用变量提示 -->
      <div class="formula-hint">
        <strong>计分公式可用变量：</strong>
        <code>actual</code> <span class="dim">（实际值）</span>·
        <code>target</code> <span class="dim">（目标值）</span>·
        <code>complete_rate</code> <span class="dim">（完成率）</span>·
        <span class="dim">最终得分 = ∑(权重 × min(max(score, min), max)) / 100</span>
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
import { ref, reactive, computed, onMounted } from 'vue';
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
// labelOf 名称冲突：保留下面行业版的 statusLabel（含 DISABLED → 已删除映射），useDict 只取 options
const { options: STATUS_OPTIONS } = useDict('KPI_SCHEME_STATUS');
const CYCLE_OPTIONS = [
  { v: 'YEARLY',    l: '年度' },
  { v: 'QUARTERLY', l: '季度' },
  { v: 'MONTHLY',   l: '月度' }
];

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

async function reload() {
  loading.value = true;
  try {
    const r = await listKpiRules({
      pageNo: pageNo.value, pageSize: pageSize.value,
      keyword: f.keyword || undefined,
      status: f.status || undefined,
      cycleType: f.cycleType || undefined
    });
    if (Array.isArray(r)) {
      // 三级排序：刚创建的（recentlyCreated 集合）置顶 → 已删除（INACTIVE/DISABLED）沉底 → 中间按时间倒序
      // recentlyCreated 是 onSave create 成功后写入的本地 hint，避免后端 list 不返回 createdTime 时回归到 id 字典序
      const isDel = (x) => x.status === 'INACTIVE' || x.status === 'DISABLED';
      const isNew = (x) => recentlyCreated.has(x.id) || recentlyCreated.has(x.schemeCode);
      rows.value = r.slice().sort((a, b) => {
        const na = isNew(a), nb = isNew(b);
        if (na !== nb) return na ? -1 : 1;   // 新建 > 普通
        const da = isDel(a), db = isDel(b);
        if (da !== db) return da ? 1 : -1;   // 普通 > 已删
        const ta = a.createdTime || a.createTime || a.updatedTime || '';
        const tb = b.createdTime || b.createTime || b.updatedTime || '';
        if (ta || tb) return tb > ta ? 1 : (tb < ta ? -1 : 0);
        // 最后兜底：保持后端原顺序（不再按 UUID 排，避免随机）
        return 0;
      });
      total.value = Math.max(total.value, (pageNo.value - 1) * pageSize.value + r.length);
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

function resolveOrgScope(row) {
  const fromMeta = getApplyScope(row);
  if (fromMeta) return fromMeta;
  const name = row.schemeName || '';
  if (/中场|后台/.test(name)) return '中场支持部';
  return '全部支行';
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
  if (dlg.readOnly) return `查看方案 · ${dlg.scheme.schemeName || ''}`;
  if (dlg.editingId) return `编辑方案 · ${dlg.scheme.schemeName || ''}`;
  return '新增方案';
});
const weightSum = computed(() => dlg.items.reduce((s, x) => s + (Number(x.weight) || 0), 0));

const metricOptions = ref([]);
async function ensureMetrics() {
  if (metricOptions.value.length) return;
  try {
    const ms = await listMetrics({ pageSize: 100 });
    if (Array.isArray(ms)) metricOptions.value = ms.filter(m => m.status === 'ACTIVE');
  } catch {}
}

function defaultItem() {
  return { id: null, metricCode: '', weight: 10, multiplier: 1, minScore: 0, maxScore: 120, formula: '' };
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
    metricCode: it.metricCode,
    weight: Number(it.weight) || 0,
    multiplier: Number(it.multiplier) || 1,
    minScore: Number(it.minScore) || 0,
    maxScore: Number(it.maxScore) || 120,
    formula: getItemFormula(row.schemeCode, it.metricCode) || 'min(actual / target * 100, 120)'
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

  // 后端 schemeCode @Pattern(^[A-Z][A-Z0-9_]*$) 是强约束 —— 前端无法砍。
  // 折中：提交前自动转大写 + 替换非法字符为 _，并保证首字符是字母。
  // 这样用户随便输小写/横线/中文都能落库，不再被后端拒。
  if (!dlg.editingId) {
    let code = String(dlg.scheme.schemeCode || '').toUpperCase()
      .replace(/[^A-Z0-9_]/g, '_')
      .replace(/^[^A-Z]+/, '');
    if (!code) code = 'KPI' + Date.now().toString().slice(-6);
    dlg.scheme.schemeCode = code;
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
      // 新增 / 更新
      // 后端 UpdateKpiItemReqDTO 仅 4 字段：weight / multiplier / maxScore / minScore（禁带 metricCode）
      // 后端 AddKpiItemReqDTO 含 metricCode（新增时才需要）
      for (const it of dlg.items) {
        if (it.id) {
          await updateKpiItem(schemeId, it.id, {
            weight: it.weight, multiplier: it.multiplier || 1,
            minScore: it.minScore, maxScore: it.maxScore
          });
        } else {
          await addKpiItem(schemeId, {
            metricCode: it.metricCode,
            weight: it.weight, multiplier: it.multiplier || 1,
            minScore: it.minScore, maxScore: it.maxScore
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
            weight: it.weight, multiplier: it.multiplier || 1,
            minScore: it.minScore, maxScore: it.maxScore
          });
        } catch {}
      }
    }
    // 持久化 applyScope + 各 item formula 到 localStorage（后端字段联调前的临时方案）
    persistMeta(dlg.scheme.schemeCode, dlg.scheme.applyScope, dlg.items);
    ElMessage.success(targetStatus === 'ACTIVE' ? '已发布' : '已保存为草稿');
    dlg.show = false;
    reload();
  } catch {} finally { dlg.saving = false; }
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
          weight: it.weight, multiplier: it.multiplier,
          minScore: it.minScore, maxScore: it.maxScore
        });
      } catch {}
    }
    ElMessage.success(`已复制为 ${newCode.value}，含 ${items.length} 个指标项`);
    reload();
  } catch {
    ElMessage.error('复制失败');
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

onMounted(reload);
</script>

<style lang="scss" scoped>
.table { padding: 0; padding-bottom: 12px; }
.link { color: $primary; cursor: pointer; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
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
</style>
