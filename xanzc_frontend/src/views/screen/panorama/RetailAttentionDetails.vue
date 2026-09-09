<template>
  <ElDialog
    v-model="visible"
    class="retail-attention-dialog"
    :title="itemLabel"
    width="min(620px, calc(100vw - 32px))"
    append-to-body
    modal-class="retail-attention-dialog-overlay"
    destroy-on-close
    :close-on-click-modal="true"
    :close-on-press-escape="true"
  >
    <div class="retail-attention-detail" data-testid="retail-attention-detail">
      <div v-if="demo" class="retail-attention-detail__demo" data-testid="retail-attention-detail-demo">
        本地演示 · 非业务数据
      </div>
      <dl class="retail-attention-detail__facts">
        <div>
          <dt>事项</dt>
          <dd>{{ itemLabel }}</dd>
        </div>
        <div>
          <dt>数量</dt>
          <dd data-testid="retail-attention-detail-count">{{ countLabel }}</dd>
        </div>
        <div>
          <dt>责任</dt>
          <dd>{{ fieldLabel(item?.owner) }}</dd>
        </div>
        <div>
          <dt>截止</dt>
          <dd>{{ fieldLabel(item?.deadline) }}</dd>
        </div>
        <div>
          <dt>授权范围</dt>
          <dd>{{ fieldLabel(scopeLabel) }}</dd>
        </div>
        <div>
          <dt>数据日期</dt>
          <dd>{{ fieldLabel(dataDate) }}</dd>
        </div>
      </dl>

      <section class="retail-attention-detail__description" aria-label="事项说明">
        <h3>事项说明</h3>
        <p>{{ demoDetail('description', '未提供事项说明') }}</p>
      </section>
      <section class="retail-attention-detail__description" aria-label="协调要求">
        <h3>协调要求</h3>
        <p>{{ demoDetail('coordination', '未提供协调要求') }}</p>
      </section>
      <section v-if="demo" class="retail-attention-detail__description" aria-label="来源">
        <h3>来源</h3>
        <p>{{ demoDetail('source', '未提供来源') }}</p>
      </section>
    </div>
    <template #footer>
      <button type="button" class="retail-attention-detail__close" data-action="close-retail-attention" @click="close">关闭</button>
    </template>
  </ElDialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElDialog } from 'element-plus';

const props = defineProps({
  item: { type: Object, default: () => ({}) },
  demo: { type: Boolean, default: false },
  scopeLabel: { type: String, default: '' },
  dataDate: { type: String, default: '' }
});
const emit = defineEmits(['close']);
const visible = ref(true);

const itemLabel = computed(() => String(props.item?.label || '—'));
const countLabel = computed(() => {
  const value = props.item?.count;
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'boolean' || typeof value === 'object') return '—';
  if (typeof value === 'string' && value.trim() === '') return '—';
  const number = Number(value);
  if (!Number.isFinite(number)) return '—';
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(number);
});

function fieldLabel(value) {
  return value === null || value === undefined || value === '' ? '未提供' : String(value);
}

function demoDetail(key, emptyLabel) {
  if (!props.demo) return emptyLabel;
  const detail = props.item?.detail;
  if (!detail || typeof detail !== 'object' || Array.isArray(detail)) return emptyLabel;
  const value = detail[key];
  return value === null || value === undefined || value === '' ? emptyLabel : String(value);
}

function close() {
  visible.value = false;
}

watch(visible, value => {
  if (!value) emit('close');
});
</script>
