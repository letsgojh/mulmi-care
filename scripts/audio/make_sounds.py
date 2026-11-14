"""멀미케어 소리 에셋 만들기 (F4.1 "앱이 직접 만든" 소리).

샘플이나 외부 음원 없이 수식(사인파·잡음·감쇠)으로만 합성한다.
앱(app/src/main/assets/sounds)과 시연 영상이 같은 파일을 쓴다.

사용: python -I scripts/audio/make_sounds.py
"""
import os
import wave

import numpy as np

RATE = 44_100
BPM = 120
STEP = 60 / BPM / 4  # 16분음표 길이(초)
BARS = 4
LOOP_FRAMES = int(round(BARS * 16 * STEP * RATE))  # 8초, 정확히 이어지게
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "sounds")
rng = np.random.default_rng(7)  # 같은 파일이 나오도록 고정


def t_of(sec):
    return np.arange(int(sec * RATE)) / RATE


def note(name_or_hz):
    if isinstance(name_or_hz, (int, float)):
        return float(name_or_hz)
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    n, octave = name_or_hz[:-1], int(name_or_hz[-1])
    return 440.0 * 2 ** ((names[n] + (octave - 4) * 12) / 12)


def lowpass(x, a):
    """1차 저역 통과(a: 0~1, 작을수록 어둡다)."""
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


# ---------------- 악기 ----------------

def kick(heavy=1.0):
    t = t_of(0.35)
    freq = 45 + 110 * np.exp(-t * 28)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    body = np.sin(phase) * np.exp(-t * 9)
    click = rng.standard_normal(len(t)) * np.exp(-t * 400) * 0.25
    return (body + click) * heavy


def snare():
    t = t_of(0.22)
    noise = rng.standard_normal(len(t))
    noise = noise - lowpass(noise, 0.15)  # 높은 쪽만
    return noise * np.exp(-t * 22) * 0.55 + np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * 0.45


def clap():
    t = t_of(0.25)
    noise = rng.standard_normal(len(t))
    noise = noise - lowpass(noise, 0.2)
    env = np.exp(-t * 18) + 0.6 * np.exp(-np.maximum(0, t - 0.012) * 60) * (t > 0.012) + 0.5 * np.exp(-np.maximum(0, t - 0.024) * 60) * (t > 0.024)
    return noise * env * 0.45


def hat(open_=False):
    t = t_of(0.3 if open_ else 0.05)
    noise = rng.standard_normal(len(t))
    noise = noise - lowpass(noise, 0.5)
    return noise * np.exp(-t * (12 if open_ else 90)) * 0.5


def shaker():
    t = t_of(0.08)
    noise = rng.standard_normal(len(t))
    noise = noise - lowpass(noise, 0.35)
    return noise * np.sin(np.pi * t / 0.08) * 0.35


def bass(f, dur):
    t = t_of(dur)
    tone = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t) + 0.12 * np.sin(2 * np.pi * 3 * f * t)
    return tone * np.exp(-t * 5) * np.minimum(1, t / 0.005)


def marimba(f, dur=0.35):
    t = t_of(dur)
    return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t * 40)) * np.exp(-t * 9) * np.minimum(1, t / 0.002)


def flute(f, dur=0.32):
    t = t_of(dur)
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.5 * t)
    phase = 2 * np.pi * f * np.cumsum(vib) / RATE
    breath = rng.standard_normal(len(t)) * 0.04
    env = np.minimum(1, t / 0.03) * np.exp(-t * 4)
    return (np.sin(phase) + 0.15 * np.sin(2 * phase) + breath) * env


def synth(f, dur=0.22):
    t = t_of(dur)
    sq = sum(np.sin(2 * np.pi * f * k * t) / k for k in (1, 3, 5, 7))  # 부드러운 사각파
    return sq * np.exp(-t * 10) * np.minimum(1, t / 0.003) * 0.8


def stab(freqs, dur=0.12):
    t = t_of(dur)
    return sum(np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2 * f * t) for f in freqs) / len(freqs) * np.exp(-t * 25)


def bell(f, dur=0.5):
    t = t_of(dur)
    return (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * 2.76 * f * t) * np.exp(-t * 6)) * np.exp(-t * 7) * np.minimum(1, t / 0.002)


def woodblock(f, dur=0.12):
    t = t_of(dur)
    return (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * 2.3 * f * t)) * np.exp(-t * 45) * np.minimum(1, t / 0.001)


# ---------------- 비트 ----------------

def place(buf, step, sound, gain=1.0, pan=0.0):
    i = int(round(step * STEP * RATE))
    end = min(len(buf), i + len(sound))
    seg = sound[: end - i] * gain
    buf[i:end, 0] += seg * (1 - max(0, pan))
    buf[i:end, 1] += seg * (1 + min(0, pan))
    # 루프 끝을 넘는 꼬리는 처음으로 감는다(이음매 없이 반복)
    rest = sound[end - i:] * gain
    if len(rest):
        buf[: len(rest), 0] += rest * (1 - max(0, pan))
        buf[: len(rest), 1] += rest * (1 + min(0, pan))


