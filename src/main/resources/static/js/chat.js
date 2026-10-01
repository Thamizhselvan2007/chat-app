const me = JSON.parse(localStorage.getItem('me') || 'null');
if (!me) { window.location.href = '/'; throw new Error('not logged in'); }
document.getElementById('meName').textContent = me.username;

const $ = id => document.getElementById(id);
const messagesDiv = $('messages');
let users = [], groups = [], online = [];
let current = null;
const unread = {};
const names = {};
names[me.id] = me.username;

function scrollDown() { messagesDiv.scrollTop = messagesDiv.scrollHeight; }

function fmt(s) {
  const d = new Date(String(s).substring(0, 23) + 'Z');
  return isNaN(d) ? '' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function makeItem(label, active, badgeCount, dotOn, onClick) {
  const div = document.createElement('div');
  div.className = 'item' + (active ? ' active' : '');
  if (dotOn !== null) {
    const dot = document.createElement('span');
    dot.className = 'dot' + (dotOn ? ' on' : '');
    div.append(dot);
  }
  const name = document.createElement('span');
  name.textContent = label;
  div.append(name);
  if (badgeCount) {
    const b = document.createElement('span');
    b.className = 'badge';
    b.textContent = badgeCount;
    div.append(b);
  }
  div.onclick = onClick;
  return div;
}

function renderUsers() {
  const list = $('userList');
  list.innerHTML = '';
  users.forEach(u => {
    const active = current && current.type === 'user' && current.id === u.id;
    list.append(makeItem(u.username, active, unread['u' + u.id], online.includes(u.id),
      () => openChat({ type: 'user', id: u.id, name: u.username })));
  });
}

function renderGroups() {
  const list = $('groupList');
  list.innerHTML = '';
  groups.forEach(g => {
    const active = current && current.type === 'group' && current.id === g.id;
    list.append(makeItem(g.name, active, unread['g' + g.id], null,
      () => openChat({ type: 'group', id: g.id, name: g.name })));
  });
}

async function loadUsers() {
  const res = await fetch('/api/users');
  if (!res.ok) { localStorage.removeItem('me'); window.location.href = '/'; return; }
  users = await res.json();
  users.forEach(u => { names[u.id] = u.username; });
  const o = await fetch('/api/online');
  online = o.ok ? await o.json() : [];
  renderUsers();
}

async function loadGroups() {
  const res = await fetch('/api/groups');
  if (res.ok) { groups = await res.json(); renderGroups(); }
}

function addBubble(m) {
  const mine = m.senderId === me.id;
  const div = document.createElement('div');
  div.className = 'bubble' + (mine ? ' mine' : '');
  if (current && current.type === 'group' && !mine) {
    const who = document.createElement('div');
    who.className = 'who';
    who.textContent = names[m.senderId] || ('User ' + m.senderId);
    div.append(who);
  }
  const text = document.createElement('div');
  text.textContent = m.content;
  const time = document.createElement('div');
  time.className = 'time';
  time.textContent = fmt(m.sentAt);
  div.append(text, time);
  messagesDiv.append(div);
}

async function openChat(chat) {
  current = chat;
  unread[(chat.type === 'user' ? 'u' : 'g') + chat.id] = 0;
  $('chatTitle').textContent = chat.name;
  $('sendForm').classList.remove('hidden');
  $('addMemberBtn').classList.toggle('hidden', chat.type !== 'group');
  const url = chat.type === 'user' ? '/api/messages/' + chat.id : '/api/groups/' + chat.id + '/messages';
  const res = await fetch(url);
  const list = res.ok ? await res.json() : [];
  messagesDiv.innerHTML = '';
  list.forEach(addBubble);
  scrollDown();
  renderUsers();
  renderGroups();
}

let ws;
function connect() {
  ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    if (m.type === 'group') {
      if (current && current.type === 'group' && current.id === m.groupId) {
        addBubble(m); scrollDown();
      } else {
        unread['g' + m.groupId] = (unread['g' + m.groupId] || 0) + 1;
        renderGroups();
      }
    } else {
      const other = m.senderId === me.id ? m.receiverId : m.senderId;
      if (current && current.type === 'user' && current.id === other) {
        addBubble(m); scrollDown();
      } else if (m.senderId !== me.id) {
        unread['u' + other] = (unread['u' + other] || 0) + 1;
        renderUsers();
      }
    }
  };
  ws.onclose = () => setTimeout(connect, 2000);
}

$('sendForm').onsubmit = e => {
  e.preventDefault();
  const input = $('msgInput');
  const text = input.value.trim();
  if (!text || !current || ws.readyState !== 1) return;
  const payload = current.type === 'user'
    ? { receiverId: current.id, content: text }
    : { groupId: current.id, content: text };
  ws.send(JSON.stringify(payload));
  input.value = '';
};

$('newGroupBtn').onclick = async () => {
  const name = prompt('Group name?');
  if (!name || !name.trim()) return;
  const res = await fetch('/api/groups', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: name.trim() })
  });
  if (res.ok) await loadGroups(); else alert('Could not create group');
};

$('addMemberBtn').onclick = async () => {
  const uname = prompt('Username to add?');
  if (!uname) return;
  const u = users.find(x => x.username.toLowerCase() === uname.trim().toLowerCase());
  if (!u) { alert('User not found'); return; }
  const res = await fetch('/api/groups/' + current.id + '/members', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId: u.id })
  });
  alert(await res.text());
};

$('logoutBtn').onclick = async () => {
  await fetch('/api/auth/logout', { method: 'POST' });
  localStorage.removeItem('me');
  window.location.href = '/';
};

loadUsers();
loadGroups();
connect();
setInterval(loadUsers, 5000);
setInterval(loadGroups, 5000);
