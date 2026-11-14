'use strict';
// Camera and meshes define the proportions. The card only places the square render;
// it never stretches, warps or crops the object. Alpha is retained for the animated layer.
const AtelierArt=(()=>{
 let sequence=0;
 const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const tones=['#bac5c7','#97c69b','#8cbde7','#cea2ee','#e8c578'];
 function title(text){if(text.length<24)return [text];const words=text.split(' ');let n=1,d=Infinity;for(let i=1;i<words.length;i++){const a=words.slice(0,i).join(' ').length,b=words.slice(i).join(' ').length;if(Math.abs(a-b)<d){n=i;d=Math.abs(a-b);}}return [words.slice(0,n).join(' '),words.slice(n).join(' ')];}
 function face(c,{finish='',boostValue=c.roll,layer='full'}={}){
  const id='atelier'+(++sequence),t=c.tier,tone=tones[t],image=AtelierImages[c.id];
  if(!image)throw Error('Missing authored illustration: '+c.id);
  const defs=`<radialGradient id="${id}bg" cx="48%" cy="40%" r="64%"><stop stop-color="#323a3d"/><stop offset=".65" stop-color="#192127"/><stop offset="1" stop-color="#10161c"/></radialGradient><linearGradient id="${id}edge" x2=".8" y2="1"><stop stop-color="${tone}"/><stop offset=".48" stop-color="#505863"/><stop offset="1" stop-color="${tone}"/></linearGradient><filter id="${id}shadow" x="-15%" y="-15%" width="130%" height="135%"><feDropShadow dx="0" dy="6" stdDeviation="4" flood-color="#03080c" flood-opacity=".55"/></filter>`;
  const open=`<svg class="card-face atelier-face tier-${t}" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 438" width="600" height="876" role="img" aria-label="${esc(c.title+' · '+WeaponStudy.tiers[t].name+' · '+c.damageTypes.map(d=>RelicCuration.types[d].label).join(' + '))}"><defs>${defs}</defs>`;
  const art=`<g class="card-object" filter="url(#${id}shadow)"><image href="${image}" x="6" y="38" width="288" height="288" preserveAspectRatio="xMidYMid meet"/></g>`;
  if(layer==='object')return open+art+'</svg>';
  let border=`<rect x="5" y="5" width="290" height="428" rx="13" fill="none" stroke="url(#${id}edge)" stroke-width="${[1.1,1.5,2,2.8,3.4][t]}"/><rect x="10" y="10" width="280" height="418" rx="9" fill="none" stroke="${tone}" stroke-width=".55" opacity=".3"/>`;
  for(const x of [0,300])for(const y of [0,438])border+=`<g transform="translate(${x} ${y}) scale(${x?-1:1} ${y?-1:1})" fill="none" stroke="${tone}" stroke-linejoin="round"><path d="M9 43V21L21 9H43" stroke-width="${t>=3?1.5:.8}"/>${t>=2?'<path d="M15 31V20L20 15H31" stroke-width=".8"/>':''}${t===4?'<path d="M12 48L17 39L12 30M48 12L39 17L30 12" stroke-width="1"/>':''}</g>`;
  const ranks=Array.from({length:t+1},(_,i)=>`<path d="M${261-i*8} 42l2 2-2 2-2-2Z" fill="${tone}"/>`).join('');
  const labels=[...(finish&&finish!=='Normale'?[{name:finish,color:finish==='Bénédiction'?'#edcf8f':'#b6cce7'}]:[]),...(c.curse?[{name:'Malédiction',color:'#e29ab0'}]:[])];
  const badges=labels.map((b,i)=>{const x=labels.length===2?28+i*124:90;return `<rect x="${x}" y="329" width="120" height="18" rx="9" fill="#172027" stroke="${b.color}" stroke-width=".6"/><text x="${x+60}" y="341.5" fill="${b.color}" text-anchor="middle" font-size="9" font-family="system-ui,sans-serif">${esc(b.name)}</text>`;}).join('');
  const lines=title(c.title),font=Math.min(18,252/(Math.max(...lines.map(s=>s.length))*.53));
  return open+`<rect width="300" height="438" rx="17" fill="#10161c"/><rect x="9" y="9" width="282" height="420" rx="10" fill="url(#${id}bg)"/>`+(layer==='base'?'':art)+
   `<path d="M28 353H116M184 353H272" stroke="${tone}" stroke-opacity=".3" stroke-width=".6"/><path d="M145 353L150 350L155 353L150 356Z" fill="${tone}" opacity=".6"/>`+badges+border+
   `<g>${c.damageTypes.map((d,i)=>RelicCuration.icon(d,21+i*29,20,22)).join('')}</g><rect x="169" y="17" width="110" height="22" rx="4" fill="${tone}" fill-opacity=".16"/><text x="224" y="31.5" text-anchor="middle" font-family="system-ui,sans-serif" font-size="9" font-weight="700" letter-spacing=".5" fill="${tone}">${esc(WeaponStudy.tiers[t].name.toUpperCase())}</text>${ranks}`+
   lines.map((s,i)=>`<text x="150" y="${lines.length===1?384:375+i*21}" font-family="Georgia,serif" font-size="${font}" text-anchor="middle" fill="#f3eddf">${esc(s)}</text>`).join('')+
   `<text x="${c.curse?88:150}" y="419" text-anchor="middle" font-family="system-ui,sans-serif" font-size="12" font-weight="650" fill="#d2dfc5">+${WeaponStudy.percent(boostValue)} ${esc(c.stat)}</text>`+
   (c.curse?`<text x="224" y="419" text-anchor="middle" font-family="system-ui,sans-serif" font-size="9" fill="#e29ab0">−${WeaponStudy.percent(c.curse.value)} ${esc(c.curse.stat)}</text>`:'')+'</svg>';
 }
 return {face};
})();
