<!-- 保存查询方案 Dialog
     后端 SavedQuerySaveReqDTO 字段：name / dim / subjectIds(JSON 字符串) / metricCodes(JSON 字符串)。
     api/report.js 在 saveQuery() 内部把数组序列化，本组件只需收集 name 即可。 -->
<template>
  <el-dialog
    class="bp-crud-dialog"
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="保存查询方案"
    width="520px"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    aria-label="保存查询方案"
  >
    <el-form label-position="top" size="default">
      <el-form-item required>
        <template #label><span class="req">*</span> 方案名称</template>
        <el-input v-model="form.name" maxlength="200" />
      </el-form-item>
    </el-form>
    <el-alert type="info" :closable="false" class="meta">
      当前条件：维度 <b>{{ context.dim }}</b> · 指标 <b>{{ context.metrics }}</b> 项 · 对象 <b>{{ context.objects }}</b> 个
    </el-alert>
    <template #footer>
      <el-button :disabled="saving" @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { saveQuery } from '@/api/report';

const props = defineProps({
  visible: Boolean,
  /** payload: { dim, metrics:[code...], subjects:[{id,name,org}...], date } */
  context: { type: Object, default: () => ({ dim: 'EMP', metrics: 0, objects: 0, payload: {} }) }
});
const emit = defineEmits(['update:visible', 'saved']);

const saving = ref(false);
const form = reactive({
  name: `查询方案-${new Date().toISOString().slice(0, 10)}`
});

watch(() => props.visible, (v) => {
  if (v) {
    form.name = `查询方案-${new Date().toISOString().slice(0, 10)}`;
  }
});

async function save() {
  if (saving.value) return;
  if (!form.name.trim()) { ElMessage.warning('请填写方案名称'); return; }
  const p = props.context.payload || {};
  if (!Array.isArray(p.metrics)  || !p.metrics.length)  { ElMessage.warning('请先选择至少 1 个指标'); return; }
  // 对象允许为空：不选=保存后按数据范围「查全部」(与编辑弹框口径一致，后端 noSubjectSelected 分支支持)
  saving.value = true;
  try {
    const res = await saveQuery({
      name: form.name.trim(),
      dim: p.dim,
      metrics: p.metrics,
      subjects: p.subjects
    });
    ElMessage.success('方案已保存');
    emit('saved', res);
    emit('update:visible', false);
  } catch (e) {
    // http.js 拦截器已 ElMessage.error，无需重复提示
  } finally {
    saving.value = false;
  }
}
</script>

<style lang="scss" scoped>
.req { color: var(--color-danger-fg); }
.meta { margin-top: 4px; }
</style>
