// Hafnium site: interactive ClickGUI demo, live release info, feature and roadmap lists.
"use strict";

const REPO = "thebestprogrammeronearth/hafnium-client";
// Optional donation link (GitHub Sponsors, Ko-fi, ...). Leave empty to hide the button.
const DONATE_URL = "";

// Lucide-style 24x24 line icons (same shapes as the in-game GUI).
const ICONS = {
  swords: '<path d="M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6m-3 3 4 4m-1 1 2-2M14.5 6.5 18 3h3v3l-3.5 3.5M5 14l4 4m-2-1-3 3m-1-1 2 2"/>',
  runner: '<circle cx="14.5" cy="4.2" r="2.1"/><path d="m13 8.5-2.5 5.5M6 11l3-2.5h4l3 3h3.5M10.5 14l3.5 3-1 4.5M10.5 14l-2 3.5-4 1"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  user: '<circle cx="12" cy="7.5" r="4"/><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/>',
  globe: '<circle cx="12" cy="12" r="9.5"/><path d="M2.5 12h19M12 2.5c2.5 2.6 3.8 5.8 3.8 9.5s-1.3 6.9-3.8 9.5c-2.5-2.6-3.8-5.8-3.8-9.5S9.5 5.1 12 2.5Z"/>',
  bot: '<rect x="4" y="8" width="16" height="12" rx="2.5"/><path d="M12 8V4.5M9 13v1.5m6-1.5v1.5M2 13v2m20-2v2"/><circle cx="12" cy="3.6" r="1.1"/>',
  folder: '<path d="M2 5v13a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.7-.9l-.8-1.2A2 2 0 0 0 7.9 3H4a2 2 0 0 0-2 2Z"/>',
  chev: '<path d="m6 9 6 6 6-6"/>',
  chevR: '<path d="m9 18 6-6-6-6"/>',
  gem: '<path d="M6 3h12l4 6-10 12L2 9Z"/><path d="M2 9h20M12 21 8 9l4-6 4 6Z"/>',
  wheel: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3"/><path d="M12 3v6m0 6v6M3 12h6m6 0h6"/>',
  coins: '<circle cx="8" cy="8" r="6"/><path d="M18.1 10.4A6 6 0 1 1 10.3 18"/><path d="M7 6h1v4m8.7 3.9.7.7-2.8 2.8"/>',
  music: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
  route: '<circle cx="6" cy="19" r="3"/><path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"/><circle cx="18" cy="5" r="3"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/>',
};
const svg = (name, cls = "") => `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;

// The real module list. live = works in the current release; settings = opens settings instead of toggling.
const S = (name, info) => ({ name, info, live: true, settings: true });
const L = (name, info) => ({ name, info, live: true });
const P = (name) => ({ name, live: false });
const CATEGORIES = [
  { id: "combat", title: "COMBAT", icon: "swords", mods: ["AutoTotem", "AutoCrystal", "KillAura", "AimAssist", "Surround", "AntiBot", "Criticals", "HitBox", "ShieldBreaker", "AnchorMacro", "MaceSwap"].map(P) },
  { id: "movement", title: "MOVEMENT", icon: "runner", mods: ["ElytraFly", "Flight", "Speed", "Strafe", "LongJump", "NoSlow", "Jesus", "Step", "Velocity", "Sprint"].map(P) },
  { id: "render", title: "RENDER", icon: "eye", mods: ["ESP", "Tracers", "NameTags", "Chams", "StorageESP", "BlockESP", "Fullbright", "HUD", "Animations", "Crosshair"].map(P) },
  { id: "player", title: "PLAYER", icon: "user", mods: ["AutoEat", "AutoTool", "InventoryMove", "NoFall", "FastPlace", "AntiAFK", "Respawn", "Scaffold", "Timer"].map(P) },
  { id: "misc", title: "MISC", icon: "grid", mods: [
    L("Donut Wheel", "Radial menu of your own commands and waypoints. Bind “Open wheel”, click to run, drag to reorder."),
    L("Auto Sell", "Sells whitelisted items with your server's own sell command. Protected gear is never sold automatically."),
    ...["MiddleClickPearl", "AutoReconnect", "Notifications", "DiscordRPC", "Hitsounds", "PingSpoof", "SafeWalk", "FakePlayer"].map(P)] },
  { id: "world", title: "WORLD", icon: "globe", mods: [
    S("Observation", "Memory limits for the world model: what Hafnium remembers about blocks, chunks and entities."),
    L("Ancient Debris Finder", "Outlines, tracers and waypoints for ancient debris your game already received."),
    L("Navigation", "Walking routes that avoid hazards. Preview first, then start explicitly.")] },
  { id: "automation", title: "AUTOMATION", icon: "bot", mods: [
    S("Operating Mode", "Manual, Assist or Autonomous. Resets to Manual on every join; End stops everything.")] },
  { id: "client", title: "CLIENT", icon: "folder", mods: [
    L("SpotifyHUD", "Track, artist, artwork and progress from Spotify's Linux desktop session."),
    P("HUD"), S("ClickGUI", "Accent colour, opacity and blur of this menu."), P("Config"), P("Themes"),
    S("Fonts", "Inter text, pixel-snapped at every GUI scale."), P("Sounds"),
    L("Watermark", "The Hf/72 logo on your HUD."),
    L("Capabilities", "HUD panel showing what Hafnium may do on this server, and why."),
    S("Server Profile", "Record each server's rules once; prohibited actions stay off there.")] },
];

const FEATURES = [
  { icon: "gem", cat: "World", t: "Ancient Debris Finder", d: "3D outlines, tracers, distances and waypoints for debris the server already sent you. Saved per world and dimension; stale spots are marked.", wide: true },
  { icon: "wheel", cat: "Misc", t: "Donut Wheel", d: "A radial menu for your own commands and waypoints: pages, favorites, argument prompts, confirmations and JSON import/export.", wide: true },
  { icon: "coins", cat: "Misc", t: "Auto Sell", d: "Uses your server's own sell command. Protects enchanted and named gear, previews every manual sale, and keeps an honest history." },
  { icon: "music", cat: "Client", t: "SpotifyHUD", d: "Now playing, album art, progress and play/pause, draggable and scalable. No accounts or tokens stored." },
  { icon: "route", cat: "World", t: "Navigation", d: "Hazard-aware walking routes planned off the main thread, previewed before anything moves." },
  { icon: "shield", cat: "Client", t: "Capabilities & profiles", d: "See per server what's Auto, Assist or Off. Record a server's rules once and Hafnium respects them.", wide: true },
  { icon: "grid", cat: "Client", t: "The ClickGUI", d: "Draggable panels, instant search, smooth animations and crisp text at every GUI scale. Try it above.", wide: true },
];

const SHIPPED = [
  ["ClickGUI & module framework", "Panels, search, settings, keybinds, configs UI"],
  ["Ancient Debris Finder", "Observed debris with outlines, tracers and waypoints"],
  ["Donut Wheel", "Radial command and waypoint menu"],
  ["Auto Sell", "Configurable inventory selling"],
  ["SpotifyHUD", "Live music info and album artwork (Linux)"],
  ["Navigation", "Hazard-aware walking routes"],
  ["Safety model", "Capabilities HUD, server profiles, operating modes, emergency stop"],
];
const PLANNED = [
  ["Base Finder", "Analyze observed structures and flag likely player bases"],
  ["Base ESP", "Highlight observed storage and redstone"],
  ["Region Map", "Explored chunks and regions on a colour-coded map"],
  ["Netherite Finder", "Netherite resource discovery and visualization"],
  ["Elytra Finder", "Locate observed End structures and plan elytra runs"],
  ["WorldScanner", "Analyze terrain and observed block patterns"],
  ["Auto Base Hunt", "Exploration of candidate locations, only where automation is authorized"],
  ["Economy Assistant", "Prices, inventory value and profit tracking"],
  ["InventoryCore", "Inventory and equipment management"],
  ["Farm FPS Optimizer", "Better performance around large farms"],
  ["Schematic Preview", "3D building guides"],
  ["HUD Editor", "Move and resize every overlay"],
];

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = s => String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* private mode */ } },
};

// ------------------------------------------------------------------ ClickGUI demo
function demo() {
  const root = $("#panels");
  if (!root) return;
  const toastEl = $("#toast");
  let toastTimer;
  const toast = msg => {
    toastEl.textContent = msg;
    toastEl.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.remove("show"), 2200);
  };
  const enabled = new Set(store.get("hf-demo-on", ["Watermark", "Capabilities"]));
  // Phones: start with only the panels that have working modules open, so the page stays short.
  const defaultFolded = matchMedia("(max-width: 720px)").matches ? ["combat", "movement", "render", "player"] : [];
  const folded = new Set(store.get("hf-demo-folded", defaultFolded));
  let offsets = store.get("hf-demo-offsets", {});
  const mobile = () => matchMedia("(max-width: 720px)").matches;

  for (const c of CATEGORIES) {
    const p = document.createElement("div");
    p.className = "panel" + (folded.has(c.id) ? " folded" : "");
    p.dataset.id = c.id;
    p.innerHTML = `<div class="panel-head" role="button" tabindex="0" aria-expanded="${!folded.has(c.id)}">${svg(c.icon)}<span>${c.title}</span>${svg("chev", "chev")}</div><div class="panel-body"></div>`;
    const body = $(".panel-body", p);
    for (const m of c.mods) {
      const row = document.createElement("div");
      row.className = "mod" + (m.live ? "" : " soon") + (enabled.has(m.name) ? " on" : "");
      row.dataset.name = m.name.toLowerCase();
      row.tabIndex = 0;
      row.innerHTML = `<span class="name">${esc(m.name)}</span>` + (m.live ? "" : '<span class="tag soon">soon</span>')
        + (m.settings ? svg("chevR", "chev-r") : '<span class="state"></span>');
      const activate = () => {
        if (!m.live) { toast(`${m.name} is a placeholder in this release.`); return; }
        let info = row.nextElementSibling;
        if (info && info.classList.contains("mod-info")) info.remove();
        else {
          info = document.createElement("div");
          info.className = "mod-info";
          info.textContent = m.info;
          row.after(info);
        }
        if (!m.settings) {
          row.classList.toggle("on");
          row.classList.contains("on") ? enabled.add(m.name) : enabled.delete(m.name);
          store.set("hf-demo-on", [...enabled]);
          toast(`${m.name} ${row.classList.contains("on") ? "enabled" : "disabled"}`);
        }
        layout();
      };
      row.addEventListener("click", activate);
      row.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); activate(); } });
      body.append(row);
    }
    root.append(p);
  }

  // Masonry placement into columns, plus any dragged offsets (like the real GUI).
  function layout() {
    if (mobile()) return;
    const W = root.clientWidth, PW = 200, GAP = 14;
    const cols = Math.max(1, Math.floor((W + GAP) / (PW + GAP)));
    const used = cols * PW + (cols - 1) * GAP;
    const x0 = Math.max(0, (W - used) / 2);
    const heights = new Array(cols).fill(0);
    let maxH = 0;
    $$(".panel", root).forEach((p, i) => {
      const col = i < cols ? i : heights.indexOf(Math.min(...heights));
      const o = offsets[p.dataset.id] || { x: 0, y: 0 };
      const x = Math.min(Math.max(0, x0 + col * (PW + GAP) + o.x), W - PW);
      const y = Math.max(0, heights[col] + o.y);
      p.style.left = x + "px";
      p.style.top = y + "px";
      heights[col] += p.offsetHeight + GAP;
      maxH = Math.max(maxH, y + p.offsetHeight);
    });
    root.style.height = Math.max(440, maxH + 8) + "px";
  }

  // Drag headers on desktop; click (without moving) folds.
  $$(".panel-head", root).forEach(head => {
    const p = head.parentElement, id = p.dataset.id;
    let start = null;
    const fold = () => {
      p.classList.toggle("folded");
      p.classList.contains("folded") ? folded.add(id) : folded.delete(id);
      head.setAttribute("aria-expanded", String(!p.classList.contains("folded")));
      store.set("hf-demo-folded", [...folded]);
      setTimeout(layout, 260);
    };
    head.addEventListener("pointerdown", e => {
      if (mobile() || e.button !== 0) return;
      start = { x: e.clientX, y: e.clientY, left: p.offsetLeft, top: p.offsetTop, o: { ...(offsets[id] || { x: 0, y: 0 }) }, moved: false };
      head.setPointerCapture(e.pointerId);
    });
    head.addEventListener("pointermove", e => {
      if (!start) return;
      const dx = e.clientX - start.x, dy = e.clientY - start.y;
      if (!start.moved && Math.hypot(dx, dy) < 4) return;
      start.moved = true;
      p.classList.add("dragging");
      p.style.left = Math.min(Math.max(0, start.left + dx), root.clientWidth - p.offsetWidth) + "px";
      p.style.top = Math.max(0, start.top + dy) + "px";
      offsets[id] = { x: start.o.x + dx, y: start.o.y + dy };
    });
    head.addEventListener("pointerup", () => {
      if (!start) return;
      const moved = start.moved;
      start = null;
      p.classList.remove("dragging");
      if (moved) store.set("hf-demo-offsets", offsets);
      else fold();
    });
    head.addEventListener("click", () => { if (mobile()) fold(); });
    head.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fold(); } });
  });

  $("#demo-reset").addEventListener("click", () => {
    offsets = {};
    folded.clear();
    $$(".panel", root).forEach(p => { p.classList.remove("folded"); $(".panel-head", p).setAttribute("aria-expanded", "true"); });
    store.set("hf-demo-offsets", {});
    store.set("hf-demo-folded", []);
    setTimeout(layout, 260);
    toast("Layout reset");
  });

  const search = $("#demo-search");
  search.addEventListener("input", () => {
    const q = search.value.trim().toLowerCase();
    let hits = 0;
    $$(".mod", root).forEach(r => {
      const hit = !q || r.dataset.name.includes(q);
      r.style.display = hit ? "" : "none";
      r.classList.toggle("hit", !!q && hit);
      if (q && hit) hits++;
      const info = r.nextElementSibling;
      if (info && info.classList.contains("mod-info")) info.style.display = hit ? "" : "none";
    });
    $$(".panel", root).forEach(p => {
      const any = $$(".mod", p).some(r => r.style.display !== "none");
      p.style.display = any ? "" : "none";
      if (q && any) p.classList.remove("folded");
    });
    layout();
    if (q && !hits) toast(`No modules match “${search.value.trim()}”`);
  });
  // Typing anywhere in the demo focuses search, like the real GUI.
  $("#screen").addEventListener("keydown", e => {
    if (e.target === search || e.ctrlKey || e.metaKey || e.altKey || e.key.length !== 1 || e.key === " ") return;
    search.focus();
  });

  addEventListener("resize", layout);
  document.fonts && document.fonts.ready.then(layout);
  layout();
}

// ------------------------------------------------------------------ static sections
function features() {
  const grid = $("#feature-grid");
  grid.innerHTML = FEATURES.map(f => `<article class="feat${f.wide ? " wide" : ""} reveal">
      <span class="cat">${esc(f.cat)}</span><div class="ico">${svg(f.icon)}</div><h3>${esc(f.t)}</h3><p>${esc(f.d)}</p></article>`).join("");
  const li = ([t, d]) => `<li><strong>${esc(t)}</strong><span>${esc(d)}</span></li>`;
  $("#lane-ok").innerHTML = SHIPPED.map(li).join("");
  $("#lane-dev").innerHTML = PLANNED.map(li).join("");
}

// ------------------------------------------------------------------ live GitHub data
async function release() {
  const fmt = n => n >= 1e6 ? (n / 1e6).toFixed(1) + " MB" : Math.round(n / 1e3) + " KB";
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } });
    if (!r.ok) return;
    const rel = await r.json();
    const jar = (rel.assets || []).find(a => a.name.endsWith(".jar"));
    $$("[data-version]").forEach(el => el.textContent = `${rel.tag_name} is out`);
    $$("[data-version-short]").forEach(el => el.textContent = rel.tag_name);
    if (jar) {
      $$("[data-asset-meta]").forEach(el => el.textContent = `${rel.tag_name} · ${fmt(jar.size)}`);
      $$('[data-link="download"]').forEach(a => a.href = jar.browser_download_url);
    }
  } catch (e) { /* offline or rate-limited: links still point to the releases page */ }
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}`);
    if (!r.ok) return;
    const repo = await r.json();
    if (repo.stargazers_count > 0) $$("[data-stars]").forEach(el => el.textContent = repo.stargazers_count.toLocaleString());
  } catch (e) { /* ignore */ }
}

