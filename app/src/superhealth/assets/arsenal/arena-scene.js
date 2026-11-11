'use strict';
// A small, resolution-independent cartoon set. No bitmap scenery or photographic textures.
window.ArenaScene=(()=>{
  let cache=null,lastW=0,lastH=0;
  function oval(c,x,y,rx,ry,color){c.fillStyle=color;c.beginPath();c.ellipse(x,y,rx,ry,0,0,Math.PI*2);c.fill();}
  function poly(c,points,color){c.fillStyle=color;c.beginPath();points.forEach(([x,y],i)=>i?c.lineTo(x,y):c.moveTo(x,y));c.closePath();c.fill();}
  function scenery(w,h){
    const out=document.createElement('canvas');out.width=w;out.height=h;const c=out.getContext('2d');
    const sky=c.createLinearGradient(0,0,0,h*.66);sky.addColorStop(0,'#233956');sky.addColorStop(1,'#658b91');c.fillStyle=sky;c.fillRect(0,0,w,h);
    oval(c,w*.71,h*.18,w*.075,w*.075,'#d9d69d');oval(c,w*.69,h*.16,w*.07,w*.07,'#354f69');
    for(const [x,y,k] of [[.18,.21,1],[.60,.30,.7],[.83,.11,.5]]){
      oval(c,w*x,h*y,w*.075*k,h*.014*k,'#9db3b23b');oval(c,w*(x+.045*k),h*(y-.008),w*.06*k,h*.022*k,'#9db3b23b');
    }
    poly(c,[[0,h*.50],[0,h*.38],[w*.14,h*.29],[w*.28,h*.42],[w*.45,h*.24],[w*.65,h*.43],[w*.82,h*.32],[w,h*.42],[w,h*.58]],'#415e70');
    poly(c,[[w*.28,h*.42],[w*.45,h*.24],[w*.48,h*.37],[w*.59,h*.44]],'#6a8590');
    poly(c,[[0,h*.53],[w*.14,h*.42],[w*.35,h*.54],[w*.55,h*.40],[w*.78,h*.55],[w,h*.45],[w,h*.74],[0,h*.74]],'#315754');
    for(const [x,y,k] of [[.02,.45,1],[.16,.49,.7],[.91,.46,.9],[.98,.42,1.2]]){
      c.fillStyle='#35473e';c.fillRect(w*x-w*.009*k,h*y,w*.018*k,h*.16*k);
      for(let j=0;j<3;j++)poly(c,[[w*x,h*(y-.12*k+j*.035*k)],[w*(x-.065*k),h*(y+.06*k+j*.03*k)],[w*(x+.065*k),h*(y+.06*k+j*.03*k)]],j%2?'#487561':'#3b6657');
    }
    // Curved, chunky spectator wall behind the playing field.
    oval(c,w*.5,h*.68,w*.72,h*.19,'#344b4b');
    oval(c,w*.5,h*.65,w*.72,h*.18,'#687c75');
    oval(c,w*.5,h*.65,w*.61,h*.12,'#3d5b57');
    for(let i=0;i<11;i++){
      const a=Math.PI+i*Math.PI/10,x=w*.5+Math.cos(a)*w*.65,y=h*.65+Math.sin(a)*h*.16;
      poly(c,[[x-w*.043,y-h*.04],[x+w*.041,y-h*.04],[x+w*.043,y+h*.045],[x-w*.041,y+h*.045]],i%2?'#74847a':'#839387');
      c.fillStyle='#9aa696';c.fillRect(x-w*.043,y-h*.04,w*.084,h*.008);
    }
    // Two pennants and simplified stone columns, not a cage of vertical bars.
    for(const [x,color] of [[.12,'#719bc4'],[.88,'#cf866f']]){
      c.fillStyle='#536960';c.fillRect(w*(x-.025),h*.28,w*.05,h*.34);
      poly(c,[[w*(x-.045),h*.29],[w*(x+.045),h*.29],[w*(x+.035),h*.32],[w*(x-.035),h*.32]],'#9caa91');
      poly(c,[[w*(x-.041),h*.32],[w*(x+.041),h*.32],[w*(x+.041),h*.47],[w*x,h*.50],[w*(x-.041),h*.47]],color);
      poly(c,[[w*x,h*.36],[w*(x+.018),h*.40],[w*x,h*.44],[w*(x-.018),h*.40]],'#ebd7a1');
      c.fillStyle='#c2c1a0';c.fillRect(w*(x-.044),h*.315,w*.088,h*.008);
    }
    // A broad sandstone stage with a beveled edge and a very quiet central crest.
    oval(c,w*.5,h*.88,w*.74,h*.30,'#314743');
    oval(c,w*.5,h*.85,w*.73,h*.29,'#7b8068');
    oval(c,w*.5,h*.825,w*.71,h*.28,'#b5ad85');
    oval(c,w*.5,h*.825,w*.65,h*.245,'#8e9276');
    oval(c,w*.5,h*.816,w*.62,h*.225,'#aaa684');
    c.strokeStyle='#c8bc91';c.lineWidth=w*.006;c.beginPath();c.ellipse(w*.5,h*.816,w*.60,h*.216,0,0,Math.PI*2);c.stroke();
    for(let i=0;i<16;i++){
      const a=i*Math.PI/8;
      c.strokeStyle='#737d6655';c.lineWidth=1.5;c.beginPath();c.moveTo(w*.5+Math.cos(a)*w*.65,h*.825+Math.sin(a)*h*.245);c.lineTo(w*.5+Math.cos(a)*w*.71,h*.825+Math.sin(a)*h*.28);c.stroke();
    }
    c.strokeStyle='#727e6744';c.lineWidth=2;c.beginPath();c.ellipse(w*.5,h*.82,w*.16,h*.07,0,0,Math.PI*2);c.stroke();
    for(let i=0;i<8;i++){
      const a=i*Math.PI/4,x=w*.5+Math.cos(a)*w*.18,y=h*.82+Math.sin(a)*h*.078;
      poly(c,[[x,y-h*.013],[x+w*.009,y],[x,y+h*.013],[x-w*.009,y]],'#737d6640');
    }
    for(const x of [.285,.715])oval(c,w*x,h*.844,w*.14,h*.025,'#30463b35');
    for(const [x,y,k] of [[.02,.80,1],[.98,.79,.9],[.03,.97,1.2],[.96,.96,1.4]]){
      poly(c,[[w*x-w*.04*k,h*y],[w*x,h*y-h*.025*k],[w*x+w*.045*k,h*y],[w*x+w*.033*k,h*y+h*.02*k],[w*x-w*.03*k,h*y+h*.02*k]],'#6c8065');
      poly(c,[[w*x-w*.02*k,h*y],[w*x-w*.04*k,h*y-h*.055*k],[w*x,h*y-h*.015*k],[w*x+w*.025*k,h*y-h*.045*k],[w*x+w*.015*k,h*y]],'#4e7858');
    }
    const shade=c.createLinearGradient(0,0,0,h);shade.addColorStop(0,'#142c3b44');shade.addColorStop(.5,'#142c3b00');shade.addColorStop(1,'#142c3b22');c.fillStyle=shade;c.fillRect(0,0,w,h);
    return out;
  }
  return {paint(canvas,time=0){
    const dpr=Math.min(devicePixelRatio,1.5),w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);
    if(w<2||h<2)return;
    if(!cache||w!==lastW||h!==lastH){lastW=w;lastH=h;cache=scenery(w,h);canvas.width=w;canvas.height=h;}
    const c=canvas.getContext('2d');c.clearRect(0,0,w,h);c.drawImage(cache,0,0);
    for(let i=0;i<7;i++){
      const x=w*(.1+.8*((i*.137+time*.009)%1)),y=h*(.2+.35*((i*.213-time*.014+10)%1));
      oval(c,x,y,1.4*dpr,1.4*dpr,'#e4d69c55');
    }
  }};
})();
