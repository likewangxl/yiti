import { describe, expect, it, vi } from 'vitest';

import {
  createRuntimeDataVerificationService,
  OVERALL_STATUS,
  SLOT_STATUS
} from '../runtimeDataVerification.js';

const makeComponent = (slot, blockId, bind) => ({
  id: `component-${slot}`,
  component: 'ChartWidget',
  blockId,
  propValue: { bindingKey: slot },
  bindJson: JSON.stringify(bind)
});

const makeCanvas = ({ version = 3, components = [], blocks = [] } = {}) => ({
  screenId: 7,
  screenCode: 'SCR_VERIFY',
  canvasVersion: version,
  canvasDraftJson: JSON.stringify({ schemaVersion: 2, components }),
  blocks
});

const makeView = ({ version = 2, components = [], institutions = [] } = {}) => ({
  screenId: 7,
  screenCode: 'SCR_VERIFY',
  runtimeSchemaVersion: version,
  state: 'draft',
  renderPackageJson: JSON.stringify({
    schemaVersion: version,
    canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
    components,
    bindSnapshots: Object.fromEntries(components.map(item => [String(item.blockId), { bind: JSON.parse(item.bindJson) }]))
  }),
  panoramaInstitutions: institutions
});

const singleBinding = (dsId = 11, field = 'amount') => ({
  dsId,
  period: 'LATEST',
  fields: { value: field },
  units: { value: 'HUNDRED_MILLION' }
});

function deps({ canvas, view, query = vi.fn() } = {}) {
  return {
    getScreenCanvas: vi.fn().mockResolvedValue(canvas),
    getScreenView: vi.fn().mockResolvedValue(view),
    queryScreenData: query
  };
}

