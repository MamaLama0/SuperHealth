'use strict';
/** Vector originals, rendered identically in the opening, collection and detail view.
 * No remote fonts/textures. The geometry remains crisp at any WebView density.
 */
const RelicArt = (() => {
  const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const path = (d, fill='none', stroke='none', width=1) => `<path d="${d}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" stroke-linejoin="round" stroke-linecap="round"/>`;
  const circle = (x,y,r,fill,stroke='none',width=1) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${fill}" stroke="${stroke}" stroke-width="${width}"/>`;
  const palettes = {
    WARRIOR: ['#a5b3c4','#f8e4bb','#303b50','#171e29','#b59a6a','LES SERMENTS DE FER'],
    RANGER: ['#7eaa8c','#e7edc2','#254d48','#0e2326','#acbd83','LES SENTIERS OUBLIÉS'],
    MAGE: ['#83a9cf','#e0f4ff','#343f79','#11192d','#acbef0','LES ARCHIVES CÉLESTES'],
    PRIEST: ['#d2ac62','#fff0bb','#78503b','#272119','#e5ce93','LES RELIQUES DE L’AUBE'],
    OCCULTIST: ['#a185b9','#f9d2ec','#503250','#211323','#c598be','LES PACTES DU CRÉPUSCULE'],
    ARTIFICER: ['#73b5b7','#e4ffec','#305764','#122328','#c9af82','LES MACHINES DU SONGE'],
  };
  const sets = {
    WARRIOR:['Fer de veille','Acier des remparts','Acier damassé','Acier damassé et or pâle','Météorite, or et saphir'],
    RANGER:['Frêne et cuir','If et bronze','If, corne et jade','If pétrifié et argent','Bois millénaire et jade étoilé'],
    MAGE:['Étain et verre','Argent et quartz','Argent et cristal','Cristal astral et argent','Cristal vivant et or blanc'],
    PRIEST:['Lin et laiton','Ivoire et bronze','Nacre et vermeil','Vermeil et opale solaire','Or repoussé et nacre sacrée'],
    OCCULTIST:['Fer fumé et cire','Os et cuivre noir','Obsidienne et argent','Obsidienne, os et améthyste','Verre de nuit et argent funéraire'],
    ARTIFICER:['Cuivre et céramique','Laiton et acier','Cuivre, quartz et émail','Laiton gravé et verre optique','Orichalque et cristal de mémoire'],
  };
  // Each legendary is authored individually; IDs and all combat properties are retained.
  const legends = {
    weapon: [
      ['Orrery, les Sept Sceaux','Sept anneaux retiennent une étoile qui refuse de mourir.','orrery'],
      ['Deuil, la Nuit creuse','Le fil disparaît à la lumière. Son ombre reste tranchante.','eclipse'],
      ['Le Dernier Chapitre','La lame ouvre les livres que personne ne devrait lire.','script'],
      ['La Veuve des Sept Rois','Chaque courbe porte le nom d’un serment rompu.','thorns'],
      ['La Clé du premier jour','Ses dents ouvrent la porte derrière le soleil.','dawn'],
      ['Les Ciseaux de l’éternité','Deux lames. Un seul fil. Aucun retour.','orbit'],
    ],
    chest: [
      ['Le Ciel sous la peau','Des constellations vivantes veillent sous chaque plaque.','orrery'],
      ['Le Trône sans roi','La couronne s’est brisée ; la cuirasse tient encore.','crown'],
      ['Le Cœur héliographe','Un soleil mécanique bat derrière les côtes d’or.','engine'],
      ['L’Hôte du Premier Pacte','La doublure murmure le nom de son prochain porteur.','eclipse'],
      ['La Chasuble de l’origine','Broderie de nacre, fil de lumière, sept cloches muettes.','dawn'],
      ['Le Rêve du dragon','Sous les écailles, quelque chose respire encore.','thorns'],
    ],
    guard: [
      ['Le Soleil des exilés','Un fragment d’aurore enchâssé dans une roue de vermeil.','dawn'],
      ['Le Septième Horizon','Sept cercles se ferment avant que le coup ne tombe.','orrery'],
      ['Le Rempart du dernier roi','La couronne a plié. Le rempart, jamais.','crown'],
      ['La Porte de l’éclipse','On ne voit pas ce qui se tient de l’autre côté.','eclipse'],
      ['Le Veilleur sans paupière','Il observe les coups qui n’ont pas encore été portés.','eye'],
      ['Le Prisme des mille refuges','Chaque facette abrite un ciel différent.','prism'],
    ],
    legs: [
      ['Les Pas du soleil voilé','Une poussière d’or demeure là où le porteur est passé.','dawn'],
      ['Les Sabots du premier ange','Le métal s’arrête juste avant de toucher le sol.','wings'],
      ['Les Sept Traversées','Des astrolabes minuscules comptent des routes impossibles.','orrery'],
      ['La Marche du roi absent','Leurs traces traversent la pierre et s’effacent dans l’air.','crown'],
      ['Les Messagères d’Hermès','Chaque plume est une lame d’or articulée.','wings'],
      ['Les Pas du roi noyé','Des écailles de nuit, ourlées d’écume argentée.','tide'],
    ],
  };
  const classLegends = {
    'class-1-weapon-3':['Le Bâton des neuf mondes','Neuf orbites autour d’un fragment de ciel.','orrery'],
    'class-3-weapon-3':['Le Faucon du dernier bois','Ses ailes sculptées se souviennent du vent.','wings'],
    'class-5-weapon-3':['La Fourche du ciel captif','Trois dents d’orichalque maintiennent un éclair immobile.','engine'],
    'class-1-chest-3':['L’Atlas des nuits futures','Chaque pli révèle une constellation encore inconnue.','script'],
    'class-3-chest-3':['Le Manteau du roi des bois','Une ramure d’argent protège un cœur de feuilles.','thorns'],
    'class-5-chest-3':['Le Laboratoire du ciel','Un réacteur de cristal et des conduits de cuivre ciselé.','engine'],
    'class-1-guard-3':['La Mémoire de l’océan','Une sphère de verre enferme le reflet d’une mer disparue.','tide'],
    'class-3-guard-3':['La Carapace du premier jardin','Jade, écailles et racines grandissent ensemble.','thorns'],
    'class-5-guard-3':['Le Vitrail du tonnerre','La lumière reste prisonnière des nervures de cuivre.','prism'],
    'class-1-legs-3':['Les Patins de l’horizon','Leurs lames tracent des méridiens dans le vide.','orbit'],
    'class-3-legs-3':['Les Raquettes du dernier hiver','Une forêt miniature dort sous la neige des cadres.','thorns'],
    'class-5-legs-3':['Les Bottes du navigateur céleste','Des turbines d’orichalque tournent sans bruit.','engine'],
  };
  function title(text) {
    if(text.length<=23)return [text];
    const words=text.split(' '); let best=1, distance=Infinity;
    for(let i=1;i<words.length;i++) {const d=Math.abs(words.slice(0,i).join(' ').length-words.slice(i).join(' ').length);if(d<distance){distance=d;best=i;}}
    return [words.slice(0,best).join(' '),words.slice(best).join(' ')];
  }
  function enrich(card) {
    const legend=classLegends[card.id]||(card.tier===4&&!card.id.startsWith('class-')&&!card.design?legends[card.slot][card.variant]:null);
    const epicNotes={WARRIOR:['La garde porte les entailles de cent sièges.','Le dernier serment du maître de forge est gravé dans le métal.'],RANGER:['Une ramure d’argent se referme sur un cœur de jade.','Le bois garde la mémoire des sentiers disparus.'],MAGE:['Des signes minuscules changent de place à la nuit tombée.','Le cristal garde un éclat de la première constellation.'],PRIEST:['La nacre luit au rythme d’une cloche lointaine.','Sept vœux sont cousus sous le fil de vermeil.'],OCCULTIST:['Les gravures semblent plus profondes dans l’obscurité.','Un nom effacé revient lentement à la surface.'],ARTIFICER:['Chaque rouage possède sa propre inscription.','Un cristal suspendu alimente les conduits de cuivre.']};
    const theme=legend?.[2]||({WARRIOR:'crown',RANGER:'thorns',MAGE:'orrery',PRIEST:'dawn',OCCULTIST:'eclipse',ARTIFICER:'engine'})[card.school];
    return {...card,...(card.tier===3?{note:epicNotes[card.school][card.variant%2]}:{}), ...(legend?{name:title(legend[0]),note:legend[1]}:{}),
      ...(card.tier>=3&&!card.design?{material:sets[card.school][card.tier]}:{}), relicTheme:theme};
  }
  function gem(x,y,size,tone,hi) {
    return `<g transform="translate(${x} ${y})">`+path(`M0 ${-size}L${size*.7} 0 0 ${size} ${-size*.7} 0Z`,tone,hi,.7)+
      path(`M0 ${-size}V${size}L${-size*.7} 0Z`,hi)+path(`M0 ${-size}L${size*.7} 0H0Z`,'#fff8e7')+'</g>';
  }
  function filigree(x,y,scale,color,flip=1) {
    return `<g transform="translate(${x} ${y}) scale(${scale*flip} ${scale})" opacity=".85">`+
      path('M0 39C14 25-7 12 2 0C10-11 28-3 19 5C12 10 5 1 12-3M1 17C25 10 28 24 17 26C9 27 8 18 15 17M0 30C-14 21-18 31-9 35M2 2C-8-5-9-17-2-21','none',color,.9)+'</g>';
  }
  function emblem(theme,color,hi) {
    let out='';
    if(['orrery','orbit','engine'].includes(theme)) {
      for(let i=0;i<3;i++)out+=`<ellipse rx="${30+i*8}" ry="${10+i*5}" transform="rotate(${i*60+20})" fill="none" stroke="${color}" stroke-width="${i===1?1.8:.8}"/>`;
      out+=circle(0,0,14,'#121d2b',hi,1)+gem(0,0,10,color,hi);
      if(theme==='engine')for(let i=0;i<12;i++)out+=`<g transform="rotate(${i*30})">${path('M-3-43V-49H3V-43',color,hi,.6)}</g>`;
    } else if(theme==='crown') {
      out=path('M-31-15L-19-4-12-25 0-8 12-25 19-4 31-15 23 15H-23Z',color,hi,1.2)+path('M-23 10H23M-20 19H20','none',hi,2)+gem(0,1,7,'#729ab9',hi);
    } else if(theme==='wings') {
      for(const s of [-1,1])for(let i=0;i<6;i++)out+=`<g transform="scale(${s} 1)">`+path(`M3 10Q${17+i*4} ${-25-i*3} ${43-i*4} ${-32+i*8}Q24 20 3 17Z`,color,hi,.8)+'</g>';
      out+=gem(0,4,11,color,hi);
    } else if(theme==='thorns') {
      for(const s of [-1,1])out+=`<g transform="scale(${s} 1)">`+path('M0 30Q35 9 20-29M21-14l14-9-13 1M21 2l18-4-15 10M12 19l13 9-4-16','none',color,2)+path('M20-23Q1-26 8-39Q23-35 20-23M25 2Q39-12 43-1Q35 10 25 2',color,hi,.7)+'</g>';
      out+=gem(0,0,13,color,hi);
    } else if(theme==='prism') {
      out=path('M0-43L33-12 24 26 0 42-24 26-33-12Z','#274761',hi,1.2)+path('M0-43V42L33-12H-33L24 26-24 26Z','none',color,1.2)+path('M0-43L-33-12H0Z',color);
    } else if(theme==='eye') {
      out=path('M-44 0Q0-43 44 0Q0 43-44 0Z','#1b1828',hi,1.3)+circle(0,0,19,color,hi)+path('M0-16Q8 0 0 16Q-8 0 0-16Z','#111221')+circle(-6,-7,4,'#fff7e8');
    } else if(theme==='tide') {
      for(let i=0;i<4;i++)out+=path(`M-38 ${-16+i*12}q10-13 20 0t20 0t20 0t20 0`,'none',i%2?color:hi,1.2);
    } else if(theme==='script') {
      out=path('M0-23Q-20-36-34-24V25Q-14 17 0 31Q14 17 34 25V-24Q20-36 0-23Z',color,hi,1.3)+path('M0-23V30','none',hi);
      for(let i=0;i<5;i++)out+=path(`M-26 ${-15+i*7}l18 4m16 0 18-4`,'none',hi,.8);
    } else {
      if(theme==='dawn')for(let i=0;i<16;i++)out+=`<g transform="rotate(${i*22.5})">${path('M0-25V-40','none',hi,i%2?.8:2)}</g>`;
      out+=circle(0,0,24,color,hi,1.4)+circle(theme==='eclipse'?10:0,theme==='eclipse'?-6:0,theme==='eclipse'?21:16,theme==='eclipse'?'#151322':'none',hi,.7);
    }
    return out;
  }
  function scenery(card,id,p) {
    const [,hi,,night,trim]=p, tier=card.tier, seed=card.number*13+card.variant*19;
    let s=`<rect x="9" y="9" width="282" height="420" rx="15" fill="${night}"/><rect x="10" y="38" width="280" height="280" fill="url(#${id}sky)"/>`;
    s+='<g opacity="'+(card.refinement?'.38':'1')+'">';
    // Six environments rather than a circular badge repeated behind every object.
    if(card.school==='WARRIOR') {
      for(let i=0;i<7;i++)s+=path(`M${12+i*43} 277V${90+(i%3)*19}l15-13 15 13V277Z`,'#26313e',trim,.25);
      s+=path('M28 263L118 230 276 266 219 281H73Z','#121a24',trim,.5)+path('M38 282H267M56 290H246','none',trim,.6);
    } else if(card.school==='RANGER') {
      for(let i=0;i<8;i++) {const x=8+i*43; s+=path(`M${x} 282Q${x+22} 174 ${x-7} 60L${x+7} 53Q${x+41} 160 ${x+17} 283Z`,i%2?'#203d3c':'#193133');s+=path(`M${x+13} 175l-27-49m31 25 27-44`,'none',trim,.5);}
      s+=`<ellipse cx="150" cy="277" rx="113" ry="16" fill="#0a1a20"/>`;
    } else if(card.school==='MAGE') {
      s+=circle(175,123,64,'none',trim,.7)+circle(175,123,58,'none',trim,.3);
      for(let i=0;i<9;i++)s+=path(`M${15+i*34} 285L150 172M13 ${222+i*10}H287`,'none',trim,.25);
      s+=`<ellipse cx="160" cy="149" rx="117" ry="49" transform="rotate(-30 160 149)" fill="none" stroke="${trim}" stroke-width=".6"/>`;
    } else if(card.school==='PRIEST') {
      for(const x of [24,75,126,177,228])s+=path(`M${x} 279V112Q${x+23} 58 ${x+46} 112V279Z`,'#443729',trim,.5)+path(`M${x+23} 90V269M${x} 154H${x+46}`,'none',trim,.5);
      s+=path('M150 45L276 288H24Z',hi)+'<path d="M150 45L276 288H24Z" fill="'+night+'" opacity=".9"/>';
    } else if(card.school==='OCCULTIST') {
      s+=circle(163,132,74,'#77506e')+circle(184,113,68,night);
      for(let i=0;i<8;i++)s+=path(`M${8+i*43} 282V${203-i%3*21}l14-26 14 26V282Z`,'#120e1b',trim,.35);
      s+=path('M19 271Q91 231 140 265T286 258','none',trim,.65);
    } else {
      for(let i=0;i<4;i++){const x=42+i*70,y=100+(i%2)*133;s+=circle(x,y,45,'none',trim,1)+circle(x,y,33,'none',trim,.5);for(let j=0;j<12;j++)s+=`<g transform="translate(${x} ${y}) rotate(${j*30})">${path('M-3-40V-48H3V-40','none',trim,.6)}</g>`;}
      s+=path('M25 273V160H67V89M273 277V188H249V60','none',trim,2);
    }
    s+=`</g><rect x="10" y="38" width="280" height="280" fill="url(#${id}mist)"/>`;
    if(tier>=2) for(let i=0;i<(card.refinement?8:16+tier*5);i++)s+=circle(22+(i*73+seed)%257,55+(i*47+seed)%225,i%5? .65:1.2,hi);
    if(tier>=3) {
      if(!card.refinement)s+=`<g transform="translate(150 172)" opacity=".32">${emblem(card.relicTheme,trim,hi)}</g>`;
      for(const side of [0,1])s+=filigree(side?274:26,78,1.2,trim,side?-1:1)+filigree(side?274:26,228,.8,trim,side?-1:1);
    }
    return s;
  }
  function artwork(card,id,p) {
    const [core,hi,shade,,trim]=p,tier=card.tier;
    const refined=typeof RefinedArt!=='undefined'&&RefinedArt.make(card,id);
    if(refined){
      const defs=refined.defs+`<g id="${id}shape">${refined.shape}</g>`;
      const art=`<g clip-path="url(#${id}window)"><g transform="translate(150 194) rotate(${refined.angle}) scale(${refined.scale})"><g class="card-object" filter="url(#${id}shadow)"><use href="#${id}shape"/></g></g></g>`;
      return {defs,art};
    }
    // The original, varied silhouettes are now forged with narrow edges and layered materials.
    const metal=`url(#${id}metal)`, cloth=`url(#${id}cloth)`;
    const adapted={...card,colors:card.tier<3?card.colors:[core,hi,shade],pose:[0,0,0,1]};
    const forged=FantasyArt.make(card,id,p)||RelicSculpt.make(card,id,p)||RelicForge.make(card,id,p);
    let shape=forged||WeaponStudy.equipment(adapted,card.slot==='chest'&&/robe|cape|mante|chasuble|surcot|veste/i.test(card.type)?cloth:metal);
    shape=shape.replace(/stroke-width="([\d.]+)"/g,(_,n)=>`stroke-width="${Math.max(.6,Number(n)*.62)}"`);
    const ornament=tier>=2&&!forged?'<g opacity=".92">'+
      (card.slot==='weapon'?filigree(-5,-27,.45,trim)+filigree(5,-27,.45,trim,-1):
        filigree(-27,-36,.72,trim)+filigree(27,-36,.72,trim,-1))+'</g>':'';
    const relief=tier>=3&&!forged?`<g transform="translate(${card.slot==='weapon'?0:0} ${card.slot==='weapon'?-42:-10}) scale(${card.slot==='weapon'?.36:.55})">${emblem(card.relicTheme,trim,hi)}</g>`:'';
    const etching=Array.from({length:tier>=3?22:7},(_,i)=>path(`M${-78+i*8} -100q${18+i%3*6} 30 0 70t0 90`,'none',hi,tier>=3?.45:.3)).join('');
    const rivets=tier>=1&&!forged?Array.from({length:8},(_,i)=>circle(-43+(i%2)*86,-41+Math.floor(i/2)*24,1.25,trim,shade,.4)).join(''):'';
    // All surface details are clipped to the actual silhouette, including open bows and chains.
    const defs=`<g id="${id}shape">${shape}</g><mask id="${id}mask" maskUnits="userSpaceOnUse" x="-130" y="-140" width="260" height="280" style="mask-type:alpha"><use href="#${id}shape"/></mask>`;
    const angle=card.fantasyForm?(card.slot==='weapon'?8:0):card.design?(card.slot==='weapon'?12:0):card.slot==='weapon'?(card.pose?.[2]||-18)*.65:card.slot==='legs'?-9:card.variant%2?8:-8;
    const scale=card.fantasyForm?(card.slot==='weapon'?1.15:1.33):card.design?(card.slot==='weapon'?1.16:card.slot==='legs'?1.24:1.33):forged?(card.slot==='weapon'?1.12:1.29):card.slot==='weapon'?(['axe','scythe'].includes(card.art)&&!card.special?1.05:1.17):1.35;
    let detail=`<use href="#${id}shape"/><g mask="url(#${id}mask)"><g opacity=".22">${etching}</g>${ornament}${relief}${rivets}<rect x="-120" y="-130" width="240" height="260" fill="url(#${id}polish)"/></g>`;
    if(tier>=3&&!card.design&&!card.fantasyForm){
      // Suspended fragments give the rarest pieces a distinct silhouette, not just a recolor.
      const n=tier===4?7:3;
      for(let i=0;i<n;i++){const a=(i/n*Math.PI*2)-.5;detail+=gem(Math.cos(a)*(card.slot==='weapon'?48:66),Math.sin(a)*70,2.2+(i%3),core,hi);}
    }
    const art=`<g clip-path="url(#${id}window)"><g transform="translate(150 191) rotate(${angle}) scale(${scale})"><g class="card-object" filter="url(#${id}shadow)">${detail}</g></g></g>`;
    return {defs,art};
  }
  // Rarity is visible on a normal card too. These decorations carry no bonus
  // and never stand in for the independent blessing/curse labels.
  function prestige(card,id){
    if(card.tier<3)return {defs:'',back:'',front:''};
    const legendary=card.tier===4,color=legendary?'#ffcb70':'#bd83ff',light=legendary?'#fff0bc':'#c9d9ff';
    const defs=`<radialGradient id="${id}prestige"><stop stop-color="${color}" stop-opacity="${legendary?.35:.25}"/><stop offset=".55" stop-color="${color}" stop-opacity=".13"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient><linearGradient id="${id}radiance" x1="0" y1="1" x2=".2" y2="0"><stop stop-color="${color}" stop-opacity="0"/><stop offset=".5" stop-color="${light}" stop-opacity=".45"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></linearGradient>`;
    let rays='';
    for(let i=0;i<(legendary?12:8);i++)rays+=`<g transform="translate(150 190) rotate(${i*360/(legendary?12:8)+15})">`+path(`M-2-43L-${legendary?6:3}-110 0-126 ${legendary?6:3}-110 2-43Z`,`url(#${id}radiance)`)+path('M0-98V-116','none',light,.6)+'</g>';
    let tracery='';
    const n=legendary?12:8;
    for(let i=0;i<n;i++)tracery+=`<g transform="translate(150 190) rotate(${i*360/n})">`+path('M-3-94L0-100 3-94 0-88Z',i%2?color:light)+path('M-17-86Q0-90 17-86','none',color,.7)+'</g>';
    const back=`<g class="rarity-aura" clip-path="url(#${id}window)" data-prestige="${card.tier}"><ellipse class="rarity-halo" cx="150" cy="191" rx="131" ry="140" fill="url(#${id}prestige)"/><g class="rarity-rays" opacity="${legendary?.65:.42}">${rays}</g><g opacity="${legendary?.66:.45}">${tracery}</g>${legendary?'<ellipse cx="150" cy="297" rx="88" ry="8" fill="none" stroke="'+color+'" stroke-width="1" opacity=".4"/>':''}</g>`;
    let front='<g class="rarity-sparks" clip-path="url(#'+id+'window)">';
    for(let i=0;i<(legendary?12:7);i++){
      const x=i%2?244+(i%3)*5:39+(i%3)*7,y=75+(i*43)%229,k=i%3===0?4:2;
      front+=`<g style="--spark-delay:-${i*.37}s" transform="translate(${x} ${y})">`+path(`M0-${k}L.8-.8 ${k} 0 .8.8 0 ${k}-.8.8-${k} 0-.8-.8Z`,light)+circle(0,0,k*2.5,color,'none',0).replace('<circle','<circle opacity=".09"')+'</g>';
    }
    front+='</g>';
    if(legendary)for(const x of [18,282])front+=`<g transform="translate(${x} 0) scale(${x===18?1:-1} 1)">`+path('M0 51L13 59 5 67 13 76 5 84V111M0 313L13 305 5 297 13 288 5 280V256','none',color,1.1)+path('M4 62L8 66 4 70 0 66ZM4 294L8 298 4 302 0 298Z',light)+'</g>';
    return {defs,back,front};
  }
  let sequence=0;
  function face(card,{finish='',boostValue=card.roll,layer='full'}={}) {
    const tier=card.tier,p=[...palettes[card.school]];
    if(tier===4)p[4]='#d5b575';
    const [core,hi,shade,night,trim,collection]=p;
    const id='relic'+(++sequence),rim=WeaponStudy.tiers[tier].color;
    const {defs:shapeDefs,art}=artwork(card,id,p),rarityArt=prestige(card,id);
    const defs=`<clipPath id="${id}window"><rect x="12" y="47" width="276" height="296" rx="5"/></clipPath>
      <linearGradient id="${id}sky" x2=".8" y2="1"><stop stop-color="${shade}"/><stop offset="1" stop-color="${night}"/></linearGradient>
      <radialGradient id="${id}mist" cx="45%" cy="40%" r="68%"><stop stop-color="${core}" stop-opacity=".10"/><stop offset=".65" stop-color="${night}" stop-opacity=".25"/><stop offset="1" stop-color="${night}" stop-opacity=".95"/></radialGradient>
      <linearGradient id="${id}metal" x1="0" y1=".2" x2="1" y2=".75"><stop stop-color="${shade}"/><stop offset=".22" stop-color="${core}"/><stop offset=".38" stop-color="${hi}"/><stop offset=".44" stop-color="${core}"/><stop offset=".66" stop-color="${shade}"/><stop offset=".88" stop-color="${core}"/><stop offset="1" stop-color="${hi}"/></linearGradient>
      <linearGradient id="${id}gilt" x1="0" y1="0" x2="1" y2=".8"><stop stop-color="#574330"/><stop offset=".25" stop-color="${trim}"/><stop offset=".43" stop-color="#fff0c3"/><stop offset=".5" stop-color="#a1844d"/><stop offset=".8" stop-color="#e5d2a2"/><stop offset="1" stop-color="#635037"/></linearGradient>
      <linearGradient id="${id}glass" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${shade}"/><stop offset=".35" stop-color="${core}"/><stop offset=".42" stop-color="${hi}"/><stop offset=".48" stop-color="${core}"/><stop offset=".75" stop-color="${night}"/><stop offset="1" stop-color="${core}"/></linearGradient>
      <linearGradient id="${id}cloth"><stop stop-color="${shade}"/><stop offset=".25" stop-color="${core}"/><stop offset=".4" stop-color="${shade}"/><stop offset=".57" stop-color="${core}"/><stop offset=".8" stop-color="${shade}"/><stop offset="1" stop-color="${core}"/></linearGradient>
      <linearGradient id="${id}polish" x2=".6" y2="1"><stop stop-color="#ffffff" stop-opacity=".23"/><stop offset=".4" stop-color="#ffffff" stop-opacity="0"/><stop offset="1" stop-color="#040b17" stop-opacity=".4"/></linearGradient>
      <linearGradient id="${id}footer" x2="0" y2="1"><stop stop-color="${night}" stop-opacity="0"/><stop offset=".18" stop-color="${night}"/><stop offset="1" stop-color="#0e1119"/></linearGradient>
      <linearGradient id="${id}rim" x2=".6" y2="1"><stop stop-color="${hi}"/><stop offset=".28" stop-color="${trim}"/><stop offset=".5" stop-color="${shade}"/><stop offset=".8" stop-color="${trim}"/><stop offset="1" stop-color="${hi}"/></linearGradient>
      <filter id="${id}shadow" x="-45%" y="-35%" width="190%" height="180%" color-interpolation-filters="sRGB"><feDropShadow dx="4" dy="9" stdDeviation="4" flood-color="#030912" flood-opacity=".9"/><feDropShadow dx="-1" dy="-1" stdDeviation=".5" flood-color="${hi}" flood-opacity=".5"/></filter>${shapeDefs}${rarityArt.defs}`;
    const open=`<svg class="card-face relic-face tier-${tier}" xmlns="http://www.w3.org/2000/svg" width="600" height="876" viewBox="0 0 300 438" role="img" aria-label="${esc(card.name.join(' ')+' · '+WeaponStudy.tiers[tier].name+' · '+card.damageTypes.map(t=>RelicCuration.types[t].label).join(' + '))}"><defs>${defs}</defs>`;
    if(layer==='object')return open+art+'</svg>';
    let frame=`<rect x="5" y="5" width="290" height="428" rx="17" fill="none" stroke="${rim}" stroke-width="${[1.2,2,2.7,3.6,4.5][tier]}"/><rect x="10" y="10" width="280" height="418" rx="12" fill="none" stroke="${rim}" stroke-opacity=".45" stroke-width=".6"/>`;
    // Color, rank cuts and a written label make rarity readable even as a thumbnail.
    for(const x of [0,300])for(const y of [0,438])frame+=`<g transform="translate(${x} ${y}) scale(${x?-1:1} ${y?-1:1})">`+
      path(tier>=3?'M6 64V23Q6 6 23 6H70L56 12H24Q12 12 12 24V50Z':'M6 44V23Q6 6 23 6H44','none',rim,tier>=3?2:1)+
      (tier>=2?path(tier===4?'M15 32L15 15 32 15 25 20 23 28 20 25Z':'M15 29V15H29L21 21Z',rim):'')+'</g>';
    if(tier>=3)for(const x of [8,292])for(let i=0;i<tier;i++)frame+=path(`M${x} ${167+i*10}l3 4-3 4-3-4Z`,rim);
    const rarity=WeaponStudy.tiers[tier].name.toUpperCase(), label=equipmentSchools[card.school], titleLines=title(card.name.join(' '));
    const titleSize=Math.min(tier===4?19:18,252/(Math.max(...titleLines.map(s=>s.length))*.53));
    const info=finish&&finish!=='Normale'?finish:'';
    const effects=[...(info?[{text:info,color:info==='Bénédiction'?'#ffe1a6':'#c5d4f5'}]:[]),...(card.curse?[{text:'Malédiction',color:'#f6a2b9'}]:[])];
    const badges=effects.map((e,i)=>{const x=effects.length===2?84+i*132:150;return `<rect x="${x-59}" y="326" width="118" height="19" rx="9.5" fill="#10151e" stroke="${e.color}" stroke-opacity=".5" stroke-width=".6"/><text x="${x}" y="339" text-anchor="middle" font-family="system-ui,sans-serif" font-size="8.6" fill="${e.color}">${esc(e.text)}</text>`;}).join('');
    const footer=`<rect x="11" y="312" width="278" height="115" rx="7" fill="url(#${id}footer)"/>`+badges+
      `<g text-anchor="middle" font-family="system-ui,sans-serif">`+
      titleLines.map((text,i)=>`<text x="150" y="${titleLines.length===1?379:366+i*22}" font-family="Georgia,serif" font-size="${titleSize}" fill="#fff3df">${esc(text)}</text>`).join('')+
      `<text x="${card.curse?87:150}" y="416" fill="#d3e8cf" font-size="13" font-weight="650">+${WeaponStudy.percent(boostValue)} ${esc(card.stat)}</text>`+
      (card.curse?`<text x="221" y="416" fill="#e9a5b7" font-size="9">−${WeaponStudy.percent(card.curse.value)} ${esc(card.curse.stat)}</text>`:'')+'</g>';
    return open+`<rect width="300" height="438" rx="19" fill="#0c1017"/>`+scenery(card,id,p)+rarityArt.back+(layer==='base'?'':art)+rarityArt.front+footer+frame+
      `<g font-family="system-ui,sans-serif">${card.damageTypes.map((t,i)=>RelicCuration.icon(t,23+i*31,18,23)).join('')}<rect x="174" y="18" width="104" height="23" rx="5" fill="${rim}" fill-opacity=".15" stroke="${rim}" stroke-width=".7"/><text x="226" y="33" text-anchor="middle" font-size="8.6" font-weight="700" letter-spacing=".45" fill="${rim}">${esc(rarity)}</text></g></svg>`;
  }

  return {enrich,face};
})();
