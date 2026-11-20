const $ = (selector) => document.querySelector(selector);
const roles = { STUDENT: '学生', CLUB_STAFF: '社团工作人员', TEACHER: '审核老师', ADMIN: '管理员' };
const statuses = { PENDING: '待审核', APPROVED: '已发布', REJECTED: '已驳回', CANCELLED: '已取消' };
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const date = value => esc(value.replace('T', ' ').slice(0, 16));
let user = null, tab = 'discover', clubs = [], events = [], postRows = [], toastTimer, renderVersion = 0;

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
  const tabs = [['discover', '◎', '发现活动'], ['posts', '▤', '社团公告']];
  if (user) tabs.push(['messages', '✉', '站内消息']);
  if (user?.role === 'STUDENT') tabs.push(['registered', '▤', '我的报名']);
  if (user?.role === 'CLUB_STAFF') tabs.push(['club', '▤', '社团活动'], ['create', '+', '创建活动'], ['club-application', '+', '申请新社团'], ['club-posts', '▤', '管理公告'], ['reviews', '▤', '审核记录']);
  if (user?.role === 'TEACHER') tabs.push(['review', '✓', '审核活动'], ['approved', '▤', '已通过活动'], ['club-review', '✓', '社团申请'], ['staff', '▤', '负责人名单'], ['reviews', '▤', '审核记录'], ['review-posts', '✓', '公告审核']);
  if (user?.role === 'ADMIN') tabs.push(['accounts', '▤', '账号管理'], ['admin-events', '▤', '全部活动'], ['staff', '▤', '负责人名单'], ['reviews', '▤', '审核记录'], ['new-user', '+', '创建账号']);
  $('#navigation').innerHTML = tabs.map(([key, icon, label]) => `<button data-tab="${key}" class="${tab === key ? 'active' : ''}" ${tab === key ? 'aria-current="page"' : ''}><span class="nav-icon" aria-hidden="true">${icon}</span>${label}</button>`).join('');
  $('#identity').textContent = user ? `${user.name} · ${roles[user.role]}` : '校园社团活动平台';
  $('#login-open').hidden = Boolean(user); $('#logout').hidden = !user;
}
const title = (heading, subtitle = '') => `<div class="section-head"><h2 tabindex="-1">${heading}<small>${subtitle}</small></h2></div>`;
const empty = text => `<div class="empty">${text}</div>`;
const badge = status => `<span class="badge ${status.toLowerCase()}">${statuses[status]}</span>`;

