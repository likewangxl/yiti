import { describe, expect, it } from 'vitest';
import { adaptPanoramaResults } from '../dataAdapter';
const binding=(slot,fields,units={})=>({slot,dsId:1,period:'LATEST',fields,units});
describe('地图按最新授权机构归属关联',()=>{
  it('名称与地址所属城市不同，以机构编码关联目录城市，并过滤范围外机构',()=>{
    const model=adaptPanoramaResults({ranking:{binding:binding('ranking',{orgCode:'code',name:'name',value:'amount'},{value:'YUAN'}),response:{columns:['code','name','amount'],rows:[['A','榆林分行旧名称',100000000],['OUT','西安分行',900000000]]}}},{view:{orgScopeMode:'NAMED_GROUP'},panoramaInstitutions:[{orgCode:'A',orgName:'地址已迁入西安的经营机构',cityCode:'610100',cityName:'西安市'}]});
    expect(model.rankings).toHaveLength(1);
    expect(model.rankings[0]).toMatchObject({orgCode:'A',cityCode:'610100',cityName:'西安市',name:'地址已迁入西安的经营机构',deposit:1});
    expect(model.issues).toEqual(expect.arrayContaining([expect.objectContaining({slot:'ranking',code:'UNAUTHORIZED_ORG'})]));
  });
  it('目录缺城市不能用旧数据行城市或机构名称代替地址归属',()=>{
    const model=adaptPanoramaResults({branches:{binding:binding('branches',{orgCode:'code',cityCode:'city'}),response:{columns:['code','city'],rows:[['A','610800']]}}},{view:{orgScopeMode:'NAMED_GROUP'},panoramaInstitutions:[{orgCode:'A',orgName:'榆林分行',cityCode:''}]});
    expect(model.institutions[0].cityCode).toBe('');
  });
});