// ------------------------------------------------------------------ page chrome
function chrome() {
  const menu = $("#menu"), nav = $("#nav");
  menu.addEventListener("click", () => {
    const open = nav.classList.toggle("open");
    menu.setAttribute("aria-expanded", String(open));
  });
  $$("a", nav).forEach(a => a.addEventListener("click", () => { nav.classList.remove("open"); menu.setAttribute("aria-expanded", "false"); }));
  if (DONATE_URL) $$('[data-link="donate"]').forEach(a => { a.href = DONATE_URL; a.classList.remove("hidden"); });
  $$("[data-copy]").forEach(b => b.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText($("#" + b.dataset.copy).textContent);
      b.textContent = "Copied";
    } catch (e) { b.textContent = "Select & copy"; }
    setTimeout(() => b.textContent = "Copy", 1600);
  }));
  $$(".section-head, .tier, .lane, .steps li, details").forEach(el => el.classList.add("reveal"));
  if ("IntersectionObserver" in window && !location.search.includes("noanim")) {
    const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }), { rootMargin: "0px 0px -8% 0px" });
    $$(".reveal").forEach(el => io.observe(el));
  } else $$(".reveal").forEach(el => el.classList.add("in"));
}

document.addEventListener("DOMContentLoaded", () => {
  features();
  chrome();
  demo();
  release();
});
