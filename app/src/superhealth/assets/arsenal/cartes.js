'use strict';
const CardArt=(()=>{
  const counts=[1,1,1,1];
  const rarities=[
    ['Commune','#c4c7d0','◆'],['Peu commune','#80d8aa','◆◆'],['Rare','#8cc5ff','◆◆◆'],['Épique','#e5a9f9','✦'],
  ];
  const families={
    weapon:{name:'Armes',stat:'BRA',accent:'#ed8ba4',shade:'#4b2037',forms:['Épée','Rapière','Hache','Dague','Masse','Faux','Lance','Marteau']},
    chest:{name:'Armures',stat:'PEC',accent:'#d7a2e3',shade:'#40294d',forms:['Cuirasse','Plastron','Brigandine','Haubert','Corselet','Broigne','Lorica','Armure']},
    guard:{name:'Boucliers',stat:'DOS',accent:'#8ad9e7',shade:'#214858',forms:['Écu','Pavois','Rondache','Targe','Égide','Bocle','Bouclier','Scutum']},
    legs:{name:'Jambières',stat:'JAM',accent:'#e7b28a',shade:'#56382c',forms:['Grèves','Jambières','Cnemides','Cuissards','Guêtres','Garde-jambes','Sabots','Bottes']},
  };
  const epithets=[
    ['du guet','du pèlerin','des faubourgs','de la chapelle','du gué','du scriptorium','de l’écuyer','du bastion','du cloître','du relais','de la patrouille','de l’atelier','des faubourgs','du gué'],
    ['des épines','de la Rose noire','du guetteur gris','de la cour d’ivoire','du chêne creux','de l’aube scellée','de la crypte','des sentinelles','de la cour d’ivoire','du voile','de l’aube scellée','du chêne creux'],
    ['de la Lune creuse','du vœu brisé','des corbeaux d’argent','de l’ermite rouge','du chant funèbre','du lys spectral','du sépulcre','de la cour sans roi','du songe pourpre'],
    ['du Cœur des ronces','de la Nuit couronnée','du Vitrail vivant','de la Rose éternelle','du Dernier Serment'],
  ];
  const catalog=Object.entries(families).flatMap(([slot,info])=>{
    let number=0;
    return counts.flatMap((count,rarity)=>Array.from({length:count},(_,i)=>{
      const form=(i*3+rarity*2)%8;
      return {id:i+'-'+slot+'-'+rarity,slot,rarity,form,variant:i,number:++number,
        name:info.forms[form]+' '+epithets[rarity][i],color:info.accent};
    }));
  });
  const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const within=(n,m)=>((n%m)+m)%m;
  const steelColors=['#ced2dd','#d8c39c','#9ebcca','#bcb3d8','#d5c0b8','#aabdc7','#d7bd9a','#bdb7d1'];
  const medallion='<path d="M0-11l9 11-9 11-9-11Z"/>';
  const path=(d,fill,stroke='none',width=1)=>'<path d="'+d+'" fill="'+fill+'" stroke="'+stroke+'" stroke-width="'+width+'" stroke-linejoin="round"/>';
  const barbs=(stroke,y)=>'<path d="M-49 '+y+'l13-12 6 11m79 1-13-12-6 11" stroke="'+stroke+'" fill="none" stroke-width="1.4"/>';
  const weaponPaths=[
    'M0-79L13-62 8 28H-8L-13-62Z','M0-86L7-69 5 31H-5L-7-69Z',
    'M-7-74H7V-55Q25-61 40-79Q57-62 53-23Q28-39 7-34H-7Q-28-39-53-23Q-57-62-40-79Q-25-61-7-55Z',
    'M0-57L17-34 10 16 0 26-10 16-17-34Z',
    'M-17-60L-12-78H12L17-60 31-53 18-41 17-23 0-31-17-23-18-41-31-53Z',
    'M-3-73Q30-91 56-61Q32-69 10-42L4-30-11-40Z',
    'M0-90L19-59 11-36 0-26-11-36-19-59Z',
    'M-31-67L-23-77H23L31-67V-39L23-31H-23L-31-39Z',
  ];
  function weapon(item,tone,metal,gold){
    const shape=weaponPaths[item.form];
    const long=item.form>=2&&item.form!==3;
    const handle=long?'M-5-37H5V76L0 83-5 76Z':'M-7 29H7L6 70 0 78-6 70Z';
    const cross=long?'':'M-30 19L-16 27H16L30 19 23 34H-23Z';
    return '<g transform="rotate('+(item.form===5?-25:21)+')">'+path(handle,'#543543',gold,2.3)+path(shape,metal,gold,2.1)+
      (cross?path(cross,gold,'#7c5464',1.5):'')+
      '<path d="M0 '+(long?-66:-60)+'v'+(long?31:70)+'" fill="none" stroke="'+tone+'" opacity=".81" stroke-width="1.8"/>'+
      path('M-4 57l8-5m-8 14 8-5','none',gold,1.7)+'</g>';
  }
  function armor(item,tone,metal,gold){
    const shoulder=[52,42,61,45,48,58,46,64][item.form],bottom=[35,27,39,31,34,38,33,44][item.form];
    let out=path('M-'+shoulder+' -41L-33-63-15-44Q0-38 15-44L33-63 '+shoulder+' -41 43-18 '+bottom+' 52 16 67H-16L-'+bottom+' 52-43-18Z',metal,gold,2.2);
    out+=path('M-'+(shoulder-5)+' -38L-34-53-17-38-32-23-43-25ZM'+(shoulder-5)+' -38L34-53 17-38 32-23 43-25Z',gold,'#7b5268',1.4);
    const ribs=2+item.form%4;
    for(let i=0;i<ribs;i++)out+=path('M-35 '+(i*10+4)+'L0 '+(i*10+22)+' 35 '+(i*10+4),'none','#574158',3.2);
    if(item.form%2===1){
      for(let i=0;i<3;i++)for(let j=0;j<8;j++)out+='<circle cx="'+(-28+j*8)+'" cy="'+(-20+i*6)+'" r="1.25" fill="'+tone+'" opacity=".7"/>';
    }
    return out+'<g transform="translate(0 -17)">'+medallion.replace('/>',' fill="'+tone+'" stroke="'+gold+'" stroke-width="1.7"/>')+'</g>';
  }
  function shield(item,tone,metal,gold){
    const outlines=[
      'M0-65L51-44V-3Q48 36 0 69Q-48 36-51-3V-44Z','M-37-63H37L47-38V9L29 60H-29L-47 9V-38Z',
      'M0-57A57 57 0 1 1-.1-57Z','M0-58L53-19 40 47 0 59-40 47-53-19Z',
      'M0-73Q30-58 48-52V12Q34 53 0 74Q-34 53-48 12V-52Q-30-58 0-73Z',
      'M0-49Q46-49 47 0Q46 49 0 49Q-46 49-47 0Q-46-49 0-49Z',
      'M-39-52Q0-62 39-52L54-30V14Q32 56 0 64Q-32 56-54 14V-30Z',
      'M-46-62H46V28L30 63H-30L-46 28Z',
    ];
    const d=outlines[item.form];
    const motifs=['M0-35V35M-32 0H32','M-24-27L0-5 24-27M-24 13L0 35 24 13','M0-32l31 32-31 32-31-32Z','M-29-10L0-29 29-10 0 25Z'];
    return path(d,gold,'#f0d9ad',2)+'<g transform="scale(.83)">'+path(d,metal,'#553748',2.3)+
      path(motifs[item.form%4],'none',gold,2.6)+'</g><circle r="'+(9+item.form%3*2)+'" fill="'+tone+'" stroke="'+gold+'" stroke-width="2"/>';
  }
  function legs(item,tone,metal,gold){
    const heel=[17,21,24,16,22,19,26,23][item.form];
    const leg=path('M-51-54L-28-64-12-51-16-9-18 35-21 61-38 67-'+(heel+28)+' 53-47 20-55-14Z',metal,gold,2.1)+
      path('M-53-47L-28-54-13-46-17-29-28-19-49-28Z',gold,'#845a63',1.6)+
      path('M-44-11l13 6 12-7M-44 9l13 6 11-7M-42 28l12 5 10-7M-42 45l11 6 9-7','none','#624554',2.9)+
      '<path d="M-28-43l6 7-6 7-6-7Z" fill="'+tone+'"/>';
    return leg+'<g transform="translate(0 '+(item.form%2?13:7)+') scale(-1 1)">'+leg+'</g>';
  }
  function equipment(item,{equipped=false,palette}={}){
    const tone=palette?.[1]||item.color;
    const metal=palette?.[0]||steelColors[within(item.form+item.variant+item.rarity,8)];
    const gold=['#8d807c','#bba991','#d8c2a8','#efcb8a','#f9d48d'][item.rarity];
    const art=({weapon,guard:shield,chest:armor,legs})[item.slot](item,tone,metal,gold);
    const scale=equipped?.72:1;
    return '<g transform="scale('+scale+')" stroke-linejoin="round">'+art+'</g>';
  }
  function leaves(x,y,color,flip=false){
    return '<g transform="translate('+x+' '+y+') scale('+(flip?-1:1)+' 1)" fill="none" stroke="'+color+'"><path d="M0 58Q12 31 2 7Q0-2 17-13M4 41q14-7 21-1-6 11-21 1M7 22q-13-9-18-4 6 10 18 4M9-2q15-10 20-3-5 7-20 3"/></g>';
  }
  function face(item,{finish='',boost='',level=0,style='sceau'}={}){
    const info=families[item.slot],tier=item.rarity,[label,rim,symbol]=rarities[tier];
    const variant=item.variant,code=info.stat+' · ÉTUDE '+String(item.number).padStart(2,'0');
    const words=item.name.split(' ');
    let first=item.name,second='';
    if(item.name.length>22){
      let k=1;
      while(k<words.length-1&&words.slice(0,k+1).join(' ').length<21)k++;
      first=words.slice(0,k).join(' ');second=words.slice(k).join(' ');
    }
    const motif=item.form%4;
    const sky=within(variant+tier*2,4);
    const bg=['#211f32','#272137','#1b253b','#312333'][sky];
    const r=tier===3?103:tier===2?87:74;
    const stars=Array.from({length:[4,10,18,27][tier]},(_,i)=>{
      const x=24+(i*71+variant*19)%203,y=58+(i*43+variant*11)%193;
      return '<circle cx="'+x+'" cy="'+y+'" r="'+(i%4===0?1.55:.65)+'" fill="'+rim+'" opacity="'+(i%3===0?.64:.30)+'"/>';
    }).join('');
    let background='<rect x="15" y="40" width="220" height="217" rx="14" fill="'+info.shade+'" opacity="'+(.30+tier*.13)+'"/>'+
      '<circle cx="125" cy="146" r="'+r+'" fill="'+bg+'" stroke="'+rim+'" stroke-opacity=".38"/>';
    if(tier>0)background+='<path d="M33 237V128Q33 77 125 49Q217 77 217 128V237Z" fill="none" stroke="'+rim+'" stroke-opacity=".40"/>'+leaves(35,173,rim)+leaves(215,173,rim,true);
    if(tier>1){
      background+='<circle cx="125" cy="146" r="'+(r-8)+'" fill="none" stroke="'+rim+'" stroke-dasharray="2 5" stroke-opacity=".65"/>';
      if(motif===0)background+=path('M125 67L198 183H52Z M125 225L52 109H198Z','none',rim,1);
      if(motif===1)background+='<ellipse cx="125" cy="146" rx="95" ry="40" transform="rotate(-35 125 146)" fill="none" stroke="'+rim+'" stroke-opacity=".65"/>';
      if(motif===2)background+=path('M125 59L209 146 125 232 41 146Z','none',rim,1);
      if(motif===3)background+='<circle cx="125" cy="146" r="53" fill="none" stroke="'+rim+'" stroke-dasharray="14 5"/>';
    }
    if(tier===3){
      const crown=Array.from({length:16},(_,i)=>'<path d="M125 40v17m0 171v18" transform="rotate('+(i*22.5)+' 125 143)"/>').join('');
      background+='<g stroke="#f6d696" opacity=".52">'+crown+'</g>';
    }
    if(style==='vitrail'){
      const panes=['#532745','#3d4a79','#7b3b61','#6d5357'];
      const glass=Array.from({length:8},(_,i)=>{
        const x=15+i*30;
        return '<path d="M'+x+' 253L'+(24+i*28)+' 86 125 40 '+(x+33)+' 253Z" fill="'+panes[(i+variant)%4]+'" stroke="'+rim+'" stroke-opacity=".32"/>';
      }).slice(0,5).join('');
      background='<path d="M19 252V112Q19 74 125 40Q231 74 231 112V252Z" fill="#252536" stroke="'+rim+'" stroke-width="1.5"/>'+
        '<g opacity="'+(.52+tier*.10)+'">'+glass+'<path d="M19 126L125 182 231 126M20 211L125 106 231 211" fill="none" stroke="'+rim+'" stroke-opacity=".65"/></g>'+
        '<circle cx="125" cy="147" r="'+(tier===3?81:67)+'" fill="#262439" fill-opacity=".85" stroke="'+rim+'" stroke-width="'+(1+tier*.6)+'"/>'+
        (tier>=2?'<circle cx="125" cy="147" r="77" fill="none" stroke="'+rim+'" stroke-dasharray="3 5"/>':'');
    }
    if(style==='codex'){
      const notes=Array.from({length:3+tier},(_,i)=>'<path d="M33 '+(68+i*9)+'h'+(14+(i*13)%26)+'m139 '+(147-i*3)+'h'+(12+(i*9)%19)+'" stroke="#815b54" stroke-width="1.4" opacity=".70"/>').join('');
      background='<path d="M24 51L220 46 231 60 227 244 216 251 23 245 16 229 18 65Z" fill="#dcc49f" stroke="#af8059" stroke-width="2.2"/>'+
        '<path d="M29 63H218V234H29Z" fill="#ead6ae" fill-opacity=".60" stroke="#ab7b61"/>'+notes+
        '<circle cx="125" cy="146" r="'+(tier===3?89:69)+'" fill="none" stroke="#96736e" stroke-dasharray="3 4"/>'+
        (tier>0?'<path d="M60 147H191M125 69V228M55 84L83 113M196 205L166 179" stroke="#9b7363" stroke-opacity=".56"/>':'')+
        (tier>=2?'<path d="M38 205l19-12 21 12-20 22Z M177 77l12-12 16 14-13 13Z" fill="#914a61" opacity=".82"/>':'')+
        (tier===3?'<path d="M34 65l16 12 14-12m122 164 16-12 14 12M47 219l24-15M190 70l19 18" fill="none" stroke="#a97449" stroke-width="2"/>':'');
    }
    const scale=[.93,1.00,1.10,1.25][tier]*(item.slot==='guard'?.97:1);
    const border='<rect x="6" y="6" width="238" height="338" rx="12" fill="none" stroke="'+rim+'" stroke-width="'+(tier===0?1.6:2.8)+'"/>'+
      (tier>0?'<rect x="11" y="11" width="228" height="328" rx="10" fill="none" stroke="'+rim+'" stroke-width="1" stroke-opacity=".53"/>':'')+
      (tier>1?path('M17 39V18H42M208 18H233V39M17 310v23h25m166 0h25v-23','none',tier===3?'#f3d291':rim,2.6):'');
    return '<svg class="card-face" xmlns="http://www.w3.org/2000/svg" width="500" height="700" viewBox="0 0 250 350" role="img" aria-label="'+escape(item.name+' · '+label)+'">'+
      '<rect width="250" height="350" rx="14" fill="'+bg+'"/>'+background+stars+
      '<g transform="translate(125 146) scale('+scale+')"><g class="card-object">'+equipment(item)+'</g></g>'+
      '<path d="M13 252Q125 240 237 252V331H13Z" fill="'+bg+'" stroke="'+rim+'" stroke-opacity=".46"/>'+
      border+'<g font-family="system-ui,sans-serif" text-anchor="middle">'+
      '<text x="26" y="29" text-anchor="start" fill="'+rim+'" font-size="10" font-weight="800" letter-spacing=".8">'+code+'</text>'+
      '<text x="224" y="29" text-anchor="end" fill="'+rim+'" font-size="12">'+symbol+'</text>'+
      '<text x="125" y="'+(second?275:286)+'" fill="#fff1e8" font-family="Georgia,serif" font-size="18" font-weight="bold">'+escape(first)+'</text>'+
      (second?'<text x="125" y="296" fill="#fff1e8" font-family="Georgia,serif" font-size="17">'+escape(second)+'</text>':'')+
      '<text x="125" y="317" fill="'+rim+'" font-size="10" font-weight="750" letter-spacing=".72">'+escape(label.toUpperCase()+(finish?' · '+finish.toUpperCase():''))+'</text>'+
      (boost?'<text x="125" y="333" fill="#dfe6ea" font-size="9">'+escape(boost+(level?' · NIV. '+level:''))+'</text>':'')+'</g></svg>';
  }
  return {catalog,families,rarities,counts,equipment,face};
})();
