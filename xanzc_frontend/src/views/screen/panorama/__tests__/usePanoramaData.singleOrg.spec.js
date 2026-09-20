// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';

const queryScreenData = vi.fn();
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => queryScreenData(...args) }));

import { usePanoramaData } from '../usePanoramaData';

const directory = [
  { orgCode: 'A', orgName: '甲支行', cityCode: '610100' },
  { orgCode: 'B', orgName: '乙支行', cityCode: '610200' }
];

function snapshot(bind, blockId) {
  return { bind: { dsId: blockId, period: 'LATEST', ...bind }, componentType: 'METRIC_CARD' };
}

function viewWithSlots(bindings, extra = {}) {
  const components = Object.entries(bindings).map(([slot, blockId]) => ({
    id: `node-${slot}`, component: 'ChartWidget', blockId,
    innerType: 'METRIC_CARD', propValue: { bindingKey: slot }
  }));
  const bindSnapshots = {};
  for (const [slot, blockId] of Object.entries(bindings)) {
    const binds = {
      deposit: { fields: { value: 'deposit' }, units: { value: 'YUAN' } },
      trend: { fields: { date: 'date', deposit: 'deposit' }, units: { deposit: 'YUAN' } },
      branches: { fields: { orgCode: 'org_code', orgName: 'org_name' }, units: {} },
      branchTrend: { fields: { date: 'date', deposit: 'deposit' }, units: { deposit: 'YUAN' } },
      ranking: { fields: { orgCode: 'org_code', name: 'org_name', value: 'deposit' }, units: { value: 'YUAN' } },
      citySummary: { fields: { cityCode: 'city_code', deposit: 'deposit' }, units: { deposit: 'YUAN' } }
    }[slot] || { fields: { value: 'value' }, units: { value: 'YUAN' } };
    bindSnapshots[String(blockId)] = snapshot(binds, blockId);
  }
  return {
    screenCode: 'SCR_BRANCH_SINGLE',
    runtimeSchemaVersion: 2,
    orgScopeMode: 'NAMED_GROUP',
    renderPackage: {
      canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
      components,
      bindSnapshots
    },
    panoramaInstitutions: directory,
    ...extra
  };
}

function corporateView() {
  return {
    screenCode: 'SCR_CORP_SINGLE',
    runtimeSchemaVersion: 2,
    org_scope_mode: 'NAMED_GROUP',
    renderPackage: {
      canvasStyle: { presentation: { type: 'CODE', template: 'corporate-overview-v1' } },
      components: [
        { id: 'corp-deposit', component: 'ChartWidget', blockId: 101, propValue: { bindingKey: 'corpDeposit' } },
        { id: 'corp-trend', component: 'ChartWidget', blockId: 102, propValue: { bindingKey: 'corpTrend' } },
        { id: 'corp-ranking', component: 'ChartWidget', blockId: 103, propValue: { bindingKey: 'corpRanking' } },
        { id: 'corp-branches', component: 'ChartWidget', blockId: 104, propValue: { bindingKey: 'branches' } }
      ],
      bindSnapshots: {
        '101': snapshot({ fields: { value: 'deposit' }, units: { value: 'YUAN' } }, 101),
        '102': snapshot({ period: 'LAST_6M_EOM', fields: { date: 'date', deposit: 'deposit' }, units: { deposit: 'YUAN' } }, 102),
        '103': snapshot({ fields: { orgCode: 'org_code', name: 'org_name', deposit: 'deposit' }, units: { deposit: 'YUAN' } }, 103),
        '104': snapshot({ fields: { orgCode: 'org_code', orgName: 'org_name' }, units: {} }, 104)
      }
    },
    panorama_institutions: directory
  };
}

function retailView() {
  return {
    screenCode: 'SCR_RETAIL_SINGLE',
    runtimeSchemaVersion: 2,
    orgScopeMode: 'NAMED_GROUP',
    renderPackage: {
      canvasStyle: { presentation: { type: 'CODE', template: 'retail-overview-v1' } },
      components: [
        { id: 'retail-aum', component: 'ChartWidget', blockId: 201, propValue: { bindingKey: 'retailAum' } },
        { id: 'retail-trend', component: 'ChartWidget', blockId: 202, propValue: { bindingKey: 'retailTrend' } },
        { id: 'retail-ranking', component: 'ChartWidget', blockId: 203, propValue: { bindingKey: 'retailRanking' } },
        { id: 'retail-branches', component: 'ChartWidget', blockId: 204, propValue: { bindingKey: 'branches' } }
      ],
      bindSnapshots: {
        '201': snapshot({ fields: { value: 'aum' }, units: { value: 'YUAN' } }, 201),
        '202': snapshot({ period: 'LAST_6M_EOM', fields: { date: 'date', aum: 'aum' }, units: { aum: 'YUAN' } }, 202),
        '203': snapshot({ fields: { orgCode: 'org_code', name: 'org_name', aum: 'aum' }, units: { aum: 'YUAN' } }, 203),
        '204': snapshot({ fields: { orgCode: 'org_code', orgName: 'org_name' }, units: {} }, 204)
      }
    },
    panoramaInstitutions: directory
  };
}

function genericResponse(request) {
  if (request.blockId === 11) return { columns: ['deposit'], rows: [[100000000]] };
  if (request.blockId === 12) return { columns: ['date', 'deposit'], rows: [['2026-09', 100000000]] };
  if (request.blockId === 13) return { columns: ['org_code', 'org_name'], rows: [['A', '来源甲'], ['B', '来源乙']] };
  if (request.blockId === 14) return { columns: ['date', 'deposit'], rows: [['2026-09', 100000000]] };
  return { columns: ['value'], rows: [[100000000]] };
}

