'use strict';

const canvas=document.querySelector('.fighter-canvas')||document.querySelector('canvas');
const poster=document.querySelector('img');
const stage=document.querySelector('main');
const prefersStill=matchMedia('(prefers-reduced-motion: reduce)').matches;
const looks={
  skeletonminion:[2.8,'#c6a37752','skeleton'], skeletonrogue:[2.8,'#8fa7de52','skeleton'],
  skeletonwarrior:[2.8,'#c796ef60','skeleton'], skeletonmage:[2.8,'#ad70ea60','skeleton'],
  skeletongolem:[2.8,'#e9aa535a','skeleton'], necromancer:[2.8,'#b578e75a','skeleton'],
  knight:[2.8,'#e0ad6e60','knight'], mage:[2.8,'#7eaff960','mage'],
  barbarian:[2.8,'#d38e5a62','barbarian'], ranger:[2.8,'#99bc5b58','ranger'], rogue:[2.8,'#a28ac858','rogue'],
};
Object.assign(looks, {
  'gobelin-berserker': [2.328, '#BD625455', null],
  'gobelin-eclaireur': [2.097, '#638FAD55', null],
  'gobelin-maraudeur': [2.211, '#79A54B55', null],
  'gobelin-occultiste': [2.28, '#9970AD55', null],
  'gobelin-rempart': [2.178, '#BA8C5555', null],
  'slime-ambre': [1.189, '#BA8C5555', null],
  'slime-azur': [1.098, '#638FAD55', null],
  'slime-carmin': [1.267, '#BD625455', null],
  'slime-maudit': [1.316, '#9970AD55', null],
  'slime-mousse': [1.325, '#79A54B55', null],
  'troll-brisefer': [3.851, '#82977A55', null],
});
let gltfRenderer=null,clipName='Idle',clipStarted=0;
const isBestiary=id=>id.includes('-');
let gl,renderer,chosen='',active='',paused=false,lastHit=null,hitAt=-5000,request=0;
let inspect=false, drag=null, heading=0, grounded=false;
const native=(kind,value='')=>window.ArsenalHost?.action(kind,String(value));
canvas.addEventListener('pointerdown',event=>{
  if(!inspect||drag)return;
  drag={id:event.pointerId,x:event.clientX};
  canvas.setPointerCapture(event.pointerId);
});
canvas.addEventListener('pointermove',event=>{
  if(drag?.id!==event.pointerId)return;
  heading+=(event.clientX-drag.x)*.014;
  drag.x=event.clientX;
  drawCreature(active,performance.now());
});
canvas.addEventListener('lostpointercapture',()=>{drag=null});
try {gl=canvas.getContext('webgl2',{alpha:true,antialias:true,premultipliedAlpha:true});}
catch {}
const fallback=()=>{
  renderer=null;
  canvas.hidden=true;
  poster.hidden=!chosen;
  if(chosen)poster.src='creature-'+chosen+'.webp';
};
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();fallback()});

