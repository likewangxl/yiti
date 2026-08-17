<template>
<main v-bp-overflow-tooltip class="bp-crud pl-page" aria-labelledby="product-lib-title">
    <header class="page-h">
      <PageTitle id="product-lib-title"><span class="sub">中后台组织维护，产品负责人由通讯录反向关联</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="产品资料库操作">
        <el-button type="primary" @click="openCreate">新增产品</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="产品资料库筛选">
      <el-form class="filter-form" :inline="true" aria-label="产品资料库筛选">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            aria-label="按产品名称或代码筛选"
            placeholder="产品名称 / 代码"
            clearable
            style="width:200px"
            @keyup.enter="reload"
            @clear="reload"
          />
        </el-form-item>
        <el-form-item label="产品部门">
          <el-tree-select
            v-model="filters.productDeptOrgCode"
            aria-label="按产品部门筛选"
            :data="orgTree"
            check-strictly
            clearable
            :props="{ label: 'name', value: 'code', children: 'children' }"
            placeholder="全部"
            style="width:200px"
            @change="reload"
          />
        </el-form-item>
        <el-form-item label="中场支持">
          <el-select v-model="filters.supportForSupportRequest" aria-label="按中场支持筛选" placeholder="全部" clearable style="width:120px" @change="reload">
            <el-option :value="true" label="是" />
            <el-option :value="false" label="否" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" aria-label="按产品状态筛选" placeholder="全部" clearable style="width:120px" @change="reload">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel"
      aria-label="产品资料库列表"
      aria-describedby="product-lib-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="product-lib-heading" class="section-title">产品资料库</h2>
          <p class="hint">附件可下载；负责人信息由通讯录中的负责产品关联维护。</p>
        </div>
        <p id="product-lib-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '产品资料库加载中' : rows.length ? `共 ${total} 个产品` : '暂无产品数据' }}
        </p>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        empty-text="暂无产品数据"
        aria-labelledby="product-lib-heading"
        aria-describedby="product-lib-state"
      >
        <el-table-column type="index" label="序号" width="60" align="center" :index="indexMethod" />
        <el-table-column prop="productDeptOrgName" label="产品部门" width="190" class-name="compact-clamp-cell" />
        <el-table-column prop="productName" label="产品名称" width="200" class-name="compact-clamp-cell" />
        <el-table-column prop="description" label="产品说明" min-width="270" class-name="compact-clamp-cell" />
        <el-table-column label="中场支持" width="100" align="center">
          <template #default="{row}">
            <el-tag :class="row.supportForSupportRequest ? 'tag-success' : 'tag-info'" effect="plain">
              {{ row.supportForSupportRequest ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responsibleEmpNames" label="产品负责人" min-width="140" show-overflow-tooltip>
          <template #default="{row}">{{ row.responsibleEmpNames || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :class="row.status === 'ACTIVE' ? 'tag-success' : 'tag-danger'" effect="plain">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{row}">{{ fmtDate(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="180" fixed="right">
          <template #default="{row}">
            <BpAdaptiveRowActions>
              <template #primary><el-button link type="primary" size="small" :disabled="deletingId === row.id" @click="openEdit(row)">编辑</el-button></template>
              <template #expanded>
                <el-button link type="primary" size="small" :disabled="!row.fileObjectId || deletingId === row.id" :title="row.fileObjectId ? (row.fileName || '下载附件') : '暂无附件'" @click="downloadAttach(row)">附件</el-button>
                <el-button link type="danger" size="small" :disabled="deletingId === row.id" @click="confirmDelete(row)">删除</el-button>
              </template>
              <template #compact>
                <el-dropdown trigger="click" popper-class="bp-crud-menu">
                  <el-button link size="small" :disabled="deletingId === row.id" aria-label="更多产品资料操作">更多</el-button>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item :disabled="!row.fileObjectId || deletingId === row.id" :title="row.fileObjectId ? (row.fileName || '下载附件') : '暂无附件'" @click="downloadAttach(row)">附件</el-dropdown-item>
                    <el-dropdown-item divided class="danger-item" :disabled="deletingId === row.id" @click="confirmDelete(row)">删除</el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>
      <nav class="pager" aria-label="产品资料库分页">
        <el-pagination
          v-model:current-page="pgNo"
          v-model:page-size="pgSize"
          :page-sizes="[10,20,50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next"
          @change="reload"
        />
      </nav>
    </section>

    <el-dialog v-model="dialogVisible" class="bp-crud-dialog" :title="editing ? '编辑产品' : '新增产品'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item v-if="!editing" label="产品代码" required>
          <el-input v-model="form.productCode" aria-label="产品代码" maxlength="64" placeholder="唯一代码，如 DEPOSIT_001" />
        </el-form-item>
        <el-form-item label="产品名称" required>
          <el-input v-model="form.productName" aria-label="产品名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="产品类别">
          <el-input v-model="form.productCategory" aria-label="产品类别" maxlength="50" placeholder="类别代码" />
        </el-form-item>
        <el-form-item label="产品部门">
          <el-tree-select
            v-model="form.productDeptOrgCode"
            aria-label="产品部门"
            :data="orgTree"
            check-strictly
            :props="{ label: 'name', value: 'code', children: 'children' }"
            placeholder="默认本人组织"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="产品说明">
          <el-input v-model="form.description" aria-label="产品说明" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="中场支持">
          <el-switch v-model="form.supportForSupportRequest" aria-label="是否支持中场请求" />
          <span class="hint support-hint">勾选后该产品在“发起中场支持”页可见</span>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-select v-model="form.status" aria-label="产品状态" style="width:140px">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :disabled="saving"
            :on-change="onFilePick"
            :on-remove="onFileRemove"
          >
            <el-button :disabled="saving">选择文件</el-button>
            <template #tip>
              <span v-if="form.fileObjectId && !pickedFile" class="hint">已有附件（重新选择可替换）</span>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import { fmtDateTime } from '@/utils/datetime';
import { call } from '@/api/http';
import { listProducts, createProduct, updateProduct, deleteProduct } from '@/api/products';
import { getOrgTree } from '@/api/orgs';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const deletingId = ref(null);
const rows = ref([]);
const total = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const orgTree = ref([]);
const filters = ref({ keyword: '', productDeptOrgCode: '', supportForSupportRequest: '', status: '' });

const fmtDate = (v) => fmtDateTime(v);
const indexMethod = (i) => (pgNo.value - 1) * pgSize.value + i + 1;

async function reload() {
  loading.value = true;
  try {
    const r = await listProducts({
      keyword: filters.value.keyword || undefined,
      productDeptOrgCode: filters.value.productDeptOrgCode || undefined,
      supportForSupportRequest: filters.value.supportForSupportRequest === '' ? undefined : filters.value.supportForSupportRequest,
      status: filters.value.status || undefined,
      pageNo: pgNo.value,
      pageSize: pgSize.value
    });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = Array.isArray(r) ? r.length : (r?.total ?? 0);
  } catch { rows.value = []; total.value = 0; } finally { loading.value = false; }
}

function resetFilters() {
  filters.value = { keyword: '', productDeptOrgCode: '', supportForSupportRequest: '', status: '' };
  pgNo.value = 1;
  reload();
}

async function loadOrg() {
  try { orgTree.value = await getOrgTree() || []; } catch { orgTree.value = []; }
}

function downloadAttach(row) {
  if (!row.fileObjectId) return;
  window.open(`/api/files/${row.fileObjectId}/download`, '_blank');
}

const dialogVisible = ref(false);
const editing = ref(null);
const pickedFile = ref(null);
const uploadRef = ref(null);
const form = ref({});

function blankForm() {
  return {
    productCode: '', productName: '', productCategory: '',
    productDeptOrgCode: userStore.user?.mainOrgCode || '',
    description: '', supportForSupportRequest: false, status: 'ACTIVE', fileObjectId: ''
  };
}

function openCreate() {
  editing.value = null;
  pickedFile.value = null;
  form.value = blankForm();
  dialogVisible.value = true;
  nextTick(() => uploadRef.value?.clearFiles());
}

function openEdit(row) {
  editing.value = row;
  pickedFile.value = null;
  form.value = {
    productName: row.productName,
    productCategory: row.productCategory,
    productDeptOrgCode: row.productDeptOrgCode,
    description: row.description,
    supportForSupportRequest: !!row.supportForSupportRequest,
    status: row.status,
    fileObjectId: row.fileObjectId || ''
  };
  dialogVisible.value = true;
  nextTick(() => uploadRef.value?.clearFiles());
}

function onFilePick(file) { pickedFile.value = file?.raw || null; }
function onFileRemove() { pickedFile.value = null; }

async function uploadIfNeeded() {
  if (!pickedFile.value) return form.value.fileObjectId || null;
  const fd = new FormData();
  fd.append('file', pickedFile.value);
  const res = await call('post', '/files/upload', { data: fd, headers: { 'Content-Type': 'multipart/form-data' } }, null);
  return res?.id || res?.fileObjectId || null;
}

async function submit() {
  if (saving.value) return;
  if (!editing.value && !form.value.productCode?.trim()) return ElMessage.warning('请填写产品代码');
  if (!form.value.productName?.trim()) return ElMessage.warning('请填写产品名称');
  saving.value = true;
  try {
    const fileObjectId = await uploadIfNeeded();
    if (editing.value) {
      await updateProduct(editing.value.id, {
        productName: form.value.productName,
        productCategory: form.value.productCategory,
        productDeptOrgCode: form.value.productDeptOrgCode || undefined,
        description: form.value.description,
        supportForSupportRequest: form.value.supportForSupportRequest,
        status: form.value.status,
        fileObjectId
      });
    } else {
      await createProduct({
        productCode: form.value.productCode,
        productName: form.value.productName,
        productCategory: form.value.productCategory,
        description: form.value.description,
        supportForSupportRequest: form.value.supportForSupportRequest,
        productDeptOrgCode: form.value.productDeptOrgCode || undefined,
        fileObjectId
      });
    }
    ElMessage.success('已保存');
    dialogVisible.value = false;
    reload();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); } finally { saving.value = false; }
}

async function onDelete(row) {
  if (deletingId.value === row.id) return;
  deletingId.value = row.id;
  try {
    await deleteProduct(row.id);
    ElMessage.success('已删除');
    reload();
  } catch (e) { ElMessage.error(e?.message || '删除失败'); } finally { deletingId.value = null; }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.productName}」？删除后无法恢复。`, '删除确认', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch { return; }
  await onDelete(row);
}

onMounted(() => { loadOrg(); reload(); });
</script>

<style scoped>
.support-hint { margin-left: var(--space-2); }
:deep(.el-upload-list__item-name) { overflow: visible; text-overflow: clip; white-space: normal; word-break: break-all; }
:deep(.el-upload-list__item) { height: auto; }
</style>
