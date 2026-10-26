'use strict';

function cookie(name) {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : '';
}

document.getElementById('login').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  const body = new URLSearchParams();
  body.set('username', form.get('username'));
  body.set('password', form.get('password'));
  if (form.get('remember-me')) body.set('remember-me', 'true');
  const res = await fetch('/login', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': cookie('XSRF-TOKEN') },
    body,
    credentials: 'same-origin'
  });
  if (res.ok) {
    location.href = '/';
  } else {
    document.getElementById('error').hidden = false;
  }
});
