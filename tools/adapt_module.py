#!/usr/bin/env python3
"""Mechanical Anubis -> Hafnium module API rewrites (run after port_anubis.py; the result is then finished by hand).

ClientModule -> Module, setting constructors, super(id, name, desc, category) -> super(name, Category.X, desc),
@EventHandler methods -> subscriptions made in onEnable(ModuleContext), TickEvent -> ClientTickEvent.END,
PacketAppliedEvent(chunk) -> ChunkLoadEvent, notifications -> a "Notifications" BoolSetting.
Usage: tools/adapt_module.py <file.java> <CATEGORY>
"""
import re, sys

path, category = sys.argv[1], sys.argv[2]
s = open(path).read()

s = s.replace("extends ClientModule", "extends Module")
s = re.sub(r"this\.addSetting\(\s*(\"[^\"]*\"|\w+),\s*", "this.setting(", s)
s = s.replace("new BooleanSetting(", "new BoolSetting(").replace("BooleanSetting ", "BoolSetting ")
s = s.replace("BooleanSetting[]", "BoolSetting[]").replace("(BooleanSetting)", "(BoolSetting)")
# NumberSetting("N", a, b, c).step(s) -> NumberSetting("N", a, b, c, s)
s = re.sub(r"new NumberSetting\(([^()]*)\)\.step\(([^()]*)\)", r"new NumberSetting(\1, \2)", s)
s = re.sub(r"(\d+\.\d+)F\b", r"\1", s) if False else s
# super("id", "Name", "desc", ModuleCategory.X)
s = re.sub(r"super\(\s*\"[^\"]*\",\s*(\"[^\"]*\"),\s*(\"(?:[^\"\\]|\\.)*\"),\s*ModuleCategory\.\w+\s*\)",
           r"super(\1, Category." + category + r", \2)", s, flags=re.S)
s = s.replace("this.usesNotifications()", "this.notificationsSetting()")
s = s.replace("this.notificationsOn()", "this.notifications.get()")
s = re.sub(r"ModuleChat\.send\(this,", "ModuleChat.send(this.name(),", s)
s = re.sub(r"ModuleChat\.actionBar\(this,", "ModuleChat.actionBar(this.name(),", s)

handlers = re.findall(r"@EventHandler\s*\n\s*public void (\w+)\((\w+) event\)", s)
s = re.sub(r"@EventHandler\s*\n\s*public void", "private void", s)
subs = []
for name, ev in handlers:
    if ev == "TickEvent":
        subs.append(f"ctx.subscribe(ClientTickEvent.class, e -> {{ if (e == ClientTickEvent.END) this.{name}(null); }});")
    elif ev == "PacketAppliedEvent":
        subs.append(f"// TODO {name}: PacketAppliedEvent -> ChunkLoadEvent")
    else:
        subs.append(f"ctx.subscribe({ev}.class, this::{name});")
s = s.replace("protected void onEnable() {", "protected void onEnable(ModuleContext ctx) {\n" +
              "".join("      " + x + "\n" for x in subs), 1)
if "onEnable(ModuleContext" not in s and subs:
    i = s.rfind("\n}")
    s = s[:i] + "\n\n   @Override\n   protected void onEnable(ModuleContext ctx) {\n" + \
        "".join("      " + x + "\n" for x in subs) + "   }\n" + s[i:]

imports = {
    "import dev.hafnium.client.anubis.module.ClientModule;": "import dev.hafnium.client.core.module.Module;\nimport dev.hafnium.client.core.module.ModuleContext;",
    "import dev.hafnium.client.anubis.module.ModuleCategory;": "import dev.hafnium.client.core.module.Category;",
    "import dev.hafnium.client.anubis.setting.BooleanSetting;": "import dev.hafnium.client.core.setting.BoolSetting;",
    "import dev.hafnium.client.anubis.setting.NumberSetting;": "import dev.hafnium.client.core.setting.NumberSetting;",
    "import dev.hafnium.client.anubis.setting.ColorSetting;": "import dev.hafnium.client.core.setting.ColorSetting;",
    "import dev.hafnium.client.anubis.setting.ModeSetting;": "import dev.hafnium.client.core.setting.ModeSetting;",
    "import dev.hafnium.client.anubis.setting.StringSetting;": "import dev.hafnium.client.core.setting.StringSetting;",
    "import dev.hafnium.client.anubis.event.EventHandler;\n": "",
    "import dev.hafnium.client.event.TickEvent;": "import dev.hafnium.client.core.event.ClientTickEvent;",
    "import dev.hafnium.client.event.PacketAppliedEvent;": "import dev.hafnium.client.event.ChunkLoadEvent;",
    "import dev.hafnium.client.modules.anubis.PerformanceModule;": "import dev.hafnium.client.modules.client.PerformanceModule;",
}
for a, b in imports.items():
    s = s.replace(a, b)
s = s.replace("TickEvent event", "Object event").replace("(TickEvent ", "(Object ")
open(path, "w").write(s)
print(path, "handlers:", handlers)
