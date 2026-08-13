<template>
<main v-bp-overflow-tooltip class="bp-crud guarantee-query" aria-labelledby="guarantee-query-title">
    <div class="page-h">
      <PageTitle id="guarantee-query-title" />
      <div class="actions action-group" role="group" aria-label="担保信息操作">
        <el-button :loading="exporting" aria-label="导出担保信息" @click="onExport">导出</el-button>
        <el-button type="primary" @click="openCreate">新增</el-button>
        <el-button type="danger" :disabled="!selection.length" @click="onBatchDelete">
          删除{{ selection.length ? `（${selection.length}）` : '（已选 0 条）' }}
        </el-button>
      </div>
    </div>

    <!-- 查询栏：客户名称 + 查询/重置 同一行 -->
    <section class="card-section filter-bar" aria-label="担保信息筛选">
      <el-form class="filter-form" inline size="default" aria-label="担保信息筛选" @submit.prevent>
        <el-form-item label="客户名称">
          <el-input v-model="f.clientName" clearable aria-label="按客户名称筛选" placeholder="请输入客户名称"
            style="width:240px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 数据列表 -->
    <section class="card-section data-panel" aria-label="担保信息列表" aria-describedby="guarantee-table-state" :aria-busy="loading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 id="guarantee-table-heading" class="section-title">担保信息列表</h2>
          <p class="hint">查看担保额度、经办人及最近变更时间。</p>
        </div>
        <p id="guarantee-table-state" class="table-state" role="status" aria-live="polite">
          {{ errorMessage || (loading ? '担保信息列表加载中' : rows.length ? `共 ${total} 条记录` : '暂无担保信息') }}
        </p>
      </div>
      <div v-if="errorMessage" class="error-state" role="alert">
        <span>{{ errorMessage }}</span>
        <el-button link type="primary" @click="load">重试</el-button>
      </div>
      <el-table :data="rows" size="default" empty-text="暂无担保信息" v-loading="loading"
                aria-labelledby="guarantee-table-heading" aria-describedby="guarantee-table-state"
                @selection-change="onSelectionChange" row-key="id">
        <el-table-column type="selection" width="45" reserve-selection />
        <el-table-column label="序号" type="index" width="64"
          :index="i => (pager.pageNo - 1) * pager.pageSize + i + 1" />
        <el-table-column label="客户名称" prop="clientName" min-width="200" />
        <el-table-column label="业务额度（万元）" prop="notionalAmount" min-width="160" align="right" />
        <el-table-column label="剩余额度（万元）" prop="occupyNotionalAmount" min-width="160" align="right" />
        <el-table-column label="融资额度（万元）" prop="usableNominalSum" min-width="160" align="right" />
        <el-table-column label="授信到期日" prop="lastExpire" min-width="120" />
        <el-table-column label="经办人" min-width="130">
          <template #default="{ row }">
            <div>{{ row.userDisplayName || row.userName || '-' }}</div>
            <div v-if="row.userName" class="sub-id">{{ row.userName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="数据变动日期" prop="createTime" min-width="170" />
        <el-table-column label="变更日期" prop="updateTime" min-width="170" />
        <el-table-column label="操作" class-name="operation-cell" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
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

    <!-- 新增 / 编辑 弹窗 -->
    <el-dialog v-model="dlg.show" class="bp-crud-dialog" :title="dlg.editId ? '编辑担保信息' : '新增担保信息'"
               width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="150px">
        <el-form-item label="客户名称" prop="clientName">
          <el-input v-model="dlg.form.clientName" maxlength="100" placeholder="请输入客户名称"
            :disabled="!!dlg.editId" />
        </el-form-item>
        <el-form-item label="业务额度（万元）" prop="notionalAmount">
          <el-input v-model="dlg.form.notionalAmount" maxlength="50" placeholder="请输入业务额度" />
        </el-form-item>
        <el-form-item label="剩余额度（万元）" prop="occupyNotionalAmount">
          <el-input v-model="dlg.form.occupyNotionalAmount" maxlength="20" placeholder="请输入剩余额度" />
        </el-form-item>
        <el-form-item label="融资额度（万元）" prop="usableNominalSum">
          <el-input v-model="dlg.form.usableNominalSum" maxlength="30" placeholder="请输入融资额度" />
        </el-form-item>
        <el-form-item label="授信到期日" prop="lastExpire">
          <el-date-picker v-model="dlg.form.lastExpire" type="date" value-format="YYYY-MM-DD"
            placeholder="选择授信到期日" style="width:100%" />
        </el-form-item>
        <el-form-item label="经办人工号" prop="userName">
          <el-input v-model="dlg.form.userName" maxlength="20" placeholder="请输入员工工号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listGuarantees, getGuarantee, createGuarantee,
  updateGuarantee, batchDeleteGuarantees, exportGuarantees
} from '@/api/guarantee';

const f = reactive({ clientName: '' });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref('');
const exporting = ref(false);
const selection = ref([]);
const pager = reactive({ pageNo: 1, pageSize: 20 });

const formRef = ref();
const emptyForm = () => ({
  clientName: '', notionalAmount: '', occupyNotionalAmount: '',
  usableNominalSum: '', lastExpire: '', userName: ''
});
const dlg = reactive({ show: false, saving: false, editId: null, form: emptyForm() });
// 金额类型校验：非负数字，最多两位小数（非空交由 required 规则处理）
// 容忍货币符号 ¥ 与千分位 ,（后端入库前会统一清洗，见 front_insert_replace 口径）
function amountValidator(label) {
  return (rule, value, callback) => {
    const v = (value == null ? '' : String(value)).replace(/[¥,]/g, '').trim();
    if (v === '') return callback();
    if (!/^\d+(\.\d{1,2})?$/.test(v)) {
      return callback(new Error(`${label}需为非负数字，最多两位小数`));
    }
    callback();
  };
}

const rules = {
  clientName:        [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  notionalAmount:      [{ required: true, message: '请输入业务额度', trigger: 'blur' },
                      { validator: amountValidator('业务额度'), trigger: 'blur' }],
  occupyNotionalAmount: [{ required: true, message: '请输入剩余额度', trigger: 'blur' },
                      { validator: amountValidator('剩余额度'), trigger: 'blur' }],
  usableNominalSum:    [{ required: true, message: '请输入融资额度', trigger: 'blur' },
                      { validator: amountValidator('融资额度'), trigger: 'blur' }],
  lastExpire:        [{ required: true, message: '请选择授信到期日', trigger: 'change' }],
  userName:          [{ required: true, message: '请输入员工工号', trigger: 'blur' }]
};

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const r = await listGuarantees({
      clientName: f.clientName || undefined,
      pageNo: pager.pageNo,
      pageSize: pager.pageSize
    });
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } catch (e) {
    rows.value = []; total.value = 0;
    errorMessage.value = '担保信息加载失败，请重试';
  } finally {
    loading.value = false;
  }
}