function phase(a) {
  const now = Date.now(), start = new Date(a.startsAt + '+08:00').getTime();
  if (now < start) return '未开始';
  if (!a.endsAt) return '结束时间未设置';
  return now < new Date(a.endsAt + '+08:00').getTime() ? '进行中' : '已结束';
}
function card(view, mode = 'discover') {
  const a = view.activity;
  const registered = mode === 'registered';
  return `<article class="card"><div class="card-banner">${esc(view.clubName)} / CAMPUS CLUB</div><div class="card-body">
    ${badge(a.status)} <span class="badge">${esc(a.category)}</span><h3>${esc(a.title)}</h3><p class="card-description">${esc(a.description)}</p>
    <p>地点 ${esc(a.location)}</p><p>时间 ${date(a.startsAt)}${a.endsAt ? ` 至 ${date(a.endsAt)}` : ''}</p>
    ${a.status === 'APPROVED' ? `<p>活动进度：${phase(a)}</p>` : ''}${a.updatedAt ? `<p>活动信息已变更：${date(a.updatedAt)}</p>` : ''}
    <div class="meta">已报名 ${view.registeredCount} / ${a.capacity} 人（剩余 ${Math.max(0, a.capacity - view.registeredCount)} 人）<br>报名截止 ${date(a.deadline)}</div>
    ${a.reviewComment && mode === 'club' ? `<p>审核意见：${esc(a.reviewComment)}</p>` : ''}
    <div class="actions"><button class="button outline" data-detail="${a.id}">活动详情</button>
    ${a.status === 'APPROVED' ? `<button class="button outline" data-share="${esc(a.id)}">分享及海报</button>` : ''}
    ${user && ['club', 'admin', 'approved', 'registered'].includes(mode) ? `<button class="button outline" data-history="${esc(a.id)}">修改记录</button>` : ''}
    ${mode === 'club' && a.status !== 'CANCELLED' ? `<button class="button outline" data-edit="${a.id}">编辑并重新送审</button>` : ''}
    ${mode === 'admin' ? `<button class="button outline" data-admin-participants="${a.id}">报名名单</button>` : ''}
    ${mode === 'club' ? `<button class="button outline" data-participants="${a.id}">报名名单</button>${a.status !== 'CANCELLED' ? `<button class="button danger" data-cancel="${a.id}">取消活动</button>` : ''}` : ''}
    ${mode === 'discover' && user?.role === 'STUDENT' ? `<button class="button dark" data-register="${a.id}">报名参加</button>` : ''}
    ${registered ? '<span class="badge">已报名</span>' : ''}
    ${!user ? '<button class="button dark" data-login>登录报名</button>' : ''}
    </div></div></article>`;
}
function activityForm(a = null) {
  return title(a ? '编辑社团活动' : '创建社团活动', '提交后将进入老师的审核列表') + `<form id="activity-form" class="panel form-grid" data-id="${a ? esc(a.id) : ''}">
    <label class="full">活动名称<input name="title" value="${esc(a?.title || '')}" minlength="2" maxlength="100" required placeholder="例如：周末吉他交流会"></label>
    <label class="full">活动简介<textarea name="description" minlength="2" maxlength="3000" required placeholder="介绍活动内容和安排">${esc(a?.description || '')}</textarea></label>
    <label>活动分类<select name="category">${['音乐', '运动', '学术', '艺术', '公益', '其他'].map(c => `<option ${c === (a?.category || '其他') ? 'selected' : ''}>${c}</option>`).join('')}</select></label>
    <label>地点<input name="location" value="${esc(a?.location || '')}" minlength="2" maxlength="200" required></label>
    <label>人数上限<input name="capacity" type="number" min="1" max="5000" value="${a?.capacity || 30}" required></label>
    <label>报名截止时间<input name="deadline" type="datetime-local" value="${esc(a?.deadline || '')}" required></label>
    <label>活动开始时间<input name="startsAt" type="datetime-local" value="${esc(a?.startsAt || '')}" required></label>
    <label>活动结束时间<input name="endsAt" type="datetime-local" value="${esc(a?.endsAt || '')}"></label>
    <label class="full">参加条件<input name="requirements" maxlength="500" value="${esc(a?.requirements || '本校在读学生')}" required></label>
    <p class="hint full">日期按北京时间填写。截止时间需要晚于当前时间，活动开始时间需要晚于截止时间。</p>
    <div class="full actions"><button class="button dark" type="submit">提交审核</button>${a ? '<button class="button outline" type="button" data-cancel-edit="club">放弃修改</button>' : ''}</div>${a ? '<p class="hint full">修改后活动将重新审核，已有报名保留。</p>' : ''}</form>`;
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
      html = title('发现活动', `${events.length} 场已发布活动`) + `<form id="search-form" class="search"><input name="q" aria-label="搜索活动或社团" placeholder="搜索活动名称或社团…" value="${esc(query)}"><label>活动日期<input type="date" id="activity-date"></label><label>兴趣分类<select id="activity-category"><option value="">全部</option>${['音乐','运动','学术','艺术','公益','其他'].map(c => `<option>${c}</option>`).join('')}</select></label><button class="button dark">搜索</button></form><p id="filter-summary" role="status"></p>`;
      html += events.length ? `<div class="grid">${events.map(v => card(v)).join('')}</div>` : empty('暂时没有符合条件的活动');
    } else if (tab === 'posts' || tab === 'club-posts' || tab === 'review-posts') {
      const mine = tab === 'club-posts', reviewing = tab === 'review-posts';
      postRows = await api(mine ? '/club/posts' : reviewing ? '/teacher/posts' : '/posts');
      if (version !== renderVersion) return;
      html = title(mine ? '管理社团公告' : reviewing ? '公告审核' : '社团公告', `${postRows.length} 条`);
      if (mine) html += `<form id="post-form" class="panel stack"><h3>发布新公告</h3><label>标题<input name="title" minlength="2" maxlength="100" required></label><label>正文<textarea name="content" minlength="2" maxlength="5000" required></textarea></label><div class="actions"><button class="button dark">提交教师审核</button><button type="button" class="button outline" data-cancel-edit="club-posts" hidden>放弃修改</button></div><p class="hint">修改已发布公告后需要重新审核。</p></form>`;
      html += postRows.length ? postRows.map(v => `<article class="panel"><h3>${esc(v.post.title)}</h3><p>${esc(v.clubName)} · ${esc(v.creatorName)} · ${date(v.post.createdAt)}</p>${badge(v.post.status)}<p class="detail-description">${esc(v.post.content)}</p>${mine && v.post.reviewComment ? `<p>审核意见：${esc(v.post.reviewComment)}</p>` : ''}${mine ? `<button class="button outline" data-edit-post="${esc(v.post.id)}">编辑并重新送审</button>` : ''}${reviewing ? `<form class="post-review-form" data-id="${esc(v.post.id)}"><label>审核意见<textarea name="comment" maxlength="1000" placeholder="驳回时必须填写"></textarea></label><div class="actions"><button name="decision" value="approve" class="button dark">通过并发布</button><button name="decision" value="reject" class="button danger">驳回</button></div></form>` : ''}</article>`).join('') : empty('暂无公告');
    } else if (tab === 'reviews') {
      const path = user.role === 'TEACHER' ? '/teacher/reviews' : user.role === 'ADMIN' ? '/admin/reviews' : '/club/reviews';
      const rows = await api(path);
      if (version !== renderVersion) return;
      html = title('审核记录', '记录活动、社团和公告的每次审核') + (rows.length ? rows.map(r => `<article class="panel"><p>${esc(r.kind)} · ${esc(r.targetTitle)} · ${r.approved ? '通过' : '驳回'}</p><p>审核人：${esc(r.reviewerName)} · ${date(r.reviewedAt)}</p><p>意见：${esc(r.comment || '无')}</p></article>`).join('') : empty('暂无审核记录'));
    } else if (tab === 'messages') {
      const rows = await api('/messages');
      if (version !== renderVersion) return;
      html = title('站内消息', '审核、活动变更与取消通知') + (rows.length ? rows.map(m => `<article class="panel"><p>${esc(m.text)}</p><p>${date(m.createdAt)} · ${m.read ? '已读' : '未读'}</p>${m.read ? '' : `<button class="button outline" data-read="${esc(m.id)}">标记已读</button>`}</article>`).join('') : empty('暂无消息'));
    } else if (tab === 'club-application') {
      const rows = await api('/club/applications');
      if (version !== renderVersion) return;
      html = title('申请新社团', '审核通过后账号将转入新社团') + `<form id="club-application-form" class="panel stack"><label>社团名称<input name="name" minlength="2" maxlength="80" required></label><label>社团简介<textarea name="description" minlength="2" maxlength="1000" required></textarea></label><p>若原社团已有活动，需先安排其他在职负责人，才能转入新社团。</p><button class="button dark">提交申请</button></form><h3>申请记录</h3>` + (rows.length ? rows.map(r => `<article class="panel"><h3>${esc(r.name)} ${badge(r.status)}</h3><p>${esc(r.description)}</p><p>审核意见：${esc(r.comment || '暂无')}</p></article>`).join('') : empty('暂无申请记录'));
    } else if (tab === 'club-review') {
      const rows = (await api('/teacher/club-applications')).filter(r => r.status === 'PENDING');
      if (version !== renderVersion) return;
      html = title('社团申请审核', `${rows.length} 项待处理`) + (rows.length ? rows.map(r => `<form class="panel club-review-form" data-id="${esc(r.id)}"><h3>${esc(r.name)}</h3><p>${esc(r.description)}</p><label>审核意见<textarea name="comment" maxlength="1000" placeholder="驳回时必须填写"></textarea></label><div class="actions"><button name="decision" value="approve" class="button dark">通过</button><button name="decision" value="reject" class="button danger">驳回</button></div></form>`).join('') : empty('没有待审核的申请'));
    } else if (tab === 'staff') {
      const rows = await api(user.role === 'ADMIN' ? '/admin/staff' : '/teacher/staff');
      if (version !== renderVersion) return;
      html = title('社团负责人名单', `${rows.length} 人`) + (rows.length ? `<div class="panel table-wrap"><table><thead><tr><th>姓名</th><th>账号</th><th>所属社团</th><th>状态</th></tr></thead><tbody>${rows.map(r => `<tr><td>${esc(r.name)}</td><td>${esc(r.username)}</td><td>${esc(clubs.find(c => c.id === r.clubId)?.name || '—')}</td><td>${r.enabled ? '正常' : '已禁用'}</td></tr>`).join('')}</tbody></table></div>` : empty('暂无负责人'));
    } else if (tab === 'registered') {
      const rows = await api('/student/registrations');
      if (version !== renderVersion) return;
      events = rows.map(r => r.event);
      html = title('我的报名', '查看活动状态并设置站内提醒') + (rows.length ? `<div class="grid">${rows.map(r => `<div>${card(r.event, 'registered')}<form class="panel reminder-form" data-id="${esc(r.registration.id)}"><label>开始前提醒<select name="minutesBefore"><option value="">关闭提醒</option>${[[60,'1 小时'],[1440,'1 天'],[10080,'7 天']].map(([n,label]) => `<option value="${n}" ${r.reminderMinutes === n ? 'selected' : ''}>${label}</option>`).join('')}</select></label><button class="button outline">保存提醒</button><p>服务器运行时产生站内消息，登录后可查看。</p></form></div>`).join('')}</div>` : empty('你还没有报名活动，去发现活动看看吧'));
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
        return `<form class="panel review-form" data-id="${a.id}"><h3>${esc(a.title)}</h3><span class="badge">${esc(v.clubName)}</span><p>提交人：${esc(v.creatorName)}</p><p>${esc(a.description)}</p><p>地点：${esc(a.location)}<br>参加条件：${esc(a.requirements)}<br>人数上限：${a.capacity}<br>报名截止：${date(a.deadline)}<br>活动开始：${date(a.startsAt)}</p><label>审核意见<textarea name="comment" maxlength="1000" placeholder="驳回时必须填写原因"></textarea></label><div class="actions"><button type="submit" name="decision" value="approve" class="button dark">通过并发布</button><button type="submit" name="decision" value="reject" class="button danger">驳回</button></div></form>`;
      }).join('')}</div>` : empty('待审核列表为空'));
    } else if (tab === 'approved' || tab === 'admin-events') {
      const admin = tab === 'admin-events';
      events = await api(admin ? '/admin/activities' : '/teacher/approved');
      if (version !== renderVersion) return;
      html = title(admin ? '全部社团活动' : '已通过活动', `${events.length} 场活动`) + (events.length ? `<div class="grid">${events.map(v => `<div>` + card(v, admin ? 'admin' : 'approved') + (!admin ? `<form class="panel end-form" data-id="${esc(v.activity.id)}"><label>设置活动结束时间<input name="endsAt" type="datetime-local" value="${esc(v.activity.endsAt)}" required></label><button class="button outline">保存结束时间</button></form>` : '') + `</div>`).join('')}</div>` : empty('暂无活动'));
    } else if (tab === 'accounts') {
      const rows = await api('/admin/users');
      if (version !== renderVersion) return;
      html = title('账号管理', `${rows.length} 个账号`) + `<div class="panel"><label>搜索账号或姓名<input id="account-search" type="search" placeholder="输入账号或姓名"></label><label>角色分类<select id="account-filter"><option value="all">全部账号</option><option value="students">学生（含社团负责人）</option><option value="TEACHER">教师</option><option value="ADMIN">管理员</option></select></label></div><p id="account-summary" role="status">显示 ${rows.length} 个账号</p><div class="panel table-wrap" role="region" aria-label="账号列表，可横向滚动" tabindex="0"><table><thead><tr><th scope="col">账号</th><th>姓名</th><th>角色</th><th>社团</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows.map(r => `<tr data-role="${r.role}" data-search="${esc((r.username + ' ' + r.name).toLowerCase())}"><td>${esc(r.username)}</td><td>${esc(r.name)}</td><td>${roles[r.role]}</td><td>${esc(clubs.find(c => c.id === r.clubId)?.name || '—')}</td><td>${r.enabled ? '正常' : '已禁用'}</td><td><button class="button outline" data-enable="${r.id}" data-name="${esc(r.username)}" data-value="${!r.enabled}" aria-label="${r.enabled ? '禁用' : '启用'}账号 ${esc(r.username)}" ${r.id === user.id ? 'disabled' : ''}>${r.enabled ? '禁用' : '启用'}</button></td></tr>`).join('')}</tbody></table></div>`;
    }
    if (version === renderVersion) { root.innerHTML = html; if (tab === 'discover') filterActivities(); }
  } catch (error) {
    if (version === renderVersion) root.innerHTML = empty(esc(error.message));
  }
}
function filterActivities() {
  const day = $('#activity-date')?.value || '', category = $('#activity-category')?.value || '';
  let visible = 0;
  document.querySelectorAll('#workspace .grid article.card').forEach((element, index) => {
    element.hidden = Boolean((day && !events[index].activity.startsAt.startsWith(day)) || (category && events[index].activity.category !== category));
    if (!element.hidden) visible++;
  });
  const summary = $('#filter-summary');
  if (summary) summary.textContent = visible ? `显示 ${visible} 场活动` : '没有符合条件的活动，请调整搜索或筛选条件。';
}
function showDetail() { const heading = $('#detail-content h2'); if (heading) heading.id = 'detail-title'; $('#detail-dialog').showModal(); }
function openLogin() { $('#login-error').textContent = ''; $('#login-dialog').showModal(); }
function details(id) {
  const v = events.find(v => v.activity.id === id); if (!v) return;
  const a = v.activity;
  $('#detail-content').innerHTML = `${badge(a.status)}<h2>${esc(a.title)}</h2><p class="muted">${esc(v.clubName)}</p><p class="detail-description">${esc(a.description)}</p><p>地点：${esc(a.location)}<br>参加条件：${esc(a.requirements)}<br>开始时间：${date(a.startsAt)}<br>报名截止：${date(a.deadline)}<br>人数：${v.registeredCount} / ${a.capacity}</p>`;
  if (a.status === 'APPROVED') $('#detail-content').insertAdjacentHTML('beforeend', user?.role === 'STUDENT' ? `<button class="button dark" data-register="${esc(a.id)}">报名参加</button>` : !user ? '<button class="button dark" data-login>登录后报名</button>' : '');
  showDetail();
}
let posterActivity = null;
function shareActivity(id) {
  const view = events.find(v => v.activity.id === id && v.activity.status === 'APPROVED');
  if (!view) return;
  posterActivity = view;
  const url = new URL('/', location.origin); url.searchParams.set('activity', id);
  $('#detail-content').innerHTML = `<h2>分享活动</h2><p>${esc(view.activity.title)}</p><p>活动链接：<a href="${esc(url.href)}">${esc(url.href)}</a></p><p>二维码使用当前网站地址；本机地址只能在本机访问。</p><div id="share-qr" aria-label="活动链接二维码"></div><button id="poster-download" class="button dark">下载活动海报</button>`;
  new QRCode($('#share-qr'), { text: url.href, width: 180, height: 180, correctLevel: QRCode.CorrectLevel.M });
  showDetail();
}
function downloadPoster() {
  if (!posterActivity) return;
  const a = posterActivity.activity, canvas = document.createElement('canvas'); canvas.width = 900; canvas.height = 1200;
  const ctx = canvas.getContext('2d'); ctx.fillStyle = '#f3f6e8'; ctx.fillRect(0, 0, 900, 1200);
  ctx.fillStyle = '#203b2d'; ctx.font = 'bold 54px sans-serif';
  const wrap = (text, x, y, width, lineHeight) => { let line = ''; for (const ch of text) { const next = line + ch; if (ctx.measureText(next).width > width && line) { ctx.fillText(line, x, y); y += lineHeight; line = ch; } else line = next; } ctx.fillText(line, x, y); return y + lineHeight; };
  let y = wrap(a.title, 70, 130, 760, 75); ctx.font = '30px sans-serif';
  for (const line of [posterActivity.clubName + ' · ' + a.category, '时间：' + a.startsAt.replace('T', ' '), '地点：' + a.location, '报名截止：' + a.deadline.replace('T', ' ')]) y = wrap(line, 70, y + 25, 760, 45);
  ctx.fillStyle = '#fff'; ctx.fillRect(70, 780, 270, 270); const qr = $('#share-qr canvas');
  if (!qr) { toast('二维码尚未生成，请重试'); return; }
  ctx.drawImage(qr, 90, 800, 230, 230); ctx.fillStyle = '#203b2d'; ctx.font = '28px sans-serif'; ctx.fillText('扫码查看活动', 380, 930);
  const link = document.createElement('a'); link.download = 'activity-poster.png'; link.href = canvas.toDataURL('image/png'); link.click();
}
document.addEventListener('click', async event => {
  const button = event.target.closest('button'); if (!button) return;
  const d = button.dataset;
  if (d.tab) { tab = d.tab; await render(); $('#workspace h2')?.focus(); return; }
  if ('login' in d || button.id === 'login-open') { if ($('#detail-dialog').open) $('#detail-dialog').close(); openLogin(); return; }
  if (button.id === 'login-close') { $('#login-dialog').close(); return; }
  if (button.id === 'detail-close') { $('#detail-dialog').close(); return; }
  if (d.demo) { $('#login-form').elements.username.value = d.demo; $('#login-form').elements.password.value = 'demo1234'; return; }
  if (d.detail) { details(d.detail); return; }
  if (d.editPost) { const item = postRows.find(v => v.post.id === d.editPost); if (item) { const form = $('#post-form'); form.dataset.id = item.post.id; form.elements.title.value = item.post.title; form.elements.content.value = item.post.content; form.querySelector('h3').textContent = '编辑公告并重新送审'; form.querySelector('[data-cancel-edit]').hidden = false; form.scrollIntoView(); form.elements.title.focus(); } return; }
  if (d.share) { shareActivity(d.share); return; }
  if (d.cancelEdit) { await render(); $('#workspace h2')?.focus(); return; }
  if (d.history) {
    try { const rows = await api(`/activities/${d.history}/history`); $('#detail-content').innerHTML = `<h2>活动修改记录</h2>` + (rows.length ? rows.map(r => `<p>${date(r.createdAt)}：${esc(r.summary)}</p>`).join('') : '<p>暂无修改记录</p>'); showDetail(); } catch (error) { toast(error.message); } return;
  }
  if (d.read) { try { await api(`/messages/${d.read}/read`, { method: 'PATCH' }); await render(); } catch (error) { toast(error.message); } return; }
  if (button.id === 'poster-download') { downloadPoster(); return; }
  if (d.edit) { const found = events.find(v => v.activity.id === d.edit); if (found) { $('#workspace').innerHTML = activityForm(found.activity); $('#workspace h2')?.focus(); } return; }
  if (!(d.register || d.cancel || d.participants || d.adminParticipants || d.enable || button.id === 'logout')) return;
  button.disabled = true;
  try {
    if (button.id === 'logout') { await api('/auth/logout', { method: 'POST' }); user = null; tab = 'discover'; }
    if (d.register) { await api(`/activities/${d.register}/registrations`, { method: 'POST' }); if ($('#detail-dialog').open) $('#detail-dialog').close(); toast('报名成功，可以在“我的报名”中查看'); }
    if (d.cancel) {
      if (!confirm('确认取消此活动？已报名学生将看到“已取消”状态。')) return;
      await api(`/club/activities/${d.cancel}/cancel`, { method: 'POST' }); toast('活动已取消');
    }
    if (d.enable) { if (!confirm(`确认${d.value === 'true' ? '启用' : '禁用'}账号 ${d.name}？`)) return; await api(`/admin/users/${d.enable}/enabled`, { method: 'PATCH', body: { enabled: d.value === 'true' } }); toast('账号状态已更新'); }
    if (d.participants || d.adminParticipants) {
      const rows = await api(d.adminParticipants ? `/admin/activities/${d.adminParticipants}/participants` : `/club/activities/${d.participants}/participants`);
      $('#detail-content').innerHTML = `<h2>报名名单</h2>${rows.length ? `<ul>${rows.map(r => `<li>${esc(r.student.name)}（${esc(r.student.username)}）<br><span class="muted">${date(r.registeredAt)}</span></li>`).join('')}</ul>` : '<p class="muted">暂时没有学生报名。</p>'}`;
      showDetail(); return;
    }
    await render();
  } catch (error) { toast(error.message); }
  finally { button.disabled = false; }
});
function filterAccounts() {
  const role = $('#account-filter')?.value || 'all', query = ($('#account-search')?.value || '').trim().toLowerCase();
  let visible = 0;
  document.querySelectorAll('tr[data-role]').forEach(row => {
    const matchRole = role === 'all' || (role === 'students' ? ['STUDENT', 'CLUB_STAFF'].includes(row.dataset.role) : row.dataset.role === role);
    row.hidden = !matchRole || !row.dataset.search.includes(query);
    if (!row.hidden) visible++;
  });
  if ($('#account-summary')) $('#account-summary').textContent = visible ? `显示 ${visible} 个账号` : '没有符合条件的账号，请调整搜索或分类。';
}
document.addEventListener('input', event => { if (event.target.id === 'account-search') filterAccounts(); });
document.addEventListener('change', event => {
  if (['activity-date', 'activity-category'].includes(event.target.id)) filterActivities();
  if (event.target.id === 'account-filter') filterAccounts();
  if (event.target.id === 'new-role') $('#club-field').hidden = event.target.value !== 'CLUB_STAFF';
});
document.addEventListener('submit', async event => {
  const form = event.target; event.preventDefault();
  const data = Object.fromEntries(new FormData(form));
  if (form.id === 'search-form') { const day = $('#activity-date').value, category = $('#activity-category').value; await render(data.q); if ($('#activity-date')) { $('#activity-date').value = day; $('#activity-category').value = category; filterActivities(); } return; }
  const submit = event.submitter;
  if (form.matches('.review-form, .post-review-form, .club-review-form') && submit?.value === 'reject' && !data.comment.trim()) { toast('驳回时请填写审核意见'); form.elements.comment.focus(); return; }
  if (submit) submit.disabled = true;
  try {
    if (form.id === 'login-form') {
      user = await api('/auth/login', { method: 'POST', body: data });
      $('#login-dialog').close();
      tab = ({ STUDENT: 'discover', CLUB_STAFF: 'club', TEACHER: 'review', ADMIN: 'accounts' })[user.role];
    } else if (form.id === 'activity-form') {
      data.capacity = Number(data.capacity);
      await api(form.dataset.id ? `/club/activities/${form.dataset.id}` : '/club/activities', { method: form.dataset.id ? 'PUT' : 'POST', body: data });
      tab = 'club'; toast('活动已提交，等待老师审核');
    } else if (form.id === 'post-form') {
      await api(form.dataset.id ? `/club/posts/${form.dataset.id}` : '/club/posts', { method: form.dataset.id ? 'PUT' : 'POST', body: data }); toast('公告已提交教师审核');
    } else if (form.classList.contains('post-review-form')) {
      const approved = submit?.value === 'approve';
      await api(`/teacher/posts/${form.dataset.id}/review`, { method: 'POST', body: { approved, comment: data.comment } }); toast(approved ? '公告已通过并发布' : '公告已驳回');
    } else if (form.classList.contains('reminder-form')) {
      await api(`/student/registrations/${form.dataset.id}/reminder`, { method: 'PATCH', body: { minutesBefore: data.minutesBefore ? Number(data.minutesBefore) : null } }); toast('提醒设置已保存');
    } else if (form.id === 'club-application-form') {
      await api('/club/applications', { method: 'POST', body: data }); toast('社团申请已提交');
    } else if (form.classList.contains('club-review-form')) {
      const approved = submit?.value === 'approve';
      await api(`/teacher/club-applications/${form.dataset.id}/review`, { method: 'POST', body: { approved, comment: data.comment } });
      clubs = await api('/clubs'); toast(approved ? '社团申请已通过' : '社团申请已驳回');
    } else if (form.classList.contains('end-form')) {
      await api(`/teacher/activities/${form.dataset.id}/end-time`, { method: 'PATCH', body: data });
      toast('活动结束时间已更新');
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
  const shareId = new URL(location.href).searchParams.get('activity');
  if (shareId) { if (events.some(v => v.activity.id === shareId)) details(shareId); else toast('活动不存在、未公开或已取消'); }
})();
