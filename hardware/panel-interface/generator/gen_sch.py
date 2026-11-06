#!/usr/bin/env python3
"""Writes panel-interface.kicad_sch, the project libraries and the project files.

Run through ./build.sh (inside the KiCad Flatpak, which holds the stock symbol libraries).
"""
import math
import os
import re
import uuid

PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAME = "panel-interface"
LIBDIR = "/app/extensions/Library/Symbols/symbols"
NS = uuid.UUID("6c1e0c52-9d0a-4e39-9d7a-0a1b2c3d4e5f")


def uid(key):
    return str(uuid.uuid5(NS, key))


# ---------------------------------------------------------------- s-expression helpers
def extract_symbol(text, name):
    m = re.search(r'^\s*\(symbol "%s"\s*$' % re.escape(name), text, re.M)
    if not m:
        raise SystemExit("symbol not found: " + name)
    i = text.index("(", m.start())
    depth, in_str, j = 0, False, i
    while True:
        c = text[j]
        if in_str:
            if c == "\\":
                j += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[i : j + 1]
        j += 1


def pins_of(symtext):
    pins = {}
    for m in re.finditer(
        r'\(pin\s+\w+\s+\w+\s*\(at\s+(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)\).*?\(number\s+"([^"]+)"',
        symtext,
        re.S,
    ):
        pins[m.group(4)] = (float(m.group(1)), float(m.group(2)))
    return pins


RELAY_SYM = """(symbol "FP2_Monostable"
		(pin_names (offset 0.6))
		(exclude_from_sim no)
		(in_bom yes)
		(on_board yes)
		(property "Reference" "K" (at -12.7 13.97 0) (effects (font (size 1.27 1.27)) (justify left)))
		(property "Value" "FP2_Monostable" (at -12.7 11.43 0) (effects (font (size 1.27 1.27)) (justify left)))
		(property "Footprint" "panel_interface:Relay_DPDT_Axicom_FP2" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
		(property "Datasheet" "https://www.te.com/commerce/DocumentDelivery/DDEController?Action=srchrtrv&DocNm=108-98005&DocType=DS" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
		(property "Description" "Axicom/Alcatel FP2 monostable, polarized coil, 2 form C" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
		(symbol "FP2_Monostable_0_1"
			(rectangle (start -10.16 10.16) (end 10.16 -10.16)
				(stroke (width 0.254) (type default)) (fill (type background)))
			(rectangle (start -6.35 2.54) (end -3.81 -2.54)
				(stroke (width 0.254) (type default)) (fill (type none)))
			(polyline (pts (xy -5.08 2.54) (xy -5.08 5.08) (xy -10.16 5.08))
				(stroke (width 0) (type default)) (fill (type none)))
			(polyline (pts (xy -5.08 -2.54) (xy -5.08 -5.08) (xy -10.16 -5.08))
				(stroke (width 0) (type default)) (fill (type none)))
			(text "+" (at -7.62 3.81 0) (effects (font (size 1.27 1.27))))
			(text "-" (at -7.62 -3.81 0) (effects (font (size 1.27 1.27))))
		)
		(symbol "FP2_Monostable_1_1"
			(pin passive line (at -12.7 5.08 0) (length 2.54) (name "" (effects (font (size 1.27 1.27)))) (number "1" (effects (font (size 1.27 1.27)))))
			(pin passive line (at -12.7 -5.08 0) (length 2.54) (name "" (effects (font (size 1.27 1.27)))) (number "10" (effects (font (size 1.27 1.27)))))
			(pin passive line (at 12.7 5.08 180) (length 2.54) (name "COM" (effects (font (size 1.27 1.27)))) (number "8" (effects (font (size 1.27 1.27)))))
			(pin passive line (at 12.7 0 180) (length 2.54) (name "NC" (effects (font (size 1.27 1.27)))) (number "9" (effects (font (size 1.27 1.27)))))
			(pin passive line (at 12.7 -5.08 180) (length 2.54) (name "NO" (effects (font (size 1.27 1.27)))) (number "7" (effects (font (size 1.27 1.27)))))
			(pin passive line (at -2.54 -12.7 90) (length 2.54) (name "NC" (effects (font (size 1.27 1.27)))) (number "2" (effects (font (size 1.27 1.27)))))
			(pin passive line (at 0 -12.7 90) (length 2.54) (name "COM" (effects (font (size 1.27 1.27)))) (number "3" (effects (font (size 1.27 1.27)))))
			(pin passive line (at 2.54 -12.7 90) (length 2.54) (name "NO" (effects (font (size 1.27 1.27)))) (number "4" (effects (font (size 1.27 1.27)))))
			(pin no_connect line (at 7.62 -12.7 90) (length 2.54) (name "" (effects (font (size 1.27 1.27)))) (number "5" (effects (font (size 1.27 1.27)))))
			(pin no_connect line (at 7.62 12.7 270) (length 2.54) (name "" (effects (font (size 1.27 1.27)))) (number "6" (effects (font (size 1.27 1.27)))))
		)
		(embedded_fonts no)
	)"""

