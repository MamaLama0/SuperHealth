import {test} from 'node:test';
import assert from 'node:assert/strict';
import {battle,stances} from './combat.mjs';
const a={strength:16,dexterity:12,constitution:18},b={strength:10,dexterity:20,constitution:13};
test('combat matches the Kotlin vector and is symmetric',()=>{
  const result=battle(a,b,'FEINT','GUARD');
  assert.deepEqual(result.exchanges.map(r=>r.myDamage),[3200,1950,3019]);
  assert.equal(result.myHealth,2570); assert.equal(result.theirHealth,1831);
  for(const left of stances)for(const right of stances) {
    const ab=battle(a,b,left,right), ba=battle(b,a,right,left);
    assert.equal(ab.myHealth,ba.theirHealth);assert.equal(ab.theirHealth,ba.myHealth);
  }
});
test('base gear bonuses matter without making same-character battles asymmetric',()=>{
  const gear={...a,forceBonus:330,dexBonus:330,conBonus:660};
  assert.equal(battle(gear,gear,'GUARD','GUARD').winner,0);
  assert.equal(battle(gear,a,'GUARD','GUARD').winner,1);
});
