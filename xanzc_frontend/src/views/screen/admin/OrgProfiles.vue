<template>
<main v-bp-overflow-tooltip class="bp-crud org-profile-page" aria-labelledby="org-profile-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="org-profile-page-title"><span class="sub">机构外部同步字段只读；以下为本地经营属性，不回写上游机构系统</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="机构经营画像操作"><el-button @click="reload">刷新</el-button></div>
    </header>

    <section class="card-section filter-bar filters" aria-label="机构经营画像筛选">
      <el-form inline size="default" class="filter-form" aria-label="机构经营画像筛选">
        <el-form-item label="机构">
          <el-input v-model="filters.keyword" clearable placeholder="机构编码或名称" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="机构性质">
          <el-select v-model="filters.orgNature" clearable placeholder="全部">
            <el-option v-for="item in NATURES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="经营等级">
          <el-select v-model="filters.operatingLevel" clearable placeholder="全部">
            <el-option v-for="item in LEVELS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="filters.city" clearable placeholder="城市编码或名称" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="缺口筛选">
          <el-select v-model="filters.gap" clearable placeholder="全部缺口">
            <el-option v-for="item in GAP_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" v-loading="loading" aria-label="机构经营画像列表" aria-describedby="org-profile-table-state">
      <div class="toolbar">
        <div>
          <h2 id="org-profile-table-heading" class="section-title">机构经营画像列表</h2>
          <p class="hint">外部同步字段保持只读；本地画像仅服务经营范围、地图和命名机构组配置。</p>
        </div>
        <p id="org-profile-table-state" class="table-state" role="status" aria-live="polite">
          {{ loadError || (loading ? '机构经营画像列表加载中' : `共 ${readiness.total} 个机构`) }}
        </p>
      </div>
      <div class="readiness-summary" aria-label="当前查询结果的机构画像缺口统计">
        <div class="readiness-item" data-testid="org-profile-total"><span>查询总数</span><strong>{{ readiness.total }}</strong></div>
        <div class="readiness-item" data-testid="org-profile-configured"><span>已配置画像</span><strong>{{ readiness.profileConfigured }}</strong></div>
        <div class="readiness-item" data-testid="org-profile-missing"><span>未配置画像</span><strong>{{ readiness.missingProfile }}</strong></div>
        <div class="readiness-item" data-testid="org-profile-missing-city"><span>城市缺失</span><strong>{{ readiness.missingCity }}</strong></div>
        <div class="readiness-item" data-testid="org-profile-located"><span>有效坐标</span><strong>{{ readiness.located }}</strong></div>
        <div class="readiness-item" data-testid="org-profile-missing-coordinates"><span>待定位</span><strong>{{ readiness.missingCoordinates }}</strong></div>
      </div>
      <div v-if="loadError" class="error-state" role="alert">
        <span>{{ loadError }}</span>
        <el-button link type="primary" @click="reload">重试</el-button>
      </div>
      <el-table :data="rows" size="default" stripe border :empty-text="loadError ? '机构画像读取失败，请点击重试' : '暂无机构画像'" aria-labelledby="org-profile-table-heading" aria-describedby="org-profile-table-state">
        <el-table-column prop="orgCode" label="机构编码" width="120" show-overflow-tooltip />
        <el-table-column prop="orgName" label="机构名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="机构性质" width="120">
          <template #default="{ row }">{{ natureLabel(row.orgNature) }}</template>
        </el-table-column>
        <el-table-column label="经营管理等级" width="130">
          <template #default="{ row }">{{ levelLabel(row.operatingLevel) }}</template>
        </el-table-column>
        <el-table-column label="归属一级经营机构" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ownerOperatingOrgName || row.ownerOperatingOrgCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="城市" width="110">
          <template #default="{ row }">{{ row.cityName || row.cityCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="GCJ-02 坐标" width="180">
          <template #default="{ row }">{{ row.lng ?? '-' }}, {{ row.lat ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="画像状态" width="96">
          <template #default="{ row }"><el-tag size="small" effect="plain" :class="statusClass(row)">{{ profileStatusLabel(row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑画像</el-button>
            <el-button link type="primary" @click="openLocation(row)">地址与定位</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <OrgLocationDialog
      v-model="locationDialog.show"
      :org="locationDialog.org"
      @saved="onLocationSaved"
    />

    <el-dialog v-model="dialog.show" class="bp-crud-dialog" title="编辑机构本地画像" width="620px">
      <div class="readonly-tip">外部机构名称、层级和状态只读；本地经营属性仅服务大屏机构组与地图。</div>
      <el-form label-width="130px" size="small">
        <el-form-item label="机构"><span>{{ dialog.form.orgName }}（{{ dialog.form.orgCode }}）</span></el-form-item>
        <el-form-item label="机构性质" required>
          <el-select v-model="dialog.form.orgNature"><el-option v-for="item in NATURES" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </el-form-item>
        <el-form-item label="经营管理等级" required>
          <el-select v-model="dialog.form.operatingLevel"><el-option v-for="item in LEVELS" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </el-form-item>
        <el-form-item v-if="dialog.form.operatingLevel === 'SUBORDINATE'" label="归属一级经营机构" required>
          <el-select v-model="dialog.form.ownerOperatingOrgCode" filterable placeholder="请选择有效 PRIMARY 机构">
            <el-option v-for="item in primaryOptions" :key="item.orgCode" :label="`${item.orgName} (${item.orgCode})`" :value="item.orgCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市编码"><el-input v-model="dialog.form.cityCode" placeholder="地图节点使用时必填" /></el-form-item>
        <el-form-item label="城市名称"><el-input v-model="dialog.form.cityName" /></el-form-item>
        <el-form-item label="经度"><el-input-number v-model="dialog.form.lng" :min="-180" :max="180" :precision="6" controls-position="right" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="dialog.form.lat" :min="-90" :max="90" :precision="6" controls-position="right" /></el-form-item>
        <el-form-item label="坐标系"><el-input model-value="GCJ-02" disabled /><span class="hint">本期固定 GCJ-02，禁止混用 WGS-84/BD-09。</span></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="dialog.form.status"><el-radio-button value="ACTIVE">启用</el-radio-button><el-radio-button value="DISABLED">停用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="口径说明"><el-input v-model="dialog.form.remark" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="dialog.form.reason" maxlength="500" placeholder="请填写本次画像调整原因" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog.show = false">取消</el-button><el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listOrgProfiles, updateOrgProfile } from '@/api/screen';
