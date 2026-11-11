import { stances, beats, validCharacter, battle, modifier } from './combat.mjs';
import { randomBytes, randomInt, randomUUID } from 'node:crypto';
import { readFile, writeFile, rename, unlink } from 'node:fs/promises';
import { join } from 'node:path';

const cards = JSON.parse(await readFile(new URL('./arsenal-catalogue.json', import.meta.url)));
const catalog = new Map(cards.map(card => [card.id, card]));
const families = new Set(cards.map(card => card.slot));
const DAY = 86_400_000;
const HEX = /^[a-f0-9]{64}$/;
const ID = /^[a-f0-9-]{36}$/;
const GOLD = 25;
const PACK_PRICE = 75;
const COLLECTION_VERSION = 1;
const WEEKLY_GOALS = [2, 4, 6];
const rarity = [.515, .30, .145, .035, .005];
const finishes = [['SHINY',108,.008],['ANIMATED',114,.0022],['CHROMA',125,.0011],['BLESSED',134,.0006],['BUG',100,.0001]];
const fail = status => { const error = new Error('arsenal'); error.status = status; throw error; };
const fraction = () => randomInt(0, 1_000_000) / 1_000_000;
const monday = day => day - ((day + 3) % 7 + 7) % 7;
const monthKey = now => new Date(now).toISOString().slice(0,7);
const cleanText = v => typeof v === 'string' && ID.test(v);
const int = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;
const account = (state,id) => state.players[id] ??= { gold: 0, opened: 0, owned: {}, days: {}, claims: {}, last: null };

const schools = ['WARRIOR','MAGE','PRIEST','RANGER','OCCULTIST','ARTIFICER'];
const slots = ['WEAPON','CHEST','GUARD','LEGS'];
const representatives = ['WARRIOR','RANGER','MAGE','PRIEST'];
const pairs = representatives.flatMap((school,index)=>representatives.slice(index+1).map(other=>[school,other]));
const damageTypes = {WARRIOR:'MELEE', RANGER:'RANGED', MAGE:'ARCANIST', ARTIFICER:'ARCANIST', OCCULTIST:'ARCANIST', PRIEST:'DIVINE'};
const floorAffinities = [['DIVINE','ARCANIST'],['RANGED','MELEE'],['ARCANIST','RANGED'],['MELEE','RANGED'],['ARCANIST','MELEE']];
const packSchools = (day, slot) => pairs[((day + slots.indexOf(slot)) % pairs.length + pairs.length) % pairs.length];
const legacyCard = id => !id.startsWith('exp-') && !id.startsWith('class-') && !id.startsWith('relic-') && !id.startsWith('atelier-');

// New clients submit equal shares per equipped item; a dual item contributes .5 + .5.
// Legacy school payloads remain valid and are normalized to the four combat types.
function combatMix(body) {
  if (Object.hasOwn(body, 'damageMix')) {
    const source = body.damageMix;
    if (!source || typeof source !== 'object' || Array.isArray(source)) return {};
    const entries = Object.entries(source);
    if (entries.some(([type,weight])=>!['MELEE','RANGED','ARCANIST','DIVINE'].includes(type) ||
      !Number.isFinite(weight) || weight<.5 || weight>4 || !Number.isInteger(weight*2)) ||
      entries.reduce((sum,[,weight])=>sum+weight,0)>4) return {};
    return source;
  }
  const entries=schools.map(s=>[s,body.schools?.[s]]).filter(([,count])=>int(count,1,4));
  if(entries.reduce((total,[,count])=>total+count,0)>4)return {};
  return entries.reduce((mix,[school,count])=>{const type=damageTypes[school];mix[type]=(mix[type]||0)+count;return mix;},{});
}
function damage(base, submitted, [weak,resist], ward=false) {
  let mix=Object.entries(submitted||{});
  if(!mix.length)mix=[['MELEE',1]];
  const scale=mix.reduce((total,[s,count])=>total+count*(s===weak?1.65:s===resist?.55:1),0) /
    mix.reduce((total,[,count])=>total+count,0);
  return Math.max(12,Math.round(base*scale*(ward&&!mix.some(([s])=>s===weak)?.72:1)));
}

