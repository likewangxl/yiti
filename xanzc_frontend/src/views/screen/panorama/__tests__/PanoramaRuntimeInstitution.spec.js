// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';

const harness = vi.hoisted(() => ({
  state: null,
  router: { push: vi.fn(), back: vi.fn() },
  dashboardStub: {
    props: { model: { type: Object, default: () => ({}) } },
    template: '<main data-testid="dashboard-probe">{{ JSON.stringify(model) }}</main>'
  }
}));

vi.mock('vue-router', () => ({ useRouter: () => harness.router }));
vi.mock('@/api/screen', () => ({
  listAvailableScreens: vi.fn(),
  getScreenView: vi.fn()
}));

vi.mock('../PanoramaDashboard.vue', () => ({ default: harness.dashboardStub }));
vi.mock('../CorporateDashboard.vue', () => ({ default: harness.dashboardStub }));
vi.mock('../RetailDashboard.vue', () => ({ default: harness.dashboardStub }));

vi.mock('../usePanoramaData', () => ({
  usePanoramaData: () => harness.state
}));

import PanoramaRuntime from '../PanoramaRuntime.vue';

const rules = {
  allowedOperatingLevels: ['PRIMARY', 'SECONDARY_BRANCH'],
  allowedOrgNatures: ['SECONDARY_BRANCH']
};

const directory = [
  {
    orgCode: 'PARENT',
    orgName: '上级机构',
    cityCode: '610100',
    cityName: '西安',
    operatingLevel: 'PRIMARY',
    orgNature: 'SECONDARY_BRANCH',
    active: true,
    authorized: true,
    parentOrgCode: null,
    located: true,
    lng: 108.94,
    lat: 34.34,
    coordSys: 'GCJ02'
  },
  {
    orgCode: 'CHILD',
    orgName: '下级机构',
    cityCode: '610100',
    cityName: '西安',
    operatingLevel: 'SECONDARY_BRANCH',
    orgNature: 'SECONDARY_BRANCH',
    parentOrgCode: 'PARENT',
    active: true,
    authorized: true,
    located: false
  },
  {
    orgCode: 'FILTERED',
    orgName: '目录内但不允许',
    cityCode: '610100',
    operatingLevel: 'MICRO',
    orgNature: 'MICRO',
    active: true,
    authorized: true,
    located: false
  }
];

function makeView(template, overrides = {}) {
  return {
    screenCode: `SCR_${template}`,
    panoramaInstitutions: directory,
    institutionRules: rules,
    renderPackage: {
      canvasStyle: {
        presentation: {
          type: 'CODE',
          template,
          displaySchemaVersion: 1,
          display: { components: [] }
        }
      }
    },
    ...overrides
  };
}

function makeModel() {
  return {
    title: '运行链路测试',
    kpis: [{ key: 'deposit', value: 10 }],
    trend: [],
    composition: [],
    institutions: [
      {
        orgCode: 'PARENT',
        orgName: '查询结果不应覆盖目录名称',
        metrics: { deposit: 100 },
        trend: [{ date: '2026-09', deposit: 100 }],
        attention: [{ label: '上级关注', count: 1 }]
      },
      {
        orgCode: 'CHILD',
        metrics: { deposit: 40 }
      },
      {
        orgCode: 'NOT_IN_DIRECTORY',
        metrics: { deposit: 999 }
      }
    ],
    rankings: [
      { orgCode: 'PARENT', name: '上级机构', deposit: 100 },
      { orgCode: 'CHILD', name: '下级机构', deposit: 40 },
      { orgCode: 'NOT_IN_DIRECTORY', name: '目录外结果', deposit: 999 }
    ],
    attention: [],
    citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 140 }] } },
    issues: [],
    configuredSlots: ['branches', 'ranking'],
    sourceQualities: {},
    sourceDates: {},
    sourceMetadata: {}
  };
}

function mountRuntime(view, model = makeModel()) {
  harness.state = {
    model: ref(model),
    loading: ref(false),
    error: ref(''),
    lastQueriedAt: ref(''),
    slotIssues: ref({}),
    refresh: vi.fn(),
    selectBranch: vi.fn()
  };
  return mount(PanoramaRuntime, { props: { view, context: {} } });
}

