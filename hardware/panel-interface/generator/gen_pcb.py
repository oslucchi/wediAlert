#!/usr/bin/env python3
"""Places and routes panel-interface.kicad_pcb from the schematic's exported netlist.

Connectivity comes only from out/netlist.net. Run through ./build.sh (needs KiCad's pcbnew).
KiCad's DRC, not this router, is what decides whether the result is valid.
"""
import heapq
import math
import os
import random
import re
import sys
import uuid

import pcbnew

PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAME = "panel-interface"
FPROOT = "/app/extensions/Library/Footprints/footprints"
NS = uuid.UUID("6c1e0c52-9d0a-4e39-9d7a-0a1b2c3d4e5f")

BOARD_W, BOARD_H = 92.0, 30.0
OX, OY = 100.0, 60.0  # where the board sits on the KiCad sheet
MOD_X, MOD_Y = 39.0, 0.6  # module's top-left corner

GRID = 0.2
CLR = 0.22  # router clearance; the DRC minimum is 0.2
SIGNAL_W = 0.4
VIA_D, VIA_DRILL = 0.8, 0.4
EDGE_KEEP = 0.5


def mm(v):
    return pcbnew.FromMM(v)


def uid(key):
    return str(uuid.uuid5(NS, key))


# x, y, rotation. Reasons: J1 faces the box wall as today, J4 is near Pi pins 35/37,
# J3 near HAT pins 2/6, module IN near J2.
PLACE = {
    "J1": (18.24, 3.0, 180),
    "J2": (33.5, 3.0, 180),
    "K1": (3.0, 15.31, 0),
    "K2": (19.0, 15.31, 0),
    "F1": (3.0, 18.5, 0),
    "F2": (19.0, 18.5, 0),
    "R9": (12.0, 18.5, 0),
    "R10": (12.0, 21.85, 0),
    "R1": (1.6, 21.85, 0),
    "R2": (17.0, 21.85, 0),
    "D1": (7.0, 27.6, 0),
    "D2": (20.4, 27.6, 0),
    "D3": (33.0, 8.6, 0),
    "D5": (28.0, 18.9, 0),
    "D4": (33.0, 13.4, 0),
    "D6": (33.0, 18.9, 0),
    "J4": (27.0, 27.6, 90),
    "U1": (MOD_X, MOD_Y, 0),
    "R7": (40.0, 27.8, 0),
    "R8": (45.6, 27.8, 0),
    "R5": (51.2, 27.8, 0),
    "R6": (56.8, 27.8, 0),
    "R3": (62.4, 27.8, 0),
    "R4": (68.0, 27.8, 0),
    "J3": (80.5, 27.7, 180),
    "H1": (2.7, 27.4, 0),
    "H2": (88.4, 27.6, 0),
}

# Silk reference positions: clear of pads, and readable with the parts fitted where possible.
REF_AT = {
    "F1": (5.54, 18.5), "F2": (21.54, 18.5), "R1": (5.41, 21.85), "R2": (20.81, 21.85),
    "K1": (8.08, 11.5), "K2": (24.08, 11.5), "J1": (10.6, 6.2), "J2": (31.0, 6.2),
    "D3": (37.2, 8.6), "D4": (37.2, 13.4), "D5": (29.27, 21.9), "D6": (34.27, 21.9),
    "R9": (13.27, 16.85), "R10": (15.9, 20.0), "D1": (8.27, 25.8), "D2": (21.67, 25.8),
    "J4": (31.5, 25.4), "J3": (77.5, 27.7),
    "R7": (41.27, 25.3), "R8": (46.87, 25.3), "R5": (52.47, 25.3), "R6": (58.07, 25.3),
    "R3": (63.67, 25.3), "R4": (69.27, 25.3),
}
# These carry the supply current, or would otherwise be starved by neighbouring tracks.
SOLID_GND = {("U1", "2"), ("U1", "4"), ("J2", "2"), ("J3", "2"), ("R10", "2"), ("D6", "1")}

