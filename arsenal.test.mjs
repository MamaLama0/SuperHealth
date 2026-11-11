import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createArsenal } from './arsenal.mjs';
import { randomUUID } from 'node:crypto';

const DAY = 86400000;
const day = Date.parse('2026-11-09') / DAY;
const first = 'a'.repeat(64), second = 'b'.repeat(64), third = 'c'.repeat(64);
const rank = (day, rank = 2) => ({ day, rank });
async function fixture(t) {
  const dir = await mkdtemp(join(tmpdir(),'superhealth-arsenal-'));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  let store = await createArsenal(dir);
  return {
    dir,
    run: async (id, op, today, fields = {}, friends = []) =>
      (await store(id,{ op:'arsenal-'+op, today, ...fields },today*DAY+1000,friends)).arsenal,
    restart: async ()=>{store=await createArsenal(dir);},
  };
}

test('one receipt per day, persistent rolls and repeatable rank claims', async t=>{
  const {run,restart}=await fixture(t);
  const opened=await run(first,'open',day,{slot:'LEGS',days:[rank(day)]});
  assert.equal(opened.gold,45);
  assert.ok([4,5].includes(opened.opening.pieces.length));
  assert.equal(new Set(opened.owned.map(p=>p.id)).size,opened.owned.length);
  await restart();
  const retry=await run(first,'open',day,{slot:'WEAPON',days:[rank(day,3)]});
  assert.deepEqual(retry.opening,opened.opening);
  assert.equal(retry.gold,75);
  await assert.rejects(run(first,'open',day+1,{slot:'GUARD',days:[rank(day+2)]}),{status:409});
});

test('auction escrow refunds displaced bids and transfers once after expiry',async t=>{
  const {run,restart}=await fixture(t);
  const a=await run(first,'open',day,{slot:'WEAPON',days:[rank(day)]});
  await run(second,'sync',day,{days:[rank(day)]});
  await run(third,'sync',day,{days:[rank(day)]});
  const key=a.owned[0].key;
  const listed=await run(first,'list',day,{key,price:10,mode:'auction'});
  const listing=listed.listings[0].key;
  assert.ok(!listed.owned.some(p=>p.key===key));
  assert.equal((await run(second,'bid',day,{key:listing,amount:10})).gold,35);
  await assert.rejects(run(first,'cancel',day,{key:listing}),{status:409});
  await run(third,'bid',day,{key:listing,amount:11});
  assert.equal((await run(second,'state',day)).gold,45);
  await restart();
  assert.ok((await run(third,'state',day+1)).owned.some(p=>p.key===key));
  assert.equal((await run(first,'state',day+1)).gold,56);
  assert.equal((await run(first,'state',day+1)).gold,56);
});

test('private offer exchanges both pieces and hides listing from other players',async t=>{
  const {run}=await fixture(t);
  const a=await run(first,'open',day,{slot:'WEAPON',days:[rank(day)]});
  const b=await run(second,'open',day,{slot:'LEGS',days:[rank(day)]});
  const aPiece=a.owned[0], bPiece=b.owned[0];
  const sent=await run(first,'list',day,{key:aPiece.key,price:1,mode:'offer',to:second,wanted:bPiece.id},[second]);
  const id=sent.listings[0].key;
  assert.equal((await run(third,'state',day)).listings.length,0);
  await assert.rejects(run(third,'buy',day,{key:id}),{status:403});
  await assert.rejects(run(second,'buy',day,{key:id}),{status:409});
  const accepted=await run(second,'buy',day,{key:id,give:bPiece.key});
  assert.ok(accepted.owned.some(p=>p.key===aPiece.key));
  assert.ok((await run(first,'state',day)).owned.some(p=>p.key===bPiece.key));
});

