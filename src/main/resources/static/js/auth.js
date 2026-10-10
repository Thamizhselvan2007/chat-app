const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const tabLogin = document.getElementById('tabLogin');
const tabRegister = document.getElementById('tabRegister');
const msg = document.getElementById('msg');

function show(text, type) {
  msg.textContent = text;
  msg.className = type;
}

function switchTab(login) {
  loginForm.classList.toggle('hidden', !login);
  registerForm.classList.toggle('hidden', login);
  tabLogin.classList.toggle('active', login);
  tabRegister.classList.toggle('active', !login);
  document.getElementById('verifyForm').classList.add('hidden');
  show('', '');
}
tabLogin.onclick = () => switchTab(true);
tabRegister.onclick = () => switchTab(false);

async function post(url, data) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  const text = await res.text();
  return { ok: res.ok, text };
}

loginForm.onsubmit = async (e) => {
  e.preventDefault();
  const r = await post('/api/auth/login', {
    username: document.getElementById('loginUser').value.trim(),
    password: document.getElementById('loginPass').value
  });
  if (r.ok) {
    localStorage.setItem('me', r.text); window.location.href = '/chat.html';
  } else {
    show(r.text, 'error');
  }
};

let pendingEmail = '';

registerForm.onsubmit = async (e) => {
  e.preventDefault();
  const email = document.getElementById('regEmail').value.trim();
  show('Sending code...', '');
  const r = await post('/api/auth/register', {
    username: document.getElementById('regUser').value.trim(),
    email: email,
    password: document.getElementById('regPass').value
  });
  if (r.ok) {
    pendingEmail = email;
    registerForm.classList.add('hidden');
    document.getElementById('verifyForm').classList.remove('hidden');
    show(r.text, 'ok');
  } else {
    show(r.text, 'error');
  }
};

document.getElementById('verifyForm').onsubmit = async (e) => {
  e.preventDefault();
  const r = await post('/api/auth/verify', {
    email: pendingEmail,
    code: document.getElementById('verCode').value.trim()
  });
  if (r.ok) {
    document.getElementById('verifyForm').classList.add('hidden');
    switchTab(true);
    show('Account created. Please log in.', 'ok');
  } else {
    show(r.text, 'error');
  }
};
