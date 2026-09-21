async page => {
 await page.emulateMedia({ reducedMotion: 'reduce' });
 await page.evaluate(async () => {
  const {createApp,h}=await import('/node_modules/.vite/deps/vue.js');
  const Gauge=(await import('/src/views/screen/components/CompletionWaterGauge.vue')).default;
  const host=document.createElement('section');host.id='water-color-qa';
  Object.assign(host.style,{position:'fixed',inset:'100px auto auto 40px',zIndex:'6000',padding:'24px',borderRadius:'14px',background:'#081b39',color:'#d9e9ff',fontFamily:'sans-serif'});
  document.body.appendChild(host);
  window.__waterColorQa=createApp({render:()=>h('div',{},[h('p',{style:'margin:0 0 18px;font-size:14px'},'水位分色 · 仅开发态示例'),h('div',{style:'display:flex;gap:28px'},[35,72,85.92,110].map(value=>h(Gauge,{value})))])});
  window.__waterColorQa.mount(host);
 });
 await page.locator('#water-color-qa').evaluate(el=>{
  for(const gauge of el.querySelectorAll('[data-tone]')) {
   const filled=gauge.querySelector('.completion-water-gauge__wave--front, rect[fill]');
   const id=filled.getAttribute('fill').slice(5,-1);
   const resolved=document.getElementById(id).querySelector('stop').getAttribute('stop-color');
   const expected=gauge.querySelector('.completion-water-gauge__edge').getAttribute('stroke');
   if(resolved!==expected)throw new Error('SVG gradient collision: '+gauge.dataset.tone+' resolves '+resolved+' expected '+expected);
  }
 });
 const tones=await page.locator('#water-color-qa [data-tone]').evaluateAll(nodes=>nodes.map(n=>({level:n.dataset.level,tone:n.dataset.tone})));
 if(tones.map(t=>t.tone).join(',')!=='coral,gold,cyan,green')throw new Error('wrong color bands');
 await page.locator('#water-color-qa').screenshot({path:'/Users/likewang/workspace/cx-wsp/一体化经营管理系统/yiti/docs/superpowers/evidence/2026-09-21-map-polish/water-colors.png'});
 await page.evaluate(()=>{window.__waterColorQa.unmount();delete window.__waterColorQa;document.querySelector('#water-color-qa').remove();});
 return {developmentFixture:true,tones};
}
