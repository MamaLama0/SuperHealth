'use strict';

const equipmentSchools = {
  WARRIOR: {name:'Guerrier', element:'Mêlée', mark:'◆', colors:['#a7a4ad','#f4dfb4','#51475b'], scene:'atelier'},
  MAGE: {name:'Mage', element:'Arcaniste', mark:'✦', colors:['#7896d9','#c2edff','#333d7a'], scene:'orbit'},
  PRIEST: {name:'Prêtre', element:'Divine', mark:'✚', colors:['#c39d58','#fff2b5','#77553f'], scene:'sun'},
  RANGER: {name:'Rôdeur', element:'À distance', mark:'❧', colors:['#739574','#d3e5a7','#3c5950'], scene:'grove'},
  OCCULTIST: {name:'Occultiste', element:'Arcaniste', mark:'☾', colors:['#926dac','#edb5e6','#503464'], scene:'abyss'},
  ARTIFICER: {name:'Artificier', element:'Arcaniste', mark:'ϟ', colors:['#5baca9','#d3f7d6','#306065'], scene:'glass'},
};

// Each group contributes four distinct items in every body location.
const classLoot = {
  WARRIOR: {
    weapon: [['Gantelet du mauvais jour','Gantelet','Acier piqué','gauntlet'],['Rapière de l’écuyère','Rapière','Acier bleui','rapier'],['Marteau des portes basses','Marteau','Fer noir et frêne','club'],['Lame de la première garde','Épée dentelée','Acier damassé','serrated']],
    chest: [['Cotte de la recrue','Cotte de mailles','Fer et toile','mail'],['Gambison des cent reprises','Gambison','Lin rembourré','quilt'],['Plastron du bélier','Cuirasse à cornes','Bronze et cuir','ribcage'],['Surcot des deux armées','Surcot','Acier et brocart','surcoat']],
    guard: [['Boucle de la garde basse','Boucle','Fer forgé','ringguard'],['Pavois du pont-levis','Pavois','Frêne cerclé','kite'],['Égide du cercle d’armes','Égide','Acier damassé','wheel'],['Bastion de la passe étroite','Bouclier à pointes','Acier et grenat','spikeguard']],
    legs: [['Solerets des faubourgs','Solerets','Acier et cuir','lost-shoe'],['Cuissards de l’arène','Cuissards','Bronze noirci','knees'],['Jambières du mur gris','Jambières','Plaques et lin','waders'],['Étriers du rempart','Étriers','Argent et corde','stirrups']],
  },
  MAGE: {
    weapon: [['Baguette du premier sort','Baguette','Houx et quartz','wand'],['Gants des étincelles sages','Gants runiques','Soie et argent','gauntlet'],['Orbe du poids perdu','Orbe','Cristal suspendu','orb'],['Bâton des neuf orbites','Bâton astral','If et saphir','astrolabe']],
    chest: [['Robe de l’apprenti sans manche','Robe de mage','Lin indigo','mage-robe'],['Veste du ciel de poche','Veste astrale','Soie bleue','surcoat'],['Manteau de la comète pâle','Manteau à queue','Velours lunaire','tailcoat'],['Robe des cartes du ciel','Robe d’astromancien','Soie et fils d’argent','mage-robe']],
    guard: [['Craie du cercle imparfait','Sceau de protection','Calcaire et étain','seal'],['Prisme du second reflet','Prisme','Verre poli','prism'],['Astrolabe pare-étincelle','Astrolabe','Laiton et améthyste','astrolabe'],['Bulle qui se souvient','Orbe de garde','Verre astral','orb']],
    legs: [['Chausses du copiste astral','Chausses','Soie et laine','trousers'],['Babouches du pas flottant','Babouches','Soie et argent','curl-shoe'],['Échasses de l’apesanteur','Échasses','If spectral','stilts'],['Patins du ciel ouvert','Patins','Acier stellaire','skates']],
  },
  PRIEST: {
    weapon: [['Croix du veilleur','Croix de prêtre','Chêne et fer','cross'],['Encensoir de la nuit claire','Encensoir','Laiton et ambre','censer'],['Cloche de la neuvième aube','Cloche','Bronze et nacre','bell'],['Croix du jour sans fin','Croix reliquaire','Or et quartz','cross']],
    chest: [['Aube du petit matin','Aube','Lin écru','surcoat'],['Étole de la porte haute','Étole','Soie et fils d’or','stole'],['Chasuble du dernier pèlerin','Chasuble','Brocart pourpre','reliquary'],['Mantelet des sept cloches','Mantelet liturgique','Velours et argent','mantle']],
    guard: [['Médaille du détour','Médaillon','Étain et chanvre','seal'],['Chapelet des pas retrouvés','Chapelet','Nacre et cuivre','talisman'],['Vitrail du refuge','Vitrail protecteur','Verre et plomb','window'],['Reliquaire du soleil levé','Reliquaire','Or repoussé','reliquary']],
    legs: [['Sandales du cloître mouillé','Sandales','Cuir de chèvre','sandals'],['Chaussons du prieur distrait','Chaussons','Laine et soie','slippers'],['Bandes du chemin blanc','Bandes bénies','Lin et ambre','wraps'],['Bottes des pèlerins invisibles','Bottes de procession','Cuir et lumière','wingboot']],
  },
  RANGER: {
    weapon: [['Coudières du chemin creux','Coudières de rôdeur','Cuir cru et frêne','elbows'],['Sarbacane des pluies fines','Sarbacane','Bambou et corde','pipe'],['Arc à deux serments','Arc recourbé','If et corne','twinbow'],['Faucon de la main verte','Faucon d’arc','Bois, plumes et jade','falcon']],
    chest: [['Veste du chasseur de haies','Veste de rôdeur','Cuir tanné','vest'],['Cape du feu sans fumée','Cape de piste','Feutre et ronces','leaf-cape'],['Gilet aux dix fioles','Gilet de piste','Lin et verre vert','vialvest'],['Manteau du cerf d’automne','Manteau de chasse','Laine, cuir et bois','leaf-cape']],
    guard: [['Nasse du mauvais côté','Nasse','Osier et fil','basket'],['Filet des trois gués','Filet de garde','Chanvre et os','net'],['Ailes du premier guetteur','Ailes pare-pluie','Plumes et cuir','falcon'],['Carapace de la source moussue','Carapace','Écaille et jade','shell']],
    legs: [['Guêtres des flaques royales','Guêtres','Chanvre huilé','wraps'],['Crampons du sentier tordu','Crampons','Fer et bois','crampons'],['Pattes du renard de brume','Bottes fendues','Cuir et corne','hoof'],['Raquettes du dernier hiver','Raquettes','Frêne enchanté','snowshoe']],
  },
  OCCULTIST: {
    weapon: [['Grimoire des pages muettes','Grimoire d’occultiste','Cuir noir et cire','chainbook'],['Gants du murmure','Gants sigillés','Velours sombre','gauntlet'],['Dague de la lune enterrée','Dague rituelle','Obsidienne','kris'],['Codex du témoin aveugle','Codex enchaîné','Os et argent noir','chainbook']],
    chest: [['Robe du clerc sans ombre','Robe d’occultiste','Lin fumé','mage-robe'],['Capuche aux yeux de verre','Capuche','Soie et opale','hood'],['Manteau de la treizième porte','Manteau des ombres','Velours et obsidienne','tailcoat'],['Cage du pacte silencieux','Cuirasse rituelle','Os et cuivre','ribcage']],
    guard: [['Masque du témoin absent','Masque de garde','Os et cire','mask'],['Miroir qui ne pardonne pas','Miroir pare-sort','Verre fumé','mirror'],['Scellé des deux ombres','Sceau des ombres','Obsidienne et argent','seal'],['Œil du puits sans fond','Œil protecteur','Améthyste et plomb','eye']],
    legs: [['Chaussures du pas sans bruit','Souliers pointus','Velours et cire','curl-shoe'],['Jarretières du cercle fermé','Jarretières rituelles','Soie noire','garters'],['Bottes de la tombe ouverte','Bottes griffues','Acier noir et corne','claws'],['Sabots du spectre de craie','Sabots fendus','Os et verre noir','hoof']],
  },
  ARTIFICER: {
    weapon: [['Bobine du premier essai','Bobine à arc','Cuivre et céramique','coil'],['Clé du laboratoire fermé','Clé à cliquet','Laiton et acier','keyblade'],['Gantelet du coup de tonnerre','Gantelet électrique','Fer et cuivre','gauntlet'],['Fourche des éclairs en cage','Fourche d’orage','Alliage et quartz','trident']],
    chest: [['Tablier aux poches fumantes','Tablier','Cuir brûlé','apron'],['Gilet des six batteries','Gilet d’artificier','Cuivre et toile','vialvest'],['Harnais du courant inversé','Harnais de bobines','Cuir, cuivre et verre','harness'],['Plastron du laboratoire volant','Plastron mécanique','Acier bleui','coil-armor']],
    guard: [['Cadran du feu de poche','Cadran','Étain et verre','coil'],['Paratonnerre en boîte','Boîte de décharge','Cuivre martelé','battery'],['Disque de la machine muette','Disque à engrenage','Laiton et acier','wheel'],['Vitrail de la foudre captive','Vitrail électrique','Quartz et cuivre','window']],
    legs: [['Crampons du sol aimanté','Crampons','Fer et cuivre','crampons'],['Patins du couloir interdit','Patins à roulettes','Étain et cuir','roller'],['Ressorts du saut dirigé','Bottes à ressort','Acier et caoutchouc','springboot'],['Bottes des nuages bas','Bottes-fusées','Cuivre et verre','jetboot']],
  },
};

