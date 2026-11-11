'use strict';
const ArsenalCatalog=(()=>{
  const {tiers,percent}=WeaponStudy;
  const face=(card,options)=>card.edition===4?AtelierArt.face(card,options):RelicArt.face(card,options);
  const slots={
    weapon:{name:'Armes',stat:'FOR',color:'#ef8da9',forms:['Couteau','Estoc','Hache','Masse','Faux','Arc','Lance','Flamberge','Sceptre','Espadon']},
    chest:{name:'Torse',stat:'CON',color:'#d9a3e7',forms:['Cuirasse','Plastron','Brigandine','Haubert','Corselet','Broigne','Lorica','Armure']},
    guard:{name:'Protections',stat:'CON',color:'#84dae7',forms:['Écu','Pavois','Rondache','Targe','Égide','Bocle','Bouclier','Scutum']},
    legs:{name:'Jambes',stat:'DEX',color:'#e6b38f',forms:['Grèves','Jambières','Cnémides','Cuissards','Guêtres','Garde-jambes','Sabots','Bottes']},
  };
  const forms=['knife','rapier','axe','mace','scythe','bow','spear','flamberge','scepter','greatsword'];
  const materials=[
    ['Fer noir','Acier bruni','Bronze coulé','Fonte polie','Verre fumé','Bois d’if','Acier bleui','Obsidienne','Laiton','Acier noir'],
    ['Laiton gravé','Acier damassé','Cuivre martelé','Fer émaillé','Nacre','If et argent','Acier et quartz','Obsidienne et cuivre','Or martelé','Acier et améthyste'],
    ['Argent vieilli','Acier lunaire','Bronze runique','Fonte d’ombre','Cristal opalin','If spectral','Alliage astral','Obsidienne gravée','Or et grenat','Fer et améthyste'],
  ];
  const supportMaterials={
    chest:['et cuir','et lin','et velours','et mailles fines','et soie noire'],
    guard:['et chêne','et cuir bouilli','et bois d’if','et frêne','et acier'],
    legs:['et cuir','et sangles de lin','et velours','et maille','et cuir clouté'],
  };
  const swatches=[
    ['#a1a8af','#f1dfc5','#475362'],['#ad8055','#f4d699','#5f3b36'],['#899fb3','#dce8fa','#483f5c'],
    ['#8e7bab','#f5cfef','#472645'],['#81a999','#d7e9bc','#2c4e4f'],['#b67977','#ffe0c8','#593345'],
    ['#6e9bbf','#d3f0f2','#374b69'],['#b39b70','#ffe4ae','#725647'],['#b579a2','#ffdbed','#442745'],
    ['#8d9dad','#dae6d3','#4f5869'],['#a16662','#ffd1a2','#4e3446'],['#8ca3c1','#d9c9f1','#3b385c'],
  ];
  const epithets=[
    'du guet|du pèlerin|des faubourgs|du vieux gué|des marchands|du cloître|du guetteur|de la patrouille|du relais|de l’écuyer|des remparts|du sentier|des forges basses|de l’atelier|de la chapelle',
    'des épines|de la Rose noire|de la Cour d’ivoire|de l’If creux|de l’Aube scellée|des sentinelles|de la crypte|du Voile|du Lys de fer|de l’Ordre gris|de la Lune fendue|des Vitraux morts|de la Porte close',
    'de la Lune creuse|du Vœu brisé|des Corbeaux d’argent|de l’Ermite rouge|du Chant funèbre|du Lys spectral|du Sépulcre|de la Cour sans roi|du Songe pourpre|du Cadran voilé',
    'du Cœur des ronces|de la Nuit couronnée|du Vitrail vivant|de la Rose éternelle|du Dernier Serment|de la Cour des cendres|du Glas argenté|du Sceau rompu',
    'de la Couronne vide|du Soleil voilé|du Premier Pacte|des Sept Sceaux',
  ].map(row=>row.split('|'));
  const backgrounds=['atelier','arch','grove','rose','moon','glass','orbit','curse','sun','abyss'];
  const descriptors=['posé en biais sur un fond gravé','décalé dans une ogive','pris dans un cercle de ronces','suspendu devant une rosace','sur un croissant de lune','encadré de lignes de vitrail','traversé par deux anneaux rituels','entouré de fissures','dressé devant un sceau doré','éclairé sur un fond de crypte'];
  const caps={regular:550,bug:1000};
  const finishes={
    normal:{name:'Normale',mult:1,chance:.9880,mark:''},
    shiny:{name:'Brillante',mult:1.08,chance:.0080,mark:'✦'},
    animated:{name:'Animée',mult:1.14,chance:.0022,mark:'✧'},
    chroma:{name:'Chromatique',mult:1.25,chance:.0011,mark:'✺'},
    blessed:{name:'Bénédiction',mult:1.34,chance:.0006,mark:'☼'},
    bug:{name:'Bug',mult:1,chance:.0001,mark:'▧'},
  };
  const bugBands=[
    {name:'Instable',chance:.96,min:45,max:90},
    {name:'Stable',chance:.0375,min:90,max:145},
    {name:'Surcharge',chance:.0025,min:230,max:340},
  ];
  const rarityWeights=[.515,.30,.145,.035,.005];
  // Authored oddities broaden the silhouettes without tying humor to a rarity.
  const oddities={
    '11-weapon-0':['Pic du mineur distrait','Pic','Frêne et fer brut','pick',[130,137,-23,1.28],'Une pioche trop bien équilibrée pour rester à la mine.'],
    '3-weapon-1':['Serpe de la cueilleuse','Serpe','Fer poli et cuir','sickle',[116,137,33,1.35],'Sa lame a la courbe d’une jeune lune.'],
    '7-weapon-2':['Fléau du bourdon','Fléau','Acier et chaîne','flail',[122,130,-29,1.15],'La tête dentée bourdonne au bout de trois maillons enchantés.'],
    '5-weapon-3':['Hallebarde du Grand Glas','Hallebarde','Acier damassé et argent','halberd',[120,141,49,1.40],'Une pointe et une hache réunies sous un anneau de cloche.'],
    '2-weapon-4':['Tranche-page de Minuit','Lame-grimoire','Acier lunaire et reliure','tomeblade',[122,144,19,1.32],'Le marque-page est plus tranchant que le livre ne le laisse penser.'],
    '10-chest-0':['Mantelet de la Porte basse','Mantelet','Laine et agrafes','mantle',[123,139,-9,1.49],'Le col et les plis lourds couvrent les épaules.'],
    '4-chest-1':['Brigandine des échoppes','Brigandine','Cuir et plaques de fer','brigandine',[127,138,7,1.52],'De petites plaques rivetées tracent un damier sur le torse.'],
    '8-chest-2':['Habit des Écailles dormantes','Armure à écailles','Bronze et velours','scale-armor',[123,143,-10,1.48],'Les écailles chevauchent le tissu jusque sous les bras.'],
    '6-chest-3':['Cage du Chevalier d’os','Cuirasse à côtes','Os sculpté et cuivre','ribcage',[128,142,8,1.52],'Des côtes gravées entourent un sceau rouge.'],
    '11-guard-0':['Porte du mauvais côté','Porte-bouclier','Planches et fer','door',[125,140,-13,1.38],'Il reste une poignée, mais l’autre côté a perdu sa serrure.'],
    '3-guard-1':['Cerf-volant du guet','Bouclier en amande','Bois peint et bronze','kite',[126,139,14,1.45],'La pointe tombe bas pour protéger le genou.'],
    '8-guard-2':['Miroir des mensonges','Miroir pare-sort','Argent et verre violet','mirror',[120,144,18,1.47],'Le reflet ne suit pas toujours le bon mouvement.'],
    '4-guard-3':['Éventail du dernier bal','Éventail de garde','Acier bleui et soie','fan',[132,146,-10,1.56],'Les brins d’acier se referment comme une mâchoire.'],
    '12-legs-0':['Bandes du messager fatigué','Bandages','Lin et cire','wraps',[124,141,13,1.41],'Noués vite. Ils tiennent depuis trois villages.'],
    '9-legs-1':['Botte du bouffon de garde','Botte à grelot','Cuir teint et laiton','bell-boot',[122,144,-18,1.45],'Le grelot ruine toute tentative de discrétion.'],
    '7-legs-2':['Sabots du cerf creux','Sabots d’armure','Bronze et corne','hoof',[126,142,-10,1.45],'Une semelle fendue en deux laisse une trace de bête.'],
    '3-legs-3':['Griffes du loup d’encre','Bottes à griffes','Acier noir et argent','claws',[125,143,10,1.50],'Les pointes recourbées accrochent la pierre.'],
    '4-weapon-0':['Poêle du dernier service','Poêle','Fonte et suie','pan',[126,146,39,1.24],'Une ancienne arme de cuisine. Elle laisse une odeur de beurre brûlé.'],
    '9-weapon-0':['Balai de la sorcière distraite','Balai','Bouleau et paille','broom',[119,150,-24,1.03],'Le manche a gardé la marque du premier sort de balayage.'],
    '6-weapon-1':['Chandelier de taverne','Chandelier','Laiton et cire','candlestick',[139,147,21,1.09],'Ramassé entre deux tabourets au cours d’une querelle.'],
    '5-weapon-2':['Arbalète du sonneur','Arbalète','If et acier bleui','crossbow',[125,146,38,1.13],'Sa détente fait un petit bruit de cloche.'],
    '3-chest-0':['Tablier du tavernier','Tablier','Cuir et toile','apron',[120,144,-7,1.17],'Une poche à épices et quelques anciennes marques de griffes.'],
    '8-chest-0':['Cape à trois reprises','Cape','Laine ravaudée','cloak',[127,145,10,1.19],'Trois coutures traversent le tissu. On compte aussi trois propriétaires.'],
    '6-chest-1':['Haubert des anneaux perdus','Haubert','Mailles de fer','mail',[122,148,-11,1.20],'Plusieurs anneaux ont été remplacés par des boucles de sac.'],
    '6-chest-2':['Plastron du bibliothécaire','Plastron','Reliures et cuir','book-armor',[128,144,8,1.31],'Deux grimoires servent de plaques. Le chapitre sur le feu reste interdit.'],
    '2-guard-0':['Couvercle de marmite','Couvercle','Fonte et étain','lid',[126,142,13,1.36],'Le manche fait une prise étonnamment solide.'],
    '7-guard-0':['Enseigne du Sanglier','Enseigne','Chêne peint','sign',[128,147,-23,1.23],'Elle indique encore la route du cellier.'],
    '7-guard-1':['Roue du mauvais chemin','Roue','Frêne et cerclage de fer','wheel',[126,148,11,1.35],'La charrette a perdu sa roue. Le groupe a gagné un bouclier.'],
    '5-guard-2':['Grimoire pare-sort','Grimoire','Cuir estampé et argent','book',[128,147,-17,1.35],'Relié avec une armature de métal pour encaisser les mauvaises lectures.'],
    '1-legs-0':['Botte trouée du pèlerin','Botte trouée','Cuir usé','boot-hole',[121,145,-15,1.31],'Le pied droit restera au sec. Pour le gauche, rien n’est promis.'],
    '6-legs-0':['Soulier de chevalier perdu','Sabaton esseulé','Acier riveté','lost-shoe',[120,147,-10,1.32],'Un seul soulier. Le chevalier est probablement allé chercher l’autre.'],
    '9-legs-0':['Chaussettes de feu de camp','Chaussettes','Laine roussie','socks',[123,145,12,1.23],'Chaudes, avec une légère odeur de fumée.'],
    '3-legs-1':['Sandales du moine pressé','Sandales','Cuir tressé','sandals',[125,146,-9,1.24],'Le moine les a nouées en courant. Les attaches tiennent encore.'],
    '8-legs-1':['Échasses du garde des marais','Échasses','Aulne et corde','stilts',[126,143,11,1.13],'Un peu hautes pour entrer à la taverne.'],
    '5-legs-2':['Patins du lac de verre','Patins','Cuir et argent','skates',[127,145,-15,1.25],'Des lames fines qui laissent un trait argenté sur la glace.'],
  };
  const controls={
    weapon:[[131,150,39],[103,150,-25],[116,145,-17],[147,147,21],[111,143,-21],[133,147,32],[122,142,51],[138,149,-34],[121,146,9],[131,146,29]],
    chest:[[126,139,-6],[115,146,11],[135,147,-8],[120,147,14],[132,142,-10]],
    guard:[[127,142,14],[113,145,-15],[141,147,9],[121,145,-22],[133,142,18]],
    legs:[[123,145,-9],[133,145,15],[121,147,-17],[137,142,8],[119,148,19]],
  };
  const curated=tiers.map((_,tier)=>WeaponStudy.cards.filter(card=>card.tier===tier));
  const integer=(lo,hi,rng)=>Math.min(hi,lo+Math.floor(rng()*(hi-lo+1)));
  function splitTitle(title){
    if(title.length<=22)return [title];
    const words=title.split(' ');
    let n=1;
    while(n<words.length-1&&words.slice(0,n+1).join(' ').length<20)n++;
    return [words.slice(0,n).join(' '),words.slice(n).join(' ')];
  }
  const statNames={BRA:'FOR',PEC:'CON',DOS:'CON',JAM:'DEX',REG:'CON','RÉG':'CON',CAR:'DEX'};
  const expansionTiers=[0,0,0,0,0,0,0,1,1,1,1,1,2,2,2,2,3,3,4,4];
  const legacyCatalog=Object.entries(slots).flatMap(([slot,info],section)=>{
    let number=0;
    const existing=tiers.flatMap((tier,rarity)=>Array.from({length:tier.count+expansionTiers.filter(t=>t===rarity).length},(_,i)=>{
      const id=i+'-'+slot+'-'+rarity;
      const preferred=slot==='weapon'?curated[rarity][i]:null;
      const form=(i*3+rarity*2+section)%info.forms.length;
      const color=(i*5+rarity*2+section*3)%swatches.length;
      const appearance=controls[slot][(form+i+rarity)%controls[slot].length];
      const type=info.forms[form];
      const design=(i*7+rarity+section*2)%backgrounds.length;
      const [low,high]=tier.range;
      const roll=low+Math.round((high-low)*((i*17+section*9+rarity*4)%61)/60);
      const cursed=(rarity===3&&i===tier.count-1)||(rarity===4&&i===tier.count-1);
      const penalty=slot==='guard'?'JAM':slot==='chest'?'BRA':slot==='legs'?'CAR':'DOS';
      const suffix=epithets[rarity][(i+section*3)%epithets[rarity].length];
      const material=materials[Math.min(2,Math.floor((rarity+1)/2))][(form+i)%10];
      const built={
        name:[type,suffix],type,material:slot==='weapon'?material:material+' '+supportMaterials[slot][(i+rarity)%5],
        roll,art:slot==='weapon'?forms[form]:slot,pose:[...appearance,(slot==='weapon'?1.17:1.39)+((i+form)%4)*.065],
        colors:swatches[color],background:backgrounds[design],note:material+', '+descriptors[design]+'.',
        ...(cursed?{curse:{stat:penalty,value:rarity===4?60:45}}:{}),
      };
      const index=expansionTiers.indexOf(rarity)+i-tier.count;
      const addition=i>=tier.count?equipmentExpansion[slot][index]:null;
      const odd=oddities[id];
      const special=addition?{name:addition[0],type:addition[1],material:addition[2],special:addition[3],
        colors:swatches[addition[4]],pose:[...appearance,1.45],note:addition[2]+', '+descriptors[design]+'.'}:{};

      const extra=odd?{name:odd[0],type:odd[1],material:odd[2],special:odd[3],pose:odd[4],note:odd[5]}:{};
      const content={...built,...preferred,...extra,...special};
      if(content.curse)content.curse={...content.curse,key:content.curse.stat.replace('É','E'),stat:statNames[content.curse.stat]};
      return {...content,roll,school:schoolFor(content,slot,rarity,i),name:typeof content.name==='string'?splitTitle(content.name):content.name,id,slot,stat:info.stat,number:++number,rarity,tier:rarity,form,variant:i,color:info.color};
    }));
    const additions=Object.entries(classLoot).flatMap(([school,bySlot],group)=>bySlot[slot].map(([title,type,material,special],index)=>{
      const tier=[0,1,2,3+(group%2)][index];
      const palette=equipmentSchools[school];
      const pose=[125+((group+index)%3-1)*8,142,((group*3+index)%7-3)*7,1.40];
      const [lo,hi]=tiers[tier].range;
      return {name:splitTitle(title),type,material,special,pose,art:slot,school,id:'class-'+group+'-'+slot+'-'+index,
        tier,rarity:tier,slot,stat:info.stat,color:info.color,number:++number,variant:index+group,
        roll:Math.round(lo+(hi-lo)*((index*19+group*11)%61)/60),colors:palette.colors,background:palette.scene};
    }));
    return existing.concat(additions, RelicExpansion.filter(card=>card.slot===slot)).map(RelicArt.enrich).map(RelicCuration.apply);
  });
  const additions=Object.entries(RefinementData).filter(([,r])=>r.card).map(([id,r],i)=>{
    const {tier,slot,school,...meta}=r.card,[lo,hi]=tiers[tier].range;
    return {id,tier,rarity:tier,slot,school,...meta,number:101+i,variant:i,
      stat:slots[slot].stat,color:slots[slot].color,roll:lo+(i*17)%(hi-lo+1),
      colors:equipmentSchools[school].colors,art:slot,
      relicTheme:({WARRIOR:'crown',RANGER:'thorns',MAGE:'orrery',PRIEST:'dawn',OCCULTIST:'eclipse',ARTIFICER:'engine'})[school],
      affinityReason:r.reviewNote};
  });
  const catalog=legacyCatalog.concat(additions).map(c=>{
    const refinement=RefinementData[c.id];
    if(!refinement)throw Error('Missing construction: '+c.id);
    return {...c,edition:5,available:true,refinement,name:splitTitle(refinement.name),type:refinement.type,material:refinement.material};
  });
  // The experimental pieces remain readable for inventories that already own them.
  const allCatalog=[...catalog,...AtelierData.map(c=>({...c,available:false}))];
  // Normalize even archived/expanded cards without changing IDs, rolls or damage affinities.
  for (const card of allCatalog) {
    card.stat=slots[card.slot].stat;
    if(card.curse)card.curse={...card.curse,stat:statNames[card.curse.key]||card.curse.stat};
  }
  const byId=Object.fromEntries(allCatalog.map(card=>[card.id,card]));
  function selectWeight(weights,r){
    let edge=0;
    const index=weights.findIndex(weight=>r<(edge+=weight));
    return index<0?weights.length-1:index;
  }
  function finishRoll(rng=Math.random){
    const ids=Object.keys(finishes);
    return ids[selectWeight(ids.map(id=>finishes[id].chance),rng())];
  }
  function multiplier(finish,rng=Math.random){
    if(finish!=='bug')return finishes[finish].mult;
    const band=bugBands[selectWeight(bugBands.map(b=>b.chance),rng())];
    return integer(band.min,band.max,rng)/100;
  }
  function effect(card,{base=card.roll,mult=1,finish='normal',level=1}={}){
    const value=base*mult*(card.curse?1.4:1)*(1+.1*(level-1));
    return Math.min(finish==='bug'?caps.bug:caps.regular,Math.round(value));
  }
  function draw(slot,rng=Math.random){
    const available=catalog.filter(card=>card.slot===slot);
    if(!available.length)return [];
    return Array.from({length:3},()=>{
      const tier=selectWeight(rarityWeights,rng());
      const pool=available.filter(card=>card.tier===tier);
      const item=pool[Math.min(pool.length-1,Math.floor(rng()*pool.length))];
      available.splice(available.indexOf(item),1);
      const finish=finishRoll(rng),base=integer(...tiers[tier].range,rng);
      return {item,finish,base,mult:multiplier(finish,rng)};
    });
  }
  function cardFace(card,options={}){
    const finish=options.finish||'normal',base=options.base??card.roll,mult=options.mult??finishes[finish].mult;
    return face(card,{finish:finishes[finish].name,boostValue:options.boost??effect(card,{...options,finish,base,mult}),layer:options.layer});
  }
  function revealArt(item,options={}){
    const rarity=tiers[item.tier],animated=options.finish==='animated'||(item.edition===4&&['shiny','chroma','blessed'].includes(options.finish));
    return {tier:item.tier,name:item.name.join(' '),rarity:rarity.name,tone:rarity.color,effect:options.finish||'normal',cursed:!!item.curse,
      face:cardFace(item,options),
      ...(animated?{baseFace:cardFace(item,{...options,layer:'base'}),objectFace:cardFace(item,{...options,layer:'object'})}:{}),
    };
  }
  const classNames=(card,finish)=>finish+' rarity-'+card.tier+' school-'+card.school.toLowerCase()+(card.curse?' cursed':'')+(card.edition===4?' atelier':'');
  function effectMarkup(card,finish){
    const particles=(kind,count,palette)=>'<div class="card-motes '+kind+'" aria-hidden="true">'+Array.from({length:count},(_,i)=>{
      const x=7+(i*37+19)%86,y=9+(i*67+5)%82;
      return '<i style="--x:'+x+'%;--y:'+y+'%;--s:'+(1.3+(i%5)*.58)+'px;--dx:'+((i%7-3)*4)+'px;--lag:-'+((i*7%33)/10)+'s;--duration:'+(2.3+(i%8)*.21)+'s;--particle:'+palette[i%palette.length]+'"></i>';
    }).join('')+'</div>';
    const mask=card.edition===4?' style="mask-image:url('+AtelierImages[card.id]+');mask-size:96% 65.75%;mask-position:50% 25.33%;mask-repeat:no-repeat"':'';
    let out='<span class="card-effect" aria-hidden="true"'+mask+'></span>';
    if(finish==='animated')out+=particles('animated',17,['#c6dcff','#d7bbff','#f7eced']);
    if(finish==='blessed')out+='<span class="card-bless-seal" aria-hidden="true"></span>'+particles('benie',29,['#ffda84','#fff6d4','#f6a954']);
    if(finish==='bug')out+='<div class="card-glitch" aria-hidden="true">'+Array.from({length:13},(_,i)=>
      '<i style="--x:'+((i*29)%79)+'%;--y:'+(9+(i*37)%80)+'%;--w:'+(15+(i*23)%52)+'%;--h:'+(1+(i%4)*2)+'px;--shift:'+((i%2?-1:1)*(9+i%5*3))+'px;--lag:-'+((i*8)%31)/10+'s"></i>').join('')+'</div>';
    if(card.curse)out+=particles('maudite',26,['#ff689b','#a785f8','#ffb0bb']);
    return out;
  }
  return {tiers,slots,catalog,allCatalog,byId,finishes,bugBands,caps,rarityWeights,draw,effect,finishRoll,multiplier,cardFace,classNames,effectMarkup,revealArt,percent};
})();
