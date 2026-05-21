import { ref, onMounted } from 'vue';
import { listDictItems } from '@/api/system';

/**
 * 字典下拉 composable。
 * @param {string} dictType - SYS_DICT.dict_type，如 'BIZ_KIND' / 'KPI_SCHEME_STATUS'
 * @returns {{ options: Ref<Array<{value,label}>>, labelOf: (v) => string, loading: Ref<boolean> }}
 *
 * options 形态：[{ value: dictCode, label: dictLabel }]
 * 后端 /sys/dicts/{dictType}/items 返回的 DictItemRespDTO 字段：dictCode / dictLabel / dictValue / sortOrder
 * 这里规整成 { value, label } 形态供 el-select v-for 直接用。
 */
export function useDict(dictType) {
  const options = ref([]);
  const loading = ref(false);
  const map = ref({});  // value -> label 反查表

  async function load() {
    loading.value = true;
    try {
      const r = await listDictItems(dictType);
      const arr = Array.isArray(r) ? r : (r?.data || []);
      const opts = arr.map(d => ({
        value: d.dictCode || d.dictValue || d.value,
        label: d.dictLabel || d.label || d.dictCode
      }));
      options.value = opts;
      const m = {};
      for (const o of opts) m[o.value] = o.label;
      map.value = m;
    } catch {
      options.value = [];
      map.value = {};
    } finally { loading.value = false; }
  }

  // 工具：根据 value 反查 label，找不到则原样返回
  function labelOf(v) {
    if (v == null || v === '') return '-';
    return map.value[v] || v;
  }

  onMounted(load);

  return { options, labelOf, loading, reload: load };
}
