// Run with Node after editing the equipment art catalogue. --check verifies the APK loot index.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const dir = path.join(__dirname, '../app/src/superhealth/assets/arsenal');
const renderer = vm.createContext({});
for (const name of ['cartes.js', 'armes-etudes.js', 'equipment-expansion.js', 'classes.js', 'relic-expansion.js', 'relic-sculpt.js', 'relic-forge.js', 'relic-art.js', 'relic-curation.js', 'fantasy-art.js', 'atelier-data.js', 'atelier-images.js', 'atelier-art.js', 'refinement-data.js', 'refined-art.js', 'catalogue.js']) {
  vm.runInContext(fs.readFileSync(path.join(dir, name), 'utf8'), renderer);
}
const cards = vm.runInContext(`ArsenalCatalog.allCatalog.map(card => {
  const [min, max] = ArsenalCatalog.tiers[card.tier].range;
  return {
    id: card.id, title: card.name.join(' '), type: card.type, material: card.material,
    ...(card.introduced?{introduced:card.introduced}:{}),
    tier: card.tier, min, max, available: card.available!==false, slot: card.slot.toUpperCase(), attribute: ({weapon:"FOR",chest:"CON",guard:"CON",legs:"DEX"})[card.slot], school: card.school, damageTypes: card.damageTypes,
    ...(card.set?{setId:card.set.id,setName:card.set.name}:{}),
    ...(card.curse ? { penalty: card.curse.key, penaltyAttribute: ({BRA:"FOR",PEC:"CON",DOS:"CON",JAM:"DEX",REG:"CON",CAR:"DEX"})[card.curse.key], penaltyPoints: card.curse.value } : {}),
  };
})`, renderer);
const result = '[\n' + cards.map(card => JSON.stringify(card)).join(',\n') + '\n]\n';
const target = path.join(dir, 'loot.json');
const art = process.argv.indexOf('--art');
if (art >= 0) {
  const output = process.argv[art + 1];
  fs.mkdirSync(output, {recursive:true});
  const faces = vm.runInContext('ArsenalCatalog.allCatalog.map(card => [card.id, ArsenalCatalog.revealArt(card, {finish:"normal",boost:0}).face])', renderer);
  for (const [id,svg] of faces) fs.writeFileSync(path.join(output,id+'.svg'),svg);
} else if (process.argv.includes('--check')) {
  require('node:assert/strict').equal(fs.readFileSync(target, 'utf8'), result);
} else {
  fs.writeFileSync(target, result);
}