BUCK_SYM = """(symbol "Buck_Module_XL4015"
		(pin_names (offset 1.016))
		(exclude_from_sim no)
		(in_bom yes)
		(on_board yes)
		(property "Reference" "U" (at -10.16 8.89 0) (effects (font (size 1.27 1.27)) (justify left)))
		(property "Value" "Buck_Module_XL4015" (at -10.16 6.35 0) (effects (font (size 1.27 1.27)) (justify left)))
		(property "Footprint" "panel_interface:Buck_Module_XL4015_52x23mm" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
		(property "Datasheet" "" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
		(property "Description" "12 V to 5 V step-down module with trimmer, wired by its four corner pads" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
		(symbol "Buck_Module_XL4015_0_1"
			(rectangle (start -10.16 5.08) (end 10.16 -5.08)
				(stroke (width 0.254) (type default)) (fill (type background)))
		)
		(symbol "Buck_Module_XL4015_1_1"
			(pin power_in line (at -12.7 2.54 0) (length 2.54) (name "IN+" (effects (font (size 1.27 1.27)))) (number "1" (effects (font (size 1.27 1.27)))))
			(pin power_in line (at -12.7 -2.54 0) (length 2.54) (name "IN-" (effects (font (size 1.27 1.27)))) (number "2" (effects (font (size 1.27 1.27)))))
			(pin power_out line (at 12.7 2.54 180) (length 2.54) (name "OUT+" (effects (font (size 1.27 1.27)))) (number "3" (effects (font (size 1.27 1.27)))))
			(pin power_in line (at 12.7 -2.54 180) (length 2.54) (name "OUT-" (effects (font (size 1.27 1.27)))) (number "4" (effects (font (size 1.27 1.27)))))
		)
		(embedded_fonts no)
	)"""


def write_project_libs():
    with open(os.path.join(PROJ, "panel_interface.kicad_sym"), "w") as f:
        f.write(
            '(kicad_symbol_lib\n\t(version 20251024)\n\t(generator "wedi_gen")\n\t(generator_version "10.0")\n\t'
            + RELAY_SYM
            + "\n\t"
            + BUCK_SYM
            + "\n)\n"
        )
    with open(os.path.join(PROJ, "sym-lib-table"), "w") as f:
        f.write(
            '(sym_lib_table\n  (version 7)\n  (lib (name "panel_interface")(type "KiCad")'
            '(uri "${KIPRJMOD}/panel_interface.kicad_sym")(options "")(descr "wediAlert panel interface"))\n)\n'
        )
    with open(os.path.join(PROJ, "fp-lib-table"), "w") as f:
        f.write(
            '(fp_lib_table\n  (version 7)\n  (lib (name "panel_interface")(type "KiCad")'
            '(uri "${KIPRJMOD}/panel_interface.pretty")(options "")(descr "wediAlert panel interface"))\n)\n'
        )