test('weekly boss needs both damage and the weekly target for half of members',async t=>{
  const {run}=await fixture(t);
  const a=await run(first,'raid-create',day);
  const joined=await run(second,'raid-join',day,{code:a.raid.code});
  assert.equal(joined.raid.max,230);
  for(let delta=0;delta<3;delta++){
    for(const player of [first,second]) await run(player,'raid-hit',day+delta,{days:[rank(day+delta,1)]});
  }
  const zero=await run(first,'state',day+2);
  assert.equal(zero.raid.hp,0);
  assert.equal(zero.raid.won,false);
  const cleared=await run(first,'raid-hit',day+2,{days:[rank(day),rank(day+1),rank(day+2)]});
  assert.equal(cleared.raid.qualifiers,1);
  assert.equal(cleared.raid.won,true);
  assert.equal(cleared.gold,255);
  assert.equal((await run(first,'state',day+6)).raid.code,a.raid.code);
  assert.equal((await run(first,'state',day+7)).raid,null);
});

test('solo misses consume health and a valid day recovers instead of attacking',async t=>{
  const {run}=await fixture(t);
  const hit=await run(first,'solo',day,{days:[rank(day,1)]});
  assert.equal(hit.dungeon.floor,2);
  assert.equal(hit.gold,40);
  assert.equal((await run(first,'solo',day,{days:[rank(day,1)]})).gold,40);
  const down=await run(first,'sync',day+5,{days:[]});
  assert.equal(down.dungeon.health,0);
  const recovered=await run(first,'solo',day+5,{days:[rank(day+5,1)]});
  assert.equal(recovered.dungeon.health,100);
  assert.equal(recovered.dungeon.floor,2);
});

test('v2 body packs cover exactly two rotating damage types with an idempotent receipt', async t=>{
  const {run}=await fixture(t);
  const definitions=JSON.parse(await (await import('node:fs/promises')).readFile(new URL('./arsenal-catalogue.json', import.meta.url)));
  const types={WARRIOR:"MELEE",RANGER:"RANGED",MAGE:"ARCANIST",OCCULTIST:"ARCANIST",ARTIFICER:"ARCANIST",PRIEST:"DIVINE"};
  const schools = new Map(definitions.map(c=>[c.id,c.damageTypes]));
  for(let delta=0;delta<15;delta++){
    const now=day+delta;
    const view=await run(first,'v2-open',now,{catalogueVersion:5,slot:'CHEST',days:[rank(now)]});
    assert.equal(view.rules,3);
    assert(view.opening.pieces.every(p=>!p.id.startsWith('atelier-')&&definitions.find(c=>c.id===p.id).available));
    assert.equal(view.opening.schools.length,2);
    assert.deepEqual(new Set(view.opening.pieces.flatMap(p=>schools.get(p.id))),new Set(view.opening.schools.map(s=>types[s])));
    assert(view.opening.pieces.every(p=>p.balanceVersion===2&&p.base>=definitions.find(c=>c.id===p.id).min&&p.base<=definitions.find(c=>c.id===p.id).max));
    assert.equal(view.opening.pieces.length,new Set(view.opening.pieces.map(p=>p.id)).size);
    assert.deepEqual((await run(first,'v2-open',now,{catalogueVersion:5,slot:'GUARD',days:[rank(now)]})).opening,view.opening);
  }
});

test('sport days protect the hero without granting a pack and the right affinity cracks an elite ward',async t=>{
  const {run}=await fixture(t);
  await run(first,'v2-sync',day,{days:[],sportDays:[]});
  const protectedHero=await run(first,'v2-sync',day+3,{days:[],sportDays:[day+1,day+2]});
  assert.equal(protectedHero.dungeon.health,75);
  await assert.rejects(run(first,'v2-open',day+3,{slot:'CHEST',days:[],sportDays:[day+3]}),{status:409});
  for (let i=0;i<3;i++) {
    await run(second,'v2-solo',day+i,{days:[rank(day+i,3)],schools:{PRIEST:1}});
  }
  const elite=await run(second,'v2-solo',day+3,{days:[rank(day+3,1)],schools:{MAGE:1}});
  assert.equal(elite.dungeon.floor,4);
  assert.equal(elite.dungeon.monster,41);
});