function makeRenderer(gl){
  function shader(kind,source){
    const shader=gl.createShader(kind);
    gl.shaderSource(shader,source);gl.compileShader(shader);
    if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));
    return shader;
  }
  const vertex=`#version 300 es
  in vec3 point,normal,ink; in float bone; in vec2 uv;
  uniform vec3 pivot[9];
  uniform float time,yaw,vertical,aspect,unit,strike,kind,ground,across,attack;
  out vec3 n,shade,world; out vec2 atlasUV;
  vec3 rotateX(vec3 p,float t){return vec3(p.x,cos(t)*p.y-sin(t)*p.z,sin(t)*p.y+cos(t)*p.z);}
  vec3 rotateY(vec3 p,float t){return vec3(cos(t)*p.x+sin(t)*p.z,p.y,-sin(t)*p.x+cos(t)*p.z);}
  vec3 rotateZ(vec3 p,float t){return vec3(cos(t)*p.x-sin(t)*p.y,sin(t)*p.x+cos(t)*p.y,p.z);}
  void main(){
    int j=int(bone+.1);
    float a=0.,b=0.;
    if(j==1){b=.10*sin(time*1.58);a=.05*cos(time*1.81)-strike*.10;}
    if(j==2||j==3){a=sin(time*2.05+float(j))*.065-strike*.29-attack*(j==2?1.4:.65);}
    if(j==4||j==5)b=(j==4?1.:-1.)*(.21*sin(time*5.)+.07);
    if(j==6||j==7)b=(j==6?-1.:1.)*(.095*sin(time*1.9+float(j))+.08*strike);
    if(j==8)a=.14*sin(time*3.5)+.25*strike;
    vec3 p=rotateY(rotateX(point-pivot[j],a),b)+pivot[j];
    n=rotateZ(rotateY(rotateX(normal,a),b),yaw);
    p=rotateZ(p,yaw);
    p.z+=kind>.5?sin(time*2.9)*.10:0.;
    p.z-=vertical;
    p.y+=strike*.16;p.x+=strike*.10*sin(time*28.);
    world=p;shade=ink;atlasUV=uv;
    float lift=.22;
    float y=p.z*cos(lift)+p.y*sin(lift);
    float z=p.y*cos(lift)-p.z*sin(lift);
    gl_Position=vec4(p.x*unit/aspect+across,y*unit+ground,z*.27,1.);
  }`;
  const fragment=`#version 300 es
  precision highp float;
  in vec3 n,shade,world; in vec2 atlasUV; uniform float strike;
  uniform bool textured; uniform sampler2D atlas; out vec4 fragment;
  void main(){
    vec3 norm=normalize(n);
    float key=max(0.,dot(norm,normalize(vec3(-.65,-.75,1.0))));
    float fill=max(0.,dot(norm,normalize(vec3(.82,-.48,.25))));
    float rim=pow(max(0.,dot(norm,normalize(vec3(.48,.57,1.)))),2.);
    float floorLight=smoothstep(-1.4,1.0,world.z);
    vec3 pigment=textured?texture(atlas,atlasUV).rgb:shade;
    vec3 lit=pigment*(.43+.51*key+.24*fill+.16*floorLight);
    lit+=vec3(.28,.20,.42)*rim*.30;
    lit=mix(lit,vec3(.98,.51,.46),strike*.40);
    fragment=vec4(clamp(lit,0.,1.),1.);
  }`;
  const program=gl.createProgram();
  gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));
  gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));
  gl.linkProgram(program);
  if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);gl.enable(gl.DEPTH_TEST);
  const uniform=Object.fromEntries(['pivot[0]','time','yaw','vertical','aspect','unit','strike','kind','ground','across','attack','textured'].map(k=>[k,gl.getUniformLocation(program,k)]));
  const white=gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D,white);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));
  const meshes=new Map(),atlases=new Map();
  function loadAtlas(name){
    if(!name)return null;
    if(!atlases.has(name))atlases.set(name,new Promise((resolve,reject)=>{
      const image=new Image();
      image.onload=()=>{
        const texture=gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D,texture);
        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
        gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
        gl.generateMipmap(gl.TEXTURE_2D);
        resolve(texture);
      };
      image.onerror=reject;
      image.src='kaykit-'+name+'.webp';
    }));
    return atlases.get(name);
  }
  async function load(id){
    if(meshes.has(id))return meshes.get(id);
    const [response,atlas]=await Promise.all([fetch('creature-'+id+'.mesh'),loadAtlas(looks[id][2])]);
    if(!response.ok)throw new Error('Monstre introuvable');
    const buffer=await response.arrayBuffer();
    const stride=atlas?48:40;
    if(buffer.byteLength<=108||(buffer.byteLength-108)%(3*stride))throw new Error('Maillage invalide');
    const vao=gl.createVertexArray(),vbo=gl.createBuffer(), vertices=new Float32Array(buffer,108);
    gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,vbo);gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);
    const attributes=[['point',3,0],['normal',3,12],['ink',3,24],['bone',1,36]];
    if(atlas)attributes.push(['uv',2,40]);
    for(const [name,length,offset] of attributes){
      const index=gl.getAttribLocation(program,name);
      gl.enableVertexAttribArray(index);gl.vertexAttribPointer(index,length,gl.FLOAT,false,stride,offset);
    }
    let radius=0;
    if(atlas)for(let i=0;i<vertices.length;i+=stride/4)radius=Math.max(radius,Math.hypot(vertices[i],vertices[i+1]));
    let low=Infinity;
    for(let i=0;i<vertices.length;i+=stride/4)low=Math.min(low,vertices[i+2]);
    const feet=[];
    for(let i=0;i<vertices.length;i+=stride/4)if(vertices[i+2]<low+.12)feet.push([vertices[i],vertices[i+1],vertices[i+2]]);
    const mesh={feet,vao,pivot:new Float32Array(buffer,0,27),count:vertices.byteLength/stride,atlas,radius};
    meshes.set(id,mesh);
    return mesh;
  }
  function draw(id,now,still=false,pose=null){
    const mesh=meshes.get(id);
    if(!mesh)return;
    const scale=Math.min(devicePixelRatio,1.5);
    const width=Math.round(canvas.clientWidth*scale),height=Math.round(canvas.clientHeight*scale);
    if(width<2||height<2)return;
    if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height;gl.viewport(0,0,width,height);}
    const [tall]=looks[id], elapsed=(now-hitAt)/1000;
    const strike=pose?.strike??(still||elapsed<0||elapsed>.46?0:Math.sin(Math.PI*elapsed/.46));
    const time=still||prefersStill?0:now*.001;
    if(!pose||pose.clear){gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);}
    gl.useProgram(program);
    gl.uniform3fv(uniform['pivot[0]'],mesh.pivot);
    gl.uniform1f(uniform.time,time);
    const yaw=pose?.yaw??(-.24+heading+(inspect?0:Math.sin(time*.83)*.17));
    gl.uniform1f(uniform.yaw,yaw);
    gl.uniform1f(uniform.vertical,tall*.5);
    gl.uniform1f(uniform.aspect,width/height);
    const horizontal=mesh.radius?Math.min(.61,.91/mesh.radius):.61;
    const unit=pose?Math.min(1.55/tall,(width/height)*.43,(width/height)*.44/Math.max(.1,mesh.radius)):Math.min(1.64/tall,(width/height)*horizontal);
    gl.uniform1f(uniform.unit,unit);
    let floor=Infinity;
    const sinYaw=Math.sin(yaw),cosYaw=Math.cos(yaw);
    for(const [x,y,z] of mesh.feet)floor=Math.min(floor,(z-tall*.5)*Math.cos(.22)+(sinYaw*x+cosYaw*y)*Math.sin(.22));
    gl.uniform1f(uniform.ground,pose?-.68-floor*unit+(pose.lift??0):grounded?-.72-floor*unit:0);
    gl.uniform1f(uniform.across,pose?.x??0);
    gl.uniform1f(uniform.attack,pose?.attack??0);
    gl.uniform1f(uniform.strike,strike);
    gl.uniform1f(uniform.kind,id==='bat'?1:0);
    gl.uniform1i(uniform.textured,mesh.atlas?1:0);
    gl.bindTexture(gl.TEXTURE_2D,mesh.atlas??white);
    gl.bindVertexArray(mesh.vao);gl.drawArrays(gl.TRIANGLES,0,mesh.count);
  }
  return {load,draw};
}

