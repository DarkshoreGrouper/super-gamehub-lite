#!/usr/bin/env python3
"""Fail CI if the applied APK patches lose the background service or pause gate."""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'AndroidManifest.xml').getroot()
permissions = {p.get(android + 'name') for p in manifest.findall('uses-permission')}
assert {'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
        'android.permission.WAKE_LOCK', 'android.permission.POST_NOTIFICATIONS'} <= permissions
services = [s for s in manifest.findall('./application/service')
            if s.get(android + 'name') == 'com.xj.winemu.sidebar.SuperBackgroundService']
assert len(services) == 1
service = services[0]
for name, value in {'process': ':wine', 'exported': 'false', 'foregroundServiceType': 'specialUse', 'stopWithTask': 'true'}.items():
    assert service.get(android + name) == value, name
assert any(p.get(android + 'name') == 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' for p in service.findall('property'))
wine = (root / 'smali_classes4/com/xj/winemu/WineActivity.smali').read_text()
assert wine.count('SuperBackground;->attach(Landroid/app/Activity;)V') == 1
pause = wine.split('.method public onPause()V', 1)[1].split('.end method', 1)[0]
assert 'invoke-super {p0}, Landroidx/fragment/app/FragmentActivity;->onPause()V' in pause
assert pause.index('SuperBackground;->keepRunning()Z') < pause.index('WinUIBridge;->Y()V') < pause.index('\n    :super_keep_online\n')
assert 'if-nez v0, :super_keep_online' in pause
assert 'WinUIBridge;->Z()V' in wine  # normal resume still runs
assert 'SuperDiagnostics;->stop()V' in wine  # normal exit still runs
print('Background manifest, native pause gate, resume and cleanup hooks verified')