# Connections that carry the Pi and modem current get wide tracks, routed first.
WIDE = [
    ("+12V", "J2", "1", "U1", "1", 1.5),
    ("+5V", "U1", "3", "J3", "1", 2.0),
    ("GND", "R9", "2", "R10", "2", 0.5),
]
# GND is carried by pours on both layers; the top pour stays whole under the module.
NET_W = {"+12V": 0.5}

EXPECTED = {
    ("K1", "1"): ("R1", "2"), ("K2", "1"): ("R2", "2"),
    ("J1", "1"): ("F1", "2"), ("J1", "2"): ("R1", "1"), ("J1", "3"): ("F2", "2"), ("J1", "4"): ("R2", "1"),
    ("K1", "7"): ("R7", "1"), ("K2", "7"): ("R8", "1"),
    ("R7", "2"): ("J4", "2"), ("R8", "2"): ("J4", "3"),
    ("K1", "8"): ("J4", "1"), ("K2", "8"): ("J4", "1"),
    ("K1", "10"): ("J4", "4"), ("K2", "10"): ("J4", "4"), ("D1", "2"): ("J4", "4"),
    ("D1", "1"): ("K1", "1"), ("D2", "1"): ("K2", "1"),
    ("J2", "1"): ("F1", "1"), ("J3", "1"): ("U1", "3"), ("J3", "2"): ("J4", "4"), ("U1", "2"): ("J4", "4"),
}


def read_netlist():
    t = open(os.path.join(PROJ, "out", "netlist.net")).read()
    comps = {}
    for m in re.finditer(r'\(comp\s+\(ref "([^"]+)"\)\s*\(value "([^"]*)"\)\s*\(footprint "([^"]*)"\)(.*?)\(tstamps\s+"([^"]+)"', t, re.S):
        comps[m.group(1)] = dict(value=m.group(2), fp=m.group(3), uuid=m.group(5))
    nets = {}
    for chunk in re.split(r'\(net\s+\(code', t[t.index("(nets") :])[1:]:
        name = re.search(r'\(name "([^"]*)"\)', chunk).group(1)
        nets[name] = re.findall(r'\(node\s+\(ref "([^"]+)"\)\s*\(pin "([^"]+)"\)', chunk)
    if not comps or not nets:
        sys.exit("could not read the netlist")
    return comps, nets


def check_expected(nets):
    where = {}
    for n, nodes in nets.items():
        for node in nodes:
            where[node] = n
    bad = [f"{a} != {b}" for a, b in EXPECTED.items() if where.get(a) is None or where.get(a) != where.get(b)]
    if bad:
        sys.exit("schematic connectivity differs from the design intent:\n" + "\n".join(map(str, bad)))
    print("netlist matches the design intent:", len(EXPECTED), "checks")


def lib_path(lib):
    if lib == "panel_interface":
        return os.path.join(PROJ, "panel_interface.pretty")
    return os.path.join(FPROOT, lib + ".pretty")


class Cap:
    """Copper as a segment with a radius. net=None blocks everyone (edges, holes)."""
    __slots__ = ("x1", "y1", "x2", "y2", "r", "net", "layers")

    def __init__(self, x1, y1, x2, y2, r, net, layers):
        self.x1, self.y1, self.x2, self.y2, self.r, self.net, self.layers = x1, y1, x2, y2, r, net, layers

    def dist(self, x, y):
        dx, dy = self.x2 - self.x1, self.y2 - self.y1
        ll = dx * dx + dy * dy
        t = 0 if ll == 0 else max(0, min(1, ((x - self.x1) * dx + (y - self.y1) * dy) / ll))
        return math.hypot(x - self.x1 - t * dx, y - self.y1 - t * dy) - self.r


NX, NY = int(BOARD_W / GRID) + 1, int(BOARD_H / GRID) + 1
F, B = 0, 1