# ---------------------------------------------------------------- schematic builder
class Sch:
    def __init__(self):
        self.syms = {}
        self.body = []
        self.placed = {}
        self.pwr_n = 0
        self.libs = {}

    def libsym(self, lib_id):
        if lib_id in self.syms:
            return self.syms[lib_id]
        lib, name = lib_id.split(":")
        if lib == "panel_interface":
            t = RELAY_SYM if name.startswith("FP2") else BUCK_SYM
        else:
            if lib not in self.libs:
                self.libs[lib] = open(os.path.join(LIBDIR, lib + ".kicad_sym")).read()
            t = extract_symbol(self.libs[lib], name)
        self.syms[lib_id] = t
        return t

    def pinxy(self, lib_id, num, x, y, rot):
        px, py = pins_of(self.libsym(lib_id))[num]
        a = math.radians(rot)
        xr = px * math.cos(a) - py * math.sin(a)
        yr = px * math.sin(a) + py * math.cos(a)
        return (round(x + xr, 3), round(y - yr, 3))

    def place(self, ref, lib_id, value, x, y, rot=0, fp="", extra=None, ref_off=(2.54, -1.27), val_off=(2.54, 1.27), hide_val=False, bom="yes"):
        self.libsym(lib_id)
        u = uid("sym:" + ref)
        hv = " (hide yes)" if hide_val else ""
        fa = 90 if rot in (90, 270) else 0
        props = [
            f'(property "Reference" "{ref}" (at {x + ref_off[0]} {y + ref_off[1]} {fa}) (effects (font (size 1.27 1.27)) (justify left)))',
            f'(property "Value" "{value}" (at {x + val_off[0]} {y + val_off[1]} {fa}) (effects (font (size 1.27 1.27)) (justify left){hv}))',
            f'(property "Footprint" "{fp}" (at {x} {y} 0) (effects (font (size 1.27 1.27)) (hide yes)))',
            f'(property "Datasheet" "" (at {x} {y} 0) (effects (font (size 1.27 1.27)) (hide yes)))',
        ]
        for k, v in (extra or {}).items():
            props.append(f'(property "{k}" "{v}" (at {x} {y} 0) (effects (font (size 1.27 1.27)) (hide yes)))')
        pins = "".join(f'(pin "{n}" (uuid "{uid("pin:" + ref + ":" + n)}"))' for n in pins_of(self.libsym(lib_id)))
        inst = f'(instances (project "{NAME}" (path "/{uid("root")}" (reference "{ref}") (unit 1))))'
        self.body.append(
            f'(symbol (lib_id "{lib_id}") (at {x} {y} {rot}) (unit 1) (exclude_from_sim no) (in_bom {bom}) (on_board yes) (dnp no) (uuid "{u}")\n'
            + "\n".join(props)
            + "\n"
            + pins
            + "\n"
            + inst
            + ")"
        )
        self.placed[ref] = (lib_id, x, y, rot)
        return ref

    def p(self, ref, num):
        lib_id, x, y, rot = self.placed[ref]
        return self.pinxy(lib_id, num, x, y, rot)

    def wire(self, *pts):
        for a, b in zip(pts, pts[1:]):
            if a == b:
                continue
            self.body.append(
                f'(wire (pts (xy {a[0]} {a[1]}) (xy {b[0]} {b[1]})) (stroke (width 0) (type default)) (uuid "{uid("w:%s:%s:%d" % (a, b, len(self.body)))}"))'
            )

    def junction(self, pt):
        self.body.append(f'(junction (at {pt[0]} {pt[1]}) (diameter 0) (color 0 0 0 0) (uuid "{uid("j:%s:%d" % (pt, len(self.body)))}"))')

    def label(self, name, pt, angle=0, just="left"):
        self.body.append(
            f'(label "{name}" (at {pt[0]} {pt[1]} {angle}) (effects (font (size 1.27 1.27)) (justify {just} bottom)) (uuid "{uid("l:%s:%s" % (name, pt))}"))'
        )

    def noconn(self, pt):
        self.body.append(f'(no_connect (at {pt[0]} {pt[1]}) (uuid "{uid("nc:%s:%d" % (pt, len(self.body)))}"))')

    def text(self, s, x, y, size=1.27, bold=False):
        s = s.replace('"', '\\"').replace("\n", "\\n")
        b = " (bold yes)" if bold else ""
        self.body.append(
            f'(text "{s}" (exclude_from_sim no) (at {x} {y} 0) (effects (font (size {size} {size}){b}) (justify left top)) (uuid "{uid("t:%s:%d" % (s[:20], len(self.body)))}"))'
        )

    def power(self, kind, pt, rot=0):
        self.pwr_n += 1
        ref = f"#PWR{self.pwr_n:03d}"
        off = (-1.27, 3.81) if kind == "GND" else (-1.27, -3.81)
        self.place(ref, "power:" + kind, kind, pt[0], pt[1], rot, ref_off=off, val_off=off)
        # references of power symbols stay hidden
        self.body[-1] = self.body[-1].replace(
            f'(property "Reference" "{ref}" (at {pt[0] + off[0]} {pt[1] + off[1]} 0) (effects (font (size 1.27 1.27)) (justify left)))',
            f'(property "Reference" "{ref}" (at {pt[0]} {pt[1]} 0) (effects (font (size 1.27 1.27)) (hide yes)))',
        )
        return ref

    def flag(self, pt):
        self.pwr_n += 1
        ref = f"#FLG{self.pwr_n:03d}"
        self.place(ref, "power:PWR_FLAG", "PWR_FLAG", pt[0], pt[1], 0, ref_off=(0, 0), val_off=(0, 0), hide_val=True)
        self.body[-1] = self.body[-1].replace(
            f'(property "Reference" "{ref}" (at {pt[0]} {pt[1]} 0) (effects (font (size 1.27 1.27)) (justify left)))',
            f'(property "Reference" "{ref}" (at {pt[0]} {pt[1]} 0) (effects (font (size 1.27 1.27)) (hide yes)))',
        )