function loot(slot, gold, affinities, currentCatalog = false, catalogueVersion = 0, booster = null) {
  const pool = cards.filter(card => (booster === "ARMOR" ? card.slot !== "WEAPON" : card.slot === slot) && (card.introduced??0)<=catalogueVersion && (affinities ? (currentCatalog ? card.available!==false : !card.id.startsWith('atelier-')) && (card.damageTypes||[damageTypes[card.school]]).every(type=>affinities.some(s=>damageTypes[s]===type)) : legacyCard(card.id)));
  return Array.from({length:gold || randomInt(4) === 0 ? 5 : 4}, (_,index) => {
    const target = affinities?.[index];
    const candidates = pool.filter(card => !target || (card.damageTypes||[damageTypes[card.school]]).includes(damageTypes[target]));
    const weights = rarity.map((weight, i) => candidates.some(card=>card.tier===i) ? weight * (gold && i >= 2 ? 3 : 1) : 0);
    let roll = fraction() * weights.reduce((a,b) => a+b, 0);
    const tier = weights.findIndex(weight => (roll -= weight) < 0);
    const eligible = candidates.filter(card => card.tier === tier);
    const card = eligible[randomInt(eligible.length)];
    pool.splice(pool.indexOf(card),1);
    let foil = fraction();
    let [finish,multiplier] = finishes.find(([name,,rate]) => (foil -= rate * (gold && name !== 'BUG' ? 3 : 1)) < 0) ?? ['NORMAL',100];
    if (finish === 'BUG') {
      const band = fraction();
      multiplier = band < .96 ? randomInt(45,91) : band < .9975 ? randomInt(90,146) : randomInt(230,341);
    }
    return { key:randomUUID(), id:card.id, finish, multiplier, base:randomInt(card.min, card.max+1), level:1, balanceVersion:2 };
  }).sort((a,b) => catalog.get(a.id).tier-catalog.get(b.id).tier);
}

function completeSet(player, loadout) {
  if (!Array.isArray(loadout) || loadout.length !== slots.length) return false;
  const equipped = loadout.map(key => cleanText(key) ? catalog.get(player.owned[key]?.id) : null);
  const set = equipped[0]?.setId;
  return Boolean(set && equipped.every(card => card?.setId === set) &&
    new Set(equipped.map(card => card.slot)).size === slots.length);
}

function characterSnapshot(player, body) {
  if (!validCharacter(body.character) || !Array.isArray(body.loadout??[]) || (body.loadout??[]).length>4) fail(400);
  const {strength,dexterity,constitution,days,model,forceScore,dexScore,conScore}=body.character;
  const result={strength,dexterity,constitution,days,model,forceBonus:0,dexBonus:0,conBonus:0};
  if (int(forceScore,0,100) && 8+Math.floor(forceScore*12/100)===strength) result.forceScore=forceScore;
  if (int(dexScore,0,100) && 8+Math.floor(dexScore*12/100)===dexterity) result.dexScore=dexScore;
  if (int(conScore,0,100) && 8+Math.floor(conScore*12/100)===constitution) result.conScore=conScore;
  const seen=new Set();
  for (const key of body.loadout??[]) {
    const piece=cleanText(key)&&player.owned[key],card=piece&&catalog.get(piece.id);
    if (!card || seen.has(card.slot)) fail(409);
    seen.add(card.slot);
    let base=piece.base;
    if ((piece.balanceVersion??1)<2) {
      const [low,high]=[[20,40],[32,62],[50,90],[78,140],[105,185]][card.tier];
      base=Math.round(card.min+(Math.max(low,Math.min(high,base))-low)/(high-low)*(card.max-card.min));
    }
    const rolled=Math.max(card.min,Math.min(card.max,base));
    const bonus=Math.min(piece.finish==='BUG'?1000:550,Math.round(rolled*(piece.multiplier/100)*(card.penalty?1.4:1)*(1+.1*(piece.level-1))));
    const field={FOR:'forceBonus',DEX:'dexBonus',CON:'conBonus'};
    result[field[card.attribute]??(card.slot==='WEAPON'?'forceBonus':card.slot==='LEGS'?'dexBonus':'conBonus')]+=bonus;
    if (card.penalty) result[field[card.penaltyAttribute]??({BRA:'forceBonus',JAM:'dexBonus',CAR:'dexBonus'}[card.penalty]??'conBonus')]-=card.penaltyPoints;

  }
  if (completeSet(player, body.loadout)) {
    result.forceBonus += 50;
    result.dexBonus += 50;
    result.conBonus += 50;
  }
  return result;
}