describe('usePanoramaData singleOrg', () => {
  beforeEach(() => {
    queryScreenData.mockImplementation(request => Promise.resolve(genericResponse(request)));
  });

  afterEach(() => {
    queryScreenData.mockReset();
    vi.restoreAllMocks();
  });

  it('只接受命名机构组内的上下文机构，所有请求带机构范围且跳过全组槽位', async () => {
    const view = viewWithSlots({ deposit: 11, trend: 12, branches: 13, branchTrend: 14, ranking: 15, citySummary: 16 });
    const state = usePanoramaData(ref(view), ref({ screenCode: view.screenCode, orgCode: 'A' }), {
      autoLoad: false, singleOrg: true
    });

    await state.refresh();

    const requests = queryScreenData.mock.calls.map(([request]) => request);
    expect(requests.map(request => request.blockId)).toEqual(expect.arrayContaining([11, 12, 13]));
    expect(requests.every(request => ![14, 15, 16].includes(request.blockId))).toBe(true);
    expect(requests.every(request => request.contextParams).valueOf()).toBe(true);
    expect(requests.every(request => request.contextParams.orgCode === 'A')).toBe(true);
    expect(state.model.value.institutions.map(item => item.orgCode)).toEqual(['A']);
    expect(state.model.value.trend).toEqual([{ date: '2026-09', deposit: 1, loan: null }]);
  });

  it.each([
    ['legacy context', { orgScopeMode: 'LEGACY_CONTEXT' }, { orgCode: 'A' }, 'NAMED_GROUP'],
    ['unauthorized context org', {}, { orgCode: 'Z' }, 'Z']
  ])('非法 singleOrg 范围（%s）零请求并清空模型', async (_label, viewPatch, context, errorPart) => {
    const view = viewWithSlots({ deposit: 11 }, viewPatch);
    const state = usePanoramaData(ref(view), ref({ screenCode: view.screenCode, ...context }), {
      autoLoad: false, singleOrg: true
    });

    await state.refresh();

    expect(queryScreenData).not.toHaveBeenCalled();
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'SINGLE_ORG_SCOPE_INVALID' })
    ]));
    expect(state.error.value).toContain(errorPart);
  });

  it('上下文机构切换会立即清空旧模型，迟到的旧响应不能回填新机构', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise(resolve => pending.push({ request, resolve })));
    const view = viewWithSlots({ deposit: 11 });
    const context = ref({ screenCode: view.screenCode, orgCode: 'A' });
    const state = usePanoramaData(ref(view), context, { autoLoad: false, singleOrg: true });

    const first = state.refresh();
    await vi.waitFor(() => expect(pending).toHaveLength(1));
    pending[0].resolve({ columns: ['deposit'], rows: [[100000000]] });
    await first;
    expect(state.model.value.kpis[0].value).toBe(1);

    const stale = state.refresh();
    await vi.waitFor(() => expect(pending).toHaveLength(2));
    context.value = { ...context.value, orgCode: 'B' };
    const second = state.refresh();
    expect(state.model.value.kpis).toEqual([]);
    await vi.waitFor(() => expect(pending).toHaveLength(3));
    pending[2].resolve({ columns: ['deposit'], rows: [[200000000]] });
    await second;
    pending[1].resolve({ columns: ['deposit'], rows: [[999000000]] });
    await stale;

    expect(queryScreenData.mock.calls[2][0].contextParams).toEqual({ orgCode: 'B' });
    expect(state.model.value.kpis[0].value).toBe(2);
  });

  it('singleOrg 的 selectBranch 不能切到目录外机构，同机构仍沿用上下文范围', async () => {
    const view = viewWithSlots({ deposit: 11, branchTrend: 14 });
    const context = ref({ screenCode: view.screenCode, orgCode: 'A' });
    const state = usePanoramaData(ref(view), context, { autoLoad: false, singleOrg: true });
    await state.refresh();
    const initialCount = queryScreenData.mock.calls.length;

    await state.selectBranch('B');
    expect(queryScreenData).toHaveBeenCalledTimes(initialCount);
    await state.selectBranch('A');
    const trendRequest = queryScreenData.mock.calls.at(-1)[0];
    expect(trendRequest.blockId).toBe(14);
    expect(trendRequest.contextParams).toEqual({ orgCode: 'A' });
  });

  it.each([
    ['corporate', corporateView(), 101, 102, 104],
    ['retail', retailView(), 201, 202, 204]
  ])('对公/零售 singleOrg 适配仅保留选中目录且所有已请求槽位带机构范围（%s）', async (_label, view, metricBlock, trendBlock, branchesBlock) => {
    queryScreenData.mockImplementation(request => {
      if (request.blockId === metricBlock) return Promise.resolve({ columns: ['deposit'], rows: [[100000000]] });
      if (request.blockId === trendBlock) return Promise.resolve({ columns: ['date', request.blockId === 202 ? 'aum' : 'deposit'], rows: [['2026-09', 100000000]] });
      if (request.blockId === branchesBlock) return Promise.resolve({ columns: ['org_code', 'org_name'], rows: [['A', '来源甲'], ['B', '来源乙']] });
      return Promise.resolve({ columns: ['value'], rows: [[100000000]] });
    });
    const state = usePanoramaData(ref(view), ref({ screenCode: view.screenCode, orgCode: 'B' }), {
      autoLoad: false, singleOrg: true
    });

    await state.refresh();

    const requests = queryScreenData.mock.calls.map(([request]) => request);
    expect(requests.length).toBeGreaterThan(0);
    expect(requests.every(request => request.contextParams?.orgCode === 'B')).toBe(true);
    expect(state.model.value.institutions.map(item => item.orgCode)).toEqual(['B']);
    expect(requests.map(request => request.blockId)).toEqual(expect.arrayContaining([metricBlock, trendBlock, branchesBlock]));
  });
});