beforeEach(() => {
  harness.router.push.mockReset();
  harness.router.back.mockReset();
});

describe('PanoramaRuntime institutionViewModel 接入', () => {
  it.each([
    ['综合', 'branch-overview-v1'],
    ['对公', 'corporate-overview-v1'],
    ['零售', 'retail-overview-v1'],
    ['支行', 'branch-overview-v1']
  ])('%s 仅使用 v1 授权目录，合并同机构指标并保留原城市汇总', (_label, template) => {
    const wrapper = mountRuntime(makeView(template));
    const dashboardModel = JSON.parse(wrapper.get('[data-testid="dashboard-probe"]').text());

    expect(dashboardModel.institutions.map(item => item.orgCode)).toEqual(['PARENT', 'CHILD']);
    expect(dashboardModel.institutions[0]).toMatchObject({
      orgName: '上级机构',
      metrics: { deposit: 100 },
      contributionStatus: 'CANNOT_AGGREGATE',
      aggregateAllowed: false,
      trend: [{ date: '2026-09', deposit: 100 }],
      attention: [{ label: '上级关注', count: 1 }]
    });
    expect(dashboardModel.citySummaries).toEqual(makeModel().citySummaries);
    expect(dashboardModel.rankings.map(item => item.orgCode)).toEqual(['PARENT', 'CHILD']);
    expect(wrapper.text()).toContain('同时展示，不能现场汇总');
    wrapper.unmount();
  });

  it('v1 缺 institutionRules 时 fail-close，并在统一运行状态显示原因', () => {
    const wrapper = mountRuntime(makeView('branch-overview-v1', { institutionRules: undefined }));
    const dashboardModel = JSON.parse(wrapper.get('[data-testid="dashboard-probe"]').text());

    expect(dashboardModel.institutions).toEqual([]);
    expect(wrapper.get('[data-testid="presentation-runtime-status"]').attributes('data-state')).toBe('UNCONFIGURED');
    expect(wrapper.get('[data-testid="presentation-runtime-status"]').text())
      .toContain('未提供允许的机构层级或机构性质白名单');
    expect(wrapper.text()).toContain('未提供允许的机构层级或机构性质白名单');
    wrapper.unmount();
  });

  it('v1 缺服务端 panoramaInstitutions 时 fail-close，不使用查询机构行补目录', () => {
    const wrapper = mountRuntime(makeView('branch-overview-v1', { panoramaInstitutions: undefined }));
    const dashboardModel = JSON.parse(wrapper.get('[data-testid="dashboard-probe"]').text());

    expect(dashboardModel.institutions).toEqual([]);
    expect(wrapper.get('[data-testid="presentation-runtime-status"]').text())
      .toContain('机构展示必须使用服务端授权目录');
    wrapper.unmount();
  });

  it('v1 机构规则过滤目录外查询行时不再把预期过滤误报为运行故障', () => {
    const model = makeModel();
    model.issues = [
      { slot: 'ranking', code: 'UNAUTHORIZED_ORG', message: '目录外行已过滤' },
      { slot: 'ranking', code: 'MISSING_COLUMN', message: '真实字段缺失' }
    ];
    const wrapper = mountRuntime(makeView('branch-overview-v1'), model);
    const dashboardModel = JSON.parse(wrapper.get('[data-testid="dashboard-probe"]').text());

    expect(dashboardModel.issues).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'UNAUTHORIZED_ORG' })
    ]));
    expect(dashboardModel.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'MISSING_COLUMN' })
    ]));
    wrapper.unmount();
  });

  it('非 v1 继续使用旧模型机构目录，不套用新规则过滤', () => {
    const view = makeView('branch-overview-v1', {
      renderPackage: { canvasStyle: { presentation: { template: 'branch-overview-v1' } } },
      institutionRules: undefined
    });
    const wrapper = mountRuntime(view);
    const dashboardModel = JSON.parse(wrapper.get('[data-testid="dashboard-probe"]').text());

    expect(dashboardModel.institutions.map(item => item.orgCode)).toEqual([
      'PARENT', 'CHILD', 'NOT_IN_DIRECTORY'
    ]);
    expect(wrapper.find('[data-testid="presentation-runtime-status"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