FP_R = "Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P7.62mm_Horizontal"
FP_RV = "Resistor_THT:R_Axial_DIN0207_L6.3mm_D2.5mm_P2.54mm_Vertical"
FP_D = "Diode_THT:D_DO-35_SOD27_P2.54mm_Vertical_AnodeUp"
FP_LED = "LED_THT:LED_D3.0mm"
FP_F = "panel_interface:Polyfuse_Radial_P5.08mm"
FP_K = "panel_interface:Relay_DPDT_Axicom_FP2"
FP_U = "panel_interface:Buck_Module_XL4015_52x23mm"
FP_MSTB = "Connector_Phoenix_MSTB:PhoenixContact_MSTBA_2,5_%d-G-5,08_1x%02d_P5.08mm_Horizontal"
FP_HDR = "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical"
FP_WIRE = "panel_interface:SolderWire_Pads_1x02_P6mm"
FP_HOLE = "panel_interface:Hole_M3"


def channel(s, n, ox, oy, refs, gpio, role, header_pin):
    """One panel line: +12V > fuse > loop out > panel > loop back > coil, plus LEDs and the GPIO path."""
    s.text(f"LINE {n}: {role}  -  GPIO{gpio} (Pi pin {header_pin})", ox, oy - 10.16, 1.5, True)
    y0 = oy
    s.power("+12V", (ox, y0))
    s.place(refs["F"], "Device:Polyfuse", "MF-R010 100mA", ox + 7.62, y0, 90, FP_F, None, ref_off=(2.54, -3.81), val_off=(11.43, 3.81))
    s.wire((ox, y0), s.p(refs["F"], "1"))
    a = s.p(refs["F"], "2")
    e = (ox + 22.86, y0)
    s.wire(a, e)
    s.label(f"LOOP{n}_OUT", e)

    y1 = oy + 7.62
    s.label(f"LOOP{n}_RET", (ox, y1))
    xled = ox + 7.62
    xcoil = ox + 22.86
    s.wire((ox, y1), (xcoil, y1))
    s.junction((xled, y1))

    # Panel LED: lit while the loop is closed (12 V on the return wire)
    s.place(refs["Rp"], "Device:R", "2k2", xled, y1 + 3.81, 0, FP_RV)
    s.place(refs["Dp"], "Device:LED", "PANEL", xled, y1 + 11.43, 90, FP_LED, ref_off=(7.62, -1.27), val_off=(10.16, 1.27))
    s.power("GND", (xled, y1 + 15.24))
    s.wire(s.p(refs["Rp"], "1"), (xled, y1))
    s.wire(s.p(refs["Rp"], "2"), s.p(refs["Dp"], "2"))
    s.wire(s.p(refs["Dp"], "1"), (xled, y1 + 15.24))

    # Coil: 270R series, flyback diode, polarized coil
    s.place(refs["Rc"], "Device:R", "270 0.6W", xcoil, y1 + 3.81, 0, FP_R)
    ycoil = y1 + 7.62
    s.wire(s.p(refs["Rc"], "1"), (xcoil, y1))
    rx = xcoil + 30.48
    s.place(refs["K"], "panel_interface:FP2_Monostable", "FP2 D3009 (5 V, 178R)", rx, ycoil + 5.08, 0, FP_K, ref_off=(-10.16, -17.78), val_off=(-10.16, -15.24))
    s.wire(s.p(refs["Rc"], "2"), s.p(refs["K"], "1"))
    xd = xcoil + 10.16
    s.junction((xd, ycoil))
    s.place(refs["D"], "Device:D", "1N4148", xd, ycoil + 3.81, 270, FP_D, ref_off=(2.54, -1.27), val_off=(2.54, 1.27))
    s.wire(s.p(refs["D"], "1"), (xd, ycoil))
    gy = ycoil + 12.7
    s.wire(s.p(refs["D"], "2"), (xd, gy))
    c10 = s.p(refs["K"], "10")
    s.wire(c10, (xd, c10[1]))
    s.junction((xd, c10[1]))
    s.power("GND", (xd, gy))

    # Spare pole and unused pins
    for pin in ("9", "2", "3", "4", "5", "6"):
        s.noconn(s.p(refs["K"], pin))

    # Contact: 3V3 > COM, NO > LED + 10k + 1k > GPIO
    com = s.p(refs["K"], "8")
    s.power("+3.3V", (com[0] + 2.54, com[1]))
    s.wire(com, (com[0] + 2.54, com[1]))
    no = s.p(refs["K"], "7")
    xl = no[0] + 7.62
    xp = xl + 12.7
    s.wire(no, (xp, no[1]))
    s.junction((xl, no[1]))
    s.junction((xp, no[1]))
    s.place(refs["Rl"], "Device:R", "470", xl, no[1] + 3.81, 0, FP_RV)
    s.place(refs["Dl"], "Device:LED", "RELAY", xl, no[1] + 11.43, 90, FP_LED, ref_off=(7.62, -1.27), val_off=(10.16, 1.27))
    s.wire(s.p(refs["Rl"], "1"), (xl, no[1]))
    s.wire(s.p(refs["Rl"], "2"), s.p(refs["Dl"], "2"))
    s.wire(s.p(refs["Dl"], "1"), (xl, no[1] + 15.24))
    s.power("GND", (xl, no[1] + 15.24))
    s.place(refs["Rd"], "Device:R", "10k", xp, no[1] + 3.81, 0, FP_RV)
    s.wire(s.p(refs["Rd"], "1"), (xp, no[1]))
    s.wire(s.p(refs["Rd"], "2"), (xp, no[1] + 15.24))
    s.power("GND", (xp, no[1] + 15.24))
    s.place(refs["Rs"], "Device:R", "1k", xp + 7.62, no[1], 90, FP_RV, ref_off=(0, -2.54), val_off=(5.08, -2.54))
    s.wire((xp, no[1]), s.p(refs["Rs"], "1"))
    q = s.p(refs["Rs"], "2")
    s.wire(q, (q[0] + 2.54, q[1]))
    s.label(f"GPIO{gpio}", (q[0] + 2.54, q[1]))


