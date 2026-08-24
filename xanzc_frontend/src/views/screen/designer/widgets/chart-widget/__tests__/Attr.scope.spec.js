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
  chartMetas: [{ innerType: 'GAUGE', needTimeseries: false }]
}));
vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning }
}));

import Attr from '../Attr.vue';

const stubs = {
  CommonAttr: { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': true,
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
});