test('a single daily tracker allows an attack, and one ice cube protects exactly its chosen day', async t=>{
  const {run}=await fixture(t);
  const clear=async (today, school)=>run(first,'v2-solo',today,{days:[],attackDays:[today],schools:{[school]:1}});
  assert.equal((await run(first,'v2-solo',day,{days:[],attackDays:[]})).dungeon.floor,1);
  const one=await clear(day,'PRIEST');
  assert.equal(one.dungeon.floor,2);
  assert.equal(one.gold,15);
  assert.equal((await clear(day+1,'RANGER')).dungeon.floor,3);
  const win=await clear(day+2,'ARTIFICER');
  assert.equal(win.dungeon.floor,4);
  assert.equal(win.ice,1);
  const freeze=()=>run(first,'v3-freeze',day+3,{days:[],attackDays:[]});
  assert.equal((await freeze()).dungeon.frozenDay,day+3);
  assert.equal((await freeze()).ice,0);
  assert.equal((await run(first,'v2-sync',day+4,{days:[],attackDays:[]})).dungeon.health,100);
  assert.equal((await run(first,'v2-sync',day+5,{days:[],attackDays:[]})).dungeon.health,68);
});

test('invite acceptance, gifts, and bonus pack receipts respect membership and balances', async t=>{
  const {run}=await fixture(t);
  const created=await run(first,'raid-create',day);
  await assert.rejects(run(first,'v3-raid-invite',day,{to:second}),{status:409});
  await run(first,'v3-raid-invite',day,{to:second},[second]);
  assert.deepEqual((await run(second,'state',day)).raidInvites,[{code:created.raid.code,from:first}]);
  await assert.rejects(run(third,'v3-raid-accept',day,{code:created.raid.code}),{status:403});
  assert.equal((await run(second,'v3-raid-accept',day,{code:created.raid.code})).raid.members,2);
  for (let delta=0;delta<3;delta++) {
    for (const member of [first,second]) await run(member,'raid-hit',day+delta,{days:[rank(day+delta,3)]}).catch(error=>{
      if (error.status!==409) throw error;
    });
  }
  const winner=await run(first,'state',day+2);
  assert.ok(winner.raid.won);
  assert.equal(winner.packCredits.length,1);
  assert.equal(winner.ice,1);
  for (const [i,kind] of ['GOLD','ICE','PACK'].entries()) {
    const request='a0000000-0000-4000-8000-00000000000'+i;
    await assert.rejects(run(first,'v3-gift',day+2,{to:third,kind,request}),{status:403});
    const sent=await run(first,'v3-gift',day+2,{to:third,kind,request},[third]);
    assert.deepEqual(await run(first,'v3-gift',day+2,{to:third,kind,request},[third]).then(v=>[v.gold,v.ice,v.packCredits]),
      [sent.gold,sent.ice,sent.packCredits]);
  }
  const recipient=await run(third,'state',day+2);
  assert.equal(recipient.gold,25);
  assert.equal(recipient.ice,1);
  assert.equal(recipient.packCredits.length,1);
  const rewardKey=recipient.packCredits[0];
  const opened=await run(third,'v3-open',day+2,{rewardKey,slot:'LEGS',days:[]});
  assert.ok(opened.opening.pieces.length>=4);
  assert.equal(opened.opened,0);
  assert.equal(opened.packCredits.length,0);
  assert.deepEqual((await run(third,'v3-open',day+2,{rewardKey,slot:'WEAPON',days:[]})).opening,opened.opening);
});


