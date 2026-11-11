// Standalone review: works offline, keeps its stable localStorage key across regeneration.
const fs=require('node:fs'),path=require('node:path');
const assets=path.join(__dirname,'../app/src/superhealth/assets/arsenal');
let html=fs.readFileSync(path.join(assets,'review.html'),'utf8');
html=html.replace(/<link rel="stylesheet" href="([\w.-]+)">/g,(_,f)=>'<style>'+fs.readFileSync(path.join(assets,f),'utf8')+'</style>');
html=html.replace(/<script src="([\w.-]+)"><\/script>/g,(_,f)=>'<script>'+fs.readFileSync(path.join(assets,f),'utf8').replace(/<\/script/gi,'<\\/script')+'</script>');
fs.writeFileSync(path.join(__dirname,'../review/arsenal-rework/index.html'),html);
console.log('Standalone rating view generated.');
