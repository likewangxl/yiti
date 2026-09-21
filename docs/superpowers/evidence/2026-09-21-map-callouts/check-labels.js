async page => {
  const map = page.locator('.panorama-map-panel .panorama-map');
  const result = await map.evaluate(el => {
    const bounds = el.getBoundingClientRect();
    const buttons = [...el.querySelectorAll('button[data-city-code]')];
    const lines = [...el.querySelectorAll('[data-testid="map-city-callout-line"]')];
    const rects = buttons.map(button => ({code:button.dataset.cityCode, text:button.textContent.trim(), ...button.getBoundingClientRect().toJSON()}));
    const overlap = [];
    rects.forEach((a,i)=>rects.slice(i+1).forEach(b=>{if(a.left<b.right && a.right>b.left && a.top<b.bottom && a.bottom>b.top) overlap.push([a.code,b.code]);}));
    const outside = rects.filter(r=>r.left<bounds.left-1 || r.right>bounds.right+1 || r.top<bounds.top-1 || r.bottom>bounds.bottom+1).map(r=>r.code);
    const controls = el.querySelector('.panorama-map__controls').getBoundingClientRect();
    const controlOverlap = rects.filter(r=>r.left<controls.right && r.right>controls.left && r.top<controls.bottom && r.bottom>controls.top).map(r=>r.code);
    return {width:bounds.width,height:bounds.height,labels:rects.length,lines:lines.length,overlap,outside,controlOverlap,webgl:el.dataset.webglReady,rects};
  });
  if(result.labels!==10 || result.lines!==10 || result.overlap.length || result.outside.length || result.controlOverlap.length) throw new Error(JSON.stringify(result));
  return result;
}
