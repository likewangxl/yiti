<template>
  <div class="presentation-editor-shell">
    <LegacyMigrationPanel v-if="migrationPreview" :preview="migrationPreview"
                          @preview="previewMigration" @cancel="cancelMigration" @apply="applyMigration" />
  <section class="presentation-editor" aria-label="经营大屏组件配置">
    <aside class="presentation-editor__list">
      <header><h2>页面组件</h2><span>{{ session.components.length }} 项</span></header>
      <div class="presentation-editor__add">
        <select v-model="newType" aria-label="新增组件类型">
          <option v-for="type in COMPONENT_TYPES" :key="type" :value="type">{{ typeLabel(type) }}</option>
        </select>
        <button type="button" @click="add">新增</button>
      </div>
      <button v-for="component in orderedComponents" :key="component.componentId" type="button"
              class="presentation-editor__item" :class="{ active: selectedId === component.componentId }"
              :data-component-id="component.componentId"
              @click="select(component.componentId)">
        <span>{{ componentTitle(component) || typeLabel(component.componentType) }}</span>
        <small>{{ component.layoutRegion }} · {{ component.editorStatus || 'BOUND' }}</small>
      </button>
      <p v-if="!session.components.length" class="presentation-editor__empty">尚未添加展示组件。</p>
    </aside>

    <main class="presentation-editor__preview">
      <header><div><h2>即时预览</h2><p>本地样式预览，保存后才能进行真实数据验证。</p></div>
        <span :class="{ dirty: session.dirty }">{{ session.dirty ? '未保存' : '已同步' }}</span></header>
      <div class="presentation-editor__canvas">
        <article v-for="component in visibleComponents" :key="component.componentId"
                 :class="['presentation-editor__preview-card', `region-${component.layoutRegion.toLowerCase()}`]"
                 @click="select(component.componentId)">
          <small>{{ typeLabel(component.componentType) }}</small>
          <h3>{{ componentTitle(component) || '自动标题待绑定' }}</h3>
          <strong>{{ previewValue(component) }}</strong>
          <p>{{ component.text?.subtitle || component.text?.description || '配置数据来源后显示真实内容' }}</p>
        </article>
        <p v-if="!visibleComponents.length" class="presentation-editor__empty">没有可见组件。</p>
      </div>
    </main>

    <aside class="presentation-editor__properties">
      <template v-if="selected">
        <header><h2>组件配置</h2><span>{{ typeLabel(selected.componentType) }}</span></header>
        <div class="presentation-editor__toolbar">
          <button type="button" @click="duplicate">复制</button>
          <button type="button" @click="move('UP')">上移</button>
          <button type="button" @click="move('DOWN')">下移</button>
          <button type="button" @click="toggle">{{ selected.visible ? '隐藏' : '显示' }}</button>
          <button type="button" class="danger" @click="remove">删除</button>
        </div>
        <label>标题模式<select :value="selected.text?.titleMode" @change="changeText({ titleMode: $event.target.value })">
          <option value="AUTO">自动继承指标名</option><option value="CUSTOM">自定义</option>
        </select></label>
        <label>组件标题<input :value="selected.text?.title || ''" placeholder="留空恢复自动标题"
          @input="changeTitle($event.target.value)" /></label>
        <label>副标题<input :value="selected.text?.subtitle || ''" @input="changeText({ subtitle: $event.target.value })" /></label>
        <label>说明<textarea :value="selected.text?.description || ''" rows="2"
          @input="changeText({ description: $event.target.value })"></textarea></label>
        <label>主展示字段<input :value="selected.content?.mainField || ''"
          @input="changeContent({ mainField: $event.target.value })" /></label>
        <label>数据区块<select :value="selected.dataRefs?.[0]?.blockId || ''" @change="bind($event.target.value)">
          <option value="">待绑定</option>
          <option v-for="item in blockOptions" :key="item.blockId" :value="item.blockId">{{ item.label }}</option>
        </select></label>
        <p v-if="selected.editorStatus === 'DRAFT_UNBOUND'" class="presentation-editor__warning">该组件尚未绑定已有blockId，不能保存发布。</p>
        <p v-if="session.reviewRequiredComponentIds?.includes(selected.componentId)" class="presentation-editor__warning">数据来源已变化，请核对自定义标题。</p>
        <div class="presentation-editor__row">
          <label>显示单位<select :value="selected.format?.displayUnit || 'AUTO'" @change="changeFormat({ displayUnit: $event.target.value })">
            <option v-for="unit in DISPLAY_UNITS" :key="unit" :value="unit">{{ unit }}</option>
          </select></label>
          <label>小数位<input type="number" min="0" max="8" :value="selected.format?.decimals ?? 2"
            @input="changeFormat({ decimals: Number($event.target.value) })" /></label>
        </div>
        <label>点击动作<select :value="selected.interaction?.action || 'NONE'" @change="changeAction($event.target.value)">
          <option v-for="action in INTERACTION_ACTIONS" :key="action" :value="action">{{ action }}</option>
        </select></label>
        <details><summary>高级身份信息</summary><p>componentId：{{ selected.componentId }}</p><p>区域：{{ selected.layoutRegion }} · 顺序：{{ selected.order }}</p></details>
      </template>
      <p v-else class="presentation-editor__empty">从左侧选择一个组件。</p>
      <footer><button type="button" :disabled="!session.dirty" @click="$emit('cancel')">取消本地修改</button></footer>
    </aside>
  </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { DISPLAY_UNITS, INTERACTION_ACTIONS, resolveComponentTitle } from '../contract/displayContract';