function settleMarket(state, now) {
  for (const [key, listing] of Object.entries(state.listings)) {
    if (listing.end > now) continue;
    const seller = account(state,listing.seller);
    if (listing.bidder) {
      seller.gold += listing.bid;
      account(state,listing.bidder).owned[listing.piece.key] = listing.piece;
    } else seller.owned[listing.piece.key] = listing.piece;
    delete state.listings[key];
  }
}

function claimDays(player, submitted, day) {
  if (!Array.isArray(submitted) || submitted.length > 35) fail(400);
  for (const item of submitted) {
    if (!int(item?.day, day-34, day) || !int(item.rank,1,3)) continue;
    const prior = player.days[item.day] ?? 0;
    if (item.rank <= prior) continue;
    player.days[item.day] = item.rank;
    const prizes = [0,GOLD,45,75];
    player.gold += prizes[item.rank]-prizes[prior];
  }
  for (const key of Object.keys(player.days)) if (+key < day-42) delete player.days[key];
}

function protectDays(player, submitted, today) {
  player.sport ??= {};
  if (!Array.isArray(submitted) || submitted.length > 35) return;
  for (const day of submitted) if (int(day,today-34,today)) player.sport[day] = true;
  for (const day of Object.keys(player.sport)) if (+day<today-42) delete player.sport[day];
}

function recordAttackDays(player, submitted, today) {
  if (!Array.isArray(submitted) || submitted.length > 35) return;
  player.attackDays ??= {};
  for (const day of submitted) if (int(day,today-34,today)) player.attackDays[day] = true;
  for (const key of Object.keys(player.attackDays)) if (+key<today-42) delete player.attackDays[key];
}

function grantPack(player) {
  (player.packCredits ??= []).push(randomUUID());
}

function rewardQuests(player, today) {
  player.weeklyPacks ??= {};
  const week = monday(today);
  const count = Object.keys(player.days).filter(day => +day >= week && +day <= today).length;
  const milestones = WEEKLY_GOALS.filter(goal => count >= goal).length;
  const paid = player.weeklyPacks[week] ?? 0;
  for (let i = paid; i < milestones; i++) grantPack(player);
  if (milestones > paid) player.weeklyPacks[week] = milestones;
  for (const start of Object.keys(player.weeklyPacks)) if (+start < week - 42) delete player.weeklyPacks[start];
}

function settleDungeon(player,today) {
  const dungeon = player.dungeon ??= {floor:1,health:100,monster:0,settled:today-1};
  const elite = dungeon.floor%4===0;
  if (today-1>dungeon.settled) {
    for (let day=Math.max(today-34,dungeon.settled+1);day<today;day++) {
      if (player.days[day] || player.sport?.[day] || player.attackDays?.[day] || dungeon.frozenDay===day) continue;
      const hp = Math.max(0,dungeon.health-(elite ? 32 : 25));
      if (hp!==dungeon.health) dungeon.log='Le monstre t’a attaqué pendant ton absence.';
      dungeon.health=hp;
    }
    dungeon.settled=today-1;
  }
  return dungeon;
}

