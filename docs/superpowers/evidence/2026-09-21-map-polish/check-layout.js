async page => {
 const result=await page.locator('.panorama-map-panel .panorama-map').evaluate(el=>{
  const paths=[...el.querySelectorAll('[data-testid="map-city-callout-line"]')];
  const labels=[...el.querySelectorAll('button[data-city-code]')];
  const colors=new Set(paths.map(p=>getComputedStyle(p).stroke));
  const ys=[...new Set(labels.map(b=>Number(b.getBoundingClientRect().y.toFixed(2))))].sort((a,b)=>a-b);
  const steps=ys.slice(1).map((y,i)=>y-ys[i]);
  return {labels:labels.length,curves:paths.filter(p=>p.getAttribute('d').includes(' C ')).length,colors:colors.size,rows:ys.length,stepDifference:Math.max(...steps)-Math.min(...steps)};
 });
 if(result.labels!==10 || result.curves!==10 || result.colors<5 || result.rows!==5 || result.stepDifference>.1)throw new Error(JSON.stringify(result));
 const gauge=page.locator('[data-testid="completion-water-gauge"]');
 if(await gauge.count()!==1 || await page.locator('[data-testid="target-progress-bullet"]').count())throw new Error('duplicate progress chart');
 return {...result,waterLevel:await gauge.getAttribute('data-level'),waterTone:await gauge.getAttribute('data-tone')};
}
