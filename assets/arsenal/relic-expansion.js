'use strict';
// One individually authored collectible per family and visual tradition. These are
// visual traditions only: the player's four damage types live in equipmentSchools.
const RelicExpansion = [
 ['WARRIOR','weapon',4,'Le Jugement du griffon','Hallebarde','griffin','Griffon d’or, acier damassé et grenat'],
 ['WARRIOR','chest',3,'La Cuirasse des cent rois','Cuirasse lamellaire','sovereign','Acier noir, cuir et or pâle'],
 ['WARRIOR','guard',4,'Le Lion de la porte rouge','Pavois héraldique','lion','Or repoussé et émail rouge'],
 ['WARRIOR','legs',3,'La Marche des cataphractes','Grèves de cavalerie','cavalry','Acier riveté et velours'],
 ['MAGE','weapon',3,'L’Atlas du ciel brisé','Grimoire suspendu','atlas','Reliure d’argent et cristal'],
 ['MAGE','chest',4,'La Nuit aux mille yeux','Manteau céleste','astral','Soie de nuit et opales'],
 ['MAGE','guard',3,'L’Iris des neuf mondes','Astrolabe de garde','iris','Argent et saphir'],
 ['MAGE','legs',4,'Les Anneaux de l’apesanteur','Bottes orbitales','orbital','Argent vivant et cristal'],
 ['PRIEST','weapon',4,'Le Chant du premier soleil','Encensoir de procession','censer','Vermeil, perles et opale'],
 ['PRIEST','chest',3,'Les Plumes du sanctuaire','Chasuble séraphique','seraph','Brocart ivoire et fil d’or'],
 ['PRIEST','guard',4,'Le Triptyque de l’aurore','Reliquaire articulé','triptych','Nacre, cristal et or'],
 ['PRIEST','legs',3,'Les Pas du jardin blanc','Sandales de cérémonie','pilgrim','Cuir blanc et perles'],
 ['RANGER','weapon',3,'Le Croc du grand cerf','Arc de chasse','antler','If, bois de cerf et jade'],
 ['RANGER','chest',4,'La Peau de la forêt ancienne','Manteau à écailles','drake','Écailles de jade et cuir'],
 ['RANGER','guard',3,'Le Scarabée du bois dormant','Carapace de garde','beetle','Chitine et bronze'],
 ['RANGER','legs',4,'La Course du lynx royal','Bottes de chasse','lynx','Cuir, fourrure et argent'],
 ['OCCULTIST','weapon',4,'La Lanterne des noms perdus','Lanterne rituelle','lantern','Argent noir et verre de nuit'],
 ['OCCULTIST','chest',3,'La Colonne du roi oublié','Cuirasse vertébrale','spine','Os sculpté et obsidienne'],
 ['OCCULTIST','guard',4,'Le Reflet du dernier témoin','Miroir rituel','mirror','Obsidienne et argent noir'],
 ['OCCULTIST','legs',3,'Les Chausses du voile déchiré','Bottes lacées','spectral','Velours, os et argent'],
 ['ARTIFICER','weapon',3,'Le Marteau des heures','Marteau mécanique','chrono','Laiton, acier et quartz'],
 ['ARTIFICER','chest',4,'Le Cœur du grand automate','Harnais mécanique','reactor','Orichalque et verre optique'],
 ['ARTIFICER','guard',3,'Le Rempart à six volets','Bouclier déployable','folding','Acier bleui et cuivre'],
 ['ARTIFICER','legs',4,'Les Turbines du ciel profond','Bottes à turbines','turbine','Cuivre, céramique et cristal'],
].map(([school,slot,tier,name,type,design,material],i)=>({
 id:`relic-${school.toLowerCase()}-${slot}-v2`,school,slot,tier,rarity:tier,name:[name],type,material,design,
 special:design,art:slot,variant:i,form:i%8,pose:[125,146,0,1.2],number:95+Math.floor(i/4),
 stat:({weapon:'Bras',chest:'Pectoraux',guard:'Dos',legs:'Jambes'})[slot],
 colors:equipmentSchools[school].colors,color:equipmentSchools[school].colors[0],background:equipmentSchools[school].scene,
 roll:(tier===4?240:150)+(i*7)%(tier===4?91:61),
 // A curse is an equipment trade-off, independent of its damage type or visual school.
 ...(i===2||i===10||i===14?{curse:{key:slot==='guard'?'JAM':'DOS',stat:slot==='guard'?'Jambes':'Dos',value:tier===4?60:45}}:{}),
}));