import { filterOrgProfiles } from '@/utils/screenScope';
import { analyzeOrgProfiles, profileStatusOf } from '@/utils/orgProfileReadiness';
import OrgLocationDialog from './OrgLocationDialog.vue';

const NATURES = [
  { value: 'DEPARTMENT', label: '部门' },
  { value: 'LOCAL_BRANCH', label: '本地经营机构' },
  { value: 'SECONDARY_BRANCH', label: '异地分行' },
  { value: 'OUTLET', label: '下属网点' },
  { value: 'OTHER', label: '其他' }
];
const LEVELS = [
  { value: 'PRIMARY', label: '一级经营机构' },
  { value: 'SUBORDINATE', label: '下属机构' },
  { value: 'NONE', label: '非经营机构' }
];
const GAP_OPTIONS = [
  { value: 'MISSING_PROFILE', label: '未配置画像' },
  { value: 'UNKNOWN_STATUS', label: '状态未知' },
  { value: 'MISSING_CITY', label: '城市缺失' },
  { value: 'MISSING_COORDINATES', label: '待定位' },
  { value: 'LOCATED', label: '已定位' }
];
const queryRows = ref([]);
const loading = ref(false);
const loadError = ref('');
const filters = reactive({ keyword: '', orgNature: '', operatingLevel: '', city: '', gap: '' });
const dialog = reactive({ show: false, saving: false, form: {} });
const locationDialog = reactive({ show: false, org: null });
let requestSequence = 0;
let mounted = false;

function gapMatches(entry, gap) {
  if (!gap) return true;
  if (gap === 'MISSING_PROFILE') return entry.profileStatus === 'UNCONFIGURED';
  if (gap === 'UNKNOWN_STATUS') return entry.profileStatus === 'UNKNOWN';
  if (gap === 'MISSING_CITY') return !entry.hasCity;
  if (gap === 'MISSING_COORDINATES') return !entry.located;
  if (gap === 'LOCATED') return entry.located;
  return true;
}

const rows = computed(() => {
  const filtered = filterOrgProfiles(queryRows.value, filters);
  if (!filters.gap) return filtered;
  const entries = analyzeOrgProfiles(filtered).entries;
  return filtered.filter((row, index) => gapMatches(entries[index], filters.gap));
});
const readiness = computed(() => analyzeOrgProfiles(rows.value));

// 一级候选来自当前查询结果，不随 gap 视图筛选消失，保证编辑下属机构时仍可选择有效归属。
const primaryOptions = computed(() => filterOrgProfiles(queryRows.value, filters)
  .filter(row => row.operatingLevel === 'PRIMARY' && active(row)));
function profileStatus(row) { return profileStatusOf(row); }
function active(row) { return profileStatus(row) === 'ACTIVE'; }
function profileStatusLabel(row) {
  return { ACTIVE: '启用', DISABLED: '停用', UNCONFIGURED: '未配置', UNKNOWN: '状态未知' }[profileStatus(row)] || '状态未知';
}
function statusClass(row) {
  return { ACTIVE: 'tag-success', DISABLED: 'tag-info', UNCONFIGURED: 'tag-warning', UNKNOWN: 'tag-warning' }[profileStatus(row)] || 'tag-warning';
}
function natureLabel(v) { return NATURES.find(x => x.value === v)?.label || v || '-'; }
function levelLabel(v) { return LEVELS.find(x => x.value === v)?.label || v || '-'; }
function optionalQueryText(value) {
  const normalized = String(value || '').trim();
  return normalized || undefined;
}