THEMES = {
    # 이름: (코드 진행 근음, 멜로디 음계, 멜로디 악기, 강한 킥 여부)
    "sea": dict(roots=["C2", "G2", "A2", "F2"], chords=[["C4", "E4", "G4"], ["B3", "D4", "G4"], ["C4", "E4", "A4"], ["C4", "F4", "A4"]],
                scale=["C5", "D5", "E5", "G5", "A5", "C6"], lead=marimba, perc="hat", heavy=1.0),
    "forest": dict(roots=["G2", "D2", "E2", "C2"], chords=[["B3", "D4", "G4"], ["A3", "D4", "F#4"], ["B3", "E4", "G4"], ["C4", "E4", "G4"]],
                   scale=["G5", "A5", "B5", "D6", "E6", "G6"], lead=flute, perc="shaker", heavy=0.9),
    "space": dict(roots=["A1", "F2", "C2", "G2"], chords=[["A3", "C4", "E4"], ["A3", "C4", "F4"], ["G3", "C4", "E4"], ["G3", "B3", "D4"]],
                  scale=["A4", "C5", "D5", "E5", "G5", "A5"], lead=synth, perc="hat", heavy=1.25),
}

# 16스텝 패턴 (1 = 친다). 마디마다 조금씩 달라 리듬감이 생긴다
KICK = [[0, 6, 8, 11], [0, 6, 8, 10], [0, 6, 8, 11], [0, 3, 6, 8, 10, 13]]
SNARE = [4, 12]
BASS = [0, 3, 6, 8, 10, 14]
STABS = [2, 6, 10, 14]
# 멜로디: (스텝, 음계 번호, 길이). 1·3마디는 묻고, 2·4마디는 답한다
MELODY = [
    [(0, 2, 2), (2, 3, 2), (4, 4, 2), (7, 3, 1), (8, 2, 2), (11, 1, 1), (12, 2, 4)],
    [(0, 4, 2), (3, 3, 1), (4, 2, 2), (6, 1, 2), (8, 0, 3), (12, 1, 2), (14, 2, 2)],
    [(0, 2, 2), (2, 3, 2), (4, 4, 2), (7, 5, 1), (8, 4, 2), (10, 3, 2), (12, 4, 4)],
    [(0, 5, 2), (2, 4, 2), (4, 3, 1), (5, 2, 1), (6, 1, 2), (8, 2, 2), (10, 0, 6)],
]


def beat(theme):
    th = THEMES[theme]
    buf = np.zeros((LOOP_FRAMES, 2))
    k = kick(th["heavy"])
    for bar in range(BARS):
        base = bar * 16
        for s in KICK[bar]:
            place(buf, base + s, k, 0.95)
        for s in SNARE:
            place(buf, base + s, snare() if bar % 2 == 0 else clap(), 0.6)
        if bar == BARS - 1:
            for s in (13, 14, 15):  # 마지막 마디 끝 필인
                place(buf, base + s, snare(), 0.35)
        for s in range(0, 16, 2):
            if th["perc"] == "shaker":
                place(buf, base + s, shaker(), 0.55 if s % 4 else 0.35, pan=0.3)
                place(buf, base + s + 1, shaker(), 0.25, pan=0.3)
            else:
                place(buf, base + s, hat(open_=(s == 14 and bar % 2 == 1)), 0.4 if s % 4 else 0.22, pan=-0.25)
        root = note(th["roots"][bar])
        for i, s in enumerate(BASS):
            f = root * (2 if i in (2, 5) else 1)  # 옥타브를 오가며 통통 튄다
            place(buf, base + s, bass(f, 0.28), 0.5)
        chord = [note(n) for n in th["chords"][bar]]
        for s in STABS:
            place(buf, base + s, stab(chord), 0.22, pan=0.2)
        scale = [note(n) for n in th["scale"]]
        for s, deg, ln in MELODY[bar]:
            place(buf, base + s, th["lead"](scale[deg], ln * STEP + 0.15), 0.32, pan=-0.1)
    return buf / np.max(np.abs(buf)) * 0.55


# ---------------- 효과음 ----------------

def stereo(mono, pan):
    """pan -1 = 왼쪽만, 1 = 오른쪽만."""
    return np.stack([mono * (1 - max(0, pan)), mono * (1 + min(0, pan))], axis=1)


def seq(*parts):
    """(소리, 시작 초) 들을 이어 붙인다."""
    end = max(int(start * RATE) + len(s) for s, start in parts)
    out = np.zeros(end)
    for s, start in parts:
        i = int(start * RATE)
        out[i: i + len(s)] += s
    return out


def cue_left():
    # 왼쪽: 낮은 나무 소리 두 번, 내려가는 음 "똑-똑"
    m = seq((woodblock(note("G5")), 0.0), (woodblock(note("C5")), 0.09), (marimba(note("C4"), 0.25) * 0.5, 0.09))
    return stereo(m / np.max(np.abs(m)) * 0.85, -1.0)


def cue_right():
    # 오른쪽: 높은 종소리 두 번, 올라가는 음 "띵-딩"
    m = seq((bell(note("E6"), 0.3), 0.0), (bell(note("B6"), 0.35), 0.09))
    return stereo(m / np.max(np.abs(m)) * 0.85, 1.0)


def star_lost():
    m = seq((bell(note("E5"), 0.18), 0.0), (bell(note("C5"), 0.18), 0.1), (bell(note("A4"), 0.3), 0.2))
    return stereo(m / np.max(np.abs(m)) * 0.6, 0.0)


def chime():
    m = seq((bell(note("C6"), 0.25), 0.0), (bell(note("G6"), 0.4), 0.12))
    return stereo(m / np.max(np.abs(m)) * 0.5, 0.0)


def write(name, data):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print(name, f"{len(data) / RATE:.2f}s")


if __name__ == "__main__":
    for th in THEMES:
        write(f"beat_{th}.wav", beat(th))
    write("cue_left.wav", cue_left())
    write("cue_right.wav", cue_right())
    write("star_lost.wav", star_lost())
    write("chime.wav", chime())