test('dual affinities share one equipment weight and legacy payloads stay compatible',async t=>{
  const {run}=await fixture(t);
  const a=await run(first,'v2-solo',day,{days:[rank(day,1)],damageMix:{MELEE:.5,ARCANIST:.5},schools:{PRIEST:4}});
  // Floor 1 resists Arcanist: 48 × (1 + .55) / 2 = 37. Three HP remain.
  assert.equal(a.dungeon.floor,1);
  assert.equal(a.dungeon.monster,3);
  const legacy=await run(second,'v2-solo',day,{days:[rank(day,1)],schools:{WARRIOR:1,OCCULTIST:1}});
  assert.equal(legacy.dungeon.monster,a.dungeon.monster);
  const invalid=await run(third,'v2-solo',day,{days:[rank(day,1)],damageMix:{DIVINE:20}});
  assert.equal(invalid.dungeon.floor,2); // Invalid weights fall back to a normal melee hit.
});

test('each drawn base roll varies inside its rarity and survives a restart unchanged',async t=>{
  const {run,restart}=await fixture(t);
  const definitions=JSON.parse(await (await import('node:fs/promises')).readFile(new URL('./arsenal-catalogue.json',import.meta.url)));
  const byId=new Map(definitions.map(c=>[c.id,c])),rolls=new Map();
  for(let i=0;i<24;i++){
    const now=day+i,opened=await run(first,'v2-open',now,{slot:'WEAPON',days:[rank(now)]});
    for(const p of opened.opening.pieces){const c=byId.get(p.id);assert(p.base>=c.min&&p.base<=c.max);(rolls.get(c.tier)||rolls.set(c.tier,new Set()).get(c.tier)).add(p.base);}
    if(i===23){await restart();assert.deepEqual((await run(first,'v2-open',now,{slot:'WEAPON',days:[rank(now)]})).opening,opened.opening);}
  }
  assert(rolls.get(0).size>1); // Many independent rolls, never a catalogue's fixed preview value.
});

test('the 400 vector pieces return to draws while experimental owned items and rolls survive', async t=>{
  const {run,restart,dir}=await fixture(t);
  const old=await run(first,'v2-open',day,{slot:'WEAPON',days:[rank(day)]});
  assert(old.opening.pieces.every(p=>!p.id.startsWith('atelier-')));
  // Model an actual saved v4 inventory, independently of the new available flags.
  const file=join(dir,'arsenal-v1.json'),state=JSON.parse(await readFile(file,'utf8'));
  const experimental={...old.owned[0],key:'archived-v4-piece',id:'atelier-griffon-weapon',base:271};
  state.players[first].owned[experimental.key]=experimental;
  await writeFile(file,JSON.stringify(state));await restart();
  const fresh=await run(first,'v2-open',day+1,{catalogueVersion:5,slot:'WEAPON',days:[rank(day+1)]});
  assert(fresh.opening.pieces.every(p=>!p.id.startsWith('atelier-')));
  assert.deepEqual(fresh.owned.find(p=>p.key===experimental.key),experimental);
  for(const p of old.owned)assert.deepEqual(fresh.owned.find(n=>n.key===p.key),p);
  const downgraded=await run(first,'v2-open',day+1,{slot:'WEAPON',days:[rank(day+1)]});
  assert.deepEqual(downgraded.opening,fresh.opening);
  assert(downgraded.owned.every(p=>!p.id.startsWith('atelier-')));
  await restart();
  const restored=await run(first,'v2-open',day+1,{catalogueVersion:5,slot:'WEAPON',days:[rank(day+1)]});
  assert.deepEqual(restored.opening,fresh.opening);
  assert.deepEqual(restored.owned,fresh.owned);
});