function onSearch() { pager.pageNo = 1; load(); }
function onReset() { f.clientName = ''; pager.pageNo = 1; load(); }
function onSizeChange() { pager.pageNo = 1; load(); }
function onSelectionChange(val) { selection.value = val; }

function openCreate() {
  dlg.editId = null;
  dlg.form = emptyForm();
  dlg.show = true;
  formRef.value?.clearValidate?.();
}

async function openEdit(row) {
  dlg.editId = row.id;
  // 先用列表行填充，再请求详情反显最新数据
  dlg.form = {
    clientName: row.clientName || '', notionalAmount: row.notionalAmount || '',
    occupyNotionalAmount: row.occupyNotionalAmount || '', usableNominalSum: row.usableNominalSum || '',
    lastExpire: row.lastExpire || '', userName: row.userName || ''
  };
  dlg.show = true;
  formRef.value?.clearValidate?.();
  try {
    const d = await getGuarantee(row.id);
    if (d && d.id) {
      dlg.form = {
        clientName: d.clientName || '', notionalAmount: d.notionalAmount || '',
        occupyNotionalAmount: d.occupyNotionalAmount || '', usableNominalSum: d.usableNominalSum || '',
        lastExpire: d.lastExpire || '', userName: d.userName || ''
      };
    }
  } catch { /* 反显失败保留列表行数据 */ }
}

async function onSave() {
  try {
    await formRef.value.validate();
  } catch { return; }
  dlg.saving = true;
  try {
    if (dlg.editId) {
      await updateGuarantee(dlg.editId, dlg.form);
      ElMessage.success('保存成功');
    } else {
      const r = await createGuarantee(dlg.form);
      // 命中合同表 → 按合同批量导入（忽略表单录入）；否则落库表单单条
      if (r && r.source === 'CONTRACT') {
        ElMessage.success(`已按合同导入 ${r.count} 条记录`);
      } else {
        ElMessage.success('新增成功');
      }
    }
    dlg.show = false;
    load();
  } catch (e) {
    // 后端「客户已存在」(PORTAL-40906) 等业务错误直接透出 message
    ElMessage.error(e?.message || '保存失败');
  } finally {
    dlg.saving = false;
  }
}

async function onBatchDelete() {
  if (!selection.value.length) return;
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selection.value.length} 条担保信息？删除后不可恢复。`,
      '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' });
  } catch { return; }
  try {
    const ids = selection.value.map(r => r.id);
    await batchDeleteGuarantees(ids);
    ElMessage.success('删除成功');
    selection.value = [];
    load();
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e));
  }
}

async function onExport() {
  exporting.value = true;
  try {
    const ids = selection.value.map(r => r.id);
    await exportGuarantees(ids.length ? { ids } : { clientName: f.clientName || undefined });
    ElMessage.success(ids.length ? `已导出选中 ${ids.length} 条` : '已导出');
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || e));
  } finally {
    exporting.value = false;
  }
}

load();
</script>

<style lang="scss" scoped>
.guarantee-query {
  min-width: 0;
}
.guarantee-query :deep(.sub-id) {
  color: var(--color-text-muted);
  font-size: 12px;
}
.error-state {
  align-items: center;
  background: var(--color-danger-bg);
  border: 1px solid var(--color-border);
  color: var(--color-danger-fg);
  display: flex;
  gap: var(--space-3);
  justify-content: space-between;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