function solo(player,today,attack,power=0,mix=null,stance=null,character=null,setDamage=0){
  const dungeon=settleDungeon(player,today);
  if (!attack || !(player.days[today] || player.attackDays?.[today]) || (dungeon.lastAttack??-1)>=today) return;
  const elite=dungeon.floor%4===0;
  const maximum=elite ? 120 : dungeon.floor%7===0 ? 76 : 36+dungeon.floor%3*4;
  dungeon.lastAttack=today;
  if (!dungeon.health) {
    dungeon.health=100; dungeon.dealt=0; dungeon.received=0; dungeon.defeatedFloor=null; dungeon.log='Tu as récupéré tes points de vie.'; return;
  }
  const attributePower=character?modifier(character.strength)*2+modifier(character.dexterity):0;
  const base=48+attributePower+Math.max(0,(player.days[today]??0)-1)*9+power;
  const matchup=elite?['ARCANIST','MELEE']:floorAffinities[(dungeon.floor-1)%5];
  const intent=stances[(dungeon.floor-1)%3];
  const scale=stance===null?100:beats(stance,intent)?120:beats(intent,stance)?85:100;
  const hit=Math.floor((mix===null?base:damage(base,mix,matchup,elite))*scale/100) + setDamage;
  dungeon.dealt=Math.min(dungeon.monster||maximum,hit);
  dungeon.received=0; dungeon.defeatedFloor=null;
  dungeon.monster=Math.max(0,(dungeon.monster||maximum)-hit);
  if (!dungeon.monster) {
    const gold=elite?35:15;
    const ice=dungeon.floor%3===0;
    if (elite) grantPack(player);
    if (ice) player.ice=(player.ice??0)+1;
    player.gold+=gold; dungeon.defeatedFloor=dungeon.floor; dungeon.floor++;
    dungeon.log='Monstre vaincu · +'+gold+' or'+(elite?' · +1 paquet':'')+(ice?' · +1 glaçon':'');
  } else {
    dungeon.received=character===null||dungeon.frozenDay===today?0:Math.floor(Math.max(1,(elite?18:12)-(character?modifier(character.constitution):0))*(stance==='GUARD'?.5:1));
    dungeon.health=Math.max(0,dungeon.health-dungeon.received);
    dungeon.log='Attaque réussie · −'+hit+' PV';
  }
}

function raidView(raid, id, today) {
  if (!raid) return null;
  const week = raid.week;
  const qualifiers = Object.values(raid.hard).filter(days => days.filter(day => monday(day) === week).length >= 3).length;
  return { code:raid.code, week:raid.week, members:raid.members.length, hp:raid.hp, max:raid.max, won:raid.won, qualifiers,
    required:Math.ceil(raid.members.length/2), mine:(raid.hard[id] ?? []).filter(day=>monday(day)===week).length,
    hit:raid.attacks[id]===today, canInvite:raid.owner===id&&!raid.won&&!Object.keys(raid.attacks).length,
    invited:raid.owner===id?Object.keys(raid.invites??{}):[] };
}

