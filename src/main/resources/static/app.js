const $ = (selector) => document.querySelector(selector);
const roles = { STUDENT: '学生', CLUB_STAFF: '社团工作人员', TEACHER: '审核老师', ADMIN: '管理员' };
const statuses = { PENDING: '待审核', APPROVED: '已发布', REJECTED: '已驳回', CANCELLED: '已取消' };
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const date = value => esc(value.replace('T', ' ').slice(0, 16));
let user = null, tab = 'discover', clubs = [], events = [], toastTimer, renderVersion = 0;

async function api(path, options = {}) {
  const response = await fetch('/api' + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.message || `请求失败 (${response.status})`);
  return result;
}
function toast(text) {
  $('#toast').textContent = text; $('#toast').hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => $('#toast').hidden = true, 4200);
}
function navigation() {
  const tabs = [['discover', '◎', '发现活动']];
  if (user?.role === 'STUDENT') tabs.push(['registered', '▤', '我的报名']);
  if (user?.role === 'CLUB_STAFF') tabs.push(['club', '▤', '社团活动'], ['create', '+', '创建活动']);
  if (user?.role === 'TEACHER') tabs.push(['review', '✓', '审核活动']);
  if (user?.role === 'ADMIN') tabs.push(['accounts', '▤', '账号管理'], ['new-user', '+', '创建账号']);
  $('#navigation').innerHTML = tabs.map(([key, icon, label]) => `<button data-tab="${key}" class="${tab === key ? 'active' : ''}" ${tab === key ? 'aria-current="page"' : ''}><span class="nav-icon">${icon}</span>${label}</button>`).join('');
  $('#identity').textContent = user ? `${user.name} · ${roles[user.role]}` : '校园社团活动平台';
  $('#login-open').hidden = Boolean(user); $('#logout').hidden = !user;
}
const title = (heading, subtitle = '') => `<div class="section-head"><h2>${heading}<small>${subtitle}</small></h2></div>`;
const empty = text => `<div class="empty">${text}</div>`;
const badge = status => `<span class="badge ${status.toLowerCase()}">${statuses[status]}</span>`;

