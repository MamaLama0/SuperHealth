'use strict';

const canvas=document.querySelector('.pack-canvas');
let gl;
try { gl=canvas.getContext('webgl2',{alpha:true,antialias:true}); } catch {}
const stage=document.querySelector('.stage'), help=document.querySelector('.help');
const openButton=document.querySelector('#open'), closeButton=document.querySelector('#close');
const results=document.querySelector('.results'), fallback=document.querySelector('.fallback-card');
const reduceMotion=matchMedia('(prefers-reduced-motion: reduce)').matches;
const directionsBySlot=Object.fromEntries(packDirections.filter(style=>!style.edition).map(style=>[style.slot,style]));
const specials=Object.fromEntries(packDirections.filter(style=>style.edition).map(style=>[(style.key||style.slug)+':'+style.slot,style]));
const native=(action,value='')=>window.ArsenalHost?.action(action,String(value));
const mouthHold={at:.57,ms:450};
const haptic=kind=>native("haptic",kind);
const motions=[
  {ms:1800,release:.23,detach:.70,fall:.52,rise:.34,rebound:.08,lift:.73,twist:.65,roll:1.10,pitch:2.8,frequency:36,path:0,rag:.55},
  {ms:2150,release:.29,detach:.76,fall:.55,rise:.41,rebound:.14,lift:.99,twist:4.25,roll:1.72,pitch:2.4,frequency:31,path:1,rag:1.35},
  {ms:2260,release:.35,detach:.76,fall:.54,rise:.43,rebound:.19,lift:.56,twist:1.55,roll:2.68,pitch:4.3,frequency:51,path:2,rag:1},
];
let selected=false,reportedRender='';
const reportRendered=()=>{
  const key=slot+'|'+skin+'|'+edition;
  if(selected&&reportedRender!==key){reportedRender=key;native('rendered',key);}
};
let slot='weapon',style=directionsBySlot[slot],gold=false,skin=0,edition='',eligible=false,paused=false,ready=false,loading=false,openedAt=null;
let motion=motions[0],yaw=-.24,targetYaw=yaw,cut=0,renderedCut=0,tornAtOpening=0,gesture=null;
let latest=performance.now(),stageScale=1,lastTick=0,fallbackTimer,first=null,motionAt=null,tearDirection=0;
let rewardArt=[],cardIndex=0,faceUp=false,allRevealed=false,leavingCard=null,discoveryReady=false;
let cardDrag=null,cardOffset=0,incoming=null,revealAt=null,summaryTimer;
let english=false;
const say=(fr,en)=>english?en:fr;
const wrapperSlug=style=>style.slug+(english&&style.material==='prism'?'-en':'');
const initialHelp=()=>say('Glisse sur le tiers supérieur pour déchirer le sachet.','Swipe across the top third to open the pack.');
const ordinal=()=>cardIndex+1+'/'+rewardArt.length;
const canSwipeCard=()=>discoveryReady&&!allRevealed&&cardIndex<rewardArt.length;
const swipeDuration=reduceMotion?60:205;

const showNotch=()=>{targetYaw=Math.round(yaw/(2*Math.PI))*2*Math.PI};
const snapFace=angle=>{targetYaw=Math.round(angle/Math.PI)*Math.PI};

function cardNode(item,animate=true){
  const wrapper=document.createElement('div');
  wrapper.className='card-tilt';
  const card=document.createElement('div');
  card.className='full-card '+ArsenalCatalog.classNames(ArsenalCatalog.byId[item.id],item.effect);
  card.innerHTML=item.face+(animate?ArsenalCatalog.effectMarkup(ArsenalCatalog.byId[item.id],item.effect):'');
  wrapper.append(card);
  return wrapper;
}
function updateControls() {
  openButton.hidden=openedAt!==null;
  openButton.disabled=loading||!eligible;
  if(openedAt===null&&!gesture)help.textContent=loading?say('Déchirure en cours…','Opening…'):eligible?(gl?initialHelp():say('Touche Ouvrir pour découvrir tes cartes.','Tap Open to reveal your cards.')):'';
}
function closePack(){
  if(openedAt!==null||loading)return;
  cut=0;tearDirection=0;gesture=null;lastTick=0;closeButton.hidden=true;
  updateControls();
}
function prepareOpen(){
  if(openedAt!==null)return;
  loading=true;
  gesture=null;
  showNotch();
  cut=Math.max(cut,.43);
  closeButton.hidden=true;
  updateControls();
}
function requestOpening(){
  if(!eligible||loading||openedAt!==null)return;
  const progress=cut;
  prepareOpen();
  native('open',slot+'|'+(tearDirection||1)+'|'+progress.toFixed(3));
}
function select(next,nextGold,canOpen,nextSkin=skin,nextEdition=edition){
  if(openedAt!==null)return;
  if(!directionsBySlot[next])return;
  const wasSelected=selected;
  selected=true;
  const changed=next!==slot||nextGold!==gold||nextSkin!==skin||nextEdition!==edition;
  slot=next;gold=nextGold;eligible=canOpen;skin=nextSkin;edition=nextEdition;
  const base=directionsBySlot[slot],special=specials[edition+':'+slot],tintOnly=specials[edition+':weapon'];
  style=special||{...base,edition:tintOnly?.edition,accent:tintOnly?.accent??base.accent,
    wrapper:tintOnly?.base??base.tints[skin%3]};
  const tint=style.wrapper??style.base;
  stage.style.setProperty('--card-back','url("card-back-'+slot+'.svg")');
  stage.style.setProperty('--wrapper', 'url("'+wrapperSlug(style)+'-0.webp")');
  stage.style.setProperty('--tone',gold?'#e8bd68':style.accent);
  fallback.style.setProperty('--pack',tint);
  fallback.textContent='';
  fallback.ariaLabel=(slot==='weapon'?say('Armes','Weapons'):slot==='chest'?say('Armures','Armor'):style.label)+(gold?' · Gold':'');
  if(changed){
    ready=!gl;
    canvas.style.visibility='hidden';
    stage.classList.toggle('loading-model',!!gl);
    closePack();if(wasSelected)stage.animate([{opacity:.38,transform:'translateY(10px)'},{opacity:1,transform:'translateY(0)'}],reduceMotion?0:220)}
  if(!gl)reportRendered();
  updateControls();
}
function openingFailed(message){
  loading=false;stage.classList.remove('seam-primed');closePack();help.textContent=message;
}
function showOpening(data,direction=1,progress=0){
  if(!data.pieces.length||first===data.pieces[0].serial)return;
  loading=false;
  first=data.pieces[0].serial;
  select(data.slot,data.gold,true,data.skin??0,data.edition??'');
  tearDirection=direction===-1?-1:1;
  document.documentElement.classList.add('focus');
  rewardArt=data.pieces.map(piece=>{
    const item=ArsenalCatalog.byId[piece.id];
    return {...ArsenalCatalog.revealArt(item,{finish:piece.effect,boost:piece.boost}),id:piece.id,fresh:piece.fresh};
  });
  motion=motions[Math.abs(first)%motions.length];
  yaw=targetYaw=0;
  tornAtOpening=Math.max(.03,Math.min(1,progress));cut=1;
  openedAt=performance.now();
  motionAt=null;
  closeButton.hidden=openButton.hidden=true;
  help.textContent='';
  if(!gl)fallback.className='fallback-card opening';
  clearTimeout(fallbackTimer);
  fallbackTimer=setTimeout(()=>{
    if(!discoveryReady){if(gl)enableFallback();else beginDiscovery();}
  },!gl?mouthHold.ms+650:motion.ms+mouthHold.ms+1350);
}
function beginDiscovery(){
  if(discoveryReady)return;
  discoveryReady=true;
  clearTimeout(fallbackTimer);
  settleFace(performance.now());
}
function settleFace(now){
  faceUp=true;revealAt=now;
  help.textContent=ordinal();
  const item=rewardArt[cardIndex];
  if(!gl){fallback.className='fallback-card face';fallback.replaceChildren(cardNode(item));}
}
function advanceCard(direction){
  if(!canSwipeCard())return;
  haptic('card');
  const now=performance.now();
  leavingCard={index:cardIndex,at:now,direction,from:cardOffset,front:true};
  cardIndex++;
  cardOffset=0;
  if(cardIndex===rewardArt.length){
    if(!gl)fallback.firstElementChild?.animate([{transform:'none'},
      {transform:`translateX(${direction*120}%)`,opacity:0}],{duration:swipeDuration,fill:'forwards'});
    summaryTimer=setTimeout(showAll,swipeDuration);
    return;
  }
  incoming={at:now,direction};
  settleFace(now);
}
function showAll(){
  if(allRevealed||openedAt===null)return;
  allRevealed=discoveryReady=true;
  clearTimeout(fallbackTimer);
  clearTimeout(summaryTimer);
  fallback.hidden=true;
  help.textContent='';
  results.hidden=false;results.dataset.count=rewardArt.length;
  results.replaceChildren(...rewardArt.map((item,i)=>{
    const card=cardNode(item,false);
    card.setAttribute('aria-label',item.name+' · '+item.rarity);
    card.style.setProperty('--index',i);
    return card;
  }));
  native('summary');
}
function clearOpening(){
  clearTimeout(fallbackTimer);
  clearTimeout(summaryTimer);
  openedAt=null;motionAt=null;first=null;rewardArt=[];cardIndex=0;faceUp=false;allRevealed=false;discoveryReady=false;
  cardDrag=leavingCard=incoming=revealAt=null;cardOffset=0;renderedCut=cut=tearDirection=0;stage.classList.remove('seam-primed');
  document.documentElement.classList.remove('focus');
  results.hidden=true;results.replaceChildren();fallback.hidden=false;
  fallback.className='fallback-card';select(slot,gold,false);
}
openButton.onclick=requestOpening;closeButton.onclick=closePack;
let lastFallbackSwipe=0;
fallback.onclick=()=>{if(performance.now()-lastFallbackSwipe>350){if(!discoveryReady)requestOpening()}};

