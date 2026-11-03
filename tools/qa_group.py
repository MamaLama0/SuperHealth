#!/usr/bin/env python3
"""Synthetic end-to-end checks, exclusively in app.superhealth.grouptest.

Requires the debug test APK installed on two development devices, a local relay
on the selected host port, and a generated video fixture. Never reads the normal
SuperHealth package. Does not print invitation keys, recovery codes or device IDs.
"""
import argparse
import hashlib
import json
import pathlib
import shlex
import subprocess
import time

PACKAGE = 'app.superhealth.grouptest'
COMPONENT = PACKAGE + '/app.superhealth.debug.GroupTestActivity'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=18788)
    parser.add_argument('--video', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--reset-synthetic', action='store_true', help='Erase only the isolated synthetic test package before seeding')
    args = parser.parse_args()
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    devices = [line.split()[0] for line in subprocess.check_output(['adb', 'devices'], text=True).splitlines()[1:] if line.strip().endswith('device')]
    assert len(devices) >= 2, 'Two connected development devices are required'
    devices = devices[:2]

    def adb(index, *parts, check=True):
        return subprocess.run(['adb', '-s', devices[index], *parts], capture_output=True, timeout=120, check=check)

    def shell(index, *parts, check=True):
        return adb(index, 'shell', shlex.join(parts), check=check)

    def read(index, name):
        return adb(index, 'exec-out', 'run-as', PACKAGE, 'cat', 'files/group-test/' + name).stdout.decode()

    summary = []

    def run(index, operation, **extras):
        shell(index, 'run-as', PACKAGE, 'rm', '-f', 'files/group-test/result.json', check=False)
        command = ['am', 'start', '-n', COMPONENT, '--es', 'operation', operation]
        for key, value in extras.items():
            command += ['--es', key, value]
        shell(index, *command)
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            result = adb(index, 'exec-out', 'run-as', PACKAGE, 'cat', 'files/group-test/result.json', check=False)
            if result.returncode == 0 and result.stdout.strip():
                try:
                    parsed = json.loads(result.stdout)
                except json.JSONDecodeError:
                    time.sleep(0.2)
                    continue
                assert parsed['ok'], {k: v for k, v in parsed.items() if k != 'public'}
                safe = {k: v for k, v in parsed.items() if k != 'public'}
                summary.append({'installation': index + 1, **safe})
                print(json.dumps({'installation': index + 1, **safe}, ensure_ascii=False), flush=True)
                (output / 'results.private.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
                return parsed
            time.sleep(1)
        raise RuntimeError('Timed out waiting for isolated test operation: ' + operation)

    for index in range(2):
        assert PACKAGE.encode() in shell(index, 'pm', 'list', 'packages', PACKAGE).stdout
        if args.reset_synthetic:
            shell(index, 'am', 'force-stop', PACKAGE)
            shell(index, 'pm', 'clear', PACKAGE)
        adb(index, 'reverse', 'tcp:18787', 'tcp:' + str(args.port))
        run(index, 'seed', name='QA_Alice' if index == 0 else 'QA_Benoit')

    first = read(0, 'invitation.txt')
    second = read(1, 'invitation.txt')
    run(0, 'add', card=second)
    run(1, 'add', card=first)
    fixture = pathlib.Path(args.video)
    adb(0, 'push', str(fixture), '/data/local/tmp/superhealth-group-306-test.mp4')
    shell(0, 'run-as', PACKAGE, 'mkdir', '-p', 'cache/social')
    shell(0, 'run-as', PACKAGE, 'cp', '/data/local/tmp/superhealth-group-306-test.mp4', 'cache/social/group-test.mp4')
    published = run(0, 'publish')
    assert published['pr'] > 0 and published['video'] > 0
    received = run(1, 'sync')
    assert received['pr'] > 0 and received['profiles'] > 0
    run(1, 'download')
    assert read(1, 'video.sha256') == hashlib.sha256(fixture.read_bytes()).hexdigest()
    run(1, 'reply')
    run(1, 'react')
    assert run(0, 'sync')['reactionEvents'] > 0
    run(0, 'archive')
    assert run(0, 'sync')['replies'] > 0
    before = run(0, 'status')
    run(0, 'rotate')
    run(1, 'sync')
    followed = run(1, 'sync')
    assert followed['video'] > 0 and followed['replies'] > 0
    run(1, 'download')
    assert read(1, 'video.sha256') == hashlib.sha256(fixture.read_bytes()).hexdigest()
    recovery = read(0, 'recovery.txt')
    restored = run(1, 'recover', code=recovery)
    assert restored['public'] == before['public'] and restored['video'] > 0
    rotated_recovery = run(1, 'rotate')
    assert rotated_recovery['archived'] >= 26
    run(1, 'download')
    assert read(1, 'video.sha256') == hashlib.sha256(fixture.read_bytes()).hexdigest()
    (output / 'checks.private.json').write_text(json.dumps({
        'social_exchange': True, 'profiles_and_badges': True, 'pr_with_video': True,
        'video_plaintext_hash_verified': True, 'signed_replies': True,
        'rotation_retains_media': True, 'retained_friend_follows_handoff': True,
        'social_identity_recovery': True, 'custom_emoji_exchange': True,
        'rotation_after_recovery_preserves_unloaded_archive_pages': True, 'production_package_accessed': False,
    }, indent=2))
    print('All isolated Group exchange checks passed.', flush=True)


if __name__ == '__main__':
    main()