export async function createArsenal(dataDir) {
  const file = join(dataDir,'arsenal-v1.json');
  let current;
  try { current = JSON.parse(await readFile(file,'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; current = {players:{},listings:{},raids:{}}; }
  async function save(next) {
    const temporary = file + '.' + randomUUID() + '.tmp';
    try {
      await writeFile(temporary,JSON.stringify(next),{flag:'wx',mode:0o600});
      await rename(temporary,file);
    } finally { await unlink(temporary).catch(()=>{}); }
    current = next;
  }

  if (current.collectionVersion !== COLLECTION_VERSION) {
    for (const listing of Object.values(current.listings)) {
      if (listing.bidder) account(current, listing.bidder).gold += listing.bid;
    }
    for (const player of Object.values(current.players)) {
      player.owned = {};
      player.last = null;
      player.rewardReceipts = {};
      player.opened = 0;
    }
    current.listings = {};
    current.duels = {};
    current.collectionVersion = COLLECTION_VERSION;
    await save(current);
  }

  // Called in the economy queue after the account token has been verified. Escrow, balances and ownership share one commit.
  return async (id, body, now, friends = [], reciprocal = friends) => {
    const next = structuredClone(current);
    next.duels ??= {};
    for (const [key,duel] of Object.entries(next.duels)) if (duel.expires <= now) delete next.duels[key];
    const player = account(next,id);
    if (body.collectionVersion === COLLECTION_VERSION && !player.release40Gift) {
      for (let i = 0; i < 15; i++) grantPack(player);
      player.release40Gift = true;
    }
    const utc = Math.floor(now/DAY), today = int(body.today,utc-1,utc+1) ? body.today : utc;
    const week = monday(utc);
    const typed = /^arsenal-v[23]-/.test(body.op);
    const modern = typed || body.rules >= 2;
    const currentCatalog = body.catalogueVersion >= 4;
    const canRead = id => (catalog.get(id)?.introduced??0)<=(body.catalogueVersion??0) && (modern || legacyCard(id)) && (currentCatalog || !id.startsWith('atelier-'));
    const op = body.op.replace(/^arsenal-v[23]-/, 'arsenal-');
    for (const [code, raid] of Object.entries(next.raids)) {
      if (raid.week == null && raid.month === monthKey(now)) raid.week = week;
      if (raid.week !== week) delete next.raids[code];
    }
    settleMarket(next,now);
    let raid = Object.values(next.raids).find(group=>group.members.includes(id));
    let opening = null;
    if (['arsenal-state','arsenal-sync','arsenal-solo'].includes(op)) {
      if (op !== 'arsenal-state') {
        claimDays(player,body.days,today);
        if (typed) { protectDays(player,body.sportDays,today); recordAttackDays(player,body.attackDays,today); }
        if (body.combatVersion === 1 && !stances.includes(body.stance)) fail(400);
        const character = [1, 2].includes(body.combatVersion) ? body.character : null;
        if ([1, 2].includes(body.combatVersion) && !validCharacter(character)) fail(400);
        solo(player, today, op === 'arsenal-solo', int(body.power,0,5) ? body.power : 0,
          typed ? combatMix(body) : null, body.combatVersion === 1 ? body.stance : null, character, completeSet(player, body.loadout) ? 1 : 0);
      }
    } else if (op === 'arsenal-open') {
      if (!families.has(body.slot)) fail(400);
      if (body.booster != null && (!['WEAPON','ARMOR'].includes(body.booster) || body.slot !== (body.booster==='WEAPON'?'WEAPON':'CHEST'))) fail(400);
      claimDays(player,body.days,today);
      if (body.op === 'arsenal-v3-open') {
        if (!cleanText(body.rewardKey)) fail(400);
        const key=body.rewardKey;
        player.rewardReceipts ??= {};
        opening=player.rewardReceipts[key]??null;
        if (!opening && player.packCredits?.includes(key)) {
          if (Object.keys(player.owned).length > 2000) fail(507);
          const affinities=packSchools(today+player.opened,body.slot);
          const pieces=loot(body.slot,false,affinities,currentCatalog,body.catalogueVersion??0,body.booster);
          for (const piece of pieces) player.owned[piece.key]=piece;
          player.packCredits=player.packCredits.filter(credit=>credit!==key);
          opening=player.rewardReceipts[key]={day:today,utc,slot:body.slot,gold:false,pieces,schools:affinities};
          for (const [receipt,draw] of Object.entries(player.rewardReceipts)) if (draw.utc<utc-35) delete player.rewardReceipts[receipt];
        }
      } else if (player.last?.day === today) {
        if (!modern && player.last.pieces.some(piece=>!legacyCard(piece.id))) fail(409);
        opening = player.last;
      } else {
        const requiredRank = body.collectionVersion === COLLECTION_VERSION ? 3 : 1;
        if ((player.days[today] ?? 0) < requiredRank || player.last?.day > today) fail(409);
        if (Object.keys(player.owned).length > 2000) fail(507);
        const gold = (player.opened+1)%10===0;
        const affinities = typed ? packSchools(today,body.slot) : null;
        const pieces = loot(body.slot,gold,affinities,currentCatalog,body.catalogueVersion??0,body.booster);
        for (const piece of pieces) player.owned[piece.key] = piece;
        opening = player.last = {day:today,utc,slot:body.slot,gold,pieces,...(affinities?{schools:affinities}:{})};
        player.opened++;
      }
    } else if (op === 'arsenal-duel-invite') {
      if (!cleanText(body.request) || !HEX.test(body.to??'') || body.to===id || !reciprocal.includes(body.to)) fail(403);
      if (!validCharacter(body.character)) fail(400);
      const scoreKeys = ['forceScore','dexScore','conScore'];
      const scores = scoreKeys.some(key => body.character[key] != null) ? scoreKeys.map(key => body.character[key]) : [];
      const signature=JSON.stringify([body.to,...['strength','dexterity','constitution','days','model'].map(k=>body.character[k]),body.loadout??[],...scores]);
      const existing=Object.values(next.duels).find(d=>d.from===id&&d.request===body.request);
      if (existing && (existing.signature!==signature)) fail(409);
      if (!existing) {
        if (Object.values(next.duels).filter(d=>d.from===id&&d.status==='pending').length>=8 || Object.keys(next.duels).length>=3000 ||
          Object.values(next.duels).some(d=>d.status==='pending'&&[d.from,d.to].includes(id)&&[d.from,d.to].includes(body.to))) fail(409);
        const key=randomUUID();
        next.duels[key]={id:key,request:body.request,signature,from:id,to:body.to,a:characterSnapshot(player,body),status:'pending',expires:now+3*DAY};
      }
    } else if (['arsenal-duel-accept','arsenal-duel-decline','arsenal-duel-cancel'].includes(op)) {
      const duel=cleanText(body.duel)&&next.duels[body.duel];
      if (!duel || ![duel.from,duel.to].includes(id)) fail(404);
      if (op==='arsenal-duel-cancel') {
        if (duel.from!==id) fail(403);
        if (duel.status==='pending') duel.status='cancelled';
      } else {
        if (duel.to!==id) fail(403);
        if (op==='arsenal-duel-decline') { if (duel.status==='pending') duel.status='declined'; }
        else if (duel.status==='pending') {
          if (!reciprocal.includes(duel.from)) fail(403);
          if (!validCharacter(body.character)) fail(400);
          duel.b=characterSnapshot(player,body);
          duel.result=battle(duel.a,duel.b);
          duel.status='complete'; duel.expires=now+7*DAY;
        } else if (duel.status!=='complete') fail(409);
      }
    } else if (op === 'arsenal-freeze') {
      claimDays(player,body.days,today);
      protectDays(player,body.sportDays,today);
      recordAttackDays(player,body.attackDays,today);
      const dungeon=settleDungeon(player,today);
      if (dungeon.frozenDay!==today) {
        if (!player.ice || !dungeon.health) fail(409);
        player.ice--; dungeon.frozenDay=today;
      }
    } else if (op === 'arsenal-purchase-pack') {
      if (!cleanText(body.request)) fail(400);
      player.packPurchases ??= {};
      if (!Object.hasOwn(player.packPurchases, body.request)) {
        if (player.gold < PACK_PRICE) fail(402);
        player.gold -= PACK_PRICE;
        grantPack(player);
        player.packPurchases[body.request] = utc;
      }
      for (const [request, day] of Object.entries(player.packPurchases)) {
        if (day < utc - 35) delete player.packPurchases[request];
      }
    } else if (op === 'arsenal-gift') {
      if (!cleanText(body.request) || !reciprocal.includes(body.to) || body.to===id ||
          !['GOLD','PACK','ICE'].includes(body.kind)) fail(403);
      player.giftReceipts ??= {};
      const prior=player.giftReceipts[body.request];
      const gift=`${body.to}:${body.kind}`;
      if (prior && prior.gift!==gift) fail(409);
      if (!prior) {
        const other=account(next,body.to);
        if (body.kind==='GOLD') {
          if (player.gold<25) fail(409);
          player.gold-=25; other.gold+=25;
        } else if (body.kind==='ICE') {
          if (!player.ice) fail(409);
          player.ice--; other.ice=(other.ice??0)+1;
        } else {
          const token=player.packCredits?.shift();
          if (!token) fail(409);
          (other.packCredits??=[]).push(token);
        }
        player.giftReceipts[body.request]={gift,utc};
        for (const [receipt,entry] of Object.entries(player.giftReceipts)) if (entry.utc<utc-35) delete player.giftReceipts[receipt];
      }
    } else if (body.op === 'arsenal-raid-create') {
      if (raid) fail(409);
      let code;
      do { code = randomBytes(4).toString('hex').toUpperCase(); } while (next.raids[code]);
      raid = next.raids[code] = {code,week,owner:id,members:[id],max:115,hp:115,attacks:{},hard:{},won:false};
    } else if (op === 'arsenal-raid-invite') {
      if (!raid || raid.owner!==id || raid.won || raid.members.length>=8 || Object.keys(raid.attacks).length ||
          !reciprocal.includes(body.to) || raid.members.includes(body.to)) fail(409);
      raid.invites ??= {};
      if (!raid.invites[body.to] && Object.keys(raid.invites).length>=24) fail(409);
      if (Object.values(next.raids).some(team=>team.members.includes(body.to))) fail(409);
      raid.invites[body.to]=id;
    } else if (op === 'arsenal-raid-join' || op === 'arsenal-raid-accept') {
      if (raid || typeof body.code !== 'string' || !/^[A-F0-9]{8}$/.test(body.code)) fail(409);
      raid = next.raids[body.code];
      if (!raid || raid.members.length >= 8 || Object.keys(raid.attacks).length || raid.won) fail(409);
      if (op==='arsenal-raid-accept' && !raid.invites?.[id]) fail(403);
      if (raid.invites) delete raid.invites[id];
      raid.members.push(id);
      raid.hp = raid.max = 115 * raid.members.length;
    } else if (op === 'arsenal-raid-hit') {
      if (!raid || raid.won || raid.members.length < 2) fail(409);
      claimDays(player,body.days,today);
      if (typed) recordAttackDays(player,body.attackDays,today);
      if (!(player.days[today] || player.attackDays?.[today])) fail(409);
      raid.hard[id] = Object.keys(player.days).map(Number).filter(day => player.days[day] >= 2 && day <= today && day >= week);
      if ((raid.attacks[id] ?? -1) < today) {
        raid.attacks[id] = today;
        const base = player.days[today] === 3 ? 58 : player.days[today] === 2 ? 49 : 40;
        const matchup = ((week + 3) / 7) % 2 === 0 ? ['ARCANIST','RANGED'] : ['DIVINE','ARCANIST'];
        raid.hp = Math.max(0,raid.hp-(typed?damage(base,combatMix(body),matchup,true):base));
      }
      const view = raidView(raid,id,today);
      if (!raid.hp && view.qualifiers >= view.required) {
        raid.won = true;
        for (const member of raid.members) {
          const winner=account(next,member);
          winner.gold+=120; winner.ice=(winner.ice??0)+1; grantPack(winner);
        }
      }
    } else if (body.op === 'arsenal-fuse') {
      const keep = cleanText(body.key) && player.owned[body.key];
      const spare = cleanText(body.spare) && player.owned[body.spare];
      if (!keep || !spare || keep===spare || keep.id!==spare.id || keep.finish!==spare.finish || keep.level>=5 || spare.level!==1) fail(409);
      keep.level++;
      delete player.owned[body.spare];
    } else if (body.op === 'arsenal-list') {
      if (!cleanText(body.key) || !int(body.price,1,10_000) || !['auction','fixed','offer'].includes(body.mode)) fail(400);
      const piece = player.owned[body.key];
      if (!piece || Object.values(next.listings).filter(listing=>listing.seller===id).length>=8 ||
          Object.keys(next.listings).length>=3000) fail(409);
      const recipient = body.mode==='offer' ? body.to : null;
      if (recipient && (!HEX.test(recipient) || !friends.includes(recipient) || recipient===id)) fail(400);
      if (body.mode==='offer' && !recipient) fail(400);
      const key = randomUUID();
      const wanted = body.wanted == null ? null : body.wanted;
      if (wanted !== null && (body.mode!=='offer' || !catalog.has(wanted))) fail(400);
      next.listings[key] = {key,seller:id,piece,price:body.price,fixed:body.mode==='fixed',end:now+(body.mode==='offer'?3:1)*DAY,recipient,wanted,bid:0,bidder:null};
      delete player.owned[body.key];
    } else if (['arsenal-bid','arsenal-buy','arsenal-cancel','arsenal-decline'].includes(body.op)) {
      const listing = cleanText(body.key) && next.listings[body.key];
      if (!listing || (!modern&&!legacyCard(listing.piece.id))) fail(404);
      if (body.op==='arsenal-cancel' || body.op==='arsenal-decline') {
        const authorized = body.op==='arsenal-cancel' ? listing.seller===id : listing.recipient===id;
        if (!authorized || listing.bidder) fail(409);
        account(next,listing.seller).owned[listing.piece.key]=listing.piece;
        delete next.listings[body.key];
      } else if (body.op==='arsenal-buy') {
        if (listing.seller===id || !(listing.recipient===id || (listing.fixed && !listing.recipient))) fail(403);
        const seller = account(next,listing.seller);
        if (listing.wanted) {
          const piece = cleanText(body.give) && player.owned[body.give];
          if (!piece || piece.id!==listing.wanted) fail(409);
          delete player.owned[piece.key]; seller.owned[piece.key]=piece;
        } else {
          if (player.gold<listing.price) fail(402);
          player.gold-=listing.price; seller.gold+=listing.price;
        }
        player.owned[listing.piece.key]=listing.piece;
        delete next.listings[body.key];
      } else {
        const minimum = Math.max(listing.price,listing.bid+1);
        const prior = listing.bidder===id ? listing.bid : 0;
        if (listing.recipient || listing.fixed || listing.seller===id || !int(body.amount,minimum,10_000)) fail(409);
        if (player.gold+prior<body.amount) fail(402);
        if (listing.bidder) account(next,listing.bidder).gold+=listing.bid;
        player.gold-=body.amount; listing.bid=body.amount; listing.bidder=id;
      }
    } else fail(400);
    if (body.collectionVersion === COLLECTION_VERSION) rewardQuests(player, today);
    next.revision=(next.revision??0)+1;
    await save(next);
    const listings = Object.values(next.listings)
      .filter(l=>canRead(l.piece.id)&&(body.collectionVersion === COLLECTION_VERSION || !l.fixed)&&(!l.recipient||l.recipient===id||l.seller===id))
      .sort((a,b)=>a.end-b.end)
      .slice(0,100).map(({seller,bidder,...l})=>({...l,seller:l.recipient===id?seller:null,mine:seller===id,leading:bidder===id,incoming:l.recipient===id}));
    const raidInvites=raid?[]:Object.values(next.raids).filter(team=>team.invites?.[id]&&!team.won&&
      team.members.length<8&&!Object.keys(team.attacks).length).map(team=>({code:team.code,from:team.invites[id]}));
    const duels=Object.values(next.duels).filter(d=>[d.from,d.to].includes(id)).map(d=>{
      const incoming=d.to===id,complete=d.status==='complete';
      return {id:d.id,opponent:incoming?d.from:d.to,incoming,status:d.status,expires:d.expires,
        mine:incoming?(d.b??null):d.a,theirs:complete?(incoming?d.a:d.b):null};
    });
    return {arsenal:{rules:3,boosters:2,combat:2,arena:1,collectionVersion:COLLECTION_VERSION,packPrice:PACK_PRICE,duels, packCredits:player.packCredits??[], ice:player.ice??0, raidInvites,revision:next.revision,gold:player.gold,opened:player.opened,owned:Object.values(player.owned).filter(piece=>canRead(piece.id)),opening:opening?.pieces.every(piece=>canRead(piece.id))?opening:null,listings,dungeon:player.dungeon??null,raid:raidView(raid,id,today)}};
  };
}
