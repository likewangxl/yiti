<template>
  <div class="pl-page">
    <div class="page-h">
      <h1>产品资料库 <span class="sub">中后台组织维护 · 负责人来自通讯录反向关联</span></h1>
      <div class="actions"><el-button type="primary" @click="openCreate">＋ 新增产品</el-button></div>
    </div>

    <div class="card-section">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="产品名称 / 代码" clearable style="width:200px"
                    @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="产品部门">
          <el-tree-select v-model="filters.productDeptOrgCode" :data="orgTree" check-strictly clearable
                          :props="{ label: 'name', value: 'code', children: 'children' }"
                          placeholder="全部" style="width:200px" @change="reload" />
        </el-form-item>
        <el-form-item label="中场支持">
          <el-select v-model="filters.supportForSupportRequest" placeholder="全部" clearable style="width:120px" @change="reload">
            <el-option :value="true" label="是" />
            <el-option :value="false" label="否" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:120px" @change="reload">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="reload">查询</el-button></el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="rows" v-loading="loading" empty-text="暂无产品">
        <el-table-column prop="productDeptOrgName" label="产品部门" width="140" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" width="150" show-overflow-tooltip />
        <el-table-column prop="description" label="产品说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="中场支持" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="row.supportForSupportRequest ? 'success' : 'info'" effect="plain">
              {{ row.supportForSupportRequest ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responsibleEmpNames" label="产品负责人" min-width="140" show-overflow-tooltip>
          <template #default="{row}">{{ row.responsibleEmpNames || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="plain">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{row}">{{ fmtDate(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" :disabled="!row.fileObjectId" @click="downloadAttach(row)">附件</el-button>
            <el-popconfirm :title="`确认删除「${row.productName}」？`" @confirm="onDelete(row)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[10,20,50]"
                       :total="total" background layout="total, sizes, prev, pager, next" @change="reload" />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑产品' : '新增产品'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="产品代码" required v-if="!editing">
          <el-input v-model="form.productCode" maxlength="64" placeholder="唯一代码，如 DEPOSIT_001" />
        </el-form-item>
        <el-form-item label="产品名称" required>
          <el-input v-model="form.productName" maxlength="100" />
        </el-form-item>
        <el-form-item label="产品类别">
          <el-input v-model="form.productCategory" maxlength="50" placeholder="类别代码" />
        </el-form-item>
        <el-form-item label="产品部门">
          <el-tree-select v-model="form.productDeptOrgCode" :data="orgTree" check-strictly
                          :props="{ label: 'name', value: 'code', children: 'children' }"
                          placeholder="默认本人组织" style="width:100%" />
        </el-form-item>
        <el-form-item label="产品说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="中场支持">
          <el-switch v-model="form.supportForSupportRequest" />
          <span class="hint">勾选后该产品在「发起中场支持」页可见</span>
        </el-form-item>
        <el-form-item label="状态" v-if="editing">
          <el-select v-model="form.status" style="width:140px">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <el-upload :auto-upload="false" :show-file-list="true" :limit="1" :on-change="onFilePick" :on-remove="onFileRemove">
            <el-button>选择文件</el-button>
            <template #tip>
              <span v-if="form.fileObjectId && !pickedFile" class="hint">已有附件（重新选择可替换）</span>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { call } from '@/api/http';
import { listProducts, createProduct, updateProduct, deleteProduct } from '@/api/products';
import { getOrgTree } from '@/api/orgs';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const orgTree = ref([]);
const filters = ref({ keyword: '', productDeptOrgCode: '', supportForSupportRequest: '', status: '' });

const fmtDate = (v) => fmtDateTime(v);

async function reload() {
  loading.value = true;
  try {
    const r = await listProducts({
      keyword: filters.value.keyword || undefined,
      productDeptOrgCode: filters.value.productDeptOrgCode || undefined,
      supportForSupportRequest: filters.value.supportForSupportRequest === '' ? undefined : filters.value.supportForSupportRequest,
      status: filters.value.status || undefined,
      pageNo: pgNo.value, pageSize: pgSize.value
    });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = Array.isArray(r) ? r.length : (r?.total ?? 0);
  } catch { rows.value = []; total.value = 0; } finally { loading.value = false; }
}

async function loadOrg() {
  try { orgTree.value = await getOrgTree() || []; } catch { orgTree.value = []; }
}

function downloadAttach(row) {
  if (!row.fileObjectId) return;
  window.open(`/api/files/${row.fileObjectId}/download`, '_blank');
}

// ---- 新增 / 编辑 ----
const dialogVisible = ref(false);
const editing = ref(null);
const pickedFile = ref(null);
const form = ref({});
function blankForm() {
  return {
    productCode: '', productName: '', productCategory: '',
    productDeptOrgCode: userStore.user?.mainOrgCode || '',
    description: '', supportForSupportRequest: false, status: 'ACTIVE', fileObjectId: ''
  };
}
function openCreate() {
  editing.value = null; pickedFile.value = null;
  form.value = blankForm();
  dialogVisible.value = true;
}
function openEdit(row) {
  editing.value = row; pickedFile.value = null;
  form.value = {
    productName: row.productName, productCategory: row.productCategory,
    productDeptOrgCode: row.productDeptOrgCode, description: row.description,
    supportForSupportRequest: !!row.supportForSupportRequest, status: row.status,
    fileObjectId: row.fileObjectId || ''
  };
  dialogVisible.value = true;
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
  if (!editing.value && !form.value.productCode?.trim()) return ElMessage.warning('请填写产品代码');
  if (!form.value.productName?.trim()) return ElMessage.warning('请填写产品名称');
  saving.value = true;
  try {
    const fileObjectId = await uploadIfNeeded();
    if (editing.value) {
      await updateProduct(editing.value.id, {
        productName: form.value.productName,
        productCategory: form.value.productCategory,
        description: form.value.description,
        supportForSupportRequest: form.value.supportForSupportRequest,
        fileObjectId,
        // 负责人不在此维护（反向来自通讯录）→ 不传 responsibleEmpIds
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
  try { await deleteProduct(row.id); ElMessage.success('已删除'); reload(); }
  catch (e) { ElMessage.error(e?.message || '删除失败'); }
}

onMounted(() => { loadOrg(); reload(); });
</script>

<style scoped>
.pl-page { }
.page-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-h h1 { font-size: 18px; margin: 0; }
.page-h .sub { font-size: 12px; color: #909399; font-weight: normal; margin-left: 8px; }
.filter-bar { margin: 0; }
.pager { margin-top: 12px; text-align: right; }
.hint { color: #909399; font-size: 12px; margin-left: 8px; }
</style>
