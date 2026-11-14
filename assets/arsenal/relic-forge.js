'use strict';
/** Hand-built geometry for epic and legendary relics, in the illustrator's local space. */
const RelicForge=(()=>{
  function make(card,id,p){
    if(card.tier<3)return null;
    const [core,hi,dark,,gold]=p,metal=`url(#${id}metal)`,cloth=`url(#${id}cloth)`;
    const P=(d,f=metal,s=gold,w=.8)=>`<path d="${d}" fill="${f}" stroke="${s}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"/>`;
    const L=(d,c=gold,w=.7)=>P(d,'none',c,w);
    const C=(x,y,r,f=metal,s=gold,w=.8)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${f}" stroke="${s}" stroke-width="${w}"/>`;
    const gem=(x,y,r=5,color=core)=>`<g transform="translate(${x} ${y})">`+P(`M0-${r}L${r*.7} 0 0 ${r}-${r*.7} 0Z`,color,hi,.6)+P(`M0-${r}V${r}L-${r*.7} 0Z`,hi,'none')+P(`M0-${r}L${r*.7} 0H0Z`,'#fff9e2','none')+'</g>';
    const bead=(x,y,r=1.5)=>C(x,y,r,gold,dark,.4)+C(x-.4,y-.5,r*.3,hi,'none');
    const scroll=(x,y,scale=1,flip=1)=>`<g transform="translate(${x} ${y}) scale(${scale*flip} ${scale})">`+L('M0 36C16 20-12 13 0-1C12-15 26-2 17 4C9 8 7-4 15-5M1 17C20 9 25 23 15 25C9 26 7 18 14 17M1 29C-10 19-15 30-8 33')+'</g>';
    const band=(y,width=7)=>P(`M-${width} ${y}h${width*2}v5h-${width*2}Z`,gold,hi,.5)+L(`M-${width} ${y+2}h${width*2}`,dark,.6);
    const handle=(from=-30,to=85)=>{
      let a=P(`M-4 ${from}H4L5 ${to} 0 ${to+5}-5 ${to}Z`,dark,gold,1)+L(`M-2 ${from+4}V${to}`,core,1.5);
      for(let y=from+8;y<to;y+=8)a+=L(`M-4 ${y}l8-4`,gold,.8);
      return a+band(from)+band(to-3)+gem(0,to+3,4);
    };
    const wing=(x,y,s=1,flip=1)=>`<g transform="translate(${x} ${y}) scale(${s*flip} ${s})">`+
      Array.from({length:7},(_,i)=>P(`M0 12Q${18+i*5} ${-32+i*2} ${48-i*3} ${-32+i*7}Q21 30 0 20Z`,i%2?metal:gold,hi,.6)).join('')+L('M0 18Q25 5 44-27',dark,1.3)+'</g>';
    const orbit=(x,y,r,angle=0)=>`<g transform="translate(${x} ${y}) rotate(${angle})"><ellipse rx="${r}" ry="${r*.38}" fill="none" stroke="${gold}" stroke-width="1.5"/><ellipse rx="${r-2}" ry="${r*.38-2}" fill="none" stroke="${hi}" stroke-width=".4"/></g>`;
    const sun=(x,y,r)=>{let a=C(x,y,r,metal,gold,1.2)+C(x,y,r-4,dark,hi,.5);for(let i=0;i<12;i++)a+=`<g transform="translate(${x} ${y}) rotate(${i*30})">`+P(`M-2-${r+3}L0-${r+12} 2-${r+3}Z`,gold,hi,.4)+'</g>';return a+gem(x,y,r*.65);};
    const runes=(y,count=9)=>Array.from({length:count},(_,i)=>`<g transform="translate(${(i-(count-1)/2)*7} ${y})">`+L('M-1 3V-3l3 2-3 2m0-3-2 1',hi,.55)+'</g>').join('');
    const ribs=(y,count=5)=>Array.from({length:count},(_,i)=>P(`M-34 ${y+i*9}Q0 ${y+17+i*9} 34 ${y+i*9}L30 ${y+7+i*9}Q0 ${y+23+i*9}-30 ${y+7+i*9}Z`,i%2?metal:dark,gold,.65)).join('');
    const number=card.variant||0,theme=card.relicTheme;
    if(card.slot==='weapon'){
      const index=card.id.startsWith('class-')?-1:number;
      if(card.tier===4&&(index===0||card.special==='astrolabe'||card.id==='class-1-weapon-3')){
        let a=handle(-39,81)+P('M-12-35L-23-52-19-71-6-58 0-48 6-58 19-71 23-52 12-35Z',metal,gold,1);
        for(let i=0;i<7;i++)a+=orbit(0,-58,22+i*2,i*25.7);
        a+=sun(0,-58,11)+P('M-7-89L0-104 7-89 0-82Z',metal,hi,.7);
        for(let i=0;i<7;i++){const t=i*Math.PI*2/7;a+=gem(Math.cos(t)*31,-58+Math.sin(t)*31,3.4);}
        return a+scroll(-3,-17,.34)+scroll(3,24,.34,-1);
      }
      if(card.tier===4&&index===1){
        let a=P('M0-111L19-84 16-51 11-43 17-29 13 20H-13L-17-29-11-43-16-51-19-84Z',metal,gold,1.2);
        a+=P('M0-96L8-79 5 10H-5L-8-79Z','#101521',hi,.7)+L('M-13-73L-10-48-6-41-11-29-8 14M13-73L10-48 6-41 11-29 8 14',hi,.8);
        a+=wing(-7,23,.66,-1)+wing(7,23,.66)+P('M-12 17L0 10 12 17 8 37H-8Z',gold,hi,.8)+gem(0,24,9);
        a+=handle(37,83)+P('M0 83l9 8-9 11-9-11Z',metal,gold,1)+gem(0,91,5);
        for(let i=0;i<6;i++)a+=L(`M-2 ${-67+i*12}l4 3-4 3`,core,.7);return a;
      }
      if(card.tier===4&&index===2){
        let a=P('M-37-14Q-17-23 0-13Q17-23 37-14V45Q16 36 0 49Q-16 36-37 45Z',dark,gold,2);
        for(let i=0;i<5;i++)a+=L(`M-34 ${-11+i*2}Q-17 ${-21+i*2} 0 ${-10+i*2}Q17 ${-21+i*2} 34 ${-11+i*2}`,hi,.6);
        a+=P('M0-115L11-99 8 0 0 16-8 0-11-99Z',metal,hi,.9)+L('M0-104V-2',dark,1.2)+P('M-26-11L0-5 26-11 23 0 0 9-23 0Z',gold,hi,.7);
        a+=handle(15,79)+scroll(-29,4,.56)+scroll(29,4,.56,-1)+gem(-24,27,5)+gem(24,27,5);
        for(let i=0;i<7;i++)a+=L(`M-38 ${-3+i*7}q-11 4-4 9m80-9q11 4 4 9`,gold,1.2);return a;
      }
      if(card.tier===4&&index===3){
        let a=handle(30,83)+P('M0-111Q28-91 7-74Q-18-58 7-45Q27-29 5-16Q-12-4 9 11L7 27H-7L-13 11Q-32-5-10-20Q10-32-15-47Q-35-64-11-78Q11-92 0-111Z',metal,gold,1);
        a+=L('M1-96Q14-87-1-76T0-48T0-19T0 13',hi,1.4)+wing(-5,24,.56,-1)+wing(5,24,.56)+gem(0,29,8);
        for(let i=0;i<7;i++)a+=`<g transform="translate(0 ${-80+i*14}) rotate(${i%2?-32:32})">`+P('M-11 0l-10-7 5 13M11 0l10-7-5 13',dark,gold,.6)+'</g>';return a;
      }
      if(card.tier===4&&index===4){
        let a=handle(-39,76)+sun(0,-62,23)+C(0,-62,13,dark,gold,1.3)+C(0,-62,8,'none',hi,.8);
        a+=P('M2 45H29V55H20V63H33V77H2Z',metal,gold,1)+P('M10 49H24V53H15V68H26V73H10Z',dark,hi,.5)+scroll(-4,-25,.4)+wing(-18,-48,.38,-1)+wing(18,-48,.38);return a;
      }
      if(card.tier===4&&index===5){
        let a='';for(const flip of [-1,1])a+=`<g transform="scale(${flip} 1) rotate(16)">`+P('M-3 10L-13-93-4-111 9-67 7 14Z',metal,gold,.8)+L('M-4-96L2 3',hi,1)+P('M-2 12Q31 33 29 63Q26 87 8 75Q-7 59 5 46Q15 34-2 23Z',gold,hi,.8)+P('M12 47Q24 51 21 64Q17 74 10 66Q4 54 12 47Z',dark,hi,.7)+scroll(8,41,.38)+'</g>';return a+C(0,15,7,metal,hi)+gem(0,15,4);
      }
      if(card.id==='class-5-weapon-3'||card.special==='trident'){
        let a=handle(-34,85)+P('M-6-35L-28-52-34-93-21-80-16-57-6-55V-91L0-110 6-91V-55L16-57 21-80 34-93 28-52 6-35Z',metal,hi,1);
        a+=orbit(0,-49,24)+gem(0,-58,11)+L('M-23-78L-14-67-21-59 0-48 15-64 25-76',hi,1.5);return a+band(-10)+band(26);
      }
      if(card.id==='class-3-weapon-3'||card.special==='falcon')return handle(-10,75)+wing(-3,-18,1.15,-1)+wing(3,-18,1.15)+P('M0-54L15-41 9-31 14-8 0 19-14-8-9-31-15-41Z',metal,gold,1)+gem(0,-16,10)+gem(0,-42,4);
      // Epic blades are individually shaped; unusual instruments keep their authored outline.
      if(!card.special&&['spear','rapier','greatsword','knife'].includes(card.art)){
        let a=handle(12,86)+P(card.art==='spear'?'M0-113L24-74 12-43 0-25-12-43-24-74Z':'M0-111L16-89 12 7 0 21-12 7-16-89Z',metal,hi,1);
        a+=P('M0-97L6-77 3-31H-3L-6-77Z',dark,gold,.6)+wing(-6,12,.42,-1)+wing(6,12,.42)+gem(0,13,7);
        for(let y=-73;y<-25;y+=9)a+=L(`M-2 ${y}l4 3-4 2`,hi,.6);return a;
      }
      return null;
    }
    if(card.slot==='chest'){
      if(card.special&&['ribcage','shell','book-armor'].includes(card.special))return null;
      const robe=/robe|cape|mante|chasuble|surcot|cocon|couronne|habit/i.test(card.type+' '+card.name.join(' '))||['mage-robe','leaf-cape','tailcoat','stole','reliquary','mantle'].includes(card.special);
      let a='';
      if(robe){
        a=P('M-19-67L-46-53-58 36-70 71-36 65-19 83 0 70 19 83 36 65 70 71 58 36 46-53 19-67Z',cloth,gold,1.2);
        for(const s of [-1,1])a+=`<g transform="scale(${s} 1)">`+P('M16-63L42-49 50 64 28 73 21 20Z',metal,gold,.8)+L('M31-49L34 29 44 62M22-41L28 57M44-36L52 44',hi,.7)+scroll(31,-32,.65)+scroll(39,18,.7)+P('M19-63L7-45 17-16 29-51Z',dark,gold,.8)+'</g>';
        a+=P('M-19-67Q0-53 19-67L10-47H-10Z',metal,hi,.8)+gem(0,-40,8)+P('M-16 9H16L18 18H-18Z',gold,hi,.7);
        for(let i=0;i<6;i++)a+=gem((i%2?1:-1)*(27+Math.floor(i/2)*6),-19+Math.floor(i/2)*27,3);
        if(theme==='thorns')a+=wing(-28,-53,.42,-1)+wing(28,-53,.42);
        if(theme==='script'||theme==='orrery'){for(let i=0;i<14;i++){const x=(i*19)%35-17,y=-18+(i*29)%78;a+=C(x,y,1.2,hi,'none')+L(`M${x} ${y}l${i%2?8:-8} 11`,gold,.45);}}
        if(theme==='dawn')a+=sun(0,-8,12)+runes(52,5);
      }else{
        a=P('M-29-59L-57-46-63-14-48-5-37-23-30 56-15 72H15L30 56 37-23 48-5 63-14 57-46 29-59 12-47H-12Z',dark,gold,1.2);
        a+=P('M-27-55L-12-44H12L27-55 40-29 29 18 0 32-29 18-40-29Z',metal,hi,1);
        for(const s of [-1,1])a+=`<g transform="scale(${s} 1)">`+P('M29-58L51-54 65-40 60-21 46-16 34-28Z',metal,gold,1)+P('M34-48L50-47 58-37 53-28 44-25Z',dark,hi,.6)+L('M33-53L51-50 62-39',hi,1)+scroll(15,-31,.55)+P('M8-36L26-39 31-19 20-6 8-13Z',metal,gold,.6)+L('M10-32L23-34 27-20 19-12',hi,.5)+'</g>';
        a+=ribs(13,5)+gem(0,-19,12)+gem(-49,-36,5)+gem(49,-36,5)+runes(53,7);
        for(const s of [-1,1])for(let i=0;i<4;i++)a+=bead(s*(22+i*2),18+i*9,1.2);
        if(theme==='engine')a+=orbit(0,-19,19)+C(0,-19,14,'none',gold,2);
        if(theme==='orrery')a+=orbit(0,-19,22,28)+orbit(0,-19,22,-28);
      }
      if(card.tier===4)for(const s of [-1,1])a+=`<g transform="scale(${s} 1)">`+P('M42-48L51-74 57-65 54-48 66-58 70-49 60-35Z',metal,gold,.9)+gem(54,-58,4)+'</g>';
      return a;
    }
    if(card.slot==='guard'){
      if(card.special&&['fan','lantern','screen','shell','reliquary','window'].includes(card.special)&&card.tier===3)return null;
      if(theme==='eye'){
        let a=P('M-77 0Q-37-56 0-39Q37-56 77 0Q37 56 0 39Q-37 56-77 0Z',metal,gold,1.4)+P('M-62 0Q0-54 62 0Q0 54-62 0Z',dark,hi,1);
        a+=C(0,0,29,metal,gold,2)+C(0,0,23,core,hi,.8)+P('M0-22Q12 0 0 22Q-12 0 0-22Z','#0e1020',gold,.8)+C(-9,-11,5,hi,'none');
        for(let i=0;i<12;i++)a+=`<g transform="rotate(${i*30})">`+P('M-3-43L0-57 3-43Z',gold,hi,.5)+'</g>';return a+scroll(-54,-7,.55)+scroll(54,-7,.55,-1);
      }
      if(theme==='prism'){
        let a=P('M0-87L47-53 62 8 37 55 0 80-37 55-62 8-47-53Z',metal,gold,1.4);
        a+=P('M0-73L39-45 49 8 29 45 0 65-29 45-49 8-39-45Z',dark,hi,.7)+P('M0-73L-39-45 0-22 39-45Z',core,hi,.7)+P('M0-22L-49 8 0 65Z',metal,hi,.7)+P('M0-22L49 8 0 65Z',core,hi,.7)+L('M0-73V65M-39-45L49 8M39-45L-49 8',gold,1.3);
        for(const [x,y] of [[0,-80],[44,-49],[55,8],[33,50],[0,72],[-33,50],[-55,8],[-44,-49]])a+=gem(x,y,4);return a;
      }
      const round=['dawn','orrery','tide','engine'].includes(theme);
      const outline=round?'M0-74A66 74 0 1 1-.01-74Z':'M0-81L58-55 54 17 33 57 0 81-33 57-54 17-58-55Z';
      let a=P(outline,metal,gold,1.6)+`<g transform="scale(.88)">${P(outline,dark,hi,1)}</g><g transform="scale(.78)">${P(outline,metal,gold,1)}</g>`;
      for(let i=0;i<20;i++){const t=i*Math.PI*2/20;a+=bead(Math.cos(t)*59,Math.sin(t)*67,1.65);}
      if(theme==='dawn')a+=sun(0,0,30)+scroll(-30,-45,.8)+scroll(30,-45,.8,-1);
      else if(theme==='orrery'||theme==='engine'){for(let i=0;i<4;i++)a+=orbit(0,0,37+i*3,i*45);a+=C(0,0,21,dark,gold,1)+gem(0,0,16);for(let i=0;i<8;i++)a+=`<g transform="rotate(${i*45})">`+gem(0,-52,4)+'</g>';}
      else if(theme==='crown')a+=P('M-34-21L-19-9-13-38 0-18 13-38 19-9 34-21 25 15H-25Z',gold,hi,1)+P('M-27 18H27V25H-27Z',metal,gold,.8)+gem(0,0,11)+scroll(-18,25,.55)+scroll(18,25,.55,-1);
      else if(theme==='eclipse')a+=C(0,-4,37,metal,gold,1)+C(10,-14,31,dark,hi,.6)+gem(0,46,8)+scroll(-39,-28,.8)+scroll(39,-28,.8,-1);
      else if(theme==='tide'){for(let i=0;i<6;i++)a+=L(`M-39 ${-34+i*13}q13-18 26 0t26 0t26 0`,hi,1);a+=gem(0,0,19);}
      else {for(const side of [-1,1])a+=`<g transform="scale(${side} 1)">`+L('M0 43Q41 12 26-42M24-28l17-7-15 17M30-3l15 5-19 8M16 23l17 8-22 3',gold,2)+P('M26-35Q6-37 12-52Q31-49 26-35Z',core,hi,.7)+'</g>';a+=gem(0,-8,18);}
      return a+runes(54,5);
    }
    if(card.slot==='legs'){
      if(['snowshoe','stilts','skates','hoof'].includes(card.special)&&theme!=='wings')return null;
      let a='';for(const side of [-1,1]){
        let leg=P('M-44-69L-17-70-9-49-13-16-11 17-13 40-4 63-9 75H-53L-57 65-46 41-49 4-53-29Z',dark,gold,1);
        leg+=P('M-44-63L-23-65-15-48-21-24-43-19-48-33Z',metal,hi,.8)+P('M-43-18L-23-22-17-7-21 7-42 12-48-3Z',metal,gold,.8)+gem(-32,-4,8);
        leg+=P('M-42 13L-22 8-19 40-29 50-44 42Z',metal,hi,.8)+L('M-36 17L-31 41M-26 16L-27 36',gold,1);
        for(let i=0;i<4;i++)leg+=P(`M-44 ${43+i*7}L-24 ${43+i*7}-15 ${49+i*7}-50 ${51+i*7}Z`,metal,gold,.65);
        leg+=P('M-51 71H-9L-9 77H-53Z',gold,hi,.6)+scroll(-35,-58,.5)+scroll(-36,17,.45);
        for(let i=0;i<4;i++)leg+=bead(-43,18+i*6,1.1);
        if(theme==='wings')leg+=wing(-45,9,.7,-1);
        else if(theme==='engine')leg+=C(-33,-4,14,'none',gold,2)+orbit(-33,-4,18,90)+P('M-53 28L-61 33-57 53-48 47Z',metal,gold,.8);
        else if(theme==='thorns')leg+=P('M-46-47L-61-61-58-41-48-31M-45 20L-57 14-50 33Z',metal,gold,.8);
        else if(theme==='dawn')leg+=sun(-32,-46,8);
        else if(theme==='orrery')leg+=orbit(-32,-4,16,35)+orbit(-32,-4,16,-35);
        else if(theme==='eclipse')leg+=L('M-33-60l-4 13 8 8-7 12M-31 16l-5 12 8 12',hi,1.1);
        a+=`<g transform="translate(${side===1?0:8} ${side===1?-5:5}) scale(${side} 1)">${leg}</g>`;
      }return a;
    }
    return null;
  }
  return {make};
})();
