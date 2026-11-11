'use strict';
// Check bootstrap selection and the projected ground plane with a small deterministic mesh.
const {readFileSync}=require('node:fs');
const {runInNewContext}=require('node:vm');
const assert=require('node:assert/strict');
const calls=[],uniforms={};
const buffer=new ArrayBuffer(108+3*48);
const vertices=new Float32Array(buffer,108);
[[-.2,-.1,0],[.2,.1,0],[0,0,2.5]].forEach((p,i)=>vertices.set([...p,0,0,1,1,1,1,0,0,0],i*12));
const gl=new Proxy({}, {get:(_,name)=> {
  if(name==='getShaderParameter'||name==='getProgramParameter')return ()=>true;
  if(name==='getUniformLocation')return (_,key)=>key;
  if(name==='uniform1f')return (key,value)=>{uniforms[key]=value};
  if(name==='drawArrays')return ()=>calls.push('draw');
  if(name==='getAttribLocation')return ()=>0;
  return ()=>({});
}});
const canvas={clientWidth:300,clientHeight:275,style:{},addEventListener(){},getContext(){return gl}};
const stage={style:{setProperty(){}},classList:{toggle(){}}};
const poster={hidden:true};
const native=[];
const context={document:{documentElement:{classList:{contains:()=>false}},querySelector:s=>s==='canvas'||s==='.fighter-canvas'?canvas:s==='img'?poster:stage},
  matchMedia:()=>({matches:false}),devicePixelRatio:1,Float32Array,URLSearchParams,
  performance:{now:()=>100,mark:()=>{}},requestAnimationFrame:()=>1,cancelAnimationFrame(){},setTimeout,
  initialCreature:'skeletonminion',location:{search:'?model=skeletonminion&ground=true'},
  fetch:async path=>{calls.push(path);return {ok:true,arrayBuffer:async()=>buffer}},
  Image:class {set src(value){calls.push(value);queueMicrotask(()=>this.onload())}},
  ArsenalHost:{action:(...args)=>native.push(args)}};
context.window=context;
runInNewContext(readFileSync('app/src/superhealth/assets/arsenal/creature.js','utf8'),context);
setImmediate(async()=>{
  assert.equal(calls.filter(x=>x.endsWith?.('.mesh')).join(','),'creature-skeletonminion.mesh');
  assert(native.some(([kind])=>kind==='ready'));
  assert(native.some(([kind,value])=>kind==='rendered'&&value==='skeletonminion'));
  const floor=Math.min(...[[-.2,-.1,0],[.2,.1,0]].map(([x,y,z])=>(z-uniforms.vertical)*Math.cos(.22)+(Math.sin(uniforms.yaw)*x+Math.cos(uniforms.yaw)*y)*Math.sin(.22)));
  assert(Math.abs(floor*uniforms.unit+uniforms.ground+.72)<1e-6,'feet sit on the floor plane');
  context.beast.setDungeon(false);
  await context.beast.snapshot('skeletonminion').catch(()=>{});
  assert.equal(uniforms.ground,0,'portrait framing does not inherit dungeon grounding');
  assert.equal(await context.beast.select('invalid'),false);
  console.log('Creature bootstrap, selected assets, grounded feet and portrait framing: OK');
});
