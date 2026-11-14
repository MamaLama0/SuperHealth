'use strict';
// Pochettes des packs Athlète (Magma, Glacier, Tempête, Montagne) pour le sachet 3D de pack.js.
// Tout est dessiné ici en SVG : aucune image embarquée, le texte suit la langue et l'édition dorée.
const AthletePacks=(()=>{
  const W=512,H=826;
  const themes={
    magma:{mesh:'chest',width:1.24,name:['MAGMA','MAGMA'],stat:'PEC',core:'#fff6c0',mid:'#ff8a1f',outer:'#e53935',deep:'#2a0708',night:'#12040a'},
    glacier:{mesh:'guard',width:1.3,name:['GLACIER','GLACIER'],stat:'DOS',core:'#e8fbff',mid:'#7fdbff',outer:'#2f6fde',deep:'#0a1c46',night:'#050b22'},
    tempete:{mesh:'weapon',width:1.16,name:['TEMPÊTE','STORM'],stat:'BRA',core:'#ffffff',mid:'#ffe45c',outer:'#8c6bff',deep:'#1d1247',night:'#0b0720'},
    montagne:{mesh:'legs',width:1.19,name:['MONTAGNE','MOUNTAIN'],stat:'JAM',core:'#ffe7b0',mid:'#d89a4a',outer:'#6b3e1e',deep:'#2b170b',night:'#120a06'},
  };
  const golden={core:'#fff4c8',mid:'#ffc94a',outer:'#a86a08',deep:'#3a2400',night:'#1c1100'};
  const emblems={
    // Mêmes tracés que Vfx.emblem côté Android, sur un rayon de 1.
    magma:'<path stroke="none" d="M0-1C.9-.1 .7 1 0 1-.7 1-.8.1-.25-.3-.2.1.1.1 0-1z"/>',
    glacier:Array.from({length:6},(_,i)=>{
      const a=i*Math.PI/3,c=Math.cos(a),s=Math.sin(a),mx=c*.55,my=s*.55;
      const twig=d=>`M${mx.toFixed(3)} ${my.toFixed(3)}L${(mx+Math.cos(a+d)*.3).toFixed(3)} ${(my+Math.sin(a+d)*.3).toFixed(3)}`;
      return `<path d="M0 0L${c.toFixed(3)} ${s.toFixed(3)}${twig(.8)}${twig(-.8)}" fill="none" stroke-width=".13" stroke-linecap="round"/>`;
    }).join(''),
    tempete:'<path stroke="none" d="M.25-1L-.45.1H-.02L-.25 1 .5-.15H.05z"/>',
    montagne:'<path stroke="none" d="M-1 .7L-.3-.6 0-.1 .35-.8 1 .7z"/>',
  };
  const scenes={
    magma:c=>`
      <path d="M0 520L92 430l44 38 70-96 34 44 38-62 52 86 60-52 122 132V826H0z" fill="${c.night}" opacity=".92"/>
      <path d="M206 372l34 44 38-62 16 26-22 54-34-18-22 30z" fill="${c.mid}" opacity=".9"/>
      <path d="M252 392q-18 70 6 122t-14 118q-10 60 12 194" fill="none" stroke="${c.mid}" stroke-width="13" stroke-linecap="round" opacity=".85"/>
      <path d="M252 392q-18 70 6 122t-14 118q-10 60 12 194" fill="none" stroke="${c.core}" stroke-width="4" stroke-linecap="round"/>
      <path d="M120 600q40 30 14 92M392 574q-34 44-6 112" fill="none" stroke="${c.outer}" stroke-width="7" stroke-linecap="round" opacity=".8"/>
      ${dots(c.core,[[84,318,3],[148,244,2],[402,290,3],[440,372,2],[330,224,2],[196,168,3],[64,196,2],[462,188,2],[282,150,2]])}`,
    glacier:c=>`
      <path d="M-20 300q140-70 270-10t290-30v70q-150 40-290-10t-270 40z" fill="${c.mid}" opacity=".20"/>
      <path d="M-20 252q160-50 280 4t272-34v36q-140 44-272-2t-280 30z" fill="${c.core}" opacity=".13"/>
      <path d="M0 560L70 452l38 52 60-118 46 82 44-140 54 124 42-64 60 96 46-54 52 90V826H0z" fill="${c.night}" opacity=".9"/>
      <path d="M258 328l22 50-30 46-22-36zM168 386l20 36-34 58-18-30zM354 388l26 40-34 30z" fill="${c.core}" opacity=".85"/>
      <path d="M258 328l-30 96 22 0zM168 386l-32 64 18 30z" fill="${c.mid}" opacity=".6"/>
      ${dots(c.core,[[70,150,2],[130,210,3],[420,170,3],[468,250,2],[352,130,2],[232,176,2],[40,330,2],[474,360,2]])}`,
    tempete:c=>`
      <path d="M-30 196q60-70 150-38 54-64 132-30 70-52 138-4 82-22 152 44v96H-30z" fill="${c.night}" opacity=".85"/>
      <path d="M-30 268q84-56 168-22 70-44 142-8 82-40 156 6 46-10 106 30v70H-30z" fill="${c.deep}" opacity=".9"/>
      <path d="M318 292l-62 122h40l-58 140 132-176h-46l42-86z" fill="${c.mid}"/>
      <path d="M318 292l-62 122h40l-58 140" fill="none" stroke="${c.core}" stroke-width="5" stroke-linejoin="round"/>
      <path d="M132 310l-28 72h22l-34 96M420 318l-18 60h18l-30 84" fill="none" stroke="${c.mid}" stroke-width="5" stroke-linejoin="round" opacity=".7"/>
      <g stroke="${c.core}" stroke-width="2" stroke-linecap="round" opacity=".28">${Array.from({length:17},(_,i)=>`<path d="M${30+i*28} ${590+(i*37)%70}l-14 44"/>`).join('')}</g>`,
    montagne:c=>`
      <circle cx="366" cy="262" r="62" fill="${c.core}" opacity=".9"/>
      <circle cx="366" cy="262" r="104" fill="${c.core}" opacity=".13"/>
      <path d="M0 470l96-150 62 74 86-170 78 130 58-70 132 190V826H0z" fill="${c.outer}" opacity=".95"/>
      <path d="M244 224l34 56-32 30-26-22-24 34zM96 320l26 30-22 26-22-12zM380 284l30 36-26 20-20-30z" fill="${c.core}" opacity=".92"/>
      <path d="M0 596l110-104 84 62 96-112 92 82 130-74V826H0z" fill="${c.deep}" opacity=".96"/>
      <path d="M0 690l150-70 120 46 110-64 132 58V826H0z" fill="${c.night}"/>
      <g fill="${c.night}">${[[54,650],[98,628],[330,640],[372,618],[446,650]].map(([x,y])=>`<path d="M${x} ${y}l14 40h-28z"/><path d="M${x} ${y+16}l18 44h-36z"/>`).join('')}</g>`,
  };
  function dots(color,list){return `<g fill="${color}">${list.map(([x,y,r])=>`<circle cx="${x}" cy="${y}" r="${r}"/>`).join('')}</g>`;}
  function ribs(color){
    let d='';
    for(let x=6;x<W;x+=9)d+=`M${x} 6V54M${x} 772V820`;
    return `<path d="${d}" stroke="${color}" stroke-width="2.4" stroke-opacity=".34"/>`;
  }
  function frame(c,body){
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="system-ui,-apple-system,Roboto,sans-serif">
<defs>
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${c.night}"/><stop offset=".34" stop-color="${c.deep}"/><stop offset=".62" stop-color="${c.outer}"/><stop offset="1" stop-color="${c.deep}"/></linearGradient>
<radialGradient id="halo" cx=".5" cy=".46" r=".5"><stop stop-color="${c.mid}" stop-opacity=".75"/><stop offset=".55" stop-color="${c.outer}" stop-opacity=".28"/><stop offset="1" stop-color="${c.outer}" stop-opacity="0"/></radialGradient>
<linearGradient id="seal" x1="0" y1="0" x2="1" y2="0"><stop stop-color="${c.outer}"/><stop offset=".18" stop-color="${c.core}"/><stop offset=".38" stop-color="${c.mid}"/><stop offset=".6" stop-color="${c.core}"/><stop offset=".82" stop-color="${c.mid}"/><stop offset="1" stop-color="${c.outer}"/></linearGradient>
<linearGradient id="title" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#fff"/><stop offset=".55" stop-color="${c.core}"/><stop offset="1" stop-color="${c.mid}"/></linearGradient>
<linearGradient id="shade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#000" stop-opacity=".46"/><stop offset=".26" stop-color="#000" stop-opacity="0"/><stop offset=".6" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".82"/></linearGradient>
<pattern id="weave" width="26" height="26" patternUnits="userSpaceOnUse" patternTransform="rotate(28)"><path d="M0 0V26" stroke="#fff" stroke-opacity=".05" stroke-width="3"/></pattern>
</defs>
<rect width="${W}" height="${H}" fill="url(#sky)"/>
${body}
<rect width="${W}" height="${H}" fill="url(#weave)"/>
<rect width="${W}" height="60" fill="url(#seal)"/><rect y="766" width="${W}" height="60" fill="url(#seal)"/>
${ribs(c.night)}
<path d="M0 60H${W}M0 766H${W}" stroke="${c.night}" stroke-opacity=".5" stroke-width="4"/>
<path d="M0 107H${W}" stroke="#fff" stroke-opacity=".32" stroke-width="1.6" stroke-dasharray="9 8"/>
</svg>`;
  }
  function medallion(c,key,cx,cy,r){
    const rays=Array.from({length:24},(_,i)=>{
      const a=i*Math.PI/12,inner=r*1.22,outer=r*(i%2?1.36:1.52);
      return `M${(cx+Math.cos(a)*inner).toFixed(1)} ${(cy+Math.sin(a)*inner).toFixed(1)}L${(cx+Math.cos(a)*outer).toFixed(1)} ${(cy+Math.sin(a)*outer).toFixed(1)}`;
    }).join('');
    return `<circle cx="${cx}" cy="${cy}" r="${r*2.5}" fill="url(#halo)"/>
<path d="${rays}" stroke="${c.core}" stroke-width="3" stroke-linecap="round" opacity=".7"/>
<circle cx="${cx}" cy="${cy}" r="${r*1.12}" fill="none" stroke="${c.core}" stroke-opacity=".55" stroke-width="2" stroke-dasharray="3 9"/>
<circle cx="${cx}" cy="${cy}" r="${r}" fill="${c.night}" fill-opacity=".72" stroke="url(#seal)" stroke-width="7"/>
<circle cx="${cx}" cy="${cy}" r="${r*.84}" fill="none" stroke="${c.mid}" stroke-opacity=".6" stroke-width="1.6"/>
<g transform="translate(${cx} ${cy}) scale(${r*.52})" fill="url(#title)" stroke="${c.core}">${emblems[key]}</g>`;
  }
  function front(key,gold,english){
    const t=themes[key],c=gold?{...t,...golden}:t,name=t.name[english?1:0];
    const size=Math.min(74,Math.round(560/name.length));
    const line=gold?(english?'CHANCES ×3':'CHANCES ×3')+' · '+(english?t.stat+' BONUS':'BONUS '+t.stat):(english?'3–4 CARDS · '+t.stat+' BONUS':'3–4 CARTES · BONUS '+t.stat);
    const ribbon=gold?`<g transform="translate(256 598)"><rect x="-104" y="-19" width="208" height="38" rx="19" fill="url(#seal)"/><text y="7" text-anchor="middle" font-size="19" font-weight="900" letter-spacing="3" fill="#3a2400">${english?'GOLD PACK':'PACK DORÉ'}</text></g>`:'';
    return frame(c,`${scenes[key](c)}
<rect width="${W}" height="${H}" fill="url(#shade)"/>
<text x="256" y="150" text-anchor="middle" font-size="19" font-weight="900" letter-spacing="7" fill="#fff" fill-opacity=".92">SUPERHEALTH</text>
<text x="256" y="176" text-anchor="middle" font-size="12" font-weight="700" letter-spacing="5" fill="${c.core}" fill-opacity=".85">${english?'ATHLETE CARDS':'CARTES ATHLÈTE'}</text>
${medallion(c,key,256,gold?388:404,gold?82:88)}
${ribbon}
<text x="256" y="${gold?686:676}" text-anchor="middle" font-size="${size}" font-weight="900" letter-spacing="2" fill="url(#title)" stroke="${c.night}" stroke-opacity=".55" stroke-width="2" paint-order="stroke">${name}</text>
<text x="256" y="${gold?720:714}" text-anchor="middle" font-size="15" font-weight="800" letter-spacing="3.4" fill="#fff" fill-opacity=".88">${line}</text>`);
  }
  function back(key,gold,english){
    const t=themes[key],c=gold?{...t,...golden}:t;
    const rows=english?['Each pack holds 3 to 4 backgrounds','from its biome. Fuse duplicates','to strengthen a background.']:['Chaque pack contient 3 à 4 fonds','de son biome. Fusionne les doublons','pour renforcer un fond.'];
    return frame(c,`<rect width="${W}" height="${H}" fill="${c.night}" fill-opacity=".55"/>
<rect x="34" y="132" width="444" height="560" rx="26" fill="none" stroke="${c.mid}" stroke-opacity=".5" stroke-width="2"/>
${medallion(c,key,256,330,62)}
<text x="256" y="490" text-anchor="middle" font-size="30" font-weight="900" letter-spacing="5" fill="url(#title)">${t.name[english?1:0]}</text>
${rows.map((row,i)=>`<text x="256" y="${540+i*26}" text-anchor="middle" font-size="16" font-weight="600" fill="#fff" fill-opacity=".8">${row}</text>`).join('')}
<text x="256" y="664" text-anchor="middle" font-size="12" font-weight="800" letter-spacing="5" fill="${c.core}" fill-opacity=".8">SUPERHEALTH</text>`);
  }
  // Dos des cartes qui sortent du sachet : encre sombre, liseré et emblème du biome.
  function cardBack(key,gold){
    const t=themes[key],c=gold?{...t,...golden}:t;
    return `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="746" viewBox="0 0 512 746">
<defs><linearGradient id="ink" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${c.deep}"/><stop offset=".5" stop-color="${c.night}"/><stop offset="1" stop-color="${c.deep}"/></linearGradient>
<linearGradient id="rim" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${c.core}"/><stop offset=".4" stop-color="${c.mid}"/><stop offset=".7" stop-color="${c.outer}"/><stop offset="1" stop-color="${c.core}"/></linearGradient>
<radialGradient id="glow"><stop stop-color="${c.mid}" stop-opacity=".5"/><stop offset="1" stop-color="${c.mid}" stop-opacity="0"/></radialGradient>
<pattern id="grid" width="34" height="34" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0H34V34" fill="none" stroke="${c.mid}" stroke-opacity=".16" stroke-width="1.4"/></pattern></defs>
<rect width="512" height="746" rx="34" fill="url(#ink)"/><rect width="512" height="746" rx="34" fill="url(#grid)"/>
<rect x="15" y="15" width="482" height="716" rx="24" fill="none" stroke="url(#rim)" stroke-width="7"/>
<rect x="34" y="34" width="444" height="678" rx="14" fill="none" stroke="${c.mid}" stroke-opacity=".45" stroke-width="1.6"/>
<circle cx="256" cy="373" r="230" fill="url(#glow)"/>
<circle cx="256" cy="373" r="112" fill="${c.night}" stroke="url(#rim)" stroke-width="8"/>
<circle cx="256" cy="373" r="134" fill="none" stroke="${c.core}" stroke-opacity=".5" stroke-width="2" stroke-dasharray="4 11"/>
<g transform="translate(256 373) scale(62)" fill="${c.core}" stroke="${c.core}">${emblems[key]}</g>
</svg>`;
  }
  const url=svg=>'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg);
  const directions=Object.entries(themes).map(([key,t])=>({
    slot:key,mesh:t.mesh,width:t.width,slug:'athlete-'+key,label:t.name[0],base:t.outer,accent:t.mid,tints:[t.outer,t.outer,t.outer],
    art:(gold,english)=>[url(front(key,gold,english)),url(back(key,gold,english))],
    back:gold=>url(cardBack(key,gold)),
  }));
  return {directions,front,back,cardBack,themes};
})();
const packDirections=AthletePacks.directions;
