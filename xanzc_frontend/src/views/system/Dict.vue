<template>
  <div>
    <div class="page-h">
      <h1>字典管理</h1>
      <div class="actions">
        <el-button @click="loadItems(picked)">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新增字典项</el-button>
      </div>
    </div>

    <div class="layout">
      <div class="card-section types">
        <div class="hh">字典类型 ({{ types.length }})</div>
        <div v-for="t in types" :key="dictKey(t)"
             :class="['ti', { active: dictKey(t) === picked }]"
             @click="picked = dictKey(t)">
          <div class="t1">{{ dictLabel(t) }}</div>
          <div class="t2">{{ dictKey(t) }} · {{ t.itemCount ?? 0 }} 项</div>
        </div>
      </div>

      <div class="card-section items">
        <el-table :data="rows" size="default" empty-text="暂无字典项" v-loading="loading">
          <el-table-column label="编码" width="160">
            <template #default="{row}"><code class="mono">{{ row.dictCode || row.code }}</code></template>
          </el-table-column>
          <el-table-column label="标签" min-width="180">
            <template #default="{row}">{{ row.dictLabel || row.label }}</template>
          </el-table-column>
          <el-table-column label="值" min-width="160">
            <template #default="{row}">{{ row.dictValue || row.value }}</template>
          </el-table-column>
          <el-table-column label="排序" width="80" align="center">
            <template #default="{row}">{{ row.sortOrder ?? row.sort }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{row}">
              <el-tag :class="isActive(row) ? 'tag-success' : 'tag-warning'" effect="plain">
                {{ isActive(row) ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="onToggle(row)">
                {{ isActive(row) ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 新增 / 编辑 弹框（截图 183） -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑字典项' : '新增字典项'" width="480px"
      :close-on-click-modal="false" @closed="onDlgClosed">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlgRules" label-position="top" size="default">
        <el-form-item label="编码" prop="code" required>
          <el-input v-model="dlg.form.code" :disabled="!!dlg.editing" placeholder="如 IND008" />
        </el-form-item>
        <el-form-item label="标签" prop="label" required>
          <el-input v-model="dlg.form.label" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="dlg.form.value" placeholder="可选，默认与编码一致" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dlg.form.sort" :min="0" :max="9999" :precision="0" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dlg.form.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { sysDictTypes, sysDictItems } from '@/mock';
import { listDictTypes, listDictItems } from '@/api/system';
import { call } from '@/api/http';

const dictKey   = (t) => t.dictType ?? t.code;
const dictLabel = (t) => t.dictTypeLabel ?? t.remark ?? t.label ?? t.dictType ?? t.code;
const isActive  = (r) => r.status === 'ACTIVE' || r.status === 0 || r.status === '启用';

const types = ref(sysDictTypes);
const picked = ref(dictKey(sysDictTypes[0] || {}));
const rows = ref(sysDictItems[picked.value] || []);
const loading = ref(false);

async function loadItems(t) {
  if (!t) return;
  loading.value = true;
  try {
    const r = await listDictItems(t);
    if (Array.isArray(r)) rows.value = r;
  } catch {} finally { loading.value = false; }
}
onMounted(async () => {
  try {
    const r = await listDictTypes();
    if (Array.isArray(r) && r.length) {
      types.value = r;
      picked.value = dictKey(r[0]);
    }
  } catch {}
  loadItems(picked.value);
});
watch(picked, loadItems);

// === 新增 / 编辑 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, saving: false, editing: null,
  form: { code: '', label: '', value: '', sort: 8, status: 'ACTIVE' }
});
const dlgRules = {
  code:  [{ required: true, message: '编码必填' }],
  label: [{ required: true, message: '标签必填' }]
};

function openCreate() {
  dlg.editing = null;
  Object.assign(dlg.form, { code: '', label: '', value: '', sort: 8, status: 'ACTIVE' });
  dlg.show = true;
}
function openEdit(row) {
  dlg.editing = row;
  Object.assign(dlg.form, {
    code:  row.dictCode  ?? row.code,
    label: row.dictLabel ?? row.label,
    value: row.dictValue ?? row.value ?? '',
    sort:  Number(row.sortOrder ?? row.sort ?? 0),
    status: isActive(row) ? 'ACTIVE' : 'DISABLED'
  });
  dlg.show = true;
}
function onDlgClosed() { dlg.editing = null; }

async function onSave() {
  try { await dlgFormRef.value.validate(); } catch { return; }
  dlg.saving = true;
  const payload = { ...dlg.form, dictType: picked.value };
  try {
    if (dlg.editing) {
      // 后端 DictUpdateReqDTO 只接受 dictLabel/dictValue/sortOrder/remark
      const { dictLabel, dictValue, sortOrder, remark } = {
        dictLabel: payload.label, dictValue: payload.value,
        sortOrder: payload.sort, remark: payload.remark || ''
      };
      await call('put', `/admin/sys/dicts/${dlg.editing.id}`, {
        data: { dictLabel, dictValue, sortOrder, remark }
      }, { ok: true });
    } else {
      // 后端 DictCreateReqDTO 需要 dictType/dictCode/dictLabel/dictValue/sortOrder
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
    ElMessage.success(dlg.editing ? '已更新' : '已新增');
    dlg.show = false;
    // 前端先行回显（后端真接口刷新后会覆盖）
    if (dlg.editing) {
      const idx = rows.value.findIndex(r => (r.dictCode ?? r.code) === payload.code);
      if (idx >= 0) {
        rows.value[idx] = { ...rows.value[idx],
          dictLabel: payload.label, label: payload.label,
          dictValue: payload.value, value: payload.value,
          sortOrder: payload.sort, sort: payload.sort,
          status: payload.status
        };
      }
    } else {
      rows.value.push({
        dictCode: payload.code, code: payload.code,
        dictLabel: payload.label, label: payload.label,
        dictValue: payload.value || payload.code, value: payload.value || payload.code,
        sortOrder: payload.sort, sort: payload.sort,
        status: payload.status
      });
    }
    loadItems(picked.value);
  } catch { ElMessage.error('保存失败'); } finally { dlg.saving = false; }
}

async function onToggle(row) {
  const willDisable = isActive(row);
  try {
    await ElMessageBox.confirm(
      `确认${willDisable ? '禁用' : '启用'}字典项 ${row.dictLabel || row.label}？`,
      willDisable ? '禁用' : '启用',
      { type: willDisable ? 'warning' : 'info' }
    );
  } catch { return; }
  const code = row.dictCode || row.code;
  const next = willDisable ? 'DISABLED' : 'ACTIVE';
  try {
    await call('put', `/admin/sys/dicts/${row.id}/status`, { data: { status: next } }, { ok: true });
    row.status = next;
    ElMessage.success(willDisable ? '已禁用' : '已启用');
  } catch { ElMessage.error('操作失败'); }
}
</script>

<style lang="scss" scoped>
.layout { display: grid; grid-template-columns: 220px 1fr; gap: 12px; }
.types { padding: 0; max-height: calc(100vh - 140px); overflow-y: auto;
  .hh { padding: 12px 14px; border-bottom: 1px solid $border-1; font-weight: 500; font-size: 13px; position: sticky; top: 0; background: #fff; z-index: 1; }
  .ti { padding: 10px 14px; cursor: pointer; font-size: 12.5px;
    &:hover { background: $bg-soft; }
    &.active { background: $primary-100; color: $primary; }
    .t1 { font-family: ui-monospace, monospace; font-size: 12px; font-weight: 500; }
    .t2 { color: $text-3; font-size: 11px; margin-top: 2px; }
    &.active .t2 { color: $primary-400; }
  }
}
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.items { padding: 0; }
</style>
