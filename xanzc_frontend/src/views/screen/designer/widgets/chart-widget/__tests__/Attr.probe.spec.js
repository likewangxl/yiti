// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const listScreenDatasources = vi.fn();
const listOrgGroups = vi.fn();
const probeScreenDatasourceColumns = vi.fn();
const queryScreenData = vi.fn();

vi.mock('@/api/screen', () => ({
  listScreenDatasources: (...args) => listScreenDatasources(...args),
  listOrgGroups: (...args) => listOrgGroups(...args),
  probeScreenDatasourceColumns: (...args) => probeScreenDatasourceColumns(...args),
  queryScreenData: (...args) => queryScreenData(...args)
}));
vi.mock('@/stores/screenDesigner', () => ({
  useScreenDesignerStore: () => ({ pushSnapshotDebounced: vi.fn() })
}));
vi.mock('@/views/screen/designer/widgets', () => ({
  chartMetas: [{ innerType: 'GAUGE', needTimeseries: false }]
}));

import Attr from '../Attr.vue';

const ElInputStub = {
  props: ['modelValue'], emits: ['update:modelValue'],
  template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};
const ElButtonStub = {
  emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>'
};
const stubs = {
  CommonAttr: { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': true,
  'el-input': ElInputStub,
  'el-input-number': true,
  'el-button': ElButtonStub,
  'el-switch': true,
  'el-radio-group': true,
  'el-radio-button': true
};

describe('ChartWidget Attr 列探测', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listScreenDatasources.mockResolvedValue([{
      id: 72, dsName: '机构宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ scopeMode: 'SUBJECT', table: 'ORG_INDEX_RESULT' })
    }]);
    listOrgGroups.mockResolvedValue([]);
    probeScreenDatasourceColumns.mockResolvedValue({ columns: ['complete_rate'] });
  });

  it('配置器属性面板不再借 runtime schemaVersion=1 取数，填写原因后只调用已保存数据源 probe 端点', async () => {
    const wrapper = mount(Attr, {
      props: {
        element: { innerType: 'GAUGE', bindJson: JSON.stringify({ dsId: 72, period: 'LAST_1M' }), styleJson: '{}', drillJson: '{}', propValue: {} }
      },
      global: { stubs }
    });
    await flushPromises();

    expect(queryScreenData).not.toHaveBeenCalled();
    const reason = wrapper.find('[data-testid="datasource-probe-reason"]');
    expect(reason.exists()).toBe(true);
    await reason.setValue('配置仪表盘数值列');
    await wrapper.find('[data-testid="datasource-probe-submit"]').trigger('click');
    await flushPromises();

    expect(probeScreenDatasourceColumns).toHaveBeenCalledWith(72, {
      period: 'LAST_1M', dateFrom: null, dateTo: null,
      contextParams: { orgCode: null, empId: null }, reason: '配置仪表盘数值列'
    });
    expect(queryScreenData).not.toHaveBeenCalled();
  });
});