function card(view, mode = 'discover') {
  const a = view.activity;
  const registered = mode === 'registered';
  return `<article class="card"><div class="card-banner">${esc(view.clubName)} / CAMPUS CLUB</div><div class="card-body">
    ${badge(a.status)}<h3>${esc(a.title)}</h3><p class="card-description">${esc(a.description)}</p>
    <p>地点 ${esc(a.location)}</p><p>时间 ${date(a.startsAt)}</p>
    <div class="meta">已报名 ${view.registeredCount} / ${a.capacity} 人<br>报名截止 ${date(a.deadline)}</div>
    ${a.reviewComment && mode === 'club' ? `<p>审核意见：${esc(a.reviewComment)}</p>` : ''}
    <div class="actions"><button class="button outline" data-detail="${a.id}">活动详情</button>
    ${mode === 'club' ? `<button class="button outline" data-participants="${a.id}">报名名单</button>${a.status !== 'CANCELLED' ? `<button class="button danger" data-cancel="${a.id}">取消活动</button>` : ''}` : ''}
    ${mode === 'discover' && user?.role === 'STUDENT' ? `<button class="button dark" data-register="${a.id}">报名参加</button>` : ''}
    ${registered ? '<span class="badge">已报名</span>' : ''}
    ${!user ? '<button class="button dark" data-login>登录报名</button>' : ''}
    </div></div></article>`;
}
function activityForm() {
  return title('创建社团活动', '提交后将进入老师的审核列表') + `<form id="activity-form" class="panel form-grid">
    <label class="full">活动名称<input name="title" minlength="2" maxlength="100" required placeholder="例如：周末吉他交流会"></label>
    <label class="full">活动简介<textarea name="description" minlength="2" maxlength="3000" required placeholder="介绍活动内容和安排"></textarea></label>
    <label>地点<input name="location" minlength="2" maxlength="200" required></label>
    <label>人数上限<input name="capacity" type="number" min="1" max="5000" value="30" required></label>
    <label>报名截止时间<input name="deadline" type="datetime-local" required></label>
    <label>活动开始时间<input name="startsAt" type="datetime-local" required></label>
    <label class="full">参加条件<input name="requirements" maxlength="500" value="本校在读学生" required></label>
    <p class="hint full">日期按北京时间填写。截止时间需要晚于当前时间，活动开始时间需要晚于截止时间。</p>
    <div class="full"><button class="button dark" type="submit">提交审核</button></div></form>`;
}
function userForm() {
  return title('创建账号', '为账号分配角色和所属社团') + `<form id="user-form" class="panel form-grid">
    <label>账号<input name="username" pattern="[a-zA-Z0-9_]{3,30}" required placeholder="3–30 位字母、数字或下划线"></label>
    <label>姓名<input name="name" maxlength="50" required></label>
    <label>初始密码<input type="password" name="password" minlength="8" maxlength="128" autocomplete="new-password" required></label>
    <label>角色<select name="role" id="new-role">${Object.entries(roles).map(([key, label]) => `<option value="${key}">${label}</option>`).join('')}</select></label>
    <label id="club-field" hidden>所属社团<select name="clubId">${clubs.map(c => `<option value="${esc(c.id)}">${esc(c.name)}</option>`).join('')}</select></label>
    <div class="full"><button class="button dark" type="submit">创建账号</button></div></form>`;
}
async function render(query = '') {
  const version = ++renderVersion;
  navigation();
  const root = $('#workspace');
  try {
    if (tab === 'create') { root.innerHTML = activityForm(); return; }
    if (tab === 'new-user') { root.innerHTML = userForm(); return; }
    root.innerHTML = '<p class="muted">正在加载…</p>';
    let html;
    if (tab === 'discover') {
      const result = await api('/activities?q=' + encodeURIComponent(query));
      if (version !== renderVersion) return;
      events = result;
      html = title('发现活动', `${events.length} 场已发布活动`) + `<form id="search-form" class="search"><input name="q" aria-label="搜索活动或社团" placeholder="搜索活动名称或社团…" value="${esc(query)}"><button class="button dark">搜索</button></form>`;
      html += events.length ? `<div class="grid">${events.map(v => card(v)).join('')}</div>` : empty('暂时没有符合条件的活动');
    } else if (tab === 'registered') {
      const rows = await api('/student/registrations');
      if (version !== renderVersion) return;
      events = rows.map(r => r.event);
      html = title('我的报名', '查看已报名活动及最新状态') + (events.length ? `<div class="grid">${events.map(v => card(v, 'registered')).join('')}</div>` : empty('你还没有报名活动，去发现活动看看吧'));
    } else if (tab === 'club') {
      const result = await api('/club/activities');
      if (version !== renderVersion) return;
      events = result;
      html = title('社团活动', '查看审核结果和报名情况') + (events.length ? `<div class="grid">${events.map(v => card(v, 'club')).join('')}</div>` : empty('还没有活动，点击“创建活动”提交第一场活动'));
    } else if (tab === 'review') {
      const result = await api('/teacher/pending');
      if (version !== renderVersion) return;
      events = result;
      html = title('待审核活动', `${events.length} 项待处理`) + (events.length ? `<div class="review-list">${events.map(v => {
        const a = v.activity;
        return `<form class="panel review-form" data-id="${a.id}"><h3>${esc(a.title)}</h3><span class="badge">${esc(v.clubName)}</span><p>${esc(a.description)}</p><p>地点：${esc(a.location)}<br>参加条件：${esc(a.requirements)}<br>人数上限：${a.capacity}<br>报名截止：${date(a.deadline)}<br>活动开始：${date(a.startsAt)}</p><label>审核意见<textarea name="comment" maxlength="1000" placeholder="驳回时必须填写原因"></textarea></label><div class="actions"><button type="submit" name="decision" value="approve" class="button dark">通过并发布</button><button type="submit" name="decision" value="reject" class="button danger">驳回</button></div></form>`;
      }).join('')}</div>` : empty('待审核列表为空'));
    } else if (tab === 'accounts') {
      const rows = await api('/admin/users');
      if (version !== renderVersion) return;
      html = title('账号管理', `${rows.length} 个账号`) + `<div class="panel table-wrap"><table><thead><tr><th>账号</th><th>姓名</th><th>角色</th><th>社团</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows.map(r => `<tr><td>${esc(r.username)}</td><td>${esc(r.name)}</td><td>${roles[r.role]}</td><td>${esc(clubs.find(c => c.id === r.clubId)?.name || '—')}</td><td>${r.enabled ? '正常' : '已禁用'}</td><td><button class="button outline" data-enable="${r.id}" data-value="${!r.enabled}" ${r.id === user.id ? 'disabled' : ''}>${r.enabled ? '禁用' : '启用'}</button></td></tr>`).join('')}</tbody></table></div>`;
    }
    if (version === renderVersion) root.innerHTML = html;
  } catch (error) {
    if (version === renderVersion) root.innerHTML = empty(esc(error.message));
  }
}
function openLogin() { $('#login-error').textContent = ''; $('#login-dialog').showModal(); }
function details(id) {
  const v = events.find(v => v.activity.id === id); if (!v) return;
  const a = v.activity;
  $('#detail-content').innerHTML = `${badge(a.status)}<h2>${esc(a.title)}</h2><p class="muted">${esc(v.clubName)}</p><p class="detail-description">${esc(a.description)}</p><p>地点：${esc(a.location)}<br>参加条件：${esc(a.requirements)}<br>开始时间：${date(a.startsAt)}<br>报名截止：${date(a.deadline)}<br>人数：${v.registeredCount} / ${a.capacity}</p>`;
  $('#detail-dialog').showModal();
}
document.addEventListener('click', async event => {
  const button = event.target.closest('button'); if (!button) return;
  const d = button.dataset;
  if (d.tab) { tab = d.tab; await render(); return; }
  if ('login' in d || button.id === 'login-open') { openLogin(); return; }
  if (button.id === 'login-close') { $('#login-dialog').close(); return; }
  if (button.id === 'detail-close') { $('#detail-dialog').close(); return; }
  if (d.demo) { $('#login-form').elements.username.value = d.demo; $('#login-form').elements.password.value = 'demo1234'; return; }
  if (d.detail) { details(d.detail); return; }
  if (!(d.register || d.cancel || d.participants || d.enable || button.id === 'logout')) return;
  button.disabled = true;
  try {
    if (button.id === 'logout') { await api('/auth/logout', { method: 'POST' }); user = null; tab = 'discover'; }
    if (d.register) { await api(`/activities/${d.register}/registrations`, { method: 'POST' }); toast('报名成功，可以在“我的报名”中查看'); }
    if (d.cancel) {
      if (!confirm('确认取消此活动？已报名学生将看到“已取消”状态。')) return;
      await api(`/club/activities/${d.cancel}/cancel`, { method: 'POST' }); toast('活动已取消');
    }
    if (d.enable) { await api(`/admin/users/${d.enable}/enabled`, { method: 'PATCH', body: { enabled: d.value === 'true' } }); toast('账号状态已更新'); }
    if (d.participants) {
      const rows = await api(`/club/activities/${d.participants}/participants`);
      $('#detail-content').innerHTML = `<h2>报名名单</h2>${rows.length ? `<ul>${rows.map(r => `<li>${esc(r.student.name)}（${esc(r.student.username)}）<br><span class="muted">${date(r.registeredAt)}</span></li>`).join('')}</ul>` : '<p class="muted">暂时没有学生报名。</p>'}`;
      $('#detail-dialog').showModal(); return;
    }
    await render();
  } catch (error) { toast(error.message); }
  finally { button.disabled = false; }
});
document.addEventListener('change', event => {
  if (event.target.id === 'new-role') $('#club-field').hidden = event.target.value !== 'CLUB_STAFF';
});
document.addEventListener('submit', async event => {
  const form = event.target; event.preventDefault();
  const data = Object.fromEntries(new FormData(form));
  if (form.id === 'search-form') { await render(data.q); return; }
  const submit = event.submitter; if (submit) submit.disabled = true;
  try {
    if (form.id === 'login-form') {
      user = await api('/auth/login', { method: 'POST', body: data });
      $('#login-dialog').close();
      tab = ({ STUDENT: 'discover', CLUB_STAFF: 'club', TEACHER: 'review', ADMIN: 'accounts' })[user.role];
    } else if (form.id === 'activity-form') {
      data.capacity = Number(data.capacity);
      await api('/club/activities', { method: 'POST', body: data });
      tab = 'club'; toast('活动已提交，等待老师审核');
    } else if (form.classList.contains('review-form')) {
      const approved = submit?.value === 'approve';
      await api(`/teacher/activities/${form.dataset.id}/review`, { method: 'POST', body: { approved, comment: data.comment } });
      toast(approved ? '活动已通过并发布' : '活动已驳回');
    } else if (form.id === 'user-form') {
      if (data.role !== 'CLUB_STAFF') data.clubId = null;
      await api('/admin/users', { method: 'POST', body: data });
      tab = 'accounts'; toast('账号已创建');
    }
    await render();
  } catch (error) {
    if (form.id === 'login-form') $('#login-error').textContent = error.message;
    else toast(error.message);
  } finally { if (submit) submit.disabled = false; }
});
(async () => {
  try { $('#demo-help').hidden = !(await api('/config')).demoLoginEnabled; } catch { /* Keep demo helper hidden when config is unavailable. */ }
  try { clubs = await api('/clubs'); } catch (error) { toast(error.message); }
  try { user = await api('/auth/me'); } catch { user = null; }
  await render();
})();
