<template>
  <div class="org-container">
    <h1 class="page-title">党组织管理</h1>

    <el-row :gutter="20">
      <el-col :xs="24" :md="8">
        <el-card class="tree-card" v-loading="treeLoading">
          <template #header>
            <div class="card-header">
              <span class="card-title">组织结构</span>
              <el-button type="success" size="small" @click="handleAddRoot">
                + 新增顶级
              </el-button>
            </div>
          </template>

          <el-tree
            ref="treeRef"
            :data="treeData"
            node-key="id"
            :props="{ children: 'children', label: 'orgName' }"
            :expand-on-click-node="false"
            default-expand-all
            @node-click="selectNode"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <span class="node-label">{{ node.label }}</span>
                <div class="node-actions">
                  <el-icon class="action-icon" @click.stop="handleAddChild(data)">
                    <Plus />
                  </el-icon>
                  <el-icon class="action-icon" @click.stop="handleEditNode(data)">
                    <Edit />
                  </el-icon>
                  <el-icon class="action-icon" @click.stop="handleDeleteNode(data)">
                    <Delete />
                  </el-icon>
                </div>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card v-if="selectedNode" class="detail-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">组织详情</span>
              <el-button type="primary" @click="handleEditNode(selectedNode)">
                编辑
              </el-button>
            </div>
          </template>

          <el-form label-width="120px" class="detail-form">
            <el-form-item label="组织名称">
              <span>{{ selectedNode.orgName }}</span>
            </el-form-item>

            <el-form-item label="组织代码">
              <span>{{ selectedNode.orgCode || '暂无' }}</span>
            </el-form-item>

            <el-form-item label="组织类型">
              <span>{{ selectedNode.orgType || '暂无' }}</span>
            </el-form-item>

            <el-form-item label="负责人">
              <span>{{ selectedNode.principal || '未指定' }}</span>
            </el-form-item>

            <el-form-item label="联系电话">
              <span>{{ selectedNode.contactPhone || '暂无' }}</span>
            </el-form-item>

            <el-form-item label="组织地址">
              <span>{{ selectedNode.orgAddress || '暂无' }}</span>
            </el-form-item>

            <el-form-item label="支部书记工号">
              <span>{{ selectedNode.secretaryId || '未指定' }}</span>
            </el-form-item>

            <el-form-item label="备注">
              <span>{{ selectedNode.remark || '暂无' }}</span>
            </el-form-item>

            <el-form-item label="创建时间">
              <span>{{ selectedNode.createTime || '-' }}</span>
            </el-form-item>
          </el-form>

          <h4 class="section-title">下级组织</h4>
          <el-table
            v-if="selectedNode.children && selectedNode.children.length > 0"
            :data="selectedNode.children"
            stripe
            style="width: 100%"
            max-height="400"
          >
            <el-table-column prop="orgName" label="组织名称" />
            <el-table-column prop="orgCode" label="组织代码" width="120" />
            <el-table-column prop="orgType" label="类型" width="120" />
            <el-table-column prop="principal" label="负责人" width="100" />
          </el-table>
          <div v-else class="empty-text">暂无下级组织</div>
        </el-card>

        <el-empty v-else description="请选择组织查看详情" />
      </el-col>
    </el-row>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑组织' : '新增组织'"
      width="50%"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="120px"
        class="form-content"
      >
        <el-form-item label="组织名称" prop="orgName">
          <el-input v-model="formData.orgName" placeholder="请输入组织名称" />
        </el-form-item>

        <el-form-item label="组织代码" prop="orgCode">
          <el-input v-model="formData.orgCode" placeholder="请输入组织代码" />
        </el-form-item>

        <el-form-item label="组织类型" prop="orgType">
          <el-select v-model="formData.orgType" placeholder="请选择组织类型" style="width: 100%">
            <el-option v-for="opt in orgTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="负责人" prop="principal">
          <el-input v-model="formData.principal" placeholder="请输入负责人姓名" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="组织地址" prop="orgAddress">
          <el-input v-model="formData.orgAddress" placeholder="请输入组织地址" />
        </el-form-item>

        <!-- Task 15 新增字段：后端 RePartyOrg 实体有 secretaryId(支部书记平台用户工号) 列，源系统 OrgView.vue 无此字段 -->
        <el-form-item label="支部书记工号" prop="secretaryId">
          <el-input v-model="formData.secretaryId" placeholder="请输入支部书记平台用户工号" />
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗：删除原因必填（高危操作审计留痕，对齐后端 ReOrgDeleteReqDTO.reason @NotBlank +
         @AuditLog(reasonRequired=true)）。2026-07-19 修复：原实现仅 ElMessageBox 二次确认、无任何
         reason 承载通道，见 red-engine-center/CLAUDE.md「技术债」④（已修复） -->
    <el-dialog v-model="deleteDialog.show" title="删除党组织" width="480px">
      <div class="delete-desc">确定删除组织"{{ deleteDialog.node?.orgName }}"吗？此操作不可恢复。</div>
      <el-form ref="deleteFormRef" :model="deleteDialog.form" :rules="deleteRules" label-width="90px">
        <el-form-item label="删除原因" prop="reason">
          <el-input
            v-model="deleteDialog.form.reason"
            type="textarea"
            :rows="3"
            placeholder="高危操作，审计强制留痕，必填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deleteDialog.show = false">取消</el-button>
        <el-button type="danger" :loading="deleteDialog.submitting" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 红色引擎（党建）党组织管理：移植自 redengine/red-engine-web/src/views/system/OrgView.vue。
