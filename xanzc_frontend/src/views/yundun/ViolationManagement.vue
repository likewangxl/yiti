<template>
  <div class="violation-page bp-crud" v-bp-overflow-tooltip>
    <div class="page-h">
      <div>
        <PageTitle />
        <span class="desc">{{ config.subtitle }}</span>
      </div>
    </div>

    <section class="filter-panel card-section" :style="filterLayoutStyle">
      <el-form label-position="left" :label-width="config.filterLabelWidth || '156px'" class="filter-form" @submit.prevent>
        <div class="filter-grid">
          <FilterControl
            v-for="field in visibleFields"
            :key="field.key"
            :field="field"
            :model="filters"
            @enter="onSearch"
          />
        </div>
      </el-form>

      <div class="filter-actions">
        <el-button type="primary" :loading="loading" @click="onSearch">搜索</el-button>
        <el-button @click="onReset">重置</el-button>
        <el-button type="primary" @click="openCreate">新增</el-button>
        <el-button type="danger" plain :disabled="!selection.length" @click="onBatchDelete">删除</el-button>
        <el-button :loading="importing" @click="openImport">导入</el-button>
        <el-button :loading="exporting" @click="onExport">导出</el-button>
      </div>

      <button class="expand-btn" type="button" @click="expanded = !expanded">
        {{ expanded ? '收起' : '展开全部搜索项' }}
        <el-icon><ArrowUp v-if="expanded" /><ArrowDown v-else /></el-icon>
      </button>
    </section>

    <section class="card-section table-panel">
      <el-table
        :data="tableRows"
        border
        stripe
        row-key="id"
        empty-text="暂无数据"
        v-loading="loading"
        :span-method="tableSpanMethod"
        @selection-change="selection = $event"
      >
        <el-table-column type="selection" width="48" fixed="left" reserve-selection />
        <el-table-column label="序号" prop="__sequence" width="64" fixed="left" align="center">
          <template #default="{ row }">
            {{ (pager.pageNo - 1) * pager.pageSize + row.__sequence }}
          </template>
        </el-table-column>
        <el-table-column
          v-for="column in config.columns"
          :key="column.key"
          :label="column.displayLabel || column.label"
          :prop="column.key"
          :width="column.width"
          :align="column.align || 'left'"
          :show-overflow-tooltip="column.overflow"
        >
          <template v-if="column.labelTooltip" #header>
            <el-tooltip :content="column.label" placement="top">
              <span class="label-with-tooltip">{{ column.displayLabel || column.label }}</span>
            </el-tooltip>
          </template>
          <template v-if="column.format" #default="{ row }">
            {{ formatColumnValue(column, row[column.key]) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="112" fixed="right" align="center" class-name="operation-cell">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pager.pageNo"
          v-model:page-size="pager.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialog.show" :title="dialogTitle" width="min(1040px, 92vw)" top="5vh"
      :close-on-click-modal="false" destroy-on-close>
      <el-form ref="recordFormRef" :model="dialog.form" :rules="dialogRules"
        label-position="top" class="record-form" :hide-required-asterisk="true">
        <div class="record-grid">
          <el-form-item v-for="field in visibleFormFields" :key="field.key"
            :label="field.label" :prop="field.key">
            <template v-if="field.labelTooltip" #label>
              <el-tooltip :content="field.label" placement="top">
                <span class="label-with-tooltip">{{ field.displayLabel || field.label }}</span>
              </el-tooltip>
            </template>
            <el-select v-if="field.type === 'select'" v-model="dialog.form[field.key]" filterable clearable
              :disabled="dialog.mode === 'view'" :placeholder="formFieldPlaceholder(field)" style="width:100%">
              <el-option v-for="option in field.options" :key="String(option.value)"
                :label="option.label" :value="option.value" />
            </el-select>
            <el-date-picker v-else-if="field.type === 'datetime'" v-model="dialog.form[field.key]"
              type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="formFieldPlaceholder(field)"
              :disabled="dialog.mode === 'view'" style="width:100%" />
            <el-date-picker v-else-if="field.type === 'date'" v-model="dialog.form[field.key]"
              type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" :placeholder="formFieldPlaceholder(field)"
              :disabled="dialog.mode === 'view'" style="width:100%" />
            <el-input-number v-else-if="field.type === 'number'" v-model="dialog.form[field.key]"
              :precision="4" :controls="false" :disabled="dialog.mode === 'view'"
              :placeholder="formFieldPlaceholder(field)" style="width:100%" />
            <el-input v-else v-model="dialog.form[field.key]" :type="field.inputType || 'text'"
              :rows="3" clearable :disabled="dialog.mode === 'view'"
              :placeholder="formFieldPlaceholder(field)" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog.show = false">{{ dialog.mode === 'view' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="dialog.mode !== 'view'" type="primary" :loading="dialog.saving" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialog.show" :title="`导入${config.title}`" width="min(720px, 92vw)"
      :close-on-click-modal="false" destroy-on-close @closed="resetImportDialog">
      <div class="import-toolbar">
        <div>
          <strong>{{ config.title }}</strong>
          <span>请先下载当前业务类型的导入模板，按模板填写后上传。</span>
        </div>
        <el-button type="primary" plain :loading="importDialog.downloading" @click="downloadTemplate">
          下载导入模板
        </el-button>
      </div>
      <el-upload
        ref="importUploadRef"
        v-model:file-list="importDialog.fileList"
        class="excel-upload"
        drag
        action="#"
        accept=".xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :on-change="onImportFileChange"
        :on-remove="onImportFileRemove"
        :on-exceed="onImportFileExceed"
      >
        <div class="upload-copy">将 Excel 文件拖到此处，或 <em>点击选择文件</em></div>
        <template #tip><div class="el-upload__tip">仅支持 xlsx/xls 格式文件。</div></template>
      </el-upload>
      <el-form label-position="top" class="import-reason-form">
        <el-form-item label="导入原因" required>
          <el-input v-model="importDialog.reason" type="textarea" :rows="3" maxlength="200"
            show-word-limit placeholder="请输入导入原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialog.show = false">取消</el-button>
        <el-button type="primary" :loading="importDialog.submitting"
          :disabled="!importDialog.file || !importDialog.reason.trim() || importDialog.submitting"
          @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue';
import { ElButton, ElDatePicker, ElFormItem, ElIcon, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElSelect, ElTooltip } from 'element-plus';
import {
  batchDeleteViolations,
  createViolation,
  downloadViolationImportTemplate,
  exportViolations,
  getViolation,
  importViolations,
  listViolations,
  updateViolation
} from '@/api/yundun';
import {
  buildViolationFormData,
  buildViolationFormRules,
  buildViolationSavePayload,
  buildViolationTableRows,
  resolveNumberRangePrecision,
  trimTrailingDecimalZeros,
  violationConfigs
} from './violationConfig';

const route = useRoute();
const config = computed(() => violationConfigs[route.meta.violationKind] || violationConfigs.accountability);
const filters = reactive({});
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);
const importing = ref(false);
const expanded = ref(false);
const selection = ref([]);
const pager = reactive({ pageNo: 1, pageSize: 20 });
const dialog = reactive({ show: false, saving: false, mode: 'create', id: null, form: {} });
const importDialog = reactive({
  show: false,
  downloading: false,
  submitting: false,
  file: null,
  fileList: [],
  reason: ''
});
let detailRequestToken = 0;
const recordFormRef = ref();
const importUploadRef = ref();
const dialogRules = computed(() => buildViolationFormRules(config.value, dialog.mode));
const tableRows = computed(() => buildViolationTableRows(config.value, rows.value));
const visibleFormFields = computed(() => config.value.formFields.filter(field => !field.hidden));

const visibleFields = computed(() => expanded.value
  ? config.value.searchFields
  : config.value.searchFields.filter(field => field.quick));
const filterLayoutStyle = computed(() => ({
  '--filter-columns': config.value.filterColumns || 3,
  '--filter-column-min-width': config.value.filterColumnMinWidth || '260px',
  '--filter-label-font-size': config.value.filterLabelFontSize || '14px',
  '--action-columns': config.value.actionColumns || 3,
  '--action-area-width': config.value.actionColumns === 2 ? '184px' : '268px'
}));
const dialogTitle = computed(() => `${dialog.mode === 'create' ? '新增' : dialog.mode === 'edit' ? '编辑' : '查看'}${config.value.title}`);

function formFieldPlaceholder(field) {
  if (dialog.mode === 'view') return '';
  if (field.type === 'select') return '请选择';
  if (field.type === 'datetime') return '请选择时间';
  if (field.type === 'date') return '请选择日期';
  if (field.type === 'number') return '';
  return `请输入${field.label}`;
}

const FilterControl = defineComponent({
  name: 'FilterControl',
  props: { field: { type: Object, required: true }, model: { type: Object, required: true } },
  emits: ['enter'],
  setup(props, { emit }) {
    return () => {
      const field = props.field;
      let control;
      if (field.type === 'select') {
        control = h(ElSelect, {
          modelValue: props.model[field.key],
          'onUpdate:modelValue': value => { props.model[field.key] = value; },
          clearable: true,
          filterable: true,
          placeholder: '请选择',
          style: 'width:100%'
        }, () => field.options.map(option => h(ElOption, {
          key: String(option.value), label: option.label, value: option.value
        })));
      } else if (field.type === 'datetime-range') {
        control = h(ElDatePicker, {
          modelValue: props.model[field.key],
          'onUpdate:modelValue': value => { props.model[field.key] = value; },
          type: 'datetimerange',
          valueFormat: 'YYYY-MM-DDTHH:mm:ss',
          rangeSeparator: '至',
          startPlaceholder: field.startPlaceholder ?? '开始时间',
          endPlaceholder: field.endPlaceholder ?? '结束时间',
          style: 'width:100%'
        });
      } else if (field.type === 'number-range') {
        control = h('div', { class: 'number-range' }, [
          h(ElInputNumber, {
            modelValue: props.model[field.minKey],
            'onUpdate:modelValue': value => { props.model[field.minKey] = value; },
            controls: false, placeholder: field.minPlaceholder ?? '最小值',
            precision: resolveNumberRangePrecision(field),
            class: 'number-range__input', style: { width: '100%', minWidth: 0 }
          }),
          h('span', { class: 'number-range__separator' }, '—'),
          h(ElInputNumber, {
            modelValue: props.model[field.maxKey],
            'onUpdate:modelValue': value => { props.model[field.maxKey] = value; },
            controls: false, placeholder: field.maxPlaceholder ?? '最大值',
            precision: resolveNumberRangePrecision(field),
            class: 'number-range__input', style: { width: '100%', minWidth: 0 }
          })
        ]);
      } else {
        control = h(ElInput, {
          modelValue: props.model[field.key],
          'onUpdate:modelValue': value => { props.model[field.key] = value; },
          clearable: true,
          placeholder: field.searchPlaceholder || `请输入${field.label}`,
          onKeyup: event => { if (event.key === 'Enter') emit('enter'); }
        });
      }
      const slots = { default: () => control };
      if (field.labelTooltip) {
        slots.label = () => h(ElTooltip, { content: field.label, placement: 'top' }, () => h(
          'span', { class: 'label-with-tooltip' }, field.displayLabel || field.label
        ));
      }
      return h(ElFormItem, { label: field.label, class: 'filter-item' }, slots);
    };
  }
});

function queryParams() {
  const params = {};
  for (const field of config.value.searchFields) {
    if (field.type === 'datetime-range') {
      const range = filters[field.key];
      if (Array.isArray(range)) {
        params[field.startKey] = range[0];
        params[field.endKey] = range[1];
      }
    } else if (field.type === 'number-range') {
      if (filters[field.minKey] !== null && filters[field.minKey] !== undefined) params[field.minKey] = filters[field.minKey];
      if (filters[field.maxKey] !== null && filters[field.maxKey] !== undefined) params[field.maxKey] = filters[field.maxKey];
    } else if (filters[field.key] !== '' && filters[field.key] !== null && filters[field.key] !== undefined) {
      params[field.key] = filters[field.key];
    }
  }
  return params;
}

async function load() {
  loading.value = true;
  try {
    const result = await listViolations(config.value.kind, { ...queryParams(), ...pager });
    rows.value = result?.records || [];
    total.value = result?.total || 0;
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  for (const field of config.value.searchFields) {
    filters[field.key] = undefined;
    if (field.minKey) filters[field.minKey] = undefined;
    if (field.maxKey) filters[field.maxKey] = undefined;
  }
}

function onSearch() { pager.pageNo = 1; load(); }
function onReset() { resetFilters(); pager.pageNo = 1; load(); }
function onSizeChange() { pager.pageNo = 1; load(); }
function formatDateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-'; }
function formatColumnValue(column, value) {
  if (column.format === 'datetime') return formatDateTime(value);
  if (column.format === 'trim-decimal-zeros') return trimTrailingDecimalZeros(value);
  return value;
}

function tableSpanMethod({ row, column }) {
  const mergeColumns = config.value.mergeColumns || [];
  if (!mergeColumns.includes(column.property)) return [1, 1];
  return row.__customerRowSpan > 0 ? [row.__customerRowSpan, 1] : [0, 0];
}

function emptyForm() {
  return buildViolationFormData(config.value);
}

function openCreate() {
  ++detailRequestToken;
  dialog.mode = 'create';
  dialog.id = null;
  dialog.form = emptyForm();
  dialog.show = true;
}

async function openWithMode(row, mode) {
  const requestToken = ++detailRequestToken;
  dialog.mode = mode;
  dialog.id = row.id;
  dialog.form = emptyForm();
  dialog.show = false;
  try {
    const detail = await getViolation(config.value.kind, row.id);
    if (requestToken !== detailRequestToken) return;
    if (!detail || typeof detail !== 'object' || Array.isArray(detail) || detail.id == null) {
      throw new Error('获取详情失败，请稍后重试');
    }
    dialog.form = buildViolationFormData(config.value, detail);
    dialog.show = true;
  } catch (error) {
    if (requestToken !== detailRequestToken) return;
    dialog.show = false;
    ElMessage.error(error?.message || '获取详情失败，请稍后重试');
  }
}
function openEdit(row) { openWithMode(row, 'edit'); }
function openDetail(row) { openWithMode(row, 'view'); }

async function saveRecord() {
  try {
    await recordFormRef.value?.validate();
  } catch {
    return;
  }
  dialog.saving = true;
  try {
    const payload = buildViolationSavePayload(config.value, dialog.form);
    if (dialog.mode === 'edit') await updateViolation(config.value.kind, dialog.id, payload);
    else await createViolation(config.value.kind, payload);
    ElMessage.success('保存成功');
    dialog.show = false;
    load();
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    dialog.saving = false;
  }
}

async function onBatchDelete() {
  if (!selection.value.length) return;
  let reason;
  try {
    const result = await ElMessageBox.prompt(`将删除选中的 ${selection.value.length} 条记录，请填写删除原因。`, '删除确认', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', inputPlaceholder: '请输入删除原因',
      inputValidator: value => Boolean(value?.trim()) || '删除原因不能为空', type: 'warning'
    });
    reason = result.value.trim();
  } catch { return; }
  await batchDeleteViolations(config.value.kind, selection.value.map(item => item.id), reason);
  ElMessage.success('删除成功');
  selection.value = [];
  load();
}

async function onExport() {
  exporting.value = true;
  try {
    const ids = selection.value.map(item => item.id);
    const params = ids.length ? { ids } : queryParams();
    await exportViolations(config.value.kind, params, config.value.exportName);
    ElMessage.success(ids.length ? `已导出选中 ${ids.length} 条记录` : '导出成功');
  } finally {
    exporting.value = false;
  }
}

function clearImportDialog() {
  importDialog.file = null;
  importDialog.fileList = [];
  importDialog.reason = '';
}

function resetImportDialog() {
  clearImportDialog();
  importDialog.downloading = false;
  importDialog.submitting = false;
}

function openImport() {
  resetImportDialog();
  importDialog.show = true;
}

function onImportFileChange(uploadFile, uploadFiles = []) {
  if (!uploadFile?.raw) return;
  const filename = String(uploadFile.name || '').toLowerCase();
  if (!filename.endsWith('.xlsx') && !filename.endsWith('.xls')) {
    ElMessage.error('仅支持 xlsx/xls 格式文件');
    clearImportDialog();
    return;
  }
  importDialog.file = uploadFile.raw;
  importDialog.fileList = uploadFiles.slice(-1);
}

function onImportFileRemove() {
  clearImportDialog();
}

function onImportFileExceed() {
  ElMessage.warning('每次只能上传一个文件，请先移除已选文件');
}

function saveBlob(blob, filename) {
  if (!blob) return;
  const data = blob instanceof Blob ? blob : new Blob([blob]);
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  setTimeout(() => {
    URL.revokeObjectURL(url);
    link.remove();
  }, 0);
}

async function downloadTemplate() {
  importDialog.downloading = true;
  try {
    const blob = await downloadViolationImportTemplate(config.value.kind);
    saveBlob(blob, `${config.value.exportName}导入模板.xlsx`);
  } finally {
    importDialog.downloading = false;
  }
}

async function submitImport() {
  if (!importDialog.file || !importDialog.reason.trim()) {
    ElMessage.warning(!importDialog.file ? '请先选择 Excel 文件' : '请输入导入原因');
    return;
  }
  importing.value = true;
  importDialog.submitting = true;
  try {
    const count = await importViolations(config.value.kind, importDialog.file, importDialog.reason.trim());
    ElMessage.success(`成功导入 ${count} 条记录`);
    importDialog.show = false;
    await load();
  } finally {
    importDialog.submitting = false;
    importing.value = false;
  }
}

watch(() => config.value.kind, () => {
  detailRequestToken += 1;
  dialog.show = false;
  expanded.value = false;
  selection.value = [];
  resetFilters();
  pager.pageNo = 1;
  load();
}, { immediate: true });
</script>

<style scoped lang="scss">
.violation-page { min-width: 860px; }
.page-h > div { display:flex; align-items:baseline; }
.filter-panel { position:relative; padding:18px 20px 44px; }
.filter-form { padding-right:var(--action-area-width, 268px); }
.filter-grid { display:grid; grid-template-columns:repeat(var(--filter-columns, 3), minmax(var(--filter-column-min-width, 260px), 1fr)); gap:14px 24px; }
.filter-item { margin-bottom:0; min-width:0; }
.filter-item :deep(.el-form-item__label) { color:#4b5563; font-size:var(--filter-label-font-size, 14px); line-height:34px; }
.filter-item :deep(.el-form-item__content) { min-width:0; }
.filter-grid :deep(.number-range) { display:grid; grid-template-columns:minmax(0, 1fr) 12px minmax(0, 1fr); align-items:center; width:100%; gap:4px; color:#9ca3af; }
.filter-grid :deep(.number-range__input) { width:100%; min-width:0; }
.filter-grid :deep(.number-range__separator) { text-align:center; white-space:nowrap; }
.filter-actions { position:absolute; top:18px; right:20px; display:grid; grid-template-columns:repeat(var(--action-columns, 3), 76px); gap:8px; }
.filter-actions :deep(.el-button) { margin-left:0; width:76px; }
.expand-btn { position:absolute; left:50%; bottom:12px; display:flex; align-items:center; gap:5px; border:0; color:#4b5563; background:transparent; cursor:pointer; transform:translateX(-50%); }
.expand-btn:hover { color:#1f5b8f; }
.table-panel { padding:0; overflow:hidden; }
.table-panel :deep(.el-table th.el-table__cell) { background:#f7f9fc; color:#3f4a5a; font-weight:600; }
.table-panel :deep(.el-table .cell) { line-height:20px; }
.pager { display:flex; justify-content:flex-end; padding:14px 18px; border-top:1px solid #ebeef5; }
.record-form { max-height:68vh; overflow:auto; padding-right:8px; }
.record-grid { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:2px 18px; }
.record-grid :deep(.el-form-item) { min-width:0; }
.record-grid :deep(.el-form-item__label) { line-height:20px; margin-bottom:7px; color:#4b5563; }
.import-toolbar { display:flex; align-items:center; justify-content:space-between; gap:18px; margin:2px 0 16px; }
.import-toolbar strong { display:block; margin-bottom:4px; }
.import-toolbar span { color:#909399; font-size:12px; }
.excel-upload { width:100%; }
.excel-upload :deep(.el-upload), .excel-upload :deep(.el-upload-dragger) { width:100%; }
.upload-copy { color:#606266; }
.upload-copy em { color:var(--el-color-primary); font-style:normal; }
.import-reason-form { margin-top:18px; }
@media (max-width:1200px) {
  .filter-grid { grid-template-columns:repeat(2, minmax(280px, 1fr)); }
  .record-grid { grid-template-columns:repeat(2, minmax(0, 1fr)); }
}
</style>
