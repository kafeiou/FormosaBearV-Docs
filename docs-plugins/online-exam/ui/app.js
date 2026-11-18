(() => {
  const pathMatch = window.location.pathname.match(/^(.*\/plugin\/[^/]+)\/assets\//);
  const ACTION_BASE = pathMatch ? `${pathMatch[1]}/action` : '../../action';
  const appRootMatch = window.location.pathname.match(/^(.*)\/api\/plugin\/[^/]+\/assets\//);
  const APP_ROOT = appRootMatch ? (appRootMatch[1] || '') : '';
  const urlParams = new URLSearchParams(window.location.search);
  const currentLang = urlParams.get('lang') || 'zh_TW';

  function buildDocumentViewUrl(docId) {
    return `${APP_ROOT}/#/document/view/${encodeURIComponent(docId)}`;
  }

  function renderRefDocsLinksHtml(docs, labelKey) {
    if (!Array.isArray(docs) || !docs.length) return '';
    const links = docs.map(d =>
      `<a class="ref-doc-link" href="${buildDocumentViewUrl(d.id)}" target="_blank" rel="noopener">📄 ${escapeHtml(d.title || d.id)}</a>`
    ).join('');
    return `<div class="ref-docs-box"><span>${escapeHtml(t(labelKey))}</span>${links}</div>`;
  }

  let i18nDict = {};
  let fallbackDict = {};
  let state = {
    isTopAdmin: false,
    isTeacher: false,
    user: null,
    teacherGroups: [],
    exams: [],
    availableRefDocs: [],
    selectedCreateRefDocIds: new Set(),
    currentExam: null,
    currentAttempt: null
  };

  function resolveKey(obj, keyPath) {
    return keyPath.split('.').reduce((acc, k) => (acc && acc[k] !== undefined ? acc[k] : undefined), obj);
  }

  function t(key, params = {}) {
    let str = resolveKey(i18nDict, key);
    if (str === undefined) {
      str = resolveKey(fallbackDict, key);
    }
    if (typeof str !== 'string') {
      return key;
    }
    return str.replace(/\{\{(\w+)\}\}/g, (_, k) => (params[k] !== undefined ? params[k] : ''));
  }

  async function loadLocales() {
    try {
      const enRes = await fetch('../locale/en.json', { credentials: 'same-origin' });
      if (enRes.ok) fallbackDict = await enRes.json();
    } catch (e) {}
    if (currentLang === 'en') {
      i18nDict = fallbackDict;
    } else {
      try {
        const langRes = await fetch(`../locale/${encodeURIComponent(currentLang)}.json`, { credentials: 'same-origin' });
        i18nDict = langRes.ok ? await langRes.json() : fallbackDict;
      } catch (e) {
        i18nDict = fallbackDict;
      }
    }
    document.querySelectorAll('[data-i18n]').forEach(el => {
      el.textContent = t(el.getAttribute('data-i18n'));
    });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      el.setAttribute('placeholder', t(el.getAttribute('data-i18n-placeholder')));
    });
  }

  function formatApiError(data, status, statusText) {
    if (data && data.type) {
      const mapped = resolveKey(i18nDict, `error.${data.type}`) || resolveKey(fallbackDict, `error.${data.type}`);
      if (typeof mapped === 'string') {
        return t(`error.${data.type}`, data);
      }
    }
    return (data && (data.message || data.type)) || `HTTP ${status} (${statusText || t('error.default')})`;
  }

  async function api(action, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }
    const res = await fetch(`${ACTION_BASE}/${action}`, {
      credentials: 'same-origin',
      ...options,
      headers
    });
    const text = await res.text();
    let data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch (e) {
      data = {};
    }
    if (!res.ok) {
      const err = new Error(formatApiError(data, res.status, res.statusText));
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  function showAlert(msg, type = 'info') {
    const box = document.getElementById('alertBox');
    box.textContent = msg;
    box.className = `alert ${type}`;
    box.classList.remove('hidden');
    setTimeout(() => box.classList.add('hidden'), 5000);
  }

  function switchTab(tabName) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    document.querySelectorAll('.tab-panel').forEach(panel => {
      panel.classList.toggle('active', panel.id === `tab-${tabName}`);
    });
  }

  function initDefaultDateRange() {
    const toDate = new Date();
    const fromDate = new Date(toDate.getTime() - 30 * 24 * 60 * 60 * 1000);
    const fmt = d => d.toISOString().slice(0, 10);
    document.getElementById('summaryFromDate').value = fmt(fromDate);
    document.getElementById('summaryToDate').value = fmt(toDate);
  }

  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      switchTab(btn.dataset.tab);
      if (btn.dataset.tab === 'scores') {
        loadScores();
      } else if (btn.dataset.tab === 'exams' || btn.dataset.tab === 'manage') {
        loadExams();
        if (btn.dataset.tab === 'manage' && (state.isTeacher || state.isTopAdmin)) {
          loadCreateRefDocs();
        }
      }
    });
  });

  document.getElementById('backToListBtn').addEventListener('click', () => {
    switchTab('exams');
    loadExams();
  });

  async function init() {
    await loadLocales();
    initDefaultDateRange();
    try {
      const info = await api('info');
      state.isTopAdmin = !!info.is_top_admin;
      state.isTeacher = !!info.is_teacher;
      state.user = info;
      state.teacherGroups = info.teacher_groups || [];

      const roleSuffix = state.isTopAdmin ? t('header.admin_suffix') : (state.isTeacher ? t('header.teacher_suffix') : '');
      document.getElementById('userBadge').textContent = `${info.display_name || info.username}${roleSuffix}`;

      if (state.isTeacher || state.isTopAdmin) {
        document.querySelectorAll('.teacher-only').forEach(el => el.classList.remove('hidden'));
      }
      if (state.isTopAdmin) {
        document.querySelectorAll('.top-admin-only').forEach(el => el.classList.remove('hidden'));
      }

      if (state.teacherGroups.length > 0) {
        const groupSelect = document.getElementById('examOwnerGroupId');
        groupSelect.innerHTML = '';
        state.teacherGroups.forEach(g => {
          const opt = document.createElement('option');
          opt.value = g.id;
          opt.textContent = g.name;
          groupSelect.appendChild(opt);
        });
        if (state.teacherGroups.length > 1) {
          document.getElementById('ownerGroupRow').classList.remove('hidden');
        }
        await loadCreateRefDocs();
      }

      await loadExams();
    } catch (e) {
      document.getElementById('userBadge').textContent = t('header.load_failed');
      showAlert(e.message, 'error');
    }
  }

  async function loadExams() {
    const data = await api(`exams?lang=${encodeURIComponent(currentLang)}`);
    state.exams = data.exams || [];
    renderMyExams();
    renderSummaryExamFilter();
    if (state.isTeacher || state.isTopAdmin) {
      renderManageExams();
    }
  }

  function renderSummaryExamFilter() {
    const select = document.getElementById('summaryExamId');
    if (!select) return;
    const prev = select.value;
    select.innerHTML = `<option value="">${escapeHtml(t('scores.all_exams'))}</option>`;
    state.exams.forEach(exam => {
      const opt = document.createElement('option');
      opt.value = exam.id;
      opt.textContent = `${exam.exam_code} v${exam.exam_version} - ${exam.title}`;
      select.appendChild(opt);
    });
    if (prev) select.value = prev;
  }

  function renderMyExams() {
    const grid = document.getElementById('examGrid');
    grid.innerHTML = '';
    const takeableExams = state.exams.filter(exam => (exam.can_take && exam.enabled) || exam.my_attempt);
    if (!takeableExams.length) {
      grid.innerHTML = `<div class="panel-card">${escapeHtml(t('exams.empty'))}</div>`;
      return;
    }

    takeableExams.forEach(exam => {
      const card = document.createElement('div');
      card.className = 'exam-card';
      const attempt = exam.my_attempt;
      const canTake = exam.enabled && exam.can_take && (!attempt || exam.retake_allowed);
      const totalScore = exam.total_score || 100;
      const takeLabel = canTake ? (attempt ? t('exams.retake_exam') : t('exams.start_exam')) : t('exams.view_result');
      const refDocsHtml = renderRefDocsLinksHtml(exam.reference_documents, 'exams.ref_docs_label');
      card.innerHTML = `
        <h3>${escapeHtml(exam.title)}</h3>
        <div class="meta-row">
          <span class="badge primary">${escapeHtml(exam.exam_code)} v${escapeHtml(exam.exam_version)}</span>
          <span class="badge">${escapeHtml(exam.owner_label || '')}</span>
          <span class="badge">${escapeHtml(t('exams.questions_total', { count: exam.question_count, score: totalScore }))}</span>
          <span class="badge ${exam.retake_allowed ? 'success' : ''}">${escapeHtml(exam.retake_allowed ? t('exams.retake_allowed') : t('exams.single_attempt'))}</span>
        </div>
        ${refDocsHtml}
        ${attempt ? `<p>${escapeHtml(t('exams.latest_score'))} <strong>${attempt.score} / ${attempt.max_score}</strong></p>` : `<p class="subtitle">${escapeHtml(t('exams.not_taken'))}</p>`}
        <div class="card-actions">
          ${(exam.enabled && exam.can_take) || attempt ? `<button class="btn btn-primary btn-take">${escapeHtml(takeLabel)}</button>` : ''}
          <a class="btn btn-secondary" href="${ACTION_BASE}/export_blank_pdf?examId=${encodeURIComponent(exam.id)}&lang=${encodeURIComponent(currentLang)}" target="_blank">${escapeHtml(t('exams.blank_pdf'))}</a>
          ${attempt ? `<a class="btn btn-secondary" href="${ACTION_BASE}/export_attempt_pdf?examId=${encodeURIComponent(exam.id)}&lang=${encodeURIComponent(currentLang)}" target="_blank">${escapeHtml(t('exams.attempt_pdf'))}</a>` : ''}
        </div>
      `;
      const takeBtn = card.querySelector('.btn-take');
      if (takeBtn) {
        takeBtn.addEventListener('click', () => openExam(exam.id));
      }
      grid.appendChild(card);
    });
  }

  function renderManageExams() {
    const tbody = document.getElementById('manageExamsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    const managedExams = state.exams.filter(exam => exam.can_manage);
    if (!managedExams.length) {
      tbody.innerHTML = `<tr><td colspan="7">${escapeHtml(t('manage.empty'))}</td></tr>`;
      return;
    }

    managedExams.forEach(exam => {
      const tr = document.createElement('tr');
      const totalScore = exam.total_score || 100;
      const attemptCount = exam.attempt_count || 0;
      const refDocsBadges = Array.isArray(exam.reference_documents) && exam.reference_documents.length
        ? `<div style="margin-top:4px; display:flex; flex-wrap:wrap; gap:4px;">` +
          exam.reference_documents.map(d => `<a class="ref-doc-link" href="${buildDocumentViewUrl(d.id)}" target="_blank" rel="noopener">📄 ${escapeHtml(d.title || d.id)}</a>`).join('') +
          `</div>`
        : '';
      tr.innerHTML = `
        <td><span class="badge primary">${escapeHtml(exam.exam_code)} v${escapeHtml(exam.exam_version)}</span></td>
        <td><strong>${escapeHtml(exam.title)}</strong>${refDocsBadges}</td>
        <td><span class="badge">${escapeHtml(exam.owner_label || '')}</span></td>
        <td>${escapeHtml(t('manage.q_score_val', { count: exam.question_count, score: totalScore }))}</td>
        <td><span class="badge ${exam.enabled ? 'success' : 'danger'}">${escapeHtml(exam.enabled ? t('manage.status_enabled') : t('manage.status_disabled'))}</span></td>
        <td>${escapeHtml(t('manage.attempts_val', { count: attemptCount }))}</td>
        <td>
          <div style="display:flex; flex-wrap:wrap; gap:6px;">
            <a class="btn btn-secondary btn-sm" href="${ACTION_BASE}/export_blank_pdf?examId=${encodeURIComponent(exam.id)}&lang=${encodeURIComponent(currentLang)}" target="_blank">${escapeHtml(t('manage.btn_blank_pdf'))}</a>
            <button type="button" class="btn btn-secondary btn-sm btn-ref-docs">${escapeHtml(t('manage.btn_ref_docs'))}</button>
            <button type="button" class="btn btn-secondary btn-sm btn-acl">${escapeHtml(t('manage.btn_acl'))}</button>
            <button type="button" class="btn btn-secondary btn-sm btn-toggle">${escapeHtml(exam.enabled ? t('manage.btn_disable') : t('manage.btn_enable'))}</button>
            <button type="button" class="btn btn-danger btn-sm btn-del">${escapeHtml(t('manage.btn_delete'))}</button>
          </div>
        </td>
      `;
      tr.querySelector('.btn-ref-docs').addEventListener('click', () => openRefDocsModal(exam));
      tr.querySelector('.btn-acl').addEventListener('click', () => openAclModal(exam));
      tr.querySelector('.btn-toggle').addEventListener('click', async () => {
        await api('toggle_exam', { method: 'POST', body: JSON.stringify({ id: exam.id, enabled: !exam.enabled }) });
        showAlert(exam.enabled ? t('manage.disabled_success') : t('manage.enabled_success'), 'success');
        await loadExams();
      });
      tr.querySelector('.btn-del').addEventListener('click', async () => {
        if (!confirm(t('manage.confirm_delete', { title: exam.title }))) return;
        try {
          await api('delete_exam', { method: 'POST', body: JSON.stringify({ id: exam.id, force: false }) });
          showAlert(t('manage.deleted_success'), 'success');
          await loadExams();
        } catch (err) {
          if (err.data && err.data.type === 'ExamHasAttempts') {
            if (confirm(err.message)) {
              await api('delete_exam', { method: 'POST', body: JSON.stringify({ id: exam.id, force: true }) });
              showAlert(t('manage.deleted_keep_scores'), 'success');
              await loadExams();
            }
          } else {
            showAlert(err.message, 'error');
          }
        }
      });
      tbody.appendChild(tr);
    });
  }

  document.getElementById('refreshManageExamsBtn').addEventListener('click', loadExams);

  async function openExam(examId) {
    const data = await api(`take_exam?examId=${encodeURIComponent(examId)}&lang=${encodeURIComponent(currentLang)}`);
    state.currentExam = data.exam;
    state.currentAttempt = data.my_attempt || null;

    const totalScore = state.currentExam.total_score || 100;
    const perQ = state.currentExam.question_count ? (totalScore / state.currentExam.question_count).toFixed(2) : totalScore;
    document.getElementById('runnerTitle').textContent = state.currentExam.title;
    document.getElementById('runnerCodeVersion').textContent = t('runner.meta', {
      code: state.currentExam.exam_code,
      version: state.currentExam.exam_version,
      total: totalScore,
      perQ
    });

    renderRunner(state.currentExam, state.currentAttempt);
    switchTab('runner');
  }

  function renderRunner(exam, attempt) {
    const form = document.getElementById('examForm');
    form.innerHTML = '';
    const locked = !!attempt && !exam.retake_allowed;
    const refBox = document.getElementById('runnerRefDocs');
    const banner = document.getElementById('runnerResultBanner');
    const pdfBtn = document.getElementById('downloadAttemptPdfBtn');
    const submitBtn = document.getElementById('submitExamBtn');

    if (refBox) {
      if (Array.isArray(exam.reference_documents) && exam.reference_documents.length > 0) {
        const links = exam.reference_documents.map(d =>
          `<a class="ref-doc-link" href="${buildDocumentViewUrl(d.id)}" target="_blank" rel="noopener">📄 ${escapeHtml(d.title || d.id)}</a>`
        ).join('');
        refBox.innerHTML = `<span style="font-weight:600;">${escapeHtml(t('runner.ref_docs_label'))}</span>${links}`;
        refBox.classList.remove('hidden');
      } else {
        refBox.innerHTML = '';
        refBox.classList.add('hidden');
      }
    }

    if (attempt) {
      banner.textContent = t('runner.result_banner', { score: attempt.score, maxScore: attempt.max_score });
      banner.classList.remove('hidden');
      pdfBtn.href = `${ACTION_BASE}/export_attempt_pdf?examId=${encodeURIComponent(exam.id)}&lang=${encodeURIComponent(currentLang)}`;
      pdfBtn.classList.remove('hidden');
    } else {
      banner.classList.add('hidden');
      pdfBtn.classList.add('hidden');
    }

    submitBtn.classList.toggle('hidden', locked);

    const prevAnswers = attempt ? (attempt.answers || {}) : {};
    const prevResults = attempt ? (attempt.results || {}) : {};

    (exam.questions || []).forEach((q, idx) => {
      const card = document.createElement('div');
      card.className = 'question-card';
      const res = prevResults[q.id];
      const badgeHtml = res
        ? `<span class="badge ${res.correct ? 'success' : 'danger'}">${escapeHtml(res.correct ? t('runner.correct') : t('runner.incorrect'))}</span>`
        : '';

      const titleDiv = document.createElement('div');
      titleDiv.className = 'question-title';
      titleDiv.innerHTML = `${idx + 1}. ${escapeHtml(q.prompt)} ${badgeHtml}`;
      card.appendChild(titleDiv);

      if (q.type === 'TRUE_FALSE') {
        ['true', 'false'].forEach(val => {
          const label = document.createElement('label');
          label.className = 'choice-item';
          const checked = String(prevAnswers[q.id] || '').toLowerCase() === val ? 'checked' : '';
          label.innerHTML = `<input type="radio" name="${q.id}" value="${val}" ${checked} ${locked ? 'disabled' : ''}> <span>${escapeHtml(val === 'true' ? t('runner.true_label') : t('runner.false_label'))}</span>`;
          card.appendChild(label);
        });
      } else if (q.type === 'SINGLE_CHOICE') {
        (q.choices || []).forEach(c => {
          const label = document.createElement('label');
          label.className = 'choice-item';
          const checked = prevAnswers[q.id] === c.id ? 'checked' : '';
          label.innerHTML = `<input type="radio" name="${q.id}" value="${c.id}" ${checked} ${locked ? 'disabled' : ''}> <span>${escapeHtml(c.text)}</span>`;
          card.appendChild(label);
        });
      } else {
        const selectedArr = Array.isArray(prevAnswers[q.id]) ? prevAnswers[q.id] : [];
        (q.choices || []).forEach(c => {
          const label = document.createElement('label');
          label.className = 'choice-item';
          const checked = selectedArr.includes(c.id) ? 'checked' : '';
          label.innerHTML = `<input type="checkbox" name="${q.id}" value="${c.id}" ${checked} ${locked ? 'disabled' : ''}> <span>${escapeHtml(c.text)}</span>`;
          card.appendChild(label);
        });
      }
      form.appendChild(card);
    });
  }

  document.getElementById('submitExamBtn').addEventListener('click', async () => {
    if (!state.currentExam) return;
    const answers = {};
    const unanswered = [];
    const cards = document.querySelectorAll('#examForm .question-card');
    (state.currentExam.questions || []).forEach((q, idx) => {
      let answered = false;
      if (q.type === 'MULTIPLE_CHOICE') {
        const vals = Array.from(document.querySelectorAll(`input[name="${q.id}"]:checked`)).map(el => el.value);
        answers[q.id] = vals;
        answered = vals.length > 0;
      } else {
        const checked = document.querySelector(`input[name="${q.id}"]:checked`);
        if (checked) {
          answers[q.id] = checked.value;
          answered = true;
        }
      }
      if (cards[idx]) {
        cards[idx].classList.toggle('unanswered', !answered);
      }
      if (!answered) {
        unanswered.push(idx + 1);
      }
    });

    if (unanswered.length > 0) {
      showAlert(t('runner.unanswered_warning', { questions: unanswered.join(', ') }), 'error');
      const firstUnanswered = document.querySelector('#examForm .question-card.unanswered');
      if (firstUnanswered) {
        firstUnanswered.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      return;
    }

    try {
      const res = await api('submit_exam', {
        method: 'POST',
        body: JSON.stringify({ exam_id: state.currentExam.id, answers })
      });
      state.currentAttempt = res.attempt;
      renderRunner(state.currentExam, state.currentAttempt);
      showAlert(t('runner.submitted_success'), 'success');
    } catch (e) {
      showAlert(e.message, 'error');
    }
  });

  async function loadScores() {
    try {
      const data = await api(`attempts?lang=${encodeURIComponent(currentLang)}`);
      const tbody = document.getElementById('scoresTableBody');
      tbody.innerHTML = '';
      (data.attempts || []).forEach(att => {
        const tr = document.createElement('tr');
        const dateStr = att.submitted_at ? new Date(att.submitted_at).toLocaleString() : '-';
        tr.innerHTML = `
          <td>${escapeHtml(att.exam_code)}</td>
          <td>${escapeHtml(att.exam_version)}</td>
          <td>${escapeHtml(att.exam_title)}</td>
          <td>${escapeHtml(att.owner_label || '')}</td>
          <td>${escapeHtml(att.display_name || att.username)} (${escapeHtml(att.username)})</td>
          <td><strong>${att.score} / ${att.max_score}</strong></td>
          <td>${escapeHtml(dateStr)}</td>
          <td><a class="btn btn-secondary btn-sm" href="${ACTION_BASE}/export_attempt_pdf?examId=${encodeURIComponent(att.exam_id)}&userId=${encodeURIComponent(att.user_id)}&lang=${encodeURIComponent(currentLang)}" target="_blank">${escapeHtml(t('scores.download_pdf'))}</a></td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      showAlert(e.message, 'error');
    }
  }

  document.getElementById('refreshScoresBtn').addEventListener('click', loadScores);

  document.getElementById('exportSummaryPdfBtn').addEventListener('click', () => {
    const fromDate = document.getElementById('summaryFromDate').value;
    const toDate = document.getElementById('summaryToDate').value;
    const examId = document.getElementById('summaryExamId').value;
    if (!fromDate || !toDate) {
      showAlert(t('error.ValidationError'), 'error');
      return;
    }
    const url = `${ACTION_BASE}/export_summary_pdf?from=${encodeURIComponent(fromDate)}&to=${encodeURIComponent(toDate)}&examId=${encodeURIComponent(examId)}&lang=${encodeURIComponent(currentLang)}`;
    window.open(url, '_blank');
  });

  document.getElementById('giftFileInput').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      document.getElementById('giftTextarea').value = reader.result;
    };
    reader.readAsText(file, 'UTF-8');
  });

  document.getElementById('previewGiftBtn').addEventListener('click', async () => {
    try {
      const gift = document.getElementById('giftTextarea').value;
      const data = await api('parse_gift', { method: 'POST', body: JSON.stringify({ gift }) });
      const box = document.getElementById('giftPreviewBox');
      box.innerHTML = `<strong>${escapeHtml(t('manage.preview_header', { count: data.count }))}</strong><br>` +
        data.questions.map((q, i) => `${i + 1}. [${q.type}] ${escapeHtml(q.prompt)}`).join('<br>');
      box.classList.remove('hidden');
    } catch (e) {
      showAlert(e.message, 'error');
    }
  });

  document.getElementById('saveExamBtn').addEventListener('click', async () => {
    try {
      const ownerGroupId = state.teacherGroups.length === 1
        ? state.teacherGroups[0].id
        : (document.getElementById('examOwnerGroupId') ? document.getElementById('examOwnerGroupId').value : '');
      const payload = {
        title: document.getElementById('examTitle').value.trim(),
        exam_code: document.getElementById('examCode').value.trim(),
        exam_version: document.getElementById('examVersion').value.trim(),
        total_score: Number(document.getElementById('examTotalScore').value) || 100,
        owner_group_id: ownerGroupId,
        retake_allowed: document.getElementById('examRetakeAllowed').checked,
        enabled: document.getElementById('examEnabled').checked,
        reference_document_ids: Array.from(state.selectedCreateRefDocIds),
        gift: document.getElementById('giftTextarea').value
      };
      if (!payload.title || !payload.exam_code || !payload.exam_version || !payload.gift.trim()) {
        showAlert(t('error.ValidationError'), 'error');
        return;
      }
      await api('save_exam', { method: 'POST', body: JSON.stringify(payload) });
      showAlert(t('manage.saved_success'), 'success');
      document.getElementById('examTitle').value = '';
      document.getElementById('examCode').value = '';
      document.getElementById('examVersion').value = '';
      document.getElementById('examTotalScore').value = '100';
      document.getElementById('examEnabled').checked = true;
      document.getElementById('giftTextarea').value = '';
      document.getElementById('giftPreviewBox').classList.add('hidden');
      state.selectedCreateRefDocIds.clear();
      renderCreateRefDocsChecklist();
      await loadExams();
    } catch (e) {
      showAlert(e.message, 'error');
    }
  });

  function renderChecklistModal(title, data, onToggle) {
    document.getElementById('aclModalTitle').textContent = title;
    const usersBox = document.getElementById('aclUsersList');
    usersBox.innerHTML = '';
    (data.users || []).forEach(u => {
      const label = document.createElement('label');
      label.className = 'acl-item';
      label.innerHTML = `<input type="checkbox" ${u.allowed ? 'checked' : ''}> <span>${escapeHtml(u.name || u.username)} (${escapeHtml(u.username)})</span>`;
      label.querySelector('input').addEventListener('change', e => onToggle('USER', u.id, e));
      usersBox.appendChild(label);
    });

    const groupsBox = document.getElementById('aclGroupsList');
    groupsBox.innerHTML = '';
    (data.groups || []).forEach(g => {
      const label = document.createElement('label');
      label.className = 'acl-item';
      label.innerHTML = `<input type="checkbox" ${g.allowed ? 'checked' : ''}> <span>${escapeHtml(g.name)}</span>`;
      label.querySelector('input').addEventListener('change', e => onToggle('GROUP', g.id, e));
      groupsBox.appendChild(label);
    });
    document.getElementById('aclModal').classList.remove('hidden');
  }

  async function openAclModal(exam) {
    try {
      const data = await api(`list_acl_targets?examId=${encodeURIComponent(exam.id)}`);
      renderChecklistModal(t('acl.modal_title', { title: exam.title }), data, async (targetType, targetId, e) => {
        try {
          await api('set_exam_acl', {
            method: 'POST',
            body: JSON.stringify({ exam_id: exam.id, target_type: targetType, target_id: targetId, allow: e.target.checked })
          });
        } catch (err) {
          e.target.checked = !e.target.checked;
          showAlert(err.message, 'error');
        }
      });
    } catch (e) {
      showAlert(e.message, 'error');
    }
  }

  document.getElementById('openTeachersModalBtn').addEventListener('click', async () => {
    try {
      const data = await api('list_teachers');
      renderChecklistModal(t('acl.teachers_modal_title'), data, async (targetType, targetId, e) => {
        try {
          await api('set_teacher', {
            method: 'POST',
            body: JSON.stringify({ target_type: targetType, target_id: targetId, allow: e.target.checked })
          });
        } catch (err) {
          e.target.checked = !e.target.checked;
          showAlert(err.message, 'error');
        }
      });
    } catch (e) {
      showAlert(e.message, 'error');
    }
  });

  document.getElementById('closeAclModalBtn').addEventListener('click', () => {
    document.getElementById('aclModal').classList.add('hidden');
  });

  async function loadCreateRefDocs() {
    try {
      const data = await api('list_reference_documents');
      state.availableRefDocs = data.documents || [];
      renderCreateRefDocsChecklist();
    } catch (e) {
      // Ignore quietly if not teacher
    }
  }

  function renderCreateRefDocsChecklist() {
    const box = document.getElementById('createRefDocsList');
    if (!box) return;
    const searchEl = document.getElementById('createRefDocsSearch');
    const q = (searchEl ? searchEl.value : '').trim().toLowerCase();
    box.innerHTML = '';
    const filtered = state.availableRefDocs.filter(d => !q || String(d.title || '').toLowerCase().includes(q));
    if (!filtered.length) {
      box.innerHTML = `<div class="subtitle">${escapeHtml(t('manage.ref_docs_empty'))}</div>`;
      return;
    }
    filtered.forEach(d => {
      const label = document.createElement('label');
      label.className = 'acl-item';
      const checked = state.selectedCreateRefDocIds.has(d.id) ? 'checked' : '';
      label.innerHTML = `<input type="checkbox" ${checked}> <span>📄 ${escapeHtml(d.title)}</span>`;
      label.querySelector('input').addEventListener('change', e => {
        if (e.target.checked) {
          state.selectedCreateRefDocIds.add(d.id);
        } else {
          state.selectedCreateRefDocIds.delete(d.id);
        }
      });
      box.appendChild(label);
    });
  }

  const createRefSearchEl = document.getElementById('createRefDocsSearch');
  if (createRefSearchEl) {
    createRefSearchEl.addEventListener('input', renderCreateRefDocsChecklist);
  }

  let modalRefDocsItems = [];
  let modalRefDocsExam = null;

  function renderModalRefDocsChecklist() {
    const box = document.getElementById('modalRefDocsList');
    if (!box || !modalRefDocsExam) return;
    const searchEl = document.getElementById('modalRefDocsSearch');
    const q = (searchEl ? searchEl.value : '').trim().toLowerCase();
    box.innerHTML = '';
    const filtered = modalRefDocsItems.filter(d => !q || String(d.title || '').toLowerCase().includes(q));
    if (!filtered.length) {
      box.innerHTML = `<div class="subtitle">${escapeHtml(t('manage.ref_docs_empty'))}</div>`;
      return;
    }
    filtered.forEach(d => {
      const label = document.createElement('label');
      label.className = 'acl-item';
      label.innerHTML = `<input type="checkbox" ${d.allowed ? 'checked' : ''}> <span>📄 ${escapeHtml(d.title)}</span>`;
      label.querySelector('input').addEventListener('change', async e => {
        try {
          await api('set_exam_reference_documents', {
            method: 'POST',
            body: JSON.stringify({ exam_id: modalRefDocsExam.id, document_id: d.id, allow: e.target.checked })
          });
          d.allowed = e.target.checked;
          await loadExams();
        } catch (err) {
          e.target.checked = !e.target.checked;
          showAlert(err.message, 'error');
        }
      });
      box.appendChild(label);
    });
  }

  async function openRefDocsModal(exam) {
    try {
      modalRefDocsExam = exam;
      const searchEl = document.getElementById('modalRefDocsSearch');
      if (searchEl) searchEl.value = '';
      const data = await api(`list_reference_documents?examId=${encodeURIComponent(exam.id)}`);
      modalRefDocsItems = data.documents || [];
      document.getElementById('refDocsModalTitle').textContent = t('manage.ref_docs_modal_title', { title: exam.title });
      renderModalRefDocsChecklist();
      document.getElementById('refDocsModal').classList.remove('hidden');
    } catch (e) {
      showAlert(e.message, 'error');
    }
  }

  const modalRefSearchEl = document.getElementById('modalRefDocsSearch');
  if (modalRefSearchEl) {
    modalRefSearchEl.addEventListener('input', renderModalRefDocsChecklist);
  }

  const closeRefDocsModalBtn = document.getElementById('closeRefDocsModalBtn');
  if (closeRefDocsModalBtn) {
    closeRefDocsModalBtn.addEventListener('click', () => {
      document.getElementById('refDocsModal').classList.add('hidden');
    });
  }

  function escapeHtml(str) {
    return String(str || '').replace(/[&<>"']/g, s => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[s]));
  }

  init();
})();
