'use strict';
const landscape=document.querySelector('#landscape'),battleStage=document.querySelector('main');
const battleFx=document.querySelector('#effects'),fx2=battleFx.getContext('2d');
const beatLength=1.25,introLength=.25,firstImpact=.35,secondImpact=.86,finaleLength=.8;
let battle=null,battleFrame=0,battleTime=0,battleLast=0,battlePaused=false,battleSpeed=1,battleFinished=false,battleRound=-1;
let battleLoaded=false,battleHits=new Set(),battleGeneration=0;
let photoWork=Promise.resolve();
const fighterPhotos={me:null,them:null},photoSources={me:null,them:null},photoCutouts={me:true,them:true};
function paintPhoto(side,x,attack,hit,w,h){
  const image=fighterPhotos[side];if(!image)return;
  const sign=side==='me'?1:-1,ratio=image.naturalWidth/image.naturalHeight;
  const height=Math.min(h*.65,w*.38/ratio),width=height*ratio;
  fx2.save();fx2.translate(w*(x+1)/2,h*(.84-attack*.025));
  fx2.rotate(sign*(attack*.16-hit*.12));fx2.scale(1+attack*.055-hit*.055,1-attack*.045+hit*.025);
  fx2.filter=hit>0?'brightness('+(1+hit*.35)+')':'none';
  if(!photoCutouts[side]){fx2.beginPath();fx2.roundRect(-width/2,-height,width,height,14);fx2.clip();}
  fx2.drawImage(image,-width/2,-height,width,height);fx2.restore();
}
const pulse=(t,a,b)=>t<a||t>b?0:Math.sin((t-a)/(b-a)*Math.PI);
function publishHit(index,side){
  const key=index+'|'+side;
  if(battleHits.has(key))return;
  battleHits.add(key);
  const r=battle.rounds[index];
  native('hit',JSON.stringify({round:index,target:side,damage:side==='them'?r.mine:r.theirs,
    health:side==='them'?r.theirHealth:r.myHealth}));
}
function finishBattle(){
  if(!battle||battleFinished)return;
  battleFinished=true;
  cancelAnimationFrame(battleFrame);battleFrame=0;
  battleTime=introLength+battle.rounds.length*beatLength+finaleLength;
  paintBattle(performance.now());
  native('finish');
}
function paintBattle(now){
  ArenaScene.paint(landscape,prefersStill?0:battleTime);
  if(!battleLoaded)return;
  const round=Math.min(battle.rounds.length-1,Math.max(0,Math.floor((battleTime-introLength)/beatLength)));
  const local=battleTime-introLength-round*beatLength;
  const swing=t=>t<-.18||t>.22?0:t<0?Math.pow((t+.18)/.18,2):Math.pow(1-t/.22,2);
  const atA=round%2?secondImpact:firstImpact,atB=round%2?firstImpact:secondImpact;
  const swingA=battleFinished||prefersStill?0:swing(local-atA),swingB=battleFinished||prefersStill?0:swing(local-atB);
  const hitA=battleFinished||prefersStill?0:pulse(local,atB,atB+.24);
  const hitB=battleFinished||prefersStill?0:pulse(local,atA,atA+.24);
  const xA=-.43+swingA*.43-hitA*.10,xB=.43-swingB*.43+hitB*.10;
  if(battleStage?.style){
    const shake=(hitA+hitB)*Math.sin(local*95)*2.2;
    battleStage.style.transform=`translate(${shake}px,${-Math.abs(shake)*.4}px)`;
  }
  if(renderer){
    gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    const idle=prefersStill?0:Math.sin(now*.0013)*.025;
    if(!fighterPhotos.me)renderer.draw(battle.me,now,false,{clear:false,x:xA,yaw:.45+idle,
      attack:swingA,strike:hitA,lift:swingA*.04});
    if(!fighterPhotos.them)renderer.draw(battle.them,now,false,{clear:false,x:xB,yaw:-.45-idle,
      attack:swingB,strike:hitB,lift:swingB*.04});
  }
  const dpr=Math.min(devicePixelRatio,1.5),w=battleFx.clientWidth,h=battleFx.clientHeight;
  if(battleFx.width!==Math.round(w*dpr)||battleFx.height!==Math.round(h*dpr)){
    battleFx.width=Math.round(w*dpr);battleFx.height=Math.round(h*dpr);
  }
  fx2.setTransform(dpr,0,0,dpr,0,0);fx2.clearRect(0,0,w,h);
  paintPhoto('me',xA,swingA,hitA,w,h);paintPhoto('them',xB,swingB,hitB,w,h);
  if(prefersStill||battle.preview||battleFinished)return;
  const finale=battleTime-introLength-battle.rounds.length*beatLength;
  if(finale>=0){
    const last=battle.rounds.at(-1),winner=last.myHealth===last.theirHealth?0:last.myHealth>last.theirHealth?1:-1;
    DuelEffects.finale(fx2,w,h,Math.min(1,finale/finaleLength),winner);
    return;
  }
  if(local<0)return;
  for(const [side,at] of [[1,atA],[-1,atB]])
    DuelEffects.attack(fx2,w,h,(local-at)*1.75,side,battle.rounds[round]?.stat,round);
  fx2.globalAlpha=1;fx2.filter='none';
}
function battleTick(now){
  battleFrame=0;
  if(battlePaused||!battleLoaded||battleFinished)return;
  const dt=battleLast?Math.min((now-battleLast)/1000,.12):0;battleLast=now;
  battleTime+=dt*battleSpeed;
  const index=Math.floor((battleTime-introLength)/beatLength);
  if(index>=battle.rounds.length){
    if(battleTime>=introLength+battle.rounds.length*beatLength+finaleLength){finishBattle();return;}
    paintBattle(now);battleFrame=requestAnimationFrame(battleTick);return;
  }
  if(index>=0){
    if(index!==battleRound){battleRound=index;native('round',index);}
    const local=battleTime-introLength-index*beatLength;
    if(local>=firstImpact)publishHit(index,index%2?'me':'them');
    if(local>=secondImpact)publishHit(index,index%2?'them':'me');
  }
  paintBattle(now);
  battleFrame=requestAnimationFrame(battleTick);
}
function resumeBattle(){
  if(!battleFrame&&!battlePaused&&battleLoaded&&!battleFinished){battleLast=0;battleFrame=requestAnimationFrame(battleTick);}
}
window.duel={
  photos(data){
    photoWork=Promise.all(['me','them'].map(side=>{
      photoCutouts[side]=data[side+'Cutout']!==false;
      if(photoSources[side]===data[side])return;
      const source=data[side];photoSources[side]=source;
      if(!source){fighterPhotos[side]=null;return battle&&renderer?renderer.load(battle[side]):null;}
      return new Promise(resolve=>{
        const image=new Image();
        image.onload=()=>{if(photoSources[side]===source)fighterPhotos[side]=image;resolve();if(battleLoaded&&(battlePaused||battleFinished))paintBattle(performance.now());};
        image.onerror=()=>resolve();image.src=source;
      });
    }));
  },
  async start(data){
    if(battle?.key===data.key)return;
    const generation=++battleGeneration;
    cancelAnimationFrame(battleFrame);battleFrame=0;
    battle=data;battleTime=0;ArenaScene.paint(landscape,0);battleRound=-1;battleFinished=false;battleLoaded=false;battleHits.clear();
    fx2.clearRect(0,0,battleFx.width,battleFx.height);
    if(gl){gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);}
    if(!data.rounds.length){finishBattle();return;}
    try{
      await photoWork;
      if(!renderer)throw Error('WebGL unavailable');
      await Promise.all([...new Set(['me','them'].filter(side=>!fighterPhotos[side]).map(side=>data[side]))].map(id=>renderer.load(id)));
    }catch{
      renderer=null;canvas.hidden=true;
      const portraits=document.querySelector('.duel-posters');portraits.replaceChildren();portraits.hidden=false;
      for(const side of ['me','them']){const img=document.createElement('img');img.src='creature-'+data[side]+'.webp';img.style.visibility=fighterPhotos[side]?'hidden':'visible';img.alt='';portraits.append(img);}
    }
    if(generation!==battleGeneration)return;
    battleLoaded=true;paintBattle(performance.now());native('loaded');resumeBattle();
  },
  pause(value){battlePaused=value;if(value){cancelAnimationFrame(battleFrame);battleFrame=0;}else resumeBattle();},
  speed(value){battleSpeed=value===2?2:1;},
  skip(){finishBattle();},
};
new ResizeObserver(()=>{if(battleLoaded&&(battlePaused||battleFinished))paintBattle(performance.now());}).observe(canvas);
native('ready');
