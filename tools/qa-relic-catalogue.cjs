'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'../app/src/superhealth/assets/arsenal'),ctx=vm.createContext({});
for(const file of ['cartes','armes-etudes','equipment-expansion','classes','relic-expansion','relic-sculpt','relic-forge','relic-art','relic-curation','fantasy-art','atelier-data','atelier-images','atelier-art','refinement-data','refined-art','catalogue'])vm.runInContext(fs.readFileSync(path.join(root,file+'.js'),'utf8'),ctx);
const cards=vm.runInContext('ArsenalCatalog.catalog',ctx),loot=JSON.parse(fs.readFileSync(path.join(root,'loot.json')));
assert.equal(cards.length,550);assert.equal(new Set(cards.map(c=>c.id)).size,550);
const all=vm.runInContext('ArsenalCatalog.allCatalog',ctx);assert.equal(all.length,634);assert.equal(all.filter(c=>c.available===false).length,84);
assert.deepEqual(JSON.parse(fs.readFileSync(path.join(__dirname,'../../SuperHealth-server/arsenal-catalogue.json'))),loot);
assert.equal(cards.filter(c=>c.tier>=3).length,182);
assert.equal(cards.filter(c=>c.signature).length,30);
assert.equal(cards.filter(c=>c.introduced===6).length,150);
assert.equal(new Set(cards.map(c=>c.name.join(' '))).size,550,'Unique authored names');
for(const c of cards.filter(c=>c.introduced===6))assert.equal(c.id.startsWith('exp-'),true);
const ranges=[[25,45],[50,80],[90,130],[150,210],[240,330]];
const types=vm.runInContext('Object.values(equipmentSchools).map(s=>s.element)',ctx);
assert.deepEqual(new Set(types),new Set(['Mêlée','À distance','Arcaniste','Divine']));
for(const card of cards){
  assert(card.roll>=ranges[card.tier][0]&&card.roll<=ranges[card.tier][1],card.id+' roll in new range');
  assert(loot.some(c=>c.id===card.id&&c.min===ranges[card.tier][0]&&c.max===ranges[card.tier][1]));
  assert(loot.some(c=>c.id===card.id&&c.title===card.name.join(' ')&&c.school===card.school&&JSON.stringify(c.damageTypes)===JSON.stringify(card.damageTypes)));
  assert(card.damageTypes.length>=1&&card.damageTypes.length<=2);
  assert.equal(new Set(card.damageTypes).size,card.damageTypes.length);
  if(card.damageTypes.length===2)assert(card.tier>=3,card.id+' dual affinity is curated');
  assert(!/échass|patins?|raquette|poêle|parapluie|parasol|banc |tonneau|salopette|chaussons? lapins|claquette|palmes|roulettes?|ciseaux|cisaille/i.test(card.name.join(' ')+' '+card.type),card.id+' novelty prop remains');
  for(const finish of ['normal','shiny','animated','chroma','blessed','bug']){
    ctx.testCard=card;ctx.testFinish=finish;
    assert.equal(card.edition,5);assert(card.available);
    assert(card.refinement&&card.refinement.form);
    const art=vm.runInContext('ArsenalCatalog.revealArt(testCard,{finish:testFinish})',ctx);
    assert(!/undefined|NaN|Infinity/.test(art.face.replace(/data:image\/webp;base64,[A-Za-z0-9+/=]+/g,'')),card.id+' invalid SVG');
    assert.equal(art.face.includes('class="rarity-aura"'),card.tier>=3,card.id+' intrinsic rarity aura');
    assert.equal(art.face.includes('class="rarity-sparks"'),card.tier>=3,card.id+' intrinsic rarity sparks');
    assert(art.face.includes('viewBox="0 0 300 438"'));
    for(const type of card.damageTypes)assert(art.face.includes('data-damage="'+type+'"'),card.id+' icon missing');
    assert(!/<text[^>]*>(?:MÊLÉE|À DISTANCE|ARCANISTE|DIVINE)<\/text>/.test(art.face));
    assert.equal(art.face.includes('>Malédiction</text>'),!!card.curse);
    assert.equal(art.face.includes('>Bénédiction</text>'),finish==='blessed');
    if(finish==='animated'){assert(art.objectFace.includes('card-object'));assert(!art.baseFace.includes('<g class="card-object"'));assert.equal(art.baseFace.includes('class="rarity-aura"'),card.tier>=3);assert(!art.objectFace.includes('class="rarity-aura"')); }
  }
}
const sets=new Map();for(const c of cards)if(c.set){if(!sets.has(c.set.id))sets.set(c.set.id,[]);sets.get(c.set.id).push(c);}
assert.equal(sets.size,66);
for(const [id,pieces] of sets){assert.deepEqual(pieces.map(c=>c.slot).sort(),['chest','guard','legs','weapon'],id+' full set');assert(pieces[0].damageTypes.some(t=>pieces.every(c=>c.damageTypes.includes(t))),id+' shared affinity');}
for(const type of ['MELEE','RANGED','ARCANIST','DIVINE'])for(let tier=0;tier<5;tier++)for(const slot of ['weapon','chest','guard','legs'])assert(cards.some(c=>c.tier===tier&&c.slot===slot&&c.damageTypes.includes(type)),[type,tier,slot]+' missing equipment');
assert(cards.some(c=>c.tier===4&&c.damageTypes.length===1));
const icons=vm.runInContext('RelicCuration.types',ctx),native=fs.readFileSync(path.join(__dirname,'../app/src/superhealth/java/app/superhealth/ui/cards/DamageIcons.kt'),'utf8');
for(const spec of Object.values(icons))assert(native.includes(spec.path),'Native/card icon mismatch');
if(process.argv[2]){
  const before=JSON.parse(fs.readFileSync(process.argv[2]));
  const mechanics=c=>Object.fromEntries(['id','tier','slot','penalty','penaltyPoints'].filter(k=>k in c).map(k=>[k,c[k]]));
  for(const old of before)assert.deepEqual(mechanics(loot.find(c=>c.id===old.id)),mechanics(old),'Existing IDs, rarities and penalties are preserved');
}
console.log('550 refined pieces, 84 archived IDs, 66 complete sets, client/server parity, 3300 finishes and transparent animated layers: OK');
