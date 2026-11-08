"""Test-only Spotify-shaped service on an isolated session bus; never claims real playback."""
import sys, subprocess, json
from gi.repository import Gio, GLib
if len(sys.argv) > 1 and sys.argv[1] == 'serve':
    bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)
    state={'playing':True}
    xml='''<node><interface name="org.freedesktop.DBus.Properties"><method name="GetAll"><arg type="s" direction="in"/><arg type="a{sv}" direction="out"/></method></interface><interface name="org.mpris.MediaPlayer2.Player"><method name="PlayPause"/></interface></node>'''
    node=Gio.DBusNodeInfo.new_for_xml(xml)
    def method(conn,sender,path,interface,name,args,invocation):
        if name=='PlayPause':
            state['playing']=not state['playing'];invocation.return_value(None);return
        meta={'xesam:title':GLib.Variant('s','Fixture track'), 'xesam:artist':GLib.Variant('as',['Fixture artist']), 'mpris:trackid':GLib.Variant('o','/fixture/track'), 'mpris:length':GLib.Variant('x',180000000)}
        props={'Metadata':GLib.Variant('a{sv}',meta),'PlaybackStatus':GLib.Variant('s','Playing' if state['playing'] else 'Paused'),'Position':GLib.Variant('x',42000000),'CanControl':GLib.Variant('b',True),'CanPause':GLib.Variant('b',True)}
        invocation.return_value(GLib.Variant('(a{sv})',(props,)))
    for interface in node.interfaces:bus.register_object('/org/mpris/MediaPlayer2',interface,method,None,None)
    Gio.bus_own_name_on_connection(bus,'org.mpris.MediaPlayer2.spotify',Gio.BusNameOwnerFlags.NONE,lambda *a:print('ready',flush=True),None)
    GLib.MainLoop().run()
else:
    bridge=sys.argv[1]
    service=subprocess.Popen([sys.executable,__file__,'serve'],stdout=subprocess.PIPE,text=True)
    try:
        assert service.stdout.readline().strip()=='ready'
        def query(action):return json.loads(subprocess.check_output([sys.executable,bridge,action],timeout=5))
        p=query('poll');assert p['title']=='Fixture track' and p['artist']=='Fixture artist'
        assert p['position']==42000 and p['duration']==180000 and p['playing'] and p['controls']
        query('toggle');assert query('poll')['playing'] is False
        service.terminate();service.wait(timeout=3)
        assert query('poll')['error']
        print('MPRIS fixture: metadata, units, controls, pause and unavailable-session handling passed')
    finally:
        service.terminate()
