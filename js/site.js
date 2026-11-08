// Optional donation link (GitHub Sponsors, Ko-fi, ...). Leave empty to hide the button.
const DONATE_URL = "";

// Keep this list honest: "ok" = in the current release, "dev" = not yet.
const FEATURES = [
  { s: "ok", t: "ClickGUI", d: "Draggable category panels, instant search, smooth animations and crisp Inter text at every GUI scale." },
  { s: "ok", t: "Ancient Debris Finder", d: "Outlines, tracers, distances and waypoints for debris the server has already sent you, saved per world and dimension." },
  { s: "ok", t: "Donut Wheel", d: "A radial menu for your own commands and waypoints: pages, favorites, confirmations and import/export." },
  { s: "ok", t: "Auto Sell", d: "Sells whitelisted items with your server's own sell command. Protects valuable gear and always asks before manual sales." },
  { s: "ok", t: "SpotifyHUD", d: "Now playing, artwork, progress and play/pause from Spotify's desktop session (Linux)." },
  { s: "ok", t: "Navigation", d: "Walking routes that avoid hazards, with a preview before anything moves." },
  { s: "ok", t: "Capabilities indicator", d: "A HUD panel that shows, per server, which actions are available (Auto / Assist / Off) and why." },
  { s: "ok", t: "Server profiles & modes", d: "Record each server's rules once. Manual, Assist and Autonomous modes, with an emergency stop on End." },
  { s: "dev", t: "Inventory management", d: "Sorting and inventory tasks on top of the automation core." },
  { s: "dev", t: "Gameplay modules", d: "The other Combat, Movement, Render and Player modules shown in the GUI are placeholders for now." },
  { s: "dev", t: "Saved configs", d: "The config manager UI is in place; saving configs to disk is coming." },
];

document.addEventListener("DOMContentLoaded", () => {
  const grid = document.getElementById("feature-grid");
  for (const f of FEATURES) {
    const card = document.createElement("div");
    card.className = "card";
    const badge = document.createElement("span");
    badge.className = "status " + f.s;
    badge.textContent = f.s === "ok" ? "Included" : "In development";
    const h = document.createElement("h3");
    h.textContent = f.t;
    const p = document.createElement("p");
    p.textContent = f.d;
    card.append(badge, h, p);
    grid.append(card);
  }
  if (DONATE_URL) {
    document.querySelectorAll('[data-link="donate"]').forEach(a => {
      a.href = DONATE_URL;
      a.classList.remove("hidden");
    });
  }
});
