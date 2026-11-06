# wediAlert fire panel interface

Replaces the hand-wired perfboard. Two supervised 12 V loops from the fire panel drive two relays,
whose contacts put 3.3 V on GPIO19 and GPIO26. It also carries the 12 V to 5 V module.
Open `panel-interface.kicad_pro` in KiCad 10.

| Condition | Loop | Relay | GPIO reads | wediAlert sees |
|---|---|---|---|---|
| Normal | closed | energized | 1 | normal |
| Alarm, panel power loss, cut cable | open | released | 0 | active |

No software change is needed: the pins and the logic match `GpioHandler`.

## Check before ordering

- **Board: 92 x 30 mm**, plus 7.5 mm of J1/J2 overhanging one long edge, for the box of 110 x 110 x 60 mm
  with the Pi and HAT alongside. Lay a paper cut-out in the box to check it clears the corner posts, and
  that the plugs and the panel cable have room to bend beyond the overhang.
- **Module: measured** at 52 x 23 mm with pads on a 50 x 21 mm rectangle (`MOD_*` in `gen_sch.py`). The
  board's 1.3 mm pad holes are an assumption: check them against the wire or pin you use.
- **Green plug: confirmed** 5.08 mm pitch.

## Parts

| Ref | Value | Notes |
|---|---|---|
| K1, K2 | FP2 D3009 | Reuse the existing relays. 5 V coil, 178 ohm, polarized |
| R1, R2 | 270 ohm, 0.6 W | Coil series resistor, about 0.2 W dissipated |
| D1, D2 | 1N4148 | Coil flyback. Band to the coil + side, as on the silk |
| F1, F2 | Bourns MF-R010 | Resettable, 100 mA hold, 5.08 mm pitch |
| D3, D4 | LED 3 mm, any color | Panel loop. 4 mA from 12 V |
| R3, R4 | 2.2 k | For D3, D4 |
| D5, D6 | LED 3 mm **red, orange or yellow** | Relay state. Blue or white will not light from 3.3 V |
| R5, R6 | 470 ohm | For D5, D6, about 3 mA |
| R7, R8 | 1 k | GPIO series protection |
| R9, R10 | 10 k | Pull-down. Overrides a leftover Pi pull-up |
| U1 | XL4015 module | Reuse the existing one |
| J1 | 4-way 5.08 mm MSTBA-style header | Mates with your existing green plug (5.08 mm confirmed) |
| J2 | 2-way 5.08 mm MSTBA-style header | 12 V in, with its plug |
| J3 | Solder pads | 5 V wires, 1 mm2 |
| J4 | 1x4 2.54 mm pin header | Dupont wires to the HAT |

The LED footprints can hold the LED directly, or take wires to LEDs on the box wall as today.

## Connections

| Connector | Pin | Signal | Goes to |
|---|---|---|---|
| J1 | 1 / 2 | Line 1 out / return | Panel fault loop |
| J1 | 3 / 4 | Line 2 out / return | Panel alarm loop |
| J2 | 1 / 2 | +12 V / GND | Supply, at least 2 A |
| J3 | 1 / 2 | +5 V / GND | HAT pin 2 / pin 6 |
| J4 | 1 | 3V3 | Pi pin 17 (**new wire**) |
| J4 | 2 | GPIO19 | Pi pin 35 |
| J4 | 3 | GPIO26 | Pi pin 37 |
| J4 | 4 | GND | Pi pin 39 |

Connector labels are on the bottom silkscreen, where they stay readable once parts are fitted.

| LED | Lit when |
|---|---|
| D3 / D5 | Line 1 loop closed / line 1 relay closed |
| D4 / D6 | Line 2 loop closed / line 2 relay closed |

The panel LED goes off for an alarm and for an unplugged cable alike, since both open the loop.

## Bring-up

Keep `wedialert` disabled until the last step.

1. **Before soldering each relay**, with a meter: 178 ohm or so between pins 1 and 10, and continuity
   between 8 and 9. Apply 5 V, + on pin 1 and - on pin 10: it should click, with continuity between
   8 and 7. The datasheet is a 2012 TE edition and these relays are older Alcatel parts, so this
   confirms the pinout before it matters.
2. **Module alone, Pi disconnected:** apply 12 V and set the trimmer to 5.1 V at J3.
3. **Connect J4 and J3 to the Pi.** Power up with nothing on J1. Both inputs must read 0, even after
   forcing a pull-up, because R9/R10 win:
   ```bash
   gpio -g mode 19 up; gpio -g mode 26 up; gpio -g read 19; gpio -g read 26
   ```
4. **Link J1 pins 1-2.** D3 and D5 light, the relay clicks, GPIO19 reads 1. Then the same with
   pins 3-4, for D4, D6 and GPIO26.
5. **Put back the pull-down**, as the service does, and measure 4.5 to 5 V across each coil:
   ```bash
   gpio -g mode 19 down; gpio -g mode 26 down
   ```
6. **On the panel**, check both inputs read 1, then enable the service. Warn the SMS contacts first.
   ```bash
   sudo systemctl enable --now wedialert
   ```

## Rebuilding

`generator/build.sh` regenerates everything from the scripts and runs ERC and DRC. It overwrites the
schematic and board, so hand edits made in KiCad are lost: once you start editing there, stop using it.

After moving a part on the board, run with `ROUTE_SEARCH=1` to find a new routing order (about 30 s per
attempt). The tracks were routed by a script: valid, but less tidy than hand routing.

`out/` holds the schematic PDF, BOM, Gerbers and drill files, and the ERC and DRC reports.

The DRC reports 2 accepted silkscreen warnings: the J1 and J2 outlines overhang the edge by design.
