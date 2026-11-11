'use strict';
const WeaponStudy=(()=>{
  const tiers=[
    {name:'Commune',mark:'◆',color:'#bcc3ca',range:[25,45],count:15},
    {name:'Peu commune',mark:'◆◆',color:'#88d6ab',range:[50,80],count:13},
    {name:'Rare',mark:'◆◆◆',color:'#8fcaff',range:[90,130],count:10},
    {name:'Épique',mark:'✦',color:'#e5a4ff',range:[150,210],count:8},
    {name:'Légendaire',mark:'♛',color:'#f9d281',range:[240,330],count:4},
  ];
  const cards=[
    {id:'01',name:['Couteau de veillée'],type:'Couteau',material:'Fer noir et cuir',tier:0,roll:27,art:'knife',pose:[138,153,49,1.18],colors:['#8d9aa3','#ddd4c2','#535563'],background:'atelier',note:'Petit fer ébréché, éclairé comme une lame posée sur un établi.'},
    {id:'02',name:['Estoc du guet'],type:'Estoc',material:'Acier bruni et laiton',tier:0,roll:38,art:'rapier',pose:[107,152,-27,1.02],colors:['#9da9b4','#f3d59a','#485365'],background:'arch',note:'Lame fine, garde filigranée. La diagonale traverse une alcôve étroite.'},
    {id:'03',name:['Hache de l’If'],type:'Hache',material:'Bronze et bois d’if',tier:1,roll:46,art:'axe',pose:[115,148,-18,1.03],colors:['#ce9b5d','#ffe0a0','#70503a'],background:'grove',note:'La tête de hache se détache devant un arc de branches et de feuilles gravées.'},
    {id:'04',name:['Masse du Cloître'],type:'Masse',material:'Fonte, argent et lin',tier:1,roll:61,art:'mace',pose:[152,152,19,1.06],colors:['#8394ac','#e1e9f0','#4c4c66'],background:'rose',note:'La tête à ailettes empiète sur une rosace décentrée, avec un manche de lin clair.'},
    {id:'05',name:['Faux de nacre'],type:'Faux',material:'Nacre et verre fumé',tier:2,roll:83,art:'scythe',pose:[104,156,-19,1.08],colors:['#9dcbd1','#f1e6ff','#466b85'],background:'moon',note:'Un grand croissant remplit le haut de la carte, le manche descend sur le côté.'},
    {id:'06',name:['Arc du Vitrail'],type:'Arc',material:'If noir, argent et verre bleu',tier:2,roll:54,art:'bow',pose:[131,145,32,1.18],colors:['#72a9d3','#d1f3eb','#3f456d'],background:'glass',note:'Silhouette ajourée, corde tendue et petits cabochons bleus sur les branches.'},
    {id:'07',name:['Lance de la comète'],type:'Lance',material:'Acier bleui et cristal',tier:3,roll:137,art:'spear',pose:[125,148,52,1.22],colors:['#6ea7d5','#d1eaff','#424e84'],background:'orbit',note:'La lance coupe la carte en diagonale et traverse plusieurs anneaux de magie.'},
    {id:'08',name:['Lame des Cendres','froides'],type:'Flamberge maudite',material:'Obsidienne et cuivre',tier:3,roll:98,curse:{stat:'RÉG',value:45},art:'flamberge',pose:[134,147,-32,1.20],colors:['#85486d','#ff9bb6','#2b1a34'],background:'curse',note:'Fissures rose vif, lame ondulée et pentacle renversé. La cendre ronge la récupération.'},
    {id:'09',name:['Sceptre des','Sept Sceaux'],type:'Sceptre',material:'Or martelé et grenat',tier:4,roll:109,art:'scepter',pose:[124,151,10,1.21],colors:['#dcb064','#ffe8ad','#814765'],background:'sun',note:'Un grenat suspendu dans une couronne gravée. Un jet volontairement bas malgré la rareté.'},
    {id:'10',name:['Grande épée de','la Nuit creuse'],type:'Espadon maudit',material:'Acier noir et améthyste',tier:4,roll:149,curse:{stat:'DOS',value:60},art:'greatsword',pose:[124,145,31,1.32],colors:['#9978c0','#ffd5f5','#35223e'],background:'abyss',note:'Lame évidée, fil spectral et sceau rompu. Un gain offensif contre une garde moins solide.'},
  ];
  const nfmt=new Intl.NumberFormat('fr-FR',{minimumFractionDigits:2,maximumFractionDigits:2});
  const percent=n=>nfmt.format(n/100)+' %';
  const boost=(card,roll)=>card.curse?Math.round(roll*1.4):roll;
  const escape=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const outline=(d,fill,stroke,w=1.7)=>'<path d="'+d+'" fill="'+fill+'" stroke="'+stroke+'" stroke-width="'+w+'" stroke-linecap="round" stroke-linejoin="round"/>';
  const spark=(x,y,s,c)=>'<path d="M'+(x-s)+' '+y+'l'+s+' -'+s+' '+s+' '+s+' -'+s+' '+s+'Z" fill="'+c+'"/>';
  let sequence=0;
  function weapon(card,fill){
    const hi=card.colors[1],dark=card.colors[2];
    const p=(d,c=fill,w=2)=>outline(d,c,hi,w);
    const line=(d,c=hi,w=1.3)=>outline(d,'none',c,w);
    const grip=(x1,x2,y1,y2,c=dark)=>'<rect x="'+x1+'" y="'+y1+'" width="'+(x2-x1)+'" height="'+(y2-y1)+'" rx="3" fill="'+c+'" stroke="'+hi+'" stroke-width="1.5"/>';
    switch(card.art){
      case 'knife':
        return p('M-11-54L18-68Q24-39 15 4L7 17H-9L-15 4Z')+line('M-7-42L13-55Q16-29 8-2 M-14-23l5 2-4 4',dark,2)+p('M-25 14L26 11 18 24H-19Z',dark)+grip(-8,8,23,65)+line('M-7 32l14-5m-14 15 14-5m-14 15 14-5m-14 15 14-5')+p('M-10 65H10L6 71H-6Z',dark);
      case 'rapier':
        return p('M0-94L7-78 5 21-5 21-7-78Z')+line('M0-86V16',dark,1.5)+grip(-4,4,34,72)+line('M-22 22Q0 16 23 22M-20 22Q-30 40-8 53Q18 66 18 34Q18 22 8 25M-12 38Q16 19 22 35',hi,2.5)+p('M0 66l7 7-7 10-7-10Z',dark);
      case 'axe':
        return p('M-8-76H8L7 76 0 86-7 76Z','#8e604b')+line('M0-65V72',dark,1.7)+p('M-3-54Q34-48 46-83Q68-61 61-28Q45-14 35-13Q17-39-3-30Z')+p('M46-83Q68-61 61-28L52-22Q59-53 41-72Z',hi,1.2)+line('M9-49Q34-39 45-62M13-41Q28-32 44-47M-6 51l13-9m-13 23 13-9',dark,2)+p('M-16-61H7V-29H-16Z',dark);
      case 'mace':
        return grip(-6,6,-48,73)+p('M-24-68L-11-82H11L24-68V-41L10-30H-10L-24-41Z')+
          p('M-20-63L-37-69-26-49-37-43-20-38ZM20-63L37-69 26-49 37-43 20-38Z',dark)+p('M0-84L13-64 10-45 0-31-10-45-13-64Z')+
          line('M-6 43l12-5m-12 15 12-5m-12 15 12-5')+p('M-10 73H10L0 84Z',dark);
      case 'scythe':
        return p('M-15-53L-5-59-5 85-15 91Z',dark)+p('M-14-56Q4-84 34-79Q58-73 83-48Q50-67 27-56Q4-43-5-27Z')+
          p('M-7-52Q10-75 34-75Q60-66 83-48Q49-70 29-55Q4-43-5-27Z',hi,1.05)+line('M4-55Q25-71 55-58 M-14 23l9-5m-9 16 9-5',dark,1.5)+p('M-18-52L-5-57-3-28-17-21Z',fill);
      case 'bow':
        return p('M-35-78Q21-70 43-29Q56 0 43 30Q22 68-35 78L-25 65Q16 58 29 24Q37 0 29-24Q16-58-25-65Z',dark)+
          line('M-34-76L-45 0-34 76',hi,1.5)+line('M-27-70Q19-60 34-26Q44 0 34 26Q20 60-27 70',hi,2.2)+
          p('M29-9L42-15 49 0 42 15 29 9Z')+p('M5-53l8-4 7 9-8 5ZM5 53l8 4 7-9-8-5Z')+
          line('M-49 0H54M44-5L54 0 44 5',hi,1.4);
      case 'spear':
        return p('M-5-52H5V78L0 92-5 78Z',dark)+p('M0-104L21-65 10-38 0-22-10-38-21-65Z')+
          p('M0-91L9-66 0-41-9-66Z',hi,1.2)+p('M-20-42L0-29 20-42 10-19H-10Z',dark)+line('M-5-2l10-5m-10 18 10-5m-10 18 10-5');
      case 'flamberge':
        return p('M0-88Q25-77 8-63Q-13-49 11-38Q26-25 6-15Q-8-7 9 6L7 24H-7L-11 10Q-28-3-10-15Q6-26-12-38Q-30-53-9-64Q8-75 0-88Z',dark)+
          line('M0-76Q12-70 0-62Q-18-51 3-41Q16-31-4-19Q-18-9 0 5V20','#ff739f',2.4)+p('M-27 18L0 25 27 18 19 34H-19Z')+grip(-6,6,32,70)+p('M0 64l11 11-11 9-11-9Z','#92415f');
      case 'scepter':
        return p('M-8-31H8L7 76 0 84-7 76Z')+line('M-5 25l10-7m-10 20 10-7m-10 20 10-7m-10 20 10-7',dark,1.8)+
          p('M0-89L13-77 26-84 30-59 17-40 0-31-17-40-30-59-26-84-13-77Z',fill)+
          p('M0-76L17-60 0-40-17-60Z','#a33b60')+p('M0-72L8-61 0-48-8-61Z','#e67b8e',1.2)+
          '<circle cy="-60" r="26" fill="none" stroke="'+hi+'" stroke-width="1" stroke-dasharray="2 4"/>';
      case 'greatsword':
        return p('M0-90L25-64 19 22H-19L-25-64Z',dark)+p('M0-77L11-60 7 4-7 4-11-60Z','#201522',1.8)+
          line('M-17-56L-14 15M17-56L14 15M-9-18L0-30 9-18','#e193d6',2.5)+p('M-18 16L-47 15-32 38-15 30-8 45H8L15 30 32 38 47 15 18 16Z')+
          grip(-7,7,42,72)+p('M0 63l12 12-12 12-12-12Z','#9f73d8')+line('M-7 55l14-6m-14 15 14-6');
    }
    return '';
  }
  function oddity(card,fill){
    const hi=card.colors[1],dark=card.colors[2];
    const p=(d,c=fill,w=2)=>outline(d,c,hi,w);
    const line=(d,c=hi,w=1.6)=>outline(d,'none',c,w);
    const c=(x,y,r,ink)=>'<circle cx="'+x+'" cy="'+y+'" r="'+r+'" fill="'+ink+'" stroke="'+hi+'" stroke-width="1.5"/>';
    switch(card.special){
      case 'hook':return p('M-6 75L0-51Q-18-66-23-45L-27-29-36-53Q-32-82-5-79 14-77 16-51L6 77Z')+line('M-3 57L6-44Q7-65-10-68',dark,3)+line('M-9 45l17 7m-18 6 17 7',hi,2);
      case 'keyblade':return p('M-7 75V-32A29 29 0 1 1 7-32V-7H25V5H13V18H7V75Z')+c(0,-59,15,dark)+line('M-1 70V-34M-20-58a20 20 0 0 1 39 0',dark,3);
      case 'shears':return p('M-38-75Q-27-66 3-20L41 24-41-16Z')+p('M35-75Q31-40-12 0L-38 28 11-20Z')+c(-25,47,23,dark)+c(25,47,23,dark)+c(-25,47,13,'#24202b')+c(25,47,13,'#24202b')+c(0,-6,5,hi);
      case 'club':return p('M-11 77L-8 25-24-31-20-69-5-82 19-77 29-64 28-29 12 26 8 77Z')+line('M-14-58Q14-45 22-63M-11-39q23 12 36 2M-5 49l14 6m-15 5 15 6',dark,4)+c(13,-60,4,hi)+c(-9,-31,5,dark);
      case 'trident':return p('M-5 77V-31L-27-49-38-46-36-79-23-56-17-61-16-45-5-42-10-63 0-84 10-63 5-42 16-45 17-61 23-56 36-79 38-46 27-49 5-31V77Z')+line('M0 68V-65M-29-55L-17-44 0-34 17-44 29-55',dark,2.6)+c(0,-33,5,hi);
      case 'pipe':return p('M-12 71L-6-58-17-70H24L20-54 8 70Z')+line('M-8 56L12 57M-5-37l20 1M-5-30l19 1M-13-62h32',dark,3)+[-18,-3,12,27].map(y=>c(4,y,3,dark)).join('');
      case 'whip':return p('M-61 38L-48 28-10 61-24 78Z',dark)+line('M-52 32Q-79-28-32-44Q29-63 58-39Q84-16 47 5Q17 19 10-8Q3-39 56-70',hi,5)+line('M-52 32Q-79-28-32-44Q29-63 58-39Q84-16 47 5Q17 19 10-8Q3-39 56-70',dark,2)+line('M-47 39l-5 8m14 0-7 9m16 0-6 8',hi,2);
      case 'lute':return p('M-15-12L5-77 20-73 12-7Q21 4 29 17Q59 36 38 66Q25 81 0 77Q-30 78-43 58Q-60 30-31 17Q-11 11-15-12Z')+c(0,32,13,dark)+p('M-18 52H15V60H-18Z',dark)+line('M-10 57L8-70m-12 127 17-126m-10 126 15-125M-21 14q-31 24-13 42',hi,1.4);
      case 'poncho':return p('M-19-52Q0-36 19-52L41-42 77 37 31 49 0 71-31 49-77 37-41-42Z')+p('M-46-32L-32-36 0 39 32-36 46-32 64 23 0 58-64 23Z',dark)+line('M-65 31L0 63 65 31M-17-47L0-32 17-47',hi,2.5)+line('M-62 38l-5 10m20-3-3 10m21-1-1 9m19-2v11m73-34 5 10m-20-3 3 10m-21-1 1 9m-19-2v11');
      case 'shirt':return p('M-24-66Q0-41 24-66L43-58 65-25 43-10 35-28 39 62Q0 74-39 62L-35-28-43-10-65-25-43-58Z')+p('M-27-59L0-39 27-59 11-29 0-40-11-29Z',dark)+line('M0-39v100M-34 48q29 17 68 0M-38-52l-10 29m86-29 10 29',dark,3)+[-24,-9,6,21].map(y=>c(2,y,2,hi)).join('');
      case 'coat':return p('M-31-75L-58-52-67 6-47 15-35-24-39 25-56 69-18 75 0 52 18 75 56 69 39 25 35-24 47 15 67 6 58-52 31-75Z')+p('M-29-71L0-35 29-71 28-31 0 8-28-31Z',dark)+line('M-34 24l26 9m16 0 26-9M-45 56l27 5m36 0 27-5M0 8v45',hi,2.2)+[-5,9,22,37].map(y=>c(8,y,2,hi)).join('');
      case 'overalls':return p('M-34-62H-24L-23-21H23L24-62H34L38 21 51 65 13 71 0 41-13 71-51 65-38 21Z')+p('M-31-13H31L34 32H-34Z',dark)+p('M-17-4H17L12 18H-12Z')+line('M-43 56l29 3m28 0 29-3M-29 25H29',hi,2)+c(-27,-17,4,hi)+c(27,-17,4,hi);
      case 'barrel':return p('M-43-57Q0-70 43-57 64 0 43 58Q0 72-43 58-64 0-43-57Z')+line('M-24-60Q-40 0-24 61M0-63V64M24-60Q40 0 24 61',dark,3)+p('M-49-40Q0-29 49-40L52-28Q0-16-52-28ZM-52 27Q0 39 52 27L49 40Q0 53-49 40Z',dark)+c(0,1,8,hi);
      case 'scarf':return p('M-48-41Q-39-69 4-63Q49-66 58-42Q65-18 30-13L60 35 35 50 6 8-19 70-44 60-24-16Q-53-17-48-41Z')+p('M-35-41Q-15-56 18-50Q38-51 43-40 32-26 2-28-20-28-35-41Z',dark)+line('M-42-30q26 19 71 9M-34 53l19 6m52-21 14-9M-20-9q26 3 43-6',hi,2.6);
      case 'robe':return p('M-27-71L-52-55-72-11-49 6-30-21-25 11-54 71-29 78H29L54 71 25 11 30-21 49 6 72-11 52-55 27-71 0-48Z')+p('M-23-52L0-36 23-52 16 11 26 68H-26L-16 11Z',dark)+line('M-35 57L-19 17M35 57L19 17M0-34V64M-31 13H31M-48-4l-14-10m110 10 14-10',hi,2.6);
      case 'vest':return p('M-33-62L-53-43Q-31-16-41 3L-46 61Q0 77 46 61L41 3Q31-16 53-43L33-62 15-48 0-19-15-48Z')+line('M-40 14l49 49m-47-70 69 68m-54-82 62 62m-69 13 64-65m-41 70 46-48m-66 40 59-60M0-17V63',dark,3)+c(0,12,3,hi)+c(0,31,3,hi);
      case 'carapace':return p('M-59-32L-32-63-15-51H15L32-63 59-32 67 3 48 47 0 69-48 47-67 3Z',dark)+p('M0-48L39-27 45 20 25 43 0 55-25 43-45 20-39-27Z')+line('M0-48v103M-58-28l36 24L0-15 22-4 58-28M-61 4l39-8-3 47m86-39-39-8 3 47',hi,2.7);
      case 'harness':return p('M-55-54L-39-66-4-21 37-65 53-51 14 0 56 42 42 57 0 14-40 57-55 43-15 0Z',dark)+p('M-60 35L-17 23H17L60 35V54L17 45H-17L-60 54Z')+p('M0-25L23 0 0 25-23 0Z')+c(0,0,9,dark)+line('M-47 42l25-8m44 0 25 8M-44-54l29 38m59-38-29 38',hi,2.5);
      case 'cocoon':return p('M0-75Q54-70 62-23 67 6 42 49L0 79-42 49Q-67 6-62-23-54-70 0-75Z')+p('M0-61Q37-55 44-19 37 23 0 49-37 23-44-19-37-55 0-61Z',dark)+line('M-51-42Q0-23 51-42M-59-21Q0 1 59-21M-59 0Q0 28 59 0M-45 32Q0 51 45 32M-25 58Q0 68 25 58',hi,2.5)+p('M0-43L15-17 0 13-15-17Z');
      case 'reliquary':return p('M-31-63L-62-38-47 60 0 72 47 60 62-38 31-63 0-51Z',dark)+p('M-35 54V-15Q-35-39 0-57Q35-39 35-15V54Z')+p('M-27 46V-14Q-27-31 0-45Q27-31 27-14V46Z',dark)+line('M0-44v90M-25-14L0 7 25-14M-25 29L0 7 25 29M-59-35l21 5m76 0 21-5',hi,2.7)+c(0,7,7,hi);
      case 'umbrella':return p('M-69 6Q-62-61 0-65Q62-61 69 6Q49-6 34 8Q16-5 0 8Q-16-5-34 8Q-49-6-69 6Z')+line('M-34 8Q-30-49 0-65Q30-49 34 8M0-65V54Q0 76 17 68 25 65 23 52',hi,3)+line('M-60-12Q0-33 60-12',dark,3);
      case 'basket':return line('M-38-13Q-45-73 0-73Q45-73 38-13',hi,6)+p('M-67-15H67L50 51Q0 69-50 51Z')+p('M-67-15Q0-5 67-15L65-5Q0 7-65-5Z',dark)+line('M-56 6Q0 21 56 6M-55 19Q0 35 55 19M-52 34Q0 49 52 34M-44-9l7 62m12-57 3 61m22-60v63m25-64-3 61m22-66-7 62',dark,2.5);
      case 'bench':return p('M-71-29L52-51 72-28 65-10-56 9-72-12Z')+p('M-54 9L-51 58-36 53-35 6ZM44-8L43 41 55 36 62-10Z',dark)+line('M-64-14L65-36M-55 0L63-20M-48 38l92-18',hi,3.4)+c(-56,-20,3,dark)+c(54,-36,3,dark);
      case 'curtain':return p('M-65-55H65L50-42 62 58 30 69 0 55-30 69-62 58-50-42Z')+p('M-71-60H71V-48H-71Z',dark)+line('M-48-44Q-29 8-44 61M-25-44q11 33-4 99M0-45q12 48 0 99M25-44Q15 10 30 60M48-44Q34 13 47 58',dark,4)+line('M-57 50L-30 63 0 49 30 63 57 50',hi,2);
      case 'net':return p('M0-73L67-17 52 52 0 73-52 52-67-17Z','none',4)+line('M-67-17L18 66M-53-28L42 57M-38-42L55 40M-23-54L59 20M-9-66L64-2M67-17L-18 66M53-28L-42 57M38-42L-55 40M23-54L-59 20M9-66L-64-2',hi,2.2)+[[-67,-17],[67,-17],[-52,52],[52,52],[0,73],[0,-73]].map(([x,y])=>c(x,y,5,dark)).join('');
      case 'lantern':return p('M-45-37L-26-55 0-70 26-55 45-37 36-26 38 46 46 57Q0 72-46 57L-38 46-36-26Z',dark)+p('M-25-24H25L26 46H-26Z')+line('M-26-25L26 46m0-71-52 71M-42 54q42 10 84 0M-38-37q38-10 76 0M-9-65q-4-23 9-23t9 23',hi,2.7)+p('M0-4Q20 17 6 35Q-8 40-14 23Q-15 12 0-4Z','#f5bf73');
      case 'screen':return p('M-70-44L-29-67 0-56 29-67 70-44 65 60 29 45 0 68-29 45-65 60Z',dark)+p('M-61-37L-30-57-30 36-57 48ZM-21-54L0-46 21-54 21 40 0 56-21 40ZM30-57L61-37 57 48 30 36Z')+line('M-56 16l18-28m21 36 33-43m22 13 15 22M-33-46L-39 27m78-54 8 50',hi,2.2);
      case 'shell':return p('M0-74Q47-74 62-32 79 13 44 51L0 72-44 51Q-79 13-62-32-47-74 0-74Z')+p('M0-53L30-26 36 12 20 41 0 52-20 41-36 12-30-26Z',dark)+line('M0-53V-74M-30-26L-53-54M30-26L53-54M-36 12l-32-6m104 6 32-6M-20 41l-18 14m58-14 18 14M0 52v20',hi,3);
      case 'swarm':return [[-42,-37,24],[-6,-54,-12],[35,-30,28],[-50,9,-18],[7,-4,5],[49,15,-31],[-19,36,16],[17,54,-12]].map(([x,y,a])=>
        '<g transform="translate('+x+' '+y+') rotate('+a+')">'+p('M0 0Q-22-32-29-19L-20 8-4 9 0 16 4 9 20 8 29-19Q22-32 0 0Z',fill)+line('M0 0L-20-15m20 15 20-15',dark,2)+'</g>').join('');
      case 'prism':return p('M-41-47L19-76 60-24 47 42 0 73-53 32-64-11Z')+p('M-41-47L6-18 19-76 60-24 6-18 47 42 0 73 6-18-53 32-64-11Z',dark)+line('M-41-47L6-18 19-76M60-24L6-18 47 42M0 73L6-18-53 32M-64-11l70-7',hi,2.6)+c(6,-18,7,hi);
      case 'talisman':return line('M-5 21Q-90-3-59-48Q-36-83 0-63Q36-83 59-48Q90-3 5 21',hi,4)+p('M0 7L29 40 0 75-29 40Z')+p('M0 19L18 40 0 62-18 40Z',dark)+[[-46,-50],[-59,-23],[-40,3],[46,-50],[59,-23],[40,3],[0,-63]].map(([x,y])=>c(x,y,5,fill)).join('');
      case 'eye':return p('M-75 0Q-45-53 0-45Q45-53 75 0Q45 50 0 45Q-45 50-75 0Z')+p('M-59 0Q0-62 59 0Q0 62-59 0Z',dark)+c(0,0,26,fill)+p('M0-23L11 0 0 23-11 0Z',dark)+line('M-46-39l-9-13m30 5-5-17m30 13v-20m25 24 5-17m16 25 9-13M-46 39l-9 13m55 0v19m46-32 9 13',hi,2.6);
      case 'slippers':return [-33,29].map((x,i)=>'<g transform="translate('+x+' '+(i?-12:8)+')">'+p('M-23-4Q-30-54-15-62Q-4-61-5-17Q4-60 17-53Q27-50 15-5Q37 22 19 49H-21Q-33 27-23-4Z')+p('M-19 22Q0 38 23 22L19 49H-21Z',dark)+c(-9,15,3,hi)+c(10,15,3,hi)+'</g>').join('');
      case 'waders':return p('M-46-71H46L45 8 61 49 45 62H15L7 13H-7L-15 62H-46L-61 49-45 8Z',dark)+p('M-45-63H45L32-25 35 7-35 7-32-25Z')+line('M-40 38l22 2m36 0 22-2M-30-44H30M-33-21H33',hi,2.8)+c(-20,-55,4,hi)+c(20,-55,4,hi);
      case 'trousers':return p('M-46-65H46L50-19 53 58 17 71 0 1-17 71-53 58-50-19Z')+p('M-45-62H45V-48H-45Z M-44-32L-20-24-31 10-46 0Z M24-11H46V23L30 29 23 18Z',dark)+line('M0-48V1M-46 52l22 6m48 0 22-6M-35 18l-5 28m75-28 5 28',hi,2.5);
      case 'garters':return p('M-55-61H-17V-28H-55ZM17-50H55V-17H17Z')+p('M-55 16H-17V44H-55ZM17 28H55V56H17Z',dark)+line('M-37-30v47m74-35v47M-47-55h20v18h-20zM27-44h20v18H27zM-47 21h20v17h-20zM27 33h20v17H27z',hi,3);
      case 'snowshoe':return [-31,32].map((x,i)=>'<g transform="translate('+x+' '+(i?-10:9)+')">'+p('M0-73Q39-56 26-4 22 37 0 65-22 37-26-4-39-56 0-73Z',dark)+line('M-20-46L17 26M-26-22L10 44M19-46L-18 26M25-22L-10 44M-25-7H25M-21 13H21',hi,2.6)+p('M-16-18H16V-6H-16Z')+'</g>').join('');
      case 'flippers':return [-31,31].map((x,i)=>'<g transform="translate('+x+' '+(i?-9:10)+')">'+p('M-18-65H17L13-18 33 43 14 34 4 53-8 38-29 50-16-18Z')+p('M-16-53H15L10-13H-11Z',dark)+line('M-8-13l-10 46m21-49 1 49m4-48 13 42',hi,2.3)+'</g>').join('');
      case 'springboot':return p('M-31-73L19-62 12-16 40-8 60 8 49 22H-46L-33-18Z',dark)+p('M-28-69L17-59 12-41-31-47Z')+line('M-23-33l34 8m-36 4 32 9M-31 23L32 32-29 41 32 49-28 57 32 64',hi,4)+p('M-44 63H55V73H-44Z');
      case 'roller':return p('M-28-70H19L14-18 50-5 67 15 55 29H-50L-40-14Z')+p('M-50 29H53L49 41H-45Z',dark)+line('M-24-48h38m-39 12h38m-43 11h42M-43 18H50',dark,3)+[-35,-12,13,37].map(x=>c(x,48,11,fill)+c(x,48,4,dark)).join('');
      case 'jetboot':return p('M-25-76L20-64 11-15 50 4 62 21 43 37H-46L-39-8Z',dark)+p('M-43 23L-20 30 31 24 43 14 51 23 43 37H-46Z')+p('M-49-52L-35-56-29 15-46 29-57 18Z')+line('M-29-54l44 9m-46 4 44 9m-50 7 47 5',hi,2.5)+p('M-40 42l9 18 7-18 7 27 8-26m26 0 8 17 7-19 6 25 7-25','#f2b270');
      case 'crab':return p('M-31-64L17-54 13-5 28 8 53-2 69 15 49 28 69 37 51 54 9 62-51 51-60 24-43-5Z')+p('M-46 14L-64 32-49 52-25 55-20 35Z',dark)+line('M-32-44L14-32m-48 2 47 11m-52 3 48 13M-20 39Q16 23 48 28M-12 46q24-14 51-10',hi,3)+c(-35,21,5,dark);
      case 'knees':return [-32,32].map((x,i)=>'<g transform="translate('+x+' '+(i?-13:11)+')">'+p('M-24-45L0-60 24-45 29-8 13 46 0 61-13 46-29-8Z',dark)+p('M0-40L22-18 13 21 0 33-13 21-22-18Z')+line('M0-40v73M-25 1h13m24 0h13M-18 33l18 14 18-14',hi,2.4)+'</g>').join('');
      case 'stirrups':return [-32,33].map((x,i)=>'<g transform="translate('+x+' '+(i?-10:9)+')">'+p('M-7-68H7V-21H-7Z',dark)+line('M0-23Q-31-11-27 47H27Q31-11 0-23Z',hi,6)+p('M-29 41H29V54H-29Z')+c(0,-28,7,fill)+'</g>').join('');
      case 'wingboot':return p('M-22-46L11-44 11-3 41 15 56 31 46 49H-44L-40 12Z',dark)+p('M-26 4Q-72-24-69-70L-45-46-40-66-23-39-15-53-8-21-12 10Z')+line('M-62-53l42 55m-26-48 26 48m-8-36 8 36M-35 21H42M-37 39H47',hi,2.7)+p('M-23-42H10V-29H-27Z');
      case 'flail':return p('M-6 11H6V73H-6Z',dark)+p('M-13 66H13L8 81H-8Z',dark)+line('M0 11Q4-20 27-27Q50-29 47-56',hi,3.4)+
        c(46,-65,20,fill)+p('M32-79L27-92 42-84 50-97 54-83 68-87 65-73 76-67 63-62 70-50 56-52 51-41 44-51 30-46 33-60 22-64 33-70Z',dark)+c(46,-65,8,fill);
      case 'halberd':return p('M-6-91H6V85L0 94-6 85Z',dark)+p('M0-106L15-81 8-54H-8L-15-81Z')+
        p('M-5-64Q26-71 48-95L44-57Q35-28-6-41Z')+p('M4-61L-35-58-47-42-18-44 4-40Z',dark)+line('M11-61Q31-66 39-78M-4 26l8-5m-8 18 8-5',hi,2.1);
      case 'sickle':return p('M-8-22H8L10 70 0 82-10 70Z',dark)+p('M-5-18Q-39-32-45-62Q-40-95-2-92Q31-88 51-59Q22-78 2-71Q-17-66-15-49Q-11-37 8-33L8-19Z')+
        line('M-32-66Q-25-84-5-82Q15-81 30-73M-7 46l14-7m-14 18 14-7',hi,2.1);
      case 'pick':return p('M-7-65H7V79L0 89-7 79Z','#8c6250')+p('M-58-55Q-30-79 0-69Q31-68 57-40Q25-51 7-51L0-43-8-51Q-31-57-58-55Z')+
        line('M-7 61l14-9m-14 21 14-9M-40-61Q-5-73 23-56',dark,2.1);
      case 'tomeblade':return p('M-40-22L41-22 49 63H-35Z',dark)+p('M-32-25L45-25 51 54H-27Z')+
        p('M0-102L16-77 10 21 0 36-10 21-16-77Z',dark)+line('M0-91V18M-28 30H-10M17 30H42',hi,2.3)+p('M0-14l8 10-8 11-8-11Z','#e47c91');
      case 'brigandine':{
        const pieces=Array.from({length:18},(_,i)=>'<rect x="'+(-36+i%6*12)+'" y="'+(-18+Math.floor(i/6)*18)+'" width="10" height="15" rx="2" fill="'+fill+'" stroke="'+hi+'" stroke-width=".9"/>').join('');
        return p('M-31-65L-55-46-64-8-46 15-42 62H42L46 15 64-8 55-46 31-65 13-46H-13Z',dark)+pieces+line('M-36 47H36M0-46V59 M-45-28L-28-41M45-28L28-41',hi,2.8);
      }
      case 'mantle':return p('M-22-64Q0-44 22-64L50-41 69 45 41 68 0 55-40 74-69 45-50-41Z',dark)+p('M-32-49Q0-32 32-49L47-33 25-14H-25L-47-33Z')+
        line('M-26-14L-44 60M-12-12L-16 57M14-12L23 60M30-13L47 52',hi,2.5)+c(0,-22,6,fill);
      case 'scale-armor':{
        const scales=Array.from({length:22},(_,i)=>{
          const x=-30+i%6*12,y=-23+Math.floor(i/6)*15;
          return '<path d="M'+x+' '+y+'q6-4 12 0v8q-6 8-12 0Z" fill="'+fill+'" stroke="'+hi+'" stroke-width="1"/>';
        }).join('');
        return p('M-42-58L-64-33-50-15-41 3-36 59H36L41 3 50-15 64-33 42-58 16-36H-16Z',dark)+scales+line('M-32 52H32');
      }
      case 'ribcage':return p('M-58-37L-40-61-16-47H16L40-61 58-37 44-13 37 63H-37L-44-13Z',dark)+
        line('M0-43V61 M-43-29Q-18-17 0-20Q18-17 43-29 M-40-9Q-20 6 0 1Q20 6 40-9 M-39 11Q-19 28 0 23Q19 28 39 11 M-37 34Q-18 48 0 44Q18 48 37 34',hi,4.7)+p('M0-10l10 12-10 14-10-14Z','#c6728a');
      case 'kite':return p('M0-78L49-48V8L0 84-49 8V-48Z',dark)+p('M0-65L37-42V5L0 68-37 5V-42Z')+line('M0-58V61M-29-26L0-10 29-26M-26 5L0 30 26 5',hi,2.2);
      case 'door':return p('M-51-65H51V59L39 76H-39L-51 59Z',dark)+p('M-42-55H42V58L35 66H-35L-42 58Z')+
        line('M-26-54V62M-9-54V66M9-54V66M26-54V62M-41-25H41M-41 26H41',dark,3.4)+c(26,1,6,hi);
      case 'mirror':return p('M0-75L55-44 62 18 29 61-30 67-59 19-54-39Z',dark)+p('M0-61L45-37 51 16 23 49-24 54-47 15-44-32Z')+
        line('M-33-25L13-47M-36-12L26-39M-24 28L28 2',hi,3.8)+p('M0-12l13 13-13 13-13-13Z','#926baf');
      case 'fan':return p('M0 57L-67-27Q-28-65 0-69Q28-65 67-27Z',dark)+p('M0 45L-55-27Q-23-54 0-56Q23-54 55-27Z')+
        line('M0 44L-36-41M0 44L-18-50M0 44V-54M0 44L18-50M0 44L36-41',hi,2.5)+c(0,43,7,hi);
      case 'hoof':{
        const one=p('M-47-69H-14L-18-10-10 32-15 60H-54L-60 42-48-3Z',dark)+p('M-55 29L-12 23-15 60H-54L-60 42Z')+
          line('M-34 31L-38 57M-47-50H-17M-47-36H-18',hi,2.2);
        return one+'<g transform="translate(0 10) scale(-1 1)">'+one+'</g>';
      }
      case 'claws':{
        const one=p('M-48-59L-17-52-21 10-8 43-25 61H-54L-58 40-47 7Z')+
          p('M-53 32L-48 65-40 50-31 68-25 51-15 62-10 43Z',dark)+line('M-46-32l24 4m-25 9 24 4m-25 9 24 4',hi,2.5);
        return one+'<g transform="translate(0 -9) scale(-1 1)">'+one+'</g>';
      }
      case 'wraps':{
        const one=p('M-46-66L-20-63-23 53-41 67-55 41Z',dark)+line('M-48-51L-20-40M-50-31L-22-20M-51-10L-23 1M-52 11L-24 22M-53 32L-25 43',hi,5);
        return one+'<g transform="translate(0 11) scale(-1 1)">'+one+'</g>';
      }
      case 'bell-boot':return p('M-31-57H23L20-5 34 18 64 24 65 48 36 63-40 57-42 41-32-8Z',dark)+
        p('M-31-49H22L21-25H-34Z')+p('M-39 33Q18 48 63 27L64 47 36 62-39 55Z')+c(52,25,9,fill)+line('M-27-12l40 10m-43 4 45 11',hi,2.8);
      case 'pan':return p('M-8-4H8V81L0 89-8 81Z',dark)+c(0,-41,40,fill)+c(0,-41,32,dark)+line('M-22-46Q-15-62 2-61M-24-36Q-19-50-10-52',hi,2.3);
      case 'broom':return p('M-5-94H5V22H-5Z','#87634f')+p('M-11 10H11L29 75 14 84H-14L-29 75Z')+line('M-17 27H17M-12 28L-19 76M-5 29L-6 79M5 29L6 79M12 28L19 76',dark,2.3);
      case 'candlestick':return p('M-5-14H5V62L29 77 23 86H-23L-29 77-5 62Z')+p('M-9-60H9V-17H-9Z','#f1d0aa')+p('M0-88Q-19-68 0-63Q15-69 0-88Z','#ea946d')+line('M0-84V-67');
      case 'crossbow':return p('M-7-77H7V75L0 87-7 75Z',dark)+p('M-72-33Q-39-77 0-51Q39-77 72-33L56-29Q25-53 0-32Q-25-53-56-29Z')+line('M-65-32L0-10 65-32M0-91V21',hi,2.1)+p('M-14-8L0-18 14-8V14H-14Z');
      case 'book':return p('M-46-65L43-54 49 60-39 52Z',dark)+p('M-39-60L45-52 50 51-34 43Z')+p('M-23-51L-12-48-9 40-20 37Z','#754854')+line('M-5-40l26 3m-27 10 32 3m-28 13 24 2')+p('M7-12l11 15-11 12-10-15Z',dark);
      case 'lid':return c(0,0,66,fill)+c(0,0,53,dark)+c(0,0,44,fill)+p('M-17 4Q-22-18 0-18Q22-18 17 4L10 5Q12-7 0-7Q-12-7-10 5Z',dark)+line('M-38-33Q-15-51 7-48',hi,2.6);
      case 'sign':return p('M-66-27L33-27 62-3 33 24H-66Z',dark)+p('M-56-19H28L49-3 27 16H-56Z')+line('M-47-8H12M-47 5H17M-15 24V64',hi,2.3)+c(-55,-10,2,hi)+c(-55,9,2,hi);
      case 'wheel':return c(0,0,61,fill)+c(0,0,52,dark)+'<g stroke="'+hi+'" stroke-width="5">'+Array.from({length:8},(_,i)=>'<path d="M0-46V0" transform="rotate('+(i*45)+')"/>').join('')+'</g>'+c(0,0,12,fill);
      case 'apron':return p('M-23-76H23L20-39 53-18 47 70 0 82-47 70-53-18-20-39Z',dark)+line('M-18-70Q0-101 18-70M-49-12l-25 13m123-13 25 13')+p('M-26 13H26V51H-26Z')+line('M-24 17l10 9 10-9 10 9 10-9 8 9',hi,1.3);
      case 'cloak':return p('M-20-67L-40-51-61 75-29 65 0 83 30 61 61 72 40-51 20-67Z',dark)+p('M-20-65Q0-50 20-65L37-47 18-28H-18L-37-47Z')+line('M-23-30L-38 66M7-26L0 73M24-30L36 57',hi,2)+c(0,-35,5,fill);
      case 'mail':{
        const meshes=Array.from({length:42},(_,i)=>{
          const x=-28+(i%7)*9,y=-27+Math.floor(i/7)*11;
          return '<circle cx="'+x+'" cy="'+y+'" r="4.3" fill="none" stroke="'+hi+'" opacity=".64"/>';
        }).join('');
        return p('M-31-65L-63-43-49-15-37-20-37 64H37V-20L49-15 63-43 31-65 15-43H-15Z',dark)+meshes+line('M-37 56H37 M-43-23L-27-40 M43-23L27-40');
      }
      case 'book-armor':return p('M-62-30L-37-49-15-39H15L37-49 62-30 48-1 43 59H-43L-48-1Z',dark)+
        p('M-36-31L-7-25-4 43-33 36ZM6-25L35-32 34 36 4 43Z')+line('M-29-17l16 3m-15 11 16 3m24-13 16-3m-16 15 16-3',dark,2);
      case 'boot-hole':return p('M-28-62L17-58 13 11 27 23 59 32Q69 37 59 55L9 65-38 53-33 8Z',dark)+p('M-25-55L15-52 14-29-28-32Z')+line('M-22-20l30-5m-28 16 26-6m-28 16 26-6 M-30 47Q8 60 53 46',hi,2.4)+p('M25 38l11-8 9 8-6 9-15-2Z','#161523',2.4);
      case 'lost-shoe':return p('M-46 14Q-28-12-25-52L9-61 17 8 35 16 68 36 56 51 26 57-50 51-59 40Z')+
        p('M-48 39Q2 44 56 28L66 37 56 51 26 57-50 51-59 40Z',dark)+line('M-39 14L15 13M-29-5L13-7M-27-24L11-27M-25-43L9-46 M22 15l-1 23m14-19 1 17m12-10 1 9',dark,2.9);
      case 'sandals':{
        const one=p('M-50-56Q-27-72-13-53L-19 49Q-27 75-52 55L-55-21Z','#9a6e52')+line('M-52-30L-14-14M-54-13L-17-37M-52 13L-19 28M-53 27L-20 11',hi,3.6);
        return one+'<g transform="translate(0 -8) scale(-1 1)">'+one+'</g>';
      }
      case 'socks':{
        const one=p('M-46-69H-14L-19 18-2 37-13 56-43 52-53 38Z')+line('M-45-57H-15M-46-48H-16M-49 26l31 8m-32 1 27 8',dark,4);
        return one+'<g transform="translate(0 10) scale(-1 1)">'+one+'</g>';
      }
      case 'skates':{
        const one=p('M-48-61H-16L-19-1-5 19V44H-53V6Z',dark)+line('M-49 17H-14M-46-23H-19M-50 44v13h44l10-8m-38 8v-12',hi,2.9);
        return one+'<g transform="translate(0 -13) scale(-1 1)">'+one+'</g>';
      }
      case 'stilts':return p('M-36-74H-25V85H-36ZM25-83H36V76H25Z','#a77b50')+p('M-53-17H-15V-6H-53ZM15-28H53V-17H15Z')+line('M-40-39l20-9m-20 13 20-9m0 60-20 9M21-49l20-9m-20 13 20-9m0 60-20 9',dark,2);
    }
    return '';
  }
  function pentacle(cx,cy,r,inverted=false){
    const order=[0,2,4,1,3,0];
    return order.map((i,k)=>{
      const a=(inverted?Math.PI/2:-Math.PI/2)+i*2*Math.PI/5;
      return (k?'L':'M')+(cx+r*Math.cos(a)).toFixed(1)+' '+(cy+r*Math.sin(a)).toFixed(1);
    }).join('');
  }
  function scene(card,rim,ink){
    const x=card.background==='rose'?93:card.background==='grove'?142:125;
    const y=card.background==='moon'?117:145;
    let out='<circle cx="'+x+'" cy="'+y+'" r="'+(57+card.tier*8)+'" fill="'+ink+'" stroke="'+rim+'" stroke-opacity=".57"/>';
    const line=(d,w=1)=>outline(d,'none',rim,w);
    switch(card.background){
      case 'atelier': out+=line('M31 207L190 78M49 221L208 92M21 183L180 58',.7);break;
      case 'arch':out+=line('M38 222V124Q38 83 108 52Q177 83 177 124V222M58 223V128Q58 99 108 74Q158 99 158 128V223',1.3);break;
      case 'grove':out+=line('M41 223Q58 154 80 121Q109 79 182 66M64 163q-17-29-30-30m48-13q-14-31-30-31m74-1q15-29 29-30m-45 35q35 1 48 20 M204 193q-14-42-37-62',1.8);break;
      case 'rose':out+=line('M93 69Q153 96 93 145Q35 96 93 69ZM169 145Q142 205 93 145Q142 85 169 145ZM93 221Q34 195 93 145Q152 195 93 221ZM17 145Q43 85 93 145Q43 205 17 145Z');break;
      case 'moon':out+='<path d="M127 69A58 58 0 1 0 173 153A52 52 0 0 1 127 69Z" fill="'+rim+'" opacity=".26"/>';break;
      case 'glass':out+=line('M22 231V118Q22 82 125 49Q228 82 228 118V231M22 126L125 168 228 126M45 205L125 77 206 205');break;
      case 'orbit':out+='<ellipse cx="125" cy="144" rx="104" ry="41" transform="rotate(-26 125 144)" fill="none" stroke="'+rim+'" stroke-width="2"/><ellipse cx="125" cy="144" rx="104" ry="41" transform="rotate(26 125 144)" fill="none" stroke="'+rim+'" stroke-opacity=".48"/>';break;
      case 'sun':out+=line('M22 231V124Q22 71 125 43Q228 71 228 124V231 M38 218V130Q38 91 125 65Q212 91 212 130V218',1.4);break;
      case 'curse':case 'abyss':out+=line('M25 67l24 16-17 20 32 14M228 57l-19 26 13 17-26 15M38 215l21-13-6-19 18-5M222 203l-26-14 9-17-24-8',1.8);break;
    }
    if(card.tier>=2){
      const r=card.tier===4?90:card.tier===3?82:68;
      out+='<path d="'+pentacle(x,y,r,!!card.curse)+'" fill="none" stroke="'+rim+'" stroke-width="'+(card.curse?2:1.4)+'" opacity="'+(card.curse?.75:.58)+'"/>'+
        '<circle cx="'+x+'" cy="'+y+'" r="'+(r+10)+'" fill="none" stroke="'+rim+'" stroke-dasharray="3 5" opacity=".69"/>';
    }
    if(card.tier===4){
      for(let i=0;i<20;i++)out+='<path d="M125 43v14" transform="rotate('+(i*18)+' 125 145)" stroke="'+rim+'" stroke-opacity=".48"/>';
    }
    for(let i=0;i<4+card.tier*4;i++)out+=spark(25+(i*67+Number((card.number||card.id))*15)%204,52+(i*43+Number((card.number||card.id))*7)%187,i%5===0?3:1.2,rim);
    return out;
  }
  const weaponExtents={
    knife:[[-25,14],[26,11],[-15,-42],[18,-68],[24,-47],[-10,71],[10,71]],
    rapier:[[-6,-78],[0,-94],[7,-78],[-26,29],[24,28],[-7,83],[7,83]],
    axe:[[-16,-61],[46,-83],[68,-61],[61,-28],[35,-13],[-8,76],[0,86],[8,76],[-8,-76]],
    mace:[[-37,-69],[37,-69],[0,-84],[-37,-43],[37,-43],[-10,73],[0,84],[10,73]],
    scythe:[[-18,-52],[34,-79],[65,-65],[83,-48],[-15,91],[-5,85]],
    bow:[[-35,-78],[21,-70],[54,0],[43,30],[21,68],[-35,78],[-49,0]],
    spear:[[0,-104],[-21,-65],[21,-65],[-20,-42],[20,-42],[-5,78],[0,92],[5,78]],
    flamberge:[[0,-88],[17,-75],[-20,-54],[22,-24],[-24,0],[-27,18],[27,18],[-11,75],[0,84],[11,75]],
    scepter:[[0,-89],[-26,-84],[26,-84],[-30,-59],[30,-59],[-17,-40],[17,-40],[-7,76],[0,84],[7,76]],
    greatsword:[[0,-90],[-25,-64],[25,-64],[-47,15],[47,15],[-32,38],[32,38],[-12,75],[0,87],[12,75]],
  };
  const oddExtents={
    pan:[-42,42,-82,90],broom:[-30,30,-94,84],candlestick:[-29,29,-89,86],
    crossbow:[-73,73,-91,87],book:[-49,51,-65,60],lid:[-67,67,-67,67],
    sign:[-67,63,-28,64],wheel:[-62,62,-62,62],apron:[-75,75,-85,82],
    cloak:[-62,62,-67,83],mail:[-64,64,-66,64],'book-armor':[-63,63,-50,59],
    'boot-hole':[-39,69,-62,65],'lost-shoe':[-60,68,-61,57],sandals:[-55,55,-75,75],
    socks:[-53,53,-70,67],skates:[-54,54,-74,57],stilts:[-53,53,-83,85],
    flail:[-18,79,-99,85],halberd:[-49,50,-107,96],sickle:[-47,53,-93,84],
    pick:[-60,59,-79,91],tomeblade:[-42,53,-104,66],brigandine:[-65,65,-67,64],
    mantle:[-70,70,-66,76],'scale-armor':[-65,65,-60,61],ribcage:[-60,60,-63,65],
    kite:[-51,51,-80,86],door:[-53,53,-67,78],mirror:[-64,64,-77,69],fan:[-69,69,-71,59],
    hoof:[-62,62,-71,72],claws:[-60,60,-70,70],wraps:[-57,57,-68,80],'bell-boot':[-44,67,-59,65],
  };
  function stretchFor(card){
    if(card.special||card.slot&&card.slot!=='weapon')return [1,1];
    if(['rapier','flamberge','greatsword'].includes(card.art))return [.70,1.10];
    return card.art==='scepter'?[.74,1.28]:[1.07,1.10];
  }
  function fitPose(card,stretch){
    const [x,y,a,wanted]=card.pose;
    const extent=oddExtents[card.special]||({chest:[-65,65,-65,69],guard:[-56,56,-74,75],legs:[-57,57,-65,81]})[card.slot]||[-45,45,-90,90];
    const [x0,x1,y0,y1]=extent;
    const points=card.special?null:weaponExtents[card.art];
    const r=a*Math.PI/180,cos=Math.cos(r),sin=Math.sin(r);
    const posed=(points||[[x0,y0],[x0,y1],[x1,y0],[x1,y1]]).map(([px,py])=>[
      px*stretch[0]*cos-py*stretch[1]*sin,px*stretch[0]*sin+py*stretch[1]*cos,
    ]);
    const minX=Math.min(...posed.map(p=>p[0])),maxX=Math.max(...posed.map(p=>p[0]));
    const minY=Math.min(...posed.map(p=>p[1])),maxY=Math.max(...posed.map(p=>p[1]));
    const scale=Math.min(wanted,204/(maxX-minX),201/(maxY-minY));
    const clamp=(v,lo,hi)=>Math.min(hi,Math.max(lo,v));
    return [clamp(x,23-minX*scale,227-maxX*scale),clamp(y+10,42-minY*scale,266-maxY*scale),a,scale];
  }
  function equipment(card,fill=card.colors[0],slender){
    if(card.special)return classSilhouette(card,fill)||oddity(card,fill);
    if(!card.slot||card.slot==='weapon'){
      const stretch=slender||stretchFor(card).join(' ');
      return '<g transform="scale('+stretch+')">'+weapon(card,fill)+'</g>';
    }
    return CardArt.equipment(card,{palette:card.colors});
  }
  function face(card,{roll=card.roll,finish='',boostValue=boost(card,roll),layer='full'}={}){
    const t=tiers[card.tier],tag=card.curse?' · MAUDITE':'',rim=t.color;
    const name=card.name.map(escape),titleY=name.length===2?302:311;
    const stretch=stretchFor(card);
    const [x,y,a,scale]=fitPose(card,stretch);
    const ink=card.curse?'#3d1933':['#262430','#293339','#233451','#342745','#45313f'][card.tier];
    const paper=card.curse?'#211323':'#161723';
    const id='f'+(++sequence);
    const art=equipment(card,'url(#'+id+')',stretch.join(' '));
    const material=(card.material||'').toLowerCase();
    const textureType=/bois|chêne|if |frêne|bouleau|aulne/.test(material)?'wood':/cuir|lin|soie|velours|laine/.test(material)?'fabric':/cristal|quartz|verre|nacre|améthyste/.test(material)?'crystal':'metal';
    const texturePaths={
      wood:'M-18 0Q0 4 7 0T28 1 M-16 4Q0 7 8 4T28 5 M-12 8Q2 12 12 8T30 9',
      fabric:'M-6 0L8 14M0-6L14 8M-7 8L8-7M0 15L15 0',
      crystal:'M-2 0L7 13 14 6 19 11 M2 11L11 1 18 4',
      metal:'M-8 11L16-9M-4 16L20-4M1 20L25 0',
    };
    const [core,light,shade]=card.colors;
    const defs='<clipPath id="'+id+'clip"><rect x="14" y="36" width="222" height="246" rx="12"/></clipPath>'+
      '<linearGradient id="'+id+'" x1="0%" y1="1%" x2="92%" y2="98%"><stop stop-color="'+light+'"/><stop offset=".20" stop-color="'+core+'"/><stop offset=".38" stop-color="'+shade+'"/><stop offset=".48" stop-color="'+light+'"/><stop offset=".63" stop-color="'+core+'"/><stop offset="1" stop-color="'+shade+'"/></linearGradient>'+
      '<linearGradient id="'+id+'depth" x2="1" y2="1"><stop stop-color="#fbebd9" stop-opacity=".32"/><stop offset=".45" stop-color="#f4c4df" stop-opacity=".04"/><stop offset=".67" stop-color="#190e22" stop-opacity=".20"/><stop offset="1" stop-color="#140b21" stop-opacity=".54"/></linearGradient>'+
      '<radialGradient id="'+id+'spot" cx="37%" cy="28%" r="67%"><stop stop-color="'+rim+'" stop-opacity=".24"/><stop offset=".60" stop-color="'+rim+'" stop-opacity=".06"/><stop offset="1" stop-color="#0d0a16" stop-opacity=".53"/></radialGradient>'+
      '<pattern id="'+id+'etch" patternUnits="userSpaceOnUse" width="15" height="15"><path d="'+texturePaths[textureType]+'" fill="none" stroke="'+light+'" stroke-width=".8" opacity=".29"/></pattern>'+
      '<filter id="'+id+'shadow" x="-45%" y="-45%" width="200%" height="200%" color-interpolation-filters="sRGB"><feDropShadow dx="5" dy="8" stdDeviation="4.5" flood-color="#090614" flood-opacity=".84"/><feDropShadow dx="-1" dy="-2" stdDeviation="1.4" flood-color="'+rim+'" flood-opacity=".28"/></filter>'+
      '<g id="'+id+'shape">'+art+'</g><mask id="'+id+'skin" maskUnits="userSpaceOnUse" x="-144" y="-144" width="288" height="288" style="mask-type:alpha"><use href="#'+id+'shape"/></mask>';
    const border='<rect x="6.5" y="6.5" width="237" height="352" rx="12" fill="none" stroke="'+rim+'" stroke-width="'+(1+card.tier*.45)+'"/>'+
      (card.tier?'<rect x="12" y="12" width="226" height="341" rx="9" fill="none" stroke="'+rim+'" stroke-opacity=".46"/>':'')+
      (card.tier>=3?outline('M18 40V18H40M210 18H232V40M18 325v22h22m170 0h22v-22','none',rim,2):'');
    const affinity=equipmentSchools[card.school];
    const stats='+'+percent(boostValue)+' '+(card.stat||'BRA');
    const curse=card.curse?'−'+percent(card.curse.value)+' '+card.curse.stat:'';
    const info=finish?' · '+finish.toUpperCase():'';
    const statsY=348;
    const open='<svg class="card-face" xmlns="http://www.w3.org/2000/svg" width="500" height="730" viewBox="0 0 250 365" role="img" aria-label="'+escape(card.name.join(' ')+' · '+t.name+(card.curse?' · maudite':''))+'"><defs>'+defs+'</defs>';
    const illustration='<g clip-path="url(#'+id+'clip)"><g transform="translate('+x+' '+y+') rotate('+a+') scale('+scale+')"><g class="card-object" filter="url(#'+id+'shadow)">'+
      '<use href="#'+id+'shape"/><g mask="url(#'+id+'skin)"><rect x="-140" y="-140" width="280" height="280" fill="url(#'+id+'depth)"/><rect x="-140" y="-140" width="280" height="280" fill="url(#'+id+'etch)"/></g></g></g></g>';
    if(layer==='object')return open+illustration+'</svg>';
    return open+
      '<rect width="250" height="365" rx="14" fill="'+paper+'"/><path d="M14 36H236V276H14Z" fill="'+ink+'" opacity=".76"/>'+
      scene(card,card.curse?'#e687ac':rim,ink)+'<rect x="14" y="37" width="222" height="238" fill="url(#'+id+'spot)"/>'+
      (layer==='base'?'':illustration)+
      '<path d="M14 283Q125 271 236 283V358H14Z" fill="'+paper+'" stroke="'+rim+'" stroke-opacity=".46"/>'+border+
      '<g font-family="system-ui,sans-serif" text-anchor="middle"><text x="25" y="29" fill="'+rim+'" text-anchor="start" font-size="10" font-weight="750" letter-spacing=".8">'+(card.stat||'BRA')+' · '+String(card.number||card.id).padStart(2,'0')+(affinity?' · '+escape(affinity.element):'')+'</text>'+
      '<text x="224" y="29" text-anchor="end" fill="'+rim+'" font-size="12">'+t.mark+'</text>'+
      name.map((v,i)=>'<text x="125" y="'+(titleY+i*17)+'" fill="#fff1e5" font-size="'+(v.length>20?13:15)+'" font-family="Georgia,serif" font-weight="bold">'+v+'</text>').join('')+
      '<text x="125" y="335" fill="'+rim+'" font-size="8.6" font-weight="760" letter-spacing=".42">'+escape(t.name.toUpperCase()+tag+info)+'</text>'+
      '<text x="'+(curse?76:125)+'" y="'+statsY+'" fill="#a7e5bf" font-size="10.6" font-weight="800">'+stats+'</text>'+
      (curse?'<text x="172" y="'+statsY+'" fill="#f5a6b4" font-size="10.6" font-weight="800">'+curse+'</text>':'')+'</g></svg>';
  }
  return {cards,tiers,face,percent,boost,equipment};
})();
