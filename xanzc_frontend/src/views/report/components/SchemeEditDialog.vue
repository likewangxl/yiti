<!-- 编辑查询方案 —— 改 维度/名称/指标/对象。后端 updateSavedQuery 乐观锁(expectedVersion)。 -->
<template>
  <el-dialog class="bp-crud-dialog" :model-value="visible" @update:model-value="$emit('update:visible', $event)"
             title="编辑查询方案" width="640px" :close-on-click-modal="false" :close-on-press-escape="!saving" aria-label="编辑查询方案">
    <el-form label-position="top" size="default">
      <el-form-item label="维度">
        <el-radio-group :model-value="form.dim" @update:model-value="onDimChange">
          <el-radio-button v-for="d in DIMS" :key="d.code" :value="d.code">{{ d.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item required>
        <template #label><span class="req">*</span> 方案名称</template>
        <el-input v-model="form.name" maxlength="200" />
      </el-form-item>
      <el-form-item :label="`指标 (已选 ${form.metrics.length})`">
        <div class="tags">
          <el-tag v-for="c in form.metrics" :key="c" closable type="info" effect="plain"
                  @close.stop="form.metrics = form.metrics.filter(x => x !== c)">{{ c }}</el-tag>
          <el-button link type="primary" @click="metricPickerVisible = true">选择指标</el-button>
        </div>
      </el-form-item>
      <el-form-item :label="`对象 (已选 ${form.subjects.length}，不选=查全部)`">
        <div class="tags">
          <el-tag v-for="s in form.subjects" :key="s.id" closable effect="plain"
                  @close.stop="form.subjects = form.subjects.filter(x => x.id !== s.id)">{{ s.name }}</el-tag>
          <el-button link type="primary" @click="subjectPickerVisible = true">选择对象</el-button>
        </div>
      </el-form-item>
    </el-form>

    <MetricPicker v-model:visible="metricPickerVisible" v-model="form.metrics" :dim="form.dim" />
    <SubjectPicker v-model:visible="subjectPickerVisible" v-model="form.subjects" :dim="form.dim" />

    <template #footer>
      <el-button :disabled="saving" @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { updateSavedQuery } from '@/api/report';
import MetricPicker from './MetricPicker.vue';
import SubjectPicker from './SubjectPicker.vue';

const DIMS = [{ code: 'EMP', label: '员工' }, { code: 'ORG', label: '机构' }, { code: 'CUST', label: '客户' }];
const props = defineProps({
  visible: Boolean,
  scheme: { type: Object, default: () => ({ id: '', name: '', dim: 'EMP', metrics: [], subjects: [], version: 0 }) }
});
const emit = defineEmits(['update:visible', 'saved']);

const saving = ref(false);
const metricPickerVisible = ref(false);
const subjectPickerVisible = ref(false);
const form = reactive({ name: '', dim: 'EMP', metrics: [], subjects: [] });

// 打开时用 scheme 预填(深拷贝,避免直接改父数据)
watch(() => props.visible, (v) => {
  if (v && props.scheme) {
    form.name = props.scheme.name || '';
    form.dim = props.scheme.dim || 'EMP';
    form.metrics = Array.isArray(props.scheme.metrics) ? [...props.scheme.metrics] : [];
    form.subjects = Array.isArray(props.scheme.subjects)
      ? props.scheme.subjects.map(s => ({ id: s.id, name: s.name || s.id, org: s.org || '' }))
      : [];
  }
}, { immediate: true });

// 维度是总开关:切换会作废已选指标/对象。有选择时先确认,确认才切并清空,取消则维度不变。
async function onDimChange(next) {
  if (next === form.dim) return;
  if (form.metrics.length || form.subjects.length) {
    try { await ElMessageBox.confirm('切换维度会清空已选指标和对象，确定？', '切换维度', { type: 'warning' }); }
    catch { return; }  // 取消:维度保持不变
  }
  form.dim = next;
  form.metrics = [];
  form.subjects = [];
}

async function save() {
  if (saving.value) return;
  if (!form.name.trim()) { ElMessage.warning('请填写方案名称'); return; }
  if (!form.metrics.length) { ElMessage.warning('请至少选择 1 个指标'); return; }
  saving.value = true;
  try {
    await updateSavedQuery(props.scheme.id, {
      name: form.name.trim(), dim: form.dim,
      metrics: form.metrics, subjects: form.subjects,
      expectedVersion: props.scheme.version
    });
    ElMessage.success('方案已更新');
    emit('saved');
    emit('update:visible', false);
  } catch (e) {
    // 乐观锁冲突(后端归 RPT-40002 NO_ACCESS)等由 http.js 拦截器统一弹错
  } finally {
    saving.value = false;
  }
}

defineExpose({ form, save, onDimChange });
</script>

<style lang="scss" scoped>
.req { color: var(--color-danger-fg); }
.tags { display: flex; flex-wrap: wrap; gap: 6px; padding: 6px 8px; min-height: 36px;
  border: 1px solid var(--color-border-strong); border-radius: var(--radius-control); align-items: center; }
</style>
