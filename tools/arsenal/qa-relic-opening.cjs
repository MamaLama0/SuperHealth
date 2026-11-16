'use strict';
const {chromium}=require('playwright'),assert=require('node:assert/strict');
(async()=>{const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/opt/helium/chrome',headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
try{
 for(const fallback of [false,true]){
  const page=await browser.newPage({viewport:{width:390,height:620},deviceScaleFactor:2}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  if(fallback)await page.addInitScript(()=>{const get=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(type,...args){return type==='webgl2'?null:get.call(this,type,...args)};});
  await page.goto((process.env.ARSENAL_PREVIEW_URL||'http://127.0.0.1:8767')+'/index.html');
  await page.waitForFunction(()=>ready,null,{timeout:12000});
  await page.evaluate(()=>arena.select('weapon',true,true));
  await page.waitForFunction(()=>ready,null,{timeout:12000});
  await page.evaluate(()=>{document.body.style.background='#141b25';arena.showOpening({slot:'weapon',gold:true,pieces:['normal','shiny','animated','chroma','blessed','bug'].map((effect,i)=>({serial:100+i,id:['exp-chantre-weapon','exp-phoenix-guard','exp-astral-weapon','exp-dragon-chest','exp-oraison-weapon','exp-pacte-guard'][i],effect,boost:173,fresh:true}))});});
  await page.waitForFunction(()=>discoveryReady,null,{timeout:12000});
  assert.equal(await page.evaluate(()=>!!gl),!fallback,'The WebGL renderer must not silently fall back');
  for(let i=0;i<6;i++){
    assert.equal(await page.evaluate(()=>cardIndex),i);
    await page.screenshot({path:'review/arsenal-rework/opening-'+(fallback?'fallback-':'')+i+'.png'});
    await page.evaluate(()=>advanceCard(1));
    await page.waitForTimeout(250);
  }
  await page.waitForFunction(()=>allRevealed);
  assert.equal(await page.locator('.results .full-card').count(),6);
  assert.deepEqual(errors,[]);await page.close();
 }
 console.log('Opening + six finishes + animated layers + summary, WebGL and fallback: OK');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exit(1)});
