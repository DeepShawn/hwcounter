import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, normalizeData, studentStats, assignmentCounts, scoreEntryCounts, currentScoreReset, startScorePeriod, restoreScoreHistory, scoreBoundaryIndex, resetStudentScores, addStudentScores } from '../../web/js/model.js';
import { merge } from '../../web/js/cloud.js';
const rules = defaultRules();
const fixture = () => ({ id: 'c1', name: '一班', students: [{ id:'s1', no:'01', name:'张三' }, { id:'s2', no:'02', name:'李四' }], assignments: [
  { id:'a1', title:'第一回', createdAt:1000, statuses:{s1:'MISSING'}, comments:{s1:'补交中'} },
  { id:'a2', title:'第二回', createdAt:2000, statuses:{s1:'EXCELLENT', s2:'MISSING'}, comments:{} },
  { id:'a3', title:'第三回', createdAt:3000, statuses:{s1:'LATE'}, comments:{} },
], scores:[
  {id:'m0',studentId:'s1',delta:3,reason:'表现',time:1500},
  {id:'m1',studentId:'s1',delta:2,reason:'进步',time:2500},
  {id:'r0',studentId:'s1',delta:-4,reason:'重置分数至 0',time:2600},
  {id:'r1',studentId:'s1',delta:5,reason:'新重置',kind:'reset',time:3500},
] });
const values = c => studentStats(c,rules).map(s=>s.score);

test('选中作业包含在新计分范围，完整保留作业/流水/未交次数',()=>{
  const c=fixture(), original=structuredClone(c), reset=startScorePeriod(c,'a2',true,5000);
  assert.deepEqual(values(c),[104,98]); assert.deepEqual(values(reset),[102,98]);
  assert.deepEqual(reset.assignments,c.assignments); assert.deepEqual(reset.scores,c.scores); assert.deepEqual(c,original);
  assert.deepEqual(studentStats(reset,rules).map(s=>s.counts),studentStats(c,rules).map(s=>s.counts));
  assert.equal(studentStats(reset,{...rules,initial:0})[0].score,2);
  assert.equal(scoreBoundaryIndex(reset,reset.assignments),1);
  assert.equal(scoreBoundaryIndex(reset,[reset.assignments[2]]),-1);
  assert.equal(startScorePeriod(c,'bad'),c);
});
test('可不结转手动分，未来时钟及同毫秒旧记录排除，之后的新记录计入',()=>{
  const c=fixture(); c.scores.push({id:'future',studentId:'s1',delta:20,time:7000,reason:'时钟偏差'});
  const reset=startScorePeriod(c,'a2',false,5000);
  assert.equal(values(reset)[0],100); assert.equal(scoreEntryCounts(reset,c.scores.at(-1)),false);
  const next={...reset,scores:[...reset.scores,{id:'new',studentId:'s1',delta:1.5,time:5000,reason:'进步'}]};
  assert.equal(values(next)[0],101.5);
  assert.equal(scoreEntryCounts(startScorePeriod(c,'a2',true,5000),c.scores.at(-1)),true);
});
test('旧作业仍可补登记，不影响本轮分；新增作业与手动分正常累计',()=>{
  const c=startScorePeriod(fixture(),'a2',true,5000);
  const edited={...c,assignments:c.assignments.map((a,i)=>i===0?{...a,statuses:{s1:'EXCELLENT'}}:a)};
  assert.equal(values(edited)[0],102); assert.equal(studentStats(edited,rules)[0].missing,0);
  const next=addStudentScores({...c,assignments:[...c.assignments,{id:'a4',createdAt:6000,statuses:{s1:'MISSING'}}]},['s1'],2,'表现');
  assert.equal(values(next)[0],102); assert.equal(studentStats(next,rules)[0].missing,2);
});
test('本轮内仍可重置指定分数，下轮排除旧补偿，恢复历史可逆',()=>{
  const c=startScorePeriod(fixture(),'a2',true,5000), reset=resetStudentScores(c,rules,['s1'],0);
  assert.equal(values(reset)[0],0); assert.equal(reset.scores.at(-1).kind,'reset');
  const next=startScorePeriod(reset,'a3',true,Date.now()+1000);
  assert.equal(values(next)[0],99); assert.equal(next.scores.length,reset.scores.length);
  assert.equal(currentScoreReset(restoreScoreHistory(next,Date.now()+2000)),null);
  const restored=restoreScoreHistory(c,6000); assert.deepEqual(values(restored),values(fixture()));
});
test('同毫秒作业以选中项为界，删除起点作业后仍保留计分边界',()=>{
  const c=fixture(); c.assignments=c.assignments.map(a=>({...a,createdAt:2000}));
  const reset=startScorePeriod(c,'a2',true,5000);
  assert.deepEqual(reset.assignments.map(a=>assignmentCounts(reset,a)),[false,true,true]);
  const removed={...reset,assignments:reset.assignments.filter(a=>a.id!=='a2')};
  assert.equal(currentScoreReset(removed).assignmentTitle,'第二回'); assert.equal(scoreBoundaryIndex(removed,removed.assignments),1);
  const added={id:'new',createdAt:2000,statuses:{}}; assert.equal(assignmentCounts(reset,added),true);
});
test('JSON备份兼容旧班级，新计分边界和恢复事件往返不丢失',()=>{
  const c=startScorePeriod(fixture(),'a2',false,5000);
  const encoded=JSON.parse(JSON.stringify({version:1,classes:[c],currentClassId:'c1',settings:{}}));
  const back=normalizeData(encoded).classes[0]; assert.deepEqual(values(back),values(c));
  assert.deepEqual(back.scoreResets,c.scoreResets);
  const legacy=normalizeData({version:1,classes:[fixture()],settings:{}}).classes[0];
  assert.deepEqual(legacy.scoreResets,[]); assert.deepEqual(values(legacy),[104,98]);
});
test('云冲突双向合并都选最新起点，恢复事件也同步，原始作业与流水不丢',()=>{
  const base=fixture(), a=startScorePeriod(base,'a2',true,5000), b=startScorePeriod(base,'a3',false,6000);
  const wrap=c=>({classes:[c],settings:{},currentClassId:'c1'});
  for(const [x,y] of [[a,b],[b,a]]) {
    const c=merge(wrap(x),wrap(y)).classes[0]; assert.equal(currentScoreReset(c).assignmentId,'a3');
    assert.equal(c.scoreResets.length,2); assert.deepEqual(JSON.parse(JSON.stringify(c.assignments)),base.assignments); assert.deepEqual(c.scores,base.scores);
  }
  const restored=restoreScoreHistory(b,7000);
  for(const [x,y] of [[a,restored],[restored,a]]) assert.equal(currentScoreReset(merge(wrap(x),wrap(y)).classes[0]),null);
});
