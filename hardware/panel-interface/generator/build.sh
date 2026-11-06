#!/bin/sh
# Regenerates the whole KiCad project and runs ERC and DRC. Usage: ./build.sh [sch|pcb|all]
# Uses the KiCad Flatpak; set KICAD_RUN="" if KiCad is installed natively.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
PROJ=$(dirname "$HERE")
KICAD_RUN=${KICAD_RUN-"flatpak run --filesystem=$PROJ --command=sh org.kicad.KiCad -c"}
STEP=${1:-all}
run() { if [ -n "$KICAD_RUN" ]; then $KICAD_RUN "$1"; else sh -c "$1"; fi; }
mkdir -p "$PROJ/out"
if [ "$STEP" != pcb ]; then
  run "cd '$PROJ' && python3 generator/gen_sch.py && kicad-cli sch export netlist --format kicadsexpr -o out/netlist.net panel-interface.kicad_sch && kicad-cli sch erc --severity-all --exit-code-violations -o out/erc.rpt panel-interface.kicad_sch" || { echo "ERC reports violations: see out/erc.rpt"; }
fi
if [ "$STEP" != sch ]; then
  run "cd '$PROJ' && python3 generator/gen_pcb.py && kicad-cli pcb drc --severity-all --schematic-parity --refill-zones --save-board --exit-code-violations -o out/drc.rpt panel-interface.kicad_pcb" || { echo "DRC reports violations: see out/drc.rpt"; }
fi
# Fabrication files and drawings
if [ "$STEP" = all ] || [ "$STEP" = pcb ]; then
  run "cd '$PROJ' && rm -rf out/gerbers && mkdir -p out/gerbers && kicad-cli pcb export gerbers -l F.Cu,B.Cu,F.Mask,B.Mask,F.SilkS,B.SilkS,Edge.Cuts --no-protel-ext -o out/gerbers/ panel-interface.kicad_pcb >/dev/null && kicad-cli pcb export drill -o out/gerbers/ panel-interface.kicad_pcb >/dev/null && kicad-cli sch export pdf -o out/panel-interface-schematic.pdf panel-interface.kicad_sch >/dev/null && kicad-cli sch export bom -o out/bom.csv --fields 'Reference,Value,Footprint,\${QUANTITY}' --group-by Value,Footprint panel-interface.kicad_sch >/dev/null; kicad-cli pcb render --side top --width 1800 --height 900 --quality high -o out/render_top.png panel-interface.kicad_pcb >/dev/null 2>&1 || true"
  (cd "$PROJ/out" && rm -f panel-interface-gerbers.zip && python3 -c "import shutil; shutil.make_archive('panel-interface-gerbers','zip','gerbers')")
fi