def raster(caps, net, margin):
    """Returns per-layer bytearrays: 1 = this net may not use the cell."""
    grids = [bytearray(NX * NY), bytearray(NX * NY)]
    for c in caps:
        if c.net == net and net is not None:
            continue
        m = c.r + margin
        i1 = max(0, int((min(c.x1, c.x2) - m) / GRID))
        i2 = min(NX - 1, int((max(c.x1, c.x2) + m) / GRID) + 1)
        j1 = max(0, int((min(c.y1, c.y2) - m) / GRID))
        j2 = min(NY - 1, int((max(c.y1, c.y2) + m) / GRID) + 1)
        for j in range(j1, j2 + 1):
            for i in range(i1, i2 + 1):
                if c.dist(i * GRID, j * GRID) < margin:
                    for l in c.layers:
                        grids[l][j * NX + i] = 1
    return grids


DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)]


def astar(starts, goals, block, via_block, allow_top_under_module=False):
    goalset = set(goals)
    gi, gj = goals[0][0], goals[0][1]

    def h(i, j):
        dx, dy = abs(i - gi), abs(j - gj)
        return (max(dx, dy) + 0.414 * min(dx, dy))

    best = {}
    pq = []
    came = {}
    n = 0
    for (i, j, l) in starts:
        st = (i, j, l, -1)
        best[st] = 0
        heapq.heappush(pq, (h(i, j), n, st))
        n += 1
    while pq:
        _, _, st = heapq.heappop(pq)
        i, j, l, d = st
        g = best[st]
        if (i, j, l) in goalset:
            path = [st]
            while path[-1] in came:
                path.append(came[path[-1]])
            return [(p[0], p[1], p[2]) for p in reversed(path)]
        for k, (dx, dy) in enumerate(DIRS):
            ni, nj = i + dx, j + dy
            if not (0 <= ni < NX and 0 <= nj < NY):
                continue
            if block[l][nj * NX + ni] and (ni, nj, l) not in goalset:
                continue
            if l == F and not allow_top_under_module and ni * GRID > MOD_X - 0.4 and nj * GRID < MOD_Y + 23.2:
                continue
            c = 1.414 if dx and dy else 1.0
            if d != -1 and d != k:
                c += 2.0
            if l == F:
                c += 0.05  # a slight preference for the bottom layer
            ns = (ni, nj, l, k)
            ng = g + c
            if ng < best.get(ns, 1e18):
                best[ns] = ng
                came[ns] = st
                heapq.heappush(pq, (ng + h(ni, nj), n, ns))
                n += 1
        ol = 1 - l
        if not via_block[nj_idx(j, i)] and not block[ol][nj_idx(j, i)]:
            if not (ol == F and i * GRID > MOD_X - 0.4 and j * GRID < MOD_Y + 23.2):
                ns = (i, j, ol, -1)
                ng = g + 18
                if ng < best.get(ns, 1e18):
                    best[ns] = ng
                    came[ns] = st
                    heapq.heappush(pq, (ng + h(i, j), n, ns))
                    n += 1
    return None


def nj_idx(j, i):
    return j * NX + i


def cell(x, y):
    return int(round(x / GRID)), int(round(y / GRID))


