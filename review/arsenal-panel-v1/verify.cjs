const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/opt/helium/chrome',headless:true,args:['--no-sandbox','--disable-crash-reporter']});
 try{
  const ctx=await browser.newContext({viewport:{width:1500,height:1150},reducedMotion:'reduce',acceptDownloads:true});
  const p=await ctx.newPage(),errors=[];p.on('pageerror',e=>errors.push(e.message));
  await p.goto('file://'+path.join(__dirname,'index.html'));await p.waitForTimeout(500);
  assert.equal(await p.locator('.thumb').count(),10);
  const cards=await p.evaluate(()=>PANEL.cards);assert.equal(cards.filter(c=>c.tier>=3).length,7);assert.equal(new Set(cards.map(c=>c.tier)).size,5);assert.equal(new Set(cards.filter(c=>c.set?.id==='set-0-signature').map(c=>c.slot)).size,4);
  await p.screenshot({path:path.join(__dirname,'comparaison-desktop.png')});
  const oldKey='superhealth.arsenal.review.v2';await p.evaluate(key=>localStorage.setItem(key,'OLD_REVIEWS_SENTINEL'),oldKey);
  await p.locator('#score').selectOption('4');await p.locator('[data-decision="rework"]').click();
  const comment='Matière convaincante. <script>window.injected=true</script>';
  await p.locator('#comment').fill(comment);await p.reload();assert.equal(await p.locator('#score').inputValue(),'4');assert.equal(await p.locator('#comment').inputValue(),comment);assert.equal(await p.evaluate(()=>window.injected),undefined);assert.equal(await p.evaluate(key=>localStorage.getItem(key),oldKey),'OLD_REVIEWS_SENTINEL');
  const download=p.waitForEvent('download');await p.locator('#export').click();const d=await download,payload=JSON.parse(fs.readFileSync(await d.path(),'utf8'));assert.equal(payload.panel,'arsenal-panel-v1');assert.equal(payload.reviews[0].comment,comment);
  const storage=await p.evaluate(()=>localStorage.getItem('superhealth.arsenal.panel.v1.reviews'));
  for(const bad of [{...payload,panel:'relics-v3'},{...payload,reviews:[{id:cards[0].id,score:99}]},{...payload,reviews:[{id:'__proto__',comment:'bad'}]}]){
   await p.locator('#import-file').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(bad))});assert.match(await p.locator('#notice').innerText(),/Import refusé/);assert.equal(await p.evaluate(()=>localStorage.getItem('superhealth.arsenal.panel.v1.reviews')),storage);
  }
  await p.locator('#import-file').setInputFiles({name:'good.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(payload))});assert.match(await p.locator('#notice').innerText(),/1 avis importés/);
  await p.evaluate(()=>localStorage.removeItem('superhealth.arsenal.panel.v1.reviews'));await p.reload();
  fs.mkdirSync(path.join(__dirname,'comparaisons'),{recursive:true});
  for(let i=0;i<cards.length;i++){
   await p.locator('.thumb').nth(i).click();
   const types=await p.locator('#after [data-damage]').evaluateAll(es=>es.map(e=>e.dataset.damage));assert.deepEqual(types,cards[i].damageTypes);
   const clipped=await p.locator('#after svg').first().evaluate(svg=>Array.from(svg.querySelectorAll('text')).filter(e=>{const b=e.getBBox();return b.x<9||b.x+b.width>292||b.y+b.height>433;}).map(e=>e.textContent));assert.deepEqual(clipped,[],cards[i].id+' text clipping');
   const read=await p.locator('#after').innerText();const before=await p.locator('#before').innerText();const percent=(cards[i].previews.normal.boost/100).toFixed(2).replace('.',',');assert(read.includes(percent));assert(before.includes(percent));
   await p.locator('.comparison').screenshot({path:path.join(__dirname,'comparaisons',cards[i].id+'.png')});
  }
  await p.locator('.thumb').nth(2).click();await p.locator('#finish').selectOption('blessed');assert((await p.locator('#after').innerText()).includes('Bénédiction'));assert((await p.locator('#after').innerText()).includes('Malédiction'));assert((await p.locator('#before').innerText()).includes('Bénédiction'));assert.equal(await p.locator('#after [data-damage]').count(),2);await p.locator('.comparison').screenshot({path:path.join(__dirname,'effets-coexistants.png')});
  await p.locator('.thumb').nth(7).click();assert((await p.locator('#after').innerText()).includes('Bénédiction'));assert(!(await p.locator('#after').innerText()).includes('Malédiction'));assert.deepEqual(await p.locator('#after [data-damage]').evaluateAll(es=>es.map(e=>e.dataset.damage)),['ARCANIST']);
  await p.locator('#finish').selectOption('normal');
  await p.locator('[data-view="collection"]').click();assert.equal(await p.locator('#collection-grid button').count(),10);await p.locator('#collection-view').screenshot({path:path.join(__dirname,'panel-10-cartes.png')});
  await p.locator('[data-view="set"]').click();assert.equal(await p.locator('#set-grid button').count(),4);await p.locator('#set-view').screenshot({path:path.join(__dirname,'ensemble-griffon.png')});
  await p.locator('[data-view="phone"]').click();await p.locator('.phone').screenshot({path:path.join(__dirname,'comparaison-telephone.png')});
  await p.setViewportSize({width:390,height:844});
  for(const v of ['compare','collection','set','phone']){await p.locator('[data-view="'+v+'"]').click();assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'overflow '+v);}
  await p.screenshot({path:path.join(__dirname,'vue-mobile.png'),fullPage:false});
  await p.setViewportSize({width:320,height:740});for(const v of ['compare','collection','set','phone']){await p.locator('[data-view="'+v+'"]').click();assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'320px overflow '+v);}
  assert.deepEqual(errors,[]);
  const denied=await browser.newContext({viewport:{width:390,height:844}});await denied.addInitScript(()=>Object.defineProperty(window,'localStorage',{get(){throw Error('blocked');}}));const dp=await denied.newPage();await dp.goto('file://'+path.join(__dirname,'index.html'));assert.match(await dp.locator('#notice').innerText(),/indisponible/);await dp.locator('#comment').fill('Avis sans stockage');assert.match(await dp.locator('#save-state').innerText(),/exporte/i);const dd=dp.waitForEvent('download');await dp.locator('#export').click();assert(JSON.parse(fs.readFileSync(await(await dd).path(),'utf8')).reviews[0].comment==='Avis sans stockage');await denied.close();
  const report={passed:true,cards:10,highRarity:7,rarities:5,setPieces:4,checks:['10 before/after captures','catalogue damage icons and preview bonuses','text bounds','blessing + curse coexistence','blessed arcanist without curse','separate review storage','reload/export/import','invalid imports rejected atomically','comment injection remains text','storage unavailable: warning and export','390px and 320px responsive layouts','no JavaScript errors'],note:'Browser preview only; no Android build or deployment.'};
  fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
