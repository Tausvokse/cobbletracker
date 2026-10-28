(() => {
  const storage = {
    get(key, fallback) { try { return localStorage.getItem(key) || fallback; } catch { return fallback; } },
    set(key, value) { try { localStorage.setItem(key, value); } catch {} }
  };
  let lang = storage.get('ct-lang', 'en');
  if (!window.WIKI[lang]) lang = 'en';
  let theme = storage.get('ct-theme', 'dark');
  if (!['dark', 'light'].includes(theme)) theme = 'dark';
  document.documentElement.dataset.theme = theme;
  const el = id => document.getElementById(id);
  const nav = el('nav'), article = el('article'), search = el('search');
  const sidebar = el('sidebar'), backdrop = el('backdrop');
  const mobile = matchMedia('(max-width: 920px)');
  let timer;
  function announce(text) {
    clearTimeout(timer);
    el('status').textContent = text;
    el('status').classList.add('show');
    timer = setTimeout(() => el('status').classList.remove('show'), 1800);
  }
  function menu(open) {
    sidebar.classList.toggle('open', open);
    sidebar.inert = mobile.matches && !open;
    backdrop.hidden = !open;
    el('menuBtn').setAttribute('aria-expanded', String(open));
  }
  async function copy(text) {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const input = document.createElement('textarea');
        input.value = text;
        input.style.cssText = 'position:fixed;opacity:0;left:0;top:0';
        document.body.appendChild(input);
        input.select();
        const copied = document.execCommand('copy');
        input.remove();
        if (!copied) throw new Error('Clipboard unavailable');
      }
      announce(WIKI[lang].copied);
      return true;
    } catch {
      announce({en:'Select the text and copy it manually.',uk:'Виділи текст і скопіюй вручну.',ru:'Выделите текст и скопируйте вручную.'}[lang]);
      return false;
    }
  }
  function show() {
    const data = WIKI[lang];
    const index = Math.max(0, data.sections.findIndex(s => s.id === location.hash.slice(1)));
    const section = data.sections[index];
    document.title = `${section.title} — CobbleTracker`;
    el('breadcrumb').textContent = section.group;
    const heading = document.createElement('h1');
    heading.textContent = section.title;
    article.replaceChildren(heading);
    const content = document.createElement('div');
    content.className = 'section-content';
    content.innerHTML = section.body;
    article.appendChild(content);
    content.querySelectorAll('pre').forEach(pre => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'copy';
      button.textContent = data.copy;
      button.onclick = async () => {
        if (await copy(pre.querySelector('code').textContent)) button.textContent = data.copied;
      };
      pre.prepend(button);
    });
    if (section.id === 'overview') {
      const labels = {en:['Quick start →','Species JSON →','Project showcase ↗'],uk:['Швидкий старт →','JSON-спавни →','Опис моду ↗'],ru:['Быстрый старт →','JSON-спавны →','Описание мода ↗']}[lang];
      content.querySelectorAll('.hero-links a').forEach((link, i) => link.textContent = labels[i]);
    }
    nav.querySelectorAll('a[data-id]').forEach(link => {
      if (link.dataset.id === section.id) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    el('pager').replaceChildren();
    for (const [neighbor, label, direction] of [[data.sections[index - 1], data.previous, '←'], [data.sections[index + 1], data.next, '→']]) {
      if (!neighbor) continue;
      const link = document.createElement('a');
      link.href = '#' + neighbor.id;
      link.dataset.direction = neighbor.id === data.sections[index + 1]?.id ? 'next' : 'previous';
      const small = document.createElement('small');
      small.textContent = direction + ' ' + label;
      const title = document.createElement('span');
      title.textContent = neighbor.title;
      link.append(small, title);
      el('pager').append(link);
    }
    menu(false);
    window.scrollTo(0, 0);
  }
  function filter() {
    const query = search.value.trim().toLocaleLowerCase(lang);
    let matches = 0;
    nav.querySelectorAll('a[data-id]').forEach(link => {
      const found = !query || link.dataset.search.includes(query);
      link.hidden = !found;
      if (found) matches++;
    });
    nav.querySelectorAll('.nav-group').forEach(group => group.hidden = !Array.from(group.querySelectorAll('a')).some(link => !link.hidden));
    el('noResults').hidden = matches !== 0;
  }
  function render() {
    const data = WIKI[lang];
    document.documentElement.lang = lang;
    el('brandSub').textContent = data.subtitle;
    search.placeholder = data.searchPlaceholder;
    search.setAttribute('aria-label', data.searchPlaceholder);
    el('noResults').textContent = data.noResults;
    el('menuBtn').setAttribute('aria-label', data.menu);
    el('themeBtn').setAttribute('aria-label', data.theme);
    el('linkBtn').textContent = data.copyLink;
    el('updated').textContent = data.updated;
    document.querySelectorAll('[data-lang]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.lang === lang)));
    nav.replaceChildren();
    let group, groupName;
    data.sections.forEach(section => {
      if (section.group !== groupName) {
        groupName = section.group;
        group = document.createElement('div');
        group.className = 'nav-group';
        const title = document.createElement('h2');
        title.textContent = groupName;
        group.appendChild(title);
        nav.appendChild(group);
      }
      const link = document.createElement('a');
      link.href = '#' + section.id;
      link.dataset.id = section.id;
      const plain = document.createElement('div');
      plain.innerHTML = section.body;
      link.dataset.search = (section.title + ' ' + plain.textContent).toLocaleLowerCase(lang);
      link.textContent = section.title;
      group.appendChild(link);
    });
    show();
    filter();
  }
  document.querySelectorAll('[data-lang]').forEach(button => button.onclick = () => {
    lang = button.dataset.lang;
    storage.set('ct-lang', lang);
    render();
  });
  el('themeBtn').onclick = () => {
    theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    storage.set('ct-theme', theme);
  };
  el('menuBtn').onclick = () => menu(!sidebar.classList.contains('open'));
  backdrop.onclick = () => menu(false);
  el('linkBtn').onclick = () => copy(location.href);
  document.querySelector('.skip').onclick = event => {
    event.preventDefault();
    el('main').focus();
  };
  mobile.addEventListener('change', () => menu(false));
  search.addEventListener('input', filter);
  window.addEventListener('hashchange', show);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') menu(false);
    if (event.key === '/' && !event.ctrlKey && !event.metaKey && !/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName) && !document.activeElement.isContentEditable) {
      event.preventDefault();
      if (matchMedia('(max-width: 920px)').matches) menu(true);
      search.focus();
    }
  });
  render();
})();
