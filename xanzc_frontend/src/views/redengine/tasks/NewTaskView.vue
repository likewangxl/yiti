<template>
  <div class="re-task-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">新增任务</h2>
        <p class="page-desc">面向党支部发布定时任务或临时任务</p>
      </div>
      <el-button @click="handleBack">返回任务管理</el-button>
    </div>

    <div v-if="optionsError" class="load-error" role="alert">{{ optionsError }}</div>

    <el-card class="form-card" shadow="never">
      <el-form ref="formRef" :model="formData" label-width="116px" class="task-form" @submit.prevent>
        <div class="form-section-title">基本信息</div>

        <el-form-item label="任务性质" prop="nature">
          <el-radio-group v-model="formData.nature">
            <el-radio-button label="PERIODIC">定时任务</el-radio-button>
            <el-radio-button label="TEMPORARY">临时任务</el-radio-button>
          </el-radio-group>
          <div v-if="validationErrors.nature" class="field-error">{{ validationErrors.nature }}</div>
        </el-form-item>

        <el-form-item label="任务类型" prop="businessType">
          <el-select v-model="formData.businessType" clearable filterable placeholder="请选择任务类型" style="width: 360px">
            <el-option
              v-for="option in taskTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
              :disabled="formData.nature === 'TEMPORARY' && option.value === 'FOUR_DIMENSION'"
            />
          </el-select>
          <div v-if="validationErrors.businessType" class="field-error">{{ validationErrors.businessType }}</div>
        </el-form-item>

        <el-form-item label="任务标题" prop="title">
          <el-input v-model="formData.title" maxlength="100" show-word-limit placeholder="请输入任务标题" style="width: 560px" />
          <div v-if="validationErrors.title" class="field-error">{{ validationErrors.title }}</div>
        </el-form-item>

        <el-form-item label="任务说明" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="请输入任务说明，可粘贴网页链接"
            style="width: 640px"
          />
          <div v-if="validationErrors.description" class="field-error">{{ validationErrors.description }}</div>
        </el-form-item>

        <div class="form-section-title">任务对象</div>

        <el-form-item label="任务对象" prop="audienceType">
          <el-radio-group v-model="formData.audienceType">
            <el-radio-button v-for="item in audienceOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <div v-if="validationErrors.audienceType" class="field-error">{{ validationErrors.audienceType }}</div>
        </el-form-item>

        <el-form-item v-if="formData.audienceType === 'SPECIFIED_BRANCH'" label="选择党支部" prop="targetBranchIds">
          <el-select
            v-model="formData.targetBranchIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="请选择党支部"
            style="width: 560px"
          >
            <el-option v-for="option in branchOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <div v-if="validationErrors.targetBranchIds" class="field-error">{{ validationErrors.targetBranchIds }}</div>
        </el-form-item>

        <el-form-item v-if="formData.audienceType === 'SPECIFIED_EMPLOYEE'" label="选择员工" prop="targetEmployeeIds">
          <el-select
            v-model="formData.targetEmployeeIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="请选择员工"
            style="width: 560px"
          >
            <el-option v-for="option in employeeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <div v-if="validationErrors.targetEmployeeIds" class="field-error">{{ validationErrors.targetEmployeeIds }}</div>
        </el-form-item>

        <div class="form-section-title">时间设置</div>

        <template v-if="formData.nature === 'PERIODIC'">
          <el-form-item label="任务周期" prop="cycle">
            <el-select v-model="formData.cycle" clearable placeholder="请选择周期" style="width: 220px">
              <el-option v-for="option in cycleOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <div v-if="validationErrors.cycle" class="field-error">{{ validationErrors.cycle }}</div>
          </el-form-item>
          <el-form-item label="持续天数" prop="durationDays">
            <el-input-number v-model="formData.durationDays" :min="1" :max="366" controls-position="right" />
            <span class="field-suffix">天</span>
            <div v-if="validationErrors.durationDays" class="field-error">{{ validationErrors.durationDays }}</div>
          </el-form-item>
          <el-form-item label="计算窗口">
            <div v-if="windowPreview" class="window-preview" data-test="window-preview">
              {{ windowPreview.start }} 至 {{ windowPreview.end }}
            </div>
            <span v-else class="muted-text">选择周期和持续天数后自动计算</span>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="开始时间" prop="startAt">
            <el-date-picker
              v-model="formData.startAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="请选择开始时间"
              style="width: 240px"
            />
            <div v-if="validationErrors.startAt" class="field-error">{{ validationErrors.startAt }}</div>
          </el-form-item>
          <el-form-item label="截止时间" prop="endAt">
            <el-date-picker
              v-model="formData.endAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="请选择截止时间"
              style="width: 240px"
            />
            <div v-if="validationErrors.endAt" class="field-error">{{ validationErrors.endAt }}</div>
          </el-form-item>
        </template>

        <div class="form-section-title">附件要求</div>

        <el-form-item label="是否上传文件" prop="requiresFile">
          <el-switch v-model="formData.requiresFile" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item v-if="formData.requiresFile" label="允许文件类型" prop="allowedFileTypes">
          <el-checkbox-group v-model="formData.allowedFileTypes">
            <el-checkbox v-for="option in fileTypeOptions" :key="option.value" :label="option.value">
              {{ option.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="validationErrors.allowedFileTypes" class="field-error">{{ validationErrors.allowedFileTypes }}</div>
        </el-form-item>

        <div class="form-footer">
          <el-button @click="handleBack">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">发布任务</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  createTask,
  getOrgTree
} from '@/api/redengine';
import { listUsers } from '@/api/users';
import {
  AUDIENCE_TYPES,
  BUSINESS_TYPES,
  CYCLE_OPTIONS,
  FILE_TYPE_OPTIONS,
  TASK_NATURES,
  calculateTaskWindow,
  buildTaskCreatePayload,
  isPeriodicNature,
  validateTaskDraft
} from './task-domain';

const router = useRouter();
const formRef = ref(null);
const submitting = ref(false);
const loadingOptions = ref(false);
const optionsError = ref('');
const taskTypeOptions = ref([...BUSINESS_TYPES]);
const fileTypeOptions = ref([...FILE_TYPE_OPTIONS]);
const branchOptions = ref([]);
const employeeOptions = ref([]);
const validationErrors = reactive({});
const audienceOptions = AUDIENCE_TYPES;
const cycleOptions = CYCLE_OPTIONS;

const formData = reactive({
  nature: TASK_NATURES.TEMPORARY,
  businessType: '',
  title: '',
  description: '',
  audienceType: 'ALL_BRANCH',
  targetBranchIds: [],
  targetEmployeeIds: [],
  cycle: '',
  durationDays: 1,
  startAt: '',
  endAt: '',
  requiresFile: false,
  allowedFileTypes: [],
  itemCodes: []
});

function flattenOrganizations(nodes, parentLabel = '') {
  const result = [];
  (nodes || []).forEach((node) => {
    const currentLabel = parentLabel ? `${parentLabel} / ${node.orgName}` : node.orgName;
    // 后端任务分配只接受 orgLevel=2 的党支部，组织树中的上级党委不能作为目标。
    if (node.id !== undefined && node.id !== null
      && (Number(node.orgLevel) === 2 || (node.orgLevel == null && !node.children?.length))) {
      result.push({ value: node.id, label: currentLabel });
    }
    result.push(...flattenOrganizations(node.children, currentLabel));
  });
  return result;
}

function mapUsers(value) {
  const records = Array.isArray(value) ? value : value?.records || value?.list || [];
  return records
    .map((user) => ({
      value: user.userId ?? user.id,
      label: `${user.username || user.userCode || user.userId || ''}${user.displayName || user.userchnname ? ` · ${user.displayName || user.userchnname}` : ''}`.trim()
    }))
    .filter((item) => item.value !== undefined && item.value !== null);
}

function pageRecords(value) {
  return Array.isArray(value) ? value : value?.records || value?.list || [];
}

async function loadAllUsers() {
  const users = [];
  const pageSize = 100;
  let pageNo = 1;
  let total = Infinity;

  while (users.length < total) {
    const result = await listUsers({ pageNo, pageSize });
    const records = pageRecords(result);
    users.push(...records);
    total = Number(result?.total ?? result?.totalCount ?? users.length);
    if (!records.length || users.length >= total) break;
    pageNo += 1;
  }
  return users;
}

async function loadOptions() {
  loadingOptions.value = true;
  optionsError.value = '';
  const [organizations, users] = await Promise.allSettled([
    getOrgTree(),
    loadAllUsers()
  ]);
  branchOptions.value = organizations.status === 'fulfilled' ? flattenOrganizations(organizations.value) : [];
  employeeOptions.value = users.status === 'fulfilled' ? mapUsers(users.value) : [];
  if (organizations.status !== 'fulfilled' || users.status !== 'fulfilled') {
    optionsError.value = '任务对象选项加载失败，请稍后重试';
  }
  loadingOptions.value = false;
}

const windowPreview = computed(() => {
  if (!isPeriodicNature(formData.nature) || !formData.cycle || !formData.durationDays) return null;
  try {
    // 周期窗口由任务域按北京时间自然日计算，视图不使用浏览器本地日历。
    return calculateTaskWindow(formData.cycle, Number(formData.durationDays));
  } catch {
    return null;
  }
});

function clearValidationErrors() {
  Object.keys(validationErrors).forEach((key) => delete validationErrors[key]);
}

function applyValidationErrors(errors) {
  clearValidationErrors();
  Object.assign(validationErrors, errors);
}

function buildPayload() {
  return buildTaskCreatePayload(formData);
}

async function handleSubmit() {
  clearValidationErrors();
  try {
    await formRef.value?.validate?.();
  } catch {
    return;
  }
  const validation = validateTaskDraft({ ...formData });
  if (!validation.valid) {
    applyValidationErrors(validation.errors);
    ElMessage.warning(Object.values(validation.errors)[0]);
    return;
  }

  submitting.value = true;
  try {
    await createTask(buildPayload());
    ElMessage.success('任务发布成功');
    router.push('/redengine/task-management');
  } catch {
    // 统一 http 拦截器已经展示后端错误；页面保持填写内容，便于修正后重试。
  } finally {
    submitting.value = false;
  }
}

function handleBack() {
  router.push('/redengine/task-management');
}

onMounted(loadOptions);

defineExpose({
  formData,
  windowPreview,
  taskTypeOptions,
  fileTypeOptions,
  branchOptions,
  employeeOptions,
  loadingOptions,
  optionsError,
  handleSubmit,
  handleBack,
  buildPayload
});
</script>

<style scoped lang="scss">
.re-task-page { padding: 0; }

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 6px;
  color: #1e293b;
  font-size: 22px;
  font-weight: 700;
}

.page-desc { margin: 0; color: #64748b; font-size: 13px; }

.load-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}

.form-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

:deep(.form-card .el-card__body) { padding: 24px 28px; }

.task-form { max-width: 920px; }

.form-section-title {
  margin: 4px 0 20px;
  padding-left: 10px;
  border-left: 3px solid #dc2626;
  color: #1e293b;
  font-size: 15px;
  font-weight: 600;
}

.form-section-title:not(:first-child) { margin-top: 28px; }

:deep(.el-form-item) { margin-bottom: 20px; }
:deep(.el-form-item__label) { color: #475569; font-size: 13px; }

.field-suffix { margin-left: 8px; color: #64748b; font-size: 13px; }

.field-error {
  width: 100%;
  margin-top: 4px;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.4;
}

.window-preview {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 13px;
}

.muted-text { color: #94a3b8; font-size: 13px; }

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}
</style>
