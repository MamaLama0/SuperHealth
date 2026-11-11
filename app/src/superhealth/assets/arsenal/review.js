'use strict';
(()=>{
 const $=id=>document.getElementById(id),key='superhealth.arsenal.review.v2',verdicts={keep:'Garder',rework:'Retoucher',remove:'Retirer'};
 const catalog=[...ArsenalCatalog.catalog].sort((a,b)=>b.tier-a.tier||Number(!!b.signature)-Number(!!a.signature)||a.id.localeCompare(b.id));
 let reviews={},filtered=[],selected=catalog[0]?.id,page=0,storageOK=true;
 const viewKey=key+'.view',filterIds=['tier','damage','slot','new','set','status','search'];
 function remember(){try{localStorage.setItem(viewKey,JSON.stringify({selected,filters:Object.fromEntries(filterIds.map(id=>[id,$(id).value]))}));}catch{}}
 const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function valid(rows){
  if(!Array.isArray(rows))throw Error('Liste d’avis absente.');
  const result={};for(const row of rows){if(!row||!ArsenalCatalog.byId[row.id])continue;
   if(row.score!=null&&(!Number.isInteger(row.score)||row.score<1||row.score>5))throw Error('Une note doit être comprise entre 1 et 5.');
   if(row.decision!=null&&!Object.hasOwn(verdicts,row.decision))throw Error('Un verdict est invalide.');
   if(row.comment!=null&&typeof row.comment!=='string')throw Error('Un commentaire est invalide.');
   result[row.id]={score:row.score??null,decision:row.decision??null,comment:(row.comment||'').slice(0,4000),updatedAt:typeof row.updatedAt==='string'?row.updatedAt:null};
  }return result;
 }
 try{const saved=JSON.parse(localStorage.getItem(key)||'{"reviews":[]}');reviews=valid(saved.reviews);}catch{storageOK=false;$('notice').textContent='Sauvegarde locale indisponible ou illisible. Pense à exporter tes avis.';}
 const data=()=>({schema:1,catalogVersion:'vector-v6-550',exportedAt:new Date().toISOString(),reviews:Object.entries(reviews).map(([id,r])=>({id,title:ArsenalCatalog.byId[id].name.join(' '),...r}))});
 function progress(){const values=Object.entries(reviews).filter(([id])=>catalog.some(c=>c.id===id)).map(([,r])=>r),done=values.filter(r=>r.decision).length;$('progress').textContent=`${done} / ${catalog.length} objets triés · ${values.filter(r=>r.decision==='keep').length} à garder · ${values.filter(r=>r.decision==='rework').length} à retoucher`;$('export').disabled=!Object.keys(reviews).length;}
 function save(change){reviews[selected]={score:null,decision:null,comment:'',...reviews[selected],...change,updatedAt:new Date().toISOString()};
  try{localStorage.setItem(key,JSON.stringify(data()));storageOK=true;}catch{storageOK=false;}
  $('save-state').textContent=storageOK?'Avis enregistré dans ce navigateur.':'Avis gardé pour cette session. Exporte-le avant de fermer.';progress();controls();grid();
 }
 function controls(){const r=reviews[selected]||{};document.querySelectorAll('[data-score]').forEach(b=>b.setAttribute('aria-pressed',String(Number(b.dataset.score)===r.score)));document.querySelectorAll('[data-decision]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.decision===r.decision)));$('reset-score').disabled=!r.score;}
 function art(){const card=ArsenalCatalog.byId[selected];if(!card)return;const finish=$('finish').value||'normal';$('art').innerHTML=`<div class="full-card ${ArsenalCatalog.classNames(card,finish)}">${ArsenalCatalog.cardFace(card,{finish})}${ArsenalCatalog.effectMarkup(card,finish)}</div>`;}
 function detail(){const c=ArsenalCatalog.byId[selected],empty=!filtered.length;$('workspace').classList.toggle('hidden',empty);$('empty-message').classList.toggle('hidden',!empty);if(empty)return;
  $('rarity').textContent=ArsenalCatalog.tiers[c.tier].name;$('rarity').style.color=ArsenalCatalog.tiers[c.tier].color;$('name').textContent=c.name.join(' ');$('metadata').textContent=c.type+' · '+c.material;$('set-name').textContent=c.set?'Ensemble « '+c.set.name+' » · 4 pièces':'';
  $('comment').value=reviews[selected]?.comment||'';$('save-state').textContent=reviews[selected]?'Avis enregistré.':'Pas encore d’avis.';
  const index=filtered.findIndex(c=>c.id===selected);$('position').textContent=(index+1)+' / '+filtered.length;$('previous').disabled=index<=0;$('next').disabled=index>=filtered.length-1;controls();art();
 }
 function grid(){$('grid').classList.toggle('set-view',!!$('set').value);const shown=filtered.slice(page*12,page*12+12);$('grid').innerHTML=shown.map(c=>{const r=reviews[c.id];return `<button class="tile${c.id===selected?' active':''}" data-id="${c.id}" aria-label="Sélectionner ${esc(c.name.join(' '))}" aria-pressed="${c.id===selected}">${ArsenalCatalog.cardFace(c,{finish:'normal'})}<span class="tile-state">${r?.score?'★ '+r.score+'/5 · ':''}${r?.decision?verdicts[r.decision]:'À trier'}</span></button>`;}).join('');$('count').textContent=filtered.length+' objets';$('page-label').textContent=filtered.length?`${page+1} / ${Math.ceil(filtered.length/12)}`:'0 / 0';$('page-back').disabled=page===0;$('page-next').disabled=(page+1)*12>=filtered.length;}
 function filter(){const tier=$('tier').value,status=$('status').value,q=$('search').value.toLocaleLowerCase('fr');filtered=catalog.filter(c=>(!tier||(tier==='high'?c.tier>=3:c.tier===Number(tier)))&&(!$('damage').value||c.damageTypes.some(t=>RelicCuration.types[t].label===$('damage').value))&&(!$('slot').value||c.slot===$('slot').value)&&(!$('new').value||($('new').value==='expansion'?c.introduced===6:c.signature))&&(!$('set').value||c.set?.id===$('set').value)&&(!status||(status==='pending'?!reviews[c.id]?.decision:reviews[c.id]?.decision===status))&&[...c.name,c.type,c.material,c.id].join(' ').toLocaleLowerCase('fr').includes(q));if(!filtered.some(c=>c.id===selected))selected=filtered[0]?.id;page=Math.max(0,Math.floor(filtered.findIndex(c=>c.id===selected)/12));detail();grid();remember();}
 function choose(id){selected=id;page=Math.floor(filtered.findIndex(c=>c.id===id)/12);detail();grid();remember();}
 for(let i=1;i<=5;i++){const b=document.createElement('button');b.textContent=i+' ★';b.dataset.score=i;b.setAttribute('aria-label',i+' sur 5');b.onclick=()=>save({score:i});document.querySelector('.score').append(b);}
 $('legend').innerHTML=Object.entries(RelicCuration.types).map(([t,v])=>'<span>'+RelicCuration.icon(t,0,0,22)+v.label+'</span>').join('');
 const sets=[...new Map(catalog.filter(c=>c.set).map(c=>[c.set.id,c.set])).values()].sort((a,b)=>a.name.localeCompare(b.name,'fr'));
 for(const set of sets)$('set').add(new Option(set.name,set.id));
 for(const [id,f] of Object.entries(ArsenalCatalog.finishes))$('finish').add(new Option(f.name,id));
 for(const el of document.querySelectorAll('.filters select,.filters input'))el.addEventListener('input',filter);
 $('set').onchange=()=>{for(const id of ['tier','damage','slot','new','status','search'])$(id).value='';filter();};
 $('finish').onchange=art;document.querySelectorAll('[data-decision]').forEach(b=>b.onclick=()=>save({decision:reviews[selected]?.decision===b.dataset.decision?null:b.dataset.decision}));
 $('reset-score').onclick=()=>save({score:null});$('comment').oninput=()=>save({comment:$('comment').value});
 $('previous').onclick=()=>choose(filtered[filtered.findIndex(c=>c.id===selected)-1].id);$('next').onclick=()=>choose(filtered[filtered.findIndex(c=>c.id===selected)+1].id);
 $('grid').onclick=e=>{const b=e.target.closest('[data-id]');if(b){choose(b.dataset.id);$('workspace').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'});}};
 $('page-back').onclick=()=>{page--;grid();};$('page-next').onclick=()=>{page++;grid();};
 $('export').onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify(data(),null,2)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='arsenal-avis.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);$('notice').textContent='Export prêt : transmets-moi le fichier arsenal-avis.json.';};
 $('import').onclick=()=>$('import-file').click();$('import-file').onchange=async()=>{const file=$('import-file').files[0];if(!file)return;try{if(file.size>2000000)throw Error('Fichier trop volumineux.');const doc=JSON.parse(await file.text());if(doc.schema!==1)throw Error('Format d’avis inconnu.');const incoming=valid(doc.reviews);reviews={...reviews,...incoming};try{localStorage.setItem(key,JSON.stringify(data()));storageOK=true;}catch{storageOK=false;}progress();filter();$('notice').textContent=Object.keys(incoming).length+' avis importés.'+(storageOK?'':' Exporte-les avant de fermer.');}catch(e){$('notice').textContent='Import refusé : '+e.message;}finally{$('import-file').value='';}};
 try{const view=JSON.parse(localStorage.getItem(viewKey)||'null');if(view){if(catalog.some(c=>c.id===view.selected))selected=view.selected;for(const id of filterIds)if(typeof view.filters?.[id]==='string')$(id).value=view.filters[id];}}catch{}
 progress();filter();
})();
