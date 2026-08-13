async page => {
  const ROOT = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560';
  const BASE = 'http://127.0.0.1:8091';
  const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS']);
  const independent = new Set(['Login', 'NoAccess', 'Workspace', 'ReportDash']);
  const shots = new Set(['Workspace', 'InfoProducts', 'EvalTasks', 'ReportSql', 'ScreenAdminDs', 'SysUsers', 'SysResources', 'SysJobs', 'SysNotifications']);
  const specs = [
    ['NoAccess','/no-access'],['Workspace','/workspace'],
    ['AnnouncementList','/workspace/announcements',null,['announcement','/api/portal/announcements?pageNo=1&pageSize=20','id']],
    ['AnnouncementDetail',null,['/announcement/:id','announcement']],['NotificationList','/workspace/notifications'],
    ['InfoNav','/info/nav'],['InfoAddressBook','/info/address-book'],['InfoProducts','/info/products'],['InfoDocuments','/info/documents'],
    ['PerfMetrics','/perf/metrics'],['PerfKpiRules','/perf/kpi-rules'],['PerfTargets','/perf/targets'],['PerfTargetValues','/perf/target-values'],
    ['PerfImport','/perf/import'],['PerfAdjust','/perf/adjust'],['PerfCompute','/perf/compute'],['PerfTaskMonitor','/perf/task-monitor'],
    ['PerfKpiScoreDetail','/perf/kpi-score-detail'],['EvalTags','/eval/tags'],['EvalUserTags','/eval/user-tags'],['EvalRules','/eval/rules'],
    ['EvalTasks','/eval/tasks'],['EvalMyTasks','/eval/my-tasks'],['ReportDynamic','/report/dynamic'],['ReportDash','/report/dashboard'],
    ['ReportPresets','/report/presets'],['ReportFree','/report/free',null,['freeBatch','/api/reports/free/batches','id']],
    ['ReportFreeDetail',null,['/report/free/:batchId','freeBatch']],['ReportSql','/report/sql'],
    ['ReportAmasApprovals','/report/amas-approvals',null,['amasApproval','/api/reports/amas-approvals?pageNo=1&pageSize=20','perfAdjustNo']],
    ['ReportAmasApprovalDetail',null,['/report/amas-approvals/:perfAdjustNo','amasApproval']],
    ['ScreenAdminDs','/screen-admin/datasources'],['ScreenAdminOrgProfiles','/screen-admin/org-profiles'],['ScreenAdminOrgGroups','/screen-admin/org-groups'],
    ['GuaranteeQuery','/guarantee/query'],['HistoryDataImport','/guarantee/data-import'],['HistoryNotice','/guarantee/notice'],
    ['HistoryPriceApproval','/history/price-approval',null,['priceApproval','/api/reports/amas-price-approvals?pageNo=1&pageSize=20','priceApprId']],
    ['HistoryPriceApprovalDetail',null,['/history/price-approval/:priceApprId','priceApproval']],['HistoryPerfAdjust','/history/perf-adjust'],
    ['SysUsers','/system/users'],['SysRoles','/system/roles'],['SysResources','/system/resources'],['SysPermission','/system/permission'],
    ['SysDict','/system/dict'],['SysCalendar','/system/calendar'],['SysJobs','/system/jobs'],['SysAudit','/system/audit'],
    ['SysNotifications','/system/notifications'],['SysConfig','/system/config'],['SysFiles','/system/files'],['SysTimeoutRules','/system/timeout-rules'],
    ['SysAnnouncements','/system/announcements',null,['sysAnnouncement','/api/admin/announcements?pageNo=1&pageSize=20','id']],
    ['SysAnnouncementDetail',null,['/system/announcements/:id','sysAnnouncement'],'admin=1'],
    ['SysWorkflowFlows','/system/workflow-flows',null,['workflowFlow','/api/admin/workflow/flows','id']],
    ['SysWorkflowFlowEdit',null,['/system/workflow-flows/:id','workflowFlow']],['SysWorkflowMonitor','/system/workflow-monitor'],
    ['SysPersonTags','/system/person-tags']
  ];
  const expectedPhysical = {
    Workspace:3,AnnouncementList:1,InfoAddressBook:1,InfoProducts:1,InfoDocuments:1,PerfMetrics:1,PerfKpiRules:2,
    PerfTargets:3,PerfTargetValues:1,PerfImport:1,PerfAdjust:5,PerfTaskMonitor:1,EvalTags:1,EvalUserTags:1,EvalRules:2,
    EvalTasks:1,EvalMyTasks:2,ReportFree:1,ReportSql:2,ReportAmasApprovals:2,ScreenAdminDs:1,ScreenAdminOrgProfiles:1,
    GuaranteeQuery:1,HistoryDataImport:1,HistoryNotice:1,HistoryPriceApproval:1,SysUsers:1,SysRoles:1,SysResources:1,
    SysPermission:1,SysDict:1,SysJobs:1,SysAudit:1,SysNotifications:1,SysConfig:3,SysFiles:1,SysTimeoutRules:1,
    SysAnnouncements:1,SysWorkflowFlows:1,SysWorkflowMonitor:1,SysPersonTags:3
  };
  const expectedAdaptive = {
    Workspace:2,InfoProducts:1,InfoDocuments:1,PerfMetrics:1,PerfKpiRules:1,PerfTargets:1,PerfTargetValues:1,PerfImport:1,
    PerfAdjust:1,PerfTaskMonitor:1,EvalTags:1,EvalRules:1,EvalTasks:1,ReportFree:1,ReportSql:1,ScreenAdminDs:1,
    SysUsers:1,SysRoles:1,SysResources:1,SysDict:1,SysJobs:1,SysNotifications:1,SysFiles:1,SysWorkflowFlows:1,
    SysWorkflowMonitor:1,SysPersonTags:3,SchemeListDialog:1
  };
  const out = {
    status: 'running', generatedAt: new Date().toISOString(), viewport:[2560,1440], session:'pageopt2-pass-2560-4f5d9ceb',
    gitHead:'4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4', startRouteList:'No active routes', mockRoutes:0,
    credentialsArchived:false, responseBodiesArchived:false, realEntityIdsArchived:false, routes:[], discoveries:[],
    expectedMatrix:{routes:59,physicalOperationColumns:58,adaptiveOperationColumns:30}, shell:null, firstError:null
  };
  const ids = new Map();
  const redact = value => {
    let result=String(value).replace(BASE,'').replace(/([?&][^=&#]+)=([^&#]*)/g,'$1=<redacted>');
    for(const id of ids.values()){
      for(const token of [String(id),encodeURIComponent(String(id))]){
        const escaped=token.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
        result=result.replace(new RegExp('/'+escaped+'(?=/|[?#]|$)','g'),'/<redacted-id>');
      }
    }
    return result.replace(/\/[0-9a-f]{16,}(?=\/|[?#]|$)/gi,'/<redacted-id>');
  };
  const save = async () => {
    const promise = page.waitForEvent('download');
    await page.evaluate(payload => {
      const a=document.createElement('a'); const u=URL.createObjectURL(new Blob([payload],{type:'application/json'}));
      a.href=u; a.download='routes-59-shell-actions.json'; a.click(); setTimeout(()=>URL.revokeObjectURL(u),0);
    }, JSON.stringify(out,(key,value)=>typeof value==='string'?redact(value):value,2));
    await (await promise).saveAs(`${ROOT}/json/routes-59-shell-actions.json`);
  };
  const find = (value, field, depth=0) => {
    if(depth>12||value==null)return null;
    if(Array.isArray(value)){for(const x of value){const f=find(x,field,depth+1);if(f!=null)return f;}return null;}
    if(typeof value!=='object')return null;
    if(value[field]!=null&&String(value[field]))return String(value[field]);
    for(const x of Object.values(value)){const f=find(x,field,depth+1);if(f!=null)return f;}return null;
  };
  const discover = async ([key,url,field]) => {
    const r=await page.evaluate(async ({url,field})=>{const x=await fetch(url,{credentials:'same-origin'});let b=null;try{b=await x.json();}catch{}return{status:x.status,code:b?.code??null,body:b};},{url,field});
    const value=find(r.body,field); if(value)ids.set(key,value);
    const item={key,method:'GET',url:redact(url),status:r.status,code:r.code,found:Boolean(value),valueArchived:false};out.discoveries.push(item);return item;
  };
  const actualPath = spec => {
    if(spec[1])return spec[1]; const [template,key]=spec[2]; const id=ids.get(key); if(!id)return null;
    const path=template.replace(/:[^/]+/,encodeURIComponent(id)); return spec[3]?`${path}?${spec[3]}`:path;
  };
  const waitIdle = async pending => {
    const end=Date.now()+15000;let empty=null;
    while(Date.now()<end){if(!pending.size){empty??=Date.now();if(Date.now()-empty>650)return true;}else empty=null;await page.waitForTimeout(100);}return !pending.size;
  };
  const visible = el => {const r=el.getBoundingClientRect(),s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};
  const domProbe = async () => page.evaluate(() => {
    const visible=el=>{const r=el.getBoundingClientRect(),s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};
    const round=n=>Math.round(n*10)/10; const main=document.querySelector('main'); const content=document.querySelector('.content');
    const tables=[...document.querySelectorAll('.el-table')].filter(visible);
    const physical=tables.reduce((n,t)=>n+[...t.querySelectorAll('th.operation-cell')].filter(visible).length,0);
    const hosts=[...document.querySelectorAll('[data-bp-row-actions-host]')].filter(visible);
    const adaptive=hosts.map((host,index)=>{const probe=host.querySelector('[data-bp-row-actions-probe]');const vis=host.querySelector('[data-bp-row-actions-visible]');
      const mode=host.dataset.mode,available=host.clientWidth,required=probe?.scrollWidth||0;
      return{index,mode,measured:host.dataset.measured,available,required,relation:mode==='expanded'?required<=available:required>available||!available||!required,
        visibleActions:[...vis?.querySelectorAll('button,[role="menuitem"]')||[]].filter(visible).map(x=>({text:x.textContent.trim(),disabled:x.matches(':disabled,.is-disabled'),danger:x.classList.contains('el-button--danger')||x.classList.contains('danger-item')})),
        probe:{ariaHidden:probe?.getAttribute('aria-hidden'),inert:probe?.hasAttribute('inert'),visibility:probe?getComputedStyle(probe).visibility:null,pointerEvents:probe?getComputedStyle(probe).pointerEvents:null,focusable:probe?[...probe.querySelectorAll('button,a,input,[tabindex]')].some(x=>x.tabIndex>=0&&!x.closest('[inert]')):null}};
    });
    const operations=tables.flatMap((t,ti)=>[...t.querySelectorAll('td.operation-cell')].filter(visible).map(td=>{const c=td.querySelector('.cell')||td,s=getComputedStyle(c),r=td.getBoundingClientRect(),tr=t.getBoundingClientRect();return{table:ti,position:getComputedStyle(td).position,right:getComputedStyle(td).right,whiteSpace:s.whiteSpace,overflow:s.overflow,textOverflow:s.textOverflow,insideViewport:r.right<=innerWidth+1,insideTable:r.right<=tr.right+1};}));
    const tableScroll=tables.map((t,index)=>{const w=t.querySelector('.el-scrollbar__wrap')||t.querySelector('.el-table__body-wrapper');return{index,clientWidth:w?.clientWidth||0,scrollWidth:w?.scrollWidth||0,overflowX:w?getComputedStyle(w).overflowX:null};});
    const heightSet=selector=>[...new Set([...document.querySelectorAll(selector)].filter(visible).map(x=>Math.round(x.getBoundingClientRect().height)))];
    const h=document.querySelector('.hdr'),tabs=document.querySelector('.workspace-tabs'),side=document.querySelector('#app-sidebar');
    return{url:location.hash,mainCount:document.querySelectorAll('main').length,h1Count:document.querySelectorAll('h1').length,crud:Boolean(document.querySelector('main.bp-crud')),
      outer:{document:[document.documentElement.clientWidth,document.documentElement.scrollWidth],content:content?[content.clientWidth,content.scrollWidth]:null,main:main?[main.clientWidth,main.scrollWidth]:null},
      shell:{layout:document.querySelectorAll('#app>.layout').length,headerCount:document.querySelectorAll('.hdr').length,tabsCount:document.querySelectorAll('.workspace-tabs').length,tabsInHeader:document.querySelectorAll('.hdr>.workspace-tabs').length,sidebarWidth:side?round(side.getBoundingClientRect().width):null,headerHeight:h?round(h.getBoundingClientRect().height):null,tabsHeight:tabs?round(tabs.getBoundingClientRect().height):null,emptyStrip:document.querySelectorAll('.main>.workspace-tabs').length},
      density:{tableHeader:heightSet('.bp-crud .el-table__header tr'),tableRows:heightSet('.bp-crud .el-table__body tr'),tags:heightSet('.bp-crud .el-table .el-tag'),controls:heightSet('.bp-crud .el-input__wrapper,.bp-crud .el-select__wrapper,.bp-crud .el-button')},
      renderedPhysical:physical,adaptive,operations,tableScroll};
  });
  const shellToggle = async () => {
    const before=await page.evaluate(()=>({path:location.hash,width:document.querySelector('#app-sidebar')?.getBoundingClientRect().width,tabs:document.querySelectorAll('.hdr>.workspace-tabs').length,headers:document.querySelectorAll('.hdr').length}));
    await page.getByTestId('sidebar-toggle').click(); await page.waitForTimeout(80);
    const collapsed=await page.evaluate(()=>({width:document.querySelector('#app-sidebar')?.getBoundingClientRect().width,label:document.querySelector('[data-testid="sidebar-toggle"]')?.getAttribute('aria-label'),path:location.hash,tabs:document.querySelectorAll('.hdr>.workspace-tabs').length}));
    await page.screenshot({path:`${ROOT}/screenshots/shell-collapsed.png`,mask:[page.locator('.el-table__body:visible')],maskColor:'#d8dee9'});
    await page.getByTestId('sidebar-toggle').click(); await page.waitForTimeout(80);
    const restored=await page.evaluate(()=>({width:document.querySelector('#app-sidebar')?.getBoundingClientRect().width,label:document.querySelector('[data-testid="sidebar-toggle"]')?.getAttribute('aria-label'),path:location.hash,tabs:document.querySelectorAll('.hdr>.workspace-tabs').length}));
    return{before,collapsed,restored,pass:before.width===220&&collapsed.width===64&&restored.width===220&&before.path===collapsed.path&&before.path===restored.path&&before.tabs===1&&collapsed.tabs===1&&restored.tabs===1};
  };
  const loginContext=await page.context().browser().newContext({viewport:{width:2560,height:1440}});
  const loginPage=await loginContext.newPage();await loginPage.goto(`${BASE}/#/login?normal=1`);await loginPage.evaluate(()=>document.fonts?.ready);
  const loginDom=await loginPage.evaluate(()=>({path:location.hash.replace(/^#/,''),mainCount:document.querySelectorAll('main').length,h1Count:document.querySelectorAll('h1').length,document:[document.documentElement.clientWidth,document.documentElement.scrollWidth]}));
  await loginPage.screenshot({path:`${ROOT}/screenshots/Login.png`});await loginContext.close();
  const loginIssues=[];if(loginDom.path!=='/login?normal=1'||loginDom.mainCount!==1||loginDom.h1Count<1||loginDom.document[0]!==loginDom.document[1])loginIssues.push('login-baseline');
  out.routes.push({index:1,name:'Login',path:'/login?normal=1',status:loginIssues.length?'fail':'pass',expectedPhysical:0,expectedAdaptive:0,dom:loginDom,console:{errors:0,allowedDynamicWarnings:0,bad:[]},network:{settled:true,writes:[],bad:[],requests:[]},issues:loginIssues});
  if(loginIssues.length)out.firstError={route:'Login',index:1,issues:loginIssues};await save();
  for(const spec of specs){
    if(out.firstError)break;
    if(spec[3]&&Array.isArray(spec[3]))await discover(spec[3]);
    const path=actualPath(spec); const name=spec[0];
    if(!path){out.routes.push({name,status:'not_exercised_missing_real_id',expectedPhysical:expectedPhysical[name]||0,expectedAdaptive:expectedAdaptive[name]||0});continue;}
    const events=[],requests=[],pending=new Set(),reads=[];
    const onConsole=m=>events.push({type:m.type(),text:m.text().slice(0,500)});
    const onRequest=r=>{if(r.url().includes('/api/')){pending.add(r);requests.push({method:r.method(),url:r.url(),status:null,code:null});}};
    const onResponse=r=>{const q=r.request(),x=requests.findLast(v=>v.url===q.url()&&v.status==null);if(x){x.status=r.status();if((r.headers()['content-type']||'').includes('application/json'))reads.push(r.json().then(b=>x.code=b?.code??null).catch(()=>{}));}pending.delete(q);};
    const onFailed=r=>{const x=requests.findLast(v=>v.url===r.url()&&v.status==null);if(x)x.failed=r.failure()?.errorText||'failed';pending.delete(r);};
    page.on('console',onConsole);page.on('request',onRequest);page.on('response',onResponse);page.on('requestfailed',onFailed);
    await page.evaluate(p=>{location.hash=p;},path); await page.waitForTimeout(400); const settled=await waitIdle(pending);await Promise.allSettled(reads);await page.evaluate(()=>document.fonts?.ready);
    const dom=await domProbe();
    if(name==='Workspace'&&!out.shell)out.shell=await shellToggle();
    if(shots.has(name))await page.screenshot({path:`${ROOT}/screenshots/${name}.png`,mask:[page.locator('.el-table__body:visible')],maskColor:'#d8dee9'});
    page.off('console',onConsole);page.off('request',onRequest);page.off('response',onResponse);page.off('requestfailed',onFailed);
    const dynamic=events.filter(e=>e.type==='warning'&&e.text.includes('[Vue Router warn]: No match found for location with path'));
    const badConsole=events.filter(e=>e.type==='error'||(e.type==='warning'&&!e.text.includes('[Vue Router warn]: No match found for location with path'))||e.text.includes('[api fallback]')||e.text.includes('ResizeObserver loop'));
    const writes=requests.filter(r=>!safeMethods.has(r.method));const badNet=requests.filter(r=>r.failed||r.status==null||r.status>=400);
    const expected=path.split('?')[0],actual=await page.evaluate(()=>location.hash.replace(/^#/,'').split('?')[0]);const issues=[];
    if(actual!==expected)issues.push(`redirect:${actual}`);if(dom.mainCount!==1||dom.h1Count<1)issues.push('semantic-main-h1');if(independent.has(name)?dom.crud:!dom.crud)issues.push('crud-scope');
    if(dom.outer.document[0]!==dom.outer.document[1]||(dom.outer.content&&dom.outer.content[0]!==dom.outer.content[1])||(dom.outer.main&&dom.outer.main[0]!==dom.outer.main[1]))issues.push('outer-horizontal-overflow');
    if(!['Login','NoAccess'].includes(name)&&(dom.shell.layout!==1||dom.shell.headerCount!==1||dom.shell.tabsCount!==1||dom.shell.tabsInHeader!==1||dom.shell.emptyStrip!==0||dom.shell.headerHeight!==52||dom.shell.tabsHeight>52))issues.push('shell-header-tabs');
    if(dom.operations.some(x=>x.position!=='sticky'||x.right==='auto'||x.whiteSpace!=='nowrap'||x.overflow!=='visible'||x.textOverflow!=='clip'||!x.insideViewport||!x.insideTable))issues.push('operation-column');
    if(dom.tableScroll.some(x=>x.scrollWidth>x.clientWidth+1&&!['auto','scroll'].includes(x.overflowX)))issues.push('table-internal-scroll');
    if(dom.density.tableHeader.some(x=>x!==40)||dom.density.tableRows.some(x=>x!==40)||dom.density.tags.some(x=>x!==24)||dom.density.controls.some(x=>x<32))issues.push('legacy-density');
    if(dom.adaptive.some(x=>!x.relation||x.measured!=='true'||x.probe.ariaHidden!=='true'||!x.probe.inert||x.probe.visibility!=='hidden'||x.probe.pointerEvents!=='none'||x.probe.focusable))issues.push('adaptive-row-actions');
    if(badConsole.length||writes.length||badNet.length||!settled)issues.push('runtime-network');if(name==='Workspace'&&!out.shell.pass)issues.push('sidebar-toggle');
    const archivedPath=spec[1]||`${spec[2][0]}${spec[3]&&!Array.isArray(spec[3])?`?${spec[3]}`:''}`;
    const item={index:out.routes.length+1,name,path:archivedPath,status:issues.length?'fail':'pass',actualIdArchived:false,expectedPhysical:expectedPhysical[name]||0,expectedAdaptive:expectedAdaptive[name]||0,dom,
      console:{errors:events.filter(e=>e.type==='error').length,allowedDynamicWarnings:dynamic.length,bad:badConsole},network:{settled,writes:writes.map(x=>({...x,url:redact(x.url)})),bad:badNet.map(x=>({...x,url:redact(x.url)})),requests:requests.map(x=>({...x,url:redact(x.url)}))},issues};
    out.routes.push(item);if(issues.length)out.firstError={route:name,index:item.index,issues};await save();
  }
  out.summary={expected:59,recorded:out.routes.length,passed:out.routes.filter(x=>x.status==='pass').length,notExercised:out.routes.filter(x=>x.status.startsWith('not_exercised')).length,failed:out.routes.filter(x=>x.status==='fail').length,
    expectedPhysical:Object.values(expectedPhysical).reduce((a,b)=>a+b,0),expectedAdaptive:Object.values(expectedAdaptive).reduce((a,b)=>a+b,0),renderedPhysical:out.routes.reduce((n,x)=>n+(x.dom?.renderedPhysical||0),0),renderedAdaptiveHosts:out.routes.reduce((n,x)=>n+(x.dom?.adaptive?.length||0),0)};
  out.status=out.firstError?'fail-stopped':'pass';await save();return{status:out.status,summary:out.summary,firstError:out.firstError,shell:out.shell};
}
