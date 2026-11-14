'use strict';
/** Individually drawn relic silhouettes. All coordinates are local to the item,
 * so the opening, collection and review use exactly the same original artwork. */
const RelicSculpt=(()=>{
 function make(card,id,p){
  if(!card.design)return null;
  const [stone,light,shadow,,gold]=p,M=`url(#${id}metal)`,F=`url(#${id}cloth)`,G=`url(#${id}gilt)`,V=`url(#${id}glass)`;
  const P=(d,f=M,s=gold,w=.85)=>`<path d="${d}" fill="${f}" stroke="${s}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"/>`;
  const L=(d,s=light,w=.6)=>P(d,'none',s,w);
  const C=(x,y,r,f=M,s=gold,w=.8)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${f}" stroke="${s}" stroke-width="${w}"/>`;
  const E=(x,y,rx,ry,f='none',s=gold,w=1)=>`<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="${f}" stroke="${s}" stroke-width="${w}"/>`;
  const T=(body,x=0,y=0,angle=0,scale=1)=>`<g transform="translate(${x} ${y}) rotate(${angle}) scale(${scale})">${body}</g>`;
  const mirror=body=>T(body)+`<g transform="scale(-1 1)">${body}</g>`;
  const jewel=(x,y,r=6,col=stone)=>T(P(`M0-${r}L${r*.7} 0 0 ${r}-${r*.7} 0Z`,col,light,.65)+P(`M0-${r}V${r}L-${r*.7} 0Z`,shadow,'none')+P(`M0-${r}L${r*.7} 0H0Z`,light,'none'),x,y);
  const studs=(points)=>points.map(([x,y])=>C(x,y,1.5,G,shadow,.4)+C(x-.3,y-.5,.5,light,'none')).join('');
  const curl=(x,y,scale=1)=>T(L('M0 30C15 18-10 9 0-2C13-15 23 1 14 4C6 7 6-5 13-6M1 16C21 7 23 25 12 23M1 26C-13 16-16 31-6 31',gold,.85),x,y,0,scale);
  const feather=(x,y,len=34,angle=0)=>T(P(`M0 0Q-14-${len*.6} 0-${len}Q10-${len*.4} 0 0Z`,M,light,.65)+L(`M0-2V-${len-3}M0-10l-5-6M0-20l-5-6M0-15l4-5`,gold,.5),x,y,angle);
  const chain=(x,y,n=7,angle=0)=>T(Array.from({length:n},(_,i)=>E(0,i*5.3,2.2,3.4,'none',i%2?light:gold,.9)).join(''),x,y,angle);
  const ring=(x,y,r,angle=0)=>T(E(0,0,r,r*.35,'none',gold,1.8)+E(0,-1,r-1,r*.35-1,'none',light,.45),x,y,angle);
  const gear=(x,y,r,teeth=12)=>T(Array.from({length:teeth},(_,i)=>T(P(`M-2-${r-2}V-${r+3}H2V-${r-2}`,G,light,.5),0,0,i*360/teeth)).join('')+C(0,0,r,M,gold,1.5)+C(0,0,r-4,shadow,light,.6),x,y);
  const glyphs=(x,y,n=6)=>Array.from({length:n},(_,i)=>T(L('M-1-3V3m0-5 3 1-3 2m0-3-2 1',gold,.65),x+i*7,y)).join('');
  const scale=(x,y,w=12,h=11)=>P(`M${x-w/2} ${y}Q${x} ${y+2} ${x+w/2} ${y}L${x+w/2-1} ${y+h*.5}Q${x} ${y+h+3} ${x-w/2+1} ${y+h*.5}Z`,M,gold,.6);
  const shaft=(y1,y2)=>P(`M-3 ${y1}H3V${y2}L0 ${y2+7}-3 ${y2}Z`,shadow,gold,1)+L(`M-1 ${y1}V${y2}`,light,1)+Array.from({length:Math.ceil((y2-y1)/12)},(_,i)=>P(`M-4 ${y1+i*12}h8v3h-8Z`,G,light,.4)).join('');
  let a='';
  switch(card.design){
   case 'griffin':
    a=shaft(-55,97)+P('M0-116L9-93 4-69-4-69-9-93Z',M,light,1)+L('M0-108V-72',gold,1);
    a+=P('M4-76Q38-95 54-81Q35-73 32-54Q22-38 5-41L12-57Z',M,gold,1.2)+P('M13-69Q28-83 44-80Q27-65 25-54L12-48Z',G,light,.6);
    a+=P('M-4-76Q-25-84-42-69L-21-65-10-48-4-45Z',M,light,.8)+P('M-26-68L-36-64-30-73Z',G,light,.5)+C(-23,-73,1.8,'#c43f47',light,.4);
    for(let i=0;i<6;i++)a+=L(`M${13+i*4} ${-69-i*2}q-8 10-5 17`,shadow,.7);
    a+=jewel(0,-57,8,'#bd3946')+curl(-2,-29,.33)+curl(-2,23,.33)+P('M-7 94H7L0 109Z',G,light,.6);break;
   case 'atlas':
    a=P('M-68-32L-6-51 5-39 65-17 58 54 1 34-58 57Z',shadow,gold,2);
    for(let i=0;i<5;i++)a+=P(`M-63 ${-28+i*2}L-6 ${-45+i*2} 1 ${-34+i*2} 60 ${-13+i*2} 54 ${49+i*2} 0 ${29+i*2}-55 ${52+i*2}Z`,i%2?'#bbc6d1':'#e1ddc6',gold,.4);
    a+=P('M-61-26Q-26-43-3-27L0 30Q-26 17-54 43Z',V,light,.8)+P('M-3-27Q20-42 59-11L53 47Q27 22 0 30Z',M,light,.8);
    a+=L('M-3-26L0 30',gold,2)+P('M-2 28l6 49 10-11 6 3-9-41',F,gold,.6);
    for(let i=0;i<5;i++)a+=L(`M-48 ${-13+i*8}q20-10 33-4M13 ${-12+i*8}q15-5 32 10`,light,.7);
    a+=ring(1,-62,30,-18)+ring(1,-62,30,65)+jewel(1,-62,18)+chain(-52,43,6,-18)+chain(54,46,5,20)+jewel(-43,79,4)+jewel(46,76,4);
    a+=studs([[-59,-21],[-51,43],[54,-7],[49,43]])+curl(-45,0,.5);break;
   case 'censer':
    a=C(0,-99,9,'none',gold,2)+C(0,-99,6,'none',light,.6)+chain(-5,-91,18,-14)+chain(5,-91,18,14)+chain(0,-91,16);
    a+=P('M-37 17Q-31-9-12-15L0-31 12-15Q31-9 37 17Z',G,light,1)+P('M-34 23Q-32 61 0 73Q32 61 34 23Z',M,gold,1.4);
    for(let i=-2;i<=2;i++)a+=P(`M${i*11-3} 10q0-18 3-19q3 1 3 19Z`,shadow,light,.7)+L(`M${i*10} 30Q${i*9} 52 0 66`,gold,1.3);
    a+=P('M-40 17H40V24H-40Z',G,light,.8)+glyphs(-25,21,8)+C(0,72,5,G,light)+chain(0,77,4)+jewel(0,101,7,'#ffd991');
    a+=jewel(0,40,12,'#fff1cc')+mirror(curl(22,27,.55))+studs([[-32,21],[-21,21],[21,21],[32,21]]);break;
   case 'antler':
    a=P('M-4-109Q-36-87-29-64Q-4-40-10-8Q-22 29-9 58Q12 82-6 109L8 111Q32 77 14 53Q0 26 15-7Q28-39 7-65Q-12-83 9-106Z',F,gold,1.2);
    a+=L('M2-103Q-25-83-19-64Q5-39 1-8Q-13 25 2 57Q24 81 1 105',light,1.5)+L('M9-106L55 0 8 111',light,.9);
    a+=P('M-22-68L-46-84-49-107-42-94-31-88-32-103-22-91-16-71M14 48L35 64 42 85 33 76 28 78 20 63Z','#c4b68d',light,.8);
    a+=P('M-11-10L14-7 10 15-14 12Z',shadow,gold,1)+jewel(0,1,10,'#76bb95')+chain(-6,18,6,13)+feather(-13,70,25,170);
    for(let i=0;i<5;i++)a+=L(`M-11 ${-8+i*4}l24 4`,gold,.8);break;
   case 'lantern':
    a=P('M-12-68V-83Q0-104 12-83V-68', 'none',gold,3)+P('M-27-48L0-74 27-48 21-39H-21Z',G,light,1);
    a+=P('M-31-35L0-44 31-35 24 38 0 52-24 38Z',V,light,1)+P('M0-37L17-29 12 28 0 43-12 28-17-29Z',shadow,gold,.7);
    a+=P('M0 26Q-21 9-4-13Q-5 0 2-3Q15-22 10-29Q26 2 9 25Z',stone,light,.5)+P('M0 24Q-6 7 5 0Q1 17 0 24Z',light,'none');
    a+=mirror(P('M24-42L31-33 23 37 29 44 18 50 16 36 23-34Z',G,light,.7)+curl(25,-28,.36));
    a+=P('M-29 44L0 57 29 44 22 57 0 67-22 57Z',G,light,1)+chain(0,66,5)+jewel(0,98,8)+chain(-24,48,6,-15)+chain(24,48,6,15)+jewel(-16,85,4)+jewel(16,85,4);
    a+=glyphs(-17,-48,6);break;
   case 'chrono':
    a=shaft(-12,97)+P('M-69-67L-38-79 40-67 64-49 58-11 24-1-48-14-68-28Z',M,gold,1.4);
    a+=P('M-68-65L-43-58-45-15-68-29Z',shadow,light,.8)+P('M-38-76L39-65 58-49-43-58Z',G,light,.8)+P('M44-43L61-48 56-14 41-10Z',shadow,light,.8);
    a+=gear(-6,-36,25,16)+C(-6,-36,16,V,light,.8)+L('M-6-48V-36L5-29',light,1.4)+C(-6,-36,2,G,light,.5)+gear(26,-25,10)+gear(-35,-42,9);
    a+=studs([[-61,-51],[-60,-34],[47,-26],[35,-55]])+glyphs(-28,-65,8)+P('M-7 16H7V29H-7Z',G,light,.7)+curl(-2,38,.4);break;
   case 'sovereign':
    a=P('M-25-65L-59-47-62-9-44 4-32-24-31 53-46 75 0 83 46 75 31 53 32-24 44 4 62-9 59-47 25-65 12-49H-12Z',shadow,gold,1.2);
    a+=mirror(P('M25-62L50-51 60-32 53-14 38-17 30-34Z',M,light,.8)+P('M36-49L49-43 53-29 43-30Z',G,light,.6)+curl(37,-45,.4));
    for(let row=0;row<8;row++)for(let col=0;col<5;col++)a+=scale(-24+col*12,-39+row*12,11,11);
    a+=P('M-28 39H28V48H-28Z',G,light,.7)+P('M-8 37H8V51H-8Z',shadow,gold,1.6)+jewel(0,43,5,'#a53e48')+mirror(P('M9 50L28 52 39 72 13 76Z',M,gold,.8)+L('M14 57l13 4m-13 6 17 4',light,.6));break;
   case 'astral':
    a=P('M-19-72L-43-58-69 27-51 19-66 87-31 76-10 95 0 82 13 96 36 74 67 83 52 19 70 30 44-58 19-72 8-54H-8Z',F,gold,1.1);
    a+=mirror(P('M19-71L40-55 29 17 47 74 26 65 12 19 4-50Z',V,light,.8)+L('M25-54L17 9 36 68',gold,1.6)+curl(28,33,.7)+P('M25-66L41-83 39-56 20-47Z',M,gold,.8));
    for(let i=0;i<32;i++){const x=(i*37)%85-42,y=(i*31)%118-38;a+=C(x,y,i%5?.7:1.5,light,'none');if(i%5===0)a+=L(`M${x-3} ${y}h6m-3-3v6`,gold,.5);}
    a+=P('M-20-34Q0-44 20-34L13-19H-13Z',G,light,.8)+jewel(0,-31,8)+chain(-12,-18,4,-35)+chain(12,-18,4,35)+jewel(0,4,5);break;
   case 'seraph':
    a=P('M-19-64L-51-46-59 43-30 31-21 83 0 75 21 83 30 31 59 43 51-46 19-64Z','#d8ceaf',gold,1.3)+P('M-11-49H11L22 73 0 81-22 73Z',G,light,1);
    for(const side of [-1,1])for(let i=0;i<7;i++)a+=`<g transform="scale(${side} 1)">`+feather(30+i*3,-25+i*9,30,-25-i*5)+'</g>';
    a+=P('M-20-64Q0-47 20-64L11-43H-11Z',M,light,.9)+jewel(0,-35,7,'#e5c982')+mirror(curl(33,17,.55))+glyphs(-14,57,5);
    for(let i=0;i<7;i++)a+=C(0,-11+i*9,2.2,light,gold,.5);break;
   case 'drake':
    a=P('M-18-67L-54-53-71-9-52 2-44 45-55 78-23 68 0 88 23 68 55 78 44 45 52 2 71-9 54-53 18-67Z',F,gold,1.2);
    for(let row=0;row<9;row++)for(let col=0;col<5;col++)a+=scale(-28+col*14+(row%2?3:0),-45+row*12,14,14);
    a+=mirror(P('M25-63L50-59 66-38 57-19 39-24 29-40Z',M,light,1)+P('M43-56L48-76 54-55 66-58 58-43 70-36 55-32Z',G,light,.7)+L('M31-46L48-42 56-29',light,.7));
    a+=P('M-20-65L0-48 20-65 13-38 0-29-13-38Z',G,light,.8)+jewel(0,-43,8,'#74ba89')+mirror(chain(27,-41,6,12));break;
   case 'spine':
    a=P('M-19-64L-50-52-57-20-38-15-27 62 0 75 27 62 38-15 57-20 50-52 19-64 9-51H-9Z',shadow,gold,1.1);
    for(let i=0;i<7;i++)a+=mirror(P(`M4 ${-40+i*13}Q33 ${-58+i*14} 38 ${-36+i*14}Q22 ${-39+i*13} 4 ${-29+i*13}Z`,'#c4b9ac',light,.6)+L(`M10 ${-36+i*13}Q25 ${-46+i*14} 32 ${-37+i*14}`,shadow,.5));
    for(let i=0;i<9;i++)a+=P(`M-5 ${-58+i*14}l5-4 5 4 3 7-8 4-8-4Z`,M,light,.7);
    a+=mirror(P('M19-63L39-67 58-49 53-30 39-35 31-49Z','#cec1ab',gold,.8)+P('M39-59L45-79 48-57Z',M,light,.6))+jewel(0,-3,9,'#ac6d9c');break;
   case 'reactor':
    a=P('M-23-62L-48-52-56-17-36-11-27 58 0 75 27 58 36-11 56-17 48-52 23-62 12-46H-12Z',shadow,gold,1.2);
    a+=mirror(P('M23-59L42-50 47-26 33-18 23-34Z',M,light,1)+P('M12-40L26-37 31 19 19 34 10 25Z',G,light,1)+L('M24-37L29 17 17 29',shadow,2));
    a+=gear(0,-14,29,16)+C(0,-14,21,V,light,1)+ring(0,-14,17,60)+ring(0,-14,17,-60)+jewel(0,-14,12,'#9df4e8');
    for(let i=0;i<4;i++)a+=P(`M-24 ${25+i*10}Q0 ${36+i*10} 24 ${25+i*10}L21 ${32+i*10}Q0 ${43+i*10}-21 ${32+i*10}Z`,M,gold,.8);
    a+=mirror(P('M39-39H49V32L37 43 33 33 39 27Z',G,light,1)+L('M43-34V30L38 35',shadow,2)+gear(39,-44,10,9)+studs([[34,17],[40,33],[22,55]]));break;
   case 'lion':
    a=P('M-51-75L0-87 51-75 57 23Q38 66 0 89Q-38 66-57 23Z',G,light,1.5)+P('M-43-67L0-77 43-67 47 21Q31 58 0 77Q-31 58-47 21Z','#652c35',gold,1.3);
    a+=P('M0-58L13-47 31-46 32-28 41-14 31 2 30 22 12 27 0 41-12 27-30 22-31 2-41-14-32-28-31-46-13-47Z',G,light,1);
    a+=mirror(P('M0-39L17-40 26-28 19-12 23 0 9 12 0 10Z',M,gold,.8)+P('M8-27L21-26 15-20 8-21Z',shadow,light,.6)+L('M23-36l8-3M25-13l8 1M20 7l7 7',shadow,.8));
    a+=P('M-8-7H8L0 1Z',shadow,light,.6)+P('M-11 8Q0 2 11 8L6 21H-6Z',shadow,gold,.8)+L('M-7 12H7',light,1.2)+mirror(curl(24,35,.75));
    for(let i=0;i<9;i++)a+=studs([[-49+i*12,-69+Math.abs(4-i)*2]]);a+=jewel(0,61,7,'#d45b58');break;
   case 'iris':
    a=C(0,0,72,'none',gold,2)+C(0,0,68,'none',light,.7)+C(0,0,55,'none',gold,1.5);
    for(let i=0;i<16;i++)a+=T(P('M-3-58L0-69 3-58Z',M,light,.5)+L('M0-72V-78',gold,1.2),0,0,i*22.5);
    for(let i=0;i<6;i++)a+=T(P('M-4-52Q34-48 40-17L14 3Q15-29-4-52Z',V,light,.7),0,0,i*60);
    a+=C(0,0,19,shadow,gold,1.5)+jewel(0,0,16,'#98bde8')+ring(0,0,81,32)+ring(0,0,81,-32)+jewel(0,-78,6)+jewel(0,78,6);break;
   case 'triptych':
    a=P('M-28-67L0-90 28-67V70H-28Z',G,light,1.2)+P('M-23-62L0-81 23-62V64H-23Z',V,light,.8);
    a+=mirror(P('M29-59L62-45 65 69 30 62Z',M,gold,1.2)+P('M34-51L56-41 58 59 35 54Z','#d5cab0',light,.6)+P('M38 35V-19Q45-40 52-14V41Z',G,light,.7)+L('M44-20V32M39 43l14 3',shadow,.6)+C(46,-25,5,light,gold,.5)+studs([[31,-43],[31,0],[32,45]]));
    a+=C(0,-34,14,'none',light,1.5)+P('M-7-27L7-27 17 31 0 44-17 31Z',G,light,.8)+C(0,-33,6,light,gold,.5)+mirror(feather(8,11,35,33))+jewel(0,52,7,'#ffd999')+glyphs(-17,67,6)+chain(0,74,4)+jewel(0,100,5);break;
   case 'beetle':
    a=mirror(P('M10-57Q52-59 58-9Q65 41 12 75L5 12Z',M,gold,1.2)+L('M16-45Q42-38 44-7Q46 25 16 56',light,1)+L('M22-35Q34-17 30 2M26 12L18 39',gold,.7)+P('M48-22L72-31 64-13 53-7M54 15L72 20 60 30 47 33M37 51L49 69 34 65 26 64Z',G,light,.7));
    a+=P('M0-70L22-57 15-38H-15L-22-57Z',G,light,1)+P('M0-40L6-22 4 49 0 73-4 49-6-22Z',shadow,light,.7)+jewel(0,-53,11,'#83bba2')+mirror(L('M12-65Q31-85 18-92',gold,2));break;
   case 'mirror':
    a=E(0,0,50,73,M,gold,2)+E(0,0,41,63,V,light,.8)+P('M-29-37Q-7-60 15-47L-19 41-31 30Z','#d4def4','none') .replace('fill="#d4def4"','fill="#d4def4" opacity=".15"');
    a+=mirror(curl(43,-37,.85)+curl(40,22,.75)+P('M28-62L36-86 18-72 0-89 7-65Z',M,light,.8)+P('M32 54L29 77 15 71 0 93 5 66Z',G,light,.8));
    a+=P('M-17-9Q0-21 17-9Q0 9-17-9Z',shadow,gold,.7)+jewel(0,-9,6,'#b697d5')+jewel(0,-78,6)+jewel(0,80,6);
    a+=L('M10-52L0-27 15-10 9 15 24 38M0-27L-13-31M9 15L-7 30',light,.65)+chain(-42,37,7,-12)+chain(42,37,7,12);break;
   case 'folding':
    for(let i=0;i<6;i++)a+=T(P('M-4-10L-37-37-25-77 6-84 18-42 10-7Z',M,gold,1.2)+P('M-16-35L-20-67 1-72 9-40Z',shadow,light,.65)+L('M-13-40L-15-62-1-66 4-40',gold,1)+studs([[-22,-72],[4,-77],[-17,-30]]),0,0,i*60);
    a+=gear(0,0,24,12)+C(0,0,16,V,light,.8)+jewel(0,0,10,'#8de4d2');break;
   // Footwear has distinct construction: straps, fur, articulated plates or engines.
   case 'cavalry':case 'orbital':case 'pilgrim':case 'lynx':case 'spectral':case 'turbine':{
    for(const side of [-1,1]){
     let b='';
     if(card.design==='pilgrim'){
      b=P('M-16-45Q-1-53 11-42L8 30 27 53 22 66-20 67-27 56-18 32Z','#bcab8b',gold,1)+P('M-25 59L25 58 22 68H-22Z',M,light,.8);
      for(let i=0;i<5;i++)b+=P(`M-16 ${-39+i*16}L12 ${-27+i*16} 12 ${-20+i*16}-17 ${-32+i*16}Z`,G,light,.5)+P(`M12 ${-39+i*16}L-16 ${-27+i*16}-16 ${-20+i*16} 12 ${-32+i*16}Z`,F,light,.5);
      b+=chain(-1,-45,6)+jewel(0,-8,5)+studs([[-15,43],[-5,47],[5,51],[15,55]]);
     }else{
      b=P('M-21-74L14-77 20-57 11-25 15 21 12 39 32 61 26 75H-24L-29 62-19 37-21 3-27-36Z',card.design==='spectral'?F:shadow,gold,1.1);
      if(card.design==='lynx'){
       b+=P('M-22-76L-28-65-24-50-31-43-19-35-12-41-3-32 8-41 18-34 21-50 16-58 21-72Z','#beb9a0',light,.6)+L('M-20-66l4 16m5-20 4 24m5-23 2 21m7-20-3 16',shadow,.7);
       b+=P('M-18-30L12-29 11 33-17 37Z',F,gold,.8)+P('M-19 36L11 34 25 58 19 68-23 65Z',M,light,.8)+curl(-9,-22,.65);
       for(let j=0;j<3;j++)b+=P(`M${-12+j*12} 57l3 14 5-13Z`,G,light,.7);
      }else if(card.design==='spectral'){
       b+=P('M-16-66L11-68 6-30 11 31-14 38-15-19Z',F,light,.7)+P('M-24 56L12 46 31 61 25 74H-24Z',M,gold,.8);
       for(let j=0;j<9;j++)b+=L(`M-10 ${-55+j*10}l14 8m0-8-14 8`,light,.8)+studs([[-10,-55+j*10],[4,-55+j*10]]);
       b+=P('M-22-69L-28-91-12-74M11-73L19-89 18-63Z','#d0c4b4',gold,.7)+jewel(-3,-23,5);
      }else{
       b+=P('M-21-68L12-70 14-52 4-31-16-26-23-39Z',M,light,.9)+P('M-16-23L7-28 15-10 6 8-18 10-24-6Z',G,light,.9)+P('M-17 13L9 10 11 39-2 49-19 42Z',M,light,.8);
       for(let j=0;j<4;j++)b+=P(`M-20 ${43+j*7}L5 ${42+j*7} 19 ${49+j*7}-22 ${51+j*7}Z`,M,gold,.8);
       b+=curl(-12,-61,.5)+L('M-11 17L-5 38M1 15L-1 34',gold,1.1)+studs([[-17,18],[-16,29],[-16,39]]);
       if(card.design==='cavalry')b+=P('M-25 51L-38 48-40 53-27 58Z',G,light,.7)+gear(-39,52,6,8)+jewel(-4,-8,6,'#ae4758');
       if(card.design==='orbital')b+=ring(-4,-7,28,15)+ring(-4,-7,28,-50)+jewel(-4,-7,10)+ring(-1,73,31,-7)+P('M-8-72L-1-88 5-72Z',G,light,.6);
       if(card.design==='turbine')b+=P('M-26-36L-42-29-40 35-25 40-20 23Z',M,gold,1)+gear(-29,5,17,12)+C(-29,5,11,V,light,.6)+Array.from({length:6},(_,i)=>T(P('M0-3L-3-10 3-9 5-2Z',G,light,.4),-29,5,i*60)).join('')+L('M-37 35v13m7-11v16',stone,2);
      }
     }
     a+=T(b,side*32,side===-1?-5:6,side===-1?-9:8,.92);
    }break;
   }
  }
  return a||null;
 }
 return {make};
})();
