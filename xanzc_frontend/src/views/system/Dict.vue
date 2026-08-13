<template>
  <main class="bp-crud dict-page" aria-labelledby="dict-page-title" :aria-busy="typesLoading || loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="dict-page-title"><span class="sub">按类型维护统一字典项；状态变更会立即影响引用该字典的业务页面。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="字典管理操作">
        <el-button @click="refreshCurrent">刷新</el-button>
        <el-button type="primary" :disabled="!picked" @click="openCreate">新增字典项</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="字典筛选">
      <el-form class="filter-form" inline size="default" aria-label="字典筛选条件">
        <el-form-item label="字典类型">
          <el-input v-model="typeKeyword" clearable placeholder="类型编码 / 名称" aria-label="按字典类型筛选" style="width:230px" />
        </el-form-item>
        <el-form-item label="字典项">
          <el-input v-model="itemKeyword" clearable placeholder="编码 / 标签 / 值" aria-label="按字典项筛选" style="width:230px" />
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">清除筛选</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="dict-workspace" aria-label="字典主从工作区">
      <aside class="card-section data-panel dict-types" aria-labelledby="dict-types-heading" :aria-busy="typesLoading ? 'true' : 'false'">
        <div class="toolbar compact-toolbar">
          <div>
            <h2 id="dict-types-heading" class="section-title">字典类型</h2>
            <p class="hint">先选择类型，再维护右侧字典项。</p>
          </div>
          <p class="table-state" role="status" aria-live="polite">{{ typesLoading ? '加载中' : `共 ${filteredTypes.length} 个` }}</p>
        </div>
        <p v-if="typesError" class="error-state" role="alert">{{ typesError }} <el-button link type="primary" @click="loadTypes">重试</el-button></p>
        <nav class="type-list" aria-labelledby="dict-types-heading">
          <button
            v-for="type in filteredTypes"
            :key="dictKey(type)"
            type="button"
            :class="['type-option', { active: dictKey(type) === picked }]"
            :aria-current="dictKey(type) === picked ? 'true' : undefined"
            @click="picked = dictKey(type)"
          >
            <span class="type-label">{{ dictLabel(type) }}</span>
            <span class="type-meta"><code>{{ dictKey(type) }}</code><span>{{ type.itemCount ?? 0 }} 项</span></span>
          </button>
          <p v-if="!typesLoading && !filteredTypes.length" class="empty-state">暂无字典类型</p>
        </nav>
      </aside>

      <section
        class="card-section data-panel dict-items"
        aria-labelledby="dict-items-heading"
        aria-describedby="dict-items-state"
        :aria-busy="loading ? 'true' : 'false'"
      >
        <div class="toolbar">
          <div>
            <h2 id="dict-items-heading" class="section-title">{{ picked ? `${picked} 字典项` : '字典项' }}</h2>
            <p class="hint">编码创建后不可修改；禁用前请确认不会影响在用业务规则。</p>
          </div>
          <p id="dict-items-state" class="table-state" role="status" aria-live="polite">
            {{ loading ? '字典项加载中' : filteredRows.length ? `显示 ${filteredRows.length} 项` : '暂无字典项' }}
          </p>
        </div>
        <p v-if="itemsError" class="error-state" role="alert">{{ itemsError }} <el-button link type="primary" @click="loadItems(picked)">重试</el-button></p>
        <el-table :data="filteredRows" size="default" empty-text="暂无字典项" v-loading="loading" aria-labelledby="dict-items-heading" aria-describedby="dict-items-state">
          <el-table-column label="编码" width="180"><template #default="{ row }"><code class="mono">{{ row.dictCode || row.code }}</code></template></el-table-column>
          <el-table-column label="标签" min-width="200"><template #default="{ row }">{{ row.dictLabel || row.label }}</template></el-table-column>
          <el-table-column label="值" min-width="180"><template #default="{ row }">{{ row.dictValue || row.value }}</template></el-table-column>
          <el-table-column label="排序" width="90" align="right"><template #default="{ row }">{{ row.sortOrder ?? row.sort }}</template></el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :class="isActive(row) ? 'tag-success' : 'tag-warning'" effect="plain">{{ isActive(row) ? '启用' : '禁用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="row-actions" role="group" :aria-label="`${row.dictLabel || row.label} 操作`">
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" size="small" :loading="isToggling(row)" :disabled="isToggling(row)" @click="onToggle(row)">{{ isActive(row) ? '禁用' : '启用' }}</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </section>

    <el-dialog v-model="dlg.show" class="bp-crud-dialog" :title="dlg.editing ? '编辑字典项' : '新增字典项'" width="480px" :close-on-click-modal="false" @closed="onDlgClosed">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlgRules" label-position="top" size="default">
        <el-form-item label="编码" prop="code" required><el-input v-model="dlg.form.code" :disabled="!!dlg.editing" placeholder="如 IND008" /></el-form-item>
        <el-form-item label="标签" prop="label" required><el-input v-model="dlg.form.label" placeholder="显示名称" /></el-form-item>
        <el-form-item label="值"><el-input v-model="dlg.form.value" placeholder="可选，默认与编码一致" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dlg.form.sort" :min="0" :max="9999" :precision="0" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="dlg.form.status" style="width:100%"><el-option label="启用" value="ACTIVE" /><el-option label="禁用" value="DISABLED" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="dlg.saving" @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" :disabled="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listDictTypes, listDictItems } from '@/api/system';