/** 还原全部筛选条件后重新查询，不修改后端参数语义。 */
function resetFilters() {
  filters.keyword = '';
  filters.orgNature = '';
  filters.operatingLevel = '';
  filters.city = '';
  filters.gap = '';
  reload();
}

async function reload() {
  const currentRequest = ++requestSequence;
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listOrgProfiles({
      // keyword 只表达机构编码/名称；城市必须独立落在 city 参数，避免改变后端筛选口径。
      keyword: optionalQueryText(filters.keyword),
      orgNature: filters.orgNature || undefined,
      operatingLevel: filters.operatingLevel || undefined,
      city: optionalQueryText(filters.city)
    });
    const returned = Array.isArray(result)
      ? result
      : (Array.isArray(result?.records) ? result.records
        : (Array.isArray(result?.list) ? result.list : null));
    if (!returned) throw new Error('机构经营画像返回数据格式无效');
    if (!mounted || currentRequest !== requestSequence) return;
    queryRows.value = returned;
  } catch {
    if (!mounted || currentRequest !== requestSequence) return;
    queryRows.value = [];
    loadError.value = '机构经营画像列表加载失败，请刷新重试';
  } finally {
    if (mounted && currentRequest === requestSequence) loading.value = false;
  }
}
function openEdit(row) {
  const rowStatus = profileStatus(row);
  dialog.form = {
    orgCode: row.orgCode, orgName: row.orgName,
    orgNature: row.orgNature || 'OTHER', operatingLevel: row.operatingLevel || 'NONE',
    ownerOperatingOrgCode: row.ownerOperatingOrgCode || '', cityCode: row.cityCode || '', cityName: row.cityName || '',
    lng: row.lng ?? null, lat: row.lat ?? null, status: rowStatus === 'ACTIVE' ? 'ACTIVE' : (rowStatus === 'DISABLED' ? 'DISABLED' : 'DISABLED'),
    remark: row.remark || '', version: row.version, reason: ''
  };
  dialog.show = true;
}
function openLocation(row) {
  locationDialog.org = row ? { ...row } : null;
  locationDialog.show = true;
}
async function onLocationSaved() {
  await reload();
}
async function save() {
  if (!dialog.form.orgCode) return;
  if (dialog.form.operatingLevel === 'SUBORDINATE' && !dialog.form.ownerOperatingOrgCode) {
    ElMessage.warning('下属机构必须填写归属一级经营机构'); return;
  }
  if ((dialog.form.lng != null || dialog.form.lat != null) && (dialog.form.lng == null || dialog.form.lat == null)) {
    ElMessage.warning('经纬度必须同时填写'); return;
  }
  if (!String(dialog.form.reason || '').trim()) {
    ElMessage.warning('变更原因必填'); return;
  }
  dialog.saving = true;
  try {
    await updateOrgProfile(dialog.form.orgCode, {
      orgNature: dialog.form.orgNature, operatingLevel: dialog.form.operatingLevel,
      ownerOperatingOrgCode: dialog.form.operatingLevel === 'SUBORDINATE' ? dialog.form.ownerOperatingOrgCode : null,
      cityCode: dialog.form.cityCode || null, cityName: dialog.form.cityName || null,
      lng: dialog.form.lng, lat: dialog.form.lat, coordSys: 'GCJ02', status: dialog.form.status,
      remark: dialog.form.remark, version: dialog.form.version, reason: dialog.form.reason.trim()
    });
    dialog.show = false; ElMessage.success('画像已保存'); await reload();
  } finally { dialog.saving = false; }
}
onMounted(() => { mounted = true; reload(); });
onUnmounted(() => { mounted = false; requestSequence += 1; });
</script>

<style scoped>
.filters { padding-bottom: var(--space-3); }
.readonly-tip { color: var(--color-text-muted); font-size: 12px; margin-bottom: var(--space-3); }
.hint { display: block; color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.readiness-summary { display: grid; grid-template-columns: repeat(6, minmax(96px, 1fr)); gap: var(--space-2); margin-bottom: var(--space-3); }
.readiness-item { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-2); min-height: 42px; padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-control); background: var(--color-surface-soft); color: var(--color-text-muted); font-size: 12px; }
.readiness-item strong { color: var(--color-text-strong); font-size: 18px; font-weight: 600; }
.error-state { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3); border: 1px solid var(--color-danger-fg); border-radius: var(--radius-control); color: var(--color-danger-fg); background: var(--color-danger-bg); }
@media (max-width: 1100px) { .readiness-summary { grid-template-columns: repeat(3, minmax(120px, 1fr)); } }
</style>