// Task 15 变换要点：
// - 源文件 treeData 为硬编码 mock 数据、handleFormSubmit/handleDeleteNode 均为纯前端假成功
//   （ElMessage.success 但不调任何接口）；本次接入真实后端 Task 14 已建的 api/redengine.js：
//   getOrgTree() / addOrg() / updateOrg() / deleteOrg()（F1）。
// - 字段对照后端 RePartyOrg 实体重命名：源 name/code/type/description/leader/phone/email/memberCount
//   → orgName/orgCode/orgType/remark/principal/contactPhone/(无 email)/(无 memberCount，后端无此列)。
// - 新增"支部书记工号"(secretaryId) 编辑字段——后端实体有该列，源系统无对应字段。
// - 组织类型下拉不再写死 company/department/team，改用平台字典 useDict('RE_ORG_TYPE')
//   动态拉取（GET /api/sys/dicts/RE_ORG_TYPE/items，读取种子 SYS_DICT 三项：经营单位/营销部室/中后台部门）。
// - 删除交互（2026-07-19 修复）：由源系统 el-popconfirm / 早期迁移阶段的 ElMessageBox.confirm 二次确认，
//   改为「删除原因」弹窗必填——后端 DELETE /api/re/orgs/{id} 已补齐 ReOrgDeleteReqDTO.reason
//   （@NotBlank）+ @AuditLog(reasonRequired=true) 强制审计留痕（原缺口见
//   red-engine-center/CLAUDE.md「技术债」④，本次同步修复该文档标记）；删除失败（如 RE-40002
//   存在下级党组织不可删除）的错误提示走平台 http.js 既有拦截器，本组件 catch 仅吞掉避免
//   unhandled rejection，不重复弹窗。
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus, Edit, Delete } from '@element-plus/icons-vue';
import { getOrgTree, addOrg, updateOrg, deleteOrg } from '@/api/redengine';
import { useDict } from '@/composables/useDict';

const { options: orgTypeOptions } = useDict('RE_ORG_TYPE');

const treeData = ref([]);
const treeLoading = ref(false);
const treeRef = ref(null);
const dialogVisible = ref(false);
const isEdit = ref(false);
const selectedNode = ref(null);
const parentId = ref(null);
const submitting = ref(false);
const formRef = ref(null);

function emptyForm() {
  return {
    id: undefined,
    orgName: '',
    orgCode: '',
    orgType: '',
    principal: '',
    contactPhone: '',
    orgAddress: '',
    secretaryId: '',
    remark: '',
    orgLevel: 1,
    parentId: null
  };
}

const formData = ref(emptyForm());