describe('runtimeDataVerification 草稿数据结构核验', () => {
  it('未保存绑定先阻断，不请求取数，也不自动写入', async () => {
    const bind = singleBinding();
    const saved = makeComponent('deposit', 101, bind);
    const query = vi.fn().mockResolvedValue({ columns: ['amount'], rows: [[1]] });
    const service = createRuntimeDataVerificationService(deps({
      canvas: makeCanvas({ components: [saved], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      view: makeView({ components: [saved] }), query
    }));

    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' },
      canvas: makeCanvas({ version: 3, components: [makeComponent('deposit', 101, singleBinding(99))] }),
      slotOrder: ['deposit'],
      bindingState: { deposit: singleBinding(99) }
    });

    expect(result.overallStatus).toBe(OVERALL_STATUS.UNSAVED);
    expect(result.message).toContain('先保存');
    expect(query).not.toHaveBeenCalled();
    expect(result.results[0].status).toBe(SLOT_STATUS.UNVERIFIED);
  });

  it('只用已保存 blockId + screenCode + schemaVersion + draft 请求，绝不把 dsId 上送', async () => {
    const bind = singleBinding(11);
    const component = makeComponent('deposit', 101, bind);
    const query = vi.fn()
      .mockResolvedValueOnce({ columns: ['amount', 'data_date'], rows: [[0, '2026-09-07']] })
      .mockResolvedValueOnce({ columns: ['amount', 'data_date'], rows: [[2, '2026-09-07']] });
    const service = createRuntimeDataVerificationService(deps({
      canvas: makeCanvas({ components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      view: makeView({ components: [component] }), query
    }));

    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' },
      canvas: makeCanvas({ components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      slotOrder: ['deposit'],
      bindingState: { deposit: bind }
    });
    // The current binding matches the saved binding; the data request is now
    // allowed and must still use only the saved block identity.
    expect(result.overallStatus).toBe(OVERALL_STATUS.HAS_GAPS);
    expect(query).toHaveBeenCalled();
    expect(query.mock.calls[0][0]).not.toHaveProperty('dsId');
  });

  it('草稿请求不带 dsId，区分零值/null/非数字并标日期不可核验', async () => {
    const bind = {
      dsId: 11,
      period: 'LATEST',
      fields: { value: 'amount' },
      units: { value: 'HUNDRED_MILLION' }
    };
    const component = makeComponent('deposit', 101, bind);
    const query = vi.fn().mockResolvedValue({
      columns: ['amount'],
      rows: [[0], [null], ['oops']]
    });
    const service = createRuntimeDataVerificationService(deps({
      canvas: makeCanvas({ components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      view: makeView({ components: [component] }), query
    }));
    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' },
      canvas: makeCanvas({ components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      slotOrder: ['deposit'],
      bindingState: { deposit: bind }
    });
    expect(query).toHaveBeenCalledWith({
      schemaVersion: 2,
      previewState: 'draft',
      screenCode: 'SCR_VERIFY',
      blockId: 101,
      period: 'LATEST'
    });
    expect(query.mock.calls[0][0]).not.toHaveProperty('dsId');
    const row = result.results.find(item => item.slot === 'deposit');
    expect(row.rowCount).toBe(3);
    expect(row.issues.map(item => item.code)).toEqual(expect.arrayContaining([
      'MULTIPLE_ROWS', 'NULL_VALUE', 'INVALID_NUMBER', 'DATE_UNVERIFIABLE'
    ]));
    expect(row.zeroCount).toBe(1);
    expect(row.status).not.toBe(SLOT_STATUS.MISSING_CONFIG);
  });

  it('并发最多三个且相同请求去重；branchTrend 只请求授权第一机构并标单样本', async () => {
    const pending = [];
    let active = 0;
    let maxActive = 0;
    const query = vi.fn((body) => new Promise(resolve => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      pending.push({ body, resolve: value => { active -= 1; resolve(value); } });
    }));
    const slots = ['deposit', 'loan', 'customers', 'branchTrend'];
    const components = slots.map((slot, index) => makeComponent(slot, 101 + index, {
      dsId: 11 + index, period: slot === 'branchTrend' ? 'LAST_6M_EOM' : 'LATEST',
      fields: slot === 'branchTrend' ? { date: 'd', deposit: 'amount' } : { value: 'amount' },
      units: slot === 'branchTrend' ? { deposit: 'HUNDRED_MILLION' } : { value: 'HUNDRED_MILLION' }
    }));
    const blocks = components.map(item => ({ id: item.blockId, bindJson: item.bindJson }));
    const canvas = makeCanvas({ components, blocks });
    const view = makeView({
      components,
      institutions: [{ orgCode: 'AUTH-1' }, { orgCode: 'AUTH-2' }]
    });
    const service = createRuntimeDataVerificationService(deps({ canvas, view, query }));
    const first = service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas, slotOrder: slots,
      bindingState: Object.fromEntries(slots.map((slot, index) => [slot, JSON.parse(components[index].bindJson)]))
    });
    const second = service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas, slotOrder: slots,
      bindingState: Object.fromEntries(slots.map((slot, index) => [slot, JSON.parse(components[index].bindJson)]))
    });

    await vi.waitFor(() => expect(query).toHaveBeenCalledTimes(3));
    expect(maxActive).toBeLessThanOrEqual(3);
    pending.splice(0, 3).forEach(({ body, resolve }) => {
      const isTrend = body.blockId === 104;
      resolve(isTrend
        ? { columns: ['d', 'amount'], rows: [['2026-09', 1]] }
        : { columns: ['amount'], rows: [[1]] });
    });
    await vi.waitFor(() => expect(query).toHaveBeenCalledTimes(4));
    expect(query.mock.calls.map(([body]) => body.contextParams).filter(Boolean)).toEqual([{ orgCode: 'AUTH-1' }]);
    const last = pending.shift();
    last.resolve({ columns: ['d', 'amount'], rows: [['2026-09', 1]] });
    const [firstResult, secondResult] = await Promise.all([first, second]);
    expect(firstResult.overallStatus).toBe(OVERALL_STATUS.STALE);
    expect(secondResult.results.find(item => item.slot === 'branchTrend')).toMatchObject({ sampleOnly: true });
  });

  it('再读画布版本变化会废弃本次结果，401/403 清空结果', async () => {
    const bind = singleBinding();
    const component = makeComponent('deposit', 101, bind);
    let reads = 0;
    const getScreenCanvas = vi.fn().mockImplementation(() => Promise.resolve(
      makeCanvas({ version: reads++ === 0 ? 3 : 4, components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] })
    ));
    const query = vi.fn().mockResolvedValue({ columns: ['amount'], rows: [[1]] });
    const service = createRuntimeDataVerificationService({
      canvas: makeCanvas({ version: 3, components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      view: makeView({ components: [component] }),
      getScreenView: vi.fn().mockResolvedValue(makeView({ components: [component] })),
      query,
      getScreenCanvas
    });
    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' },
      canvas: makeCanvas({ version: 3, components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      slotOrder: ['deposit'], bindingState: { deposit: bind }
    });
    expect(result.overallStatus).toBe(OVERALL_STATUS.STALE);
    expect(result.results).toEqual([]);

    const forbidden = Object.assign(new Error('forbidden'), { status: 403 });
    const denied = createRuntimeDataVerificationService(deps({
      canvas: makeCanvas({ version: 3, components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      view: makeView({ components: [component] }),
      query: vi.fn().mockRejectedValue(forbidden)
    }));
    const deniedResult = await denied.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' },
      canvas: makeCanvas({ version: 3, components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] }),
      slotOrder: ['deposit'], bindingState: { deposit: bind }
    });
    expect(deniedResult.overallStatus).toBe(OVERALL_STATUS.PERMISSION_DENIED);
    expect(deniedResult.results).toEqual([]);
  });

  it('拒绝未知或缺失的 schemaVersion/画布版本，不用 1 作为默认安全降级', async () => {
    const bind = singleBinding();
    const component = makeComponent('deposit', 101, bind);
    const query = vi.fn().mockResolvedValue({ columns: ['amount'], rows: [[1]] });
    const base = makeCanvas({ version: 3, components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] });
    const unknownSchema = createRuntimeDataVerificationService({
      getScreenCanvas: vi.fn().mockResolvedValue(base),
      getScreenView: vi.fn().mockResolvedValue({ ...makeView({ components: [component] }), runtimeSchemaVersion: '2' }),
      queryScreenData: query
    });
    const unknown = await unknownSchema.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas: base,
      slotOrder: ['deposit'], bindingState: { deposit: bind }
    });
    expect(unknown.overallStatus).toBe(OVERALL_STATUS.CONFIG_ERROR);
    expect(query).not.toHaveBeenCalled();

    const missingVersion = createRuntimeDataVerificationService({
      getScreenCanvas: vi.fn().mockResolvedValue({ ...base, canvasVersion: null }),
      getScreenView: vi.fn().mockResolvedValue({ ...makeView({ components: [component] }), runtimeSchemaVersion: undefined }),
      queryScreenData: query
    });
    const missing = await missingVersion.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas: { ...base, canvasVersion: null },
      slotOrder: ['deposit'], bindingState: { deposit: bind }
    });
    expect(missing.overallStatus).toBe(OVERALL_STATUS.STALE);
  });

  it('日期必须能解析为年月或日期，非法日期保持不可核验；零售单值槽多行不通过', async () => {
    const retailBind = {
      dsId: 21, period: 'LATEST', fields: { value: 'aum', date: 'when' },
      units: { value: 'HUNDRED_MILLION' }
    };
    const component = makeComponent('retailAum', 201, retailBind);
    const query = vi.fn().mockResolvedValue({
      columns: ['aum', 'when'], rows: [[1, 'not-a-date'], [2, '2026-13-01']]
    });
    const canvas = makeCanvas({ components: [component], blocks: [{ id: 201, bindJson: JSON.stringify(retailBind) }] });
    const service = createRuntimeDataVerificationService(deps({
      canvas, view: makeView({ components: [component] }), query
    }));
    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas,
      slotOrder: ['retailAum'], bindingState: { retailAum: retailBind }
    });
    const row = result.results[0];
    expect(row.status).toBe(SLOT_STATUS.STRUCTURE_ERROR);
    expect(row.issues.map(item => item.code)).toEqual(expect.arrayContaining(['MULTIPLE_ROWS', 'INVALID_DATE', 'DATE_UNVERIFIABLE']));
  });

  it('数据源静态常量 SQL 只标不可信来源，不把一行当作真实数据结构通过', async () => {
    const bind = singleBinding(11);
    const component = makeComponent('deposit', 101, bind);
    const canvas = makeCanvas({ components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] });
    const service = createRuntimeDataVerificationService(deps({
      canvas, view: makeView({ components: [component] }),
      query: vi.fn().mockResolvedValue({ columns: ['amount'], rows: [[1]] })
    }));
    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas,
      datasources: [{ id: 11, configJson: JSON.stringify({ sql: 'SELECT 1 AS amount' }) }],
      slotOrder: ['deposit'], bindingState: { deposit: bind }
    });
    expect(result.results[0].issues.map(item => item.code)).toContain('CONSTANT_SOURCE');
    expect(result.results[0].status).toBe(SLOT_STATUS.VERIFIED_WITH_WARNINGS);
  });

  it('历史非 CODE 草稿明确不适用，不把旧画布区块当作代码槽位完整', async () => {
    const legacyComponent = { component: 'ChartWidget', blockId: 101, innerType: 'BAR_COMPARE' };
    const canvas = makeCanvas({ components: [legacyComponent], blocks: [{ id: 101, bindJson: '{}' }] });
    const result = await createRuntimeDataVerificationService(deps({
      canvas, view: { ...makeView({ components: [] }), renderPackageJson: JSON.stringify({ schemaVersion: 1, components: [legacyComponent] }) },
      query: vi.fn()
    })).verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas, slotOrder: ['deposit'], bindingState: {}
    });
    expect(result.applicable).toBe(false);
    expect(result.message).toContain('不适用');
  });

  it('屏、当前画布、服务端画布和草稿视图身份必须一致，否则零请求', async () => {
    const bind = singleBinding();
    const component = makeComponent('deposit', 101, bind);
    const canvas = makeCanvas({ components: [component], blocks: [{ id: 101, bindJson: JSON.stringify(bind) }] });
    const query = vi.fn().mockResolvedValue({ columns: ['amount'], rows: [[1]] });
    const mismatchView = { ...makeView({ components: [component] }), screenId: 8, screenCode: 'SCR_OTHER' };
    const service = createRuntimeDataVerificationService(deps({ canvas, view: mismatchView, query }));
    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY' }, canvas,
      slotOrder: ['deposit'], bindingState: { deposit: bind }
    });
    expect(result.overallStatus).toBe(OVERALL_STATUS.CONFIG_ERROR);
    expect(query).not.toHaveBeenCalled();
  });

  it('草稿视图缺少 panoramaInstitutions 时不信任宿主 screen 目录，也不请求 branchTrend', async () => {
    const bind = {
      dsId: 12, period: 'LAST_6M_EOM', fields: { date: 'd', deposit: 'amount' },
      units: { deposit: 'HUNDRED_MILLION' }
    };
    const component = makeComponent('branchTrend', 102, bind);
    const canvas = makeCanvas({ components: [component], blocks: [{ id: 102, bindJson: JSON.stringify(bind) }] });
    const query = vi.fn().mockResolvedValue({ columns: ['d', 'amount'], rows: [['2026-09', 1]] });
    const view = makeView({ components: [component] });
    delete view.panoramaInstitutions;
    const result = await createRuntimeDataVerificationService(deps({ canvas, view, query })).verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY', panoramaInstitutions: [{ orgCode: 'HOST_ONLY' }] },
      canvas, slotOrder: ['branchTrend'], bindingState: { branchTrend: bind }
    });
    expect(query).not.toHaveBeenCalled();
    expect(result.results[0]).toMatchObject({ status: SLOT_STATUS.NOT_VERIFIABLE, sampleOnly: true });
  });

  it('校验趋势重复日期、branches 重复机构和授权目录覆盖且不使用管理名单', async () => {
    const branchBind = {
      dsId: 11, period: 'LATEST', fields: { orgCode: 'org', deposit: 'amount' },
      units: { deposit: 'HUNDRED_MILLION' }
    };
    const trendBind = {
      dsId: 12, period: 'LAST_6M_EOM', fields: { date: 'd', deposit: 'amount' },
      units: { deposit: 'HUNDRED_MILLION' }
    };
    const components = [makeComponent('branches', 101, branchBind), makeComponent('trend', 102, trendBind)];
    const query = vi.fn()
      .mockResolvedValueOnce({ columns: ['org', 'amount'], rows: [['AUTH-1', 0], ['AUTH-1', null], ['OUTSIDE', 2]] })
      .mockResolvedValueOnce({ columns: ['d', 'amount'], rows: [['2026-08', 1], ['2026-08', 2]] });
    const canvas = makeCanvas({ components, blocks: components.map(item => ({ id: item.blockId, bindJson: item.bindJson })) });
    const service = createRuntimeDataVerificationService(deps({
      canvas,
      view: makeView({ components, institutions: [{ orgCode: 'AUTH-1' }, { orgCode: 'AUTH-2' }] }), query
    }));
    const result = await service.verify({
      screen: { id: 7, screenCode: 'SCR_VERIFY', panoramaInstitutions: [{ orgCode: 'MANAGEMENT-ONLY' }] },
      canvas, slotOrder: ['branches', 'trend'],
      bindingState: { branches: branchBind, trend: trendBind }
    });
    const branches = result.results.find(item => item.slot === 'branches');
    const trend = result.results.find(item => item.slot === 'trend');
    expect(branches.coverage).toMatchObject({ total: 2, available: 1, missing: ['AUTH-2'] });
    expect(branches.issues.map(item => item.code)).toEqual(expect.arrayContaining([
      'DUPLICATE_ORG_CODE', 'UNAUTHORIZED_ORG_CODE'
    ]));
    expect(trend.issues.map(item => item.code)).toContain('DUPLICATE_DATE');
    expect(result.overallStatus).not.toBe(OVERALL_STATUS.COMPLETE);
  });
});
