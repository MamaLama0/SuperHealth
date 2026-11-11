'use strict';
// The authored manifest is the source of truth. No image service is involved.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..');
const original=JSON.parse(fs.readFileSync(path.join(root,'art/arsenal-v5/refinements.json'),'utf8'));
const additions=JSON.parse(fs.readFileSync(path.join(root,'art/arsenal-expansion/items.json'),'utf8'));
assert.equal(Object.keys(original).length,400);assert.equal(Object.keys(additions).length,150);
assert(Object.keys(additions).every(id=>!original[id]));
const data={...original,...additions};
assert.equal(Object.keys(data).length,550);
for(const [id,r] of Object.entries(data))for(const key of ['form','name','type','material','reviewNote'])assert(r[key],id+': '+key);
const output="'use strict';\nconst RefinementData="+JSON.stringify(data)+';\n';
const target=path.join(root,'app/src/superhealth/assets/arsenal/refinement-data.js');
if(process.argv.includes('--check'))assert.equal(fs.readFileSync(target,'utf8'),output);
else fs.writeFileSync(target,output);
console.log('550 authored refinements: manifest/runtime parity OK');
