async page => {
  const ROOT = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization-20260813/final-pass/2560/rerun-22108e35-final';
  const BASE = 'http://127.0.0.1:8091';
  const independent = new Set(['Login', 'NoAccess', 'Workspace', 'ReportDash']);
  const warningTargets = new Set(['PerfAdjust', 'HistoryPerfAdjust', 'ScreenAdminDs', 'ScreenAdminOrgProfiles']);
  const cancelTargets = {
    EvalTags: ['row', '编辑'], EvalUserTags: ['row', '编辑'], ScreenAdminDs: ['row', '编辑'],
    ScreenAdminOrgProfiles: ['row', '编辑画像'], ScreenAdminOrgGroups: ['page', '新建机构组'], SysUsers: ['row', '编辑']
  };
  const shots = new Set(['NotificationList', 'InfoProducts', 'PerfTaskMonitor', 'PerfAdjust', 'ScreenAdminDs',
    'ScreenAdminOrgProfiles', 'HistoryPerfAdjust', 'SysResources', 'SysConfig', 'SysPersonTags']);
  const specs = [
    ['Login','/login?normal=1'],['NoAccess','/no-access'],['Workspace','/workspace'],
    ['AnnouncementList','/workspace/announcements',null,['announcement','/api/portal/announcements?pageNo=1&pageSize=20','id']],
    ['AnnouncementDetail',null,['/announcement/:id','announcement']],['NotificationList','/workspace/notifications'],
    ['InfoNav','/info/nav'],['InfoAddressBook','/info/address-book'],['InfoProducts','/info/products'],['InfoDocuments','/info/documents'],
    ['PerfMetrics','/perf/metrics'],['PerfKpiRules','/perf/kpi-rules'],['PerfTargets','/perf/targets'],
    ['PerfTargetValues','/perf/target-values'],['PerfImport','/perf/import'],['PerfAdjust','/perf/adjust'],
    ['PerfCompute','/perf/compute'],['PerfTaskMonitor','/perf/task-monitor'],['PerfKpiScoreDetail','/perf/kpi-score-detail'],
    ['EvalTags','/eval/tags'],['EvalUserTags','/eval/user-tags'],['EvalRules','/eval/rules'],['EvalTasks','/eval/tasks'],['EvalMyTasks','/eval/my-tasks'],
    ['ReportDynamic','/report/dynamic'],['ReportDash','/report/dashboard'],['ReportPresets','/report/presets'],
    ['ReportFree','/report/free',null,['freeBatch','/api/reports/free/batches','id']],
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
    ['SysAnnouncementDetail',null,['/system/announcements/:id','sysAnnouncement','admin=1']],
    ['SysWorkflowFlows','/system/workflow-flows',null,['workflowFlow','/api/admin/workflow/flows','id']],
    ['SysWorkflowFlowEdit',null,['/system/workflow-flows/:id','workflowFlow']],['SysWorkflowMonitor','/system/workflow-monitor'],['SysPersonTags','/system/person-tags']
  ];
  const ids = {}, secretValues = new Set();
  const out = {
    status: 'running', viewport: [2560, 1440], session: 'pageopt-final-2560-22108e35',
    gitHead: '22108e35edb4f859c2d806ec4b1868d7aac274f6', startRouteList: 'No active routes', mockRoutes: 0,
    credentialsArchived: false, routes: [], discoveries: [], cancellations: [], resets: [], warningTargets: {},
    productHover: null, tabs: null, firstError: null
  };
  const save = async () => {
    const p = page.waitForEvent('download');
    await page.evaluate(value => {
      const url = URL.createObjectURL(new Blob([value], { type: 'application/json' }));
      const a = document.createElement('a'); a.href = url; a.download = 'routes-59.json'; a.click(); URL.revokeObjectURL(url);
    }, JSON.stringify(out, null, 2));
    await (await p).saveAs(`${ROOT}/json/routes-59.json`);
  };
  const escapeRegExp = value => String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const redacted = raw => {
    let text = String(raw).replace(BASE, '');
    for (const value of secretValues) {
      text = text
        .replace(new RegExp(`/${escapeRegExp(value)}(?=/|[?#]|$)`, 'g'), '/<redacted-id>')
        .replace(new RegExp(`/${escapeRegExp(encodeURIComponent(value))}(?=/|[?#]|$)`, 'g'), '/<redacted-id>');
    }
    return text
      .replace(/(\/api\/admin\/roles\/)[^/?#]+(?=\/resources(?:[/?#]|$))/g, '$1<redacted-id>')
      .replace(/(\/api\/admin\/users\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/admin\/workflow\/flows\/)(?!meta(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/perf\/kpi-schemes\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/perf\/metrics\/)(?!categories(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/portal\/announcements\/)(?!recent(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/reports\/amas-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/reports\/amas-price-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/sys\/dicts\/)[^/?#]+(?=\/items(?:[/?#]|$))/g, '$1<redacted-id>')
      .replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>');
  };
  const walk = (value, field, depth = 0) => {
    if (value == null || depth > 12) return null;
    if (Array.isArray(value)) { for (const item of value) { const found = walk(item, field, depth + 1); if (found) return found; } return null; }
    if (typeof value !== 'object') return null;
    if (value[field] != null && String(value[field])) return String(value[field]);
    for (const item of Object.values(value)) { const found = walk(item, field, depth + 1); if (found) return found; }
    return null;
  };
  const discover = async tuple => {
    const [key, url, field] = tuple;
    const result = await page.evaluate(async u => {
      const response = await fetch(u, { credentials: 'same-origin' });
      let body = null; try { body = await response.json(); } catch {}
      return { status: response.status, code: body?.code, body };
    }, url);
    const value = walk(result.body, field);
    if (value) { ids[key] = value; secretValues.add(value); }
    const item = { key, url: redacted(url), status: result.status, code: result.code ?? null, found: Boolean(value), valueArchived: false };
    out.discoveries.push(item); return item;
  };
  const idle = async pending => {
    const end = Date.now() + 15000; let zero = 0;
    while (Date.now() < end) {
      if (!pending.size) { if (!zero) zero = Date.now(); if (Date.now() - zero >= 600) return true; }
      else zero = 0;
      await page.waitForTimeout(100);
    }
    return !pending.size;
  };
  const visible = async locator => locator.filter({ visible: true });
  const dom = () => page.evaluate(() => {
    const vis = el => { const r=el.getBoundingClientRect(),s=getComputedStyle(el); return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'; };
    const n = v => Math.round(v * 10) / 10;
    const main=document.querySelector('main'),content=document.querySelector('.content'),crud=document.querySelector('main.bp-crud');
    const tables=[...document.querySelectorAll('.el-table')].filter(vis).map((table,index)=>{
      const heads=[...table.querySelectorAll('.el-table__header-wrapper tr')].filter(vis);
      const headCells=[...table.querySelectorAll('.el-table__header-wrapper th.el-table__cell')].filter(vis);
      const rows=[...table.querySelectorAll('.el-table__body-wrapper tr.el-table__row')].filter(vis);
      const cells=[...table.querySelectorAll('.el-table__body-wrapper td.el-table__cell')].filter(vis);
      const scroller=table.querySelector('.el-scrollbar__wrap')||table.querySelector('.el-table__body-wrapper');
      const ops=cells.filter(cell=>cell.classList.contains('operation-cell')).map(cell=>{
        const box=cell.querySelector('.cell')||cell,s=getComputedStyle(cell),bs=getComputedStyle(box);
        return {position:s.position,right:s.right,whiteSpace:bs.whiteSpace,overflow:bs.overflow,textOverflow:bs.textOverflow};
      });
      return { index, tableBorder:[getComputedStyle(table).borderLeftWidth,getComputedStyle(table).borderRightWidth],
        headerRows:heads.map(x=>n(x.getBoundingClientRect().height)),headerCells:headCells.map(x=>n(x.getBoundingClientRect().height)),
        rows:rows.map(x=>n(x.getBoundingClientRect().height)),cells:cells.map(x=>({height:n(x.getBoundingClientRect().height),paddingTop:getComputedStyle(x).paddingTop,paddingBottom:getComputedStyle(x).paddingBottom,borderBottom:getComputedStyle(x).borderBottomWidth,borderRight:getComputedStyle(x).borderRightWidth})),
        scroller:scroller?{clientWidth:scroller.clientWidth,scrollWidth:scroller.scrollWidth,overflowX:getComputedStyle(scroller).overflowX}:null,ops};
    });
    const controls=crud?[...crud.querySelectorAll('.filter-form .el-input__wrapper,.filter-form .el-select__wrapper,.filter-form .el-date-editor,.filter-form button.el-button:not(.is-link)')].filter(vis).map(x=>n(x.getBoundingClientRect().height)):[];
    const tags=crud?[...crud.querySelectorAll('.el-table .el-tag')].filter(vis).slice(0,30).map(x=>n(x.getBoundingClientRect().height)):[];
    const stacks=crud?[...crud.querySelectorAll('td.compact-stack-cell .cell')].filter(vis).slice(0,30).map(x=>({height:n(x.getBoundingClientRect().height),children:[...x.children].map(c=>({height:n(c.getBoundingClientRect().height),marginTop:getComputedStyle(c).marginTop,marginBottom:getComputedStyle(c).marginBottom,scrollHeight:c.scrollHeight,clientHeight:c.clientHeight}))})):[];
    const taskErrors=[...document.querySelectorAll('.err-inline')].filter(vis).map(x=>({height:n(x.getBoundingClientRect().height),marginTop:getComputedStyle(x).marginTop,marginBottom:getComputedStyle(x).marginBottom,scrollHeight:x.scrollHeight,clientHeight:x.clientHeight,clipped:x.scrollHeight>x.clientHeight+1}));
    return { mainCount:document.querySelectorAll('main').length,h1Count:document.querySelectorAll('h1').length,crud:Boolean(crud),
      document:[document.documentElement.clientWidth,document.documentElement.scrollWidth],content:content?[content.clientWidth,content.scrollWidth]:null,main:main?[main.clientWidth,main.scrollWidth]:null,
      tables,controls,tags,stacks,taskErrors,redengine:document.querySelectorAll('[class*="redengine"],[class^="re-"],a[href*="redengine"]').length };
  });
  const productHover = async () => {
    const cells=page.locator('td.compact-clamp-cell .cell:visible'), samples=[];
    for(let i=0;i<await cells.count();i++){
      const cell=cells.nth(i), before=await cell.evaluate(el=>({text:el.textContent?.trim()||'',sw:el.scrollWidth,cw:el.clientWidth,sh:el.scrollHeight,ch:el.clientHeight,title:el.getAttribute('title')}));
      if(!before.text) continue;
      const kind=(before.sh>before.ch+1||before.sw>before.cw+1)?'long':'short';
      if(samples.some(x=>x.kind===kind)) continue;
      await cell.hover(); await page.waitForTimeout(80);
      const after=await cell.evaluate(el=>({title:el.getAttribute('title'),marker:el.getAttribute('data-bp-overflow-tooltip')}));
      samples.push({kind,textLength:before.text.length,metrics:[before.cw,before.sw,before.ch,before.sh],beforeTitle:before.title,afterTitle:after.title?'<full-text>':null,titleMatches:after.title===before.text,markerMatches:after.marker===before.text});
    }
    return {count:await cells.count(),long:samples.find(x=>x.kind==='long')||null,short:samples.find(x=>x.kind==='short')||null};
  };
  const cancel = async name => {
    const [kind,label]=cancelTargets[name]; let trigger;
    if(kind==='row'){
      const row=page.locator('.el-table__body-wrapper tbody tr:visible').first(); if(!await row.count()) return {name,status:'no-real-row'};
      trigger=row.getByRole('button',{name:label,exact:true}).first();
    } else trigger=page.getByRole('button',{name:new RegExp(label)}).first();
    if(!await trigger.count()||!await trigger.isVisible()) return {name,status:'no-trigger'};
    await trigger.click(); const dialog=page.locator('.el-dialog:visible').last();
    try{await dialog.waitFor({state:'visible',timeout:5000});}catch{return {name,status:'dialog-not-opened'};}
    const title=((await dialog.locator('.el-dialog__title').textContent())||'').trim();
    await page.screenshot({path:`${ROOT}/screenshots/${name}-dialog.png`,mask:[dialog.locator('input,textarea,.el-descriptions__content')],maskColor:'#d8dee9'});
    const button=dialog.getByRole('button',{name:'取消',exact:true}).last(); if(!await button.count()) return {name,status:'cancel-missing',title};
    await button.click(); await dialog.waitFor({state:'hidden',timeout:5000}); return {name,status:'opened-and-cancelled',title};
  };
  const reset = async name => {
    if(name==='EvalTasks'){
      const input=page.getByLabel('按任务名称批次或创建人筛选'); await input.fill('qa-read-only-reset');
      await page.getByRole('button',{name:'重置',exact:true}).first().click();
      return {name,status:(await input.inputValue())===''?'cleared':'not-cleared'};
    }
    const button=page.getByRole('button',{name:'重置',exact:true}).first();
    if(!await button.count()) return {name,status:'missing'}; await button.click(); return {name,status:'clicked'};
  };
  const tabs = async () => {
    const meta=await page.evaluate(()=>{const root=document.querySelector('.workspace-tabs'),scroll=document.querySelector('.workspace-tabs__scroll'),items=[...document.querySelectorAll('.workspace-tabs__tab')],closes=[...document.querySelectorAll('.workspace-tabs__close')],active=document.querySelector('.workspace-tabs__tab--active'),sr=scroll?.getBoundingClientRect(),ar=active?.getBoundingClientRect();return{height:Math.round(root?.getBoundingClientRect().height||0),count:items.length,itemHeights:[...new Set(items.map(x=>Math.round(x.getBoundingClientRect().height)))],mins:[...new Set(items.map(x=>getComputedStyle(x).minWidth))],maxs:[...new Set(items.map(x=>getComputedStyle(x).maxWidth))],closeSizes:[...new Set(closes.map(x=>`${Math.round(x.getBoundingClientRect().width)}x${Math.round(x.getBoundingClientRect().height)}`))],overflow:Boolean(scroll&&scroll.scrollWidth>scroll.clientWidth+1),activeVisible:Boolean(sr&&ar&&ar.left>=sr.left-1&&ar.right<=sr.right+1)}});
    const labels=page.locator('.workspace-tabs__label'),last=await labels.count()-1; await labels.nth(last).focus(); await page.keyboard.press('ArrowLeft');
    const left=await page.evaluate(()=>[...document.querySelectorAll('.workspace-tabs__label')].indexOf(document.activeElement)); await page.keyboard.press('Home');
    const home=await page.evaluate(()=>[...document.querySelectorAll('.workspace-tabs__label')].indexOf(document.activeElement)); await page.keyboard.press('End');
    const end=await page.evaluate(()=>[...document.querySelectorAll('.workspace-tabs__label')].indexOf(document.activeElement)); return {...meta,keyboard:{last,left,home,end}};
  };

  for(const spec of specs){
    if(out.firstError) break;
    const [name,path,param,disc]=spec; let actual=path,template=path?.split('?')[0];
    if(param){const [tpl,key,query]=param, value=ids[key]; template=tpl; if(!value){out.firstError={route:name,issues:['real-id-missing']};break;} actual=tpl.replace(/:[^/]+/,encodeURIComponent(value))+(query?`?${query}`:'');}
    const expected=actual.split('?')[0],events=[],requests=[],map=new Map(),pending=new Set(),reads=[],failed=[];
    const onConsole=m=>events.push({type:m.type(),text:m.text().slice(0,500)});
    const onRequest=r=>{if(!r.url().includes('/api/'))return;const x={method:r.method(),url:r.url(),status:null,code:null};map.set(r,x);requests.push(x);pending.add(r);};
    const onResponse=r=>{const q=r.request(),x=map.get(q);if(!x)return;x.status=r.status();pending.delete(q);reads.push((async()=>{if(!(r.headers()['content-type']||'').includes('json'))return;try{const b=await r.json();if(b&&Object.hasOwn(b,'code'))x.code=b.code;}catch{}})());};
    const onFailed=r=>{if(r.url().includes('/api/'))failed.push({method:r.method(),url:r.url(),error:r.failure()?.errorText});pending.delete(r);};
    page.on('console',onConsole);page.on('request',onRequest);page.on('response',onResponse);page.on('requestfailed',onFailed);
    await page.evaluate(p=>{location.hash=p;},actual);await page.waitForTimeout(350);let settled=await idle(pending);
    const item={index:out.routes.length+1,name,path:template,status:'pass'};
    if(name==='InfoProducts'){item.productHover=out.productHover=await productHover();}
    if(name==='EvalTasks'||name==='SysConfig'){item.reset=await reset(name);out.resets.push(item.reset);settled=(await idle(pending))&&settled;}
    if(cancelTargets[name]){item.cancellation=await cancel(name);out.cancellations.push(item.cancellation);settled=(await idle(pending))&&settled;}
    item.dom=await dom();
    if(shots.has(name))await page.screenshot({path:`${ROOT}/screenshots/${name}.png`,mask:[page.locator('.el-table__body:visible')],maskColor:'#d8dee9'});
    if(disc){item.discovery=await discover(disc);settled=(await idle(pending))&&settled;}
    if(name==='SysPersonTags'){item.tabs=out.tabs=await tabs();}
    await Promise.allSettled(reads); page.off('console',onConsole);page.off('request',onRequest);page.off('response',onResponse);page.off('requestfailed',onFailed);
    const dynamic=events.filter(x=>x.type==='warning'&&x.text.includes('[Vue Router warn]: No match found for location with path'));
    const warnings=events.filter(x=>x.type==='warning'&&!x.text.includes('[Vue Router warn]: No match found for location with path'));
    const errors=events.filter(x=>x.type==='error'),fallback=events.filter(x=>x.text.includes('[api fallback]'));
    const writes=requests.filter(x=>!['GET','HEAD','OPTIONS'].includes(x.method)),bad=requests.filter(x=>x.status==null||x.status>=400||(x.code!=null&&String(x.code)!=='0'));
    item.console={errors:errors.length,nonDynamicWarnings:warnings.length,allowedDynamicWarnings:dynamic.length,fallback:fallback.length,entries:events};
    item.network={settled,requests:requests.map(x=>({...x,url:redacted(x.url)})),failed:failed.map(x=>({...x,url:redacted(x.url)})),writes:writes.map(x=>({...x,url:redacted(x.url)})),bad:bad.map(x=>({...x,url:redacted(x.url)}))};
    if(warningTargets.has(name))out.warningTargets[name]=warnings.length;
    const issues=[],d=item.dom;
    if((await page.evaluate(()=>location.hash.replace(/^#/,'').split('?')[0]))!==expected)issues.push('redirect');
    if(d.mainCount!==1||d.h1Count<1)issues.push('semantic-main-h1'); if(independent.has(name)?d.crud:!d.crud)issues.push('crud-scope');
    if(d.document[1]!==d.document[0]||(d.content&&d.content[1]!==d.content[0])||(d.main&&d.main[1]!==d.main[0]))issues.push('outer-horizontal-overflow');
    for(const t of d.tables){
      if(t.tableBorder.some(x=>x!=='1px'))issues.push(`table-${t.index}-outer-border`);
      if(t.headerRows.some(x=>x!==40)||t.headerCells.some(x=>x!==40)||t.rows.some(x=>x!==40)||t.cells.some(x=>x.height!==40))issues.push(`table-${t.index}-height-not-40`);
      if(t.cells.some(x=>x.paddingTop!=='0px'||x.paddingBottom!=='0px'||x.borderBottom!=='1px'||x.borderRight!=='1px'))issues.push(`table-${t.index}-cell-box`);
      if(t.scroller&&t.scroller.scrollWidth>t.scroller.clientWidth+1&&!['auto','scroll'].includes(t.scroller.overflowX))issues.push(`table-${t.index}-internal-scroll`);
      if(t.ops.some(x=>x.position!=='sticky'||x.right==='auto'||x.whiteSpace!=='nowrap'||x.overflow!=='visible'||x.textOverflow!=='clip'))issues.push(`table-${t.index}-operation-sticky`);
    }
    if(d.controls.some(x=>x!==32))issues.push('control-not-32');if(d.tags.some(x=>x!==24))issues.push('tag-not-24');
    if(d.stacks.some(x=>x.height!==39||x.children.some(c=>c.marginTop!=='0px'||c.marginBottom!=='0px'||c.scrollHeight>c.clientHeight+1)))issues.push('compact-stack-clipped');
    if(name==='PerfTaskMonitor'&&d.taskErrors.length&&d.taskErrors.some(x=>x.marginTop!=='0px'||x.marginBottom!=='0px'||x.clipped))issues.push('task-monitor-error-clipped');
    if(errors.length||warnings.length||fallback.length||failed.length||writes.length||bad.length||!settled)issues.push('runtime-or-network');
    if(item.cancellation&&item.cancellation.status!=='opened-and-cancelled')issues.push(`cancel-${item.cancellation.status}`);
    if(item.reset&&!['cleared','clicked'].includes(item.reset.status))issues.push(`reset-${item.reset.status}`);
    if(name==='InfoProducts'){
      if(!out.productHover.short||out.productHover.short.afterTitle!==null)issues.push('product-short-hover-title');
      if(out.productHover.long&&!out.productHover.long.titleMatches)issues.push('product-long-hover-title');
      item.productLongOverflowStatus=out.productHover.long?'exercised-real-overflow-sample':'not_exercised_no_real_overflow_sample';
    }
    if(name==='SysPersonTags'){const x=out.tabs;if(x.height!==48||x.itemHeights.some(v=>v!==40)||!x.mins.includes('112px')||!x.maxs.includes('200px')||x.closeSizes.some(v=>v!=='40x40')||!x.overflow||!x.activeVisible||x.keyboard.left!==x.keyboard.last-1||x.keyboard.home!==0||x.keyboard.end!==x.keyboard.last)issues.push('tabs');}
    item.issues=[...new Set(issues)];if(item.issues.length){item.status='fail';out.firstError={route:name,index:item.index,issues:item.issues};}
    out.routes.push(item);await save();
  }
  out.summary={expected:59,recorded:out.routes.length,passed:out.routes.filter(x=>x.status==='pass').length,failed:out.routes.filter(x=>x.status==='fail').length,realIds:out.discoveries.filter(x=>x.found&&x.status===200&&String(x.code)==='0').length,cancellations:out.cancellations.filter(x=>x.status==='opened-and-cancelled').length,resets:out.resets.length,warningTargetsZero:Object.values(out.warningTargets).filter(x=>x===0).length};
  out.status=out.firstError?'fail-stopped':'pass';await save();return{status:out.status,summary:out.summary,firstError:out.firstError};
}