const rules = {
  orgName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  orgCode: [{ required: true, message: '请输入组织代码', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择组织类型', trigger: 'change' }]
};

// 拉取党组织完整父子树（GET /api/re/orgs/tree），后端已递归装配 children，前端无需再组树
async function loadTree() {
  treeLoading.value = true;
  try {
    treeData.value = await getOrgTree();
  } finally {
    treeLoading.value = false;
  }
}
onMounted(loadTree);

// 递归查找节点（编辑/删除刷新树后，让详情面板跟随新数据同步刷新用）
function findNode(list, id) {
  for (const n of list || []) {
    if (n.id === id) return n;
    const hit = findNode(n.children, id);
    if (hit) return hit;
  }
  return null;
}

const selectNode = (data) => {
  selectedNode.value = data;
};

const handleAddRoot = () => {
  isEdit.value = false;
  parentId.value = null;
  formData.value = { ...emptyForm(), orgLevel: 1, parentId: null };
  dialogVisible.value = true;
};

const handleAddChild = (node) => {
  isEdit.value = false;
  parentId.value = node.id;
  formData.value = { ...emptyForm(), orgLevel: (node.orgLevel || 1) + 1, parentId: node.id };
  dialogVisible.value = true;
};

const handleEditNode = (node) => {
  isEdit.value = true;
  parentId.value = null;
  formData.value = {
    id: node.id,
    orgName: node.orgName,
    orgCode: node.orgCode || '',
    orgType: node.orgType || '',
    principal: node.principal || '',
    contactPhone: node.contactPhone || '',
    orgAddress: node.orgAddress || '',
    secretaryId: node.secretaryId || '',
    remark: node.remark || '',
    orgLevel: node.orgLevel,
    parentId: node.parentId ?? null
  };
  dialogVisible.value = true;
};

// 删除党组织弹窗状态（高危操作，删除原因必填，见上方脚本头部注释）
const deleteFormRef = ref(null);
const deleteDialog = reactive({
  show: false,
  submitting: false,
  node: null,
  form: { reason: '' }
});
const deleteRules = {
  reason: [{ required: true, message: '请填写删除原因（审计留痕必填）', trigger: 'blur' }]
};

// 点击删除图标：打开「删除原因」弹窗，而非直接二次确认后即删除
const handleDeleteNode = (node) => {
  deleteDialog.node = node;
  deleteDialog.form = { reason: '' };
  deleteDialog.show = true;
};

async function confirmDelete() {
  try {
    await deleteFormRef.value?.validate();
  } catch {
    return;
  }
  deleteDialog.submitting = true;
  try {
    await deleteOrg(deleteDialog.node.id, deleteDialog.form.reason);
    ElMessage.success('组织已删除');
    if (selectedNode.value?.id === deleteDialog.node.id) selectedNode.value = null;
    deleteDialog.show = false;
    await loadTree();
  } catch (e) {
    // http.js 响应拦截器已对业务失败（如 RE-40002 存在下级党组织不可删除）弹出错误提示，这里不重复
  } finally {
    deleteDialog.submitting = false;
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields();
  parentId.value = null;
};

const handleFormSubmit = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      if (isEdit.value) {
        const { id, ...rest } = formData.value;
        await updateOrg(id, rest);
        ElMessage.success('组织已更新');
      } else {
        await addOrg({ ...formData.value, parentId: parentId.value });
        ElMessage.success('组织已添加');
      }
      dialogVisible.value = false;
      await loadTree();
      if (selectedNode.value) {
        selectedNode.value = findNode(treeData.value, formData.value.id) || selectedNode.value;
      }
    } catch (e) {
      // http.js 已弹出错误提示（如 orgName 为空的参数校验失败），这里不重复
    } finally {
      submitting.value = false;
    }
  });
};
</script>

<style scoped lang="scss">
.org-container {
  .delete-desc {
    margin-bottom: 12px;
    color: #64748b;
    font-size: 13px;
  }

  .page-title {
    font-size: 24px;
    margin-bottom: 20px;
    color: #2c3e50;
  }

  .tree-card {
    :deep(.el-card__body) {
      padding: 15px;

      .el-tree {
        background: transparent;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .card-title {
        font-size: 16px;
        font-weight: 600;
      }
    }

    .tree-node {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
      flex: 1;

      .node-label {
        flex: 1;
      }

      .node-actions {
        display: none;
        gap: 8px;

        .action-icon {
          cursor: pointer;
          font-size: 16px;
          color: #409eff;
          transition: color 0.3s;

          &:hover {
            color: #66b1ff;
          }
        }
      }
    }

    :deep(.el-tree-node__content:hover .node-actions) {
      display: flex;
    }
  }

  .detail-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .card-title {
        font-size: 16px;
        font-weight: 600;
      }
    }

    .detail-form {
      :deep(.el-form-item__content) {
        color: #2c3e50;
      }
    }

    .section-title {
      font-size: 14px;
      font-weight: 600;
      color: #2c3e50;
      margin: 20px 0 15px;
      padding-bottom: 10px;
      border-bottom: 1px solid #ecf0f1;
    }

    .empty-text {
      padding: 40px 0;
      text-align: center;
      color: #909399;
    }
  }

  .form-content {
    padding: 20px 0;

    :deep(.el-form-item) {
      margin-bottom: 18px;
    }

    :deep(.el-input),
    :deep(.el-textarea),
    :deep(.el-select) {
      width: 100%;
    }
  }
}
</style>
