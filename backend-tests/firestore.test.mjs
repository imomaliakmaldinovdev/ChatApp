import { before, after, beforeEach, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, collection, query, where, getDocs, serverTimestamp } from 'firebase/firestore';
let env;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-chat-app', firestore: { rules: readFileSync('firestore.rules','utf8'), host:'127.0.0.1', port:8080 } }); });
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db=context.firestore();
    for (const uid of ['alice','bob','eve']) await setDoc(doc(db,'profiles',uid),{displayName:uid,searchName:uid,bio:''});
    await setDoc(doc(db,'conversations','alice:bob'),{memberIds:['alice','bob'],createdAt:new Date()});
    await setDoc(doc(db,'conversations/alice:bob/messages/seed'),{senderId:'alice',text:'Hello',createdAt:new Date()});
  });
});
const db = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
const message = (uid,text='Hello') => ({senderId:uid,text,createdAt:serverTimestamp()});
test('participants can read and send text', async () => {
 await assertSucceeds(getDoc(doc(db('bob'),'conversations/alice:bob/messages/seed')));
 await assertSucceeds(setDoc(doc(db('bob'),'conversations/alice:bob/messages/reply'),message('bob')));
});
test('non-member and anonymous cannot read or send',async()=>{
 for(const uid of ['eve',null]) {
  await assertFails(getDoc(doc(db(uid),'conversations/alice:bob')));
  await assertFails(getDoc(doc(db(uid),'conversations/alice:bob/messages/seed')));
  await assertFails(setDoc(doc(db(uid),'conversations/alice:bob/messages/attack'),message(uid??'alice')));
 }
});
test('sender spoofing, empty, whitespace and oversized text fail',async()=>{
 const ref=doc(db('alice'),'conversations/alice:bob/messages/invalid');
 await assertFails(setDoc(ref,message('bob')));
 for(const text of ['', '   ', '\n', 'x'.repeat(4001)]) await assertFails(setDoc(ref,message('alice',text)));
 await assertSucceeds(setDoc(ref,message('alice','x'.repeat(4000))));
});
test('multiline message permitted',async()=>{ await assertSucceeds(setDoc(doc(db('alice'),'conversations/alice:bob/messages/multiline'),message('alice','Hello\nBob'))); });
test('messages immutable and duplicate IDs cannot overwrite',async()=>{
 const ref=doc(db('alice'),'conversations/alice:bob/messages/seed');
 await assertFails(setDoc(ref,message('alice','Changed')));
 await assertFails(deleteDoc(ref));
});
test('profile owner only, with private fields rejected',async()=>{
 await assertSucceeds(updateDoc(doc(db('alice'),'profiles/alice'),{bio:'Hi'}));
 await assertFails(updateDoc(doc(db('bob'),'profiles/alice'),{bio:'Forged'}));
 await assertFails(updateDoc(doc(db('alice'),'profiles/alice'),{email:'private@example.test'}));
 await assertFails(getDoc(doc(db(null),'profiles/alice')));
});
test('canonical pair creation validates membership, profiles, timestamp',async()=>{
 const data={memberIds:['alice','eve'],createdAt:serverTimestamp()};
 await assertFails(setDoc(doc(db('bob'),'conversations/alice:eve'),data));
 await assertFails(setDoc(doc(db('alice'),'conversations/random'),data));
 await assertFails(setDoc(doc(db('alice'),'conversations/alice:missing'),{memberIds:['alice','missing'],createdAt:serverTimestamp()}));
 await assertSucceeds(setDoc(doc(db('alice'),'conversations/alice:eve'),data));
 await assertFails(updateDoc(doc(db('alice'),'conversations/alice:bob'),{memberIds:['alice','eve']}));
});
test('queries must constrain membership',async()=>{
 const store=db('alice');
 await assertFails(getDocs(collection(store,'conversations')));
 await assertSucceeds(getDocs(query(collection(store,'conversations'),where('memberIds','array-contains','alice'))));
});
test('forged timestamps, extra message fields and unknown collections denied',async()=>{
 const store=db('alice');
 await assertFails(setDoc(doc(store,'conversations/alice:bob/messages/fake'),{...message('alice'),createdAt:new Date(0)}));
 await assertFails(setDoc(doc(store,'conversations/alice:bob/messages/extra'),{...message('alice'),admin:true}));
 await assertFails(setDoc(doc(store,'private/secrets'),{value:'x'}));
});
