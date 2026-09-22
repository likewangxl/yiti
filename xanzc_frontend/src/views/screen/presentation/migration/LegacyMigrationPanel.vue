<template>
  <section class="legacy-migration" data-testid="legacy-migration-preview" aria-label="旧配置迁移预览">
    <header class="legacy-migration__header">
      <div>
        <h3>旧配置迁移预览</h3>
        <p>仅按旧组件编码、绑定快照和字段结构匹配，不会按中文标题猜测数据身份。</p>
      </div>
      <span :class="['legacy-migration__lossless', { blocked: !preview.canDeclareLossless }]">
        {{ preview.canDeclareLossless ? '可声明无损迁移' : '存在未识别项，不能声明无损完成' }}
      </span>
    </header>

    <div class="legacy-migration__summary" role="status">
      <span data-testid="migration-status-migrated">已迁移 {{ preview.summary?.migrated || 0 }}</span>
      <span data-testid="migration-status-unresolved">无法确定 {{ preview.summary?.unresolved || 0 }}</span>
      <span data-testid="migration-status-missing">缺字段 {{ preview.summary?.missingFields || 0 }}</span>
      <span data-testid="migration-status-confirmation">待确认 {{ preview.summary?.needsConfirmation || 0 }}</span>
    </div>

    <div class="legacy-migration__groups">
      <template v-for="group in groups" :key="group.key">
        <details v-if="group.items.length" :open="group.key !== 'migrated'">
          <summary>{{ group.label }}（{{ group.items.length }}）</summary>
          <ul>
            <li v-for="item in group.items" :key="`${group.key}-${item.sourcePath || item.componentId}`">
              <strong>{{ item.bindingKey || item.componentId || '未命名组件' }}</strong>
              <span v-if="item.blockId"> · block {{ item.blockId }}</span>
              <span v-if="item.reasons?.length">：{{ item.reasons.join('；') }}</span>
            </li>
          </ul>
        </details>
      </template>
    </div>

    <p v-if="preview.unmappedItems?.length" class="legacy-migration__warning" role="alert">
      未识别项会保留在本次迁移记录中，不会静默删除；应用后仍需人工补齐，保存前不能把草稿标记为无损完成。
    </p>
    <p v-else class="legacy-migration__hint">应用只更新当前编辑器草稿，旧发布包保持不变。</p>

    <footer class="legacy-migration__actions">
      <button type="button" data-testid="migration-preview" @click="$emit('preview')">重新预览</button>
      <button type="button" data-testid="migration-cancel" @click="$emit('cancel')">取消</button>
      <button type="button" data-testid="migration-apply" @click="$emit('apply', preview)">应用到当前草稿</button>
    </footer>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { MIGRATION_STATUS, migrationStatusLabel } from './legacyPresentationMigration';

const props = defineProps({ preview: { type: Object, required: true } });
defineEmits(['preview', 'cancel', 'apply']);

const groups = computed(() => [
  { key: 'migrated', label: migrationStatusLabel(MIGRATION_STATUS.MIGRATED), items: props.preview.migrated || [] },
  { key: 'unresolved', label: migrationStatusLabel(MIGRATION_STATUS.UNRESOLVED), items: props.preview.unresolved || [] },
  { key: 'missing', label: migrationStatusLabel(MIGRATION_STATUS.MISSING_FIELDS), items: props.preview.missingFields || [] },
  { key: 'confirmation', label: migrationStatusLabel(MIGRATION_STATUS.NEEDS_CONFIRMATION), items: props.preview.needsConfirmation || [] }
]);
</script>

<style scoped>
.legacy-migration{margin:0 auto 12px;max-width:1600px;padding:12px 16px;background:#fff8e8;border:1px solid #f1cf84;border-radius:8px;color:#5c4300}.legacy-migration__header{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.legacy-migration h3{margin:0;font-size:15px}.legacy-migration p{margin:4px 0;color:#795b16;font-size:12px}.legacy-migration__lossless{white-space:nowrap;color:#16734a;font-size:12px}.legacy-migration__lossless.blocked{color:#a33a20;font-weight:600}.legacy-migration__summary{display:flex;gap:14px;flex-wrap:wrap;margin:10px 0;font-size:12px}.legacy-migration__summary span{padding:3px 7px;border-radius:4px;background:#fff}.legacy-migration__groups{display:grid;gap:5px}.legacy-migration details{border-top:1px solid rgba(121,91,22,.2);padding-top:5px}.legacy-migration summary{cursor:pointer;font-size:12px}.legacy-migration ul{margin:5px 0 3px;padding-left:20px;font-size:12px}.legacy-migration li{margin:3px 0}.legacy-migration__warning{padding:7px;background:#fff1e8;border-radius:5px;color:#9b3721!important}.legacy-migration__hint{color:#4b6f59!important}.legacy-migration__actions{display:flex;justify-content:flex-end;gap:7px;margin-top:10px}.legacy-migration button{min-height:30px;padding:0 10px;border:1px solid #caa84f;border-radius:5px;background:#fff;color:inherit;cursor:pointer}.legacy-migration button:last-child{background:#8f5d00;color:#fff;border-color:#8f5d00}
</style>
