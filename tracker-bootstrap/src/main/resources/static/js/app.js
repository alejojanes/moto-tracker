'use strict';

// App web de rastreo: mapa con la última posición GPS, recorrido, geocercas y alertas.

const STALE_MINUTES = 15;
const state = { imei: null, reliable: null, latest: null, stream: null, route: null, zones: [] };

const map = L.map('map', { zoomControl: true }).setView([4.6, -74.1], 6);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '© OpenStreetMap'
}).addTo(map);

const motoIcon = L.divIcon({
  className: '',
  html: '<div style="width:22px;height:22px;border-radius:50%;background:#15803d;border:3px solid #fff;box-shadow:0 0 0 2px #15803d"></div>',
  iconSize: [22, 22],
  iconAnchor: [11, 11]
});
const motoMarker = L.marker([0, 0], { icon: motoIcon });
const approxCircle = L.circle([0, 0], { radius: 500, color: '#b45309', dashArray: '6 6', fill: false });
const zoneLayer = L.layerGroup().addTo(map);

// ---------- utilidades ----------

function cookie(name) {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : '';
}

async function api(path, options = {}) {
  const method = options.method || 'GET';
  const headers = { Accept: 'application/json' };
  if (method !== 'GET') {
    headers['X-XSRF-TOKEN'] = cookie('XSRF-TOKEN');
    headers['Content-Type'] = 'application/json';
  }
  const res = await fetch(path, { method, headers, credentials: 'same-origin',
    body: options.body ? JSON.stringify(options.body) : undefined });
  if (res.status === 401) {
    location.href = '/login.html';
    throw new Error('Sesión vencida');
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || ('Error ' + res.status));
  }
  return res.status === 204 ? null : res.json();
}

function ago(iso) {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return 'hace menos de 1 min';
  if (minutes < 60) return 'hace ' + minutes + ' min';
  const hours = Math.round(minutes / 60);
  if (hours < 48) return 'hace ' + hours + ' h';
  return 'hace ' + Math.round(hours / 24) + ' días';
}

function fmt(iso) {
  return new Date(iso).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' });
}