def route_all(pads, nets, seed):
    """pads: {(ref,pin): (x,y,cap)}. Returns (tracks, vias, failures)."""
    rng = random.Random(seed)
    base = []
    for key, (x, y, cap) in pads.items():
        base.append(cap)
    # board edges
    for s in ((0, 0, BOARD_W, 0), (BOARD_W, 0, BOARD_W, BOARD_H), (BOARD_W, BOARD_H, 0, BOARD_H), (0, BOARD_H, 0, 0)):
        base.append(Cap(s[0], s[1], s[2], s[3], EDGE_KEEP - CLR, None, (F, B)))
    for ref in ("H1", "H2"):
        x, y, _ = PLACE[ref]
        base.append(Cap(x, y, x, y, 1.7, None, (F, B)))

    tracks, vias = [], []
    caps = list(base)
    fails = []

    def connect(net, tree_pads, target, w):
        nonlocal caps
        block = raster(caps, net, CLR + w / 2)
        via_block = raster(caps, net, CLR + VIA_D / 2)
        via_any = bytearray(a | b for a, b in zip(via_block[0], via_block[1]))
        starts = []
        for key in tree_pads:
            i, j = cell(*pads[key][:2])
            starts += [(i, j, F), (i, j, B)]
        for (x1, y1, x2, y2, tw, l, tn) in tracks:
            if tn != net:
                continue
            steps = max(1, int(math.hypot(x2 - x1, y2 - y1) / GRID))
            for s in range(steps + 1):
                i, j = cell(x1 + (x2 - x1) * s / steps, y1 + (y2 - y1) * s / steps)
                starts.append((i, j, l))
        tx, ty = pads[target][:2]
        ti, tj = cell(tx, ty)
        path = astar(starts, [(ti, tj, F), (ti, tj, B)], block, via_any)
        if path is None:
            return False
        # exact pad centres at both ends, when the path starts on a pad
        pts = [(p[0] * GRID, p[1] * GRID, p[2]) for p in path]
        start_pt = pts[0]
        for key in tree_pads:
            if cell(*pads[key][:2]) == (path[0][0], path[0][1]):
                x, y = pads[key][:2]
                add_track(start_pt[0], start_pt[1], x, y, w, start_pt[2], net)
                break
        add_track(pts[-1][0], pts[-1][1], tx, ty, w, pts[-1][2], net)
        run = pts[0]
        prev = pts[0]
        pd = None
        for p in pts[1:]:
            if p[2] != prev[2]:
                if prev != run:
                    add_track(run[0], run[1], prev[0], prev[1], w, prev[2], net)
                vias.append((p[0], p[1], net))
                caps.append(Cap(p[0], p[1], p[0], p[1], VIA_D / 2, net, (F, B)))
                run, prev, pd = p, p, None
                continue
            d = (round(p[0] - prev[0], 3), round(p[1] - prev[1], 3))
            if pd is not None and d != pd:
                add_track(run[0], run[1], prev[0], prev[1], w, prev[2], net)
                run = prev
            pd, prev = d, p
        if prev[:2] != run[:2]:
            add_track(run[0], run[1], prev[0], prev[1], w, prev[2], net)
        return True

    def add_track(x1, y1, x2, y2, w, l, net):
        if abs(x1 - x2) < 1e-6 and abs(y1 - y2) < 1e-6:
            return
        tracks.append((x1, y1, x2, y2, w, l, net))
        caps.append(Cap(x1, y1, x2, y2, w / 2, net, (l,)))

    for net, ra, pa, rb, pb, w in WIDE:
        if not connect(net, [(ra, pa)], (rb, pb), w):
            fails.append((net, ra, rb))

    order = [n for n in nets if n != "GND" and len(nets[n]) > 1 and not (len(nets[n]) == 2 and n.startswith("+"))]
    rng.shuffle(order)
    order.sort(key=lambda n: 0 if n == "+12V" else 1)
    for net in order:
        nodes = [tuple(x) for x in nets[net]]
        done = [n for n in nodes if any(w[0] == net and ((w[1], w[2]) == n or (w[3], w[4]) == n) for w in WIDE)]
        if not done:
            done = [nodes[0]]
        todo = [n for n in nodes if n not in done]
        w = NET_W.get(net, SIGNAL_W)
        while todo:
            todo.sort(key=lambda n: min(math.hypot(pads[n][0] - pads[t][0], pads[n][1] - pads[t][1]) for t in done))
            n = todo.pop(0)
            if not connect(net, done, n, w):
                fails.append((net, n))
            done.append(n)
    return tracks, vias, fails


