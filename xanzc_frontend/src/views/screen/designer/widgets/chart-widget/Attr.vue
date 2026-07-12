<template>
  <CommonAttr :element="element">
    <el-form-item label="数据源">
      <el-select v-model="bind.dsId" filterable @change="syncBind">
        <el-option v-for="d in datasources" :key="d.id" :label="d.dsName" :value="d.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="周期"><el-input v-model="bind.period" placeholder="LATEST / LAST_10D" @input="syncBind" /></el-form-item>
    <el-form-item label="标题"><el-input v-model="styleCfg.title" @input="syncStyle" /></el-form-item>
    <el-form-item label="刷新(秒)"><el-input-number v-model="styleCfg.refreshSec" :min="0" @change="syncStyle" /></el-form-item>
    <el-form-item label="钻取"><el-switch v-model="drill.drillEnabled" @change="syncDrill" /></el-form-item>
  </CommonAttr>
</template>
<script setup>
import { reactive, ref, onMounted } from 'vue';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { listScreenDatasources } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const datasources = ref([]);
const bind = reactive(parse(props.element.bindJson));
const styleCfg = reactive(parse(props.element.styleJson));
const drill = reactive(parse(props.element.drillJson));
function parse(j) { try { return j ? JSON.parse(j) : {}; } catch { return {}; } }
function syncBind() { props.element.bindJson = JSON.stringify(bind); store.pushSnapshotDebounced(); }
function syncStyle() { props.element.styleJson = JSON.stringify(styleCfg); store.pushSnapshotDebounced(); }
function syncDrill() { props.element.drillJson = JSON.stringify(drill); store.pushSnapshotDebounced(); }
onMounted(async () => { datasources.value = await listScreenDatasources(); });
</script>