def build():
    s = Sch()
    ch = [
        dict(F="F1", Rp="R3", Dp="D3", Rc="R1", K="K1", D="D1", Rl="R5", Dl="D5", Rd="R9", Rs="R7"),
        dict(F="F2", Rp="R4", Dp="D4", Rc="R2", K="K2", D="D2", Rl="R6", Dl="D6", Rd="R10", Rs="R8"),
    ]
    channel(s, 1, 27.94, 30.48, ch[0], 19, "fault / guasto", 35)
    channel(s, 2, 27.94, 88.9, ch[1], 26, "alarm / allarme", 37)

    # Panel connector
    y = 142.24
    s.text("PANEL CABLE\nline 1: pins 1-2, line 2: pins 3-4", 27.94, y - 10.16, 1.27, True)
    s.place("J1", "Connector:Conn_01x04_Pin", "Panel", 33.02, y + 2.54, 0, FP_MSTB % (4, 4), ref_off=(-3.81, -6.35), val_off=(-3.81, 8.89))
    for pin, net in (("1", "LOOP1_OUT"), ("2", "LOOP1_RET"), ("3", "LOOP2_OUT"), ("4", "LOOP2_RET")):
        q = s.p("J1", pin)
        s.wire(q, (q[0] + 2.54, q[1]))
        s.label(net, (q[0] + 2.54, q[1]))

    # Pi connector
    x = 76.2
    s.text("TO THE PI (HAT header)\n1: 3V3 pin 17   2: GPIO19 pin 35\n3: GPIO26 pin 37   4: GND pin 39", x - 5.08, y - 10.16, 1.27, True)
    s.place("J4", "Connector:Conn_01x04_Pin", "To Pi", x, y + 2.54, 0, FP_HDR, ref_off=(-3.81, -6.35), val_off=(-3.81, 8.89))
    q = s.p("J4", "1")
    s.wire(q, (q[0] + 5.08, q[1]))
    s.power("+3.3V", (q[0] + 5.08, q[1]))
    s.flag((q[0] + 2.54, q[1]))
    s.junction((q[0] + 2.54, q[1]))
    for pin, net in (("2", "GPIO19"), ("3", "GPIO26")):
        q = s.p("J4", pin)
        s.wire(q, (q[0] + 2.54, q[1]))
        s.label(net, (q[0] + 2.54, q[1]))
    q = s.p("J4", "4")
    s.wire(q, (q[0] + 2.54, q[1]), (q[0] + 2.54, q[1] + 2.54))
    s.power("GND", (q[0] + 2.54, q[1] + 2.54))

    # 12 V input, buck module, 5 V output
    x = 134.62
    s.text("POWER: 12 V in, 5 V out to HAT pins 2 and 6\nSet the trimmer to 5.1 V BEFORE connecting the Pi", x - 5.08, y - 15.24, 1.27, True)
    s.place("J2", "Connector:Conn_01x02_Pin", "12V in", x, y, 0, FP_MSTB % (2, 2), ref_off=(-3.81, -3.81), val_off=(-3.81, 6.35))
    s.place("U1", "panel_interface:Buck_Module_XL4015", "XL4015 module", x + 30.48, y + 2.54, 0, FP_U, ref_off=(-10.16, -10.16), val_off=(-10.16, -7.62))
    q1 = s.p("J2", "1")
    q2 = s.p("J2", "2")
    u1 = s.p("U1", "1")
    u2 = s.p("U1", "2")
    s.wire(q1, u1)
    s.wire(q2, (x + 5.08, q2[1]), (x + 5.08, u2[1]), u2)
    s.power("+12V", (x + 8.89, u1[1]))
    s.junction((x + 8.89, u1[1]))
    s.flag((x + 11.43, u1[1]))
    s.junction((x + 11.43, u1[1]))
    s.junction((x + 8.89, u2[1]))
    s.wire((x + 8.89, u2[1]), (x + 8.89, u2[1] + 5.08))
    s.power("GND", (x + 8.89, u2[1] + 5.08))
    s.flag((x + 11.43, u2[1]))
    s.junction((x + 11.43, u2[1]))
    o1 = s.p("U1", "3")
    o2 = s.p("U1", "4")
    s.place("J3", "Connector:Conn_01x02_Pin", "5V out", o1[0] + 25.4, y, 180, FP_WIRE, ref_off=(-3.81, -5.08), val_off=(-3.81, 7.62))
    j1 = s.p("J3", "1")
    j2 = s.p("J3", "2")
    xm = o1[0] + 5.08
    s.wire(o1, (xm, o1[1]), (xm, j1[1]), j1)
    xn = o1[0] + 10.16
    s.wire(o2, (xn, o2[1]), (xn, j2[1]), j2)
    s.power("+5V", (xm, j1[1]))
    s.junction((xm, j1[1]))
    s.junction((xn, o2[1]))
    s.wire((xn, o2[1]), (xn, o2[1] + 5.08))
    s.power("GND", (xn, o2[1] + 5.08))

    # Mounting holes (not connected)
    s.place("H1", "Mechanical:MountingHole", "M3", 152.4, 73.66, 0, FP_HOLE, hide_val=True, bom="no")
    s.place("H2", "Mechanical:MountingHole", "M3", 165.1, 73.66, 0, FP_HOLE, hide_val=True, bom="no")

    s.text(
        "NOTES\n"
        "1. Normal = loop closed: coil on, NO closed, GPIO = 3.3 V (reads 1). Alarm, power loss or cut cable = GPIO 0.\n"
        "2. The FP2 D3009 coil is 5 V (178R, limit 11 V) and POLARIZED: pin 1 +, pin 10 -. R1/R2 drop 12 V to about 4.8 V.\n"
        "3. 3V3 comes from the Pi, so a GPIO never sees voltage while the Pi is off.\n"
        "4. Relay LEDs run from 3.3 V: use red, orange or yellow (forward voltage up to 2.2 V). Blue or white will not light.\n"
        "5. R9/R10 pull down harder than a leftover Pi pull-up. R7/R8 protect the GPIO from wiring mistakes.\n"
        "6. F1/F2: a short on the panel cable trips only that line, so the Pi and modem stay powered and can report it.\n"
        "7. The PANEL LED cannot tell an alarm from an unplugged cable: both open the loop.",
        148.59, 30.48, 1.27,
    )

    libsyms = []
    for lib_id, t in s.syms.items():
        libsyms.append(t.replace('(symbol "%s"' % lib_id.split(":")[1], '(symbol "%s"' % lib_id, 1))
    out = (
        f'(kicad_sch\n\t(version 20250114)\n\t(generator "eeschema")\n\t(generator_version "9.0")\n\t(uuid "{uid("root")}")\n\t(paper "A4")\n'
        '\t(title_block (title "wediAlert fire panel interface") (rev "1") (company "wedi") (comment 1 "2 supervised 12 V loops > relays > GPIO19 / GPIO26, plus 12 V to 5 V supply"))\n'
        "\t(lib_symbols\n" + "\n".join(libsyms) + "\n\t)\n" + "\n".join(s.body) + "\n"
        f'\t(sheet_instances (path "/" (page "1")))\n\t(embedded_fonts no)\n)\n'
    )
    with open(os.path.join(PROJ, NAME + ".kicad_sch"), "w") as f:
        f.write(out)


