async page => {
  const result=await page.locator('.panorama-map-panel .panorama-map').evaluate(el=>{
    const lines=[...el.querySelectorAll('[data-testid="map-city-callout-line"]')];
    return lines.map(line=>{
      const circle=el.querySelector(`path[data-region-code="${line.dataset.cityCode}"]`).parentElement.querySelector('circle');
      const a=new DOMPoint(circle.cx.baseVal.value,circle.cy.baseVal.value).matrixTransform(circle.getScreenCTM());
      const first=line.points.getItem(0);
      const b=new DOMPoint(first.x,first.y).matrixTransform(line.getScreenCTM());
      return {code:line.dataset.cityCode,error:Math.hypot(a.x-b.x,a.y-b.y)};
    });
  });
  if(result.some(r=>r.error>1))throw new Error(JSON.stringify(result));
  return {anchorsChecked:result.length,maxPixelError:Math.max(...result.map(r=>r.error))};
}
