'use strict';
// GLSL port of CardShaders.kt / HOLO: light follows the printed luminance,
// preserving dark materials instead of bleaching the illustration with additive foil.
const RelicShaders={glsl:`
float relicHash(vec2 p){p=mod(p,289.);vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
vec3 relicHue(float h){return clamp(abs(fract(h+vec3(0.,.333,.667))*6.-3.)-1.,0.,1.);}
vec3 relicHolo(vec3 base,vec2 uv,vec2 tilt,float time,float spark,float chroma){
 float lit=smoothstep(.05,.65,dot(base,vec3(.2126,.7152,.0722)));
 float still=1.-clamp(length(tilt)*3.,0.,1.);
 vec2 t=tilt+still*.25*vec2(sin(time*.37),cos(time*.29));
 float period=1.25,axis=uv.x*.62+uv.y*.48,center=fract(time*.045)*period+t.x*.45-t.y*.35;
 float d=axis-center;d-=period*floor(d/period+.5);
 float echo=abs(d)-period*.5,glow=exp(-d*d*9.)+.28*exp(-echo*echo*16.);
 vec3 tint=mix(vec3(.85,.93,1.),relicHue(fract(axis*1.2-center/period+.15)),chroma);
 vec2 sp=(uv-(.5+.45*t))*vec2(1.,1.46);float spot=exp(-dot(sp,sp)*6.);
 vec3 col=mix(base,base*(.55+.9*tint),clamp(glow*.45*lit,0.,1.));
 col+=tint*glow*(.012+.16*lit)+vec3(spot*(.008+.10*lit));
 vec2 fc=uv*vec2(600.,876.),grid=floor(fc/9.);float h=relicHash(grid),tw=sin(time*3.+h*60.+t.x*8.);
 float point=1.-smoothstep(0.,.5,length(fract(fc/9.)-.5));
 col+=vec3(spark*step(.985,h)*smoothstep(.6,1.,tw)*point*point*(.5+.8*lit));
 return clamp(col,0.,1.);
}`};
