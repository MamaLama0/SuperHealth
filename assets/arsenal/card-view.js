'use strict';
const stage=document.querySelector('.card-tilt');
window.showCard=(id,finish,boost)=>{
  const card=ArsenalCatalog.byId[id];
  if(!card||!Object.hasOwn(ArsenalCatalog.finishes,finish))return;
  const surface=document.createElement('div');
  surface.className='full-card '+ArsenalCatalog.classNames(card,finish);
  surface.innerHTML=ArsenalCatalog.cardFace(card,{finish,boost})+ArsenalCatalog.effectMarkup(card,finish);
  stage.replaceChildren(surface);
};
stage.addEventListener('pointermove',event=>{
  const box=stage.getBoundingClientRect(),surface=stage.firstElementChild;
  if(!surface||!box.width||!box.height)return;
  const x=Math.max(0,Math.min(1,(event.clientX-box.left)/box.width));
  const y=Math.max(0,Math.min(1,(event.clientY-box.top)/box.height));
  surface.classList.add('pointer-foil');
  surface.style.setProperty('--light',100*x+'%');
  surface.style.setProperty('--rx',((.5-y)*11)+'deg');
  surface.style.setProperty('--ry',((x-.5)*11)+'deg');
});
stage.addEventListener('pointerleave',()=>{
  const surface=stage.firstElementChild;
  if(!surface)return;
  surface.classList.remove('pointer-foil');
  surface.style.removeProperty('--rx');surface.style.removeProperty('--ry');
});
