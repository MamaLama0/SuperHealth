'use strict';
/** Local glTF 2.0 renderer for the authored bestiary. GPU skinning, embedded textures,
 * LINEAR/STEP clips, opaque + two-pass translucent shells. No network dependencies.
 * Kept separate from the legacy avatar renderer so existing duel portraits stay valid.
 */
const BestiaryGLTF = (() => {
  const identity=()=>new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);
  function multiply(a,b){const o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;}
  function transform(t=[0,0,0],q=[0,0,0,1],s=[1,1,1]) {
    const [x,y,z,w]=q,xx=x*x,yy=y*y,zz=z*z,xy=x*y,xz=x*z,yz=y*z,wx=w*x,wy=w*y,wz=w*z;
    return new Float32Array([(1-2*(yy+zz))*s[0],2*(xy+wz)*s[0],2*(xz-wy)*s[0],0,
      2*(xy-wz)*s[1],(1-2*(xx+zz))*s[1],2*(yz+wx)*s[1],0,
      2*(xz+wy)*s[2],2*(yz-wx)*s[2],(1-2*(xx+yy))*s[2],0,...t,1]);
  }
  function slerp(a,b,t){
    let dot=a.reduce((s,v,i)=>s+v*b[i],0);const sign=dot<0?-1:1;dot=Math.abs(dot);
    let u=1-t,v=t;if(dot<.9995){const angle=Math.acos(Math.min(1,dot)),d=Math.sin(angle);u=Math.sin((1-t)*angle)/d;v=Math.sin(t*angle)/d;}
    const q=a.map((x,i)=>x*u+b[i]*v*sign),length=Math.hypot(...q);return q.map(x=>x/length);
  }
  function parse(buffer) {
    const view=new DataView(buffer);if(view.getUint32(0,true)!==0x46546c67||view.getUint32(4,true)!==2)throw Error('Invalid glTF');
    let doc,bin;for(let offset=12;offset<buffer.byteLength;){const length=view.getUint32(offset,true),kind=view.getUint32(offset+4,true);offset+=8;
      if(kind===0x4e4f534a)doc=JSON.parse(new TextDecoder().decode(new Uint8Array(buffer,offset,length)));
      if(kind===0x004e4942)bin=buffer.slice(offset,offset+length);offset+=length;}
    if(!doc||!bin)throw Error('Missing glTF chunks');
    const cache=new Map(),types={5120:[Int8Array,1],5121:[Uint8Array,1],5122:[Int16Array,2],5123:[Uint16Array,2],5125:[Uint32Array,4],5126:[Float32Array,4]};
    function accessor(index){
      if(cache.has(index))return cache.get(index);
      const a=doc.accessors[index],v=doc.bufferViews[a.bufferView], [Type,bytes]=types[a.componentType],count={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16}[a.type];
      if(a.sparse)throw Error('Sparse accessor not supported by the bestiary exporter');
      const start=(v.byteOffset||0)+(a.byteOffset||0),stride=v.byteStride||count*bytes;
      let result;
      if(stride===count*bytes)result=new Type(bin,start,a.count*count);
      else{result=new Type(a.count*count);for(let i=0;i<a.count;i++)result.set(new Type(bin,start+i*stride,count),i*count);}
      cache.set(index,result);return result;
    }
    const clips=new Map((doc.animations||[]).map(a=>[a.name,{channels:a.channels.map(c=>{
      const sample=a.samplers[c.sampler];if(!['LINEAR','STEP',undefined].includes(sample.interpolation))throw Error('Unsupported clip interpolation');
      return {node:c.target.node,path:c.target.path,times:accessor(sample.input),values:accessor(sample.output),step:sample.interpolation==='STEP'};
    }),duration:Math.max(...a.samplers.map(s=>accessor(s.input).at(-1)))}]));
    return {doc,bin,accessor,clips};
  }
  function pose(model,clip,time){
    const {doc}=model,states=doc.nodes.map(n=>({translation:n.translation||[0,0,0],rotation:n.rotation||[0,0,0,1],scale:n.scale||[1,1,1]}));
    if(clip)for(const c of clip.channels){
      const n=c.path==='rotation'?4:3;let k=0;while(k+1<c.times.length&&c.times[k+1]<=time)k++;
      const next=Math.min(k+1,c.times.length-1),a=Array.from(c.values.subarray(k*n,k*n+n)),b=Array.from(c.values.subarray(next*n,next*n+n));
      const f=c.step||next===k?0:Math.max(0,Math.min(1,(time-c.times[k])/(c.times[next]-c.times[k])));
      states[c.node][c.path]=c.path==='rotation'?slerp(a,b,f):a.map((v,i)=>v+(b[i]-v)*f);
    }
    const worlds=new Array(doc.nodes.length);
    function visit(index,parent){const n=doc.nodes[index],s=states[index];worlds[index]=multiply(parent,n.matrix||transform(s.translation,s.rotation,s.scale));for(const child of n.children||[])visit(child,worlds[index]);}
    for(const root of doc.scenes[doc.scene||0].nodes)visit(root,identity());
    return worlds;
  }
  function create(gl,canvas){
    function shader(kind,source){const s=gl.createShader(kind);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
    const program=gl.createProgram();
    gl.attachShader(program,shader(gl.VERTEX_SHADER,`#version 300 es
      in vec3 position,normal;in vec2 uv;in vec4 joints,weights;
      uniform mat4 bones[32];uniform mat4 model;uniform bool skinned;
      uniform float yaw,unit,aspect,vertical,ground,across;
      out vec3 N,P;out vec2 UV;
      void main(){mat4 skin=skinned?(bones[int(joints.x)]*weights.x+bones[int(joints.y)]*weights.y+bones[int(joints.z)]*weights.z+bones[int(joints.w)]*weights.w):model;
        vec3 p=(skin*vec4(position,1.)).xyz;vec3 n=mat3(skin)*normal;
        // glTF: +Y up, +Z facing viewer. Existing arena: +Z up, -Y front.
        p=vec3(p.x,-p.z,p.y);n=vec3(n.x,-n.z,n.y);
        mat3 turn=mat3(cos(yaw),sin(yaw),0.,-sin(yaw),cos(yaw),0.,0.,0.,1.);
        p=turn*p;n=turn*n;p.z-=vertical;N=n;P=p;UV=uv;
        float y=p.z*cos(.22)+p.y*sin(.22),z=p.y*cos(.22)-p.z*sin(.22);
        gl_Position=vec4(p.x*unit/aspect+across,y*unit+ground,z*.12,1.);
      }`));
    gl.attachShader(program,shader(gl.FRAGMENT_SHADER,`#version 300 es
      precision highp float;in vec3 N,P;in vec2 UV;out vec4 color;
      uniform vec4 pigment;uniform vec3 emission;uniform sampler2D atlas;uniform bool textured;
      uniform float roughness,metallic,strike,alphaCutoff;uniform bool jelly;
      void main(){vec4 ink=pigment;if(textured){vec4 texel=texture(atlas,UV);ink*=vec4(pow(texel.rgb,vec3(2.2)),texel.a);}if(ink.a<alphaCutoff)discard;
        vec3 n=normalize(gl_FrontFacing?N:-N),light=normalize(vec3(-.65,-.75,1.));
        float key=max(0.,dot(n,light)),fill=max(0.,dot(n,normalize(vec3(.8,-.5,.3))));
        vec3 eye=normalize(vec3(0.,-1.,.22));float rim=pow(1.-abs(dot(n,eye)),3.);
        float spec=pow(max(0.,dot(n,normalize(light+eye))),mix(85.,8.,roughness));
        vec3 lit=ink.rgb*(.37+.68*key+.24*fill)+mix(vec3(.13),ink.rgb,metallic)*spec+emission;
        if(jelly)lit+=vec3(.18,.24,.26)*rim+vec3(.6)*spec*.4;
        lit=mix(lit,vec3(1.,.34,.23),strike*.32);color=vec4(pow(clamp(lit,0.,1.),vec3(1./2.2)),ink.a);
      }`));
    gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
    const uniforms=Object.fromEntries(['bones[0]','model','skinned','yaw','unit','aspect','vertical','ground','across','pigment','emission','atlas','textured','roughness','metallic','strike','alphaCutoff','jelly'].map(n=>[n,gl.getUniformLocation(program,n)]));
    const models=new Map(),pending=new Map();
    async function load(id){
      if(models.has(id))return models.get(id);if(pending.has(id))return pending.get(id);
      const work=(async()=>{
        const response=await fetch('bestiary/'+id+'.glb');if(!response.ok)throw Error('Missing creature '+id);
        const model=parse(await response.arrayBuffer()),{doc,bin,accessor}=model;
        if(doc.skins?.some(s=>s.joints.length>32))throw Error('Bestiary bone budget exceeded');
        const textures=await Promise.all((doc.images||[]).map(async im=>{
          const view=doc.bufferViews[im.bufferView],blob=new Blob([new Uint8Array(bin,view.byteOffset||0,view.byteLength)],{type:im.mimeType});
          const image=await createImageBitmap(blob,{premultiplyAlpha:'none'}),texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);
          gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,false);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);image.close();
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
          gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.generateMipmap(gl.TEXTURE_2D);return texture;
        }));
        model.draws=[];model.min=[Infinity,Infinity,Infinity];model.max=[-Infinity,-Infinity,-Infinity];
        const bind=pose(model,model.clips.get('Idle'),0);
        const bindSkins=(doc.skins||[]).map(s=>{const inverse=accessor(s.inverseBindMatrices);return s.joints.map((joint,i)=>multiply(bind[joint],inverse.subarray(i*16,i*16+16)));});
        for(let node=0;node<doc.nodes.length;node++){
          const n=doc.nodes[node];if(n.mesh===undefined)continue;
          for(const primitive of doc.meshes[n.mesh].primitives){
            const vao=gl.createVertexArray();gl.bindVertexArray(vao);
            for(const [name,semantic,size] of [['position','POSITION',3],['normal','NORMAL',3],['uv','TEXCOORD_0',2],['joints','JOINTS_0',4],['weights','WEIGHTS_0',4]]){
              const index=gl.getAttribLocation(program,name),a=primitive.attributes[semantic];if(a===undefined){gl.disableVertexAttribArray(index);continue;}
              const attr=doc.accessors[a],vbo=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,vbo);gl.bufferData(gl.ARRAY_BUFFER,accessor(a),gl.STATIC_DRAW);
              gl.enableVertexAttribArray(index);gl.vertexAttribPointer(index,size,attr.componentType,!!attr.normalized,0,0);
            }
            const positions=accessor(primitive.attributes.POSITION),world=bind[node];
            const joints=n.skin!==undefined?accessor(primitive.attributes.JOINTS_0):null,weights=n.skin!==undefined?accessor(primitive.attributes.WEIGHTS_0):null;
            for(let i=0;i<positions.length;i+=3)for(let k=0;k<3;k++){
              let value=0;
              if(joints){const v=i/3*4;for(let j=0;j<4;j++){const mat=bindSkins[n.skin][joints[v+j]];value+=weights[v+j]*(mat[k]*positions[i]+mat[4+k]*positions[i+1]+mat[8+k]*positions[i+2]+mat[12+k]);}}
              else value=world[k]*positions[i]+world[4+k]*positions[i+1]+world[8+k]*positions[i+2]+world[12+k];
              model.min[k]=Math.min(model.min[k],value);model.max[k]=Math.max(model.max[k],value);
            }
            let indices=null,type=0;if(primitive.indices!==undefined){indices=accessor(primitive.indices);type=doc.accessors[primitive.indices].componentType;gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,gl.createBuffer());gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,indices,gl.STATIC_DRAW);}
            const mat=doc.materials?.[primitive.material]||{},pbr=mat.pbrMetallicRoughness||{};
            model.draws.push({vao,node,skin:n.skin,count:indices?.length||positions.length/3,type,mat,pbr,texture:pbr.baseColorTexture?textures[doc.textures[pbr.baseColorTexture.index].source]:null});
          }
        }
        model.draws.sort((a,b)=>(a.mat.alphaMode==='BLEND')-(b.mat.alphaMode==='BLEND')||((a.pbr.baseColorFactor?.[3]??1)-(b.pbr.baseColorFactor?.[3]??1)));
        models.set(id,model);return model;
      })();pending.set(id,work);try{return await work;}finally{pending.delete(id);}
    }
    function draw(id,now,options={}){
      const m=models.get(id);if(!m)return;
      const ratio=Math.min(devicePixelRatio,1.75),w=Math.round(canvas.clientWidth*ratio),h=Math.round(canvas.clientHeight*ratio);if(w<2||h<2)return;
      if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}gl.viewport(0,0,w,h);
      gl.useProgram(program);gl.enable(gl.DEPTH_TEST);gl.depthMask(true);gl.disable(gl.BLEND);
      if(options.clear!==false){gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);}
      let clip=m.clips.get(options.clip||'Idle')||m.clips.get('Idle'),time=options.still?0:options.clipTime??now*.001;
      time=options.once?Math.min(time,clip.duration):time%Math.max(.01,clip.duration);
      const worlds=pose(m,clip,time),skinMatrices=(m.doc.skins||[]).map(s=>{
        const inverse=m.accessor(s.inverseBindMatrices),result=new Float32Array(32*16);
        for(let i=0;i<s.joints.length;i++)result.set(multiply(worlds[s.joints[i]],inverse.subarray(i*16,i*16+16)),i*16);return result;
      });
      const tall=m.max[1]-m.min[1],radius=Math.max(Math.abs(m.min[0]),Math.abs(m.max[0]),Math.abs(m.min[2]),Math.abs(m.max[2]));
      const yaw=options.yaw??-.24,unit=Math.min(1.62/Math.max(2.35,tall),w/h*.82/Math.max(.9,radius)),vertical=m.min[1]+Math.max(2.35,tall)*.5;
      gl.uniform1f(uniforms.yaw,yaw);gl.uniform1f(uniforms.unit,unit);gl.uniform1f(uniforms.aspect,w/h);gl.uniform1f(uniforms.vertical,vertical);
      gl.uniform1f(uniforms.ground,options.grounded?-.72+(vertical-m.min[1])*Math.cos(.22)*unit:0);gl.uniform1f(uniforms.across,0);
      gl.uniform1f(uniforms.strike,options.strike||0);gl.uniform1i(uniforms.atlas,0);gl.activeTexture(gl.TEXTURE0);
      for(const d of m.draws){
        const {pbr,mat}=d,transparent=mat.alphaMode==='BLEND';
        gl.uniform1i(uniforms.skinned,d.skin!==undefined);if(d.skin!==undefined)gl.uniformMatrix4fv(uniforms['bones[0]'],false,skinMatrices[d.skin]);else gl.uniformMatrix4fv(uniforms.model,false,worlds[d.node]);
        gl.uniform4fv(uniforms.pigment,pbr.baseColorFactor||[1,1,1,1]);gl.uniform3fv(uniforms.emission,mat.emissiveFactor||[0,0,0]);
        gl.uniform1f(uniforms.roughness,pbr.roughnessFactor??1);gl.uniform1f(uniforms.metallic,pbr.metallicFactor??1);gl.uniform1f(uniforms.alphaCutoff,mat.alphaMode==='MASK'?mat.alphaCutoff??.5:0);
        gl.uniform1i(uniforms.jelly,transparent);gl.uniform1i(uniforms.textured,!!d.texture);gl.bindTexture(gl.TEXTURE_2D,d.texture);gl.bindVertexArray(d.vao);
        const paint=()=>d.type?gl.drawElements(gl.TRIANGLES,d.count,d.type,0):gl.drawArrays(gl.TRIANGLES,0,d.count);
        if(transparent){gl.enable(gl.BLEND);gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);gl.enable(gl.CULL_FACE);gl.cullFace(gl.FRONT);paint();gl.cullFace(gl.BACK);paint();gl.disable(gl.CULL_FACE);}
        else{gl.disable(gl.BLEND);gl.depthMask(true);gl.disable(gl.CULL_FACE);paint();}
      }
      gl.depthMask(true);gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);
    }
    return {load,draw,has:id=>models.has(id),duration:(id,clip)=>models.get(id)?.clips.get(clip)?.duration||0};
  }
  return {create,parse,pose};
})();
