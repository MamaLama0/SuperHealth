'use strict';
// Hand-drawn, deterministic effects shared by cutout photos and 3D fighters.
window.DuelEffects=(()=>{
  const palettes={FOR:['#ffb65c','#fff1ae'],DEX:['#82d8df','#e2ffff'],CON:['#c0a5ff','#f5e7ff'],PEC:['#ffb65c','#fff1ae'],DOS:['#82d8df','#e2ffff'],BRA:['#c0a5ff','#f5e7ff'],JAM:['#a9ca83','#f2e7ae'],RÉG:['#83dbad','#e4ffd7'],CAR:['#ffe17b','#ffffd1']};
  function line(c,points,color,width){c.strokeStyle=color;c.lineWidth=width;c.lineCap='round';c.lineJoin='round';c.beginPath();points.forEach(([x,y],i)=>i?c.lineTo(x,y):c.moveTo(x,y));c.stroke();}
  function star(c,x,y,r,color,phase=0){c.fillStyle=color;c.beginPath();for(let i=0;i<12;i++){const a=i*Math.PI/6+phase,k=i%2?.35:1;c.lineTo(x+Math.cos(a)*r*k,y+Math.sin(a)*r*k);}c.closePath();c.fill();}
  function ring(c,x,y,r,color,width=3,ratio=1){c.strokeStyle=color;c.lineWidth=width;c.beginPath();c.ellipse(x,y,r,r*ratio,0,0,Math.PI*2);c.stroke();}
  return {
    attack(c,w,h,age,side,stat,seed){
      if(age<-.66||age>.62)return;
      const [color,core]=palettes[stat]||palettes.PEC,s=w/420;
      const ox=w*(side===1?.285:.715),tx=w*(side===1?.715:.285),y=h*.67;
      c.save();
      if(age<-.22){
        const p=(age+.66)/.44;c.globalAlpha=.65*p;
        ring(c,ox,h*.838,(24+18*p)*s,color,3*s,.28);
        for(let i=0;i<6;i++){
          const a=i*Math.PI/3+p*2,rad=(40-23*p)*s;
          star(c,ox+Math.cos(a)*rad,y+Math.sin(a)*rad,3*s,color,a);
        }
        c.globalAlpha=p;star(c,ox+side*13*s,y,(4+9*p)*s,core,p);
      }else if(age<0){
        const p=(age+.22)/.22,x=ox+(tx-ox)*p,arc=Math.sin(p*Math.PI)*h*.045;
        c.globalAlpha=.28;line(c,[[ox,y],[x,y-arc]],color,15*s);
        c.globalAlpha=1;
        if(stat==='CAR'||stat==='BRA'){
          const pts=[];for(let i=0;i<=8;i++)pts.push([ox+(x-ox)*i/8,y-arc*i/8+(i%2?9:-9)*s*Math.sin(i*Math.PI/8)]);
          line(c,pts,color,7*s);line(c,pts,core,2*s);
        }else{
          line(c,[[x-side*40*s,y-arc+7*s],[x,y-arc]],color,13*s);
          star(c,x,y-arc,stat==='DOS'?16*s:12*s,core,p*3);
        }
      }else{
        const p=age/.62,ease=1-Math.pow(1-p,3);
        // One brief, low-opacity flash, then a bold graphic impact and fading debris.
        c.globalAlpha=.10*Math.max(0,1-age/.10);c.fillStyle=core;c.fillRect(0,0,w,h);
        c.globalAlpha=Math.max(0,1-p*1.5);
        star(c,tx,y,(22+42*ease)*s,color,seed*.4);
        star(c,tx,y,(12+25*ease)*s,core,seed*.4);
        c.globalAlpha=1-p;
        ring(c,tx,y,(10+66*ease)*s,color,(4-3*p)*s);
        if(stat==='DOS'||stat==='BRA'){
          line(c,[[tx-28*s,y+38*s],[tx+28*s,y-38*s]],core,7*(1-p)*s);
          if(stat==='BRA')line(c,[[tx-28*s,y-38*s],[tx+28*s,y+38*s]],color,5*(1-p)*s);
        }
        for(let i=0;i<12;i++){
          const a=i*2.399+seed*.5,rad=(20+(i%3?68:92)*ease)*s;
          const x=tx+Math.cos(a)*rad,yy=y+Math.sin(a)*rad+p*p*28*s;
          line(c,[[x,yy],[x+Math.cos(a)*8*(1-p)*s,yy+Math.sin(a)*8*(1-p)*s]],i%3?color:core,3*s);
        }
        if(stat==='JAM'||stat==='RÉG'){
          c.globalAlpha=(1-p)*.65;
          for(let i=0;i<5;i++)ring(c,tx+(i-2)*16*s,h*.838,(6+20*ease)*s,color,3*s,.35);
        }
      }
      c.restore();
    },
    finale(c,w,h,p,winner){
      const x=w*(winner===1?.285:winner===-1?.715:.5),y=h*.32,s=w/420;
      c.save();c.globalAlpha=Math.min(1,p*4)*(1-p*.3);
      // A short victory crest; the result remains determined solely by the stats.
      ring(c,x,y,(20+12*p)*s,'#f0cf7f',3*s);
      star(c,x,y,17*s,'#ffe7a3',-.3);
      for(let i=0;i<10;i++){
        const a=i*Math.PI/5,rad=(32+25*p)*s;
        star(c,x+Math.cos(a)*rad,y+Math.sin(a)*rad,3*s,i%2?'#ffbd99':'#a9d9ef',p*3);
      }
      c.restore();
    }
  };
})();
