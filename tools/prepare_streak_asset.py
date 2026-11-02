#!/usr/bin/env python3
"""Adapt the attributed Rive source for continuous native flame animation.

Usage: python3 tools/prepare_streak_asset.py ORIGINAL.riv OUTPUT.riv
Source: https://rive.app/marketplace/28627-54130-streak-animation-improved/
CC BY 4.0, indoorraccoon, remix of Dynamic streak fire by aristote.
The two changes set StateTransitionFlags.Disabled (bit 0), defined by
https://github.com/rive-app/rive-runtime/blob/main/include/rive/animation/state_transition_flags.hpp
No timing callback is required: Pop holds its final pose while the flame-tip
layer keeps looping. Disable pointer interruption too, so tapping the card
cannot send the graphic into its Out animation.
"""
import hashlib
from pathlib import Path
import sys
import struct


def adapt(source: bytes) -> bytes:
    if hashlib.sha256(source).hexdigest() != 'ce55b290e2e0b2380b5d64872a3b0a2baf0a3458f30506049edf6d9c6f440de1':
        raise ValueError('Unexpected source revision; inspect its state machine before adapting.')
    output = bytearray(source)
    assert output[286914:286925] == bytes.fromhex('4197010598010ca0016400')
    output[286920] |= 1  # Pop -> Bounce -> Out: keep Pop's completed pose.
    assert output[286789:286794] == bytes.fromhex('4197010600')
    # Slow the native tips and embers by 50%, without changing entry or the counter.
    assert abs(struct.unpack_from('<f', output, 286951)[0] - .8) < .0001
    assert struct.unpack_from('<f', output, 286985)[0] == .5
    struct.pack_into('<f', output, 286951, .4)
    struct.pack_into('<f', output, 286985, .25)
    output[286793:286793] = bytes.fromhex('980101')  # Any -> interrupted Out: flags = Disabled.
    return bytes(output)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    Path(sys.argv[2]).write_bytes(adapt(Path(sys.argv[1]).read_bytes()))
