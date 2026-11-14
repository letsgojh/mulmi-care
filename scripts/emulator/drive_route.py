#!/usr/bin/env python3
"""
실제 도로 좌표를 따라 주행을 재현해 Pleos Connect Emulator의 Virtual Console로 보낸다.

에뮬레이터 내비는 위치를 바꿀 수 없어(GPS를 넣어도 판교에 고정), 차량 신호만 실제 길에 맞춰 만든다.
- 속도: 커브 반경에 맞춰 줄인다 (옆 가속도 alat 이하), 가감속은 완만하게
- 조향: 곡률 → 앞바퀴 각도(자전거 모델, atan(축거 × 곡률)) → × 조향비 = Virtual Console 핸들 각도(오른쪽 +)
- 위치: scripts/emulator/map/state.json 에 써서 재현 지도(map/index.html)가 따라 그린다

사용 예 (저장소 루트에서):
  python scripts/emulator/drive_route.py --segments "8.0-9.3,14.6-16.6,37-40.5"
  python scripts/emulator/drive_route.py --init-only      # 첫 토막 시작 위치만 지도에 표시하고 끝냄

토막(km 구간) 사이는 순간 이동한다. 영상 편집점으로 쓴다(각 토막 시작에 "=== 토막 N" 출력).
경로 출처: © OpenStreetMap contributors (ODbL), OSRM으로 계산.
"""
import argparse
import json
import math
import os
import subprocess
import sys
import threading
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--route", default=os.path.join(HERE, "routes", "hangyeryeong.geojson"))
    p.add_argument("--segments", default="8.0-9.3,14.6-16.6,37-40.5", help="km 구간들, 쉼표로 구분")
    p.add_argument("--vmax", type=float, default=60, help="최고 속도 km/h")
    p.add_argument("--alat", type=float, default=2.0, help="커브 옆 가속도 한계 m/s²")
    p.add_argument("--accel", type=float, default=1.0, help="가속 m/s²")
    p.add_argument("--decel", type=float, default=1.5, help="감속 m/s² (5.4km/h/s: 구간 판단 감속 이벤트 정도, 급감속 아님)")
    # Virtual Console은 정수 값만 받는다. 0.5초마다 계단식으로 바꾸면 앱이 계단 하나를 급감속·급조향으로 본다
    # (리허설에서 급한 동작 없이 별이 떨어짐). 0.1초 간격으로 한 칸을 작게 한다.
    p.add_argument("--dt", type=float, default=0.1, help="갱신 간격 초")
    p.add_argument("--wheelbase", type=float, default=2.9)
    p.add_argument("--ratio", type=float, default=14.0, help="조향비 (앞바퀴 → 핸들)")
    p.add_argument("--console", default="http://127.0.0.1:8088")
    p.add_argument("--adb", default=os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb"))
    p.add_argument("--no-gps", action="store_true", help="adb emu geo fix를 보내지 않음")
    p.add_argument("--init-only", action="store_true")
    return p.parse_args()


# --- 경로 기하 ---

def load_route(path):
    coords = json.load(open(path, encoding="utf-8"))["geometry"]["coordinates"]
    lat0 = math.radians(sum(c[1] for c in coords) / len(coords))
    kx, ky = 111320 * math.cos(lat0), 110540
    return coords, kx, ky


def resample(coords, kx, ky, step=5.0):
    """5m 간격 점들: (s, x, y, lng, lat)"""
    pts = []
    s = 0.0
    carry = 0.0
    for (lng1, lat1), (lng2, lat2) in zip(coords, coords[1:]):
        x1, y1, x2, y2 = lng1 * kx, lat1 * ky, lng2 * kx, lat2 * ky
        d = math.hypot(x2 - x1, y2 - y1)
        if d == 0:
            continue
        pos = carry
        while pos <= d:
            f = pos / d
            pts.append((s + pos, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, lng1 + (lng2 - lng1) * f, lat1 + (lat2 - lat1) * f))
            pos += step
        carry = pos - d
        s += d
    return pts


def wrap(a):
    return (a + math.pi) % (2 * math.pi) - math.pi


def curvature(pts, w=3, smooth=4):
    """곡률(1/m). 오른쪽 커브(방위각 증가)가 +."""
    n = len(pts)
    heading = [math.atan2(pts[min(i + 1, n - 1)][1] - pts[i][1], pts[min(i + 1, n - 1)][2] - pts[i][2]) for i in range(n)]
    k = []
    for i in range(n):
        a, b = max(0, i - w), min(n - 1, i + w)
        ds = pts[b][0] - pts[a][0]
        k.append(wrap(heading[b] - heading[a]) / ds if ds > 0 else 0.0)
    out = []
    for i in range(n):
        win = k[max(0, i - smooth): i + smooth + 1]
        out.append(sum(win) / len(win))
    return out


def speed_profile(pts, k, vmax, alat, accel, decel):
    vlim = [min(vmax, math.sqrt(alat / abs(c))) if abs(c) > 1e-6 else vmax for c in k]
    v = vlim[:]
    for i in range(1, len(v)):
        ds = pts[i][0] - pts[i - 1][0]
        v[i] = min(v[i], math.sqrt(v[i - 1] ** 2 + 2 * accel * ds))
    for i in range(len(v) - 2, -1, -1):
        ds = pts[i + 1][0] - pts[i][0]
        v[i] = min(v[i], math.sqrt(v[i + 1] ** 2 + 2 * decel * ds))
    return v


# --- 출력 ---

class Console:
    def __init__(self, base):
        self.base = base
        self.alive = True
        threading.Thread(target=self._heartbeat, daemon=True).start()

    def _req(self, method, path):
        try:
            urllib.request.urlopen(urllib.request.Request(self.base + path, method=method), timeout=2).read()
        except Exception:
            pass

    def _heartbeat(self):
        # Virtual Console은 브라우저 세션 신호가 1초마다 있어야 값을 받는다
        while self.alive:
            self._req("POST", "/browser/session?client=mulmi-drive")
            time.sleep(1)

    def drive(self, kph, wheel_deg):
        self._req("PUT", f"/vehicle/controls?speed={kph:.0f}&accelerator=20&brake=0&steering={wheel_deg:.0f}")

    def close(self):
        self.alive = False
        self._req("POST", "/browser/session?client=mulmi-drive&closing=1")


def write_state(state):
    path = os.path.join(HERE, "map", "state.json")
    tmp = path + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False)
        os.replace(tmp, path)
    except OSError:
        pass  # 지도 서버가 읽는 중이면 다음 갱신 때 쓴다