def main():
    comps, nets = read_netlist()
    check_expected(nets)
    path = os.path.join(PROJ, NAME + ".kicad_pcb")
    board = pcbnew.NewBoard(path)
    ds = board.GetDesignSettings()
    ds.SetCopperLayerCount(2)
    ds.m_TrackMinWidth = mm(0.3)
    ds.m_MinClearance = mm(0.2)
    ds.m_ViasMinSize = mm(0.7)

    netitems = {}
    pin_net = {}
    for name, nodes in nets.items():
        ni = pcbnew.NETINFO_ITEM(board, name)
        board.Add(ni)
        netitems[name] = ni
        for n in nodes:
            pin_net[tuple(n)] = name

    pads = {}
    for ref, c in comps.items():
        lib, fpname = c["fp"].split(":")
        fp = pcbnew.FootprintLoad(lib_path(lib), fpname)
        if fp is None:
            sys.exit("footprint not found: " + c["fp"])
        fp.SetFPID(pcbnew.LIB_ID(lib, fpname))
        fp.SetReference(ref)
        fp.SetValue(c["value"])
        fp.SetPath(pcbnew.KIID_PATH("/" + c["uuid"]))
        fp.SetSheetname("/")
        fp.SetSheetfile(NAME + ".kicad_sch")
        board.Add(fp)
        x, y, rot = PLACE[ref]
        fp.SetPosition(pcbnew.VECTOR2I(mm(OX + x), mm(OY + y)))
        fp.SetOrientationDegrees(rot)
        fp.Value().SetVisible(False)
        if ref in REF_AT:
            rx, ry = REF_AT[ref]
            fp.Reference().SetPosition(pcbnew.VECTOR2I(mm(OX + rx), mm(OY + ry)))
            fp.Reference().SetTextAngleDegrees(0)
            fp.Reference().SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
            fp.Reference().SetTextThickness(mm(0.12))
        if ref in ("H1", "H2"):
            fp.Reference().SetVisible(False)
        for p in fp.Pads():
            key = (ref, p.GetNumber())
            net = pin_net.get(key)
            if key in SOLID_GND:
                p.SetLocalZoneConnection(pcbnew.ZONE_CONNECTION_FULL)
            if net:
                p.SetNet(netitems[net])
            pos = p.GetPosition()
            px, py = pcbnew.ToMM(pos.x) - OX, pcbnew.ToMM(pos.y) - OY
            bb = p.GetBoundingBox()
            bw, bh = pcbnew.ToMM(bb.GetWidth()), pcbnew.ToMM(bb.GetHeight())
            r = min(bw, bh) / 2
            ext = (max(bw, bh) - min(bw, bh)) / 2
            if p.GetShape() in (pcbnew.PAD_SHAPE_RECT, pcbnew.PAD_SHAPE_ROUNDRECT):
                r *= 1.42  # reach of a square pad's corners
            if bw >= bh:
                cap = Cap(px - ext, py, px + ext, py, r + 0.05, net or "~", (F, B))
            else:
                cap = Cap(px, py - ext, px, py + ext, r + 0.05, net or "~", (F, B))
            pads[key] = (px, py, cap)
    for k in (("J1", "1"), ("J1", "4"), ("J2", "1"), ("J4", "1"), ("J4", "4"), ("K1", "1"), ("K1", "10"), ("J3", "1"), ("U1", "3")):
        print(k, "at", tuple(round(v, 2) for v in pads[k][:2]))

    best = None
    # Seed 3 is the order that routes this placement fully. If the placement changes,
    # set ROUTE_SEARCH=1 to try others (about 30 s each).
    for seed in (range(40) if os.environ.get("ROUTE_SEARCH") else [3]):
        tr, vi, fa = route_all(pads, nets, seed)
        score = (len(fa), len(vi), sum(math.hypot(t[2] - t[0], t[3] - t[1]) for t in tr))
        print("try", seed, "failed", len(fa), "vias", len(vi), "length", round(score[2]))
        if best is None or score < best[0]:
            best = (score, tr, vi, fa)
        if len(fa) == 0 and len(vi) <= 16:
            break
    _, tracks, vias, fails = best
    if fails:
        print("UNROUTED:", fails)

    for x1, y1, x2, y2, w, l, net in tracks:
        t = pcbnew.PCB_TRACK(board)
        t.SetStart(pcbnew.VECTOR2I(mm(OX + x1), mm(OY + y1)))
        t.SetEnd(pcbnew.VECTOR2I(mm(OX + x2), mm(OY + y2)))
        t.SetWidth(mm(w))
        t.SetLayer(pcbnew.F_Cu if l == F else pcbnew.B_Cu)
        t.SetNet(netitems[net])
        board.Add(t)
    for x, y, net in vias:
        v = pcbnew.PCB_VIA(board)
        v.SetPosition(pcbnew.VECTOR2I(mm(OX + x), mm(OY + y)))
        v.SetWidth(mm(VIA_D))
        v.SetDrill(mm(VIA_DRILL))
        v.SetNet(netitems[net])
        board.Add(v)

    # Board outline
    corners = [(0, 0), (BOARD_W, 0), (BOARD_W, BOARD_H), (0, BOARD_H)]
    for a, b in zip(corners, corners[1:] + corners[:1]):
        s = pcbnew.PCB_SHAPE(board)
        s.SetShape(pcbnew.SHAPE_T_SEGMENT)
        s.SetStart(pcbnew.VECTOR2I(mm(OX + a[0]), mm(OY + a[1])))
        s.SetEnd(pcbnew.VECTOR2I(mm(OX + b[0]), mm(OY + b[1])))
        s.SetLayer(pcbnew.Edge_Cuts)
        s.SetWidth(mm(0.1))
        board.Add(s)

    # GND on both layers
    for layer in (pcbnew.F_Cu, pcbnew.B_Cu):
        z = pcbnew.ZONE(board)
        z.SetLayer(layer)
        z.SetNet(netitems["GND"])
        z.SetLocalClearance(mm(0.3))
        z.SetMinThickness(mm(0.25))
        z.SetAssignedPriority(0)
        z.SetThermalReliefSpokeWidth(mm(0.6))
        z.SetThermalReliefGap(mm(0.3))
        o = z.Outline()
        o.NewOutline()
        for cx, cy in corners:
            o.Append(mm(OX + cx), mm(OY + cy))
        board.Add(z)

    def silk(text, x, y, layer=pcbnew.F_SilkS, size=0.8, mirror=False, rot=0):
        t = pcbnew.PCB_TEXT(board)
        t.SetText(text)
        t.SetPosition(pcbnew.VECTOR2I(mm(OX + x), mm(OY + y)))
        t.SetLayer(layer)
        t.SetTextSize(pcbnew.VECTOR2I(mm(size), mm(size)))
        t.SetTextThickness(mm(size * 0.15))
        t.SetMirrored(mirror)
        if rot:
            t.SetTextAngleDegrees(rot)
        board.Add(t)

    # Bottom silk: the wiring guide stays readable once the connectors are fitted.
    bs = pcbnew.B_SilkS
    for i, s in enumerate(("L1 OUT", "L1 RET", "L2 OUT", "L2 RET")):
        px = pads[("J1", str(i + 1))][0]
        silk(s.replace(" ", ""), px, 5.9, bs, 0.8, True)
    silk("+12V", pads[("J2", "1")][0], 5.9, bs, 0.8, True)
    silk("GND", pads[("J2", "2")][0], 5.9, bs, 0.8, True)
    for i, s in enumerate(("3V3", "G19", "G26", "GND")):
        silk(s, pads[("J4", str(i + 1))][0], 25.7, bs, 0.8, True)
    silk("+5V", pads[("J3", "1")][0], 25.2, bs, 0.8, True)
    silk("GND", pads[("J3", "2")][0], 25.2, bs, 0.8, True)
    silk("wediAlert panel interface rev 1", 64, 12, bs, 1.2, True)

    board.BuildConnectivity()
    pcbnew.ZONE_FILLER(board).Fill(board.Zones())
    board.Save(path)
    print("board saved:", len(tracks), "tracks,", len(vias), "vias,", len(fails), "unrouted")


if __name__ == "__main__":
    main()
