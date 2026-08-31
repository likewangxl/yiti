// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const mocks = vi.hoisted(() => ({
  listScreenDatasources: vi.fn(),
  listOrgGroups: vi.fn(),
  probeScreenDatasourceColumns: vi.fn(),
  pushSnapshotDebounced: vi.fn(),
  warning: vi.fn()
}));

vi.mock('@/api/screen', () => ({
  listScreenDatasources: (...args) => mocks.listScreenDatasources(...args),
  listOrgGroups: (...args) => mocks.listOrgGroups(...args),
  probeScreenDatasourceColumns: (...args) => mocks.probeScreenDatasourceColumns(...args)
}));
vi.mock('@/stores/screenDesigner', () => ({
  useScreenDesignerStore: () => ({
    bizLine: 'RETAIL',
    orgScopeMode: 'NAMED_GROUP',
    screenCode: 'SCR_RETAIL',
    pushSnapshotDebounced: mocks.pushSnapshotDebounced
  })
}));
vi.mock('@/views/screen/designer/widgets', () => ({
  chartMetas: [
    { innerType: 'GAUGE', needTimeseries: false },
    { innerType: 'KPI_DETAIL_TABLE', needTimeseries: false, needKinds: ['KPI_DETAIL'] }
  ]
}));
vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning }
}));

import Attr from '../Attr.vue';

const stubs = {
  CommonAttr: { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': {
    props: ['label', 'value', 'disabled'],
    template: '<option :value="value" :disabled="disabled" v-bind="$attrs">{{ label }}</option>'
  },
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input />' },
  'el-input-number': true,
  'el-button': { template: '<button><slot /></button>' },
  'el-switch': true,
  'el-radio-group': true,
  'el-radio-button': true
};

describe('ChartWidget Attr 数据源范围过滤', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listScreenDatasources.mockResolvedValue([
      {
        id: 9009, dsName: 'KPI细项', dsType: 'SINGLE', sourceKind: 'KPI_DETAIL', bizLine: 'RETAIL',
        configJson: JSON.stringify({ scopeMode: 'SUBJECT', subjectType: 'EMP' })
      },
      {
        id: 9010, dsName: '机构宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL',
        configJson: JSON.stringify({ scopeMode: 'NAMED_GROUP', table: 'ORG_INDEX_RESULT', subjectCol: 'org_code' })
      }
    ]);
    mocks.listOrgGroups.mockResolvedValue([]);
  });

  it('加载后若已有绑定不再属于候选，会清除绑定、标脏并提示重新选择', async () => {
    const element = {
      innerType: 'GAUGE',
      bindJson: JSON.stringify({ dsId: 9009, dsType: 'SINGLE', period: 'LATEST' }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = mount(Attr, { props: { element }, global: { stubs } });
    await flushPromises();

    const bind = JSON.parse(element.bindJson);
    expect(bind.dsId).toBeUndefined();
    expect(bind.dsType).toBeUndefined();
    expect(mocks.pushSnapshotDebounced).toHaveBeenCalled();
    expect(mocks.warning).toHaveBeenCalledWith(expect.stringContaining('命名机构组'));
    expect(wrapper.text()).toContain('机构宽表（ORG_INDEX_RESULT / org_code）');
  });

  it('数据源目录为空时视为目录加载失败，不清除已有绑定、不标脏也不告警', async () => {
    mocks.listScreenDatasources.mockResolvedValue([]);
    const element = {
      innerType: 'GAUGE',
      bindJson: JSON.stringify({ dsId: 9009, dsType: 'SINGLE', period: 'LATEST' }),
      styleJson: '{}', drillJson: '{}', propValue: {}
    };
    mount(Attr, { props: { element }, global: { stubs } });
    await flushPromises();

    expect(JSON.parse(element.bindJson)).toMatchObject({ dsId: 9009, dsType: 'SINGLE' });
    expect(mocks.pushSnapshotDebounced).not.toHaveBeenCalled();
    expect(mocks.warning).not.toHaveBeenCalled();
  });

  it('候选交集为空时说明范围冲突，并以禁用项列出符合图表类型但超出范围的数据源', async () => {
    mocks.listScreenDatasources.mockResolvedValue([
      {
        id: 9101, dsName: '员工KPI明细', dsType: 'SINGLE', sourceKind: 'KPI_DETAIL', bizLine: 'RETAIL',
        configJson: JSON.stringify({ scopeMode: 'SUBJECT', subjectType: 'EMP' })
      },
      {
        id: 9102, dsName: '机构KPI明细', dsType: 'SINGLE', sourceKind: 'KPI_DETAIL', bizLine: 'RETAIL',
        configJson: JSON.stringify({ scopeMode: 'SUBJECT', subjectType: 'ORG' })
      }
    ]);
    const element = {
      innerType: 'KPI_DETAIL_TABLE', bindJson: '{}', styleJson: '{}', drillJson: '{}', propValue: {}
    };
    const wrapper = mount(Attr, { props: { element }, global: { stubs } });
    await flushPromises();

    const conflict = wrapper.find('[data-testid="datasource-scope-conflict"]');
    expect(conflict.exists()).toBe(true);
    expect(conflict.text()).toContain('员工KPI明细');
    expect(conflict.text()).toContain('机构KPI明细');
    expect(conflict.text()).toContain('需切换为传统上下文');
    expect(conflict.text()).toContain('换用机构宽表组件');
    const blocked = wrapper.findAll('[data-testid="datasource-scope-blocked"]');
    expect(blocked).toHaveLength(2);
    expect(blocked.every(option => option.element.disabled)).toBe(true);
    expect(wrapper.find('select').element.options).toHaveLength(2);
    expect(JSON.parse(element.bindJson)).toEqual({});
  });
});