test('550 catalogue is gated by version and every new owned piece survives downgrades with its saved roll',async t=>{
  const {run,restart,dir}=await fixture(t);
  const definitions=JSON.parse(await readFile(new URL('./arsenal-catalogue.json',import.meta.url)));
  const additions=definitions.filter(c=>c.introduced===6),byId=new Map(definitions.map(c=>[c.id,c]));
  assert.equal(definitions.filter(c=>c.available!==false).length,550);
  assert.equal(additions.length,150);
  const original=await run(first,'v2-open',day,{catalogueVersion:5,slot:'WEAPON',days:[rank(day)]});
  const file=join(dir,'arsenal-v1.json'),state=JSON.parse(await readFile(file,'utf8'));
  const owned=additions.map((c,i)=>({...original.owned[0],key:'expansion-fixture-'+i,id:c.id,base:c.min+i%(c.max-c.min+1)}));
  for(const piece of owned)state.players[first].owned[piece.key]=piece;
  await writeFile(file,JSON.stringify(state));await restart();
  const upgraded=await run(first,'v2-sync',day,{catalogueVersion:6,days:[]});
  for(const piece of [...original.owned,...owned])assert.deepEqual(upgraded.owned.find(p=>p.key===piece.key),piece);
  const old=await run(first,'v2-sync',day,{catalogueVersion:5,days:[]});
  assert.deepEqual(old.owned,original.owned);
  await restart();
  assert.deepEqual((await run(first,'v2-sync',day,{catalogueVersion:6,days:[]})).owned,upgraded.owned);
  for(let i=1;i<=12;i++){
    const slot=['WEAPON','CHEST','GUARD','LEGS'][(i-1)%4],now=day+i;
    for(const [id,version] of [[first,6],[second,5]]){
      const drawn=await run(id,'v2-open',now,{catalogueVersion:version,slot,days:[rank(now)]});
      for(const p of drawn.opening.pieces){
        const c=byId.get(p.id);assert.equal(c.slot,slot);assert(c.available!==false);
        assert((c.introduced??0)<=version);assert(p.base>=c.min&&p.base<=c.max);
      }
      assert.deepEqual((await run(id,'v2-open',now,{catalogueVersion:version,slot,days:[rank(now)]})).opening,drawn.opening);
    }
  }
});

test('two boosters partition the existing catalogue without migrating inventory', async t=>{
  const {run,restart}=await fixture(t);
  const definitions=JSON.parse(await readFile(new URL('./arsenal-catalogue.json',import.meta.url)));
  const slots=new Map(definitions.map(c=>[c.id,c.slot]));
  const found=new Set();
  for(let delta=0;delta<24;delta++) {
    const now=day+delta, armor=delta%2===0;
    const fields={catalogueVersion:6,booster:armor?'ARMOR':'WEAPON',slot:armor?'CHEST':'WEAPON',days:[rank(now)]};
    const view=await run(first,'v2-open',now,fields);
    assert.equal(view.boosters,2);
    for(const piece of view.opening.pieces) {
      assert.equal(slots.get(piece.id)==='WEAPON',!armor);
      found.add(slots.get(piece.id));
    }
    const retry=await run(first,'v2-open',now,{...fields,booster:'WEAPON',slot:'WEAPON'});
    assert.deepEqual(retry.opening,view.opening);
  }
  assert.deepEqual(found,new Set(['WEAPON','CHEST','GUARD','LEGS']));
  const before=await run(first,'state',day+23,{catalogueVersion:6,rules:2});
  await restart();
  assert.deepEqual((await run(first,'state',day+23,{catalogueVersion:6,rules:2})).owned,before.owned);
  await assert.rejects(run(first,'v2-open',day+24,{booster:'ARMOR',slot:'LEGS',days:[rank(day+24)]}),{status:400});
});

