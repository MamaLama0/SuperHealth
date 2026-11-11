'use strict';
// Start a local HTTP server for app/src/superhealth/assets/arsenal, then run this script.
// NODE_PATH may point at an existing Playwright installation. No downloads are performed.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..'),out=path.join(root,'app/src/superhealth/assets/arsenal-thumbs');
(async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/opt/helium/chrome',headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader']});
  try{
    const page=await browser.newPage({viewport:{width:400,height:600}}),errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    page.on('console',m=>{if(m.type()==='error'&&/attribute|path|SVG|Expected|NaN/i.test(m.text()))errors.push(m.text());});
    await page.goto((process.env.ARSENAL_PREVIEW_URL||'http://127.0.0.1:8767')+'/card.html');
    const failures=[];
    const ids=await page.evaluate(()=>ArsenalCatalog.catalog.map(c=>c.id));
    fs.mkdirSync(out,{recursive:true});
    for(const id of ids){
      const result=await page.evaluate(async id=>{
        const card=ArsenalCatalog.byId[id],svg=ArsenalCatalog.cardFace(card,{finish:'normal',boost:0});
        showCard(id,'normal',0);
        const invalid=[];
        for(const text of document.querySelectorAll('svg text')){const box=text.getBBox();if(box.x<12||box.x+box.width>289)invalid.push(text.textContent);}
        const use=document.querySelector('.card-object>image,.card-object>use'),box=use.getBBox(),matrix=use.getCTM();
        const corners=[[box.x,box.y],[box.x+box.width,box.y],[box.x,box.y+box.height],[box.x+box.width,box.y+box.height]].map(([x,y])=>new DOMPoint(x,y).matrixTransform(matrix));
        // getCTM maps to CSS pixels; undo the SVG viewBox scale before comparing.
        const svgScale=document.querySelector('svg').getBoundingClientRect().width/300;
        if(card.edition!==4&&corners.some(p=>p.x/svgScale<12||p.x/svgScale>288||p.y/svgScale<47||p.y/svgScale>343))invalid.push('Illustration clipped');
        const image=new Image();image.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg);await image.decode();
        const canvas=document.createElement('canvas');canvas.width=336;canvas.height=360;
        canvas.getContext('2d').drawImage(image,24,94,552,592,0,0,336,360);
        return {invalid,data:canvas.toDataURL('image/webp',.9).split(',')[1]};
      },id);
      if(result.invalid.length)failures.push({id,invalid:result.invalid});
      fs.writeFileSync(path.join(out,id+'.webp'),Buffer.from(result.data,'base64'));
    }
    fs.writeFileSync(path.join(root,process.env.BOUNDS_FILE||'review/arsenal-550/bounds.json'),JSON.stringify(failures,null,2));
    assert.deepEqual(failures,[]);assert.deepEqual(errors,[]);console.log(ids.length+' SVGs rasterized, names/stats in bounds, collection thumbnails updated');
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