const schoolForms = {
  MAGE:['scepter','glass','prism','mirror','staff','orb','robe','sickle','skates'],
  PRIEST:['reliquary','candlestick','talisman','wraps','sandals','bell-boot'],
  RANGER:['bow','crossbow','spear','scythe','net','basket','snowshoe','mantle','hoof','stilts'],
  OCCULTIST:['tomeblade','book','ribcage','book-armor','eye','cocoon','swarm','claws','flamberge','greatsword'],
  ARTIFICER:['wheel','lantern','springboot','roller','jetboot','bench','pan'],
};

function schoolFor(item, slot, rarity, index){
  const shape=item.special||item.art;
  if(slot==='weapon'&&['knife','rapier','axe','mace','scythe','spear','greatsword','flamberge'].includes(shape))return 'WARRIOR';
  if(slot==='weapon'&&shape==='bow')return 'RANGER';
  if(slot==='weapon'&&shape==='scepter')return 'MAGE';
  const thematic=Object.keys(schoolForms).find(key=>schoolForms[key].includes(shape));
  if(thematic)return thematic;
  const generic=Object.keys(equipmentSchools);
  return generic[(index*5+rarity*3+Object.keys(classLoot.WARRIOR).indexOf(slot))%generic.length];
}

function classSilhouette(card, fill){
  const hi=card.colors[1],dark=card.colors[2];
  const p=(d,c=fill,w=2)=>'<path d="'+d+'" fill="'+c+'" stroke="'+hi+'" stroke-width="'+w+'" stroke-linecap="round" stroke-linejoin="round"/>';
  const l=(d,c=hi,w=1.7)=>'<path d="'+d+'" fill="none" stroke="'+c+'" stroke-width="'+w+'" stroke-linecap="round"/>';
  const c=(x,y,r,ink=dark)=>'<circle cx="'+x+'" cy="'+y+'" r="'+r+'" fill="'+ink+'" stroke="'+hi+'" stroke-width="1.4"/>';
  const gem=(x,y,r)=>p('M'+x+' '+(y-r)+'l'+(r*.7)+' '+r+'-'+(r*.7)+' '+r+'-'+(r*.7)+' -'+r+'Z',hi);
  switch(card.special){
    case 'gauntlet':return p('M-40 64L-49 30-45-12-62-27-55-42-32-33-34-63-20-70-11-61-6-76 7-73 13-60 20-70 32-66 35-51 44-55 52-45 45 10 38 29 39 65Z')+p('M-39 27L0 9 40 26 38 61H-37Z',dark)+l('M-39 44L0 27 37 44 M-34-32L37-29 M-28-46v16m14-22v22m15-22v22m15-19v18m15-15v15',hi,2.5)+gem(0,46,11);
    case 'rapier':return p('M-5-78L0-91 5-78 4 38H-4Z')+p('M-32 24Q0 48 33 22L27 40Q0 60-26 41Z',dark)+p('M-6 43H6V78L0 86-6 78Z')+l('M0-78V21M-5 52l10 4m-10 5 10 4',hi,2);
    case 'serrated':case 'kris':return p('M0-85Q-23-63-8-50L-20-37-4-27-12-11-6 17H6L17 1 8-11 24-24 12-36 22-51 7-63Z')+p('M-30 14L0 24 32 14 23 32H-23Z',dark)+p('M-6 32H6L9 73 0 83-9 73Z')+l('M0-72L6-48-1-29 4-12 0 14',hi,2.5)+gem(0,29,7);
    case 'quilt':return p('M-24-60L-51-51-67-24-43-9-35-32-40 57 0 68 40 57 35-32 43-9 67-24 51-51 24-60 0-41Z')+l('M-34-37L37 51M-33-8L31 66M-40 25l37 38M-25-47l61 72M34-37L-37 51M33-8l-64 74M40 25L3 63M25-47l-61 72',dark,2.4)+l('M0-41V65M-35 48l24 5m46-5-24 5',hi,2);
    case 'surcoat':return p('M-23-68L-54-50-63-20-43-14-32-33-38 8-54 64-19 74 0 56 19 74 54 64 38 8 32-33 43-14 63-20 54-50 23-68 0-46Z')+p('M-20-60L0-44 20-60 17-26 26 9 0 23-26 9-17-26Z',dark)+l('M0-44V56M-47 57l26 7m68-7-26 7M-40-39l-12 19m92-19 12 19',hi,2.1)+gem(0,3,10);
    case 'ringguard':return c(0,0,60,fill)+c(0,0,50,dark)+p('M-29-29L0-45 29-29V23L0 45-29 23Z')+c(0,0,17,hi)+c(0,0,7,fill)+l('M-45-45l9 9m81-9-9 9m-81 81 9-9m81 9-9-9',dark,3);
    case 'spikeguard':return p('M0-73L12-54 41-59 42-39 68-27 51-9 57 29 38 28 28 50 0 72-28 50-38 28-57 29-51-9-68-27-42-39-41-59-12-54Z',dark)+p('M-40-38L0-50 40-38 37 20 0 53-37 20Z')+l('M0-45v94M-37-29l74 45m0-45-74 45',dark,3)+gem(0,-3,15);
    case 'wand':return p('M-6-57L0-67 7-57 5 71 0 84-5 71Z')+gem(0,-68,16)+p('M-14-37H14V-22H-14Z',dark)+l('M-6 25l11-7m-11 17 11-7m-11 17 11-7m-11 17 11-7',hi,2)+l('M-22-64l-10 8m56-20 9-5m-42-7-4-9',hi,2);
    case 'orb':return c(0,-10,52,dark)+c(0,-10,42,fill)+p('M-51 11L-40 38-14 49-6 70H6L14 49 40 38 51 11 22 30-22 30Z')+l('M-40-19Q-14-50 24-34M-33 14Q4 43 36 10 M-18 53h36',hi,2.9)+gem(0,-10,17);
    case 'astrolabe':return p('M-6-20H6L5 75 0 86-5 75Z',dark)+c(0,-44,34,fill)+c(0,-44,25,dark)+l('M0-78V-10M-34-44H34M-21-67l42 46m0-46-42 46',hi,2)+gem(0,-44,12);
    case 'mage-robe':return p('M-23-64L-45-50-74-1-46 22-30-4-27 15-52 71-23 79 0 63 23 79 52 71 27 15 30-4 46 22 74-1 45-50 23-64 0-43Z')+p('M-21-56L0-41 21-56 13-22 26 58 0 69-26 58-13-22Z',dark)+l('M0-39v102M-58-8l14 10m102-10L44 2M-41 61l17 5m65-5-17 5M-27 9h54',hi,2.3)+gem(0,-12,10);
    case 'tailcoat':return p('M-31-71L-56-46-65 2-43 15-33-19-29 16-50 69-17 79 0 46 17 79 50 69 29 16 33-19 43 15 65 2 56-46 31-71 0-41Z')+p('M-27-66L0-40 27-66 19-22 0 18-19-22Z',dark)+l('M-39 56l22 8m56-8-22 8M0-12v50M-30 13l14 8m46-8-14 8',hi,2.4)+[-8,8,24].map(y=>c(5,y,2.5,hi)).join('');
    case 'seal':return c(0,0,57,fill)+c(0,0,46,dark)+c(0,0,35,fill)+l('M0-50v100m-50-50h100M-24-24l48 48m0-48-48 48',hi,2)+gem(0,0,13);
    case 'curl-shoe':return p('M-39-63L11-57 14-12 28 13 50 24 65 10 73 24 65 48 49 60-40 58-54 45-52 11Z')+p('M-50 44L38 47 62 32 68 41 49 60-40 58-54 45Z',dark)+l('M-35-40l44 8m-45 7 47 8m-50 9 52 8M18 30Q49 48 64 23',hi,2.1)+gem(0,25,9);
    case 'cross':return p('M-12-78H12V-42H47V-19H12V70L0 84-12 70V-19H-47V-42H-12Z')+p('M-5-70H5V-34H39V-27H5V61H-5V-27H-39V-34H-5Z',dark)+gem(0,-31,11)+l('M0 32v27M-27-31h10m44 0H17',hi,2.2);
    case 'censer':return l('M0-76L-37 13M0-76L37 13M0-76V10',hi,3)+c(0,-76,8,fill)+p('M-43 13Q0-12 43 13L31 45Q0 70-31 45Z')+p('M-45 12H45V23H-45Z',dark)+l('M-26 24q26 22 52 0m-41 15q15 12 30 0',hi,2.2)+gem(0,42,8);
    case 'bell':return p('M-12-69H12L16-49Q34-36 36 4L54 34V49H-54V34L-36 4Q-34-36-16-49Z')+p('M-54 32Q0 42 54 32V49H-54Z',dark)+c(0,55,12,fill)+l('M-17-36Q-29-7-29 22M17-36Q29-7 29 22 M-48 39Q0 50 48 39',hi,2.2);
    case 'stole':return p('M-44-40Q-47-74 0-75Q47-74 44-40L29-17 32 65 10 78-1-5-12 78-37 65-31-15Z')+p('M-35-43Q-31-63 0-63Q31-63 35-43L23-26Q0-44-23-26Z',dark)+l('M-30-11l-2 58m59-58 2 58M-29 60l15 5m45-5-15 5',hi,2.6)+gem(-25,31,8)+gem(22,31,8);
    case 'window':return p('M-50 64V-22Q-50-51 0-79Q50-51 50-22V64Z',dark)+p('M-40 53V-22Q-40-44 0-66Q40-44 40-22V53Z')+l('M0-64V52M-39-21h78M-38 23h76M-38-20L0 5 38-20M-38 22L0 5 38 22',hi,2.7)+gem(0,5,15);
    case 'elbows':return [-1,1].map(side=>'<g transform="translate('+(side*32)+' '+(side*9)+')">'+p('M-26-47L0-62 26-47 29-4 21 42 0 59-21 42-29-4Z',dark)+p('M0-40L24-15 19 16 0 35-19 16-24-15Z')+l('M-19-40l39 14m-47 38 49 15M-17 36l34 10',hi,2.2)+gem(0,-8,7)+'</g>').join('');
    case 'twinbow':return p('M-38-75Q44-69 27 0Q44 69-38 75L-25 63Q23 54 14 0Q23-54-25-63Z')+l('M-37-73L-17 0-37 73M-17 0H49M38-9l11 9-11 9',hi,2.4)+gem(19,0,8);
    case 'falcon':return p('M0-13Q-22-55-77-58L-54-32-74-36-42-11-57-11-28 6-38 8-11 30 0 65 11 30 38 8 28 6 57-11 42-11 74-36 54-32 77-58Q22-55 0-13Z')+p('M-16-2L0-24 16-2 0 39Z',dark)+l('M-64-48L-16 14m80-62L16 14M-57-28l26 20m88-20L31-8',hi,2.1)+gem(0,-6,7);
    case 'leaf-cape':return p('M-30-66L-59-49-75 51-52 49-46 66-24 61 0 79 24 61 46 66 52 49 75 51 59-49 30-66 0-46Z')+p('M-25-60L0-45 25-60 18-19 28 60 0 72-28 60-18-19Z',dark)+l('M-48-41L-62 42m110-83 14 83M-35-36l-8 86m78-86 8 86M0-38v100M-60 30l17 10m103-10-17 10',hi,2.1)+gem(0,-25,8);
    case 'vialvest':return p('M-32-61L-53-43-38-18-46 57 0 69 46 57 38-18 53-43 32-61 0-40Z',dark)+p('M-42 15L42-21 45-7-43 30Z')+[-25,-8,9,26].map((x,i)=>p('M'+x+' '+(-8-i*7)+'h10v26h-10Z',i%2?fill:hi)).join('')+l('M0-39v19m0 51v35M-38 46h76',hi,2.1);
    case 'crampons':return [-1,1].map(side=>'<g transform="translate('+(side*32)+' '+(side*8)+')">'+p('M-23-54L21-48 20 37 13 50H-21L-27 33Z',dark)+p('M-26 25H25V39L13 42 7 59 0 44-8 61-13 43-24 38Z')+l('M-20-24L19-9M-22 4L18-12M-20 14L21 27',hi,2.6)+'</g>').join('');
    case 'chainbook':return p('M-52-52L-18-66 2-52 48-63 58-49 54 56 6 69-50 57Z',dark)+p('M-40-49L-13-56 3-44 42-53 43 46 5 56-39 46Z')+l('M4-44V55M-39-19l37 10 44-19M-38 23l39 9 42-13',dark,4)+gem(4,11,13)+[-35,-17,1,19,37].map(x=>c(x,38-x*.35,4,dark)).join('');
    case 'hood':return p('M-40-26Q-47-55 0-76Q47-55 40-26L68 43 40 69 0 57-40 69-68 43Z')+p('M-34-18Q-33-51 0-65Q33-51 34-18L20 27H-20Z',dark)+l('M-50 23l15 25m85-25-15 25M-25-28L-9-35m34 7L9-35',hi,2.4)+gem(0,37,9);
    case 'mask':return p('M-52-35L-27-56 0-48 27-56 52-35 43 9 24 52 0 69-24 52-43 9Z')+p('M-40-25L-22-29-9-12-18 5-36-3ZM40-25L22-29 9-12 18 5 36-3Z',dark)+l('M0-37v47M-17 34l17 10 17-10M-20 44l20 13 20-13',hi,2.2)+gem(0,-31,8);
    case 'coil':return p('M-30-72H30V-60H17V45H43V71H-43V45H-17V-60H-30Z',dark)+l('M-34-45L33-32-33-17 33-3-33 12 33 26-33 40',hi,4)+gem(0,-65,10)+gem(0,58,9);
    case 'coil-armor':return p('M-25-64L-63-40-51-13-33-25-42 57 0 73 42 57 33-25 51-13 63-40 25-64 0-45Z')+p('M-26-35H26L31 43 0 60-31 43Z',dark)+l('M-25-21L26-9-25 4 26 16-25 29 25 41M-52-39l16 10m88-10-16 10',hi,2.9)+gem(0,19,10);
    case 'battery':return p('M-34-52H34L51-34V54L35 67H-35L-51 54V-34Z',dark)+p('M-29-38H29V43H-29Z')+p('M-22-65H-9V-52H-22ZM9-65H22V-52H9Z')+l('M4-25L-11 0H8L-4 25M-42-28v73m84-73v73',hi,3.7)+gem(0,54,7);
  }
  return '';
}