const character={strength:16,dexterity:12,constitution:18,days:20,model:1};
test('duels persist both snapshots once and retries never grant rewards', async t=>{
  const {run,restart}=await fixture(t);
  const fields={to:second,request:'10000000-0000-4000-8000-000000000001',character,stance:'FEINT'};
  const sent=await run(first,'v3-duel-invite',day,fields,[second]);
  const duel=sent.duels[0].id;
  assert.equal(sent.duels[0].mine.strength,16);
  const incoming=(await run(second,'state',day)).duels[0];
  assert.equal(incoming.theirs,null);
  assert.equal((await run(third,'state',day)).duels.length,0);
  await assert.rejects(run(third,'v3-duel-accept',day,{duel,character,stance:'GUARD'},[first]),{status:404});
  assert.equal((await run(first,'v3-duel-invite',day,fields,[second])).duels.length,1);
  await assert.rejects(run(first,'v3-duel-invite',day,{...fields,character:{...character,strength:17}},[second]),{status:409});
  await restart();
  const accepted=await run(second,'v3-duel-accept',day,{duel,character,stance:'GUARD'},[first]);
  assert.equal(accepted.duels[0].status,'complete'); assert.equal(accepted.duels[0].theirs.strength,16);
  const retry=await run(second,'v3-duel-accept',day,{duel,character,stance:'ASSAULT'},[first]);
  assert.deepEqual(retry.duels,accepted.duels); assert.equal(retry.gold,0);
  assert.equal((await run(first,'state',day+8)).duels.length,0);
});

test('duels validate snapshots, ownership, cancellation and pending expiry',async t=>{
  const {run}=await fixture(t);
  const fields={to:second,request:'10000000-0000-4000-8000-000000000002',character,stance:'GUARD'};
  await assert.rejects(run(first,'v3-duel-invite',day,{...fields,character:{...character,strength:999}},[second]),{status:400});
  await assert.rejects(run(first,'v3-duel-invite',day,{...fields,loadout:['10000000-0000-4000-8000-000000000000']},[second]),{status:409});
  const sent=await run(first,'v3-duel-invite',day,{...fields,character:{...character,forceBonus:99999}},[second]);
  assert.equal(sent.duels[0].mine.forceBonus,0);
  await assert.rejects(run(first,'v3-duel-accept',day,{duel:sent.duels[0].id,character,stance:'GUARD'},[second]),{status:403});
  await run(first,'v3-duel-cancel',day,{duel:sent.duels[0].id});
  await assert.rejects(run(second,'v3-duel-accept',day,{duel:sent.duels[0].id,character,stance:'GUARD'},[first]),{status:409});
  assert.equal((await run(second,'state',day+3)).duels.length,0);
});

test('tactical monster combat records damage, counters, recovery and exactly one daily action',async t=>{
  const {run,dir,restart}=await fixture(t);
  await run(first,'state',day);
  const file=join(dir,'arsenal-v1.json'), data=JSON.parse(await readFile(file));
  data.players[first].dungeon={floor:4,health:100,monster:120,settled:day-1};
  await writeFile(file,JSON.stringify(data)); await restart();
  const fields={days:[rank(day,1)],combatVersion:1,stance:'GUARD',character,damageMix:{MELEE:1}};
  const result=await run(first,'v2-solo',day,fields);
  assert.equal(result.dungeon.dealt,26);
  assert.equal(result.dungeon.received,7);
  assert.equal(result.dungeon.health,93);
  assert.equal(result.dungeon.monster,94);
  const retry=await run(first,'v2-solo',day,{...fields,stance:'ASSAULT'});
  assert.deepEqual(retry.dungeon,result.dungeon);
});


test('a frozen monster cannot counter a tactical attack', async t=>{
  const {run,dir,restart}=await fixture(t);
  await run(first,'state',day);
  const file=join(dir,'arsenal-v1.json'), data=JSON.parse(await readFile(file));
  data.players[first].dungeon={floor:4,health:100,monster:120,settled:day-1,frozenDay:day};
  await writeFile(file,JSON.stringify(data)); await restart();
  const result=await run(first,'v2-solo',day,{days:[rank(day,1)],combatVersion:1,stance:'ASSAULT',character,damageMix:{MELEE:1}});
  assert.ok(result.dungeon.dealt>0);
  assert.equal(result.dungeon.received,0);
  assert.equal(result.dungeon.health,100);
});

