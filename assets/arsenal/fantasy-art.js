'use strict';
/** Grounded fantasy constructions replacing novelty props; details follow rarity,
 * while each construction remains readable at collection-thumbnail size. */
const FantasyArt=(()=>{
 function make(c,id,p){
  if(!c.fantasyForm)return null;
  const [core,hi,dark,,trim]=p,metal=`url(#${id}metal)`,gilt=`url(#${id}gilt)`,cloth=`url(#${id}cloth)`,glass=`url(#${id}glass)`,form=c.fantasyForm,tier=c.tier;
  const P=(d,f=metal,s=trim,w=1)=>`<path d="${d}" fill="${f}" stroke="${s}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"/>`;
  const L=(d,s=hi,w=.8)=>P(d,'none',s,w);
  const C=(x,y,r,f=metal,s=trim,w=.8)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${f}" stroke="${s}" stroke-width="${w}"/>`;
  const T=(s,x=0,y=0,angle=0,scale=1)=>`<g transform="translate(${x} ${y}) rotate(${angle}) scale(${scale})">${s}</g>`;
  const sym=s=>s+`<g transform="scale(-1 1)">${s}</g>`;
  const gem=(x,y,r=6)=>T(P(`M0-${r}L${r*.7} 0 0 ${r}-${r*.7} 0Z`,core,hi,.7)+P(`M0-${r}V${r}L-${r*.7} 0Z`,dark,hi,.4)+P(`M0-${r}L${r*.7} 0H0Z`,hi,'none'),x,y);
  const nail=(x,y)=>C(x,y,1.4,gilt,dark,.4)+C(x-.3,y-.4,.4,hi,'none');
  const scroll=(x,y,s=.7)=>T(L('M0 27C13 12-9 7 0-3C11-14 24 0 14 4C7 7 6-3 11-5M0 14C17 6 21 24 10 21',trim,.8),x,y,0,s);
  const glyphs=(x,y,n=5)=>Array.from({length:n},(_,i)=>T(L('M-2-3V3m0-5 4 1-4 2m0-2-2 1',hi,.65),x,y+i*10)).join('');
  const shaft=(from=-20,to=86)=>P(`M-4 ${from}H4L5 ${to} 0 ${to+6}-5 ${to}Z`,dark,trim,1)+L(`M-1 ${from}V${to}`,core,1.2)+Array.from({length:Math.floor((to-from)/9)},(_,i)=>L(`M-4 ${from+5+i*9}l8-3`,trim,1)).join('')+P(`M-6 ${from}h12v5h-12ZM-6 ${to-4}h12v5h-12Z`,gilt,hi,.5);
  const scale=(x,y,w=13)=>P(`M${x-w/2} ${y}Q${x} ${y+3} ${x+w/2} ${y}L${x+w/2-1} ${y+8}Q${x} ${y+15} ${x-w/2+1} ${y+8}Z`,metal,trim,.7);
  const feather=(x,y,r=28,a=0)=>T(P(`M0 0Q-11-${r*.6} 0-${r}Q10-${r*.5} 0 0Z`,metal,hi,.6)+L(`M0-3V-${r-3}M0-10l-4-6M0-15l4-6`,trim,.5),x,y,a);
  const sun=(r=16)=>Array.from({length:12},(_,i)=>T(L(`M0-${r+4}V-${r+11}`,trim,i%2?.7:1.5),0,0,i*30)).join('')+C(0,0,r,gilt,hi,1)+C(0,0,r-5,dark,hi,.6)+gem(0,0,r*.6);
  let s='';
  if(c.slot==='weapon'){
   if(['shortbow','falconbow'].includes(form)){
    s=P('M-7-98Q-35-75-19-45Q-1-18-12 0Q-1 18-19 45Q-35 75-7 98L5 92Q-15 73-3 46Q14 19 3 0Q14-19-3-46Q-15-73 5-92Z',cloth,trim,1.4)+L('M0-92Q-21-73-10-45Q8-18-4 0Q8 18-10 45Q-21 73 0 92',hi,1.1)+L('M5-93L49 0 5 93',hi,.8)+L('M-12 0H70',trim,1.2)+P('M64-5L76 0 64 5Z',metal,hi,.6)+P('M-15-9H4V10H-15Z',dark,trim,1);
    if(form==='falconbow'){s+=sym(feather(-12,-48,30,-35))+T(feather(0,44,31,155),-8,3)+gem(-5,0,10)+scroll(-16,-57,.5)+scroll(-15,40,.5);}
   }else if(['hatchet','warpick','hammer','mace'].includes(form)){
    s=shaft(-50,90);
    if(form==='hatchet')s+=P('M-5-65Q-30-91-49-75Q-36-54-46-27Q-25-21-4-43L15-48 20-68 4-70Z',metal,hi,1.1)+L('M-41-71Q-27-60-37-33',trim,1.2);
    if(form==='warpick')s+=P('M-62-52Q-29-84 6-69L31-68 42-55 31-43 5-51Q-28-66-62-52Z',metal,hi,1)+L('M-48-56Q-23-69 3-60H29',trim,1.1);
    if(form==='hammer')s+=P('M-43-76L40-72 45-62 39-29-42-34-47-46Z',metal,hi,1.2)+P('M-43-76L-32-65-34-34-42-34-47-46Z',dark,trim,.8)+P('M-25-66H25V-40H-25Z',dark,trim,1)+gem(0,-53,10);
    if(form==='mace'){s+=P('M-7-82H7V-28H-7Z',gilt,hi,.7);for(let i=-1;i<=1;i++)s+=P(`M${i*10}-80L${i*18-5}-68 ${i*18-7}-43 ${i*9}-27 ${i*8+7}-44 ${i*8+6}-68Z`,metal,hi,.9);s+=T(sun(8),0,-53);}
   }else if(['staff','javelin'].includes(form)){
    s=shaft(form==='staff'?-39:-67,93);
    if(form==='javelin')s+=P('M0-112L10-83 6-67-6-67-10-83Z',metal,hi,1)+L('M0-105V-69',trim,1)+P('M-3 71L-16 60-8 83-3 88Z',cloth,hi,.5);
    else{s+=P('M-6-39Q-33-68-14-88L-5-69 0-62 5-69 14-88Q33-68 6-39Z',metal,trim,1.2)+gem(0,-65,18)+P('M-7-31H7V-23H-7Z',gilt,hi,.6);if(tier>=1)s+=glyphs(0,-12,6);}
   }else if(form==='bell'){
    s=C(0,-76,10,'none',trim,2)+P('M-8-65L8-65 13-47Q31-40 33-5L51 27V39H-51V27L-33-5Q-31-40-13-47Z',metal,hi,1.2)+P('M-51 27Q0 37 51 27V39H-51Z',gilt,hi,.8)+C(0,44,10,metal,trim,1)+sym(scroll(19,-31,.75))+gem(0,-25,9);
   }else if(form==='katar'){
    s=P('M0-104L19-58 12-9H-12L-19-58Z',metal,hi,1.1)+L('M0-95V-14',trim,1.5)+P('M-20-12H-12V80H-20ZM12-12H20V80H12Z',metal,trim,1)+P('M-17 17H17V25H-17ZM-17 57H17V65H-17Z',dark,hi,.8);
   }else{
    const blade=()=>{
     let b=shaft(26,83)+P(form==='falchion'?'M-5-105Q23-89 17-56L9 21H-12L-15-59Z':'M0-111L14-89 11 19 0 28-11 19-14-89Z',metal,hi,1.1)+L('M0-101V17',trim,1.2)+P('M-29 18Q0 29 29 18L23 30H-23Z',gilt,hi,.9)+gem(0,27,tier>=3?8:4);
     if(tier>=2||c.damageTypes.includes('ARCANIST'))b+=glyphs(0,-74,7)+sym(scroll(12,18,.4));return b;
    };
    if(form==='twinblades')s=T(blade(),-31,0,-12,.88)+T(blade(),31,6,12,.88);
    else if(form==='dagger')s=T(blade(),0,8,0,.78);
    else{s=blade();if(form==='sunblade')s+=T(sun(14),0,25)+sym(feather(16,25,33,60));}
   }
   if(tier>=3)s+=scroll(-3,38,.36)+gem(0,88,4);
   return s;
  }
  if(c.slot==='chest'){
   if(form==='cloak'){
    s=P('M-20-71L-47-54-63 78-35 66-16 86 0 75 18 87 35 66 63 78 47-54 20-71Z',cloth,trim,1.2)+P('M-20-69Q0-52 20-69L36-48 16-28H-16L-36-48Z',metal,hi,.8)+sym(L('M23-34L38 63M32-28L49 66',hi,.7))+gem(0,-35,7);
    if(tier>=2)s+=sym(scroll(28,23,.8))+glyphs(0,-13,7);
   }else{
    s=P('M-24-65L-52-47-63-14-43-3-34-25-34 54-17 74H17L34 54 34-25 43-3 63-14 52-47 24-65 10-49H-10Z',form==='gambeson'?cloth:dark,trim,1.2);
    s+=sym(P('M26-58L48-45 54-22 40-15 31-32Z',metal,hi,.9));
    if(form==='gambeson'){
     for(let i=0;i<6;i++)s+=L(`M-31 ${-40+i*18}l62 22M31 ${-40+i*18}l-62 22`,trim,.75);
     s+=L('M0-44V66',dark,2);for(let i=0;i<6;i++)s+=nail(0,-32+i*16);
    }else for(let row=0;row<8;row++)for(let col=0;col<5;col++)s+=scale(-25+col*12.5,-44+row*13,form==='dragonscale'?14:12);
    s+=P('M-34 29H34V38H-34Z',dark,trim,1)+P('M-7 28H7V40H-7Z',gilt,hi,1);
    if(tier>=2)s+=gem(0,-32,9)+sym(scroll(37,-44,.45));
    if(form==='dragonscale')s+=sym(P('M34-58L42-81 49-60 60-65 54-47 65-36 44-32Z',gilt,hi,.8));
   }
   return s;
  }
  if(c.slot==='guard'){
   if(form==='ward'){
    s=C(0,0,63,'none',trim,2)+C(0,0,58,'none',hi,.6)+P('M0-76L48-29 41 39 0 76-41 39-48-29Z',metal,hi,1.2)+P('M0-61L35-23 30 30 0 58-30 30-35-23Z',glass,trim,.9)+L('M0-61V58M-35-23L30 30M35-23L-30 30',hi,.9)+gem(0,0,18);
    for(let i=0;i<8;i++)s+=T(P('M-2-65L0-71 2-65Z',gilt,hi,.6),0,0,i*45);
   }else if(form==='buckler'||form==='sunshield'){
    s=C(0,0,71,metal,trim,1.7)+C(0,0,62,dark,hi,.8)+C(0,0,53,cloth,trim,1.2);
    for(let i=0;i<16;i++){const a=i*Math.PI/8;s+=nail(Math.cos(a)*66,Math.sin(a)*66);}
    s+=form==='sunshield'?sun(27):C(0,0,22,metal,hi,1)+C(0,0,14,metal,trim,.8);
    if(tier>=2)s+=sym(scroll(35,-23,.8));
   }else{
    const d=form==='pavise'?'M-47-66Q0-88 47-66L54 67Q0 81-54 67Z':form==='leafshield'?'M0-83Q73-49 53 23Q40 61 0 85Q-40 61-53 23Q-73-49 0-83Z':'M-54-66L0-79 54-66 49 23Q29 62 0 81Q-29 62-49 23Z';
    s=P(d,metal,hi,1.3)+T(P(d,cloth,trim,1.4),0,0,0,.86);
    if(form==='leafshield')s+=L('M0-67V68M0-40L-31-25M0-16L37-29M0 5L-38 13M0 29L26 16M0 47L-15 39',hi,1.5)+gem(0,-12,tier>=2?12:6);
    else{s+=P('M-7-62H7V63H-7Z',metal,trim,.9)+P('M-38-18H38V-6H-38Z',metal,hi,.8)+gem(0,-12,tier>=3?13:7);if(tier>=2)s+=sym(scroll(24,18,.8));}
    for(const side of [-1,1])for(let i=0;i<5;i++)s+=nail(side*(form==='pavise'?42:44-i*6),-50+i*25);
    if(tier>=3)s+=sym(P('M34-62L42-80 47-60 56-53 41-47Z',gilt,hi,.7))+glyphs(0,12,4);
   }
   return s;
  }
  if(c.slot==='legs'){
   for(const side of [-1,1]){
    let b=P('M-20-73L17-72 19-49 11-16 14 28 10 42 32 60 24 76H-22L-29 63-20 36-22-5-25-42Z',['rune','windboots','pilgrimboots'].includes(form)?cloth:dark,trim,1.1);
    if(['plate','scalegreaves'].includes(form)){
     b+=P('M-19-68L14-67 14-49 7-29-19-23-23-40Z',metal,hi,.9)+P('M-18-23L8-26 18-9 9 9-20 12-25-7Z',metal,hi,.9);
     if(form==='scalegreaves')for(let row=0;row<4;row++)for(let col=0;col<3;col++)b+=scale(-14+col*10,10+row*11,11);
     else b+=P('M-17 14L9 11 10 40-2 50-20 42Z',metal,hi,.9)+L('M-7 18L-4 40',trim,1.5);
     for(let i=0;i<4;i++)b+=P(`M-21 ${44+i*7}L8 ${42+i*7} 22 ${49+i*7}-24 ${52+i*7}Z`,metal,trim,.8);
     if(tier>=2)b+=gem(-3,-6,7)+scroll(-12,-60,.4);
    }else{
     b+=P('M-22-65L17-64 16-48-21-49Z',form==='pilgrimboots'?gilt:metal,hi,.7)+P('M-22 54L10 47 29 62 22 72-22 71Z',metal,trim,.8)+L('M-16 70H21',hi,1);
     if(form==='leather'||form==='rangerboots'||form==='fur'){
      for(let i=0;i<3;i++)b+=P(`M-20 ${-35+i*27}l32-6 1 8-33 6Z`,form==='rangerboots'?cloth:metal,trim,.7)+P(`M0 ${-40+i*27}h9v9H0Z`,'none',hi,1);
      b+=L('M-13-44L-9 36',hi,.6);
     }else{b+=P('M-9-43H5L9 39-5 47-14 36Z',metal,hi,.7)+glyphs(-2,-32,tier>=3?7:5);if(tier>=2)b+=gem(-2,-5,8);}
     if(form==='fur')b+=P('M-21-75L-29-64-23-53-27-43-13-47-5-38 2-45 15-39 14-52 23-60 16-74Z','#b9b4a3',hi,.7)+L('M-17-66l4 18m4-19 3 22m7-22-1 19m8-16-3 14',dark,.7);
     if(form==='windboots'){for(let j=0;j<4;j++)b+=feather(-19,19+j*4,26+j*4,-40-j*8);b+=L('M-24 81Q-3 74 25 82',hi,1);}
     if(form==='pilgrimboots')b+=T(sun(7),-2,-18,.1)+scroll(-10,13,.4);
    }
    if(tier>=3){b+=scroll(-11,-60,.4)+scroll(-11,16,.45);for(let j=0;j<4;j++)b+=nail(-18,12+j*8);}
    s+=T(b,side*32,side===-1?-7:7,side===-1?-8:8,.95);
   }
   return s;
  }
  throw Error('Unknown fantasy construction: '+c.id+' / '+form);
 }
 return {make};
})();
