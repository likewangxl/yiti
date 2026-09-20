/** Avoid collisions in projected screen space; geographic geometry stays unchanged. */
export function layoutMapLabels(labels) {
  const placed=[]; const result={};
  for (const label of labels) {
    const offsets=[[0,0]];
    for(let ring=1;ring<=5;ring++) for(const [x,y] of [[0,1],[0,-1],[1,0],[-1,0],[1,1],[-1,1],[1,-1],[-1,-1]]) offsets.push([x*ring,y*ring]);
    const w=label.width,h=label.height;
    let chosen;
    for(const [dx,dy] of offsets) {
      const x=Math.max(w/2+1,Math.min(99-w/2,label.x+dx*(w+1)));
      const y=Math.max(h/2+1,Math.min(99-h/2,label.y+dy*(h+1)));
      if(!placed.some(p=>Math.abs(x-p.x)<(w+p.width)/2+0.4&&Math.abs(y-p.y)<(h+p.height)/2+0.4)) {chosen={x,y};break;}
    }
    chosen ||= {x:Math.max(w/2+1,Math.min(99-w/2,label.x)),y:Math.max(h/2+1,Math.min(99-h/2,label.y))};
    placed.push({...chosen,width:w,height:h});result[label.key]=chosen;
  }
  return result;
}
