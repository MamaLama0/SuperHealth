'use strict';
const {readFileSync}=require('node:fs');
const {runInNewContext}=require('node:vm');
const assert=require('node:assert/strict');
let now=0,next=1;
const frames=new Map(),events=[],draws=[];
const paintedPhotos=[];const fx=new Proxy({}, {get:(_,key)=>key==='drawImage'?(...args)=>paintedPhotos.push(args):()=>{}});
const context={ArenaScene:{paint(){}},ResizeObserver:class {observe(){}},document:{querySelector:()=>({clientWidth:420,clientHeight:380,getContext:()=>fx})},
  gl:{clearColor(){},clear(){}},renderer:{load:async()=>{},draw:(...args)=>draws.push(args)},canvas:{},devicePixelRatio:1,prefersStill:false,
  Image:class {naturalWidth=200;naturalHeight=500;set src(value){this.source=value;queueMicrotask(()=>this.onload())}},
  performance:{now:()=>now},native:(...args)=>events.push(args),
  requestAnimationFrame:fn=>{const id=next++;frames.set(id,fn);return id},cancelAnimationFrame:id=>frames.delete(id)};
context.window=context;
runInNewContext(readFileSync('app/src/superhealth/assets/arsenal/duel-effects.js','utf8'),context);
runInNewContext(readFileSync('app/src/superhealth/assets/arsenal/duel.js','utf8'),context);
function tick(n,step=16){for(let i=0;i<n;i++){now+=step;const batch=[...frames.values()];frames.clear();batch.forEach(fn=>fn(now));}}
(async()=>{
  const data={key:'test',me:'knight',them:'mage',rounds:[{stat:'PEC',mine:2000,theirs:1000,myHealth:9000,theirHealth:8000},{stat:'DOS',mine:1800,theirs:1200,myHealth:7800,theirHealth:6200}]};
  await context.duel.start(data);
  assert(events.some(e=>e[0]==='loaded'));
  tick(90);context.duel.pause(true);
  const paused=events.length;tick(200);assert.equal(events.length,paused);
  context.duel.pause(false);context.duel.speed(2);tick(300);
  const hits=events.filter(e=>e[0]==='hit').map(e=>JSON.parse(e[1]));
  assert.deepEqual(hits.map(h=>[h.round,h.target,h.damage,h.health]),[[0,'them',2000,8000],[0,'me',1000,9000],[1,'me',1200,7800],[1,'them',1800,6200]]);
  assert.equal(events.filter(e=>e[0]==='finish').length,1);
  assert.equal(frames.size,0);
  assert(draws.some(x=>x[0]==='knight')&&draws.some(x=>x[0]==='mage'));
  await context.duel.start({...data,key:'replay'});tick(30);context.duel.skip();tick(100);
  assert.equal(events.filter(e=>e[0]==='finish').length,2);
  assert.equal(frames.size,0);
  context.duel.photos({me:'data:image/png;base64,fixture-a',them:'data:image/png;base64,fixture-b',meCutout:true,themCutout:true});
  const modelDraws=draws.length;
  await context.duel.start({...data,key:'two-cutouts'});tick(90);
  assert(paintedPhotos.length>0,'cutouts are painted and animated');
  assert.equal(draws.length,modelDraws,'photos replace both 3D models');
  context.duel.skip();
  context.duel.photos({me:'data:image/png;base64,fixture-a',them:null,meCutout:true});
  const mixed=draws.length;
  await context.duel.start({...data,key:'mixed'});tick(90);
  assert(draws.slice(mixed).every(x=>x[0]==='mage'),'mixed mode renders only the opponent as a 3D model');
  context.duel.skip();
  context.duel.speed(1);
  const before=events.length;
  await context.duel.start({...data,key:'six-rounds',rounds:Array.from({length:6},(_,i)=>({...data.rounds[i%2]}))});
  tick(540); // 8.64 s: a full bout, including its finale, must be over.
  const full=events.slice(before);
  assert.equal(full.filter(e=>e[0]==='hit').length,12,'all attacks survive the faster pacing');
  assert.equal(full.filter(e=>e[0]==='finish').length,1,'six rounds finish in under nine seconds');
  assert.equal(frames.size,0);
  const slowStart=events.length;
  await context.duel.start({...data,key:'slow-frames',rounds:Array.from({length:6},(_,i)=>({...data.rounds[i%2]}))});
  tick(90,100);
  const slow=events.slice(slowStart);
  assert.equal(slow.filter(e=>e[0]==='hit').length,12,'all attacks survive slow rendering');
  assert.equal(slow.filter(e=>e[0]==='finish').length,1,'slow rendering must not stretch the fight');
  assert.equal(frames.size,0);
  console.log('Photos, mixed mode, duel attacks, HP events, pause/resume, speed, finish and replay/skip: OK');
})();
