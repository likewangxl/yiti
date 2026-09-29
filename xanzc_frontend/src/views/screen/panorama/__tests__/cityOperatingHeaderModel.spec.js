import { describe, expect, it } from 'vitest';

import {
  buildCityOperatingHeaderModel,
  isCityOperatingHeaderEnabled
} from '../cityOperatingHeaderModel.js';
import { buildCompositionTabsModel } from '../../presentation/model/compositionTabsModel.js';

const sourcePresentation = {
  displayPresentation: {
    displaySchemaVersion: 1,
    template: 'branch-overview-v1',
    display: { components: [] }
  }
};

describe('cityOperatingHeaderModel', () => {
  it('只从当前 citySummary 生成五卡摘要、十项顶栏和存贷款构成适配', () => {
    const citySummary = {
      dataDate: '2026-09-20',
      kpis: [
        { key: 'deposit', value: 120, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'loan', value: 80, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'corpDeposit', value: 70, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'retailDeposit', value: 50, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'corpLoan', value: 45, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'retailLoan', value: 35, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'revenue', value: 12, unit: '万元', dataDate: '2026-09-20' }
      ],
      displayValues: { explicitCityBlock: { value: 1 } }
    };

    const result = buildCityOperatingHeaderModel(sourcePresentation, citySummary);
    const components = result.presentation.display.components;

    expect(isCityOperatingHeaderEnabled(sourcePresentation)).toBe(true);
    expect(result.enabled).toBe(true);
    expect(components.filter(item => item.layoutRegion === 'HEADER')).toHaveLength(10);
    expect(components.filter(item => item.layoutRegion === 'LEFT' && item.componentType === 'COMPOSITION_TABS')).toHaveLength(1);
    expect(components.some(item => item.layoutRegion === 'CENTER' || item.layoutRegion === 'RIGHT')).toBe(false);
    expect(result.model.dataDate).toBe('2026-09-20');
    expect(result.model.kpis).toEqual(citySummary.kpis);
    expect(result.model.displayValues.explicitCityBlock).toEqual({ value: 1 });
    expect(result.model.displayValues.deposit).toMatchObject({
      total: 120, corporate: 70, retail: 50,
      unitByField: { total: '亿元', corporate: '亿元', retail: '亿元' },
      dataDate: '2026-09-20'
    });
  });

  it('没有当前城市摘要时保持所有来源为空，不回退读取父层模型', () => {
    const result = buildCityOperatingHeaderModel(sourcePresentation, null);

    expect(result.enabled).toBe(true);
    expect(result.model).toMatchObject({ dataDate: '', kpis: [], displayValues: {} });
    expect(result.model).not.toHaveProperty('blockResults');
  });

  it('存贷款构成字段日期不一致时不生成跨期构成来源', () => {
    const result = buildCityOperatingHeaderModel(sourcePresentation, {
      kpis: [
        { key: 'deposit', value: 120, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'corpDeposit', value: 70, unit: '亿元', dataDate: '2026-09-20' },
        { key: 'retailDeposit', value: 50, unit: '亿元', dataDate: '2026-09-19' }
      ]
    });

    expect(result.model.displayValues.deposit).toBeUndefined();
    expect(buildCompositionTabsModel(result.presentation, result.model).tabs.find(item => item.tabKey === 'deposit'))
      .toMatchObject({ state: 'NO_SOURCE' });
  });

  it('未确认 branch-overview 配置时不启用城市经营顶栏', () => {
    expect(isCityOperatingHeaderEnabled({ screenCode: 'SCR_PROVINCE' })).toBe(false);
    expect(isCityOperatingHeaderEnabled({ displaySchemaVersion: 1, template: 'corporate-overview-v1' })).toBe(false);
  });
});
