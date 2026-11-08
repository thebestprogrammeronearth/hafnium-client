import json, sys
from gi.repository import Gio, GLib
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
name = 'org.mpris.MediaPlayer2.spotify'
path = '/org/mpris/MediaPlayer2'
iface = 'org.mpris.MediaPlayer2.Player'
def call(interface, method, args=None):
    return bus.call_sync(name, path, interface, method, args, None, Gio.DBusCallFlags.NONE, 2000, None)
try:
    action = sys.argv[1]
    props = call('org.freedesktop.DBus.Properties', 'GetAll', GLib.Variant('(s)', (iface,))).unpack()[0]
    if action == 'toggle':
        if props.get('CanControl', False) and props.get('CanPause', False):
            call(iface, 'PlayPause')
        else:
            raise RuntimeError('Spotify does not support playback control in this session')
    meta = props.get('Metadata', {})
    print(json.dumps(dict(title=meta.get('xesam:title', ''), artist=', '.join(meta.get('xesam:artist', [])),
        track=str(meta.get('mpris:trackid', '')), art=meta.get('mpris:artUrl', ''),
        position=max(0, props.get('Position', 0)//1000), duration=max(0, meta.get('mpris:length', 0)//1000),
        playing=props.get('PlaybackStatus') == 'Playing', stopped=props.get('PlaybackStatus') == 'Stopped',
        controls=props.get('CanControl', False) and props.get('CanPause', False), error='')))
except Exception as e:
    print(json.dumps(dict(error=str(e)[:300])))
