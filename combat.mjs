// Game snapshots only: no raw health logs. Keep integer formulas aligned with CharacterCombat.kt.
export const stances = ['ASSAULT', 'FEINT', 'GUARD'];
export const modifier = value => Math.floor((value-10)/2);
export const beats = (a,b) => (stances.indexOf(a)+1)%3 === stances.indexOf(b);
export function validCharacter(c) {
  return c && c.model===1 && ['strength','dexterity','constitution'].every(k=>Number.isInteger(c[k])&&c[k]>=8&&c[k]<=20) && Number.isInteger(c.days)&&c.days>=0&&c.days<=28;
}
export function battle(a,b) {
  let myHealth=10000, theirHealth=10000;
  const exchanges=[];
  for(const stat of ['strength','dexterity','constitution']) {
    if (!myHealth || !theirHealth) break;
    const key={strength:'forceBonus',dexterity:'dexBonus',constitution:'conBonus'}[stat];
    const mine=a[stat]*(10000+(a[key]??0)),theirs=b[stat]*(10000+(b[key]??0));
    const myDamage=Math.min(theirHealth,Math.floor(5200*mine/(mine+theirs)));
    const theirDamage=Math.min(myHealth,Math.floor(5200*theirs/(mine+theirs)));
    myHealth-=theirDamage; theirHealth-=myDamage;
    exchanges.push({stat,myDamage,theirDamage,myHealth,theirHealth});
  }
  return {exchanges,myHealth,theirHealth,winner:Math.sign(myHealth-theirHealth)};
}