# Buck module as measured: pads on a 50 x 21 mm rectangle, board 1 mm beyond them on every side.
MOD_L, MOD_W = 52.0, 23.0
MOD_PAD_X, MOD_PAD_Y = 1.0, 1.0  # pad centre, distance in from the module's edges
MOD_PAD_OD = 2.2


def fp_head(name, descr):
    return f'(footprint "{name}"\n\t(version 20241229)\n\t(generator "wedi_gen")\n\t(layer "F.Cu")\n\t(descr "{descr}")\n'


def fp_text(kind, val, x, y, layer="F.SilkS", size=1.0, hide=False):
    h = " (hide yes)" if hide else ""
    return f'\t(property "{kind}" "{val}" (at {x} {y} 0) (layer "{layer}") (uuid "{uid("fp:" + kind + val + str(x))}") (effects (font (size {size} {size}) (thickness 0.15))){h})\n'


def fp_rect(x1, y1, x2, y2, layer, w=0.12):
    return f'\t(fp_rect (start {x1} {y1}) (end {x2} {y2}) (stroke (width {w}) (type solid)) (fill no) (layer "{layer}") (uuid "{uid("r:%s%s%s" % (x1, y1, layer))}"))\n'


def fp_label(t, x, y, layer="F.SilkS", size=0.8):
    return f'\t(fp_text user "{t}" (at {x} {y} 0) (layer "{layer}") (uuid "{uid("ft:%s%s%s" % (t, x, y))}") (effects (font (size {size} {size}) (thickness 0.12))))\n'


