// Game snapshots only: no raw health logs. Keep integer formulas aligned with CharacterCombat.kt.
export const stances = ['ASSAULT', 'FEINT', 'GUARD'];
export const modifier = value => Math.floor((value-10)/2);
export const beats = (a,b) => (stances.indexOf(a)+1)%3 === stances.indexOf(b);
export function validCharacter(c) {
  return c && c.model===1 && ['strength','dexterity','constitution'].every(k=>Number.isInteger(c[k])&&c[k]>=8&&c[k]<=20) && Number.isInteger(c.days)&&c.days>=0&&c.days<=28;
}
export function score(character, stat) {
  const key={strength:'forceScore',dexterity:'dexScore',constitution:'conScore'}[stat];
  const precise=character[key];
  if (Number.isInteger(precise) && precise>=0 && precise<=100 && 8+Math.floor(precise*12/100)===character[stat]) return precise;
  return null;
}
export function displayScore(character, stat) {
  return score(character, stat) ?? Math.round((character[stat]-8)*100/12);
}
function power(character, stat) {
  const precise=score(character, stat);
  return precise === null ? character[stat]*100 : 800 + 12*precise;
}

export function battle(a,b) {
  let myHealth=10000, theirHealth=10000;
  const exchanges=[];
  for(const stat of ['strength','dexterity','constitution']) {
    if (!myHealth || !theirHealth) break;
    const key={strength:'forceBonus',dexterity:'dexBonus',constitution:'conBonus'}[stat];
    const mine=power(a,stat)*(10000+(a[key]??0)),theirs=power(b,stat)*(10000+(b[key]??0));
    const myDamage=Math.min(theirHealth,Math.floor(5200*mine/(mine+theirs)));
    const theirDamage=Math.min(myHealth,Math.floor(5200*theirs/(mine+theirs)));
    myHealth-=theirDamage; theirHealth-=myDamage;
    exchanges.push({stat,myDamage,theirDamage,myHealth,theirHealth});
  }
  return {exchanges,myHealth,theirHealth,winner:Math.sign(myHealth-theirHealth)};
}