import { call } from '@/api/http';

const dictKey = (type) => type.dictType ?? type.code;
const dictLabel = (type) => type.dictTypeLabel ?? type.remark ?? type.label ?? type.dictType ?? type.code;
const isActive = (row) => row.status === 'ACTIVE' || row.status === 0 || row.status === '启用';

const types = ref([]);
const picked = ref('');
const rows = ref([]);
const loading = ref(false);
const typesLoading = ref(false);
const typesError = ref('');
const itemsError = ref('');
const typeKeyword = ref('');
const itemKeyword = ref('');
const filteredTypes = computed(() => {
  const query = typeKeyword.value.trim().toLowerCase();
  return !query ? types.value : types.value.filter(type => [dictKey(type), dictLabel(type)]
    .some(value => String(value || '').toLowerCase().includes(query)));
});
const filteredRows = computed(() => {
  const query = itemKeyword.value.trim().toLowerCase();
  return !query ? rows.value : rows.value.filter(row => [row.dictCode || row.code, row.dictLabel || row.label, row.dictValue || row.value]
    .some(value => String(value || '').toLowerCase().includes(query)));
});

async function loadTypes() {
  typesLoading.value = true;
  typesError.value = '';
  try {
    const result = await listDictTypes();
    types.value = Array.isArray(result) ? result : [];
    if (!types.value.some(type => dictKey(type) === picked.value)) picked.value = dictKey(types.value[0] || {});
  } catch (error) {
    types.value = [];
    picked.value = '';
    typesError.value = `字典类型加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    typesLoading.value = false;
  }
}
async function loadItems(type) {
  if (!type) {
    rows.value = [];
    return;
  }
  loading.value = true;
  itemsError.value = '';
  try {
    const result = await listDictItems(type);
    rows.value = Array.isArray(result) ? result : [];
  } catch (error) {
    rows.value = [];
    itemsError.value = `字典项加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}
function refreshCurrent() {
  loadTypes();
  loadItems(picked.value);
}
function resetFilters() {
  typeKeyword.value = '';
  itemKeyword.value = '';
}

const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, saving: false, editing: null,
  form: { code: '', label: '', value: '', sort: 8, status: 'ACTIVE' }
});
const dlgRules = { code: [{ required: true, message: '编码必填' }], label: [{ required: true, message: '标签必填' }] };
function openCreate() {
  if (!picked.value) return;
  dlg.editing = null;
  Object.assign(dlg.form, { code: '', label: '', value: '', sort: 8, status: 'ACTIVE' });
  dlg.show = true;
}
function openEdit(row) {
  dlg.editing = row;
  Object.assign(dlg.form, {
    code: row.dictCode ?? row.code,
    label: row.dictLabel ?? row.label,
    value: row.dictValue ?? row.value ?? '',
    sort: Number(row.sortOrder ?? row.sort ?? 0),
    status: isActive(row) ? 'ACTIVE' : 'DISABLED'
  });
  dlg.show = true;
}
function onDlgClosed() {
  dlg.editing = null;
}
async function onSave() {
  if (dlg.saving) return;
  try {
    await dlgFormRef.value?.validate();
  } catch {
    return;
  }
  dlg.saving = true;
  const payload = { ...dlg.form, dictType: picked.value };
  try {
    if (dlg.editing) {
      await call('put', `/admin/sys/dicts/${dlg.editing.id}`, {
        data: {
          dictLabel: payload.label,
          dictValue: payload.value,
          sortOrder: payload.sort,
          remark: payload.remark || ''
        }
      }, { ok: true });
    } else {
      await call('post', '/admin/sys/dicts', {
        data: {
          dictType: picked.value,
          dictCode: payload.code,
          dictLabel: payload.label,
          dictValue: payload.value || payload.code,
          sortOrder: payload.sort || 0,
          remark: payload.remark || ''
        }
      }, { ok: true });
    }
    ElMessage.success(dlg.editing ? '字典项已更新' : '字典项已新增');
    dlg.show = false;
    await loadItems(picked.value);
  } catch (error) {
    ElMessage.error(`保存失败：${error?.message || error}`);
  } finally {
    dlg.saving = false;
  }
}

