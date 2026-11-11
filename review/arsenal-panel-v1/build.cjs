// Read-only snapshot of the current catalogue. Writes exclusively into this panel.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const assets=path.resolve(__dirname,'../../app/src/superhealth/assets/arsenal');
const context=vm.createContext({});
const sources=['cartes.js','armes-etudes.js','equipment-expansion.js','classes.js','relic-expansion.js','relic-sculpt.js','relic-forge.js','relic-art.js','relic-curation.js','fantasy-art.js','catalogue.js'];
for(const file of sources)vm.runInContext(fs.readFileSync(path.join(assets,file),'utf8'),context);
const selection=[
 ['relic-warrior-weapon-v2','Le griffon devient la structure de la tête : aile tranchante, bec de garde et grenat serti. Le damas et la sculpture justifient la rareté.'],
 ['relic-warrior-chest-v2','Des lamelles articulées, des rivets et des sangles donnent une vraie construction à la cuirasse. Le motif de plume relie les quatre pièces.'],
 ['relic-warrior-guard-v2','Un lion en repoussé émerge de l’émail rouge. Le relief, le volume et le contour couronné donnent une présence de relique. La malédiction reste un badge distinct.'],
 ['relic-warrior-legs-v2','Une paire complète, avec articulations aux chevilles et genouillères en bec. Le velours rouge et les nervures prolongent la cuirasse.'],
 ['4-weapon-0','La commune reste désirable par ses proportions et ses matières : fer forgé, fil affûté et frêne. Aucun ornement artificiellement précieux.'],
 ['3-legs-1','Le cuir tressé, les coutures et les semelles épaisses évoquent un objet fiable. Le motif solaire rappelle son affinité Divine, sans lui attribuer une Bénédiction.'],
 ['5-weapon-2','L’if poli et l’acier bleui mettent en valeur la mécanique. Le motif de cloche individualise la pièce sans masquer sa fonction à distance.'],
 ['9-weapon-3','Le matériau existant — acier damassé et or pâle — devient une caisse de résonance métallique ajourée. La magie est contenue dans la rosace. Arcaniste, sans malédiction.'],
 ['3-weapon-3','La courbe de l’arc reste immédiatement lisible. Les veines de l’if pétrifié et la rose d’argent portent sa double affinité À distance / Arcaniste.'],
 ['relic-priest-weapon-v2','Une architecture de vermeil ajouré, d’opale et de perles. La magie reste à l’intérieur de l’objet ; la fumée est légère. Divine ne lui ajoute pas de Bénédiction.']
];
const cards=selection.map(([id,rationale])=>{
 context.selectedId=id;
 const c=vm.runInContext('ArsenalCatalog.byId[selectedId]',context);
 if(!c)throw Error(id);
 const previews=Object.fromEntries(['normal','blessed','shiny'].map(finish=>{context.selectedFinish=finish;return [finish,vm.runInContext('({svg:ArsenalCatalog.cardFace(ArsenalCatalog.byId[selectedId],{finish:selectedFinish}),boost:ArsenalCatalog.effect(ArsenalCatalog.byId[selectedId],{finish:selectedFinish,mult:ArsenalCatalog.finishes[selectedFinish].mult})})',context)];}));
 const png=fs.readFileSync(path.join(__dirname,'art',id+'.png'));
 return {...JSON.parse(JSON.stringify(c)),rationale,previews,art:'art/'+id+'.png',artWidth:png.readUInt32BE(16),artHeight:png.readUInt32BE(20)};
});
const data={version:'arsenal-panel-v1',date:'2026-11-10',cards,tiers:vm.runInContext('ArsenalCatalog.tiers',context),types:vm.runInContext('RelicCuration.types',context)};
fs.writeFileSync(path.join(__dirname,'data.js'),'window.PANEL = '+JSON.stringify(data)+';\n');
fs.writeFileSync(path.join(__dirname,'selection.json'),JSON.stringify(cards.map(({previews,...card})=>card),null,2)+'\n');
console.log('Snapshot: '+cards.length+' cards; original catalogue untouched.');
