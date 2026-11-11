'use strict';
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({executablePath:'/opt/helium/chrome',args:['--no-sandbox','--enable-unsafe-swiftshader']});
 const output=process.env.PRESTIGE_DIR||'review/arsenal-v6/opening';fs.mkdirSync(output,{recursive:true});
 try{
  for(const fallback of [false,true]){
   const page=await browser.newPage({viewport:{width:390,height:700},deviceScaleFactor:2}),errors=[];
   page.on('pageerror',e=>errors.push(e.message));
   page.on('console',m=>{if(m.type()==='error'&&/shader|WebGL|GLSL|SVG|Expected/i.test(m.text()))errors.push(m.text());});
   if(fallback)await page.addInitScript(()=>{const get=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(type,...args){return type==='webgl2'?null:get.call(this,type,...args)};});
   await page.goto((process.env.ARSENAL_PREVIEW_URL||'http://127.0.0.1:8767')+'/index.html');
   await page.waitForFunction(()=>ready,null,{timeout:12000});
   await page.evaluate(()=>arena.select('legs',true,true));
   await page.waitForFunction(()=>ready,null,{timeout:12000});
   const fixtures=process.env.PRESTIGE_IDS?process.env.PRESTIGE_IDS.split(','):['1-legs-0','relic-warrior-legs-v2','relic-mage-legs-v2','relic-warrior-guard-v2'];
   await page.evaluate(ids=>{arena.showOpening({slot:'legs',gold:true,pieces:ids.map((id,i)=>({serial:600+i,id,effect:'normal',boost:ArsenalCatalog.byId[id].roll,fresh:false}))});},fixtures);
   await page.waitForFunction(()=>discoveryReady,null,{timeout:12000});
   assert.equal(await page.evaluate(()=>!!gl),!fallback);
   for(let i=0;i<fixtures.length;i++){
    await page.waitForTimeout(400);
    if(!fallback){assert.equal(await page.evaluate(()=>gl.getError()),0);}
    await page.screenshot({path:`${output}/${fallback?'fallback':'webgl'}-${fixtures[i]}.png`});
    await page.evaluate(()=>advanceCard(1));
   }
   await page.waitForFunction(()=>allRevealed);
   assert.equal(await page.locator('.results .rarity-aura').count(),3);
   assert.equal(await page.locator('.results .rarity-sparks').count(),3);
   assert.equal(await page.locator('.results .full-card').count(),4);
   await page.emulateMedia({reducedMotion:'reduce'});
   assert(await page.locator('.results .rarity-halo').evaluateAll(nodes=>nodes.every(n=>getComputedStyle(n).animationName==='none')));
   assert.deepEqual(errors,[]);
   // Reload with the accessibility preference so the shader clock is frozen too.
   await page.reload();assert.equal(await page.evaluate(()=>reduceMotion),true);
   await page.close();
  }
  console.log('Normal common/epic/legendary cards, intrinsic effects in WebGL/fallback, mobile and reduced motion: OK');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
