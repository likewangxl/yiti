<template>
  <main
    class="bp-crud flow-list-page"
    aria-labelledby="flow-list-page-title"
    :aria-busy="loading ? 'true' : 'false'"
  >
    <header class="page-h">
      <PageTitle id="flow-list-page-title">
        <span class="sub">发布会生成影子流程；现有线上审批的切换与生效范围不在此页自动变更。</span>
      </PageTitle>
      <div class="actions action-group" role="group" aria-label="审批流程操作">
        <el-button @click="reload">刷新</el-button>
        <el-button :loading="importing" :disabled="importing" @click="doImportExisting">导入现有流程</el-button>
        <el-button type="primary" :disabled="creating" @click="openCreate">新建流程</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="审批流程说明">
      <div class="filter-form flow-notice">
        <p class="hint">仅草稿且非只读导入的流程可删除。发布前会由后端校验完整 BPMN 图结构，失败原因会保留在弹窗中供修正。</p>
      </div>
    </section>

    <section
      class="card-section data-panel flow-table-panel"
      aria-label="审批流程列表"
      aria-labelledby="flow-list-heading"
      aria-describedby="flow-list-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="flow-list-heading" class="section-title">流程定义</h2>
          <p class="hint">点击编辑进入图式设计器；只读导入流程可查看或克隆为新草稿。</p>
        </div>
        <p id="flow-list-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '审批流程加载中' : rows.length ? `共 ${rows.length} 条审批流程` : '暂无审批流程' }}
        </p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">
        {{ loadError }} <el-button link type="primary" @click="reload">重试</el-button>
      </p>
      <el-table
        :data="rows"
        size="default"
        v-loading="loading"
        empty-text="暂无审批流程"
        aria-labelledby="flow-list-heading"
        aria-describedby="flow-list-state"
      >
        <el-table-column prop="name" label="流程名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="业务类型" width="140"><template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :class="row.status === 'PUBLISHED' ? 'tag-success' : 'tag-info'" effect="plain" size="small">{{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
        <el-table-column label="版本" width="86" align="center"><template #default="{ row }"><span class="version-num">{{ row.version ?? '-' }}</span></template></el-table-column>
        <el-table-column label="类型标记" width="118"><template #default="{ row }"><el-tag v-if="row.isReadonlyImport == 1" class="tag-warning" effect="plain" size="small">只读导入</el-tag><span v-else class="hint">-</span></template></el-table-column>
        <el-table-column label="最后更新" width="180"><template #default="{ row }">{{ fmtDateTime(row.updatedTime) }}</template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="right">
          <template #default="{ row }">
            <div class="row-actions" role="group" :aria-label="`${row.name || '审批流程'} 操作`">
              <el-button link type="primary" size="small" @click="goEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" :loading="isPublishing(row.id)" :disabled="isPublishing(row.id)" @click="doPublish(row)">发布</el-button>
              <el-button link type="primary" size="small" :loading="isCloning(row.id)" :disabled="isCloning(row.id)" @click="doClone(row)">克隆</el-button>
              <el-button
                v-if="row.status === 'DRAFT' && row.isReadonlyImport != 1"
                link
                type="danger"
                size="small"
                :loading="isDeleting(row.id)"
                :disabled="isDeleting(row.id)"
                @click="doDelete(row)"
              >删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dlg.show" class="bp-crud-dialog" title="新建审批流程" width="500px" :close-on-click-modal="false">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="92px" aria-label="新建审批流程表单">
        <el-form-item label="流程名称" prop="name"><el-input v-model="dlg.form.name" placeholder="请输入流程名称" maxlength="100" /></el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="dlg.form.bizType" placeholder="请选择业务类型" style="width:100%">
            <el-option value="ALLOC_ADJUST" label="业绩调整" />
            <el-option value="TARGET_ADJUST" label="目标方案" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="dlg.saving" @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" :disabled="dlg.saving" @click="saveDlg">创建并编辑</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { listFlows, getFlow, createFlow, publishFlow, deleteFlow, importExistingFlows } from '@/api/flowDesign';

const router = useRouter();
const BIZ_TYPE_MAP = { ALLOC_ADJUST: '业绩调整', TARGET_ADJUST: '目标方案' };
const bizTypeLabel = (type) => BIZ_TYPE_MAP[type] || (type ?? '-');
const rows = ref([]);
const loading = ref(false);
const loadError = ref('');
const importing = ref(false);
const creating = ref(false);
const publishingIds = ref(new Set());
const cloningIds = ref(new Set());
const deletingIds = ref(new Set());

function hasPending(bucket, id) { return bucket.value.has(String(id)); }
function setPending(bucket, id, pending) {
  const next = new Set(bucket.value);
  if (pending) next.add(String(id));
  else next.delete(String(id));
  bucket.value = next;
}
const isPublishing = (id) => hasPending(publishingIds, id);
const isCloning = (id) => hasPending(cloningIds, id);
const isDeleting = (id) => hasPending(deletingIds, id);

