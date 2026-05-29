<template>
  <div>
    <div class="page-h">
      <h1>审批流程 <span class="sub">查看/编辑审批流程；发布生成影子流程，切换生效另行处理，不影响现有线上审批</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button :loading="importing" @click="doImportExisting">导入现有流程</el-button>
        <el-button type="primary" @click="openCreate">+ 新建流程</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无流程">
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="业务类型" width="140">
          <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'PUBLISHED' ? 'success' : 'info'"
              effect="plain"
              size="small"
            >
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">{{ row.version ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="类型标记" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.isReadonlyImport == 1" type="warning" effect="plain" size="small">只读导入</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最后更新" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="doPublish(row)">发布</el-button>
            <el-button link type="primary" size="small" @click="doClone(row)">克隆</el-button>
            <el-button
              v-if="row.status === 'DRAFT' && row.isReadonlyImport != 1"
              link
              type="danger"
              size="small"
              @click="doDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建流程弹窗 -->
    <el-dialog v-model="dlg.show" title="新建审批流程" width="480px">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlg.rules" label-width="90px">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="dlg.form.name" placeholder="请输入流程名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="dlg.form.bizType" placeholder="请选择" style="width: 100%">
            <el-option value="ALLOC_ADJUST" label="业绩调整" />
            <el-option value="TARGET_ADJUST" label="目标方案" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="saveDlg">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { listFlows, getFlow, createFlow, publishFlow, deleteFlow, importExistingFlows } from '@/api/flowDesign';

const router = useRouter();

// 业务类型显示映射
const BIZ_TYPE_MAP = {
  ALLOC_ADJUST: '业绩调整',
  TARGET_ADJUST: '目标方案'
};
function bizTypeLabel(type) {
  return BIZ_TYPE_MAP[type] || (type ?? '-');
}

// 跳转编辑页
function goEdit(row) {
  router.push(`/system/workflow-flows/${row.id}`);
}

// === 列表 ===
const rows = ref([]);
const loading = ref(false);
const importing = ref(false);

// === 导入现有流程 ===
async function doImportExisting() {
  try {
    await ElMessageBox.confirm(
      '将把现有线上审批流程（业绩调整/目标方案）导入为只读模型供查看，幂等可重复，确认？',
      '导入现有流程',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'info' }
    );
  } catch {
    return; // 用户取消
  }
  importing.value = true;
  try {
    const result = await importExistingFlows();
    const count = Array.isArray(result) ? result.length : 0;
    ElMessage.success(`导入成功，共导入 ${count} 条流程`);
    await reload();
  } catch (e) {
    ElMessage.error(e?.message || '导入失败');
  } finally {
    importing.value = false;
  }
}

async function reload() {
  loading.value = true;
  try {
    rows.value = await listFlows();
  } catch (e) {
    ElMessage.error('加载失败：' + (e?.message || e));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

// === 发布 ===
async function doPublish(row) {
  try {
    await ElMessageBox.confirm(
      `确认发布流程「${row.name}」？发布将生成 BPMN 并部署为影子流程，版本号 +1。`,
      '发布确认',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    return; // 用户取消
  }
  try {
    await publishFlow(row.id);
    ElMessage.success('发布成功');
    await reload();
  } catch (e) {
    // 发布校验失败明细可能较长，用 alert 保持可读，不自动消失
    ElMessageBox.alert(
      e?.message || '发布失败，请检查流程配置',
      '发布失败',
      { confirmButtonText: '知道了', type: 'error' }
    );
  }
}

// === 克隆 ===
async function doClone(row) {
  try {
    const detail = await getFlow(row.id);
    const payload = {
      name: (row.name || '') + '-副本',
      bizType: detail?.bizType || row.bizType,
      nodes: detail?.nodes || [],
      edges: detail?.edges || []
    };
    const newFlow = await createFlow(payload);
    ElMessage.success('克隆成功，已跳转编辑页');
    const newId = newFlow?.id || newFlow?.flowDefId || newFlow;
    if (newId) {
      router.push(`/system/workflow-flows/${newId}`);
    } else {
      await reload();
    }
  } catch (e) {
    ElMessage.error('克隆失败：' + (e?.message || e));
  }
}

// === 删除 ===
async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除草稿流程「${row.name}」？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    );
  } catch {
    return;
  }
  try {
    await deleteFlow(row.id);
    ElMessage.success('已删除');
    await reload();
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e));
  }
}

// === 新建弹窗 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false,
  saving: false,
  form: { name: '', bizType: '' },
  rules: {
    name:    [{ required: true, message: '流程名称必填', trigger: 'blur' }, { max: 100, message: '不超过 100 位', trigger: 'blur' }],
    bizType: [{ required: true, message: '请选择业务类型', trigger: 'change' }]
  }
});

function openCreate() {
  dlg.form = { name: '', bizType: '' };
  dlg.show = true;
}

async function saveDlg() {
  try { await dlgFormRef.value?.validate(); } catch { return; }
  dlg.saving = true;
  try {
    const newFlow = await createFlow({
      name: dlg.form.name,
      bizType: dlg.form.bizType,
      nodes: [],
      edges: []
    });
    ElMessage.success('创建成功');
    dlg.show = false;
    const newId = newFlow?.id || newFlow?.flowDefId || newFlow;
    if (newId) {
      router.push(`/system/workflow-flows/${newId}`);
    } else {
      await reload();
    }
  } catch (e) {
    ElMessage.error('创建失败：' + (e?.message || e));
  } finally {
    dlg.saving = false;
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
</style>