import LegacyMigrationPanel from '../migration/LegacyMigrationPanel.vue';
import {
  applyLegacyMigrationToEditorDraft,
  previewLegacyMigration
} from '../migration/legacyPresentationMigration';
import {
  COMPONENT_TYPES, addComponent, bindComponent, deleteComponent, duplicateComponent, moveComponent,
  selectComponent, setComponentTitle, setComponentVisibility, updateComponentContent,
  updateComponentFormat, updateComponentInteraction, updateComponentText
} from './presentationEditorModel';

const props = defineProps({
  session: { type: Object, required: true },
  blockOptions: { type: Array, default: () => [] },
  // 旧 canvas/renderPackage 或其 JSON。只用于本地预览和生成草稿，不在此组件发起保存。
  migrationSource: { type: [Object, String], default: null },
  legacySource: { type: [Object, String], default: null },
  migrationPreview: { type: Object, default: null }
});
const emit = defineEmits(['update:session', 'cancel', 'migration-preview', 'migration-cancel', 'migration-applied']);
const newType = ref('METRIC_CARD');
const selectedId = computed(() => props.session.selectedComponentId);
const selected = computed(() => props.session.components.find(item => item.componentId === selectedId.value) || null);
const orderedComponents = computed(() => [...props.session.components].sort((a, b) => a.layoutRegion.localeCompare(b.layoutRegion) || a.order - b.order));
const visibleComponents = computed(() => orderedComponents.value.filter(item => item.visible));
const migrationInput = computed(() => props.migrationSource ?? props.legacySource);
const migrationPreview = computed(() => {
  if (props.migrationPreview) return props.migrationPreview;
  if (!migrationInput.value) return null;
  return previewLegacyMigration(migrationInput.value, {
    type: props.session.presentation?.type || 'CODE',
    template: props.session.presentation?.template || 'branch-overview-v1'
  });
});
const update = next => emit('update:session', next);
const typeLabel = type => ({ METRIC_CARD: '指标卡', COMPLETION: '完成情况', TREND: '趋势图', COMPOSITION_TABS: '业务结构', RANKING: '机构排名', MAP: '地图', DETAIL_TABLE: '明细表' }[type] || type);
const componentTitle = component => resolveComponentTitle(component, { metricName: component.dataRefs?.[0]?.metricName });
const previewValue = component => component.componentType === 'MAP' ? '地图视图' : component.componentType === 'DETAIL_TABLE' ? '表格预览' : '—';
function select(id) { update(selectComponent(props.session, id)); }
function add() { update(addComponent(props.session, newType.value)); }
function duplicate() { update(duplicateComponent(props.session, selectedId.value)); }
function remove() { update(deleteComponent(props.session, selectedId.value)); }
function move(direction) { update(moveComponent(props.session, selectedId.value, direction)); }
function toggle() { update(setComponentVisibility(props.session, selectedId.value, !selected.value.visible)); }
function changeTitle(title) { update(setComponentTitle(props.session, selectedId.value, title)); }
function changeText(patch) { update(updateComponentText(props.session, selectedId.value, patch)); }
function changeFormat(patch) { update(updateComponentFormat(props.session, selectedId.value, patch)); }
function changeContent(patch) { update(updateComponentContent(props.session, selectedId.value, patch)); }
function changeAction(action) { update(updateComponentInteraction(props.session, selectedId.value, { action, target: action === 'OPEN_BUSINESS_LINE' ? 'COMMON' : '' })); }
function bind(value) {
  const option = props.blockOptions.find(item => String(item.blockId) === String(value));
  if (!option) return;
  update(bindComponent(props.session, selectedId.value, option.blockId, option));
}
function previewMigration() { emit('migration-preview', migrationPreview.value); }
function cancelMigration() {
  // 取消迁移只通知父级关闭预览，不改变当前会话，也不触发保存或发布；
  // 不复用编辑器整体 cancel，避免误丢弃迁移前已经存在的本地修改。
  emit('migration-cancel', migrationPreview.value);
}
function applyMigration(preview) {
  if (!preview?.presentation) return;
  const next = applyLegacyMigrationToEditorDraft(props.session, preview);
  update(next);
  emit('migration-applied', preview);
}
</script>

