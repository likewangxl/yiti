async page => {
  const ROOT='/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/2560';
  const evidence={batch:'page-optimization2-final-pass-2560-tabs',gitHead:'4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',viewport:{width:2560,height:1440},steps:[],requests:[],console:[],fatalIssue:null};
  const safe=new Set(['GET','HEAD','OPTIONS']);
  const sanitize=value=>String(value).replace('http://127.0.0.1:8091','').replace(/([?&][^=&#]+)=([^&#]*)/g,'$1=<redacted>').replace(/\/[0-9a-f]{16,}(?=\/|[?#]|$)/gi,'/<redacted-id>');
  const requestMap=new Map();
  const onRequest=request=>{if(!request.url().includes('/api/'))return;const item={method:request.method(),url:sanitize(request.url()),status:null,failure:null};requestMap.set(request,item);evidence.requests.push(item);};
  const onResponse=response=>{const item=requestMap.get(response.request());if(item)item.status=response.status();};
  const onFailed=request=>{const item=requestMap.get(request);if(item)item.failure=request.failure()?.errorText||'failed';};
  const onConsole=message=>evidence.console.push({type:message.type(),text:message.text().slice(0,500)});
  page.on('request',onRequest);page.on('response',onResponse);page.on('requestfailed',onFailed);page.on('console',onConsole);
  const settle=async(ms=900)=>{await page.waitForTimeout(ms);await page.evaluate(()=>document.fonts?.ready);};
  const geom=async label=>page.evaluate(label=>{
    const scroll=document.querySelector('.workspace-tabs__scroll'),active=document.querySelector('.workspace-tabs__tab--active'),activeLabel=active?.querySelector('.workspace-tabs__label'),focused=document.activeElement;
    const sr=scroll?.getBoundingClientRect(),ar=active?.getBoundingClientRect(),fr=focused?.getBoundingClientRect();const inside=(r,s)=>Boolean(r&&s&&r.left>=s.left-1&&r.right<=s.right+1);
    return{label,hash:location.hash.split('?')[0],count:document.querySelectorAll('.workspace-tabs__tab').length,scroll:{left:scroll?.scrollLeft,width:scroll?.clientWidth,total:scroll?.scrollWidth,viewLeft:sr?.left,viewRight:sr?.right},
      active:{key:active?.dataset.tabKey,text:activeLabel?.textContent?.trim(),left:ar?.left,right:ar?.right,fullyVisible:inside(ar,sr)},
      focus:{tag:focused?.tagName,id:focused?.id,class:typeof focused?.className==='string'?focused.className:null,text:focused?.classList?.contains('workspace-tabs__label')?focused.textContent?.trim():null,left:fr?.left,right:fr?.right,fullyVisible:inside(fr,sr),sameAsActiveLabel:focused===activeLabel}};
  },label);
  const save=async()=>{const promise=page.waitForEvent('download');await page.evaluate(text=>{const u=URL.createObjectURL(new Blob([text],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='tabs-focus-intents.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),0);},JSON.stringify(evidence,null,2));await(await promise).saveAs(`${ROOT}/json/tabs-focus-intents.json`);};
  const stop=async issues=>{evidence.fatalIssue={issues};page.off('request',onRequest);page.off('response',onResponse);page.off('requestfailed',onFailed);page.off('console',onConsole);await save();return{fatalIssue:evidence.fatalIssue,steps:evidence.steps};};
  await page.evaluate(()=>{location.hash='/system/person-tags';});await settle();evidence.steps.push(await geom('initial-last-active'));
  let last=evidence.steps.at(-1);if(last.count<40||!last.active.fullyVisible)return stop(['initial-long-tabs-active-not-visible']);
  await page.locator('.workspace-tabs__tab--active .workspace-tabs__label').focus();await page.keyboard.press('Home');await settle(200);evidence.steps.push(await geom('home-focus'));
  await page.keyboard.press('ArrowRight');await settle(100);evidence.steps.push(await geom('arrow-right-focus'));
  await page.keyboard.press('ArrowLeft');await settle(100);evidence.steps.push(await geom('arrow-left-focus'));
  await page.keyboard.press('End');await settle(200);evidence.steps.push(await geom('end-focus'));
  if(!evidence.steps.at(-1).focus.fullyVisible)return stop(['keyboard-roving-focus-not-visible']);
  await page.keyboard.press('Enter');await settle();evidence.steps.push(await geom('enter-activates-last'));
  if(!evidence.steps.at(-1).active.fullyVisible)return stop(['enter-active-not-visible']);
  await page.locator('.workspace-tabs__tab--active .workspace-tabs__close').focus();evidence.steps.push(await geom('keyboard-close-focused'));await page.keyboard.press('Enter');
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));evidence.steps.push(await geom('keyboard-close-2raf'));await settle(900);evidence.steps.push(await geom('keyboard-close-900ms'));
  last=evidence.steps.at(-1);if(!last.active.fullyVisible||!last.focus.sameAsActiveLabel)return stop(['keyboard-active-close-focus']);
  await page.evaluate(()=>{location.hash='/system/person-tags';});await settle();evidence.steps.push(await geom('mouse-close-prepared'));
  await page.locator('.workspace-tabs__tab--active .workspace-tabs__close').click();await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));evidence.steps.push(await geom('mouse-close-2raf'));await settle(900);evidence.steps.push(await geom('mouse-close-900ms'));
  last=evidence.steps.at(-1);if(!last.active.fullyVisible||!last.focus.sameAsActiveLabel)return stop(['mouse-active-close-focus']);
  await page.evaluate(()=>{location.hash='/system/users';});await settle();evidence.steps.push(await geom('ordinary-navigation-main'));
  last=evidence.steps.at(-1);if(last.focus.id!=='app-main'&&!String(last.focus.class||'').includes('content'))return stop(['ordinary-navigation-main-focus']);
  const background=page.locator('.workspace-tabs__tab:not(.workspace-tabs__tab--active) .workspace-tabs__close').first();
  const backgroundKey=await background.locator('..').getAttribute('data-tab-key');await background.click();await settle();evidence.steps.push({...await geom('background-close'),closedKey:backgroundKey});
  last=evidence.steps.at(-1);if(last.hash!=='#/system/users'||!last.focus.sameAsActiveLabel)return stop(['background-close-current-focus']);
  const moved=await page.evaluate(()=>{const close=document.querySelector('.workspace-tabs__tab:not(.workspace-tabs__tab--active) .workspace-tabs__close');const target=document.querySelector('[data-testid="sidebar-toggle"]');if(!close||!target)return false;close.focus();close.click();target.focus();return true;});
  await settle();evidence.steps.push({...await geom('background-close-user-moved-focus'),exercised:moved});last=evidence.steps.at(-1);
  if(!moved||!String(last.focus.class||'').includes('shell-toggle'))return stop(['user-moved-focus-stolen']);
  const continuous=await page.evaluate(()=>{const buttons=[...document.querySelectorAll('.workspace-tabs__tab:not(.workspace-tabs__tab--active) .workspace-tabs__close')].slice(0,2);if(buttons.length<2)return{exercised:false};const keys=buttons.map(x=>x.closest('[data-tab-key]')?.dataset.tabKey);buttons[0].focus();buttons[0].click();const next=[...document.querySelectorAll('.workspace-tabs__tab:not(.workspace-tabs__tab--active) .workspace-tabs__close')].find(x=>!keys.includes(x.closest('[data-tab-key]')?.dataset.tabKey));if(!next)return{exercised:false,keys};next.focus();next.click();return{exercised:true,keys:[keys[0],next.closest('[data-tab-key]')?.dataset.tabKey]};});
  await settle();evidence.steps.push({...await geom('continuous-close-intents'),...continuous});last=evidence.steps.at(-1);
  if(!continuous.exercised||!last.focus.sameAsActiveLabel)return stop(['continuous-close-latest-intent-focus']);
  await page.screenshot({path:`${ROOT}/screenshots/tabs-focus-intents-pass.png`,mask:[page.locator('.el-table__body:visible')],maskColor:'#d8dee9'});
  const badConsole=evidence.console.filter(x=>x.type==='error'||(x.type==='warning'&&!x.text.includes('[Vue Router warn]: No match found for location with path'))||/api fallback|ResizeObserver loop/i.test(x.text));
  const badNetwork=evidence.requests.filter(x=>!safe.has(x.method)||x.failure||x.status==null||x.status>=400);
  evidence.summary={status:'pass',initialCount:evidence.steps[0].count,finalCount:last.count,badConsole:badConsole.length,badNetwork:badNetwork.length,unexpectedWrites:evidence.requests.filter(x=>!safe.has(x.method)).length};
  if(badConsole.length||badNetwork.length)return stop(['tabs-console-or-network']);
  page.off('request',onRequest);page.off('response',onResponse);page.off('requestfailed',onFailed);page.off('console',onConsole);await save();return{summary:evidence.summary,fatalIssue:null,steps:evidence.steps};
}