let touchStart;
fallback.addEventListener('touchstart',event=>{
  const t=event.changedTouches[0],box=fallback.getBoundingClientRect();
  touchStart={x:t.clientX,y:t.clientY,onStrip:t.clientY<box.top+box.height/3};
},{passive:true});
fallback.addEventListener('touchcancel',()=>{touchStart=null},{passive:true});
fallback.addEventListener('touchend',event=>{
  if(!touchStart)return;
  const t=event.changedTouches[0],dy=t.clientY-touchStart.y,dx=t.clientX-touchStart.x;
  const {onStrip}=touchStart;
  touchStart=null;
  if(canSwipeCard()&&Math.abs(dx)>35&&Math.abs(dx)>Math.abs(dy)){
    lastFallbackSwipe=performance.now();advanceCard(Math.sign(dx));
  }else if(openedAt===null&&onStrip&&Math.abs(dx)>35&&Math.abs(dx)>Math.abs(dy)){
    lastFallbackSwipe=performance.now();requestOpening();
  }else if(openedAt===null&&Math.abs(dy)>36&&Math.abs(dy)>Math.abs(dx)){
    lastFallbackSwipe=performance.now();native('step',dy<0?'1':'-1');
  }
},{passive:true});

if(window.ArsenalHost)document.documentElement.classList.add('native');
window.arena={language:value=>{const changed=english!==value;english=value;if(changed&&selected&&openedAt===null)select(slot,gold,eligible,skin,edition);openButton.textContent=say('Ouvrir','Open');closeButton.textContent=say('Refermer','Close');updateControls();},select,prepareOpen,showOpening,showAll,releaseWaiting:()=>{if(loading){loading=false;stage.classList.remove('seam-primed');closePack();}},clearIfOpened:()=>{if(openedAt!==null)clearOpening()},openingFailed,pause:value=>{paused=value},colors:(ink,muted)=>{
  document.documentElement.style.setProperty('--ink',ink);
  document.documentElement.style.setProperty('--muted',muted);
}};
function enableFallback(){
  gl=null;stage.classList.add('fallback');ready=true;
  stage.classList.remove('loading-model');
  reportRendered();
  if(openedAt!==null){
    if(discoveryReady&&!allRevealed){
      fallback.className='fallback-card back';fallback.textContent='';
      if(canSwipeCard()&&faceUp)settleFace(performance.now());
    }else if(!discoveryReady)beginDiscovery();
  }
  updateControls();
}
canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();enableFallback()});
// The host owns selection. Never paint a default weapon while restoring another slot.
if(window.ArsenalHost)native('ready');else select(slot,gold,false);
if(gl){
  try { run(); } catch { enableFallback(); }
}else enableFallback();
function run(){
  const fx=document.querySelector('.fx');
  const ctx=fx.getContext('2d');
  const sparks=Array.from({length:64},(_,i)=>({
    angle:i*2.39996, speed:.30+((i*13)%31)/40, life:.32+((i*19)%37)/85, size:1+(i%4)*.55
  }));
  const vertex=`#version 300 es
  in vec3 point, normal; in vec2 uv; out vec2 tex; out vec3 pos, surfaceNormal; out float across;
  uniform vec3 turn,shift,scene; uniform vec2 tilt,ratio;
  uniform float scale, peel, lip, shake, tension, frontier, packWidth, filmPart, flutter, tearKind, fray, clock, tearSide, cardScale;
  vec3 rotate(vec3 p,vec3 a){
    p.yz=mat2(cos(a.x),sin(a.x),-sin(a.x),cos(a.x))*p.yz;
    p.xz=mat2(cos(a.y),sin(a.y),-sin(a.y),cos(a.y))*p.xz;
    p.xy=mat2(cos(a.z),sin(a.z),-sin(a.z),cos(a.z))*p.xy;
    return p;
  }
  float faceX(float x){
    return clamp(x*tearSide/packWidth+.5,0.,1.);
  }
  vec3 deform(vec3 p){
    p.xy*=cardScale;
    float localX=clamp(p.x/packWidth+.5,0.,1.);
    float visibleX=faceX(p.x);
    float released=1.-smoothstep(frontier-.11,frontier+.018,visibleX);
    float loose=max(0.,frontier-visibleX);
    float curl=released*peel*min(1.,loose*2.5);
    float leftGrip=exp(-visibleX*2.5);
    p.y+=curl*(.11+.30*leftGrip)+curl*sin(visibleX*13.+shake*75.)*.035;
    p.z+=curl*(.11+.26*leftGrip);
    p.x-=curl*.061*tearSide;
    float mouth=lip*released*exp(-abs(p.y)*9.);
    p.z+=sign(p.z)*mouth*(.042+.140*sin(localX*3.14159));
    float edgeBand=exp(-abs(p.y)*55.);
    float coarse=tearKind<.5?sin(visibleX*31.)*.34:tearKind<1.5?sin(visibleX*49.)*.66+sin(visibleX*105.)*.35:sin(visibleX*27.)*.7+step(.56,visibleX)*.5;
    float fibres=sin(visibleX*136.)*.26+sin(visibleX*218.)*.12;
    p.y+=(coarse+fibres)*fray*released*edgeBand*.035;
    if(filmPart>.5){
      float tip=pow(abs(visibleX-.5)*2.,1.25);
      float folds=sin(visibleX*15.-clock*16.)*.064+sin(visibleX*27.+clock*22.)*.027;
      p.y+=flutter*tip*folds;
      p.z+=flutter*(tip*folds*1.7+sin(visibleX*19.+clock*12.)*.035);
      p.z+=sign(p.z)*flutter*edgeBand*(.013+tip*.019);
    }
    float seam=exp(-abs(p.y)*12.);
    float pinch=exp(-pow((visibleX-frontier)*7.5,2.));
    float ripple=sin(33.*localX-10.*p.y)*sin(16.*p.y+5.*localX);
    p.z+=tension*(.011*ripple+.044*seam*pinch*sin(28.*localX));
    p.x+=sin(p.y*6.+localX*3.)*shake;
    return p;
  }
  void main(){
    tex=uv;
    across=faceX(point.x);
    vec3 p=deform(point);
    vec3 n=normal;
    if(frontier>.002 && abs(n.z)>.035){
      vec3 right=normalize(cross(vec3(0.,1.,0.),n));
      vec3 up=cross(n,right);
      n=normalize(cross(deform(point+.004*right)-p,deform(point+.004*up)-p));
    }
    surfaceNormal=rotate(rotate(n,turn),vec3(tilt,0.0));
    pos=rotate(rotate(p,turn)+shift,vec3(tilt,0.0))+scene;
    float w=1.0-pos.z*.20;
    gl_Position=vec4(pos.xy*ratio*scale,-pos.z*.32,w);
  }`;
  const fragment=`#version 300 es
  precision highp float;
  in vec2 tex; in vec3 pos, surfaceNormal; in float across; out vec4 outputColor;
  uniform sampler2D art, objectArt;
  uniform vec3 color, originalWrap, wrapTint;
  uniform int mode;
  uniform float cut, flash, glint, filmPart, flutter, cardFloor, editionFx, finishFx, fxTime, curseFx, splitArt, rarityFx, wrapperFx;
  ${RelicShaders.glsl}
  float fxHash(vec2 p){return fract(sin(dot(p,vec2(41.73,87.22)))*43758.55);}
  float particles(vec2 uv,float time,float density,float speed,float seed){
    vec2 flow=uv*density+vec2(sin(uv.y*13.+time*1.3)*.43,-time*speed);
    vec2 cell=floor(flow),p=fract(flow)-vec2(.18+.65*fxHash(cell+seed),.20+.60*fxHash(cell+seed+4.));
    float show=step(.79,fxHash(cell+seed+8.));
    float d=length(p);
    float twinkle=.58+.42*sin(time*(1.5+fxHash(cell+2.))+fxHash(cell+1.)*6.283);
    return show*twinkle*((1.-smoothstep(.030,.115,d))*.96+(1.-smoothstep(.05,.25,d))*.21);
  }
  vec3 jewel(vec3 foil,vec2 center,vec3 mineral,vec3 reflected,float phase){
    vec2 q=(tex-center)*vec2(1.,1.49);
    float shape=abs(q.x)+abs(q.y)*.92;
    float bezel=1.-smoothstep(.049,.053,shape);
    float stone=1.-smoothstep(.037,.041,shape);
    float facets=.40+.39*step(0.,q.x*q.y)+.13*step(abs(q.y),abs(q.x)*.57);
    float sweep=exp(-pow((q.x+q.y*.62-sin(fxTime*1.5+phase)*.075+reflected.x*.075)*120.,2.));
    float flare=exp(-length(q-vec2(-.012,.013))*112.)*pow(.55+.45*sin(fxTime*3.+phase),8.);
    vec3 metal=mix(vec3(.37,.17,.025),vec3(1.,.82,.35),.41+.42*step(0.,q.y-q.x*.42));
    vec3 gem=mineral*facets+vec3(1.,.95,.73)*(sweep*.58+flare*.9);
    return mix(foil,mix(metal,gem,stone),bezel);
  }
  void main(){
    vec3 n=normalize(surfaceNormal);
    vec3 view=normalize(vec3(0.,.06,4.)-pos);
    vec3 reflected=reflect(-view,n);
    float light=.72+.29*max(dot(n,normalize(vec3(-.6,.6,1.))),0.);
    float streak=exp(-pow((tex.x+.38*tex.y-glint)*13.,2.));
    if((mode==1||mode==3)&&pos.y<cardFloor)discard;
    if(mode==3){
      vec2 uv0=tex;
      if(finishFx>4.5){
        float row=floor(tex.y*31.);
        float scan=step(.79,fxHash(vec2(row,floor(fxTime*2.15))))*step(.32,tex.y);
        float strength=.024+.026*fxHash(vec2(row,5.));
        uv0.x+=scan*strength*sin(fxTime*3.+tex.y*91.);
        uv0=clamp(uv0,vec2(.008),vec2(.992));
      }
      vec4 pixel=texture(art,uv0);
      if(pixel.a<.12)discard;
      vec3 printed=pixel.rgb;
      float objectMask=1.;
      if(splitArt>.5){
        vec2 center=vec2(.50,.57),p=tex-center;
        float movingAmount=step(1.5,splitArt);
        float angle=sin(fxTime*2.16)*.025*movingAmount;
        mat2 rot=mat2(cos(angle),sin(angle),-sin(angle),cos(angle));
        vec2 floatUV=clamp(rot*p+center+movingAmount*vec2(sin(fxTime*1.7)*.008,-sin(fxTime*2.2)*.012),vec2(.001),vec2(.999));
        vec4 moving=texture(objectArt,floatUV);
        // Match the printed footer fade (312–324 px), rather than cutting long
        // weapons at 302 px. Keep the rarity and effect labels readable.
        float objectWindow=smoothstep(.26,.287,tex.y)*(1.-smoothstep(.89,.905,tex.y));
        printed=mix(printed,moving.rgb,moving.a*objectWindow);
        objectMask=moving.a;
      }
      float artWindow=smoothstep(.23,.29,tex.y)*(1.-smoothstep(.88,.91,tex.y))*smoothstep(.025,.055,min(tex.x,1.-tex.x));
      artWindow*=objectMask;
      vec3 sheen=vec3(0.);
      float edge=1.-smoothstep(.024,.078,min(min(tex.x,tex.y),min(1.-tex.x,1.-tex.y)));
      if(finishFx>.5&&finishFx<1.5){
        printed=mix(printed,relicHolo(printed,tex,reflected.xy,fxTime,0.,.25),artWindow);
      }else if(finishFx>1.5&&finishFx<2.5){
        vec2 q=vec2(114.)*tex-vec2(0.,fxTime*4.);
        float dotLight=(1.-smoothstep(.07,.26,length(fract(q)-.5)))*step(.983,fract(sin(dot(floor(q),vec2(51.4,17.1)))*4315.7));
        float veil=exp(-pow((tex.x+tex.y*.36-.62-sin(fxTime*1.8)*.24)*4.4,2.));
        sheen=vec3(.40,.62,1.)*(dotLight*.75+particles(tex,fxTime,24.,3.6,4.)*.61+veil*.095)*artWindow;
      }else if(finishFx>2.5&&finishFx<3.5){
        printed=mix(printed,relicHolo(printed,tex,reflected.xy,fxTime,1.,1.),artWindow);
      }else if(finishFx>3.5&&finishFx<4.5){
        float ring=exp(-pow((length((tex-.5)*vec2(1.,1.14))-.27-.045*sin(fxTime*2.4))*21.,2.));
        float rays=pow(.5+.5*cos(atan(tex.y-.57,tex.x-.5)*10.+fxTime*.72),8.);
        float glitter=particles(tex,fxTime,19.,4.2,11.)+particles(tex,fxTime,33.,5.8,27.)*.72;
        sheen=vec3(1.,.67,.24)*(ring*.17+rays*.07+glitter*.52)*artWindow;
      }else if(finishFx>4.5){
        float mask=step(.83,fxHash(vec2(floor(tex.y*27.),floor(fxTime*1.6))))*artWindow;
        float split=(.004+.020*mask)*artWindow;
        printed.r=texture(art,clamp(uv0+vec2(split,0.),.006,.994)).r;
        printed.b=texture(art,clamp(uv0-vec2(split,0.),.006,.994)).b;
        float blocks=step(.86,fxHash(floor(tex*vec2(10.,39.))+floor(fxTime*1.6)))*.26*mask;
        float bars=step(.88,sin(tex.y*207.+fxTime*3.))*.10+blocks;
        vec3 rgb=.5+.5*cos(6.283*(tex.y*.7+tex.x*.4+fxTime*.29+vec3(0.,.33,.67)));
        sheen=rgb*(bars+.35*edge);
      }
      if(rarityFx>.5){
        // A restrained version of the existing holographic shader belongs to
        // epic/legendary cards themselves; special finishes stay additive.
        float legendary=step(1.5,rarityFx);
        vec3 prestige=mix(vec3(.65,.32,1.),vec3(1.,.72,.28),legendary);
        float border=smoothstep(.31,.35,abs(tex.x-.5))*(1.-smoothstep(.44,.47,abs(tex.x-.5)));
        float window=smoothstep(.27,.31,tex.y)*(1.-smoothstep(.88,.91,tex.y));
        float glimmers=particles(tex,fxTime*.48,18.,1.2,43.)*border;
        float sweep=exp(-pow((tex.x+tex.y*.25-fract(fxTime*.10)*1.6+.2)*16.,2.));
        float metal=step(.44,max(printed.r,max(printed.g,printed.b)));
        printed=mix(printed,relicHolo(printed,tex,reflected.xy,fxTime*.45,legendary,.14),artWindow*metal*(.10+legendary*.08));
        printed+=prestige*(glimmers*.48+sweep*metal*.10)*window;
      }
      if(curseFx>.5){
        float ashes=particles(tex,fxTime,21.,4.4,17.)+particles(tex,fxTime,37.,6.1,33.)*.70;
        float breath=.5+.5*sin(fxTime*2.4);
        sheen+=vec3(.94,.11,.41)*(edge*(.06+.05*breath)+ashes*.5*artWindow);
      }
      outputColor=vec4(printed+streak*.075*artWindow+sheen*artWindow,pixel.a); return;
    }
    if(mode==1){
      vec2 p=abs(tex-.5);
      float frame=smoothstep(.476,.491,max(p.x,p.y));
      float badge=1.-smoothstep(.012,.021,abs(max(p.x,p.y*.82+p.x*.39)-.21));
      float lines=pow(.5+.5*cos((tex.y+tex.x*.23)*68.),18.)*.04;
      vec3 paper=mix(vec3(.065,.085,.14),color,.14);
      outputColor=vec4(paper+color*(frame*.47+badge*.60+lines)+streak*.09,1.); return;
    }
    float grazing=pow(1.-max(dot(n,view),0.),2.3);
    vec3 printed=mode==2?mix(vec3(.15,.16,.20),color,.30):texture(art,tex).rgb;
    if(mode==2&&wrapperFx>.5)printed=wrapperFx>1.5?vec3(.78,.56,.20):vec3(.64,.71,.80);
    vec3 baseRatio=normalize(originalWrap+.045);
    float distanceToInk=distance(normalize(printed+.045),baseRatio);
    float mask=(mode==0?1.:0.)*(1.-smoothstep(.06,.27,distanceToInk));
    vec3 recolored=printed*clamp(wrapTint/max(originalWrap,.09),.62,1.46);
    printed=mix(printed,recolored,mask*.85);
    float edge=pow(abs(tex.x-.5)*2.,10.);
    float crimp=smoothstep(.86,.95,abs(tex.y-.5)*2.)*pow(.5+.5*sin(tex.x*201.),8.);
    float wave=sin(36.*tex.y+5.*sin(12.*tex.x))*sin(15.*tex.x+4.*tex.y);
    float stripSoft=exp(-pow((reflected.x+.26+reflected.y*.21+wave*.028)*8.2,2.));
    float stripHard=exp(-pow((reflected.x-.49+reflected.y*.34+wave*.048)*12.,2.));
    float varnish=(stripSoft*.32+stripHard*.30)*smoothstep(.02,.90,reflected.z);
    float tearEdge=exp(-pow((tex.y-.87)*265.,2.))*smoothstep(0.,.045,cut-across);
    float tooth=exp(-pow((across-cut)*55.,2.))*exp(-pow((tex.y-.87)*180.,2.))*step(.003,cut);
    vec3 foil=printed*(light+wave*(.025+.054*edge));
    foil+=vec3(.80,.89,1.)*(varnish*(.65+.48*grazing)+grazing*.15+crimp*.19);
    if(mode==0 && wrapperFx>.5){
      // Diffraction belongs to the printed foil, not a baked rainbow picture.
      // Surface normals and view direction move the spectrum as the pouch turns.
      // Dark lettering stays ink; gold retains its warm metallic substrate.
      float goldFoil=step(1.5,wrapperFx);
      float inkMask=smoothstep(.19,.60,dot(printed,vec3(.2126,.7152,.0722)));
      float facet=floor(tex.x*3.+tex.y*2.)*.17;
      float phase=tex.x*1.13+tex.y*.74+reflected.x*.82-reflected.y*.43+facet;
      vec3 spectrum=.5+.5*cos(6.283*(phase+vec3(0.,.33,.67)));
      float band=pow(.5+.5*sin(phase*6.283),3.);
      float border=smoothstep(.33,.49,abs(tex.x-.5));
      float amount=(.24+.23*band+.14*border+.15*grazing)*inkMask;
      vec3 silver=printed*(.70+.56*spectrum)+spectrum*.15;
      vec3 golden=printed*(.87+.19*spectrum)+vec3(1.,.69,.18)*band*.12;
      foil=mix(foil,mix(silver,golden,goldFoil),amount);
      float gloss=exp(-pow((tex.x*.72+tex.y*.32-reflected.x*.48-.46)*19.,2.));
      foil+=mix(vec3(.84,.95,1.),vec3(1.,.87,.48),goldFoil)*gloss*.24*inkMask;
    }
    if(mode==4)foil=vec3(.27,.30,.34)+varnish*.85+grazing*.29+tearEdge*.22;
    foil+=mix(color,vec3(1.),.82)*(tearEdge*.28+tooth*.43)+flash*.045;
    if(editionFx>.5&&editionFx<1.5){
      foil+=vec3(1.,.65,.12)*(varnish*.47+grazing*.16);
      if(mode==0&&filmPart<.5&&wrapperFx<.5){
        foil=jewel(foil,vec2(.20,.70),vec3(.94,.16,.29),reflected,.25);
        foil=jewel(foil,vec2(.80,.70),vec3(.25,.92,.58),reflected,2.1);
        foil=jewel(foil,vec2(.50,.21),vec3(.65,.36,1.),reflected,4.6);
      }
      if(mode==0&&filmPart<.5&&wrapperFx>1.5){
        // Same bezels as the vector print, including its non-WebGL fallback.
        // Tex.y is bottom-up: the lower illustration corners and footer.
        foil=jewel(foil,vec2(.160156,.306295),vec3(.94,.16,.29),reflected,.25);
        foil=jewel(foil,vec2(.839844,.306295),vec3(.16,.89,.56),reflected,2.1);
        foil=jewel(foil,vec2(.50,.112591),vec3(.65,.36,1.),reflected,4.6);
      }
    }else if(editionFx>1.5&&editionFx<2.5){
      vec3 rainbow=.5+.5*cos(6.283*(tex.x*.50+tex.y*.33+reflected.x*.35+vec3(0.,.333,.667)));
      foil+=rainbow*(varnish*.32+grazing*.14);
    }else if(editionFx>2.5){
      foil+=vec3(.49,.08,.25)*(stripHard*.20+tearEdge*.30);
    }
    if(filmPart>.5){
      float serration=.55+.45*sin(across*204.+sin(across*43.));
      float exposed=exp(-pow((tex.y-.87)*142.,2.))*smoothstep(0.,.075,cut-across);
      foil=mix(foil,vec3(.82,.87,.92),exposed*(.23+.35*serration));
      foil+=grazing*flutter*.15;
    }
    outputColor=vec4(foil,1.);

  }`;
  function shader(type,source){
    const id=gl.createShader(type);
    gl.shaderSource(id,source); gl.compileShader(id);
    if (!gl.getShaderParameter(id,gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(id));
    return id;
  }
  const program=gl.createProgram();
  gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));
  gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));
  gl.linkProgram(program);
  if(!gl.getProgramParameter(program,gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);
  const vars=Object.fromEntries(['objectArt','turn','shift','scene','tilt','ratio','scale','mode','cut','color','originalWrap','wrapTint','art','peel','lip','shake','flash','glint','tension','frontier','packWidth','filmPart','flutter','tearKind','fray','clock','cardFloor','cardScale','tearSide','editionFx','finishFx','fxTime','curseFx','splitArt','rarityFx','wrapperFx'].map(n=>[n,gl.getUniformLocation(program,n)]));
  const point=gl.getAttribLocation(program,'point');
  const uv=gl.getAttribLocation(program,'uv');
  const normal=gl.getAttribLocation(program,'normal');
  function upload(vertices){
    const vao=gl.createVertexArray(),buf=gl.createBuffer();
    gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);
    gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);
    gl.enableVertexAttribArray(point);gl.vertexAttribPointer(point,3,gl.FLOAT,false,32,0);
    gl.enableVertexAttribArray(uv);gl.vertexAttribPointer(uv,2,gl.FLOAT,false,32,12);
    gl.enableVertexAttribArray(normal);gl.vertexAttribPointer(normal,3,gl.FLOAT,false,32,20);
    return {vao,count:vertices.length/8};
  }
  const models=new Map(), loadingModels=new Set();
  function loadModel(key){
    if(models.has(key)||loadingModels.has(key))return;
    loadingModels.add(key);
    fetch(key+'.mesh').then(response=>{
      if(!response.ok)throw Error('Mesh');
      return response.arrayBuffer();
    }).then(buffer=>{
      if(!gl)return;
      const counts=new Uint32Array(buffer,0,8);
      let offset=32;
      const parts=Array.from(counts,count=>{
        const vertices=new Float32Array(buffer,offset,count*8);
        offset+=count*32;
        return upload(vertices);
      });
      models.set(key,[parts.slice(0,4),parts.slice(4)]);
    }).catch(enableFallback).finally(()=>loadingModels.delete(key));
  }
  const card=upload(new Float32Array([
    -.50,-.73,0,0,0,0,0,1, .50,-.73,0,1,0,0,0,1, .50,.73,0,1,1,0,0,1,
    -.50,-.73,0,0,0,0,0,1, .50,.73,0,1,1,0,0,1, -.50,.73,0,0,1,0,0,1,
  ]));
  const textures=new Map();
  function themeTextures(style){
    const slug=wrapperSlug(style);
    if(textures.has(slug))return textures.get(slug);
    const entry={ready:false,textures:[]};
    let count=0;
    entry.textures=[0,1].map(i=>{
      const id=gl.createTexture(),image=new Image();
      image.onload=()=>{
        if(!gl)return;
        gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,id);
        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
        gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);
        gl.generateMipmap(gl.TEXTURE_2D);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);
        entry.ready=++count===2;
      };
      image.onerror=enableFallback;
      image.src=slug+'-'+i+'.webp';
      return id;
    });
    textures.set(slug,entry);
    return entry;
  }
  gl.uniform1i(vars.objectArt,1);
  const backs=new Map();
  function backTexture(family){
    if(backs.has(family))return backs.get(family);
    const texture=gl.createTexture(),art=new Image();
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([40,29,66,255]));
    art.onload=()=>{
      if(!gl)return;
      gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,art);
      gl.generateMipmap(gl.TEXTURE_2D);
    };
    art.src='card-back-'+family+'.svg';
    backs.set(family,texture);
    return texture;
  }
  const rewardTextures=[],movingTextures=[],pendingObjects=[];
  let preparedArt=null;
  function prepareRewards(){
    if(preparedArt===rewardArt)return;
    preparedArt=rewardArt;
    for(const images of [rewardTextures,movingTextures,pendingObjects])images.splice(0).forEach(texture=>{if(texture)gl.deleteTexture(texture)});
    const batch=preparedArt;
    function load(svg,onReady){
      const img=new Image();
      img.onload=()=>{
        if(batch!==preparedArt)return;
        const texture=gl.createTexture();
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D,texture);
        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
        gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);
        gl.generateMipmap(gl.TEXTURE_2D);
        onReady(texture);
      };
      img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg);
    }
    batch.forEach((reward,i)=>{
      load(reward.face,texture=>{if(!movingTextures[i])rewardTextures[i]=texture;else gl.deleteTexture(texture)});
      if(reward.baseFace&&reward.objectFace){
        load(reward.objectFace,object=>{
          pendingObjects[i]=object;
          load(reward.baseFace,base=>{
            if(rewardTextures[i])gl.deleteTexture(rewardTextures[i]);
            rewardTextures[i]=base;movingTextures[i]=object;pendingObjects[i]=null;
          });
        });
      }
    });
  }
  gl.enable(gl.DEPTH_TEST);
  gl.depthFunc(gl.LEQUAL);
  gl.enable(gl.CULL_FACE);
  const smooth=(a,b,x)=>{const t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t)};
  let currentTextures;
  const draw=(mesh,mode,slot,texture)=>{
    gl.activeTexture(gl.TEXTURE0);
    gl.uniform1i(vars.mode,mode);
    gl.bindTexture(gl.TEXTURE_2D,texture||currentTextures[slot===1?1:0]);
    gl.bindVertexArray(mesh.vao); gl.drawArrays(gl.TRIANGLES,0,mesh.count);
  };
  const drawFilm=part=>part.forEach((mesh,i)=>draw(mesh,i===3?4:i===2?2:0,i));
  let popped=false, notchPop=false, ejected=false, warmed=false;
  function warmNext(keys){
    const key=keys.shift();
    if(!key||!gl)return;
    loadModel(key);themeTextures(directionsBySlot[key]);
    setTimeout(()=>warmNext(keys),160);
  }
  function tick(now) {
    try { render(now); }
    catch (error) {
      console.warn('Arsenal 3D indisponible',error);
      enableFallback();
    }
  }
  function render(now){
    if(!gl)return;
    if(!selected||document.hidden||(paused&&reportedRender===slot+'|'+skin+'|'+edition)||allRevealed){latest=now;requestAnimationFrame(tick);return;}
    const skinTextures=themeTextures(style);
    loadModel(slot);
    const parts=models.get(slot);
    const loaded=!!parts&&skinTextures.ready;
    stage.classList.toggle('loading-model',!loaded);
    canvas.style.visibility=loaded?'visible':'hidden';
    if(ready!==loaded){ready=loaded;updateControls();}
    if(!loaded){latest=now;requestAnimationFrame(tick);return;}
    if(openedAt===null&&!gesture&&now-latest<30){requestAnimationFrame(tick);return;}
    if(!warmed){warmed=true;setTimeout(()=>warmNext(Object.keys(directionsBySlot).filter(key=>key!==slot)),250);}
    prepareRewards();
    const cardBack=backTexture(slot);
    const dt=Math.min(50,now-latest); latest=now;
    // Give fine vector details and names enough pixels once the card is readable.
    // Keep the cheaper buffer during the wrapper animation.
    const dpr=Math.min(discoveryReady?2:1.4,devicePixelRatio);
    const w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);
    if(canvas.width!==w||canvas.height!==h){
      canvas.width=fx.width=w; canvas.height=fx.height=h; gl.viewport(0,0,w,h);
    }
    stageScale=Math.min(1.02,w/h*1.23,820*dpr/h);
    const k=1-Math.exp(-dt*.022);
    yaw+=(targetYaw-yaw)*(gesture?.tear&&eligible?Math.max(.42,k):k);
    renderedCut+=(cut-renderedCut)*k;
    if(!cardDrag)cardOffset*=Math.exp(-dt*.037);
    if(openedAt!==null&&motionAt===null)motionAt=now;
    const duration=reduceMotion?410:motion.ms;
    const elapsed=motionAt===null?0:Math.max(0,now-motionAt);
    const holdAge=Math.max(0,elapsed-duration*mouthHold.at);
    const t=(elapsed-Math.min(holdAge,mouthHold.ms))/duration;
    if(openedAt===null){popped=false;notchPop=false;ejected=false;}
    const phase=t/motion.release;
    const sweep=motion.path===2?.53*smooth(0,.38,phase)+.47*smooth(.64,1,phase):
      motion.path===1?.29*smooth(0,.29,phase)+.38*smooth(.23,.72,phase)+.33*smooth(.69,1,phase):smooth(0,1,phase);
    const frontier=openedAt===null?renderedCut:tornAtOpening+(1-tornAtOpening)*sweep;
    const detach=smooth(motion.release,.62-(.76-motion.detach)*.6,t);
    const rise=smooth(motion.rise*.85,mouthHold.at,t);
    const launch=smooth(mouthHold.at,.80,t);
    const settle=smooth(.80,1,t);
    const drop=smooth(.76,.99,t);
    const mouth=openedAt===null?.33:.36+1.72*smooth(.17,mouthHold.at,t);
    const pitch=.68*rise;
    const flare=reduceMotion?0:smooth(.60,.68,t)*(1-smooth(.69,.92,t));
    const recoil=reduceMotion||t<motion.release?0:Math.sin((t-motion.release)*motion.frequency)*Math.exp(-(t-motion.release)*13);
    if(motion.path===2&&phase>=.40&&t<1&&!notchPop){notchPop=true;haptic('tear-tick');}
    if(t>=motion.release&&!popped){popped=true;if(t<1)haptic('tear-end');}
    if(t>.72&&!ejected){ejected=true;if(t<1)haptic('eject');}
    const {accent,width}=style;
    currentTextures=skinTextures.textures;
    const rgb=hex=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255);
    gl.clearColor(0,0,0,0); gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    gl.uniform3f(vars.color,...rgb(accent));
    gl.uniform3f(vars.originalWrap,...rgb(style.base));
    gl.uniform3f(vars.wrapTint,...rgb(style.wrapper??style.base));
    gl.uniform1f(vars.editionFx,style.edition==='prisme'?2:style.edition==='maudit'?3:gold?1:0);
    gl.uniform1f(vars.wrapperFx,style.material==='prism'?(gold?2:1):0);
    gl.uniform1f(vars.packWidth,width);
    const reading=smooth(.93,1.13,t);
    const revealScale=Math.min(.94,w/h*1.63);
    const animationScale=Math.min(stageScale,.48)*(1-.13*rise*(1-drop));
    const sceneScale=openedAt===null?stageScale:animationScale+(revealScale-animationScale)*reading;
    gl.uniform2f(vars.ratio,h/w,1); gl.uniform1f(vars.scale,sceneScale);
    gl.uniform1f(vars.glint,.62);
    gl.uniform1f(vars.fxTime,reduceMotion?0:now*.001);
    gl.uniform1f(vars.filmPart,0);gl.uniform1f(vars.flutter,0);
    gl.uniform1f(vars.tearKind,motion.path);gl.uniform1f(vars.tearSide,tearDirection||1);gl.uniform1f(vars.fray,0);gl.uniform1f(vars.clock,0);
    // The wrapper and the stack share the mouth's origin until the last card
    // clears the rim. Only then does the wrapper fall away on its own.
    const mouthY=.74-.49*rise-.29*launch-recoil*.04;
    const wrapperY=mouthY-2.18*drop;
    if(t>motion.rise*.85){
      gl.uniform1f(vars.cardFloor,wrapperY-.23-.68*launch);
      gl.uniform1f(vars.cardScale,.84+.16*launch);
      gl.uniform1f(vars.frontier,0);
      gl.uniform1f(vars.lip,0);
      gl.uniform1f(vars.peel,0);
      gl.uniform1f(vars.shake,0);
      gl.uniform1f(vars.tension,0);
      gl.uniform2f(vars.tilt,pitch*(1-settle),0);
      gl.uniform3f(vars.scene,0,mouthY*(1-settle),0);
      const rebound=reduceMotion?0:Math.sin(Math.PI*launch)*motion.rebound*.57;
      const place=(index,x,y,z,tiltY,tiltZ,front)=>{
        gl.uniform3f(vars.shift,x,y,z);
        gl.uniform3f(vars.turn,0,tiltY,tiltZ);
        const texture=front?rewardTextures[index]:cardBack;
        const item=front?rewardArt[index]:null;
        gl.uniform1f(vars.finishFx,({normal:0,shiny:1,animated:2,chroma:3,blessed:4,bug:5})[item?.effect]||0);
        gl.uniform1f(vars.curseFx,item?.cursed?1:0);
        gl.uniform1f(vars.rarityFx,item?.tier>=3?item.tier-2:0);
        gl.uniform1f(vars.splitArt,front&&movingTextures[index]?(item?.effect==='animated'?2:1):0);
        gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,front&&movingTextures[index]?movingTextures[index]:cardBack);
        draw(card,texture?3:1,0,texture);
      };
      if(!allRevealed){
        for(let index=rewardArt.length-1;index>=cardIndex;index--){
          const depth=index-cardIndex;
          const front=depth===0&&(faceUp||t>=1);
          const tiltY=0;
          const entry=depth===0&&incoming&&!reduceMotion?-incoming.direction*.20*(1-smooth(0,175,now-incoming.at)):0;
          const follow=depth===0?cardOffset+entry:0;
          const fan=.3+.7*launch;
          const y=-.88+.93*rise+.77*launch-.65*settle+rebound-depth*.023*fan;
          place(index,-depth*.038*fan+follow,y,.025+.28*launch-.22*settle-depth*.021,tiltY,depth*.05*fan-follow*.10,front);
        }
        if(leavingCard){
          const exit=smooth(0,swipeDuration,now-leavingCard.at);
          if(exit<1){
            const x=leavingCard.from+(leavingCard.direction*1.75-leavingCard.from)*exit;
            place(leavingCard.index,x,.16+.075*exit,.11,0,-x*.16,leavingCard.front);
          }
          else leavingCard=null;
        }
      }
    }
    gl.uniform2f(vars.tilt,pitch,yaw);
    gl.uniform1f(vars.cardScale,1);
    gl.uniform1f(vars.flash,flare);
    gl.uniform1f(vars.cut,frontier);
    gl.uniform1f(vars.frontier,frontier);
    gl.uniform1f(vars.fray,motion.rag*frontier);
    gl.uniform1f(vars.peel,0); gl.uniform1f(vars.lip,mouth);
    gl.uniform1f(vars.tension,openedAt===null?frontier*.66:(1-drop)*frontier*(.32+.53*flare));
    gl.uniform1f(vars.shake,recoil*.018);
    const float=openedAt===null&&!reduceMotion&&!gesture&&cut<.001 ? Math.sin(now*.0021)*.025 : 0;
    gl.uniform3f(vars.scene,-.12*drop+recoil*.036,wrapperY+float,0);
    gl.uniform3f(vars.shift,0,0,0); gl.uniform3f(vars.turn,0,0,recoil*.048+drop*.09);
    drawFilm(parts[0]);
    gl.uniform2f(vars.tilt,0,yaw);
    gl.uniform1f(vars.lip,0);
    gl.uniform1f(vars.peel,openedAt===null?.79:1-.75*detach+.12*recoil);
    gl.uniform3f(vars.scene,0,float,0);
    const sail=reduceMotion?0:detach*(1-detach);
    const flutter=reduceMotion?0:Math.sin(Math.PI*detach)**.68;
    const travel=(tearDirection||1)*(motion.path===1?1.95:motion.path===2?2.32:2.52)*detach**1.68;
    const wobble=sail*Math.sin(detach*18.);
    gl.uniform1f(vars.filmPart,1);gl.uniform1f(vars.flutter,flutter);
    gl.uniform1f(vars.clock,detach*2.6);
    gl.uniform3f(vars.shift,travel,.74+motion.lift*.68*Math.sin(Math.PI*detach*.83)-.38*detach*detach,.38*Math.sin(Math.PI*detach)+.22*detach);
    gl.uniform3f(vars.turn,motion.pitch*detach+recoil*.18+wobble*.75,motion.twist*detach+wobble*2.,-motion.roll*detach+wobble*.48);
    drawFilm(parts[1]);
    ctx.clearRect(0,0,w,h);
    const seamY=h*(.5-wrapperY*sceneScale/2);
    const seamWidth=h*sceneScale*width/2;
    if(!reduceMotion && gold && !allRevealed && (openedAt===null || t<1)){
      ctx.globalCompositeOperation='lighter';
      const spread=h*sceneScale*.46;
      const glowY=h*.46;
      for(let i=0;i<22;i++){
        const phase=(now*.00031+i*.137)%1;
        const spark=sparks[i];
        const px=w/2+Math.cos(spark.angle+phase*2)*spread*(.71+.50*phase);
        const py=glowY+Math.sin(spark.angle)*spread*(.61+phase*.54)-phase*28*dpr;
        const light=Math.sin(phase*Math.PI);
        ctx.globalAlpha=light*.49;
        ctx.fillStyle=i%4===0?'#fff6d1':'#ffd770';
        ctx.fillRect(px,py,spark.size*dpr*.62,spark.size*dpr*.62);
      }
      ctx.globalAlpha=1;
      ctx.globalCompositeOperation='source-over';
    }
    if(!reduceMotion && (flare>0 || (t>.25&&t<1) || (openedAt===null&&gesture?.tear&&cut>.04))){
      ctx.globalCompositeOperation='lighter';
      const local=frontier;
      const x=w/2+Math.cos(yaw)*seamWidth*(local-.5)*(tearDirection||1)*(1-rise);
      const burst=ctx.createRadialGradient(x,seamY,0,x,seamY,h*(.055+.23*flare+.08*rise));
      burst.addColorStop(0,accent);
      burst.addColorStop(1,'transparent');
      ctx.globalAlpha=openedAt===null?.22:(1-drop)*rise*.17+flare*.37;
      ctx.fillStyle=burst; ctx.fillRect(0,0,w,h);
      const holding=holdAge>0&&holdAge<mouthHold.ms;
      const age=t>mouthHold.at?(t-.66)*1.9:(t-.23)*2.2;
      for(const [i,spark] of sparks.entries()){
        if(holding&&i<36){
          const drift=(holdAge*.00061+spark.speed*3.7)%1;
          const px=w/2+Math.cos(spark.angle+drift*1.9)*seamWidth*(.29+.51*drift);
          const py=seamY-h*(.01+.17*drift)+Math.sin(spark.angle)*h*.035;
          ctx.globalAlpha=Math.sin(drift*Math.PI)*(.28+spark.speed*.51);
          ctx.fillStyle=i%3===0?'#fff0bd':accent;
          ctx.fillRect(px,py,spark.size*dpr*.83,spark.size*dpr*.83);
        }else if(!holding&&age>0&&age<spark.life){
          const surge=t>mouthHold.at?1.44:.8;
          const vx=Math.cos(spark.angle)*h*spark.speed*surge,vy=Math.sin(spark.angle)*h*spark.speed*surge-h*.11;
          const px=w/2+vx*age,py=seamY+vy*age+h*age*age*.14;
          ctx.globalAlpha=.9*(1-age/spark.life);
          ctx.fillStyle=i%3===0?'#ffefd1':accent;
          ctx.fillRect(px,py,spark.size*dpr,spark.size*dpr);
          if(i%3===0){
            ctx.strokeStyle=accent;
            ctx.lineWidth=spark.size*dpr*.56;
            ctx.beginPath();ctx.moveTo(px,py);ctx.lineTo(px-vx*.055,py-vy*.055);ctx.stroke();
          }
        }
      }
      if(flare>0){
        ctx.globalAlpha=flare*.72;
        ctx.strokeStyle='#ffeac6';ctx.lineWidth=1.6*dpr;
        ctx.beginPath();ctx.ellipse(w/2,seamY,seamWidth*(.28+launch),h*(.015+launch*.095),-.11,0,Math.PI*2);ctx.stroke();
      }
      ctx.globalAlpha=1;
      ctx.globalCompositeOperation='source-over';
    }
    if(t>=1&&!discoveryReady) beginDiscovery();
    if(!reduceMotion&&faceUp&&!allRevealed&&revealAt!==null){
      const item=rewardArt[cardIndex],age=(now-revealAt)*.001;
      if(item&&age>=0&&age<1.08&&(item.effect!=='normal'||item.cursed||['Épique','Légendaire'].includes(item.rarity))){
        const effect=item.effect,blessed=effect==='blessed',bug=effect==='bug',cursed=item.cursed;
        const color=blessed?'#ffe69d':bug?'#6df7f4':cursed?'#f16aab':item.tone;
        const cx=w*.5,cy=h*.43,fade=1-age/1.08,spread=Math.min(w,h)*(.18+age*.33);
        const outer=ctx.createRadialGradient(cx,cy,spread*.57,cx,cy,spread*1.30);
        outer.addColorStop(0,'transparent');outer.addColorStop(.57,color+'5a');outer.addColorStop(1,'transparent');
        ctx.globalCompositeOperation='lighter';ctx.globalAlpha=fade*.72;ctx.fillStyle=outer;
        ctx.fillRect(cx-spread*1.4,cy-spread*1.4,spread*2.8,spread*2.8);
        ctx.strokeStyle=color;ctx.lineWidth=(1.1+fade*1.4)*dpr;ctx.globalAlpha=fade*(blessed?.58:.41);
        ctx.beginPath();ctx.ellipse(cx,cy,spread,spread*(blessed?.73:.94),blessed?-.31:.10,0,Math.PI*2);ctx.stroke();
        if(blessed){
          ctx.beginPath();ctx.ellipse(cx,cy,spread*.90,spread*.65,.38,0,Math.PI*2);ctx.stroke();
        }
        for(const spark of sparks){
          const distance=spread*(.82+spark.speed*age);
          const px=cx+Math.cos(spark.angle)*distance,py=cy+Math.sin(spark.angle)*distance*.91-age*h*.035;
          ctx.globalAlpha=fade*(.36+spark.life*.62);
          ctx.fillStyle=bug&&spark.size>2?'#f471c0':color;
          const s=spark.size*dpr*(1+.38*fade);
          if(bug)ctx.fillRect(px-s,py,s*2.8,s*.75);
          else{
            ctx.beginPath();ctx.moveTo(px,py-s*1.7);ctx.lineTo(px+s*.86,py);ctx.lineTo(px,py+s*1.7);ctx.lineTo(px-s*.86,py);ctx.closePath();ctx.fill();
          }
        }
        ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over';
      }
    }
    if(discoveryReady&&!allRevealed&&!faceUp){
      const tone=rewardArt[cardIndex]?.tone;
      if(tone){
        const x=w*.5,y=h*.43,radius=h*.23;
        const glow=ctx.createRadialGradient(x,y,radius*.4,x,y,radius);
        glow.addColorStop(0,'transparent');glow.addColorStop(.55,tone+'18');glow.addColorStop(1,'transparent');
        ctx.fillStyle=glow;ctx.fillRect(x-radius,y-radius,2*radius,2*radius);
      }
    }
    reportRendered();
    document.querySelector('.meter').style.transform='scaleX('+(openedAt===null?frontier:frontier*(1-detach))+')';
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
  canvas.onpointerdown=e=>{
    if(canSwipeCard()){
      const box=canvas.getBoundingClientRect(),x=(e.clientX-box.left)/box.width,y=(e.clientY-box.top)/box.height;
      if(!cardDrag&&x>.095&&x<.91&&y>.11&&y<.87){
        canvas.setPointerCapture(e.pointerId);
        cardDrag={id:e.pointerId,x:e.clientX,y:e.clientY,at:performance.now()};
      }
      return;
    }
    if(gesture||loading||openedAt!==null)return;
    canvas.setPointerCapture(e.pointerId);
    const rect=canvas.getBoundingClientRect(), centerX=rect.left+rect.width/2;
    const y=(rect.top+rect.height/2-e.clientY)/(rect.height*stageScale/2);
    const withinWidth=Math.abs(e.clientX-centerX)<rect.height*stageScale*(style.width/4+.07);
    const onStrip=(cut>.015||y>.20&&y<1.33)&&Math.abs(e.clientX-centerX)<rect.height*stageScale*(style.width/4+.16);
    gesture={id:e.pointerId,x:e.clientX,y:e.clientY,yaw:targetYaw,tear:onStrip,startCut:cut,body:withinWidth&&Math.abs(y)<1.12};
    if(onStrip&&eligible){
      showNotch();
      haptic('tear-start');
      cut=Math.max(cut,.045);
      lastTick=Math.floor(cut*4);
      closeButton.hidden=false;
      help.textContent=say('Tire sur la soudure.','Pull along the seam.');
    }
  };
  canvas.onpointermove=e=>{
    if(cardDrag?.id===e.pointerId){
      if(!reduceMotion){
        const readingScale=Math.min(.94,canvas.clientWidth/canvas.clientHeight*1.63);
        const unit=canvas.clientHeight*readingScale/2;
        cardOffset=Math.max(-1.24,Math.min(1.24,(e.clientX-cardDrag.x)/unit));
      }
      return;
    }
    if(!gesture||gesture.id!==e.pointerId||openedAt!==null)return;
    movePack(e.clientX,e.clientY);
  };
  function movePack(x,y){
    const dx=x-gesture.x,dy=y-gesture.y;
    if(!gesture.axis&&Math.hypot(dx,dy)>8)gesture.axis=Math.abs(dy)>Math.abs(dx)*(gesture.tear?1.48:1.15)?'vertical':'horizontal';
    if(gesture.axis!=='horizontal')return;
    if(gesture.tear&&eligible){
      showNotch();
      if(!tearDirection)tearDirection=Math.sign(dx);
      const distance=Math.max(51,Math.min(90,canvas.clientWidth*.21));
      cut=Math.max(0,Math.min(1,gesture.startCut+dx*tearDirection/distance));
      closeButton.hidden=cut<.015;
      const step=Math.floor(cut*4);
      if(step>lastTick){haptic('tear-tick');lastTick=step}
      help.textContent=cut>=.62?say('Relâche pour ouvrir.','Release to open.'):cut>.05?say('Continue le geste ou referme le sachet.','Keep pulling or close the pack.'):initialHelp();
    }else if(gesture.body&&!gesture.tear){
      targetYaw=gesture.yaw-dx*.011;
    }
  }
  canvas.onpointerup=e=>{
    if(cardDrag?.id===e.pointerId){
      const dx=e.clientX-cardDrag.x,dy=e.clientY-cardDrag.y;
      const velocity=Math.abs(dx)/Math.max(1,performance.now()-cardDrag.at);
      cardDrag=null;
      const travel=Math.abs(dx);
      if(travel>Math.abs(dy)*1.05 && (travel>=canvas.clientWidth*.19 || travel>=22&&velocity>.48))advanceCard(Math.sign(dx));
      else cardOffset=0;
      return;
    }
    if(gesture?.id!==e.pointerId)return;
    movePack(e.clientX,e.clientY);
    const dy=e.clientY-gesture.y,dx=e.clientX-gesture.x;
    if(gesture.axis==='vertical'&&Math.abs(dy)>36){
      closePack(); native('step',dy<0?'1':'-1');
    }else if(gesture.axis==='horizontal'&&gesture.tear&&cut>=.62)requestOpening();
    else if(gesture.axis==='horizontal'&&gesture.body&&!gesture.tear&&Math.abs(dx)>12)snapFace(targetYaw);
    else if(gesture.axis==='horizontal'&&!gesture.tear&&!gesture.body&&dx<-60)native('dashboard','');
    else if(gesture.tear&&!gesture.axis)return cancelGesture();
    gesture=null;
  };
  function cancelGesture(){
    cardDrag=null;
    cardOffset=0;
    if(gesture?.tear&&openedAt===null){
      cut=gesture.startCut;
      if(cut<.015)tearDirection=0;
      closeButton.hidden=cut<.004;
      help.textContent=cut>.05?say('Reprends la bande ou referme le paquet.','Keep pulling the strip or close the pack.'):initialHelp();
    }
    gesture=null;
  }
  canvas.onpointercancel=cancelGesture;
  canvas.onlostpointercapture=cancelGesture;
}