def main():
    a = parse_args()
    coords, kx, ky = load_route(a.route)
    pts = resample(coords, kx, ky)
    k = curvature(pts)
    v = speed_profile(pts, k, a.vmax / 3.6, a.alat, a.accel, a.decel)
    segments = [tuple(float(x) * 1000 for x in seg.split("-")) for seg in a.segments.split(",")]
    index_of = lambda s: min(range(len(pts)), key=lambda i: abs(pts[i][0] - s))

    def state(i, seg_no, wheel, phase):
        return {"lng": pts[i][3], "lat": pts[i][4], "km": round(pts[i][0] / 1000, 2), "kph": round(v[i] * 3.6),
                "wheel": round(wheel), "segment": seg_no, "segments": [[s / 1000, e / 1000] for s, e in segments],
                "phase": phase, "t": time.time()}

    if a.init_only:
        write_state(state(index_of(segments[0][0]), 1, 0, "ready"))
        print("재현 지도에 시작 위치를 표시함")
        return

    console = Console(a.console)
    t0 = time.time()
    try:
        for seg_no, (s_from, s_to) in enumerate(segments, start=1):
            i = index_of(s_from)
            end = index_of(s_to)
            print(f"\n=== 토막 {seg_no}: {s_from/1000:.1f}~{s_to/1000:.1f}km ({time.time()-t0:.0f}초)", flush=True)
            s = pts[i][0]
            next_tick = time.time()
            last_print = last_gps = 0.0
            while i < end:
                delta = math.degrees(math.atan(a.wheelbase * k[i]))
                wheel = max(-470.0, min(470.0, delta * a.ratio))
                console.drive(v[i] * 3.6, wheel)
                now = time.time()
                if now - last_gps >= 1.0:
                    last_gps = now
                    write_state(state(i, seg_no, wheel, "driving"))
                    if not a.no_gps:
                        subprocess.run([a.adb, "emu", "geo", "fix", f"{pts[i][3]:.6f}", f"{pts[i][4]:.6f}"], capture_output=True)
                if now - last_print >= 5.0:
                    last_print = now
                    print(f"[{now-t0:4.0f}s] {pts[i][0]/1000:5.2f}km {v[i]*3.6:3.0f}km/h 핸들 {wheel:+4.0f}°", flush=True)
                s += v[i] * a.dt
                while i < end and pts[i][0] < s:
                    i += 1
                next_tick += a.dt
                time.sleep(max(0.0, next_tick - time.time()))
        console.drive(0, 0)
        write_state(state(end, len(segments), 0, "arrived"))
        print(f"\n=== 주행 끝 ({time.time()-t0:.0f}초)", flush=True)
    finally:
        console.close()


if __name__ == "__main__":
    sys.exit(main())