const togglingIds = ref(new Set());
const rowId = (row) => String(row.id ?? row.dictCode ?? row.code);
const isToggling = (row) => togglingIds.value.has(rowId(row));
function setToggling(row, value) {
  const next = new Set(togglingIds.value);
  if (value) next.add(rowId(row));
  else next.delete(rowId(row));
  togglingIds.value = next;
}
async function onToggle(row) {
  if (isToggling(row)) return;
  const willDisable = isActive(row);
  try {
    await ElMessageBox.confirm(
      `确认${willDisable ? '禁用' : '启用'}字典项 ${row.dictLabel || row.label}？`,
      willDisable ? '确认禁用' : '确认启用',
      { type: willDisable ? 'warning' : 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  setToggling(row, true);
  const status = willDisable ? 'DISABLED' : 'ACTIVE';
  try {
    await call('put', `/admin/sys/dicts/${row.id}/status`, { data: { status } }, { ok: true });
    row.status = status;
    ElMessage.success(willDisable ? '字典项已禁用' : '字典项已启用');
  } catch (error) {
    ElMessage.error(`操作失败：${error?.message || error}`);
  } finally {
    setToggling(row, false);
  }
}

watch(picked, (type) => { loadItems(type); });
onMounted(loadTypes);
</script>

<style lang="scss" scoped>
.dict-workspace { display: grid; gap: var(--space-4); grid-template-columns: minmax(260px, .72fr) minmax(0, 1.8fr); min-height: min(620px, calc(100vh - 270px)); }
.dict-types,
.dict-items { min-width: 0; }
.dict-types { display: flex; flex-direction: column; overflow: hidden; }
.type-list { flex: 1; margin: 0 calc(var(--space-4) * -1) calc(var(--space-4) * -1); overflow: auto; }
.type-option {
  background: transparent;
  border: 0;
  border-left: 3px solid transparent;
  color: var(--color-text-strong);
  cursor: pointer;
  display: grid;
  font: inherit;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4);
  text-align: left;
  width: 100%;
}
.type-option:hover { background: var(--color-surface-soft); }
.type-option.active { background: var(--color-brand-100); border-left-color: var(--color-brand-700); }
.type-label { font-weight: 600; }
.type-meta { color: var(--color-text-muted); display: flex; font-size: 12px; gap: var(--space-2); justify-content: space-between; }
.type-meta code,
.mono { font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; }
.row-actions { display: flex; gap: var(--space-1); }
.empty-state { color: var(--color-text-muted); font-size: 12px; padding: var(--space-6) var(--space-4); text-align: center; }
.error-state {
  background: var(--color-danger-bg);
  border-left: 3px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  font-size: 12px;
  line-height: 18px;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
