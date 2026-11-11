'use strict';
// Visual QA against the real app assets, including native selection and no-WebGL.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const root=path.resolve(__dirname,'..');
const out=path.join(root,'review/tcg-dashboard-2026-11-11');
const assets='/app/src/superhealth/assets/arsenal/';
const variants=[['armes','weapon',false],['armures','chest',false],['armes-gold','weapon',true],['armures-gold','chest',true]];
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.webp':'image/webp','.png':'image/png'};
const server=http.createServer((req,res)=>{
  const file=path.resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
  if(!file.startsWith(root+path.sep)){res.writeHead(403).end();return;}
  fs.readFile(file,(error,data)=>{if(error){res.writeHead(404).end();return;}res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.end(data);});
});
(async()=>{
  fs.mkdirSync(out,{recursive:true});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/opt/helium/chrome',headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
  const report={variants:[],errors:[],base:JSON.parse(fs.readFileSync(path.join(out,'base.json'),'utf8')).head};
  try{
    for(const fallback of [false,true]){
      const page=await browser.newPage({viewport:{width:390,height:620},deviceScaleFactor:2,reducedMotion:'reduce'});
      page.on('pageerror',e=>report.errors.push(e.message));
      await page.addInitScript(({fallback})=>{
        window.hostMessages=[];
        window.ArsenalHost={action:(kind,value)=>hostMessages.push([kind,value])};
        if(fallback){const get=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(type,...args){return type==='webgl2'?null:get.call(this,type,...args);};}
      },{fallback});
      await page.goto(base+assets+'index.html');
      await page.waitForFunction(()=>window.arena);
      for(const [name,slot,gold] of variants){
        await page.evaluate(({slot,gold})=>{arena.select(slot,gold,true,0,gold?'or-solaire':'');document.body.style.background='#10151f';},{slot,gold});
        await page.waitForFunction(()=>ready&&reportedRender===slot+'|'+skin+'|'+edition);
        await page.evaluate(()=>{yaw=targetYaw=-.18;});
        await page.waitForTimeout(180);
        assert.equal(await page.evaluate(()=>!!gl),!fallback,'Do not silently fall back on shader compilation failure');
        assert.equal(await page.evaluate(()=>style.slug),'prism-'+slot+(gold?'-gold':''));
        await page.screenshot({path:path.join(out,name+(fallback?'-fallback':'')+'.png')});
        if(!fallback){
          // Both welded sides and the reverse must remain continuous at grazing angles.
          for(const [angle,suffix] of [[Math.PI/2,'edge-right'],[-Math.PI/2,'edge-left'],[Math.PI,'back']]){
            await page.evaluate(angle=>{yaw=targetYaw=angle;},angle);
            await page.waitForTimeout(120);
            await page.screenshot({path:path.join(out,name+'-'+suffix+'.png')});
          }
          await page.evaluate(()=>{yaw=targetYaw=-.18;});
          await page.waitForTimeout(100);
        }
        if(!fallback&&name==='armes'){
          // Compare identical camera geometry with/without the diffraction pass.
          const before=await page.locator('.pack-canvas').screenshot();
          await page.evaluate(()=>{style={...style,material:''};});
          await page.waitForTimeout(100);
          const plain=await page.locator('.pack-canvas').screenshot();
          assert.notDeepEqual(before,plain,'Foil pass changes the rendered pixels');
          await page.evaluate(()=>{style={...style,material:'prism'};});
          for(const [angle,suffix] of [[-.48,'left'],[.48,'right']]){
            await page.evaluate(angle=>{yaw=targetYaw=angle;},angle);
            await page.waitForTimeout(120);
            await page.screenshot({path:path.join(out,'prism-angle-'+suffix+'.png')});
          }
        }
        report.variants.push({name,renderer:fallback?'fallback':'webgl',ready:true});
      }
      for(const [name,slot,gold] of variants){
        const expected='prism-'+slot+(gold?'-gold':'')+'-en';
        await page.evaluate(({slot,gold})=>{arena.language(true);arena.select(slot,gold,true,0,gold?'or-solaire':'');yaw=targetYaw=-.18;},{slot,gold});
        await page.evaluate(expected=>Promise.all([0,1].map(i=>{const image=new Image();image.src=expected+'-'+i+'.webp';return image.decode();})),expected);
        await page.waitForFunction(()=>ready);
        await page.waitForTimeout(150);
        assert.equal(await page.evaluate(()=>wrapperSlug(style)),expected);
        assert.match(await page.locator('.help').innerText(),/Swipe|Tap Open/);
        await page.screenshot({path:path.join(out,name+'-en'+(fallback?'-fallback':'')+'.png')});
        if(!fallback){
          await page.evaluate(()=>{yaw=targetYaw=Math.PI;});
          await page.waitForTimeout(120);
          await page.screenshot({path:path.join(out,name+'-en-back.png')});
        }
      }
      await page.evaluate(()=>arena.language(false));
      await page.waitForTimeout(100);
      assert.equal(await page.evaluate(()=>wrapperSlug(style)),'prism-chest-gold');
      // Opening still reveals real catalogue cards and reaches its summary.
      await page.evaluate(()=>arena.showOpening({slot:'chest',gold:true,edition:'or-solaire',pieces:['normal','shiny','chroma','blessed'].map((effect,i)=>({serial:700+i,id:'exp-dragon-chest',effect,boost:173,fresh:true}))}));
      await page.waitForFunction(()=>discoveryReady,null,{timeout:12000});
      assert.equal(await page.evaluate(()=>!!gl),!fallback);
      for(let i=0;i<4;i++){assert.equal(await page.evaluate(()=>cardIndex),i);await page.evaluate(()=>advanceCard(1));await page.waitForTimeout(100);}
      await page.waitForFunction(()=>allRevealed);
      assert.equal(await page.locator('.results .full-card').count(),4);
      await page.screenshot({path:path.join(out,'opening-summary'+(fallback?'-fallback':'')+'.png')});
      await page.close();
    }
    const page=await browser.newPage({viewport:{width:1680,height:990},deviceScaleFactor:1});
    await page.goto(base+'/review/tcg-dashboard-2026-11-11/index.html');
    await page.locator('.pack-board img').last().waitFor();
    await page.evaluate(()=>Promise.all([...document.images].map(im=>im.decode().catch(()=>{}))));
    await page.locator('.pack-board').screenshot({path:path.join(out,'prisme-procedural.png')});
    assert.deepEqual(report.errors,[]);
    report.opening='4 cards revealed and summary reached in WebGL and fallback';
    fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify(report,null,2)+'\n');
    console.log(JSON.stringify(report,null,2));
  }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
