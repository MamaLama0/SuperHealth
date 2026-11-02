#!/usr/bin/python3
"""Vérifie les vraies cartes et RemoteViews sur des appareils ADB, exclusivement dans app.superhealth.qa.

Exemple : python3 tools/qa_widgets.py --serial SERIAL_A --serial SERIAL_B --output /tmp/sh-widgets
Les réglages d'apparence de la QA, le thème Android et la police système sont restaurés à la fin.
Aucune donnée de suivi n'est injectée et aucune installation personnelle n'est modifiée.
"""
import argparse
import concurrent.futures
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

PACKAGE = 'app.superhealth.qa'
SOCIAL = {'league': 'Classement', 'progress': 'Ma progression', 'streak': 'Ma progression',
          'quest': 'Quêtes de la semaine', 'friends': 'Amis', 'social': 'Social'}
SIZES = ('S', 'M', 'W', 'L')


def run_device(serial, label, output, only, modes):
    target = output / label
    target.mkdir(parents=True, exist_ok=True)
    results = []

    def adb(*args, binary=False):
        return subprocess.run(['adb', '-s', serial, *args], check=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, timeout=45, text=not binary).stdout

    def start(activity, **extras):
        command = ['shell', 'am', 'start']
        command += ['-f', '0x04000000'] if activity == 'HomeWidgetPreviewActivity' else ['-S']
        command += ['-n', f'{PACKAGE}/app.superhealth.debug.{activity}']
        for key, value in extras.items():
            command += ['--es', key, str(value)]
        result = adb(*command)
        if 'Error' in result or 'Exception' in result:
            raise RuntimeError(result)
        time.sleep(1.4)

    def hierarchy():
        for attempt in range(4):
            adb('shell', 'rm', '-f', '/data/local/tmp/superhealth-widgets.xml')
            result = adb('shell', 'uiautomator', 'dump', '--compressed', '/data/local/tmp/superhealth-widgets.xml')
            if 'dumped to:' in result:
                raw = adb('exec-out', 'cat', '/data/local/tmp/superhealth-widgets.xml')
                return ET.fromstring(raw)
            time.sleep(1)
        raise RuntimeError('Accessibility dump failed: ' + result)

    def await_title(title):
        for attempt in range(4):
            root = hierarchy()
            if any(n.get('text') == title for n in root.iter()):
                return root
            time.sleep(1)
        raise AssertionError('Missing screen title: ' + title)

    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.attrib['bounds'])))

    def capture(path, crop=None):
        data = adb('exec-out', 'screencap', '-p', binary=True)
        assert data.startswith(b'\x89PNG'), 'Invalid screenshot'
        path.write_bytes(data)
        if crop:
            from PIL import Image
            with Image.open(path) as im:
                im.crop(crop).save(path.with_name(path.stem + '-crop.png'))

    def probe(op, **extras):
        start('RegressionProbeActivity', op=op, **extras)
        for attempt in range(5):
            try:
                root = hierarchy()
            except RuntimeError:
                time.sleep(1)
                continue
            text = ' '.join(n.get('text', '') for n in root.iter())
            if any(n.get('text') == f'QA OK · {op}' for n in root.iter()):
                return
            if 'QA ERROR' in text:
                raise AssertionError(text)
            time.sleep(1)
        raise AssertionError(f'Probe {op} did not finish: {text}')

    night = adb('shell', 'cmd', 'uimode', 'night').strip().split(': ')[-1]
    font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
    bind = adb('shell', 'appwidget', 'grantbind', '--package', PACKAGE, '--user', '0')
    saved = False
    try:
        pending = subprocess.run(['adb', '-s', serial, 'shell', 'run-as', PACKAGE, 'test', '-f',
                                  'files/qa/appearance-original.json'], capture_output=True, timeout=15)
        if pending.returncode == 0:
            probe('appearance-restore')
        probe('appearance-save')
        saved = True
        for mode, android_night, system_scale, app_theme, app_text in [
                ('dark', 'yes', '1.0', 'DARK', 'NORMAL'),
                ('light-large', 'no', '1.25', 'LIGHT', 'LARGE')]:
            if mode not in modes:
                continue
            folder = target / mode
            folder.mkdir(exist_ok=True)
            adb('shell', 'cmd', 'uimode', 'night', android_night)
            adb('shell', 'settings', 'put', 'system', 'font_scale', system_scale)
            probe('appearance', theme=app_theme, textSize=app_text, language='FR')
            for key, destination in (SOCIAL.items() if only != 'app' else []):
                for size in SIZES:
                    start('HomeWidgetPreviewActivity', widget=key, size=size)
                    for attempt in range(4):
                        try:
                            root = hierarchy()
                        except RuntimeError:
                            time.sleep(1)
                            continue
                        host = next((n for n in root.iter() if n.get('class') == 'android.appwidget.AppWidgetHostView'), None)
                        texts = [n.get('text', '') for n in host.iter() if n.get('text', '').strip()] if host is not None else []
                        if texts:
                            break
                        time.sleep(1)
                    assert host is not None, f'No host {key}/{size}'
                    assert texts, f'Widget empty {key}/{size}'
                    assert not any('load widget' in t.lower() or 'impossible' in t.lower() for t in texts), texts
                    # Une ligne coupée ne doit jamais être prise pour un rendu réussi.
                    hb = bounds(host)
                    for n in host.iter():
                        if n.get('text'):
                            x1, y1, x2, y2 = bounds(n)
                            assert x2 > x1 and y2 - y1 >= 12, f'Clipped text {key}/{size}: {n.get("text")}'
                    capture(folder / f'android-{key}-{size}.png', hb)
                    results.append({'kind': 'android', 'widget': key, 'size': size, 'mode': mode, 'rendered': True})
                    if size == 'L':
                        # Le premier RemoteViews peut être remplacé après son texte : attendre aussi ses actions.
                        time.sleep(3)
                        adb('shell', 'input', 'tap', str((hb[0] + hb[2]) // 2), str((hb[1] + hb[3]) // 2))
                        for attempt in range(4):
                            time.sleep(1)
                            page = hierarchy()
                            texts = [n.get('text', '') for n in page.iter()]
                            if destination in texts:
                                break
                        assert destination in texts, f'Wrong destination {key}: {texts}'
                        results[-1]['tapDestination'] = destination
                print(f'{label} {mode}: Android {key}, 4 formats + destination OK', flush=True)
            for size in (SIZES if only != 'native' else []):
                start('WidgetPreviewActivity', cards='STREAKS,LEAGUE,PROGRESSION,FRIENDS,QUESTS', size=size)
                root = await_title('Mes séries')
                capture(folder / f'dashboard-{size}.png')
                results.append({'kind': 'dashboard', 'size': size, 'mode': mode, 'rendered': True})
                if size in ('W', 'L'):
                    adb('shell', 'input', 'swipe', '500', '1750', '500', '650', '300')
                    time.sleep(.5)
                    capture(folder / f'dashboard-{size}-bottom.png')
            for route, title in ([('league', 'Classement'), ('progress', 'Ma progression'), ('quests', 'Quêtes de la semaine'),
                                 ('social', 'Social'), ('widgets', 'Widgets Android'), ('settings/find', 'Trouver un réglage'),
                                 ('stretch/edit', "Ma séance d'étirements")] if only != 'native' else []):
                start('FlowPreviewActivity', route=route)
                root = await_title(title)
                capture(folder / f'page-{route.replace("/", "-")}.png')
                results.append({'kind': 'screen', 'route': route, 'mode': mode, 'rendered': True})
            print(f'{label} {mode}: cartes et parcours OK', flush=True)
        # Vérifier aussi les libellés anglais et la persistance après relance.
        probe('appearance', theme='DARK', textSize='NORMAL', language='EN')
        start('FlowPreviewActivity', route='quests')
        await_title('Weekly quests')
        capture(target / 'quests-english.png')
        results.append({'kind': 'screen', 'route': 'quests', 'mode': 'english', 'rendered': True})
    except Exception as error:
        capture(target / 'failure.png')
        results.append({'error': str(error)})
        raise
    finally:
        (target / ('results-' + only + '-' + '-'.join(modes) + '.json')).write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
        adb('shell', 'cmd', 'uimode', 'night', night)
        if font == 'null':
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', font)
        probe('widget-cleanup')
        if saved:
            # La recréation Android lors d'un changement de langue peut rejouer l'Intent de restauration.
            pending = subprocess.run(['adb', '-s', serial, 'shell', 'run-as', PACKAGE, 'test', '-f',
                                      'files/qa/appearance-original.json'], capture_output=True, timeout=15)
            if pending.returncode == 0:
                probe('appearance-restore')
        adb('shell', 'am', 'start', '-S', '-n', f'{PACKAGE}/app.superhealth.MainActivity')
        print(f'{label}: apparence restaurée', flush=True)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--label-offset', type=int, default=0)
    parser.add_argument('--only', choices=['all', 'native', 'app'], default='all')
    parser.add_argument('--mode', action='append', choices=['dark', 'light-large'])
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(args.serial)) as pool:
        jobs = [pool.submit(run_device, serial, f'device-{i+1+args.label_offset}', args.output, args.only, args.mode or ['dark', 'light-large']) for i, serial in enumerate(args.serial)]
        for job in jobs:
            job.result()


if __name__ == '__main__':
    main()