def pad(num, x, y, drill, od, shape="circle"):
    return f'\t(pad "{num}" thru_hole {shape} (at {x} {y}) (size {od} {od}) (drill {drill}) (layers "*.Cu" "*.Mask") (uuid "{uid("pad:%s%s%s" % (num, x, y))}"))\n'


def write_footprints():
    d = os.path.join(PROJ, "panel_interface.pretty")

    # FP2: 2.54 grid, rows 7.62 apart. Top view: 10 9 8 7 6 over 1 2 3 4 5, origin at pad 1.
    t = fp_head("Relay_DPDT_Axicom_FP2", "Axicom/Alcatel FP2 relay, 14 x 9 mm, datasheet 108-98005, top view")
    t += fp_text("Reference", "K", 5.08, -3.81, size=0.8) + fp_text("Value", "FP2", 5.08, -3.81, "F.Fab")
    x1, x2, y1, y2 = -1.93, 12.07, -8.31, 0.69
    t += fp_rect(x1, y1, x2, y2, "F.Fab", 0.1) + fp_rect(round(x1 - 0.1, 2), -8.57, round(x2 + 0.1, 2), 0.95, "F.SilkS")
    t += fp_rect(round(x1 - 0.25, 2), -8.72, round(x2 + 0.25, 2), 1.1, "F.CrtYd", 0.05)
    t += fp_label("+", -0.9, -1.4, size=1.0) + fp_label("-", -0.9, -6.2, size=1.0)
    for i in range(5):
        t += pad(i + 1, round(i * 2.54, 2), 0, 0.9, 1.5, "rect" if i == 0 else "circle")
    for i in range(5):
        t += pad(10 - i, round(i * 2.54, 2), -7.62, 0.9, 1.5)
    open(os.path.join(d, "Relay_DPDT_Axicom_FP2.kicad_mod"), "w").write(t + ")\n")

    # Buck module: origin at the module's top-left corner, IN on the left.
    t = fp_head("Buck_Module_XL4015_52x23mm", "XL4015 style 12V to 5V module, 52 x 23 mm, 4 corner pads on a 50 x 21 mm rectangle")
    t += fp_text("Reference", "U", 26, 8, size=1.0) + fp_text("Value", "XL4015", 26, 11, "F.Fab")
    for x1, y1, x2, y2 in ((2.6, 0, MOD_L - 2.6, 0), (2.6, MOD_W, MOD_L - 2.6, MOD_W), (0, 2.6, 0, MOD_W - 2.6), (MOD_L, 2.6, MOD_L, MOD_W - 2.6)):
        t += f'\t(fp_line (start {x1} {y1}) (end {x2} {y2}) (stroke (width 0.12) (type solid)) (layer "F.SilkS") (uuid "{uid("ml:%s%s" % (x1, y1))}"))\n'
    t += fp_rect(0, 0, MOD_L, MOD_W, "F.Fab", 0.1) + fp_rect(-0.1, -0.1, MOD_L + 0.1, MOD_W + 0.1, "F.CrtYd", 0.05)
    t += fp_label("IN+", 5.2, 1.0) + fp_label("IN-", 5.2, 22.0) + fp_label("OUT+", 45.8, 1.0) + fp_label("OUT-", 45.8, 22.0)
    xa, xb = MOD_PAD_X, MOD_L - MOD_PAD_X
    ya, yb = MOD_PAD_Y, MOD_W - MOD_PAD_Y
    t += pad(1, xa, ya, 1.3, MOD_PAD_OD, "rect") + pad(2, xa, yb, 1.3, MOD_PAD_OD) + pad(3, xb, ya, 1.3, MOD_PAD_OD) + pad(4, xb, yb, 1.3, MOD_PAD_OD)
    open(os.path.join(d, "Buck_Module_XL4015_52x23mm.kicad_mod"), "w").write(t + ")\n")

    # Radial PTC such as Bourns MF-R010: origin at pad 1.
    t = fp_head("Polyfuse_Radial_P5.08mm", "Radial resettable fuse, 5.08 mm pitch, body 7.4 x 3.1 mm")
    t += fp_text("Reference", "F", 2.54, 0, size=0.8) + fp_text("Value", "PTC", 2.54, 0, "F.Fab")
    t += fp_rect(-1.16, -1.55, 6.24, 1.55, "F.SilkS") + fp_rect(-1.16, -1.55, 6.24, 1.55, "F.Fab", 0.1) + fp_rect(-1.4, -1.8, 6.48, 1.8, "F.CrtYd", 0.05)
    t += pad(1, 0, 0, 0.9, 1.6, "rect") + pad(2, 5.08, 0, 0.9, 1.6)
    open(os.path.join(d, "Polyfuse_Radial_P5.08mm.kicad_mod"), "w").write(t + ")\n")

    # M3 hole with a tight courtyard: the screw head just clears the module.
    t = fp_head("Hole_M3", "M3 mounting hole")
    t += "\t(attr exclude_from_pos_files exclude_from_bom)\n"
    t += fp_text("Reference", "H", 0, 3.2, "F.Fab", 0.8) + fp_text("Value", "M3", 0, 3.2, "F.Fab", 0.8, True)
    t += f'\t(fp_circle (center 0 0) (end 2.2 0) (stroke (width 0.05) (type solid)) (fill no) (layer "F.CrtYd") (uuid "{uid("holec")}"))\n'
    t += f'\t(pad "" np_thru_hole circle (at 0 0) (size 3.2 3.2) (drill 3.2) (layers "*.Cu" "*.Mask") (uuid "{uid("holep")}"))\n'
    open(os.path.join(d, "Hole_M3.kicad_mod"), "w").write(t + ")\n")

    # 5 V output: two solder pads, wires go to HAT pins 2 and 6.
    t = fp_head("SolderWire_Pads_1x02_P6mm", "Two pads for 1 mm2 wires, 6 mm apart")
    t += fp_text("Reference", "J", 3, -2.4, size=0.8) + fp_text("Value", "5V", 3, 2.4, "F.Fab")
    t += fp_rect(-2, -1.9, 8, 1.9, "F.CrtYd", 0.05)
    t += pad(1, 0, 0, 1.5, 3.0, "rect") + pad(2, 6, 0, 1.5, 3.0)
    open(os.path.join(d, "SolderWire_Pads_1x02_P6mm.kicad_mod"), "w").write(t + ")\n")


def write_project():
    pro = os.path.join(PROJ, NAME + ".kicad_pro")
    if not os.path.exists(pro):
        with open(pro, "w") as f:
            f.write('{\n  "meta": { "filename": "%s.kicad_pro", "version": 3 },\n  "board": { "design_settings": { "rules": { "min_clearance": 0.2, "min_track_width": 0.3 } } }\n}\n' % NAME)


if __name__ == "__main__":
    os.makedirs(os.path.join(PROJ, "panel_interface.pretty"), exist_ok=True)
    write_project_libs()
    write_footprints()
    write_project()
    build()
    print("schematic written")