try { if(gl){renderer=makeRenderer(gl);if(typeof BestiaryGLTF!=='undefined')gltfRenderer=BestiaryGLTF.create(gl,canvas);} else fallback(); }
catch { fallback(); }

function drawCreature(id,now,still=false){
  if(isBestiary(id)){
    const elapsed=(now-hitAt)/1000;
    const hit=!still&&elapsed>=0&&elapsed<gltfRenderer?.duration(id,'Hit');
    const requested=hit?'Hit':clipName;
    const duration=gltfRenderer?.duration(id,requested)||0;
    const clipTime=hit?elapsed:Math.max(0,(now-clipStarted)/1000);
    const once=['Attack','Hit','Death'].includes(requested);
    const clip=once&&requested!=='Death'&&clipTime>duration?'Idle':requested;
    gltfRenderer?.draw(id,now,{still:still||prefersStill,yaw:-.24+heading,grounded,
      clip,clipTime:clip==='Idle'?now*.001:clipTime,once:clip!=='Idle'&&once,
      strike:hit?Math.sin(Math.PI*elapsed/Math.max(.01,duration)):0});
  }else renderer?.draw(id,now,still);
}
let frame=0,painted=0;
function animate(now){
  frame=0;
  if(paused||!renderer||!active)return;
  if(now-painted>=27){drawCreature(active,now);painted=now;}
  if(!prefersStill||now-hitAt<520)frame=requestAnimationFrame(animate);
}
function resume(){
  if(!frame&&!paused&&renderer&&active)frame=requestAnimationFrame(animate);
}
async function select(id,token=null){
  if(!Object.hasOwn(looks,id))return false;
  const didHit=token!==null&&lastHit!==null&&lastHit!==token;
  lastHit=token;
  const dying=didHit&&active&&chosen!==id&&isBestiary(active)&&!prefersStill;
  if(didHit&&chosen===id){
    hitAt=performance.now();clipName='Attack';
    clipStarted=hitAt+(gltfRenderer?.duration(active,'Hit')||.5)*1000;resume();
  }
  if(chosen===id)return true;
  chosen=id;
  heading=0;clipName=dying?'Death':'Idle';clipStarted=performance.now();
  if(dying){hitAt=-5000;resume();}
  stage.style.setProperty('--glow',looks[id][1]);
  const index=++request;
  if(!renderer){fallback();return false;}
  try {
    if(isBestiary(id)){if(!gltfRenderer)throw Error('GLB renderer unavailable');await gltfRenderer.load(id);}else await renderer.load(id);
    if(dying)await new Promise(resolve=>setTimeout(resolve,Math.min(1400,(gltfRenderer.duration(active,'Death')||.6)*1000)));
    else if(didHit&&!prefersStill)await new Promise(resolve=>setTimeout(resolve,320));
    if(index!==request||!renderer)return false;
    active=id;clipName='Idle';clipStarted=performance.now();canvas.hidden=false;poster.hidden=true;
    drawCreature(id,performance.now(),true);
    performance.mark("creature-visible");
    native("rendered",id);
    resume();
    return true;
  }catch{if(index===request)fallback();return false;}
}
window.beast={
  setDungeon(value){grounded=value;stage.classList.toggle("dungeon",value);},
  select,
  play(clip){if(!['Idle','Walk','Run','Move','Attack','Hit','Death'].includes(clip))return false;clipName=clip;clipStarted=performance.now();resume();return true;},
  inspect(value){inspect=value;canvas.style.touchAction=value?'none':'';},
  pause(value){paused=value;if(value&&frame){cancelAnimationFrame(frame);frame=0;}else resume();},
  async snapshot(id){
    if(!await select(id))return null;
    drawCreature(id,performance.now(),true);
    return canvas.toDataURL('image/png');
  },
};

if(typeof initialCreature==='string'&&Object.hasOwn(looks,initialCreature)){
  beast.setDungeon(new URLSearchParams(location.search).get('ground')==='true');
  select(initialCreature);
}
if(!document.documentElement.classList.contains("duel"))native("ready");