test('4.0 wipes collections and refunds escrow once, then grants fifteen launch packs once', async t => {
  const {dir,run,restart}=await fixture(t);
  const opened=await run(first,'v2-open',day,{slot:'WEAPON',days:[rank(day)]});
  await run(second,'sync',day,{days:[rank(day)]});
  const sale=await run(first,'list',day,{key:opened.owned[0].key,mode:'auction',price:10});
  await run(second,'bid',day,{key:sale.listings[0].key,amount:10});
  const file=join(dir,'arsenal-v1.json');
  const old=JSON.parse(await readFile(file,'utf8'));
  delete old.collectionVersion;
  await writeFile(file,JSON.stringify(old));
  await restart();

  const version={collectionVersion:1,catalogueVersion:6,rules:2};
  const buyer=await run(second,'state',day+1,version);
  assert.equal(buyer.gold,45);
  assert.equal(buyer.packCredits.length,15);
  const seller=await run(first,'state',day+1,version);
  assert.deepEqual(seller.owned,[]);
  assert.deepEqual(seller.listings,[]);
  assert.equal(seller.opened,0);
  const draw=await run(first,'v3-open',day+1,{...version,rewardKey:seller.packCredits[0],slot:'WEAPON',days:[]});
  assert.equal(draw.packCredits.length,14);
  await restart();
  const after=await run(first,'state',day+1,version);
  assert.deepEqual(after.owned,draw.owned);
  assert.equal(after.packCredits.length,14);
});

test('4.0 pays seal and weekly quests once and sells packs for gold with an idempotent receipt', async t => {
  const {run}=await fixture(t);
  const version={collectionVersion:1,catalogueVersion:6,rules:2};
  const start=day-((day+3)%7);
  const grand=await run(first,'v2-sync',start,{...version,days:[rank(start,2)]});
  assert.equal(grand.packCredits.length,16);
  const hero=await run(first,'v2-sync',start,{...version,days:[rank(start,3)]});
  assert.equal(hero.packCredits.length,18);
  const weekly=await run(first,'v2-sync',start+1,{...version,days:[rank(start+1,1)]});
  assert.equal(weekly.packCredits.length,19);
  const purchase={...version,request:randomUUID()};
  const paid=await run(first,'purchase-pack',start+1,purchase);
  assert.equal(paid.gold,weekly.gold-75);
  assert.equal(paid.packCredits.length,20);
  const retry=await run(first,'purchase-pack',start+1,purchase);
  assert.equal(retry.gold,paid.gold);
  assert.deepEqual(retry.packCredits,paid.packCredits);
});

test('fixed price sales move gold and cards once, and declining a private offer releases the card', async t => {
  const {run}=await fixture(t);
  const version={collectionVersion:1,catalogueVersion:6,rules:2};
  const seller=await run(first,'v2-open',day,{...version,slot:'WEAPON',days:[rank(day,1)]});
  await run(second,'v2-sync',day,{...version,days:[rank(day,1)]});
  const [sold,offered]=seller.owned;
  const listed=await run(first,'list',day,{...version,key:sold.key,mode:'fixed',price:15});
  const key=listed.listings[0].key;
  await assert.rejects(run(second,'bid',day,{...version,key,amount:15}),{status:409});
  const bought=await run(second,'buy',day,{...version,key});
  assert.equal(bought.gold,10);
  assert.ok(bought.owned.some(piece=>piece.key===sold.key));
  await assert.rejects(run(second,'buy',day,{...version,key}),{status:404});
  const sent=await run(first,'list',day,{...version,key:offered.key,mode:'offer',to:second,price:5},[second]);
  assert.equal(sent.gold,40);
  await run(second,'decline',day,{...version,key:sent.listings[0].key});
  const after=await run(first,'state',day,version);
  assert.ok(after.owned.some(piece=>piece.key===offered.key));
});
