'use strict';
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/opt/helium/chrome',headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 try {
  const page=await browser.newPage({viewport:{width:390,height:620},deviceScaleFactor:2}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto((process.env.ARSENAL_PREVIEW_URL||'http://127.0.0.1:8871')+'/index.html');
  await page.evaluate(()=>document.body.style.background='#101318');
  for(const family of ['weapon','chest'])for(const golden of [false,true]) {
   await page.evaluate(({family,golden})=>arena.select(family,golden,true,0,golden?'or-solaire':''),{family,golden});
   await page.waitForFunction(()=>ready,null,{timeout:15000});
   await page.screenshot({path:path.join(__dirname,'../review/social-arena',family+(golden?'-gold':'')+'.png')});
  }
  const counts=await page.evaluate(()=>({
   stats:[...new Set(ArsenalCatalog.allCatalog.map(c=>c.stat))].sort(),
   curses:[...new Set(ArsenalCatalog.allCatalog.filter(c=>c.curse).map(c=>c.curse.stat))].sort(),
   types:[...new Set(ArsenalCatalog.catalog.flatMap(c=>c.damageTypes))].sort(),
   dual:ArsenalCatalog.catalog.filter(c=>c.damageTypes.length===2).length
  }));
  assert.deepEqual(counts.stats,['CON','DEX','FOR']);
  assert(counts.curses.every(s=>counts.stats.includes(s)));assert.equal(counts.types.length,4);assert(counts.dual>0);
  assert.deepEqual(errors,[]);console.log('Two wrapper families, normal and gold; FOR/DEX/CON on all cards/curses; four damage types and dual affinities preserved.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