function goEdit(row) { router.push(`/system/workflow-flows/${row.id}`); }

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    rows.value = await listFlows();
  } catch (error) {
    rows.value = [];
    loadError.value = `审批流程加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}

async function doImportExisting() {
  if (importing.value) return;
  try {
    await ElMessageBox.confirm(
      '将把现有线上审批流程（业绩调整/目标方案）导入为只读模型供查看。此操作可幂等重复执行，确认导入？',
      '导入现有流程',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'info' }
    );
  } catch {
    return;
  }
  importing.value = true;
  try {
    const result = await importExistingFlows();
    const count = Array.isArray(result) ? result.length : 0;
    ElMessage.success(`导入成功，共导入 ${count} 条流程`);
    await reload();
  } catch (error) {
    ElMessage.error(`导入失败：${error?.message || '请稍后重试'}`);
  } finally {
    importing.value = false;
  }
}

async function doPublish(row) {
  if (!row?.id || isPublishing(row.id)) return;
  // 从确认阶段起锁定该行，防止双击产生两个确认框并发送两次发布请求。
  setPending(publishingIds, row.id, true);
  try {
    await ElMessageBox.confirm(
      `确认发布流程「${row.name}」？发布将生成 BPMN 并部署为影子流程，版本号 +1。`,
      '发布确认',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    setPending(publishingIds, row.id, false);
    return;
  }
  try {
    await publishFlow(row.id);
    ElMessage.success('流程已发布');
    await reload();
  } catch (error) {
    await ElMessageBox.alert(error?.message || '发布失败，请检查流程配置', '发布失败', { confirmButtonText: '知道了', type: 'error' });
  } finally {
    setPending(publishingIds, row.id, false);
  }
}

async function doClone(row) {
  if (!row?.id || isCloning(row.id)) return;
  setPending(cloningIds, row.id, true);
  try {
    const detail = await getFlow(row.id);
    const newFlow = await createFlow({
      name: `${row.name || ''}-副本`,
      bizType: detail?.bizType || row.bizType,
      nodes: detail?.nodes || [],
      edges: detail?.edges || []
    });
    ElMessage.success('克隆成功，已进入编辑页');
    const newId = newFlow?.id || newFlow?.flowDefId || newFlow;
    if (newId) router.push(`/system/workflow-flows/${newId}`);
    else await reload();
  } catch (error) {
    ElMessage.error(`克隆失败：${error?.message || '请稍后重试'}`);
  } finally {
    setPending(cloningIds, row.id, false);
  }
}

async function doDelete(row) {
  if (!row?.id || isDeleting(row.id)) return;
  setPending(deletingIds, row.id, true);
  try {
    await ElMessageBox.confirm(
      `确认删除草稿流程「${row.name}」？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    setPending(deletingIds, row.id, false);
    return;
  }
  try {
    await deleteFlow(row.id);
    ElMessage.success('草稿流程已删除');
    await reload();
  } catch (error) {
    ElMessage.error(`删除失败：${error?.message || '请稍后重试'}`);
  } finally {
    setPending(deletingIds, row.id, false);
  }
}

const dlgFormRef = ref(null);
const dlg = reactive({
  show: false,
  saving: false,
  form: { name: '', bizType: '' },
  rules: {
    name: [{ required: true, message: '流程名称必填', trigger: 'blur' }, { max: 100, message: '不超过 100 位', trigger: 'blur' }],
    bizType: [{ required: true, message: '请选择业务类型', trigger: 'change' }]
  }
});

function openCreate() {
  if (dlg.saving || creating.value) return;
  dlg.form = { name: '', bizType: '' };
  dlg.show = true;
}
async function saveDlg() {
  if (dlg.saving || creating.value) return;
  try {
    await dlgFormRef.value?.validate?.();
  } catch {
    return;
  }
  dlg.saving = true;
  creating.value = true;
  try {
    const newFlow = await createFlow({ name: dlg.form.name, bizType: dlg.form.bizType, nodes: [], edges: [] });
    ElMessage.success('流程已创建');
    dlg.show = false;
    const newId = newFlow?.id || newFlow?.flowDefId || newFlow;
    if (newId) router.push(`/system/workflow-flows/${newId}`);
    else await reload();
  } catch (error) {
    ElMessage.error(`创建失败：${error?.message || '请稍后重试'}`);
  } finally {
    dlg.saving = false;
    creating.value = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.flow-notice { display: block; }
.flow-notice .hint { margin: 0; }
.version-num { font-variant-numeric: tabular-nums; }
.row-actions { display: flex; justify-content: flex-end; gap: var(--space-1); }
</style>
