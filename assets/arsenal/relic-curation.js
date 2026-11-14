'use strict';
/** Final authored item pass. Schools are visual traditions, never combat rules.
 * Stable IDs preserve inventory and review notes when an object is redesigned. */
const RelicCuration=(()=>{
 const types={
  MELEE:{label:'Mêlée',color:'#e9ceb0',path:'M4 20L8 16M5 13L11 19M8 15L17 4L21 3L20 7L10 17M4 18L6 20'},
  RANGED:{label:'À distance',color:'#b4d89a',path:'M7 3Q22 12 7 21L12 12ZM3 12H21M17 8L21 12L17 16'},
  ARCANIST:{label:'Arcaniste',color:'#a9c9ff',path:'M12 2L19 12L12 22L5 12ZM12 6V18M8 12H16M2 4V8M0 6H4M22 16V20M20 18H24'},
  DIVINE:{label:'Divine',color:'#ffe3a5',path:'M12 7A5 5 0 1 1 11.99 7M12 1V4M12 20V23M1 12H4M20 12H23M4 4L6 6M18 18L20 20M4 20L6 18M18 6L20 4'},
 };
 const changes={};
 // id, name, actual construction, new silhouette, visual tradition, materials.
 const rows=[
 ['4-weapon-0','Hachette du guet','Hachette','hatchet','WARRIOR','Fer forgé et frêne'],
 ['9-weapon-0','Bâton du cercle des saules','Bâton druidique','staff','MAGE','Saule noueux et quartz'],
 ['11-weapon-0','Pic de guerre des montagnes','Pic de guerre','warpick','WARRIOR','Fer et cuir'],
 ['15-weapon-0','Harpon des brisants','Harpon','javelin','RANGER','Fer barbelé et corde'],
 ['16-weapon-0','Dague du veilleur gris','Dague','dagger','WARRIOR','Acier et cuir gris'],
 ['17-weapon-0','Katar des portes rouges','Katar','katar','WARRIOR','Fer, bronze et cuir'],
 ['18-weapon-0','Gourdin du garde-frontière','Gourdin',null,'WARRIOR','Chêne ferré'],
 ['19-weapon-0','Trident de la garde côtière','Trident',null,'WARRIOR','Bronze et frêne'],
 ['20-weapon-0','Flûte du barde itinérant','Flûte',null,'MAGE','Buis et bagues d’argent'],
 ['21-weapon-0','Javeline de la marche verte','Javeline','javelin','RANGER','Frêne et pointe de fer'],
 ['6-weapon-1','Masse de l’ordre du Flambeau','Masse consacrée','mace','PRIEST','Acier et laiton consacré'],
 ['15-weapon-1','Fauchon de la garde des ronces','Fauchon','falchion','WARRIOR','Acier et cuir vert'],
 ['17-weapon-1','Cloche du gardien des tombes','Cloche rituelle','bell','PRIEST','Bronze et quartz'],
 ['12-weapon-2','Maillet des gardiens du col','Marteau de guerre','hammer','WARRIOR','Acier et frêne'],
 ['4-weapon-4','Aurore, le vœu du paladin','Épée consacrée','sunblade','PRIEST','Or, acier solaire et nacre'],
 ['5-weapon-4','Les Lames du destin','Lames jumelles','twinblades','OCCULTIST','Acier lunaire et opale'],
 ['class-0-weapon-0','Gantelet de la garde de fer','Gantelet',null,'WARRIOR','Acier et cuir'],
 ['class-0-weapon-2','Marteau du bastion','Marteau de guerre','hammer','WARRIOR','Fer noir et frêne'],
 ['class-3-weapon-0','Arc court du guetteur','Arc court','shortbow','RANGER','If et corde de lin'],
 ['class-3-weapon-3','Le Faucon du dernier bois','Arc long enchanté','falconbow','RANGER','If millénaire, argent et jade'],
 ['class-5-weapon-0','Sceptre de fulguration','Sceptre runique','staff','ARTIFICER','Cuivre gravé et quartz'],
 ['class-5-weapon-1','Lame du sceau de cuivre','Lame runique','falchion','ARTIFICER','Acier bleui et runes de cuivre'],
 ['3-chest-0','Gambison du guet','Gambison','gambeson','WARRIOR','Lin matelassé et cuir'],
 ['8-chest-0','Cape du voyageur','Cape',null,'RANGER','Laine et agrafe de fer'],
 ['15-chest-0','Mantelet de la marche grise','Mantelet','cloak','RANGER','Laine grise et cuir'],
 ['16-chest-0','Tunique du novice','Tunique de mage',null,'MAGE','Lin indigo et fil d’étain'],
 ['17-chest-0','Veste du veilleur des cols','Veste de cuir',null,'RANGER','Cuir tanné et laine'],
 ['18-chest-0','Cuirasse de la garnison','Cuirasse','lamellar','WARRIOR','Lames de fer et cuir'],
 ['19-chest-0','Broigne des marches','Broigne','lamellar','WARRIOR','Cuir bouilli et écailles de fer'],
 ['20-chest-0','Manteline de l’acolyte','Manteline','cloak','PRIEST','Lin écru et laiton'],
 ['21-chest-0','Robe du scribe des runes','Robe runique',null,'MAGE','Lin bleu et fil d’argent'],
 ['6-chest-2','Brigandine du gardien des archives','Brigandine runique','lamellar','MAGE','Plaques d’acier gravées et velours'],
 ['11-chest-2','Robe du tisseur d’argent','Robe enchantée','cloak','MAGE','Soie d’araignée et argent'],
 ['8-chest-3','Cuirasse du sanctuaire','Cuirasse consacrée',null,'PRIEST','Acier, cuivre et vitrail'],
 ['5-chest-4','Le Rêve du dragon','Cuirasse à écailles','dragonscale','RANGER','Écailles de dragon et argent'],
 ['class-1-chest-0','Robe de l’apprenti','Robe de mage',null,'MAGE','Lin indigo'],
 ['class-5-chest-0','Cuirasse de l’enchanteur','Cuirasse runique','lamellar','ARTIFICER','Fer et cuivre gravé'],
 ['class-5-chest-1','Gilet aux fioles de mana','Gilet d’alchimiste',null,'ARTIFICER','Cuir, verre et cuivre'],
 ['2-guard-0','Rondache du garde des portes','Rondache','buckler','WARRIOR','Fer et chêne'],
 ['7-guard-0','Écu du Sanglier','Écu','heater','WARRIOR','Chêne peint et fer'],
 ['11-guard-0','Pavois du guet de pierre','Pavois','pavise','WARRIOR','Chêne et fer'],
 ['15-guard-0','Targe de la marche funèbre','Targe','heater','WARRIOR','Frêne et cuir noir'],
 ['16-guard-0','Bouclier du sentier des saules','Bouclier de piste','leafshield','RANGER','Saule tressé et cuir'],
 ['17-guard-0','Pavois de la garnison','Pavois','pavise','WARRIOR','Bois et bandes de fer'],
 ['18-guard-0','Sceau du premier rempart','Sceau de protection','ward','MAGE','Étain gravé et quartz'],
 ['19-guard-0','Filet du chasseur des marais','Filet de garde',null,'RANGER','Chanvre et plomb'],
 ['20-guard-0','Écu de la garde des ruelles','Écu','heater','WARRIOR','Chêne et cuir'],
 ['3-guard-1','Écu du guetteur','Écu en amande',null,'WARRIOR','Bois et bronze'],
 ['7-guard-1','Rondache des chemins de fer','Rondache','buckler','WARRIOR','Acier et bronze'],
 ['13-guard-1','Pavois de la passe close','Pavois','pavise','WARRIOR','Acier et frêne'],
 ['15-guard-1','Disque du refuge solaire','Disque consacré','sunshield','PRIEST','Argent et laiton'],
 ['16-guard-1','Targe des archers du bois profond','Targe de piste','leafshield','RANGER','If et cuir vert'],
 ['17-guard-1','Sceau du voile spectral','Sceau protecteur','ward','OCCULTIST','Argent et cristal fumé'],
 ['9-guard-3','Le Rempart des sept portes','Pavois enchanté','pavise','MAGE','Acier noir et runes d’argent'],
 ['class-0-guard-2','Égide du cercle d’armes','Rondache','buckler','WARRIOR','Acier damassé'],
 ['class-1-guard-0','Sceau du cercle de garde','Sceau de protection',null,'MAGE','Étain et quartz'],
 ['class-3-guard-0','Targe des guetteurs','Targe de piste','leafshield','RANGER','If et cuir'],
 ['class-5-guard-1','Égide de fulguration','Égide runique','ward','ARTIFICER','Cuivre, quartz et runes'],
 ['1-legs-0','Bottes du pèlerin','Bottes de marche','leather','WARRIOR','Cuir et boucles de fer'],
 ['6-legs-0','Solerets de l’écuyer','Solerets','plate','WARRIOR','Acier riveté et cuir'],
 ['9-legs-0','Guêtres de la garde des cols','Guêtres','rangerboots','RANGER','Cuir huilé et laine'],
 ['12-legs-0','Bandes du marcheur','Bandes de marche',null,'RANGER','Lin et cuir'],
 ['15-legs-0','Bottes du jeune arcaniste','Bottes runiques','rune','MAGE','Cuir bleu et fil d’argent'],
 ['16-legs-0','Bottes du pisteur des marais','Bottes de piste','rangerboots','RANGER','Cuir huilé'],
 ['17-legs-0','Chausses de la garde frontalière','Chausses renforcées',null,'WARRIOR','Toile et cuir'],
 ['18-legs-0','Sandales du temple de basalte','Sandales',null,'PRIEST','Cuir et bronze'],
 ['19-legs-0','Grèves du veilleur','Grèves','plate','WARRIOR','Fer et sangles de cuir'],
 ['20-legs-0','Bottes du traqueur des neiges','Bottes fourrées','fur','RANGER','Cuir et fourrure'],
 ['21-legs-0','Bottes du guetteur des rives','Bottes de piste','rangerboots','RANGER','Cuir huilé et bronze'],
 ['3-legs-1','Sandales du chemin des ermites','Sandales',null,'PRIEST','Cuir tressé'],
 ['8-legs-1','Guêtres du chasseur des marais','Guêtres','rangerboots','RANGER','Cuir tanné et lin'],
 ['9-legs-1','Bottes du héraut royal','Bottes de cavalier','leather','WARRIOR','Cuir rouge et laiton'],
 ['13-legs-1','Grèves du gardien d’airain','Grèves articulées','plate','WARRIOR','Bronze et cuir'],
 ['14-legs-1','Bottes de la sentinelle nocturne','Bottes de piste','rangerboots','RANGER','Cuir noir et argent'],
 ['15-legs-1','Solerets de la garde des marées','Solerets','scalegreaves','WARRIOR','Acier et nacre'],
 ['16-legs-1','Bottes du pas de brume','Bottes enchantées','rune','MAGE','Cuir gris et cristal'],
 ['17-legs-1','Chausses du marcheur des ombres','Chausses enchantées',null,'OCCULTIST','Velours et fil d’argent noir'],
 ['5-legs-2','Bottes du lac gelé','Bottes de piste','fur','RANGER','Cuir blanc, fourrure et argent'],
 ['10-legs-2','Grèves des chevaliers du corail','Grèves à écailles','scalegreaves','WARRIOR','Corail et acier'],
 ['12-legs-2','Bottes du silence','Bottes de piste','rangerboots','RANGER','Cuir noir et velours'],
 ['13-legs-2','Grèves du cavalier d’argent','Grèves de cavalerie','plate','WARRIOR','Acier et argent'],
 ['8-legs-3','Les Pas du vent lointain','Bottes runiques','windboots','MAGE','Cuir blanc et cristal du vent'],
 ['9-legs-3','Les Pas entre les mondes','Bottes de translation','rune','MAGE','Velours et obsidienne'],
 ['5-legs-4','Les Pas du roi noyé','Grèves des profondeurs','scalegreaves','OCCULTIST','Nacre et acier des profondeurs'],
 ['class-0-legs-0','Solerets des faubourgs','Solerets','plate','WARRIOR','Acier et cuir'],
 ['class-0-legs-2','Jambières du mur gris','Jambières','plate','WARRIOR','Plaques d’acier et lin'],
 ['class-0-legs-3','Grèves du dernier rempart','Grèves de cavalerie','plate','WARRIOR','Acier damassé et argent'],
 ['class-1-legs-2','Bottes du pas suspendu','Bottes runiques','windboots','MAGE','Cuir et cristal astral'],
 ['class-1-legs-3','Les Pas de l’horizon','Bottes de translation','rune','MAGE','Argent, cuir et cristal astral'],
 ['class-2-legs-1','Bottes du prieur','Bottes de procession','pilgrimboots','PRIEST','Cuir blanc et laiton'],
 ['class-2-legs-0','Sandales du cloître','Sandales',null,'PRIEST','Cuir de chèvre'],
 ['class-3-legs-0','Guêtres des sentinelles','Guêtres',null,'RANGER','Chanvre huilé et cuir'],
 ['class-3-legs-3','La Marche du dernier hiver','Bottes de traque','fur','RANGER','Cuir, fourrure blanche et jade'],
 ['class-4-legs-1','Grèves du cercle fermé','Grèves rituelles','rune','OCCULTIST','Fer noir et argent'],
 ['class-5-legs-1','Grèves des runes de cuivre','Grèves runiques','rune','ARTIFICER','Acier et cuivre'],
 ['class-5-legs-2','Bottes du pas fulgurant','Bottes runiques','windboots','ARTIFICER','Cuir et quartz'],
 ['class-5-legs-3','Les Bottes du navigateur céleste','Grèves runiques','windboots','ARTIFICER','Orichalque et cristal'],
 ['relic-artificer-legs-v2','Les Pas du ciel profond','Grèves du vent','windboots','ARTIFICER','Orichalque et cristal du vent'],
 ];
 for(const [id,name,type,form,school,material] of rows)changes[id]={name:[name],type,school,material,...(form?{fantasyForm:form,special:'fantasy-'+form,design:null}:{}),reviewRevision:3};
 const dual={
  '0-weapon-3':['MELEE','ARCANIST'],'1-weapon-3':['MELEE','ARCANIST'],'3-weapon-3':['RANGED','ARCANIST'],
  '5-weapon-3':['MELEE','DIVINE'],'7-weapon-3':['MELEE','ARCANIST'],'8-weapon-3':['MELEE','ARCANIST'],
  '1-weapon-4':['MELEE','ARCANIST'],'2-weapon-4':['MELEE','ARCANIST'],'4-weapon-4':['MELEE','DIVINE'],
  '5-weapon-4':['MELEE','ARCANIST'],'class-3-weapon-3':['RANGED','ARCANIST'],'class-5-weapon-3':['MELEE','ARCANIST'],
  'relic-warrior-weapon-v2':['MELEE','DIVINE'],'relic-artificer-weapon-v2':['MELEE','ARCANIST'],
  '1-chest-3':['MELEE','DIVINE'],'3-chest-3':['MELEE','DIVINE'],'6-chest-3':['MELEE','ARCANIST'],
  '0-chest-4':['MELEE','ARCANIST'],'2-chest-4':['MELEE','ARCANIST'],'3-chest-4':['MELEE','ARCANIST'],
  '5-chest-4':['RANGED','ARCANIST'],'class-3-chest-3':['RANGED','ARCANIST'],'relic-ranger-chest-v2':['RANGED','ARCANIST'],
  'relic-artificer-chest-v2':['MELEE','ARCANIST'],'relic-occultist-chest-v2':['MELEE','ARCANIST'],
  '0-guard-3':['MELEE','DIVINE'],'6-guard-3':['MELEE','DIVINE'],'9-guard-3':['MELEE','ARCANIST'],
  '0-guard-4':['MELEE','DIVINE'],'3-guard-4':['MELEE','ARCANIST'],'class-3-guard-3':['RANGED','ARCANIST'],
  'relic-warrior-guard-v2':['MELEE','DIVINE'],'relic-artificer-guard-v2':['MELEE','ARCANIST'],
  '3-legs-3':['MELEE','ARCANIST'],'5-legs-3':['MELEE','DIVINE'],'0-legs-4':['MELEE','DIVINE'],
  '1-legs-4':['MELEE','DIVINE'],'5-legs-4':['MELEE','ARCANIST'],
 };
 const single={
  'class-4-weapon-2':'ARCANIST','class-5-weapon-1':'ARCANIST','6-weapon-1':'DIVINE','4-weapon-3':'DIVINE','0-weapon-4':'ARCANIST','3-weapon-4':'MELEE',
  '0-chest-3':'DIVINE','4-chest-4':'DIVINE','1-chest-4':'MELEE',
  '1-guard-4':'ARCANIST','2-guard-4':'MELEE','4-guard-4':'ARCANIST','5-guard-4':'ARCANIST',
  '2-legs-4':'ARCANIST','3-legs-4':'MELEE','4-legs-4':'DIVINE',
  'relic-artificer-guard-v2':'ARCANIST',
 };
 const ranged=new Set(['bow','crossbow','twinbow','falcon','shortbow','falconbow','javelin']);
 const melee=new Set(['knife','rapier','axe','mace','scythe','spear','flamberge','greatsword','pick','sickle','club','trident','hook','whip','flail','halberd','serrated','kris','hatchet','warpick','dagger','katar','falchion','hammer','twinblades']);
 const arcane=new Set(['scepter','wand','orb','astrolabe','staff','book','chainbook','tomeblade','lute','coil','prism','mirror','eye','swarm']);
 const sacred=new Set(['cross','censer','bell','talisman','reliquary','window','sunblade','sunshield','pilgrimboots']);
 const schoolType={WARRIOR:'MELEE',RANGER:'RANGED',MAGE:'ARCANIST',PRIEST:'DIVINE',OCCULTIST:'ARCANIST',ARTIFICER:'ARCANIST'};
 function primary(c){
  const shape=c.fantasyForm||c.special||c.art;
  // Purpose-built rows and visual collections carry an explicit role (e.g. archer's bracers).
  if(changes[c.id]||c.id.startsWith('class-')||c.design){
   if(c.slot==='weapon'&&melee.has(shape))return 'MELEE';
   return schoolType[c.school];
  }
  if(c.slot==='weapon'){
   if(ranged.has(shape)||shape==='pipe'&&/sarbacane/i.test(c.type))return 'RANGED';
   if(melee.has(shape))return 'MELEE';
   if(sacred.has(shape))return 'DIVINE';
   if(arcane.has(shape)||shape==='pipe')return 'ARCANIST';
   throw Error('Unaudited weapon: '+c.id+' / '+shape);
  }
  if(arcane.has(shape)||['robe','cocoon','book-armor','mage-robe'].includes(shape))return 'ARCANIST';
  if(sacred.has(shape))return 'DIVINE';
  if(['cloak','mantle','vest','net','shell','carapace','hoof','wraps','crampons','claws'].includes(shape))return 'RANGED';
  if(shape==='lantern')return c.tier>=3?'DIVINE':'ARCANIST';
  if(c.slot==='legs'&&/guêtres|bottes/i.test(c.type))return 'RANGED';
  return 'MELEE'; // Ordinary plate, mail, shields and greaves remain physical gear.
 }
 const setNames=[
  ['Guet de fer','Arène rouge','Bélier gris','Dernier rempart','Griffon royal'],
  ['Premier cercle','Veille astrale','Comète pâle','Cartographe du ciel','Neuf mondes'],
  ['Gardiens du cloître','Chemin des pèlerins','Sanctuaire blanc','Sept cloches','Première aurore'],
  ['Guetteurs des haies','Sentier des ronces','Premier guetteur','Roi des bois','Forêt ancienne'],
  ['Veilleurs sans ombre','Murmure voilé','Lune enterrée','Pacte silencieux','Noms perdus'],
  ['Forge des runes','Sceau de cuivre','Fulguration','Navigateur céleste','Arpenteurs d’airain'],
 ];
 const schoolOrder=['WARRIOR','MAGE','PRIEST','RANGER','OCCULTIST','ARTIFICER'];
 function setFor(c){
  const match=/^class-(\d)-[a-z]+-(\d)$/.exec(c.id);
  if(match)return {id:`set-${match[1]}-${match[2]}`,name:setNames[+match[1]][+match[2]]};
  if(c.id.startsWith('relic-')){const group=schoolOrder.indexOf(c.school);return {id:`set-${group}-signature`,name:setNames[group][4]};}
  return null;
 }
 function apply(card){
  const c={...card,...changes[card.id]},shape=c.fantasyForm||c.special||c.art;
  c.damageTypes=dual[c.id]||[single[c.id]||primary(c)];
  c.set=setFor(c);
  c.signature=c.id.startsWith('relic-');
  c.affinityReason=c.damageTypes.length===2?'Construction martiale et enchantement actif.':({MELEE:'Équipement de combat rapproché.',RANGED:'Équipement de tir ou de traque.',ARCANIST:'Focaliseur, runes ou enchantement arcanique.',DIVINE:'Relique ou équipement consacré.'})[c.damageTypes[0]];
  if(!c.id.startsWith('class-')&&!c.design&&!changes[c.id]){
   const themeType=c.damageTypes.find(t=>t==='DIVINE'||t==='ARCANIST')||c.damageTypes[0];
   c.school=({MELEE:'WARRIOR',RANGED:'RANGER',DIVINE:'PRIEST',ARCANIST:['MAGE','OCCULTIST','ARTIFICER'].includes(c.school)?c.school:'MAGE'})[themeType];
   c.relicTheme=({WARRIOR:'crown',RANGER:'thorns',PRIEST:'dawn',MAGE:'orrery',OCCULTIST:'eclipse',ARTIFICER:'engine'})[c.school];
  }
  // Physical replacements get material colors; they no longer inherit a random magic palette.
  if(changes[c.id])c.colors=equipmentSchools[c.school].colors;
  return c;
 }
 function icon(type,x=0,y=0,size=24){const t=types[type];return `<svg x="${x}" y="${y}" width="${size}" height="${size}" viewBox="-2 -2 28 28" role="img" aria-label="${t.label}" data-damage="${type}"><title>${t.label}</title><path d="${t.path}" fill="none" stroke="${t.color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`;}
 return {apply,types,icon,changes};
})();
