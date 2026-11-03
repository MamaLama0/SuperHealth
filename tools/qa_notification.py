#!/usr/bin/env python3
"""Tap a synthetic battery notification after changing the isolated test app's icon.

Requires the debug app.superhealth.grouptest APK and its synthetic Group seed.
Uses the real LauncherIcon.apply implementation (DONT_KILL_APP), rather than
`pm disable`, which kills the app and can cancel the notification under test.
Never operates on the production package or prints other notifications/device IDs.
"""
import json
import pathlib
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'app.superhealth.grouptest'
XML = '/data/local/tmp/superhealth-notification-qa.xml'


def main():
    devices = [x.split()[0] for x in subprocess.check_output(['adb', 'devices'], text=True).splitlines()[1:] if x.strip().endswith('device')]
    assert devices, 'A development installation is required'
    device = devices[min(1, len(devices) - 1)]

    def adb(*args, check=True):
        result = subprocess.run(['adb', '-s', device, *args], capture_output=True, timeout=45)
        if check and result.returncode:
            raise RuntimeError('Android test command failed: ' + args[0])
        return result

    def shell(*args, check=True):
        return adb('shell', shlex.join(args), check=check)

    def operation(name, **extras):
        shell('run-as', PACKAGE, 'rm', '-f', 'files/group-test/result.json')
        args = ['am', 'start', '-n', PACKAGE + '/app.superhealth.debug.GroupTestActivity', '--es', 'operation', name]
        for key, value in extras.items():
            args += ['--es', key, value]
        shell(*args)
        for _ in range(40):
            raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/group-test/result.json', check=False)
            try:
                result = json.loads(raw.stdout)
                if result.get('operation') == name:
                    assert result['ok'], 'Synthetic operation failed: ' + name
                    return
            except json.JSONDecodeError:
                pass
            time.sleep(.25)
        raise AssertionError('Synthetic operation timed out: ' + name)

    def nodes():
        shell('uiautomator', 'dump', XML)
        return list(ET.fromstring(adb('exec-out', 'cat', XML).stdout).iter('node'))

    assert PACKAGE.encode() in shell('pm', 'list', 'packages', PACKAGE).stdout
    shell('pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    try:
        operation('icon', accent='BLUE')
        operation('notification')
        shell('input', 'keyevent', '3')
        operation('icon', accent='PINK')
        shell('input', 'keyevent', '3')
        shell('cmd', 'statusbar', 'expand-notifications')
        for _ in range(5):
            notification = next((n for n in nodes() if n.get('text') == 'Montre de test'), None)
            if notification is not None:
                break
            shell('input', 'swipe', '500', '1800', '500', '700', '400')
        else:
            raise AssertionError('Synthetic battery notification is not visible')
        a, b, c, d = map(int, re.findall(r'\d+', notification.get('bounds')))
        shell('input', 'tap', str((a + c) // 2), str((b + d) // 2))
        nodes()
        activity = shell('dumpsys', 'activity', 'activities').stdout.decode()
        resumed = [line for line in activity.splitlines() if 'ResumedActivity' in line]
        manifest = ET.parse(pathlib.Path(__file__).resolve().parents[1] / 'app/src/superhealth/AndroidManifest.xml')
        android = '{http://schemas.android.com/apk/res/android}'
        launchers = ['app.superhealth.MainActivity'] + [n.get(android + 'name') for n in manifest.iter('activity-alias') if n.get(android + 'targetActivity') == 'app.superhealth.MainActivity']
        assert any(PACKAGE + '/' + target in line for line in resumed for target in launchers), 'Battery notification opened an unexpected activity'
        print('Battery notification survives an icon change and opens SuperHealth: OK', flush=True)
    finally:
        shell('cmd', 'statusbar', 'collapse', check=False)
        operation('icon', accent='BLUE')
        shell('am', 'force-stop', PACKAGE)
        shell('rm', '-f', XML)
        shell('am', 'start', '-n', PACKAGE + '/app.superhealth.MainActivity', '--es', 'route', 'feed')


if __name__ == '__main__':
    main()
