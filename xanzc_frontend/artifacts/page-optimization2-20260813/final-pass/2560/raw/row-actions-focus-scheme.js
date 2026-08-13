async page => {
  const ROOT='/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560';
  const safe=new Set(['GET','HEAD','OPTIONS']);
  const focus=[['InfoProducts','/info/products'],['EvalTasks','/eval/tasks'],['SysUsers','/system/users'],['SysJobs','/system/jobs'],['SysNotifications','/system/notifications'],['SysResources','/system/resources'],['ScreenAdminDs','/screen-admin/datasources'],['ReportSql','/report/sql']];
  const evidence={batch:'page-optimization2-final-pass-2560-row-actions',gitHead:'4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',viewport:{width:2560,height:1440},staticMatrix:{physicalOperationColumns:58,adaptiveColumns:30},focusRoutes:{},scheme:null,dangerCancel:null,requests:[],console:[],fatalIssue:null};
  const sanitize=value=>String(value).replace('http://127.0.0.1:8091','').replace(/([?&][^=&#]+)=([^&#]*)/g,'$1=<redacted>').replace(/\/[0-9a-f]{16,}(?=\/|[?#]|$)/gi,'/<redacted-id>');
  const requestMap=new Map();
  const onRequest=request=>{if(!request.url().includes('/api/'))return;const item={method:request.method(),url:sanitize(request.url()),status:null,failure:null};requestMap.set(request,item);evidence.requests.push(item);};
  const onResponse=response=>{const item=requestMap.get(response.request());if(item)item.status=response.status();};
  const onFailed=request=>{const item=requestMap.get(request);if(item)item.failure=request.failure()?.errorText||'failed';};
  const onConsole=message=>evidence.console.push({type:message.type(),text:message.text().slice(0,500)});
  page.on('request',onRequest);page.on('response',onResponse);page.on('requestfailed',onFailed);page.on('console',onConsole);
  const settle=async(ms=1000)=>{await page.waitForTimeout(ms);await page.evaluate(()=>document.fonts?.ready);};
  const probe=async()=>page.evaluate(()=>{
    const vis=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};
    return[...document.querySelectorAll('[data-bp-row-actions-host]')].filter(vis).map((host,index)=>{const shown=host.querySelector('[data-bp-row-actions-visible]'),measure=host.querySelector('[data-bp-row-actions-probe]');const buttons=[...(shown?.querySelectorAll('button')||[])].filter(vis);
      const before=document.activeElement;measure?.querySelector('button')?.focus();const probeTookFocus=Boolean(measure?.contains(document.activeElement));before?.focus?.({preventScroll:true});
      return{index,mode:host.dataset.mode,measured:host.dataset.measured,clientWidth:host.clientWidth,requiredWidth:measure?.scrollWidth||0,relation:host.dataset.mode==='expanded'?(measure?.scrollWidth||0)<=host.clientWidth:!host.clientWidth||!(measure?.scrollWidth)||measure.scrollWidth>host.clientWidth,
        visible:buttons.map(b=>({text:b.textContent?.trim(),ariaLabel:b.getAttribute('aria-label'),disabled:b.matches(':disabled,[aria-disabled="true"],.is-disabled'),danger:b.classList.contains('el-button--danger')||b.closest('.danger-item')!=null})),
        measurement:{ariaHidden:measure?.getAttribute('aria-hidden'),inertAttr:measure?.hasAttribute('inert'),inertProperty:Boolean(measure?.inert),visibility:measure?getComputedStyle(measure).visibility:null,pointerEvents:measure?getComputedStyle(measure).pointerEvents:null,probeTookFocus,rects:measure?.getClientRects().length||0}};
    });
  });
  const synthesize=async(scope='body')=>{
    const base=page.locator(scope);const host=base.locator('[data-bp-row-actions-host]:visible').first();if(!await host.count())return{status:'not_exercised_no_real_host'};
    const requestStart=evidence.requests.length;
    const before=(await probe()).find(x=>x.index===0);
    const style=await host.evaluate(el=>{const cell=el.closest('td')?.querySelector('.cell')||el.parentElement;const old=cell.getAttribute('style');cell.style.width='72px';cell.style.maxWidth='72px';cell.style.minWidth='0';return{old};});await settle(550);
    const narrowed=(await probe()).find(x=>x.index===0);let menu=[];
    const more=host.getByRole('button',{name:/更多/});if(await more.count()){await more.click();const popper=page.locator('.el-dropdown-menu:visible');await popper.waitFor({state:'visible',timeout:3000});menu=await popper.locator('.el-dropdown-menu__item').evaluateAll(items=>items.map(x=>({text:x.textContent?.trim(),disabled:x.matches('[aria-disabled="true"],.is-disabled'),danger:x.classList.contains('danger-item')})));await more.click();await popper.waitFor({state:'hidden',timeout:3000});}
    await host.evaluate((el,old)=>{const cell=el.closest('td')?.querySelector('.cell')||el.parentElement;if(old==null)cell.removeAttribute('style');else cell.setAttribute('style',old);},style.old);await settle(550);const restored=(await probe()).find(x=>x.index===0);
    return{status:'synthetic-layout-only',sourceOrRowDataChanged:false,before,narrowed,compactMenu:menu,restored,requestDelta:evidence.requests.length-requestStart};
  };
  const save=async()=>{const promise=page.waitForEvent('download');await page.evaluate(text=>{const u=URL.createObjectURL(new Blob([text],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='row-actions-focus-scheme.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),0);},JSON.stringify(evidence,null,2));await(await promise).saveAs(`${ROOT}/json/row-actions-focus-scheme.json`);};
  const issues=[];
  for(const [name,path] of focus){await page.evaluate(p=>{location.hash=p;},path);await settle();const natural=await probe();const physical=await page.locator('th.operation-cell:visible').count();const route={physical,natural,synthetic:null};
    if(natural.length)route.synthetic=await synthesize();else route.synthetic={status:'not_exercised_no_runtime_row',reason:'ReportSql operation column is conditional export-task content'};
    evidence.focusRoutes[name]=route;await page.screenshot({path:`${ROOT}/screenshots/focus-${name}.png`,mask:[page.locator('.el-table__body td:not(.operation-cell):visible,input,textarea')],maskColor:'#d8dee9'});
    if(natural.some(h=>h.mode!=='expanded'||h.measured!=='true'||!h.relation||h.measurement.ariaHidden!=='true'||!h.measurement.inertAttr||!h.measurement.inertProperty||h.measurement.visibility!=='hidden'||h.measurement.pointerEvents!=='none'||h.measurement.probeTookFocus))issues.push(`${name}:natural-contract`);
    if(route.synthetic.status==='synthetic-layout-only'){
      const x=route.synthetic;const expanded=x.before.visible.map(v=>v.text);const compact=[...x.narrowed.visible.filter(v=>!v.text.includes('更多')).map(v=>v.text),...x.compactMenu.map(v=>v.text)];
      route.synthetic.actionOrderEqual=JSON.stringify(expanded)===JSON.stringify(compact);
      if(x.before.mode!=='expanded'||x.narrowed.mode!=='compact'||x.restored.mode!=='expanded'||x.requestDelta||!route.synthetic.actionOrderEqual)issues.push(`${name}:synthetic-contract`);
    }
    if(issues.length){evidence.fatalIssue={issues:[issues.at(-1)]};await save();return{fatalIssue:evidence.fatalIssue,focusRoutes:evidence.focusRoutes};}
  }
  await page.evaluate(()=>{location.hash='/info/products';});await settle();const dangerStart=evidence.requests.length;const danger=page.locator('[data-bp-row-actions-visible] .el-button--danger:visible').filter({hasText:'删除'}).first();
  if(await danger.count()){await danger.click();const box=page.locator('.el-message-box:visible');await box.waitFor({state:'visible',timeout:4000});await page.screenshot({path:`${ROOT}/screenshots/danger-confirm-cancel.png`,mask:[box.locator('.el-message-box__message')],maskColor:'#d8dee9'});await box.getByRole('button',{name:/取消/}).click();await box.waitFor({state:'hidden'});evidence.dangerCancel={status:'opened_and_cancelled',writeRequestDelta:evidence.requests.slice(dangerStart).filter(x=>!safe.has(x.method)).length};}
  else evidence.dangerCancel={status:'not_exercised_no_visible_danger'};
  if(evidence.dangerCancel.status!=='opened_and_cancelled'||evidence.dangerCancel.writeRequestDelta){evidence.fatalIssue={issues:['danger-confirm-cancel']};await save();return{fatalIssue:evidence.fatalIssue};}
  await page.evaluate(()=>{location.hash='/report/dynamic';});await settle();await page.getByRole('button',{name:/我的方案/}).click();const dialog=page.locator('.el-dialog:visible').filter({hasText:'我的查询方案'});await dialog.waitFor({state:'visible',timeout:4000});await settle(700);const schemeNatural=await probe();const scheme={realRows:await dialog.locator('.el-table__body tr:visible').count(),natural:schemeNatural,synthetic:null};
  if(schemeNatural.length)scheme.synthetic=await synthesize('.el-dialog:visible');else scheme.synthetic={status:'not_exercised_no_real_rows'};evidence.scheme=scheme;await page.screenshot({path:`${ROOT}/screenshots/scheme-row-actions.png`,mask:[dialog.locator('.el-table__body')],maskColor:'#d8dee9'});if(await dialog.isVisible()){await dialog.getByRole('button',{name:'关闭',exact:true}).click();await dialog.waitFor({state:'hidden'});}
  if(schemeNatural.some(h=>h.mode!=='expanded'||h.measured!=='true'||!h.relation||h.measurement.probeTookFocus))issues.push('Scheme:natural-contract');
  if(scheme.synthetic.status==='synthetic-layout-only'){
    const x=scheme.synthetic;scheme.synthetic.actionOrderEqual=JSON.stringify(x.before.visible.map(v=>v.text))===JSON.stringify([...x.narrowed.visible.filter(v=>!v.text.includes('更多')).map(v=>v.text),...x.compactMenu.map(v=>v.text)]);
    if(x.before.mode!=='expanded'||x.narrowed.mode!=='compact'||x.restored.mode!=='expanded'||x.requestDelta||!scheme.synthetic.actionOrderEqual)issues.push('Scheme:synthetic-contract');
  }
  const badConsole=evidence.console.filter(x=>x.type==='error'||(x.type==='warning'&&!x.text.includes('[Vue Router warn]: No match found for location with path'))||/api fallback|ResizeObserver loop/i.test(x.text));
  const badNetwork=evidence.requests.filter(x=>!safe.has(x.method)||x.failure||x.status==null||x.status>=400);
  evidence.summary={status:issues.length||badConsole.length||badNetwork.length?'fail':'pass',focusRoutes:8,runtimeRows:Object.values(evidence.focusRoutes).reduce((n,x)=>n+x.natural.length,0)+schemeNatural.length,syntheticRoutes:Object.values(evidence.focusRoutes).filter(x=>x.synthetic.status==='synthetic-layout-only').length,scheme:scheme.synthetic.status,badConsole:badConsole.length,badNetwork:badNetwork.length,unexpectedWrites:evidence.requests.filter(x=>!safe.has(x.method)).length,resizeObserverLoops:evidence.console.filter(x=>/ResizeObserver loop/i.test(x.text)).length};
  if(issues.length||badConsole.length||badNetwork.length)evidence.fatalIssue={issues:[...issues,...(badConsole.length?['console']:[]),...(badNetwork.length?['network']:[])]};
  page.off('request',onRequest);page.off('response',onResponse);page.off('requestfailed',onFailed);page.off('console',onConsole);await save();return{summary:evidence.summary,fatalIssue:evidence.fatalIssue,dangerCancel:evidence.dangerCancel,scheme:evidence.scheme};
}
