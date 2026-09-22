// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const router = vi.hoisted(() => ({ replace: vi.fn().mockResolvedValue(undefined), push: vi.fn() }));
const route = vi.hoisted(() => ({ query: { businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit' } }));
vi.mock('vue-router', () => ({ routerKey: null, routeLocationKey: null, useRouter: () => router, useRoute: () => route }));
vi.mock('../PanoramaMap.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaTrend.vue', () => ({ default: { template: '<div />' } }));

import CityPanorama from '../CityPanorama.vue';

const institutions = [{
  orgCode: 'ORG-1', orgName: '一号机构', cityCode: '610100', located: true, lng: 108.9, lat: 34.2,
  metrics: { deposit: 1, loan: 2, customers: 3, rate: 90 }, trend: [], attention: []
}];

function mountCity() {
  return mount(CityPanorama, {
    props: {
      model: { dataDate: '2026-09-22', citySummaries: {}, institutions },
      cityCode: '610100', cityName: '西安市', initialOrgCode: 'ORG-1'
    }
  });
}

describe('CityPanorama query navigation', () => {
  it('mount and visible state persist city/org/business context through replace query', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-testid="branch-search"]').setValue('一号');
    expect(router.replace).toHaveBeenCalledWith({ query: expect.objectContaining({
      cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit', state: expect.any(String)
    }) });
    const latest = router.replace.mock.calls.at(-1)[0].query;
    expect(JSON.parse(latest.state)).toMatchObject({ search: '一号', selectedOrgCode: 'ORG-1' });
    wrapper.unmount();
  });

  it('back clears city/org context without inventing a fallback institution', async () => {
    const wrapper = mountCity();
    await wrapper.get('[data-action="city-back"]').trigger('click');
    expect(router.replace).toHaveBeenLastCalledWith({ query: expect.not.objectContaining({ cityCode: expect.anything(), orgCode: expect.anything() }) });
    expect(router.push).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
