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
          {{ loading ? '机构经营画像列表加载中' : (rows.length ? `共 ${rows.length} 个机构` : '暂无机构画像') }}
        </p>
      </div>
      <el-table :data="rows" size="default" stripe border empty-text="暂无机构画像" aria-labelledby="org-profile-table-heading" aria-describedby="org-profile-table-state">
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
          <template #default="{ row }"><el-tag size="small" effect="plain" :class="active(row) ? 'tag-success' : 'tag-info'">{{ active(row) ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="96" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑画像</el-button></template>
        </el-table-column>
      </el-table>
    </section>

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
        <el-form-item label="状态"><el-radio-group v-model="dialog.form.status"><el-radio-button label="ACTIVE">启用</el-radio-button><el-radio-button label="DISABLED">停用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="口径说明"><el-input v-model="dialog.form.remark" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="dialog.form.reason" maxlength="500" placeholder="请填写本次画像调整原因" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog.show = false">取消</el-button><el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listOrgProfiles, updateOrgProfile } from '@/api/screen';
import { filterOrgProfiles } from '@/utils/screenScope';

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
const rows = ref([]);
const loading = ref(false);
const filters = reactive({ keyword: '', orgNature: '', operatingLevel: '', city: '' });
const dialog = reactive({ show: false, saving: false, form: {} });

const primaryOptions = computed(() => rows.value.filter(row => row.operatingLevel === 'PRIMARY' && active(row)));
function active(row) { return row.status === undefined || row.status === null || row.status === 'ACTIVE' || row.status === 0 || row.status === '0'; }
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
  reload();
}

async function reload() {
  loading.value = true;
  try {
    const result = await listOrgProfiles({
      // keyword 只表达机构编码/名称；城市必须独立落在 city 参数，避免改变后端筛选口径。
      keyword: optionalQueryText(filters.keyword),
      orgNature: filters.orgNature || undefined,
      operatingLevel: filters.operatingLevel || undefined,
      city: optionalQueryText(filters.city)
    });
    const returned = Array.isArray(result) ? result : (result?.records || []);
    rows.value = filterOrgProfiles(returned, filters);
  } catch { rows.value = []; }
  finally { loading.value = false; }
}
function openEdit(row) {
  dialog.form = {
    orgCode: row.orgCode, orgName: row.orgName,
    orgNature: row.orgNature || 'OTHER', operatingLevel: row.operatingLevel || 'NONE',
    ownerOperatingOrgCode: row.ownerOperatingOrgCode || '', cityCode: row.cityCode || '', cityName: row.cityName || '',
    lng: row.lng ?? null, lat: row.lat ?? null, status: row.status === 0 || row.status === '0' ? 'ACTIVE' : (row.status || 'ACTIVE'),
    remark: row.remark || '', version: row.version, reason: ''
  };
  dialog.show = true;
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
onMounted(reload);
</script>

<style scoped>
.filters { padding-bottom: var(--space-3); }
.readonly-tip { color: var(--color-text-muted); font-size: 12px; margin-bottom: var(--space-3); }
.hint { display: block; color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
</style>
