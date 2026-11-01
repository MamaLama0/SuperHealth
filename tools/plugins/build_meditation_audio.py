#!/usr/bin/env python3
"""Sons du plugin Méditation : voix pré-enregistrées (Piper) et sons de synthèse, sans fichier d'une autre app.

usage : python build_meditation_audio.py <src/meditation> <dossier des voix .onnx>
Voix : fr_FR-siwis-medium (CC BY 4.0, attribution dans credits.txt), en_US-ljspeech-medium (domaine public).
Une phrase par fichier : voice/<langue>/<séance>_<n>.mp3 ; le lecteur la joue à l'instant prévu.
"""
import json, os, subprocess, sys, tempfile, wave
import numpy as np
from piper import PiperVoice
from piper.config import SynthesisConfig

src, voices = sys.argv[1], sys.argv[2]
SR = 44100
rng = np.random.default_rng(7)


def encode(x, path, ext_args):
    """Flottants mono entre -1 et 1 vers un fichier compressé."""
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes()
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-", *ext_args, path], input=pcm, check=True)


def gong(f0, seconds, level):
    """Bol : partiels inharmoniques, chacun avec sa décroissance ; légère attaque pour éviter le clic."""
    t = np.arange(int(SR * seconds)) / SR
    x = np.zeros_like(t)
    for ratio, gain, decay in [(1, 1, 3.2), (1.003, .6, 3.0), (2.71, .45, 1.9), (2.74, .25, 1.7), (5.2, .16, 1.0), (8.4, .06, .6)]:
        x += gain * np.sin(2 * np.pi * f0 * ratio * t) * np.exp(-t * 3 / (decay * seconds / 3.2))
    x *= np.minimum(t / .006, 1)
    x *= np.minimum((seconds - t) / .3, 1)
    return x / np.abs(x).max() * level


def shaped(seconds, shape):
    """Bruit bouclable : spectre façonné, donc périodique sur la durée exacte du fichier."""
    n = int(SR * seconds)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = (rng.normal(size=f.size) + 1j * rng.normal(size=f.size)) * shape(np.maximum(f, 1))
    x = np.fft.irfft(spec, n)
    return x / np.abs(x).max()


def ambience():
    seconds = 60
    t = np.arange(SR * seconds) / SR
    # pluie : souffle clair, sans graves
    rain = shaped(seconds, lambda f: np.where(f < 300, (f / 300) ** 2, 1) / f ** .35 * np.exp(-f / 9000))
    # océan : bruit grave, vagues dont la période tombe juste sur la boucle
    swell = .55 + .45 * (.6 * np.sin(2 * np.pi * t * 6 / seconds) ** 2 + .4 * np.sin(2 * np.pi * t * 4 / seconds + 1) ** 2)
    ocean = shaped(seconds, lambda f: 1 / f ** .9 * np.exp(-f / 5000)) * swell
    # bruit doux : brun, le plus neutre
    brown = shaped(seconds, lambda f: 1 / f ** 1.0 * np.exp(-f / 2500))
    out = os.path.join(src, "audio")
    for name, x in [("rain", rain), ("ocean", ocean), ("brown", brown)]:
        encode(x / np.abs(x).max() * .5, f"{out}/{name}.ogg", ["-c:a", "libvorbis", "-q:a", "1"])


def speak(voice, text, path):
    cfg = SynthesisConfig(length_scale=1.22, noise_scale=.55, noise_w_scale=.7)
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            voice.synthesize_wav(text, w, syn_config=cfg)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-af", "lowpass=8500,aecho=0.9:0.5:45:0.12,loudnorm=I=-20:TP=-2",
                        "-ac", "1", "-ar", "22050", "-c:a", "libmp3lame", "-b:a", "40k", path], check=True)
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True)
    return float(probe.stdout)


os.makedirs(f"{src}/audio", exist_ok=True)
encode(gong(174.6, 11, .8), f"{src}/audio/gong_timer.mp3", ["-c:a", "libmp3lame", "-b:a", "64k"])
encode(gong(261.6, 6, .55), f"{src}/audio/gong_timer_inter.mp3", ["-c:a", "libmp3lame", "-b:a", "64k"])
ambience()

content = json.load(open(f"{src}/meditation.json"))
for lang, model in [("fr", "fr_FR-siwis-medium"), ("en", "en_US-ljspeech-medium")]:
    os.makedirs(f"{src}/audio/voice/{lang}", exist_ok=True)
    voice = PiperVoice.load(f"{voices}/{model}.onnx")
    for s in content["sessions"]:
        lines = s["lines"]
        for i, line in enumerate(lines):
            d = speak(voice, line.get(lang) or line["fr"], f"{src}/audio/voice/{lang}/{s['id']}_{i}.mp3")
            end = lines[i + 1]["at"] if i + 1 < len(lines) else s["minutes"] * 60
            if line["at"] + d + 2 > end:
                print(f"TROP LONG {lang} {s['id']} #{i}: {d:.1f}s à {line['at']}, suivante à {end}")
print("ok")