<style scoped>
.presentation-editor-shell{width:100%}
.presentation-editor{display:grid;grid-template-columns:230px minmax(360px,1fr) 320px;gap:12px;max-width:1600px;margin:0 auto 14px;min-height:540px}.presentation-editor>aside,.presentation-editor>main{background:#fff;border:1px solid #e3eaf2;border-radius:8px;padding:14px;min-width:0}.presentation-editor header{display:flex;justify-content:space-between;gap:8px;align-items:flex-start}.presentation-editor h2{font-size:15px;margin:0}.presentation-editor__add,.presentation-editor__toolbar,.presentation-editor__row{display:flex;gap:6px;margin:12px 0}.presentation-editor__add select{min-width:0;flex:1}.presentation-editor__item{display:grid;width:100%;margin:6px 0;text-align:left}.presentation-editor__item small{color:#718096}.presentation-editor__item.active{border-color:#6b83e8;background:#f1f4ff}.presentation-editor__preview header p{font-size:12px;color:#718096}.presentation-editor__preview header span{font-size:12px;color:#16805d}.presentation-editor__preview header span.dirty{color:#b26a00}.presentation-editor__canvas{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;padding:12px;background:#07102c;border-radius:8px;min-height:430px}.presentation-editor__preview-card{padding:12px;color:#eaf2ff;background:#10284b;border:1px solid #315783;border-radius:7px;cursor:pointer}.presentation-editor__preview-card h3{margin:5px 0}.presentation-editor__preview-card p,.presentation-editor__preview-card small{color:#9fc2df;font-size:11px}.presentation-editor__properties{overflow:auto;max-height:680px}.presentation-editor__properties label{display:grid;gap:5px;margin:10px 0;font-size:12px}.presentation-editor__properties input,.presentation-editor__properties select,.presentation-editor__properties textarea{width:100%;box-sizing:border-box}.presentation-editor__toolbar{flex-wrap:wrap}.presentation-editor__toolbar .danger{color:#a11a2b}.presentation-editor__warning{padding:7px;color:#8b5b00;background:#fff8e5;border-radius:5px;font-size:12px}.presentation-editor__empty{color:#718096;font-size:13px}.presentation-editor__properties footer{margin-top:16px}@media(max-width:1100px){.presentation-editor{grid-template-columns:200px minmax(0,1fr)}.presentation-editor__properties{grid-column:1/-1;max-height:none}}@media(max-width:760px){.presentation-editor{display:block}.presentation-editor>aside,.presentation-editor>main{margin-bottom:10px}.presentation-editor__canvas{grid-template-columns:1fr}}
</style>
