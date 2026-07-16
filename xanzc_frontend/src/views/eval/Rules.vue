<template>
  <div class="rules-page">
    <!-- 页头 -->
    <div class="page-h">
      <PageTitle />
      <span class="desc">为每类评价对象配置评价人组及权重</span>
      <div class="actions">
        <el-button type="primary" @click="openCreateDialog">新建规则</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="queryParams.keyword"
        placeholder="搜索规则名称"
        clearable
        style="width: 240px"
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 规则列表表格 -->
    <el-table
      v-loading="tableLoading"
      :data="tableData"
      border
      stripe
      style="width: 100%; margin-top: 16px"
    >
      <el-table-column prop="ruleId" label="规则ID" width="80" />
      <el-table-column prop="ruleName" label="规则名称" min-width="160" />
      <el-table-column label="评价对象标签" min-width="140">
        <template #default="{ row }">
          <span>{{ getTagName(row.beEvalTagId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span :class="row.status === 1 ? 'tag-success' : 'tag-info'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetailDialog(row.ruleId)">详情</el-button>
          <el-button link type="primary" @click="openEditDialog(row.ruleId)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="formDialog.visible"
      :title="formDialog.isEdit ? '编辑规则' : '新建规则'"
      width="700px"
      :close-on-click-modal="false"
      @closed="resetFormDialog"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <!-- 规则名称 -->
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="formData.ruleName" placeholder="请输入规则名称" maxlength="64" show-word-limit />
        </el-form-item>

        <!-- 被评价人标签（仅新建时可选） -->
        <el-form-item label="评价对象标签" prop="beEvalTagId">
          <el-select
            v-model="formData.beEvalTagId"
            placeholder="请选择评价对象标签"
            filterable
            :disabled="formDialog.isEdit"
            style="width: 100%"
          >
            <el-option
              v-for="tag in beEvalTagOptions"
              :key="tag.tagId"
              :label="tag.tagName"
              :value="tag.tagId"
            />
          </el-select>
          <div v-if="formDialog.isEdit" class="field-hint">编辑时不允许更改评价对象标签</div>
        </el-form-item>

        <!-- 评价人组配置 -->
        <el-form-item label="评价人组">
          <div class="group-table-wrap">
            <el-table :data="formData.groups" border size="small">
              <el-table-column label="组类型" width="140">
                <template #default="{ row }">
                  <el-select v-model="row.groupType" size="small" style="width: 100%">
                    <el-option :value="1" label="按标签选人" />
                    <el-option :value="2" label="部门员工组" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="评价人标签" min-width="140">
                <template #default="{ row }">
                  <el-select
                    v-model="row.evalTagId"
                    size="small"
                    placeholder="请选择"
                    filterable
                    clearable
                    :disabled="row.groupType !== 1"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="tag in evalTagOptions"
                      :key="tag.tagId"
                      :label="tag.tagName"
                      :value="tag.tagId"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="权重(%)" width="140">
                <template #default="{ row }">
                  <div class="weight-cell">
                    <el-input-number
                      v-model="row.weight"
                      size="small"
                      :min="0"
                      :max="100"
                      :precision="2"
                      :controls="false"
                      style="width: 90px"
                    />
                    <span class="weight-unit">%</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="评分方式" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.scoreMode" size="small" style="width: 100%">
                    <el-option :value="1" label="数值打分" />
                    <el-option :value="2" label="等级打分" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="排序" width="100">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.sortOrder"
                    size="small"
                    :min="0"
                    :controls="false"
                    style="width: 70px"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="removeGroup($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button
              type="dashed"
              size="small"
              style="margin-top: 8px; width: 100%"
              @click="addGroup"
            >
              + 添加评价人组
            </el-button>
            <!-- 权重合计提示 -->
            <div :class="['weight-total', weightTotalClass]">
              权重合计：{{ weightTotal.toFixed(2) }}%
              <span v-if="weightTotal !== 100" class="weight-warn">（必须等于 100%）</span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="formDialog.saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailDialog.visible"
      title="规则详情"
      width="700px"
      :close-on-click-modal="false"
    >
      <div v-loading="detailDialog.loading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="规则ID">{{ detailDialog.rule.ruleId }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ detailDialog.rule.ruleName }}</el-descriptions-item>
          <el-descriptions-item label="评价对象标签">
            {{ getTagName(detailDialog.rule.beEvalTagId) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <span :class="detailDialog.rule.status === 1 ? 'tag-success' : 'tag-info'">
              {{ detailDialog.rule.status === 1 ? '启用' : '停用' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">
            {{ detailDialog.rule.createTime }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-group-title">评价人组</div>
        <el-table :data="detailDialog.groups" border size="small" style="margin-top: 8px">
          <el-table-column prop="groupType" label="组类型" width="140">
            <template #default="{ row }">
              {{ row.groupType === 1 ? '按标签选人' : '部门员工组' }}
            </template>
          </el-table-column>
          <el-table-column label="评价人标签" min-width="140">
            <template #default="{ row }">
              {{ row.groupType === 1 ? getTagName(row.evalTagId) : '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="权重(%)" width="100">
            <template #default="{ row }">{{ row.weight }}%</template>
          </el-table-column>
          <el-table-column label="评分方式" width="110">
            <template #default="{ row }">
              {{ row.scoreMode === 2 ? '等级打分' : '数值打分' }}
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
        </el-table>
      </div>

      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  listAllTags,
  listRules,
  getRuleDetail,
  createRule,
  updateRule,
  deleteRule,
} from '@/api/eval'

// ===== 全部标签缓存（用于下拉和名称映射） =====
/** 所有标签列表（无类型扁平标签池） */
const allTags = ref([])

/** 被评价人标签选项（全量池） */
const beEvalTagOptions = computed(() => allTags.value)

/**
 * 根据 tagId 获取标签名称
 * @param {number|null} tagId
 */
const getTagName = (tagId) => {
  if (!tagId) return '—'
  const found = allTags.value.find((t) => t.tagId === tagId)
  return found ? found.tagName : `ID:${tagId}`
}

/** 加载全部标签（用于下拉和名称映射，走不分页 /all 端点） */
const loadAllTags = async () => {
  try {
    const res = await listAllTags()
    allTags.value = Array.isArray(res) ? res : (res?.records ?? [])
  } catch (e) {
    ElMessage.error('加载标签列表失败：' + (e?.message ?? '未知错误'))
  }
}

// ===== 列表查询 =====
/** 列表查询参数 */
const queryParams = reactive({
  keyword: '',
  pageNo: 1,
  pageSize: 20,
})

/** 表格数据 */
const tableData = ref([])
/** 数据总条数 */
const total = ref(0)
/** 表格加载状态 */
const tableLoading = ref(false)

/** 加载规则列表 */
const loadList = async () => {
  tableLoading.value = true
  try {
    const res = await listRules({
      keyword: queryParams.keyword,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    })
    tableData.value = res?.records ?? []
    total.value = res?.total ?? 0
  } catch (e) {
    ElMessage.error('加载规则列表失败：' + (e?.message ?? '未知错误'))
  } finally {
    tableLoading.value = false
  }
}

/** 查询（重置页码后加载） */
const handleSearch = () => {
  queryParams.pageNo = 1
  loadList()
}

/** 重置筛选条件 */
const handleReset = () => {
  queryParams.keyword = ''
  queryParams.pageNo = 1
  loadList()
}

// ===== 新建/编辑弹窗 =====
/** 弹窗状态 */
const formDialog = reactive({
  visible: false,
  isEdit: false,
  editRuleId: null,
  saving: false,
})

/** 表单数据 */
const formData = reactive({
  ruleName: '',
  beEvalTagId: null,
  groups: [],
})

/** 评价人标签选项（全量池，排除当前规则的被评价人标签——局部排斥） */
const evalTagOptions = computed(() => allTags.value.filter((t) => t.tagId !== formData.beEvalTagId))

/** 表单校验规则 */
const formRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  beEvalTagId: [{ required: true, message: '请选择被评价人标签', trigger: 'change' }],
}

/** 表单 ref */
const formRef = ref(null)

/** 计算权重合计 */
const weightTotal = computed(() =>
  formData.groups.reduce((sum, g) => sum + (Number(g.weight) || 0), 0)
)

/** 权重合计样式类 */
const weightTotalClass = computed(() =>
  Math.abs(weightTotal.value - 100) < 0.001 ? 'weight-total--ok' : 'weight-total--error'
)

/** 打开新建弹窗 */
const openCreateDialog = () => {
  formDialog.isEdit = false
  formDialog.editRuleId = null
  formDialog.visible = true
}

/** 打开编辑弹窗（加载详情回填） */
const openEditDialog = async (ruleId) => {
  formDialog.isEdit = true
  formDialog.editRuleId = ruleId
  formDialog.visible = true
  try {
    const res = await getRuleDetail(ruleId)
    const rule = res?.rule ?? {}
    const groups = res?.groups ?? []
    formData.ruleName = rule.ruleName ?? ''
    formData.beEvalTagId = rule.beEvalTagId ?? null
    formData.groups = groups.map((g) => ({
      groupType: g.groupType ?? 1,
      evalTagId: g.evalTagId ?? null,
      weight: g.weight ?? 0,
      scoreMode: g.scoreMode ?? 1,
      sortOrder: g.sortOrder ?? 0,
    }))
  } catch (e) {
    ElMessage.error('加载规则详情失败：' + (e?.message ?? '未知错误'))
    formDialog.visible = false
  }
}

/** 重置表单弹窗状态 */
const resetFormDialog = () => {
  formData.ruleName = ''
  formData.beEvalTagId = null
  formData.groups = []
  formRef.value?.clearValidate()
}

/** 添加评价人组行 */
const addGroup = () => {
  formData.groups.push({
    groupType: 1,
    evalTagId: null,
    weight: 0,
    scoreMode: 1,
    sortOrder: formData.groups.length + 1,
  })
}

/** 删除评价人组行 */
const removeGroup = (index) => {
  formData.groups.splice(index, 1)
}

/** 保存（新建/编辑） */
const handleSave = async () => {
  // 1. 基础表单校验
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 2. 权重合计校验
  if (Math.abs(weightTotal.value - 100) >= 0.001) {
    ElMessage.warning(`评价人组权重合计必须等于 100%，当前为 ${weightTotal.value.toFixed(2)}%`)
    return
  }

  // 3. 至少需要一个评价人组
  if (formData.groups.length === 0) {
    ElMessage.warning('请至少添加一个评价人组')
    return
  }

  formDialog.saving = true
  try {
    const payload = {
      ruleName: formData.ruleName,
      groups: formData.groups.map((g) => ({
        groupType: g.groupType,
        evalTagId: g.groupType === 1 ? g.evalTagId : null,
        weight: g.weight,
        scoreMode: g.scoreMode ?? 1,
        sortOrder: g.sortOrder,
      })),
    }

    if (formDialog.isEdit) {
      await updateRule(formDialog.editRuleId, payload)
      ElMessage.success('规则更新成功')
    } else {
      await createRule({ ...payload, beEvalTagId: formData.beEvalTagId })
      ElMessage.success('规则创建成功')
    }

    formDialog.visible = false
    loadList()
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  } finally {
    formDialog.saving = false
  }
}

// ===== 删除 =====
/**
 * 删除规则
 * @param {Object} row 规则行数据
 */
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除规则「${row.ruleName}」吗？删除后不可恢复。`,
    '删除确认',
    { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteRule(row.ruleId)
        ElMessage.success('规则已删除')
        loadList()
      } catch (e) {
        ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
      }
    })
    .catch(() => {
      // 用户取消，忽略
    })
}

// ===== 详情弹窗 =====
/** 详情弹窗状态 */
const detailDialog = reactive({
  visible: false,
  loading: false,
  rule: {},
  groups: [],
})

/** 打开详情弹窗 */
const openDetailDialog = async (ruleId) => {
  detailDialog.visible = true
  detailDialog.loading = true
  detailDialog.rule = {}
  detailDialog.groups = []
  try {
    const res = await getRuleDetail(ruleId)
    detailDialog.rule = res?.rule ?? {}
    detailDialog.groups = res?.groups ?? []
  } catch (e) {
    ElMessage.error('加载详情失败：' + (e?.message ?? '未知错误'))
    detailDialog.visible = false
  } finally {
    detailDialog.loading = false
  }
}

// ===== 初始化 =====
onMounted(() => {
  loadAllTags()
  loadList()
})
</script>

<style lang="scss" scoped>
/* SCSS 变量（与项目主题对齐） */
$text-1: #1a1a1a;
$text-2: #595959;
$text-3: #8c8c8c;
$border-1: #e0e0e0;
$bg-soft: #f7f8fa;
$primary: #2563eb;
$danger: #dc2626;

/* 页头 */
.page-h {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;

  h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: $text-1;
  }

  .desc {
    font-size: 13px;
    color: $text-3;
  }

  .actions {
    margin-left: auto;
    display: flex;
    gap: 8px;
  }
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: $bg-soft;
  border-radius: 6px;
}

/* 分页 */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 状态标签 */
.tag-success {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.tag-info {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #f3f4f6;
  color: $text-3;
  border: 1px solid $border-1;
}

.tag-warning {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

/* 评价人组表格区域 */
.group-table-wrap {
  width: 100%;

  .weight-cell {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .weight-unit {
    font-size: 13px;
    color: $text-2;
    flex-shrink: 0;
  }
}

/* 权重合计 */
.weight-total {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 500;

  &--ok {
    color: #059669;
  }

  &--error {
    color: $danger;
  }

  .weight-warn {
    font-size: 12px;
    margin-left: 4px;
  }
}

/* 表单字段提示 */
.field-hint {
  font-size: 12px;
  color: $text-3;
  margin-top: 4px;
  line-height: 1.4;
}

/* 详情弹窗分组标题 */
.detail-group-title {
  margin-top: 20px;
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 600;
  color: $text-1;
  padding-left: 8px;
  border-left: 3px solid $primary;
}
.pagination-bar :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
