'use strict';
/** Hand-constructed SVG equipment. Each ID selects its construction in RefinementData.
 * Local coordinates are physical parts: no mesh deformation, skew or random silhouettes.
 * Steel, cloth, timber and leather have separate treatments. Ornaments are attached to
 * components; rings, lacing and strings remain continuous at thumbnail resolution. */
const RefinedArt=(()=>{
 const fabrics={oxblood:['#733c40','#291d26'],navy:['#40596d','#182733'],ochre:['#a7864e','#4e3a24'],charcoal:['#55565a','#23252a'],moss:['#617450','#293c30'],umber:['#886047','#3b2c26'],teal:['#447b78','#1d383c'],indigo:['#64618d','#2b294a'],ivory:['#ddd3b5','#93886c'],plum:['#77526f','#342638']};
 const P=(d,f='none',s='#171e25',w=1.2)=>`<path d="${d}" fill="${f}" stroke="${s}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`;
 const L=(d,s='#c6c8bd',w=.8)=>P(d,'none',s,w);
 const C=(x,y,r,f,s='#171e25',w=1)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${f}" stroke="${s}" stroke-width="${w}"/>`;
 const E=(x,y,rx,ry,f,s='#171e25',w=1)=>`<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="${f}" stroke="${s}" stroke-width="${w}"/>`;
 const T=(s,x=0,y=0,a=0,k=1)=>`<g transform="translate(${x} ${y}) rotate(${a}) scale(${k})">${s}</g>`;
 const mirror=s=>s+`<g transform="scale(-1 1)">${s}</g>`;
 const repeat=(n,f)=>Array.from({length:n},(_,i)=>f(i)).join('');
 function make(c,id){
  const r=c.refinement;if(!r)return null;
  const original=r.form,aliases={splintcoat:'lamellar',leathercoat:'leather',scalehauberk:'scale',ceremonialcape:'cloak',regalia:'plate',phoenixshield:'kite',dragonshield:'heater',hornshield:'heater',moonscutum:'scutum',foldedboots:'boots',tallcavalry:'boots',pilgrimshoes:'slippers',runecuffs:'slippers',drakegreaves:'scalegreaves',tideboots:'boots'},f=aliases[original]||original,v=r.cut,t=c.tier,rich=t>=3,legend=t===4,magic=c.damageTypes.includes('ARCANIST'),holy=c.damageTypes.includes('DIVINE');
  const [dye,dyeDark]=fabrics[r.lining],steel=`url(#${id}steel)`,gold=`url(#${id}brass)`,wood=`url(#${id}wood)`,leather=`url(#${id}leather)`,cloth=`url(#${id}fabric)`,bone=`url(#${id}ivory)`,black='#222d39',edge='#cbd3d4',ink='#192128';
  const jewel=r.jewel|| (holy?'#e7ba66':magic?(r.motif==='moon'?'#a36ec0':'#73b9db'):r.motif==='leaf'?'#6ca584':'#a8464d');
  const grad=(key,colors)=>`<linearGradient id="${id+key}" x1="0" y1="0" x2="1" y2=".25">${colors.map(([p,col])=>`<stop offset="${p}" stop-color="${col}"/>`).join('')}</linearGradient>`;
  const metalTones={bronze:['#514332','#af8853','#e2c48c','#b39261','#59452e'],silver:['#566a7d','#c9dbe4','#f4f1dc','#b1cad7','#4e6378'],gold:['#75512d','#d4ad65','#ffedb9','#bc965c','#775a35'],black:['#1c2632','#4d6273','#a6c0c9','#435867','#17232e']};
  const darkSteel=rich&&['moon','skull'].includes(r.motif);
  const defs=grad('steel',metalTones[r.metal]?metalTones[r.metal].map((c,i)=>[[0,.32,.48,.55,1][i],c]):darkSteel?[[0,'#202a37'],[.32,'#536479'],[.48,'#9cabba'],[.55,'#4d5b71'],[1,'#222b3b']]:[[0,'#445362'],[.32,'#9daeb8'],[.48,'#d2d9d6'],[.55,'#8e9fa8'],[1,'#465562']])+grad('brass',[[0,'#665032'],[.35,'#b99b62'],[.52,'#e0ca8c'],[1,'#826238']])+grad('wood',[[0,'#402b21'],[.4,v%2?'#947044':'#805337'],[1,'#4d3224']])+grad('leather',[[0,'#382b28'],[.42,v%2?'#775540':'#63493b'],[1,'#3a2a25']])+grad('fabric',[[0,dyeDark],[.36,dye],[.6,dye],[1,dyeDark]])+grad('ivory',[[0,'#8f856f'],[.4,'#e1d9bc'],[.7,'#c1b595'],[1,'#8d816d']])+`<pattern id="${id}mail" width="8" height="7" patternUnits="userSpaceOnUse">${P('M0 0H8V7H0Z','#3b4851','none')}${E(2,2,2.4,2,'none','#a4b0b5',.8)}${E(6,5.5,2.4,2,'none','#8d9fa8',.8)}</pattern>`;
  const rivet=(x,y,k=1)=>C(x,y,1.45*k,gold,ink,.6)+C(x-.35*k,y-.4*k,.35*k,'#e9dfbe','none');
  const gem=(x,y,k=6)=>T(C(0,0,k+2,gold,ink,.8)+P(`M0-${k}L${k*.72} 0 0 ${k}-${k*.72} 0Z`,jewel,edge,.7)+P(`M0-${k}L-${k*.72} 0 0 ${k}V0Z`,black,'none')+P(`M0-${k}L${k*.72} 0 0-${k*.22}Z`,'#e5ffff','none')+L(`M0-${k}L${k*.72} 0 0 ${k}`,'#e5ffff',.65)+C(k*.22,-k*.35,k*.14,'#fff8db','none'),x,y);
  const scroll=(x,y,k=1)=>T(L('M0 23Q-5 10 0-3C12-16 22-2 13 3C7 5 4-2 10-4M0 12C-17 1-19 17-10 17Q-5 17-6 12',rich?'#e5bf79':'#92958c',rich?1:.8),x,y,0,k);
  const rune=(x,y,n=3)=>repeat(n,i=>T(L(i%3===0?'M-2-3V3M-2-3L2 0-2 1':i%3===1?'M-2 3V-3L2 0V3':'M-2-3L2 0-2 3M0-2V3',jewel,.8),x,y+i*9));
  function badge(kind=r.motif,k=1){
   let b='';
   if(kind==='sun')b=repeat(12,i=>T(P('M-1-19L0-26 1-19Z',gold,'none'),0,0,i*30))+C(0,0,16,gold,ink,.8)+C(0,0,11,bone,ink,.6)+C(0,0,7,gold,'none');
   else if(kind==='moon')b=P('M9-21A23 23 0 1 0 9 21A20 20 0 0 1 9-21Z',bone,ink,.8)+C(14,-9,2,jewel,'none');
   else if(kind==='star')b=P('M0-25L5-7 18-18 8-4 25 0 7 5 18 18 4 8 0 25-5 7-18 18-8 4-25 0-7-5-18-18-4-8Z',gold,ink,.8)+gem(0,0,5);
   else if(kind==='rose')b=repeat(5,i=>T(P('M0 4C-24-2-16-27-2-18C7-29 21-10 0 4Z',dye,'#d1ad87',.8),0,0,i*72))+C(0,0,7,gold,ink,.8)+C(0,0,3,bone,'none');
   else if(kind==='leaf')b=P('M-3 23Q-21 7-15-9Q-10-23 12-28Q25-2 6 14Z',gold,ink,.8)+L('M-5 28L10-22M0 12L-11 1M4 1L15-8M6-10L-4-14',bone,.9);
   else if(kind==='lily')b=P('M-3 23V7Q-25 4-21-10Q-17-20-9-11L-4-2Q-13-19 0-29Q13-19 4-2L9-11Q17-20 21-10Q25 4 3 7V23Z',gold,ink,.8)+P('M-11 5H11V11H-11Z',gold,ink,.7)+L('M0-22V4',bone,.8);
   else if(kind==='lion')b=P('M0-27L12-23 23-13 27 3 21 17 10 28H-10L-21 17-27 3-23-13-12-23Z',gold,ink,1)+P('M-14-13L-8-16 0-12 8-16 14-13 13 5 7 17H-7L-13 5Z',bone,ink,1)+P('M-10-5L-3-3-7 0ZM10-5L3-3 7 0ZM-5 5H5L0 10Z',ink,'none')+L('M0 10V14M-6 14H6',ink,1)+mirror(L('M-17-13L-20 2-13 16',bone,.8));
   else if(kind==='boar')b=P('M-22-14L-12-25-5-16H5L12-25 22-14 16 14 7 23H-7L-16 14Z',gold,ink,1)+E(0,11,11,8,bone,ink,1)+C(-4,11,1.7,ink,'none')+C(4,11,1.7,ink,'none')+mirror(P('M-10 12Q-23 10-18-6Q-14 5-9 5Z',bone,ink,.8))+L('M-10-6L-5-4M10-6L5-4',ink,1.5);
   else if(kind==='feather'||kind==='griffin')b=mirror(P('M0 16Q-12 6-27-22L-15-16-23-29Q-5-20 1-6Z',gold,ink,.7)+L('M-20-21L0 14',bone,.7))+P('M-4-8L-9-18 0-24 12-20 5-14 6 18 0 25-5 16Z',bone,ink,.8)+C(1,-19,1.2,ink,'none');
   else if(kind==='wolf'||kind==='skull')b=P('M-19-17L-12-27-6-16H6L12-27 19-17 15 13 6 23H-6L-15 13Z',bone,ink,1)+P('M-12-5L-3-2-9 3ZM12-5L3-2 9 3ZM-5 11H5L0 17Z',black,'none')+L('M-6 19H6',ink,1);
   else if(kind==='crown')b=P('M-23-13L-13-3-8-20 0-8 8-20 13-3 23-13 19 16H-19Z',gold,ink,1)+L('M-18 9H18',bone,1)+gem(0,2,4);
   else if(kind==='tower')b=P('M-18 25V-23H-10V-15H-4V-23H4V-15H10V-23H18V25Z',gold,ink,1)+P('M-6 25V10Q0 0 6 10V25Z',black,ink,.6)+L('M-15-7H15M-15 2H15M-15 13H-9M9 13H15',bone,.7);
   else if(kind==='snow')b=repeat(6,i=>T(L('M0 0V-25M0-17L-7-23M0-17L7-23M0-8L-5-12M0-8L5-12',bone,1.8),0,0,i*60))+gem(0,0,4);
   else if(kind==='anchor')b=C(0,-20,5,'none',gold,2)+L('M0-14V23M-13-7H13',gold,3)+P('M0 25Q-24 18-24 2L-15 7-20 10Q-13 19-2 18V8H2V18Q13 19 20 10L15 7 24 2Q24 18 0 25Z',gold,ink,.8);
   else if(kind==='oak')b=P('M0 26L-3 13Q-20 17-17 6Q-29 2-17-6Q-21-17-9-15Q-8-29 0-26Q8-29 9-15Q21-17 17-6Q29 2 17 6Q20 17 3 13Z',gold,ink,.8)+L('M0-21V21M0-8L-11-11M0 3L12-2M0 11L-11 7',bone,.8);
   else if(kind==='stag')b=mirror(L('M-5-11L-15-22-16-34M-14-21L-26-23-29-31M-17-26L-24-33',gold,2))+P('M-13-11L0-6 13-11 8 15 0 26-8 15Z',bone,gold,.8)+P('M-8-3L-3 0-5 3ZM8-3L3 0 5 3ZM-3 18H3L0 22Z',black,'none');
   else if(kind==='raven'||kind==='phoenix')b=mirror(P('M0 7Q-11-7-29-20L-27-6-20-7-19 2-12 0-10 10-2 18Z',kind==='raven'?steel:gold,ink,.8)+L('M-24-13L-3 12',bone,.8))+P('M-5-7L-9-18 1-25 12-19 4-15 6 10 0 28-6 10Z',gold,ink,.8)+C(0,-20,1,black,'none')+(kind==='phoenix'?P('M-4 17Q-16 28-9 32L0 22 9 32Q16 28 4 17Z',gold,ink,.6):'');
   else if(kind==='serpent')b=P('M12-22C-12-40-34-12-17 2C-8 10 12 4 11 16C10 26-8 29-17 17Q-14 35 4 30C29 24 20-2-1-5C-21-7-14-24 2-21L5-13 18-14 23-23 14-29Z',gold,ink,.8)+P('M5-23L17-22 13-16H6Z',bone,ink,.5)+C(15,-24,1.5,black,'none')+L('M18-16L24-10 29-11M24-10L26-6',jewel,.9)+L('M-13-9L-8-12M-11 0L-5-3M5 6L9 1M11 18L17 17',bone,.8);
   else if(kind==='dragon')b=P('M-19 25Q-31 3-16-15L-24-29-7-22 5-29 19-16 26-14 21-4 9-5 3 6 12 22-1 27-4 12Q-18 8-19 25Z',gold,ink,1)+P('M-10-14L-16-5-7-8-9 1 0-5Z',bone,ink,.6)+P('M7-18L16-16 10-12Z',black,'none')+L('M13-5L20-3M-14 17Q-22 7-15-2',bone,.8);
   else if(kind==='gear')b=repeat(12,i=>T(P('M-3-19V-25H3V-19Z',gold,ink,.6),0,0,i*30))+C(0,0,19,gold,ink,1)+C(0,0,13,black,edge,.7)+L('M0-11V0L8 7',bone,1.2)+C(0,0,2,jewel,'none');
   else if(kind==='wave')b=repeat(3,i=>L(`M-22 ${-12+i*12}q7-10 14 0t14 0t14 0`,bone,2));
   else if(kind==='bell')b=P('M-5-20H5Q15-14 15 5L22 16H-22L-15 5Q-15-14-5-20Z',gold,ink,1)+C(0,20,4,gold,ink,.7)+L('M-13 10H13',bone,1);
   else if(kind==='knot')b=repeat(3,i=>T(E(0,-7,9,18,'none',gold,2),0,0,i*120));
   else b=P('M-22-10L0-24 22-10V0L0-14-22 0ZM-22 6L0-8 22 6V16L0 2-22 16Z',gold,ink,.8);
   return T(b,0,0,0,k);
  }
  const grain=(a,b,x=0)=>repeat(3,i=>L(`M${x-2+i*2} ${a}q${i-1} 21 0 40T${x-2+i*2} ${b}`,'#b38a58',.45));
  const grip=(a=30,b=78,w=7)=>P(`M-${w} ${a}H${w}L${w-1} ${b}H-${w-1}Z`,leather,ink,1.3)+repeat(Math.floor((b-a)/6),i=>L(`M-${w-1} ${a+3+i*6}l${2*w-2}-2`,'#ab8b68',.75));
  const shaft=(a=-60,b=108,w=4)=>P(`M-${w} ${a}H${w}L${w-.7} ${b}H-${w-.7}Z`,wood,ink,1)+grain(a,b)+P(`M-${w+1} ${b-5}h${2*w+2}v8h-${2*w+2}Z`,steel,ink,.6);
  const socket=(y=-60,len=18)=>P(`M-6 ${y}H6L5 ${y+len}H-5Z`,steel,ink,.8)+L(`M-3 ${y+3}v${len-6}`,edge,.7)+rivet(0,y+len-4,.75);
  const edging=(d,k=.92)=>T(P(d,'none',rich?'#c0a36e':'#a6b0ad',.8),0,0,0,k);
  function sword(kind=f){
   const dagger=['knife','dagger','kris'].includes(kind),thrust=['estoc','rapier'].includes(kind),two=kind==='greatsword',w=thrust?6:dagger?12:13+(v%3)*2,top=dagger?-76:-111,base=dagger?15:25;
   let d=`M0 ${top}L${w} ${top+24} ${w-2} ${base}H-${w-2}L-${w} ${top+24}Z`;
   if(kind==='knife')d=v%3===0?'M-10 18V-71Q3-70 14-45L11 18Z':v%3===1?'M-12 18L-10-76 17-46 12 18Z':'M-10 18V-75Q23-51 12 18Z';
   if(kind==='sabre'||kind==='falchion')d=kind==='sabre'?'M-8 25Q3-25 30-108Q43-66 20-9L9 25Z':'M-11 25L-7-98Q27-88 24-59L13 25Z';
   if(['flamberge','kris'].includes(kind)){
    const amp=kind==='kris'?3:3.5,start=top+20,end=base-7,steps=48;
    const coords=Array.from({length:steps+1},(_,i)=>{const y=start+(end-start)*i/steps;return [y,Math.sin(i/steps*Math.PI*10)*amp];});
    d=`M0 ${top}L${w-2} ${start}`+coords.map(([y,wave])=>`L${w-2+wave} ${y}`).join('')+`L${w-3} ${base}H-${w-3}`+coords.reverse().map(([y,wave])=>`L${-w+2+wave} ${y}`).join('')+`L0 ${top}Z`;
   }
   let s=P(d,steel,ink,1.4)+edging(d,.965);
   if(!['knife','sabre','falchion','flamberge','kris'].includes(kind))s+=P(`M0 ${top+8}V${base-3}H-${Math.max(2,w-6)}V${top+30}Z`,'#526674','none')+L(`M0 ${top+12}V${base-6}`,edge,.8);
   if(kind==='spellsword'||kind==='sunblade'||(rich&&magic&&!['sabre','falchion'].includes(kind)))s+=rune(0,-70,6);
   const hilt=base+2,end=two?99:dagger?65:79;
   s+=P(`M-8 ${base-2}H8V${base+6}H-8Z`,gold,ink,.9)+grip(hilt+7,end,two?8:6.5);
   const guard=v%3===0?`M-32 ${hilt-5}Q0 ${hilt+4} 32 ${hilt-5}L30 ${hilt+3}Q0 ${hilt+11}-30 ${hilt+3}Z`:v%3===1?`M-29 ${hilt+7}L-26 ${hilt-3}H26L29 ${hilt+7}H23L19 ${hilt+2}H-19L-23 ${hilt+7}Z`:`M-29 ${hilt-2}Q-20 ${hilt-12}-14 ${hilt-3}H14Q20 ${hilt-12} 29 ${hilt-2}V${hilt+3}H-29Z`;
   s+=P(guard,rich?gold:steel,ink,1.2);
   if(thrust||kind==='sabre')s+=L(`M20 ${hilt+2}Q41 ${hilt+35} 2 ${end+5}`,rich?'#bba16f':'#adb9bc',3)+E(0,hilt+4,17,6,steel,ink,1);
   s+=C(0,end+5,dagger?6:8,rich?gold:steel,ink,1.2)+C(0,end+5,3,rich?jewel:leather,ink,.7);
   if(rich){
    s+=T(badge(r.motif,.48),0,hilt+1)+mirror(scroll(18,hilt-3,.48));
    if(!['sabre','falchion'].includes(kind))s+=mirror(L(`M-8 ${top+35}Q-4 ${top+47}-8 ${top+58}T-8 ${base-12}`,gold,.7));else s+=L('M4 12Q17-31 28-76',gold,.7);
    if(legend){
     const wing=kind==='sunblade'||r.motif==='feather';
     s+=mirror(wing?P(`M-5 ${hilt}Q-20 ${hilt-25}-40 ${hilt-16}L-28 ${hilt-9}-34 ${hilt-6}-22 ${hilt-2}-27 ${hilt+2}-9 ${hilt+5}Z`,gold,ink,1):P(`M-8 ${hilt-3}Q-29 ${hilt-26}-37 ${hilt-13}Q-43 ${hilt-3}-30 ${hilt+4}L-35 ${hilt-5}Q-26 ${hilt-14}-17 ${hilt+3}Z`,gold,ink,1));
     s+=gem(0,hilt+2,6)+P(`M-8 ${end-10}H8V${end-5}H-8Z`,gold,ink,.7)+(!['sabre','falchion'].includes(kind)?rune(0,top+25,5):'');
     if(kind==='spellsword')s+=P(`M-19 ${hilt-7}Q-10 ${hilt-14} 0 ${hilt-7}Q10 ${hilt-14} 19 ${hilt-7}V${hilt+9}Q10 ${hilt+4} 0 ${hilt+10}Q-10 ${hilt+4}-19 ${hilt+9}Z`,bone,gold,.8)+L(`M0 ${hilt-7}V${hilt+9}`,gold,.7);
    }
   }
   if(two)s+=P('M-12 0L-21-6-18 5-10 8ZM12 0L21-6 18 5 10 8Z',steel,ink,.8);
   return s;
  }
  function bow(kind=f){
   const tall=kind==='shortbow'?86:108,bend=kind==='longbow'?40:51;
   const d=`M12-${tall}C-${bend}-85-${bend}-39-12-8Q-6 0-12 8C-${bend} 39-${bend} 85 12 ${tall}L5 ${tall-6}C-${bend-16} 79-${bend-16} 38-6 8Q1 0-6-8C-${bend-16}-38-${bend-16}-79 5-${tall-6}Z`;
   let s=L(`M12-${tall}L12 ${tall}`,'#ddd2b2',1.1)+P(d,wood,ink,1.4)+L(`M7-${tall-5}C-${bend-7}-79-${bend-7}-39-9-8M-9 8C-${bend-7} 39-${bend-7} 79 7 ${tall-5}`,'#bd9f6c',1)+T(grip(-14,14,6),-9)+P(`M9-${tall+1}l5 2-3 7-5-3ZM9 ${tall+1}l5-2-3-7-5 3Z`,bone,ink,.8);
   // Arrow lies on the rest. Its nock meets the undrawn string at x=12.
   s+=L('M12 0H-76','#ccb98b',1.5)+P('M-74-4L-88 0-74 4Z',steel,ink,.7)+P('M11 0L25-5 25 0 11 4Z',cloth,ink,.6);
   if(t>=1)s+=P('M-17-33L-12-35-5-22-10-19ZM-17 33L-12 35-5 22-10 19Z',rich?gold:bone,ink,.8);
   if(rich){s+=T(badge(kind==='falconbow'?'feather':r.motif,.55),-28,-59)+T(badge(r.motif,.45),-28,61)+gem(-10,0,6);}
   if(kind==='antlerbow')s+=L('M-29-55L-45-66-44-82M-45-66L-55-67M-29 55L-45 66-44 82M-45 66L-55 67',bone,3);
   return s;
  }
  function pole(kind=f){
   let s=shaft(-68,111,3.6),d='';
   if(['spear','javelin','harpoon'].includes(kind)){
    const width=kind==='javelin'?7:9+v%4*1.3;d=`M0-117Q${width+2}-97 ${width}-85L4-70H-4L-${width}-85Q-${width+2}-97 0-117Z`;
    s+=P(d,steel,ink,1)+P(`M0-114V-72H-4L-${width}-85Z`,'#617786','none')+L('M0-113V-73',edge,.9);
    if(kind==='harpoon')s+=P('M6-91L17-82 8-84 4-74M-6-91L-17-82-8-84-4-74',steel,ink,.8)+L('M5-52Q26-42 17 3T12 61','#b5a17a',1.2);
   }else if(['trident','stormfork'].includes(kind)){
    s+=P('M-4-66L-25-79-26-109-20-98-19-84-5-77V-110L0-120 5-110V-77L19-84 20-98 26-109 25-79 4-66Z',steel,ink,1.1)+L('M0-113V-69M-22-100V-82L-4-72M22-100V-82L4-72',edge,.8);
    if(kind==='stormfork')s+=gem(0,-79,8)+L('M-21-98L-9-92 4-103 21-98',jewel,1.5);
   }else if(['halberd','griffinhalberd','guisarme'].includes(kind)){
    s+=P('M0-122L8-103 4-77H-4L-8-103Z',steel,ink,1)+L('M0-116V-78',edge,.8);
    s+=kind==='guisarme'?P('M-3-80Q-27-105-38-95L-43-74-34-85Q-18-89-9-67L-4-51Z',steel,ink,1.1):P('M4-91Q25-93 37-108Q40-85 51-69Q29-58 5-65ZM-4-83L-32-75-29-68-4-71Z',steel,ink,1.1)+L('M37-101Q40-84 46-71Q28-64 10-70',edge,1.2);
    if(kind==='griffinhalberd')s+=T(badge('griffin',.47),20,-81);
   }else if(kind==='warscythe'){
    s+=P('M-4-79Q18-109 62-99Q29-94 9-68L4-61H-5Z',steel,ink,1.2)+L('M4-78Q26-101 53-99',edge,1)+P('M-7-79H7V-52H-7Z',steel,ink,.8);
   }
   s+=socket(-71,24)+grip(32,63,4.5)+P('M-6-49H6V-44H-6Z',rich?gold:steel,ink,.6);
   if(t>=2)s+=repeat(4,i=>P(`M-5 ${-33+i*19}H5v3H-5Z`,gold,ink,.5));
   if(rich){s+=rune(0,-37,4)+T(badge(r.motif,.4),0,-56)+P('M5-43Q26-43 32-24L17-30 20-11 5-26Z',cloth,ink,.8)+gem(0,-59,4);
    if(kind==='spear')s+=P('M0-108L5-94 0-80-5-94Z',jewel,edge,.7)+L('M-9-82L-16-94M9-82L16-94',gold,1.8);
    if(kind==='griffinhalberd')s+=L('M12-87Q29-92 35-101M14-70Q31-67 42-73',gold,2)+mirror(scroll(7,-36,.6))+repeat(5,i=>P(`M-6 ${-19+i*22}H6v5H-6Z`,gold,ink,.7));
   }
   if(r.collection&&legend&&kind==='trident')s+=mirror(P('M-21-100L-13-88-9-74-16-77Z',gold,ink,.8)+gem(-20,-92,3))+T(badge('serpent',.55),0,-57)+rune(0,-31,7);
   return s;
  }
  function axe(kind=f){
   let s=shaft(-77,kind==='hatchet'?68:96,5)+grip(kind==='hatchet'?10:35,kind==='hatchet'?58:83,6),d;
   const y=-61,wide=kind==='hatchet'?35:42+(v%3)*5;
   // The haft crosses the eye; bevel follows the cutting edge, never an ornamental crescent.
   d=v%3===0?`M-6-74L-20-78Q-${wide-9}-91-${wide}-88Q-${wide+8}-65-${wide}-39L-22-46-8-58Z`:v%3===1?`M-6-76L-23-79-${wide}-92Q-${wide+7}-63-${wide-2}-29L-26-35-20-57-7-59Z`:`M-6-75Q-27-85-${wide}-79L-${wide+4}-49Q-25-37-6-58Z`;
   s+=P(d,steel,ink,1.5)+T(P(d,'none',edge,.9),-2,0,0,.95)+P('M-8-78H9V-53H-8Z',steel,ink,1)+E(0,-78,5,2.6,wood,ink,.8)+P('M9-73H21V-59H9Z',steel,ink,1);
   s+=rivet(0,-62,.8)+L('M-3-74V-57',edge,.7);
   if(t>=2)s+=T(badge(r.motif,.36),-27,-65)+scroll(-2,-36,.5);
   if(rich)s+=P('M-12-72L-28-77-38-70-35-52-20-57Z',`url(#${id}glass)`,gold,1)+L('M-28-76L-20-57M-12-72L-35-52',gold,.8)+gem(0,-60,4)+repeat(4,i=>P(`M-6 ${-35+i*17}H6v4H-6Z`,gold,ink,.6))+P('M-8 83H8V91H-8Z',gold,ink,.7);
   return s;
  }
  function striking(kind=f){
   let s=shaft(-57,96,5)+grip(29,83,6);
   if(kind==='pick')s+=P('M-6-77Q-35-83-57-56Q-30-65-7-58H21V-78Z',steel,ink,1.3)+P('M20-82H31V-55H20Z',steel,ink,1)+L('M-47-62Q-24-75-5-70',edge,.9);
   else if(['hammer','clockhammer'].includes(kind)){
    s+=P('M-34-81H32V-43H-34Z',steel,ink,1.5)+P('M-42-86H-30V-38H-42ZM29-84H40V-40H29Z',rich?gold:steel,ink,1.2)+P('M-26-75H24V-49H-26Z',black,'#a6b2b4',.8);
    s+=kind==='clockhammer'?T(badge('gear',.7),0,-62):rivet(-17,-63)+rivet(16,-63)+T(badge(r.motif,.4),0,-62);
   }else if(kind==='club'){
    s=P('M-4 98L-8 22Q-10-17-22-53Q-29-90-7-103Q14-110 20-86Q19-56 9-21L5 98Z',wood,ink,1.7)+L('M-8-89Q5-45-1 20M8-80Q-4-30 2 24','#bc8d55',1)+E(-7,-56,3,7,'none','#432b20',1)+grip(43,85,6)+P('M-17-30H12L10-18H-13ZM-13 3H7V13H-10Z',steel,ink,1)+rivet(-3,-24);
   }else if(kind==='flail'){
    s=T(shaft(-8,97,5)+grip(35,84,6),-23)+repeat(7,i=>E(-23+i*5,-10-i*8,3.5,5,'none',i%2?'#c2c9c6':'#718593',1.7))+T(C(0,0,20,steel,ink,1.4)+repeat(8,i=>T(P('M-4-17L0-28 4-17Z',steel,ink,.8),0,0,i*45))+gem(0,0,5),16,-82);
   }else{
    s+=P('M-7-94H7V-37H-7Z',steel,ink,1)+repeat(5,i=>{const x=(i-2)*9;return P(`M${x}-89L${x-6}-75 ${x-7}-53 ${x}-36 ${x+6}-53 ${x+5}-75Z`,i%2?black:steel,ink,1)+L(`M${x}-83V-45`,edge,.6);})+P('M-12-38H12V-30H-12Z',rich?gold:steel,ink,.9);
    if(kind==='sunmace')s+=T(badge('sun',.46),0,-64);
   }
   if(rich&&kind!=='club')s+=T(gem(0,18,4)+rune(0,-22,3)+P('M-8 83H8V91H-8Z',gold,ink,.8),kind==='flail'?-23:0);
   return s;
  }
  function instrument(kind=f){
   if(kind==='lute'){
    // Six double courses share bridge and nut anchors. Soundboard is pear-shaped,
    // neck narrow, bent pegbox with three tuners each side; no guitar waist/cutaway.
    let s=P('M-9-10C-17 4-40 22-43 47C-47 75-25 94 0 95C25 94 47 75 43 47C40 22 17 4 9-10Z',wood,ink,1.8)+P('M-8-5C-16 10-36 26-38 49C-41 72-22 88 0 89C22 88 41 72 38 49C36 26 16 10 8-5Z','#bc955d','#e0c18b',1)+P('M-7-9L-6-80H6L7-9Z',wood,ink,1.2)+P('M-6-77L-15-95-9-111 1-107 6-80Z',wood,ink,1.2)+L('M-10-94L-6-103',bone,1);
    s+=C(0,27,12,'#31261e','#e3c997',1)+repeat(8,i=>T(E(0,-5,2.2,5,'none','#c5a576',.65),0,27,i*45))+C(0,27,3,'none','#c5a576',.7)+P('M-18 66H18V71H-18Z','#4b3023',ink,.8);
    s+=repeat(6,i=>{const x=-3.5+i*1.4;return L(`M${x-.23} 68L${x*.8-.2}-79M${x+.23} 68L${x*.8+.2}-79`,'#efe0b6',.32);})+repeat(8,i=>L(`M-6 ${-70+i*8}H6`,'#cfb987',.75));
    s+=repeat(3,i=>L(`M-13 ${-99+i*7}H-22M0 ${-102+i*7}H9`,ink,2)+C(-23,-99+i*7,2.7,bone,ink,.8)+C(10,-102+i*7,2.7,bone,ink,.8));
    s+=L('M-27 46Q-29 65-17 77M27 46Q29 65 17 77','#e2c08b',.7);
    s+=C(0,27,14,'none',bone,.7)+C(0,27,16,'none',wood,.7);
    if(rich)s+=mirror(scroll(24,36,.8))+T(badge(r.motif,.35),0,81)+P('M-8-11H8V-5H-8Z',gold,ink,.6)+mirror(P('M9-3C18 13 34 29 35 50Q37 72 18 81', 'none',bone,1.2))+repeat(6,i=>T(P('M0-2L2 0 0 2-2 0Z',bone,'none'),0,-68+i*10))+P('M-22 65Q-25 68-22 72H22Q25 68 22 65Z',wood,bone,.6);
    return T(s,0,-2,-14,.98);
   }
   const pipe=kind==='blowpipe';
   let s=P('M-6-103H6V92Q0 98-6 92Z',wood,ink,1.4)+P('M-7-103H7V-91H-7ZM-7 83H7V94H-7Z',pipe?bone:gold,ink,.8)+L('M-3-86V78','#c6a573',.7);
   if(!pipe)s+=repeat(6,i=>E(1,-47+i*20,2.6,3.4,'#211d19','#c7a879',.6));
   else s+=T(P('M-2-55H2V52H-2Z',wood,ink,.8)+P('M0-67L4-53H-4Z',steel,ink,.7)+P('M-2 40L-10 53V66L0 57 10 66V53L2 40Z',cloth,ink,.6),26,13,-5);
   return s;
  }
  function book(open=false){
   if(open){let s=P('M0-40Q-33-62-68-45L-64 52Q-29 41 0 64Q29 41 64 52L68-45Q33-62 0-40Z',leather,ink,1.7)+P('M0-34Q-30-54-61-40L-58 46Q-29 38 0 57Q29 38 58 46L61-40Q30-54 0-34Z',bone,ink,.8)+L('M0-34V55','#776950',1.5);
    s+=repeat(9,i=>L(`M-51 ${-27+i*8}q21-2 41 12M10 ${-15+i*8}q20-14 41-12`,'#82765d',.75));
    s+=T(badge('star',.46),-30,4)+T(badge('moon',.4),33,6)+P('M-2 57L-5 82 5 75 12 84 8 54Z',cloth,ink,.7);return s;}
   let s=P('M-49-66L43-73 51-64V70L-39 77-49 69Z',leather,ink,1.6)+P('M-39 65L43 59V69L-39 76Z',bone,ink,.7)+L('M-37 68L40 62M-37 72L40 66','#968971',.6)+P('M-49-66L43-73V59L-49 69Z',cloth,ink,1.2)+P('M-49-66L-37-67V67L-49 69Z',leather,ink,.8)+P('M-31-55L32-60V48L-31 55Z','none',rich?'#c6a975':'#9e8b63',1)+T(badge(r.motif,rich?1:.65),1,-2);
   s+=repeat(4,i=>P(`M-51 ${-47+i*29}h15v5h-15Z`,gold,ink,.6))+P('M30-9L48-11V4L30 6Z',leather,ink,.8)+rivet(37,-2);
   if(rich)s+=mirror(scroll(23,-45,.6))+repeat(10,i=>E(-49+i*8,69+Math.sin(i/9*Math.PI)*16,4,3,'none','#c0bca8',1));
   return T(s,0,0,-8,1);
  }
  function lantern(kind=f){
   const censer=kind==='censer',bell=kind==='bell';let s='';
   if(censer){s=C(0,-101,8,'none',gold,2)+repeat(12,i=>E(-i*2.8,-89+i*6.8,2,3.4,'none','#cab07b',1))+repeat(12,i=>E(i*2.8,-89+i*6.8,2,3.4,'none','#cab07b',1))+L('M0-93V-13','#cab07b',1.2);}
   else s=E(0,-82,12,18,'none',gold,2.5)+P('M-6-67H6V-56H-6Z',gold,ink,.8);
   if(bell){s+=P('M-8-56Q-25-52-28-15Q-29 15-43 35L-44 48Q0 59 44 48L43 35Q29 15 28-15Q25-52 8-56Z',gold,ink,1.6)+E(0,48,44,8,black,gold,2)+C(0,52,8,gold,ink,.8)+L('M-37 35Q0 44 37 35','#e0ca92',1.3)+T(badge(r.motif,.55),0,-4);}
   else if(censer){s+=P('M-39 3Q-34-22 0-31Q34-22 39 3L43 9Q38 57 0 70Q-38 57-43 9Z',gold,ink,1.4)+E(0,8,43,7,gold,ink,1)+repeat(7,i=>P(`M${-27+i*9}-3q-2-11 3-16q5 7 2 16Z`,black,ink,.5))+mirror(scroll(19,25,.6))+C(0,72,4,gold,ink,.7)+L('M0 76V91',gold,1.8)+gem(0,96,4);}
   else {s+=P('M-42-46L0-67 42-46 37-37H-37Z',gold,ink,1.2)+P('M-33-36H33V58H-33Z',black,ink,1.5)+P('M-26-30H26V51H-26Z',magic?'#354353':'#736043',gold,.7)+P('M0 38Q-19 23-6 1Q-3 15 4 2Q22 27 0 38Z',jewel,'#e9d7a8',.8)+P('M-38 58H38L44 70H-44Z',gold,ink,1)+L('M-31-37V59M31-37V59M-34-37H34',gold,3)+L('M0-29V50',gold,1.4)+repeat(3,i=>P(`M${-19+i*15}-48h7v5h-7Z`,black,ink,.5));
    if(kind==='mothlantern')s+=T(badge('feather',.65),0,8);if(rich)s+=P('M-24 48V-13Q-24-25-12-29Q0-25 0-13Q0-25 12-29Q24-25 24-13V48', 'none',gold,1.6)+P('M0 33Q-8 24-2 17Q6 24 0 33Z',bone,'none')+mirror(P('M-35-39L-41-29-38 41-31 52V-33Z',gold,ink,.8)+scroll(33,-12,.52)+gem(33,46,3))+T(badge(r.motif,.35),0,-51)+P('M-26 70L-30 78H-19L-16 70ZM26 70L30 78H19L16 70Z',gold,ink,.8)+repeat(5,i=>C(-24+i*12,65,1.3,bone,'none')); }
   return s;
  }
  function focus(kind=f){
   if(['book','atlas'].includes(kind))return book(kind==='atlas');
   if(['censer','bell','lantern','mothlantern'].includes(kind))return lantern(kind);
   if(kind==='cross')return P('M-8-92H8V-54H42V-37H8V85L0 97-8 85V-37H-42V-54H-8Z',rich?gold:wood,ink,1.5)+L('M0-84V81M-34-46H34',rich?'#e9d2a0':'#b59961',1.5)+gem(0,-46,rich?10:5)+ (rich?T(badge('sun',.55),0,-46)+mirror(scroll(16,-44,.6)):'');
   if(kind==='orb'||kind==='seaorb')return C(0,-8,47,black,gold,2)+C(0,-8,41,`url(#${id}glass)`,edge,.8)+L('M-30-30Q-7-57 26-31',edge,1.4)+P('M-38 10Q-19 60 0 58Q19 60 38 10L29 17Q17 42 0 42Q-17 42-29 17Z',gold,ink,1.1)+P('M-8 55H8L15 76H-15Z',gold,ink,1)+T(badge(kind==='seaorb'?'wave':'star',.7),0,-8)+(kind==='seaorb'?mirror(P('M-34 21Q-56-6-40-35Q-49-10-26 8L-12 46-20 49Z',gold,ink,1)+L('M-28 24Q-24 37-12 44',bone,1))+repeat(7,i=>T(L('M0 0L0-17',gold,1),0,71,-55+i*18))+gem(0,55,5):'');
   const wand=kind==='wand',orbital=['orrery','worldstaff'].includes(kind);
   let s=shaft(wand?-72:-50,106,wand?3:4.5)+grip(42,83,wand?4:5.5)+socket(wand?-77:-50,14);
   if(wand)s+=P('M0-112L7-88 0-78-7-88Z',bone,ink,1)+gem(0,-83,3);
   else if(orbital){s+=C(0,-72,31,'none',gold,2)+repeat(kind==='worldstaff'?3:2,i=>T(E(0,-72,34-i*3,13+i*4,'none',i%2?edge:'#c8b279',1.2),0,0,0))+P('M-6-43L-18-52H18L6-43Z',gold,ink,.8)+gem(0,-72,13)+repeat(7,i=>{const a=i*Math.PI*2/7;return gem(Math.cos(a)*31,-72+Math.sin(a)*31,2.4);});}
   else if(kind==='staff'){s+=P('M-5-48Q-30-74-13-101Q-7-107 0-104Q-21-81-5-70Q10-71 9-86L18-99Q32-74 6-48Z',wood,ink,1.5)+L('M-1-55Q-21-75-13-92','#bc9667',1)+gem(0,-76,9);}
   else {s+=P(v%3===0?'M-6-48Q-25-65-19-91L-8-78 0-98 8-78 19-91Q25-65 6-48Z':v%3===1?'M-6-48L-25-75-15-99-8-82 0-98 8-82 15-99 25-75 6-48Z':'M-6-48L-22-59-25-88-14-98-17-74 0-58 17-74 14-98 25-88 22-59 6-48Z',rich?gold:steel,ink,1.3)+gem(0,-75,12)+P('M-8-49H8V-40H-8Z',gold,ink,1);}
   if(rich)s+=rune(0,-28,6)+mirror(scroll(8,-41,.6))+gem(0,95,4)+repeat(5,i=>P(`M-6 ${-29+i*22}H6v4H-6Z`,gold,ink,.6));
   if(legend&&kind==='worldstaff')s+=T(E(0,0,40,15,'none',gold,1.3),0,-72,65)+T(E(0,0,40,15,'none',edge,1),0,-72,-65)+P('M-9-42Q0-30 9-42V-25H-9Z',gold,ink,.9);
   if(legend&&kind==='orrery')s+=repeat(7,i=>T(L('M0-30V-36',gold,1),0,-72,i*360/7));
   return s;
  }
  function gloves(){
   const pair=f==='gloves';
   function hand(){let s=P('M-24 58L-27 18-43-3Q-47-13-37-17L-25-4V-49Q-23-57-17-52L-14-21V-63Q-12-71-5-66L-3-23 0-69Q4-77 10-68L10-22 16-59Q23-66 27-55L23-18 29-43Q37-48 39-38L29 14 23 58Z',f==='gauntlet'?steel:leather,ink,1.5)+P('M-25 40H24L30 72H-31Z',f==='gauntlet'?steel:cloth,ink,1.3)+P('M-16-13L19-13 17 31H-17Z',f==='gauntlet'?steel:cloth,ink,1)+L('M-23 48H23M-27 65H27',gold,1)+repeat(4,i=>L(`M${-20+i*14}-20l8 1`,gold,1.8))+T(badge(r.motif,.5),0,9)+rivet(-22,55)+rivet(22,55);return s;}
   return pair?T(hand(),-30,-10,-10,.75)+T('<g transform="scale(-1 1)">'+hand()+'</g>',33,13,8,.75):hand();
  }
  function crossbow(){
   let s=P('M-7-111H7L11 35 16 89 6 104H-8L-11 35Z',wood,ink,1.5)+P('M-76-51Q-42-75 0-53Q42-75 76-51L72-40Q39-59 0-42Q-39-59-72-40Z',steel,ink,1.4)+L('M-75-47L0-8 75-47','#dacda9',1.3)+L('M0-101V-10',edge,2)+P('M0-111L5-100H-5Z',steel,ink,.7)+P('M-10-55H10V-34H-10Z',leather,ink,1)+repeat(4,i=>L(`M-10 ${-51+i*4}H10`,'#baa478',1))+E(0,-122,10,12,'none',steel,3)+C(0,-8,7,gold,ink,1)+L('M6 23Q22 39 7 44',gold,3)+grip(47,80,10);
   if(rich)s+=T(badge(r.motif,.5),0,19)+mirror(scroll(39,-56,.42));return T(s,0,9,-9,.92);
  }
  // Purpose-built additions. Structural parts keep their own material and outline.
  function expansionWeapon(){
   if(original==='lyre'){
    let s=P('M-42 24Q-61-15-45-70L-33-84-26-76Q-46-35-27 23ZM42 24Q61-15 45-70L33-84 26-76Q46-35 27 23Z',wood,gold,1.3)+P('M-37-70Q0-83 37-70L36-59Q0-69-36-59Z',gold,ink,1.2)+P('M-36 16Q0 9 36 16L42 49Q32 77 0 80Q-32 77-42 49Z',wood,ink,1.5)+P('M-30 23Q0 16 30 23L33 48Q22 67 0 70Q-22 67-33 48Z',bone,gold,1);
    s+=C(0,43,10,black,gold,1)+repeat(7,i=>L(`M${-23+i*7.66} -64V50`,'#eee0be',.8)+C(-23+i*7.66,-69,2,steel,ink,.5))+P('M-27 50H27V55H-27Z',wood,gold,.8);
    if(rich)s+=mirror(scroll(34,-40,.7)+gem(36,-65,4))+T(badge(r.motif,.45),0,67)+mirror(L('M-35 28Q-39 55-17 65',gold,1.2));
    return s;
   }
   if(original==='harp'){
    let s=P('M-50 75L-56-76Q-52-96-34-92Q0-89 49-42L45-29Q-5-73-41-77L-34 74Z',wood,ink,1.6)+P('M39-43L57-26-24 84-38 77Z',wood,gold,1.4)+P('M-48 72L10 72 18 87H-53Z',wood,gold,1.2);
    s+=repeat(12,i=>{const x=-32+i*6,y=-75+(i/11)**1.3*43,bottom=74-(x+34)*103/79;return L(`M${x} ${y}V${bottom}`,'#e6d5b1',.8)+C(x,y-3,1.6,gold,ink,.5);});
    s+=L('M-47-72L-42 66M-32-85Q5-77 43-42',gold,1.7)+L('M43-29L-31 74',edge,1);
    if(rich)s+=repeat(5,i=>T(badge(r.motif,.2),-46,-54+i*25))+gem(-43,-80,5)+gem(44,-38,5)+T(badge(r.motif,.38),-19,79)+repeat(4,i=>gem(33-i*17,-13+i*23,2.4));
    return s;
   }
   if(original==='viol'){
    let s=P('M-7-14Q-26-29-34-10Q-38 5-25 19Q-19 25-26 34Q-46 51-35 72Q-24 90 0 91Q24 90 35 72Q46 51 26 34Q19 25 25 19Q38 5 34-10Q26-29 7-14Z',wood,ink,1.6)+P('M-6-10Q-24-23-29-8Q-32 5-20 18Q-14 27-22 37Q-39 52-30 69Q-20 83 0 84Q20 83 30 69Q39 52 22 37Q14 27 20 18Q32 5 29-8Q24-23 6-10Z','#b88c55',gold,.8)+P('M-6-9L-5-76H5L6-9Z',black,gold,1)+P('M-5-75L-9-98Q0-113 9-98L5-75Z',wood,ink,1.1)+C(0,-98,4,'none',gold,1);
    s+=repeat(3,i=>L(`M-7 ${-91+i*6}H-15M7 ${-91+i*6}H15`,gold,1.5)+C(-16,-91+i*6,2,bone,ink,.6)+C(16,-91+i*6,2,bone,ink,.6))+mirror(L('M-19 10Q-28 9-24 19L-18 37Q-14 47-22 47',black,2))+P('M-10 56H10L5 78H-5Z',black,gold,.8)+P('M-14 45Q0 40 14 45V49H-14Z',bone,ink,.8)+repeat(5,i=>L(`M${-3+i*1.5} -80V72`,'#f0e1be',.65));
    s+=T(P('M-1-98Q5-16 1 97H-2Q1 10-4-96Z',wood,ink,.8)+L('M-3-91L-2 88','#f0e1be',.8)+P('M-5 77H3V88H-5Z',black,gold,.7),54,0,8);
    if(rich)s+=mirror(scroll(24,52,.5))+T(badge(r.motif,.28),0,4)+gem(0,-92,3);
    return s;
   }
   if(original==='horn'){
    let s=P('M-27-88Q-68-40-47 16Q-18 82 48 58L32 26Q-4 44-24 8Q-44-28-21-77Z',bone,ink,1.6)+P('M29 26L47 17 70 64 52 76 44 57Z',gold,ink,1.3)+E(61,48,11,28,black,gold,2)+L('M-24-82Q-56-27-31 16Q-3 57 40 46',gold,1.1)+P('M-29-90L-18-85-15-74-24-70Z',gold,ink,1)+T(E(0,0,11,4,gold,ink,.6),-23,-86,28);
    s+=L('M-26-62C-60 51 6 108 51 65',leather,2)+T(badge(r.motif,.55),-16,32);
    if(rich)s+=mirror(scroll(18,27,.6))+repeat(5,i=>T(P('M-6-27H6V-19H-6Z',gold,ink,.6),-12,24,-40+i*19))+gem(35,43,5)+T(badge(r.motif,.5),-40,-14);
    return T(s,0,0,-8,.94);
   }
   if(original==='lanternstaff')return shaft(-50,108,4)+grip(50,88,5.5)+T(lantern('lantern'),0,-64,0,.48)+P('M-9-30H9L5-17H-5Z',gold,ink,1)+(rich?rune(0,-4,5)+gem(0,97,4):'');
   if(original==='maul')return shaft(-58,108,5.5)+grip(47,94,7)+P('M-40-83H33L43-74V-37L33-27H-40Z',steel,ink,1.5)+P('M-40-83H-28V-27H-40ZM26-83H36L43-74V-37L36-27H26Z',rich?gold:steel,ink,1)+P('M-22-75H20V-35H-22Z',cloth,gold,1)+T(badge(r.motif,.65),-1,-54)+rivet(-34,-72)+rivet(-34,-38)+(rich?gem(0,15,5)+mirror(scroll(12,-48,.4))+P('M-7 95H7V104H-7Z',gold,ink,.8):'');
   if(original==='doubleaxe'){
    const blade='M-7-79L-27-81Q-34-91-45-95Q-58-68-48-34L-31-39Q-26-58-8-55Z';
    return shaft(-82,102,5)+grip(36,87,6)+mirror(P(blade,steel,ink,1.4)+L('M-45-88Q-53-65-45-41',edge,1.5)+P('M-13-73L-30-73-37-62-33-48-16-59Z',rich?cloth:steel,gold,.8)+(rich?gem(-29,-62,4)+scroll(-18,-72,.45):''))+P('M-8-85H8V-50H-8Z',steel,gold,1)+gem(0,-63,rich?6:3)+(rich?rune(0,-30,5)+P('M-7 91H7V101H-7Z',gold,ink,.8):'');
   }
   if(['partisan','glaive','dragonlance'].includes(original)){
    let s=shaft(-64,110,4)+grip(35,79,5);
    if(original==='glaive')s+=P('M-5-60L-7-117Q26-106 24-85Q22-66 5-49Z',steel,ink,1.4)+L('M-2-109Q17-99 17-83Q15-69 4-60',edge,1.2);
    else s+=P('M0-120L13-96 8-73 24-83 19-61 7-55H-7L-19-61-24-83-8-73-13-96Z',steel,ink,1.3)+L('M0-112V-59M-16-72L-9-65M16-72L9-65',edge,1.1);
    s+=socket(-61,22);
    if(rich)s+=gem(0,-58,5)+rune(0,-20,5)+P('M5-38Q25-36 32-18L17-21 21-7 5-19Z',cloth,gold,.7)+repeat(4,i=>P(`M-6 ${-28+i*29}H6v4H-6Z`,gold,ink,.6));
    if(original==='dragonlance')s+=T(badge('dragon',.55),0,-82)+mirror(scroll(7,-48,.6));
    return s;
   }
   if(original==='huntingcrossbow'){
    let s=P('M-6-103H6L8 36 17 83 7 100H-9L-9 39Z',wood,ink,1.4)+P('M-72-51Q-43-74 0-57Q43-74 72-51L68-40Q39-58 0-46Q-39-58-68-40Z',steel,ink,1.3)+L('M-70-47L0-10 70-47',bone,1.2)+P('M-7-61H7V-39H-7Z',leather,ink,.8)+E(0,-109,9,12,'none',steel,2.5)+L('M0-98V-11',edge,1.5)+P('M0-107L4-97H-4Z',steel,ink,.6)+C(0,-11,7,gold,ink,1)+P('M-6 35H6V80H-6Z',leather,ink,1)+L('M8 17Q25 30 8 39',gold,2.3)+repeat(6,i=>L(`M-5 ${43+i*6}H5`,bone,.65));
    if(rich)s+=mirror(scroll(35,-55,.65)+T(badge(r.motif,.35),44,-52))+gem(0,-11,5)+T(badge(r.motif,.43),0,10)+repeat(3,i=>P(`M-10 ${51+i*13}H10v4H-10Z`,gold,ink,.6));
    return T(s,0,5,-7,.94);
   }
   return null;
  }
  function expansionGuard(){
   if(original==='scrollward'){
    let s=P('M-42-65H38L34 64H-38Z',bone,ink,1.3)+P('M-42-65Q-54-76-45-83H43Q53-73 43-65Z',bone,gold,1)+E(43,-74,6,9,leather,gold,1)+P('M-38 64Q-49 76-38 83H40Q51 72 40 64Z',bone,gold,1)+E(-39,74,6,9,leather,gold,1)+L('M-31-54H29M-31 54H29','#9e8057',1);
    s+=T(badge(r.motif,.95),0,-9)+repeat(3,i=>L(`M-23 ${24+i*8}h${46-i*5}`,'#7b6554',.85))+P('M-6 67H8L13 98 1 91-9 98Z',cloth,gold,.8)+C(1,69,10,rich?gold:dye,ink,1)+T(badge(r.motif,.25),1,69);
    if(rich)s+=mirror(L('M-31-48V45M-27-48V-30M-27 26V45',gold,1)+scroll(22,-47,.52))+repeat(5,i=>gem(-28+i*14,-74,2.5));
    return s;
   }
   if(original==='spellcage'){
    let s=E(0,-85,10,13,'none',gold,2)+P('M0-76L42-46 42 45 0 78-42 45-42-46Z',black,gold,2)+P('M0-64L31-41 31 39 0 65-31 39-31-41Z',`url(#${id}glass)`,edge,.8);
    s+=repeat(3,i=>T(E(0,0,42,15,'none',gold,1.2),0,0,i*60))+gem(0,0,18)+mirror(P('M0-76Q-36-43-30 43L0 78-6 61Q-24 34-22-30L-6-61Z',gold,ink,.7))+mirror(L('M-39-42L-28 0-39 43',edge,1.5))+P('M-10 78H10L6 89H-6Z',gold,ink,.8)+gem(0,94,4);
    if(rich)s+=mirror(repeat(4,i=>gem(39,-39+i*26,3.2))+scroll(31,-27,.62))+T(badge(r.motif,.5),0,-57);
    return s;
   }
   return null;
  }

  function weapon(){
   const additional=expansionWeapon();if(additional)return additional;
   if(['knife','dagger','kris','estoc','rapier','greatsword','sword','spellsword','sunblade','flamberge','sabre','falchion'].includes(f))return sword();
   if(f==='twinblades')return T(sword('sword'),-28,0,-12,.84)+T(sword('sabre'),30,8,10,.84);
   if(['spear','javelin','harpoon','halberd','griffinhalberd','guisarme','trident','stormfork','warscythe'].includes(f))return pole();
   if(['longbow','shortbow','recurve','rosebow','falconbow','antlerbow'].includes(f))return bow();
   if(['axe','hatchet'].includes(f))return axe();
   if(['mace','sunmace','hammer','clockhammer','pick','club','flail'].includes(f))return striking();
   if(['flute','lute','blowpipe'].includes(f))return instrument();
   if(['gauntlet','gloves'].includes(f))return gloves();
   if(f==='crossbow')return crossbow();
   if(f==='katar')return P('M0-103L19-63 12-13H-12L-19-63Z',steel,ink,1.5)+L('M0-96V-17',edge,1)+P('M-22-18H-13V79H-22ZM13-18H22V79H13Z',steel,ink,1.3)+P('M-18 15H18V24H-18ZM-18 53H18V62H-18Z',leather,ink,1)+rivet(-17,-12)+rivet(17,-12);
   if(f==='sickle')return grip(24,83,7)+P('M-7 24L-7-17Q-40-37-25-70Q-8-105 34-80Q6-86-8-65Q-22-42 7-23V24Z',steel,ink,1.5)+L('M-4-21Q-27-42-13-67Q1-90 27-81',edge,1.1)+C(0,88,7,wood,ink,1);
   if(f==='whip')return T(grip(24,92,7)+C(0,97,7,gold,ink,1),-32,5,-20)+L('M-24 27C-79-16-32-103 21-92C82-81 70-8 25-12C-10-14-7-58 27-55C58-51 60-20 48-8',ink,4)+L('M-24 27C-79-16-32-103 21-92C82-81 70-8 25-12C-10-14-7-58 27-55C58-51 60-20 48-8','#aa8050',2)+ (rich?L('M20-85L34-65 23-43 40-31',jewel,.8):'');
   return focus();
  }
  function scales(x,y,cols,rows,size=12){return repeat(rows,row=>repeat(cols,col=>{const xx=x+col*size,yy=y+row*size*.72;return P(`M${xx} ${yy}h${size}v${size*.36}q-${size/2} ${size} -${size} 0Z`,(row+col)%5===0&&rich?gold:steel,ink,.65)+L(`M${xx+2} ${yy+2}h${size-4}`,edge,.45);}));}
  function cuirass(){
   const mail=['mail','ringmail'].includes(f),lamellar=['lamellar','royallamellar','lorica'].includes(f),scaled=['scale','dragonmail','drakecloak'].includes(f),soft=['leather','brigandine','pactbrigandine','scoutvest','alchemist'].includes(f),sleeve=mail?25:soft?10:18,wide=45+(v%3)*3;
   let body=`M-19-74Q0-64 19-74L${wide}-60 ${wide+sleeve}-24 ${wide-2}-13 33-32 30 48 38 75Q0 89-38 75L-30 48-33-32-${wide-2}-13-${wide+sleeve}-24-${wide}-60Z`;
   let s=P(body,mail?(f==='ringmail'?leather:`url(#${id}mail)`):soft?leather:cloth,ink,1.6)+L('M-19-74Q0-59 19-74',rich?gold:'#bba888',3);
   if(mail){
    if(f==='ringmail')s+=`<clipPath id="${id}bodyclip">${P(body,'white')}</clipPath><g clip-path="url(#${id}bodyclip)">${repeat(12,row=>repeat(10,col=>E(-60+col*13+(row%2)*6,-55+row*11,5,4,'none','#b3b8b3',1)))}</g>`;
    s+=P('M-4 51H4L7 81H-7Z',black,ink,.6)+L('M-36 72Q0 82 36 72',gold,2)+mirror(P('M23-67L45-57 51-39 37-38 29-51Z',leather,ink,.8)+rivet(38,-52));
   }else if(soft){
    s+=L('M0-64V71',ink,2)+mirror(L('M23-56L19 23 23 69','#b59a79',.8));
    if(['brigandine','pactbrigandine'].includes(f))s+=repeat(8,row=>repeat(5,col=>rivet(-25+col*12.5,-49+row*16,.65)+rivet(-22+col*12.5,-49+row*16,.65)));
    else s+=repeat(5,i=>P(`M-5 ${-46+i*20}H9v6H-5Z`,wood,ink,.7)+rivet(6,-43+i*20,.7));
    if(['scoutvest','alchemist'].includes(f))s+=P('M-32-11L30 19 26 30-35 0Z',leather,ink,.9)+repeat(f==='alchemist'?6:5,i=>T(P('M-4-9H4V-3L6 1V16Q0 20-6 16V1L-4-3Z',f==='alchemist'?'#88a7a3':wood,ink,.8)+P('M-4-11H4V-7H-4Z',wood,ink,.6),-24+i*10,-3+i*5,-20));
    if(rich)s+=T(badge(r.motif,.7),0,-28)+mirror(scroll(24,36,.7));
   }else if(scaled||lamellar){
    if(f==='lorica')s+=repeat(7,i=>P(`M-33 ${-47+i*15}Q0 ${-38+i*15} 33 ${-47+i*15}V${-34+i*15}Q0 ${-23+i*15}-33 ${-34+i*15}Z`,steel,ink,1)+L(`M-28 ${-42+i*15}Q0 ${-34+i*15} 28 ${-42+i*15}`,edge,.6));
    else if(lamellar)s+=repeat(8,row=>repeat(6,col=>{const x=-34+col*11.5,y=-49+row*14;return P(`M${x} ${y}h11v15l-5 3-6-3Z`,steel,ink,.7)+rivet(x+3,y+4,.5)+rivet(x+8,y+4,.5)+L(`M${x+4} ${y+5}l3 5`,'#bda77d',.5);}));
    else s+=scales(-33,-48,6,10,11);
    s+=mirror(P('M23-70Q49-69 58-46L52-23 33-33 26-49Z',steel,ink,1.2)+L('M31-61Q44-60 50-46',edge,.8));
    if(rich)s+=mirror(T(badge(r.motif,.4),43,-48))+gem(0,-55,6);
   }else{
    const neck=v%2?'M-23-66Q0-48 23-66':'M-23-66L-10-58H10L23-66';
    const torso=neck+(v%3===0?'L34-49Q29-20 31 8L23 33Q0 48-23 33L-31 8Q-29-20-34-49Z':v%3===1?'L36-49Q38-26 28 9L23 33Q0 43-23 33L-28 9Q-38-26-36-49Z':'L35-50L30-22 28 13 20 32 0 43-20 32-28 13-30-22-35-50Z');
    s+=P(torso,steel,ink,1.6)+P('M0-55L-6 27 0 40 7 26Z','#aabac0','none')+L('M0-49V32','#e0e3d9',.8)+edging(torso,.94);
    s+=repeat(3,i=>P(`M-${27+i*3} ${29+i*10}Q0 ${43+i*10} ${27+i*3} ${29+i*10}V${39+i*10}Q0 ${53+i*10}-${27+i*3} ${39+i*10}Z`,steel,ink,1));
    s+=mirror(P('M25-73Q49-76 62-50L58-29 41-27 31-46Z',steel,ink,1.4)+repeat(3,i=>L(`M${37+i*2} ${-53+i*8}l${20-i*2} 5`,ink,.9))+rivet(44,-62)+rivet(55,-40));
    if(['boneplate'].includes(f))s+=mirror(repeat(5,i=>P(`M0 ${-42+i*15}Q-20 ${-54+i*15}-31 ${-35+i*15}Q-11 ${-43+i*15} 0 ${-31+i*15}Z`,bone,ink,.8)))+repeat(6,i=>P(`M0 ${-52+i*15}l5 6-5 8-5-8Z`,bone,ink,.7));
    else if(['engineplate','reactor'].includes(f))s+=T(badge('gear',1.15),0,-15)+mirror(L('M17-33L29-44 36-36V13L20 29',gold,2.3))+gem(0,-15,10);
    else if(f==='glassplate')s+=P('M0-50L21-29 17 7 0 24-17 7-21-29Z',`url(#${id}glass)`,gold,1.3)+L('M0-50V24M-21-29L17 7M21-29L-17 7',gold,1.2);
    else if(rich)s+=T(badge(r.motif,.9),0,-21)+mirror(scroll(26,-45,.58));
    if(f==='ramplate')s+=mirror(L('M38-64C63-80 64-36 44-46C33-52 42-66 49-58',bone,5));
    if(f==='thornplate')s+=mirror(L('M38-68Q54-45 43-30M45-53l12-9M47-43l12-2',gold,1.6));
    if(legend)s+=mirror(P('M31-74L45-86 57-78 54-66 42-61Z',gold,ink,1)+gem(45,-73,4))+mirror(scroll(26,44,.55));
   }
   if(t===2)s+=T(badge(r.motif,.6),0,-25)+mirror(L('M26-47L21 13',gold,.8));
   if(rich){
    s+=mirror(P('M29-67L36-63 32 25 25 24Z',gold,ink,.7)+scroll(35,-29,.45));
    s+=mirror(P('M-3 47L-31 43-38 72-7 78Z',soft?cloth:steel,gold,.9)+T(badge(r.motif,.25),-20,61));
    if(mail||soft)s+=T(badge(r.motif,.66),0,-25)+P('M-22-64H22V-55H-22Z',gold,ink,.7);
    if(legend)s+=mirror(repeat(3,i=>C(18+i*8,-47+Math.sin(i)*5,2.1,jewel,gold,.65)))+mirror(scroll(20,47,.55));
   }
   s+=P('M-33 27Q0 35 33 27V35Q0 43-33 35Z',leather,ink,1)+P('M-8 29H8V43H-8Z',gold,ink,.8)+P('M-5 32H5V40H-5Z',black,gold,.6);
   if(['dragonmail','drakecloak'].includes(f))s+=mirror(P('M32-72L45-83 58-64 66-46 54-43 40-54Z',steel,ink,1)+P('M43-76L47-93 53-73Z',bone,ink,.8));
   if(original==='splintcoat')s+=mirror(P('M-29-57L-42-48-36 23-26 19Z',steel,gold,.8)+repeat(6,i=>rivet(-34,-40+i*11,.6)))+P('M-28 49H28V59H-28Z',leather,gold,.8);
   if(original==='scalehauberk')s+=mirror(P('M-33-30L-52-33-56-8-39-1Z',steel,gold,1)+T(badge(r.motif,.38),-46,-18));
   if(original==='leathercoat')s+=mirror(P('M-13-64L-21-66-30-43-12-32Z',cloth,gold,.8)+P('M-29 40L-34 72-12 77-9 43Z',cloth,ink,1));
   if(original==='regalia')s+=mirror(P('M-31-69Q-58-81-69-54L-62-35-48-37-35-48Z',steel,gold,1.2)+T(badge(r.motif,.48),-52,-56)+repeat(4,i=>gem(-34-i*6,-60+i*5,1.7)))+P('M-11 39H11L18 90 0 84-18 90Z',cloth,gold,1)+T(badge(r.motif,.43),0,62);
   return s;
  }
  function garment(){
   const cape=['cloak','mantle','forestcloak','astralcloak','hood'].includes(f),robe=['robe','starrobe','alb','chasuble','seraphrobe'].includes(f),short=['tunic','gambeson'].includes(f),hem=short?58+(v%3)*5:f==='mantle'?64:f==='coat'?80:97,hood=f==='hood'||f==='forestcloak',stole=f==='stole';
   let d=cape?`M-19-69Q-39-70-47-48L-63 ${hem}Q-34 ${hem-9} 0 ${hem+3}Q34 ${hem-9} 63 ${hem}L47-48Q39-70 19-69Z`:`M-20-73L-43-58-66-12-45 0-30-27-27 13-41 ${hem}Q0 ${hem+7} 41 ${hem}L27 13 30-27 45 0 66-12 43-58 20-73Q0-60-20-73Z`;
   let s=P(d,cloth,ink,1.6)+`<clipPath id="${id}clothclip">${P(d,'white')}</clipPath>`;
   if(f==='gambeson')s+=`<defs><pattern id="${id}quilt" width="22" height="28" patternUnits="userSpaceOnUse">${L('M0 0L22 28M22 0L0 28','#bbb29a',.65)}</pattern></defs>`+P(d,`url(#${id}quilt)`,'none');
   if(cape){
    s+=P(`M-13-54L-31 ${hem-3}Q-9 ${hem-12} 0 ${hem+3}Q9 ${hem-12} 31 ${hem-3}L13-54Z`,dyeDark,ink,.8)+mirror(L(`M-34-36Q-32 17-52 ${hem-10}M-22-33L-25 ${hem-11}`,dye,.9));
    s+=P('M-23-66Q0-46 23-66L39-49 21-30H-21L-39-49Z',hood?cloth:leather,ink,1.1)+P('M-10-42H10V-35H-10Z',gold,ink,.8)+gem(0,-39,4);
    if(hood)s+=P('M-23-60Q-26-94 0-104Q26-94 23-60L13-44H-13Z',cloth,ink,1.3)+P('M-14-59Q-17-78 0-88Q17-78 14-59L8-49H-8Z',black,ink,.8);
   }else{
    s+=P(`M-6-63H6L10 ${hem}H-10Z`,robe?dyeDark:leather,ink,.6)+mirror(L(`M-23-39Q-13 9-30 ${hem-10}`,dyeDark,1.5))+mirror(L('M-45-44L-57-14',dye,.9));
    s+=P('M-22-73L0-60 22-73 13-49 0-57-13-49Z',f==='alb'?bone:leather,ink,1);
    s+=repeat(short?5:7,i=>C(0,-38+i*17,1.7,gold,ink,.5));
    if(!['chasuble','seraphrobe','alb'].includes(f))s+=P('M-28 8Q0 13 28 8V17Q0 22-28 17Z',leather,ink,1)+P('M-6 10H8V22H-6Z',gold,ink,.9);
   }
   if(stole){s=P('M-28-76Q0-97 28-76L34 86 10 91 3-59H-3L-10 91-34 86Z',cloth,ink,1.7)+P('M-23-71L-18 82H-27L-29-65Q0-86 29-65L27 82H18L23-71Q0-79-23-71Z',gold,ink,.7)+T(badge('sun',.45),-21,56)+T(badge('sun',.45),21,56);}
   if(f==='surcoat')s=P('M-22-75L-49-60-58-19-40-11-29-33-29 84H-7V32H7V84H29L29-33 40-11 58-19 49-60 22-75Z',`url(#${id}mail)`,ink,1.4)+P('M-18-73Q0-60 18-73L27-45 25 94H6L0 66-6 94H-25L-27-45Z',cloth,ink,1.3)+T(badge(r.motif,.85),0,-22)+P('M-27 15H27V25H-27Z',leather,ink,1)+P('M-6 15H6V26H-6Z',gold,ink,.8);
   if(c.type.includes('renforcé')&&cape)s+=mirror(P('M-23-66L-43-55-48-24-36-28-28-49Z',leather,ink,1)+repeat(3,i=>rivet(-36-i*2,-49+i*8,.8)));
   if(t===2&&f!=='stole'&&f!=='surcoat')s+=T(badge(r.motif,.46),0,-28)+mirror(L(`M-21 30L-28 ${hem-8}`,gold,.8));
   if(rich&&f==='surcoat')s+=mirror(L('M-21-48L-20 84',gold,1.5)+repeat(5,i=>T(badge(r.motif,.16),-17,34+i*10)))+gem(0,-24,5);
   if(rich&&f!=='surcoat'){
    s+=mirror(P(`M-31-51L-22-49-26 ${hem-5}-40 ${hem-9}Z`,gold,ink,.8)+P(`M-28-45L-25-44-31 ${hem-12}-35 ${hem-13}Z`,cloth,ink,.6));
    s+=mirror(repeat(6,i=>T(badge(r.motif,.18),-29,-28+i*18)));
    s+=mirror(scroll(29,43,.75))+mirror(scroll(29,-28,.62));
    if(!['stole','surcoat'].includes(f))s+=T(badge(r.motif,cape?.72:.85),0,-22);
    s+=`<g clip-path="url(#${id}clothclip)">${mirror(L(`M-35-58Q-38 20-53 ${hem-3}`,gold,2))+repeat(9,i=>T(badge(r.motif,.12),-48+i*12,hem-7))}</g>`;
    if(['starrobe','astralcloak'].includes(f))s+=mirror(L('M18 7L29 23 21 41 35 63 25 81',gold,.7)+repeat(5,i=>C(18+(i%2)*12,7+i*18,1.8,jewel,ink,.5)));
    if(f==='seraphrobe')s+=mirror(repeat(6,i=>T(P('M0 0Q-9-14 0-28Q9-14 0 0Z',bone,gold,.7)+L('M0-25V-3',gold,.6),31,-32+i*12,-20,.8)));
    if(f==='forestcloak')s+=mirror(P('M29-58Q47-70 50-48L39-32 26-37Z',leather,ink,1)+T(badge('leaf',.55),40,-43));
   }
   if(original==='ceremonialcape')s+=mirror(P('M-16-65Q-42-81-57-52L-47-37-25-39Z',rich?gold:leather,ink,1)+T(badge(r.motif,.54),-37,-54)+L('M-24-36L-38 76',bone,1.2))+L('M-24-48Q0-29 24-48',gold,1.6)+repeat(5,i=>gem(-20+i*10,-45+Math.sin(i/4*Math.PI)*8,2));
   return s;
  }
  function chest(){
   if(['robe','starrobe','alb','chasuble','seraphrobe','coat','cloak','mantle','forestcloak','astralcloak','hood','tunic','gambeson','stole','surcoat'].includes(f))return garment();
   if(f==='harness')return P('M-35-76L-17-83 37 70 20 78ZM35-76L17-83-37 70-20 78Z',leather,ink,1.4)+P('M-43 38Q0 46 43 38V56Q0 64-43 56Z',leather,ink,1.3)+C(0,-2,22,steel,ink,1.2)+T(badge(r.motif,.6),0,-2)+mirror(repeat(5,i=>rivet(9+i*3,20+i*9)))+P('M-9 42H9V59H-9Z',gold,ink,1);
   return cuirass();
  }
  function shield(){
   const round=['round','buckler','sunshield','gearshield','astrolabe','iris'].includes(f),small=f==='buckler',radius=small?59:77;
   const d=original==='dragonshield'?'M0-99L23-83 47-90 57-64 71-58 58-21 62 2 42 21 35 54 0 96-35 54-42 21-62 2-58-21-71-58-57-64-47-90-23-83Z':round?`M0-${radius}A${radius} ${radius} 0 1 1-.01-${radius}Z`:['pavise','runepavise'].includes(f)?'M-54-87Q0-105 54-87L60 78Q30 94 0 87Q-30 94-60 78Z':f==='scutum'?'M-53-82Q0-94 53-82L53 81Q0 94-53 81Z':f==='kite'?'M0-100Q68-83 58-29Q44 29 0 102Q-44 29-58-29Q-68-83 0-100Z':f==='targe'?'M-64-57L-35-83H34L65-47 57 43 0 84-57 43Z':f==='leafshield'?'M0-97Q76-47 54 25Q32 70 0 91Q-32 70-54 25Q-76-47 0-97Z':rich?'M-63-74L-43-72-33-85Q0-69 33-85L43-72 63-74 57 14Q39 64 0 96Q-39 64-57 14Z':'M-62-76Q0-62 62-76L56 20Q34 69 0 94Q-34 69-56 20Z';
   const faceMat=t===0&&v%3===0?wood:cloth;
   let s=T(P(d,black,ink,2),3,4)+P(d,rich?gold:steel,ink,1.5)+edging(d,.975)+T(P(d,black,ink,.8),0,0,0,.91)+T(P(d,small?steel:faceMat,rich?gold:edge,1),0,0,0,.86)+`<clipPath id="${id}shieldclip">${P(d,'white').replace('<path','<path transform="scale(.84)"')}</clipPath>`;
   let panels='';
   if(!small){
    if(!rich){panels+=repeat(7,i=>L(`M${-62+i*20}-102V104`,ink,1.3)+L(`M${-60+i*20}-102V104`,'#b3b3a0',.4));
     if(v%3===0)panels+=P('M-80-13H80V13H-80Z',leather,ink,1)+repeat(8,i=>rivet(-67+i*19,0,.8));
     if(v%3===1)panels+=P('M-82-83L82 83V102L-82-64Z',dyeDark,rich?gold:steel,1);
     if(v%3===2)panels+=P('M-80-100H0V0H80V100H0V0H-80Z',dyeDark,'none');
    }else{
     panels+=P('M-80-110H0V0H80V110H0V0H-80Z',dyeDark,'none')+L('M0-100V100M-80 0H80',gold,1.3);
     panels+=mirror(P('M-50-65Q-34-80-11-64L-10-12Q-30-16-52-37Z',black,gold,.85)+P('M-49 20Q-29 13-11 30L-6 74Q-30 57-41 41Z',black,gold,.85)+scroll(-31,-56,.68)+scroll(-27,27,.6));
     panels+=repeat(2,side=>repeat(7,i=>T(P('M0-2L2 0 0 2-2 0Z',gold,'none'),side?45:-45,-56+i*17)));
    }
   }
   s+=`<g clip-path="url(#${id}shieldclip)">${panels}</g>`;
   if(round){
    s+=C(0,0,radius-9,'none',rich?bone:edge,.7)+repeat(small?16:24,i=>T(rivet(radius-4.5,0,.75),0,0,i*360/(small?16:24)));
    if(rich){s+=C(0,0,radius-16,'none',gold,1.1)+repeat(12,i=>T(P(`M-3-${radius-19}Q0-${radius-29} 3-${radius-19}L0-${radius-12}Z`,gold,ink,.6),0,0,i*30))+repeat(4,i=>T(gem(0,-radius+8,3.5),0,0,i*90));}
    if(['astrolabe','iris'].includes(f)){
     s+=C(0,0,46,black,gold,1.5)+repeat(24,i=>T(L(`M0-43V-${i%3===0?35:39}`,bone,.9),0,0,i*15))+T(E(0,0,43,17,'none',gold,1.8),0,0,-32)+T(E(0,0,43,17,'none',edge,1.2),0,0,54);
     if(f==='iris')s+=repeat(8,i=>T(P('M-8-31L17-34 28-11 12 1 13-14Z',steel,gold,.8),0,0,i*45));
     s+=gem(0,0,13)+repeat(4,i=>T(gem(0,-43,3),0,0,22+i*90));
    }else if(f==='sunshield'||f==='gearshield')s+=C(0,0,43,black,gold,1.5)+T(badge(f==='sunshield'?'sun':'gear',1.7),0,0)+repeat(8,i=>T(P('M-2-43L0-53 2-43Z',gold,ink,.6),0,0,i*45));
    else{s+=C(0,0,rich?30:26,black,gold,1.4)+C(0,0,rich?23:20,steel,edge,1)+P('M0-19Q-24-6-12 13L0 20Q18 8 19-3Q13-17 0-19Z',steel,ink,.7)+L('M-10-11Q-17 0-9 10',bone,1.2)+repeat(8,i=>T(rivet(23,0,.75),0,0,i*45));if(rich)s+=T(badge(r.motif,.53),0,0)+gem(0,0,5);}
   }else{
    if(['pavise','runepavise','scutum'].includes(f))s+=P('M-8-79Q0-86 8-79V75L0 83-8 75Z',steel,ink,1)+P('M0-81L5-77V73L0 79Z',edge,'none')+mirror(P('M-48-60H-14V-52H-48ZM-47 50H-14V58H-47Z',rich?gold:steel,ink,.8)+rivet(-43,-56)+rivet(-43,54));
    if(f==='leafshield')s+=P('M0-79Q-7-20 0 74Q7-20 0-79Z',gold,ink,.8)+mirror(repeat(4,i=>P(`M-3 ${-49+i*29}Q-35 ${-69+i*30}-43 ${-42+i*27}Q-27 ${-32+i*27}-3 ${-40+i*29}Z`,rich?gold:leather,ink,.65)));
    else if(rich){
     s+=P('M0-49L34-33 31 17Q20 44 0 56Q-20 44-31 17L-34-33Z',gold,ink,1.3)+P('M0-43L28-29 26 15Q17 37 0 49Q-17 37-26 15L-28-29Z',black,bone,.8)+T(badge(f==='falconshield'?'feather':f==='lionshield'?'lion':r.motif,1.1),0,-2)+gem(0,39,3.5);
    }else s+=T(badge(f==='falconshield'?'feather':r.motif,1.15),0,-4);
    // Border studs follow this shield's actual curved outline, never floating outside it.
    if(f==='kite'||f==='leafshield')s+=mirror(repeat(7,i=>{const a=(-68+i*20)*Math.PI/180;return rivet(Math.cos(a)*47,-18+Math.sin(a)*67,.7);}));
    else s+=mirror(repeat(7,i=>rivet(['pavise','runepavise','scutum'].includes(f)?49:52-Math.max(0,i-2)*7,-57+i*20,.7)));
    if(rich)s+=T(P(d,'none',bone,.7),0,0,0,.95)+mirror(P('M-55-64L-39-68-43-53-55-44Z',gold,ink,.8)+gem(-48,-59,3.5)+scroll(-39,18,.65));
   }
   if(['kingshield','lionshield'].includes(f))s+=P('M-40-74L-33-97-19-84 0-109 19-84 33-97 40-74Z',gold,ink,1.2)+P('M-28-79L-24-88-16-79 0-96 16-79 24-88 28-79Z',black,bone,.7)+repeat(5,i=>gem(-28+i*14,-78-(i===2?9:0),3));
   if(f==='thornshield'||f==='spikeshield')s+=mirror(P('M56-54L69-70 63-39ZM44 28L58 34 46 45Z',steel,gold,1));
   if(f==='runepavise')s+=rune(-34,-47,9)+rune(34,-47,9);
   if(f==='eclipseshield')s+=C(0,0,33,black,gold,2)+T(badge('moon',1.2),0,0)+repeat(8,i=>T(P('M0-37L2-44 0-49-2-44Z',jewel,gold,.7),0,0,i*45));
   if(original==='hornshield')s+=r.motif==='boar'?mirror(P('M-45-51Q-72-39-63-9Q-62-31-44-32Z',bone,gold,.8)):mirror(L('M-42-65Q-73-87-64-104M-63-91L-77-99M-57-79L-76-83',gold,3))+T(badge('stag',.65),0,-59);
   if(original==='phoenixshield')s+=mirror(P('M-44-37L-71-59-69-39-61-32-64-25-54-15-56-8-42 2Z',gold,ink,1)+L('M-65-48L-47-19',bone,1));
   if(original==='dragonshield')s+=mirror(P('M-46-56L-52-30-41-9-32-17-38-37Z',cloth,gold,1)+scroll(-38,-48,.6))+T(badge('dragon',.7),0,-57);
   if(original==='moonscutum')s+=mirror(P('M-40-52Q-12-48-30-28Q-20-44-40-43Z',bone,gold,.7))+repeat(5,i=>gem(-24+i*12,67,2));
   if(legend&&!round)s+=mirror(P('M-57-27L-64-40-64-9-48 11-54-5Z',gold,ink,.9)+gem(-54,-21,4))+gem(0,76,4.5);
   return s;
  }
  function amulet(){
   let s=L('M-2-95C-72-95-63-34-28-4L0 18 28-4C63-34 72-95 2-95',gold,2);
   if(f==='rosary')s+=repeat(16,i=>{const a=i*Math.PI*2/16;return C(Math.sin(a)*48,-47+Math.cos(a)*47,3.5,wood,gold,.65);});
   s+=E(0,22,6,9,'none',gold,2)+C(0,55,27,gold,ink,1.3)+C(0,55,22,bone,ink,.8)+T(badge(r.motif,.68),0,55);
   return s;
  }
  function guard(){
   const additional=expansionGuard();if(additional)return additional;
   if(['round','buckler','sunshield','gearshield','astrolabe','iris','pavise','runepavise','scutum','kite','targe','leafshield','heater','thornshield','kingshield','lionshield','eclipseshield','spikeshield','falconshield'].includes(f))return shield();
   if(['amulet','rosary'].includes(f))return amulet();
   if(['book','lantern','mothlantern','seaorb'].includes(f))return focus(f);
   if(f==='bracers')return [-1,1].map((side,i)=>T(P('M-22-60Q0-68 22-60L16 65Q0 73-16 65Z',leather,ink,1.5)+P('M-14-51Q0-57 14-51L11 56Q0 62-11 56Z',wood,ink,.9)+L('M-9-45L-6 50M9-45L6 50','#ba9366',.7)+repeat(3,j=>P(`M-22 ${-39+j*38}h43v8h-43Z`,leather,ink,.8)+P(`M9 ${-41+j*38}h10v12H9Z`,gold,ink,.8))+ (rich?T(badge(r.motif,.45),0,0):''),side*32,i?8:-8,side*8,.95)).join('');
   if(['seal','prism','sundial'].includes(f)){
    let s=C(0,0,67,steel,ink,1.4)+C(0,0,58,black,gold,1.2)+repeat(12,i=>T(L('M0-53V-45',gold,1),0,0,i*30));
    if(f==='sundial')s+=P('M-32 25L0-39V25Z',gold,ink,1.1)+L('M0 25L34 37',ink,2);
    else if(f==='prism')s+=P('M0-49L42-22 42 24 0 49-42 24-42-22Z',`url(#${id}glass)`,gold,1.3)+L('M0-49V49M-42-22L42 24M42-22L-42 24',edge,1)+gem(0,0,6);
    else s+=T(badge(r.motif,1.6),0,0);
    if(rich&&f==='prism')s+=repeat(6,i=>T(P('M-4-52L0-62 4-52 3-37-3-37Z',gold,ink,.7)+gem(0,-48,3),0,0,i*60))+P('M0-45L35-20 0-3Z','#65959e',edge,.6)+P('M-35 22L0 43 0 6Z','#75668f',edge,.6);
    return s+repeat(8,i=>T(rivet(63,0),0,0,i*45));
   }
   if(['mirror','eyemirror'].includes(f))return E(0,0,58,87,rich?gold:steel,ink,1.6)+E(0,0,48,76,`url(#${id}glass)`,black,2)+L('M-28-48L21-61M-36-22L34-42',edge,.9)+ (f==='eyemirror'?P('M-29 0Q0-24 29 0Q0 24-29 0Z',black,gold,1)+E(0,0,7,13,jewel,ink,1)+E(0,0,2.5,10,ink,'none')+C(-2,-4,1.5,bone,'none'):'')+mirror(scroll(48,-36,.7))+mirror(scroll(48,28,.7))+T(badge(r.motif,.5),0,-77)+gem(0,81,5)+(legend?P('M-27-75L-21-99-10-87 0-105 10-87 21-99 27-75Z',gold,ink,1)+mirror(repeat(5,i=>gem(51,-48+i*23,2.5)))+L('M-23 35L-4 15-9-1 10-20 5-43','#a8b6c5',.8):'');
   if(f==='beetleshield')return P('M-26-53Q-25-85 0-93Q25-85 26-53Z',steel,gold,1.5)+P('M-36-51Q0-74 36-51L42-26H-42Z',black,gold,1.5)+mirror(P('M-3-28Q-45-52-56-3Q-66 45-4 83Z',cloth,gold,1.5)+L('M-10-20Q-43-10-43 25Q-38 53-10 70',gold,1)+P('M-43-25L-63-39-69-30-51-13ZM-53 5L-70 16-67 30-52 21ZM-45 40L-57 61-47 71-34 58Z',steel,ink,1))+gem(0,-46,8)+P('M-2-24H2V79H-2Z',gold,ink,.6)+(rich?mirror(repeat(4,i=>L(`M-8 ${-13+i*19}Q-31 ${-26+i*20}-48 ${-3+i*18}`,gold,.8))+scroll(-28,-6,.65)+scroll(-25,31,.55)+gem(-43,4,3))+T(badge('leaf',.42),0,-71):'');
   if(f==='shell'){
    const outline='M0-88Q75-71 68 7Q56 62 0 89Q-56 62-68 7Q-75-71 0-88Z';
    let s=T(P(outline,black,ink,2),0,4)+P(outline,rich?gold:steel,ink,1.7)+T(P(outline,black,gold,1.1),0,0,0,.91);
    s+=mirror(P('M-4-72Q-50-62-55-12Q-58 35-5 72L-4 36-29 19-6-1-31-23-5-43Z',cloth,gold,1.1)+P('M-5-42L-30-22-5-2-29 19-5 36V70Q-44 43-46 16Q-53-25-5-65Z',cloth,gold,1)+L('M-10-53L-30-34M-12-18L-42-6M-12 20L-39 27M-13 47L-28 45',edge,.7));
    s+=P('M0-77L9-56 5-36 11-18 5 3 10 25 4 45 0 77-4 45-10 25-5 3-11-18-5-36-9-56Z',steel,gold,1.2);
    if(rich)s+=mirror(repeat(5,i=>{const y=-57+i*26,x=55*Math.sqrt(Math.max(0,1-(y/85)**2));return gem(x,y,2.5);})+scroll(27,-32,.65)+scroll(26,19,.6)+L('M-8-66Q-46-53-48-12Q-45 30-8 60','#eed7a4',.8))+T(badge('leaf',.58),0,-42)+gem(0,5,9)+gem(0,46,4);
    return s;
   }
   if(['window','reliquary','triptych'].includes(f)){
    let center=P('M0-93L37-62V83H-37V-62Z',gold,ink,1.5)+P('M0-79L27-56V72H-27V-56Z',`url(#${id}glass)`,black,1)+L('M0-79V72M-27-25H27M-27 35H27',gold,2)+T(badge(magic&&!holy?'star':'sun',.9),0,2)+repeat(4,i=>rivet(-32,-35+i*32)+rivet(32,-35+i*32));
    if(magic&&!holy)center+=P('M0-75L23-54V-29H3Z','#71859f',gold,1)+P('M-23-22H-3L-12 31H-23Z','#656592',gold,1)+P('M4 41H23V67H-12Z','#8b6f8f',gold,1)+P('M8-34L-9 1H5L-6 35 16-8H3Z',bone,gold,.7);
    if(f==='triptych')center=mirror(P('M-40-64L-74-51V70L-40 83Z',gold,ink,1.3)+P('M-45-51L-67-43V60L-45 69Z',cloth,ink,1)+L('M-40-48V-35M-40 38V51',black,4)+T(badge('feather',.6),-56,5))+center;
    if(rich)center+=mirror(P('M-37-59L-42-65-42 69-37 78Z',steel,gold,.8)+L('M-32-48L-18-65 0-75',bone,.8)+T(badge('lily',.24),-33,-63)+gem(-32,26,3))+P('M0-103L6-93 0-84-6-93Z',bone,gold,1)+L('M-27-24Q-27-43-14-44Q0-43 0-24Q0-43 14-44Q27-43 27-24',gold,1.4)+L('M-27 70Q-27 50-14 49Q0 50 0 70Q0 50 14 49Q27 50 27 70',gold,1.1)+gem(0,76,4);
    return center;
   }
   if(f==='mask')return P('M-48-55Q0-83 48-55L43 22 24 59 0 76-24 59-43 22Z',bone,ink,1.7)+P('M-34-20Q-22-29-9-15L-18-4-31-8ZM34-20Q22-29 9-15L18-4 31-8Z',black,ink,1)+P('M0-26L-7 23 0 28 7 23Z',steel,ink,.8)+L('M-17 43Q0 49 17 43',ink,1.1)+L('M-43-51Q-24-66-4-56',gold,1.3)+rivet(-39,0)+rivet(39,0);
   if(f==='fan')return P('M0 77L-77-26Q0-94 77-26Z',cloth,ink,1.5)+repeat(9,i=>{const x=-77+i*19.25,y=-26-34*(1-(x/77)**2);return L(`M0 77L${x} ${y}`,gold,1.6);})+L('M-68-15Q0-77 68-15',gold,2)+C(0,77,6,gold,ink,1)+T(badge(r.motif,.7),0,-16);
   if(f==='foldshield')return repeat(6,i=>T(P('M-24-4L-31-66 0-84 31-66 24-4Z',steel,ink,1.3)+P('M-16-29L-20-61 0-73 20-61 16-29Z',cloth,gold,1)+rivet(-24,-54)+rivet(24,-54),0,0,i*60))+C(0,0,24,gold,ink,1.3)+gem(0,0,14);
   throw Error('Unknown protection '+f);
  }
  function legs(){
   if(f==='knees')return [-1,1].map((side,i)=>T(repeat(2,j=>P(`M-28 ${-26+j*51}H28v9H-28Z`,leather,ink,1)+P(`M18 ${-28+j*51}h11v13H18Z`,gold,ink,.8))+P('M0-49Q-30-38-27-4Q-26 26 0 47Q26 26 27-4Q30-38 0-49Z',steel,ink,1.6)+P('M0-34L17-16 19 8 0 29-19 8-17-16Z',cloth,gold,1)+L('M0-32V27',edge,1)+rivet(-22,-7)+rivet(22,-7)+T(badge(r.motif,.42),0,-2),side*39,i*13-7,side*8,1.2)).join('');
   if(f==='cuisses')return [-1,1].map((side,i)=>T(P('M-27-78Q0-92 27-78L23 25Q0 40-23 25Z',leather,ink,1.5)+P('M-23-72Q0-83 23-72L19 23Q0 34-19 23Z',steel,ink,1.3)+L('M0-76V23',edge,1)+repeat(2,j=>P(`M-29 ${-57+j*57}H29v8H-29Z`,leather,ink,1)+rivet(24,-53+j*57))+P('M0 29L24 44 22 63 0 78-22 63-24 44Z',steel,ink,1.3)+T(badge(r.motif,.5),0,52)+(rich?T(badge(r.motif,.65),0,-25)+mirror(scroll(15,-49,.5))+L('M-19-70V18M19-70V18',gold,1.2)+gem(0,50,4):''),side*37,i*12-6,side*5,1)).join('');

   if(f==='trousers')return P('M-40-91Q0-99 40-91L48 86 14 91 0-16-14 91-48 86Z',cloth,ink,1.6)+P('M-40-91H40V-76H-40Z',leather,ink,1)+P('M-7-91H7V-76H-7Z',gold,ink,.8)+L('M0-73V-20M-31-65L-36 77M31-65L36 77',dyeDark,1.2)+P('M-37-18L-14-20-12 13-40 17ZM37-18L14-20 12 13 40 17Z',leather,ink,1)+repeat(3,i=>L(`M-32 ${-10+i*8}h14M18 ${-10+i*8}h14`,'#ba9d7a',.7));
   // Front three-quarter construction: ankle, instep, rounded toe box and a
   // continuous welt. The second boot is mirrored and sits slightly behind.
   const plate=['greaves','sabatons','scalegreaves','ridinggreaves','royalgreaves','wolfgreaves','ashgreaves','sungreaves','angelgreaves','tidegreaves','palegreaves','runegreaves','winggreaves'].includes(f),sandals=['sandals','wingsandals'].includes(f),short=['slippers','sabatons'].includes(f),greaveOnly=f==='cnemides',fur=['furboots','lynxboots'].includes(f),bootLeather=rich&&(magic||holy)?cloth:leather,wrap=['wraps','gaiters'].includes(f),top=short?-14:-87;
   function boot(){
    const toe='M-12 24Q-13 39-21 52C-28 65-26 77-14 82Q6 91 23 81C35 74 31 63 23 51Q15 39 12 24Z';
    const sole='M-25 69Q-26 80-13 85Q7 93 25 82L29 76V84Q9 100-15 91Q-28 87-28 78Z';
    let s='';
    if(greaveOnly)return P('M-23-86Q0-100 23-86L16 59Q0 74-16 59Z',leather,ink,1.5)+P('M-18-80Q0-91 18-80L12 54Q0 66-12 54Z',gold,ink,1.3)+L('M0-82Q-5-9 0 58',bone,1.2)+repeat(3,i=>P(`M-25 ${-56+i*45}H25v7H-25Z`,leather,ink,.8)+rivet(21,-52+i*45))+(rich?T(badge(r.motif,.5),0,-29)+mirror(scroll(10,5,.5)):'');
    if(sandals){
     s=P('M-13-50Q-33-33-25-2L-29 31Q-33 58-15 75Q9 90 29 69Q36 54 25 33L17 10Q28-21 13-48Q0-57-13-50Z',leather,ink,1.5)+P('M-11-43Q-25-27-18-2L-22 32Q-28 55-11 68Q8 78 23 63Q29 52 19 36L10 10Q20-20 9-42Q0-48-11-43Z','#a17a4e','#dbc29a',.8);
     s+=P('M-23-24Q0-37 22-24L20-13Q0-24-22-13ZM-24 8Q0-4 19 10L20 21Q0 9-26 20ZM-27 33Q0 19 26 36L30 47Q1 32-28 46ZM-21 53Q4 43 30 57L26 68Q4 54-14 66Z',leather,ink,1.3)+L('M-22-19Q0-31 21-19M-22 13Q0 1 19 15M-25 39Q0 26 26 41M-16 58Q4 49 28 62',rich?gold:'#c2a37b',.8)+P('M-19-42L-24-23-20-17-12-39ZM10-41L18-18 23-24 16-44Z',leather,ink,1)+P('M11-27H23V-12H11Z',gold,ink,.8)+P('M14-24H20V-15H14Z',black,gold,.6);
     if(rich)s+=gem(0,38,5)+T(badge(r.motif,.38),0,11)+mirror(scroll(13,48,.3));
    }else{
     const body=`M-21 ${top}Q-2 ${top-9} 20 ${top+1}C20 ${top+31} 9 8 12 29Q15 42 23 51C32 63 34 75 23 81Q7 91-14 82C-27 77-27 65-21 52Q-11 35-13 20L-21 ${top+20}Z`;
     s=P(sole,'#25252b',ink,1)+P(body,bootLeather,ink,1.7)+P('M12 24Q15 42 23 51Q34 66 28 76L21 80Q25 67 14 54L3 36Z','#42322d',ink,.65)+L('M-22 69Q-24 77-10 81Q7 87 24 78','#d7b88d',1.1);
     s+=P(`M-21 ${top}Q-2 ${top-9} 20 ${top+1}L19 ${top+12}Q-2 ${top+2}-20 ${top+10}Z`,fur?bone:cloth,ink,1)+E(-1,top+1,18,4.5,black,'#ac9172',.8)+L(`M-16 ${top+15}Q-11-5-9 24M15 ${top+15}Q8 5 9 24`,'#a68564',.8);
     if(plate){
      if(!short)s+=P('M-20-80Q0-88 18-79L13-49Q-1-40-16-48Z',steel,ink,1.2)+P('M-15-49Q0-62 16-48L21-30 13-18H-14L-23-29Z',steel,gold,1)+P('M-13-15Q0-23 13-15L11 25 0 36-13 25Z',steel,ink,1.2)+L('M0-13V28',edge,1.2)+rivet(-17,-31)+rivet(16,-31);
      else s+=P('M-18-10Q0-20 17-8L12 24 0 31-14 24Z',steel,ink,1.3)+L('M0-10V23',edge,1);
      if(['scalegreaves','tidegreaves'].includes(f))s+=scales(-12,-11,3,3,8);
      s+=P(toe,steel,ink,1.2)+repeat(4,i=>{const y=29+i*10,w=12+i*3.1;return P(`M-${w} ${y}Q0 ${y+9} ${w} ${y}L${w+3} ${y+10}Q0 ${y+20}-${w+3} ${y+10}Z`,steel,ink,.9)+L(`M-${w-2} ${y+2}Q0 ${y+10} ${w-2} ${y+2}`,edge,.8);})+P('M-23 70Q0 81 28 69Q29 79 16 83Q-7 89-22 78Z',steel,ink,1)+L('M-19 73Q1 81 24 73',edge,1);
      if(rich)s+=P('M-12-75Q0-81 11-74L8-52Q0-47-10-52Z',cloth,gold,.9)+L('M-8-73Q0-76 7-72L5-55',bone,.7)+P('M0-10L7-3 5 21 0 28-6 20-8-3Z',cloth,gold,.8)+mirror(scroll(5,2,.25))+T(badge(f==='wolfgreaves'?'wolf':f==='sungreaves'?'sun':r.motif,.56),0,-33)+mirror(L('M10-10L8 23',gold,1.7))+P('M-17-79Q0-85 16-77L15-72Q0-78-16-73Z',gold,ink,.7)+gem(0,27,4)+T(badge(r.motif,.31),1,70);
      if(t===2)s+=T(badge(r.motif,.36),0,-33)+scroll(-4,0,.4);
      if(f==='ridinggreaves')s+=L('M-17 33L-29 36',gold,2)+T(badge('gear',.22),-30,37);
     }else{
      s+=L('M-11 31Q-9 43-17 55Q1 46 21 56M-20 62Q1 52 25 65','#c3a17a',.8);
      if(wrap)s+=repeat(7,i=>P(`M-18 ${-64+i*12}Q0 ${-71+i*12} 17 ${-66+i*12}L16 ${-59+i*12}Q0 ${-63+i*12}-17 ${-57+i*12}Z`,cloth,ink,.7));
      else if(f==='laceboots'||short||v%3===1){const n=short?3:7;const y0=short?0:-65;s+=P(`M-8 ${y0-5}Q0 ${y0-8} 8 ${y0-5}L7 28H-7Z`,cloth,ink,.6)+repeat(n,i=>{const y=y0+i*11;return C(-7,y,1.4,gold,ink,.3)+C(7,y,1.4,gold,ink,.3)+L(`M-7 ${y}L7 ${y+9}M7 ${y}L-7 ${y+9}`,'#d1b68a',1);});}
      else s+=repeat(short?1:3,i=>{const y=short?13:-56+i*31;return P(`M-18 ${y}Q0 ${y+5} 17 ${y-2}V${y+6}Q0 ${y+13}-17 ${y+8}Z`,leather,ink,.9)+P(`M7 ${y-3}H18V${y+10}H7Z`,gold,ink,.7)+P(`M10 ${y}H15V${y+7}H10Z`,black,ink,.5);});
      if(fur)s+=P(`M-22 ${top+4}l5-5 5 3 5-5 5 4 6-4 5 4 8-1-1 17-6-3-4 5-6-4-5 4-6-3-5 3Z`,bone,ink,1)+repeat(7,i=>L(`M${-19+i*5} ${top+5}l1 8`,'#897758',.6));
      if(rich)s+=P('M-19 58Q0 48 23 59L26 67Q0 56-22 68Z',cloth,gold,1)+mirror(scroll(11,61,.32))+T(badge(r.motif,.48),0,short?16:-33)+mirror(L(`M-16 ${top+17}Q-12 3-12 25`,gold,1.1));
      if(magic&&!wrap)s+=rune(-2,short?6:-9,3);
      if(f==='thornboots'||f==='lynxboots')s+=P('M-18 52Q-30 42-22 30L-16 35-21 39-12 48Z',gold,ink,.8)+L('M-15 51L-6 64M-10 48L0 61',gold,1);
     }
     if(legend)s+=gem(0,top+19,5)+mirror(scroll(9,-66,.42))+P('M-13 20Q0 27 12 20L14 29Q0 37-14 29Z',gold,ink,.7)+repeat(5,i=>rivet(-13+i*6,75+Math.sin(i/4*Math.PI)*4,.55));
     if(f==='crampons')s+=P('M-26 78L-25 89-20 84-13 94-8 87 2 96 8 89 18 94 23 85 29 87 29 77Q8 95-26 78Z',steel,ink,.8);
    }
    if(['wingboots','wingsandals','winggreaves','angelgreaves','navigatorboots'].includes(f))s+=T(repeat(5,i=>T(P('M0 0Q-10-9-6-33Q6-24 0 0Z',plate?gold:bone,ink,.8)+L('M-5-28L0-4',gold,.7),0,i*2,18+i*10,.85))+gem(0,0,3),17,sandals?10:22);
    if(['orbitalboots','portalboots'].includes(f))s+=E(0,3,22,7,'none',gold,1.3)+T(E(0,0,24,8,'none',jewel,1),0,3,-28)+gem(0,3,5);
    if(original==='foldedboots'||original==='tallcavalry')s+=P('M-24-84Q0-94 23-83L19-56Q0-68-20-58Z',cloth,gold,1)+L('M-22-80Q0-88 21-79',bone,.8)+(rich?T(badge(r.motif,.44),0,-71)+gem(0,-52,3):'');
    if(original==='runecuffs')s+=P('M-21-10Q0-22 21-8L17 18Q0 12-18 18Z',cloth,gold,1)+rune(-5,-8,2)+rune(6,-8,2)+(rich?gem(0,28,5):'');
    if(original==='pilgrimshoes')s+=P('M-18 22Q0 30 18 22V32Q0 42-18 32Z',leather,gold,1)+gem(0,31,rich?5:3);
    if(original==='drakegreaves')s+=P('M-19-79L-5-91 0-83 5-91 19-79 13-52H-13Z',steel,gold,1)+T(badge(r.motif,.42),0,-66)+mirror(P('M-13-4L-18 9-13 22-9 9Z',gold,ink,.7));
    if(original==='tideboots')s+=P('M-18-62Q0-69 17-60L13-26-13-26Z',cloth,gold,.8)+T(badge('anchor',.48),0,-43)+P('M-14 24Q0 30 13 24L17 40Q0 34-18 40Z',leather,gold,.8);
    return s;
   }
   return T('<g transform="scale(-1 1)">'+boot()+'</g>',-34,-7,-6,.98)+T(boot(),34,9,7,1.04);
  }
  const shape=c.slot==='weapon'?weapon():c.slot==='chest'?chest():c.slot==='guard'?guard():legs();
  return {defs,shape,angle:r.angle,scale:c.slot==='weapon'?1.15:c.slot==='legs'?1.14:1.22};
 }
 return {make};
})();