function esc(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// ---------- estado de la moto ----------

function render() {
  const status = document.getElementById('status');
  const r = state.reliable;
  const latest = state.latest;
  if (!r) {
    status.innerHTML = '<span class="pill warn">Sin posición GPS todavía</span>';
  } else {
    const stale = (Date.now() - new Date(r.fixTime).getTime()) / 60000 > STALE_MINUTES;
    const pill = stale ? '<span class="pill warn">GPS ' + ago(r.fixTime) + '</span>'
                       : '<span class="pill ok">GPS ' + ago(r.fixTime) + '</span>';
    const ign = r.ignition === true ? ' · encendida' : r.ignition === false ? ' · apagada' : '';
    status.innerHTML = pill + ' <b>' + r.speedKmh + ' km/h</b>' + ign;
    motoMarker.setLatLng([r.latitude, r.longitude]).addTo(map)
      .bindPopup('Última posición GPS<br>' + fmt(r.fixTime) + '<br>' + r.speedKmh + ' km/h'
        + (r.satellites ? '<br>' + r.satellites + ' satélites' : ''));
  }
  // Una posición no GPS más nueva se muestra solo como referencia: no es la ubicación real.
  if (latest && latest.source !== 'GPS' && (!r || latest.fixTime > r.fixTime)) {
    approxCircle.setLatLng([latest.latitude, latest.longitude]).addTo(map)
      .bindTooltip('Posición aproximada (' + (latest.source === 'NETWORK' ? 'antenas' : 'sin señal GPS') + ')');
  } else {
    approxCircle.remove();
  }
}

async function loadPosition(center) {
  const data = await api('/api/trackers/' + state.imei + '/position');
  state.reliable = data.reliable;
  state.latest = data.latest;
  render();
  if (center && state.reliable) {
    map.setView([state.reliable.latitude, state.reliable.longitude], 16);
  }
}

function connectStream() {
  if (state.stream) state.stream.close();
  state.stream = new EventSource('/api/trackers/' + state.imei + '/stream');
  state.stream.addEventListener('position', (event) => {
    const p = JSON.parse(event.data);
    state.latest = p;
    if (p.source === 'GPS') {
      state.reliable = p;
      if (state.route) state.route.addLatLng([p.latitude, p.longitude]);
    }
    render();
  });
  // EventSource se reconecta solo; al volver, recarga por si se perdió algo.
  state.stream.addEventListener('open', () => loadPosition(false).catch(console.error));
}

// ---------- recorrido ----------

async function toggleRoute() {
  if (state.route) {
    state.route.remove();
    state.route = null;
    return;
  }
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const points = await api('/api/trackers/' + state.imei + '/route?from=' + start.toISOString());
  if (points.length === 0) {
    alert('No hay recorrido GPS registrado hoy.');
    return;
  }
  state.route = L.polyline(points.map(p => [p.latitude, p.longitude]), { color: '#2563eb', weight: 4 }).addTo(map);
  map.fitBounds(state.route.getBounds(), { padding: [30, 30] });
}

// ---------- panel ----------

function openPanel(title, html) {
  document.getElementById('panel-title').textContent = title;
  document.getElementById('panel-body').innerHTML = html;
  document.getElementById('panel').hidden = false;
}

function closePanel() {
  document.getElementById('panel').hidden = true;
}

const ALERT_LABEL = {
  GEOFENCE_ENTER: 'Entró a zona', GEOFENCE_EXIT: 'Salió de zona', MOVED_WHILE_PARKED: 'Movimiento apagada',
  SOS: 'SOS', POWER_CUT: 'Corte de alimentación', DEVICE_OFFLINE: 'Desconectado', DEVICE_ONLINE: 'Conectado'
};

async function showAlerts() {
  const alerts = await api('/api/trackers/' + state.imei + '/alerts?limit=50');
  const items = alerts.map(a => '<li><div class="grow"><b>' + esc(ALERT_LABEL[a.type] || a.type) + '</b>'
    + '<small>' + esc(a.message) + ' · ' + fmt(a.time) + '</small></div>'
    + (a.latitude != null ? '<button data-lat="' + a.latitude + '" data-lon="' + a.longitude + '">Ver</button>' : '')
    + '</li>').join('');
  openPanel('Alertas', alerts.length ? '<ul class="list">' + items + '</ul>' : '<p>Sin alertas.</p>');
  document.querySelectorAll('#panel-body button[data-lat]').forEach(btn => btn.addEventListener('click', () => {
    map.setView([+btn.dataset.lat, +btn.dataset.lon], 17);
    closePanel();
  }));
}

async function loadZones() {
  state.zones = await api('/api/trackers/' + state.imei + '/geofences');
  zoneLayer.clearLayers();
  state.zones.filter(z => z.active).forEach(z => L.circle([z.latitude, z.longitude], {
    radius: z.radiusMeters, color: '#15803d', weight: 2, fillOpacity: 0.08
  }).bindTooltip(z.name).addTo(zoneLayer));
}

async function showZones() {
  await loadZones();
  const items = state.zones.map(z => '<li><div class="grow"><b>' + esc(z.name) + '</b><small>'
    + Math.round(z.radiusMeters) + ' m · ' + (z.active ? 'activa' : 'pausada') + '</small></div>'
    + '<button data-toggle="' + z.id + '" data-active="' + z.active + '">' + (z.active ? 'Pausar' : 'Activar') + '</button>'
    + '<button data-delete="' + z.id + '">Borrar</button></li>').join('');
  const where = state.reliable ? 'en la posición actual de la moto' : 'en el centro del mapa';
  openPanel('Zonas (geocercas)', (state.zones.length ? '<ul class="list">' + items + '</ul>' : '<p>Aún no tienes zonas.</p>')
    + '<form class="form" id="zone-form"><b>Nueva zona ' + where + '</b>'
    + '<input name="name" placeholder="Nombre, por ejemplo Casa" maxlength="60" required>'
    + '<input name="radius" type="number" min="50" max="50000" value="150" required aria-label="Radio en metros">'
    + '<button class="primary" type="submit">Crear zona</button></form>');

  document.querySelectorAll('#panel-body [data-toggle]').forEach(btn => btn.addEventListener('click', async () => {
    await api('/api/geofences/' + btn.dataset.toggle, { method: 'PATCH', body: { active: btn.dataset.active !== 'true' } });
    showZones();
  }));
  document.querySelectorAll('#panel-body [data-delete]').forEach(btn => btn.addEventListener('click', async () => {
    if (!confirm('¿Borrar esta zona?')) return;
    await api('/api/geofences/' + btn.dataset.delete, { method: 'DELETE' });
    showZones();
  }));
  document.getElementById('zone-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const center = state.reliable ? { lat: state.reliable.latitude, lng: state.reliable.longitude } : map.getCenter();
    try {
      await api('/api/trackers/' + state.imei + '/geofences', { method: 'POST', body: {
        name: form.get('name'), latitude: center.lat, longitude: center.lng, radiusMeters: Number(form.get('radius'))
      } });
      showZones();
    } catch (e) {
      alert(e.message);
    }
  });
}

// ---------- arranque ----------

async function selectTracker(imei) {
  state.imei = imei;
  if (state.route) { state.route.remove(); state.route = null; }
  await loadPosition(true);
  await loadZones();
  connectStream();
}

async function start() {
  const trackers = await api('/api/trackers');
  const select = document.getElementById('tracker');
  if (trackers.length === 0) {
    document.getElementById('status').innerHTML =
      '<span class="pill warn">Esperando el primer reporte del rastreador</span>';
    setTimeout(start, 15000);
    return;
  }
  select.innerHTML = trackers.map(t => '<option value="' + esc(t.imei) + '">' + esc(t.name) + '</option>').join('');
  select.hidden = trackers.length === 1;
  select.onchange = () => selectTracker(select.value);
  await selectTracker(trackers[0].imei);
}

document.getElementById('btn-center').addEventListener('click', () => loadPosition(true));
document.getElementById('btn-route').addEventListener('click', () => toggleRoute().catch(e => alert(e.message)));
document.getElementById('btn-zones').addEventListener('click', () => showZones().catch(e => alert(e.message)));
document.getElementById('btn-alerts').addEventListener('click', () => showAlerts().catch(e => alert(e.message)));
document.getElementById('panel-close').addEventListener('click', closePanel);
// Al volver a la app desde segundo plano, refresca la posición.
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.imei) loadPosition(false).catch(console.error);
});

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {});
}

start().catch(e => {
  document.getElementById('status').textContent = e.message;
});
