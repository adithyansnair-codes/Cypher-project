#!/usr/bin/env bash
# One command that shows how the training is going. Reads log files only.
#
#   bash ~/CYPHER/.dsh-scratch/work/ai/training/status.sh

SCRATCH="/Users/adithyansnair/CYPHER/.dsh-scratch"
RUN="$SCRATCH/training/runs/cypher-v3-6class"
LOG="/tmp/cypher-train-v3.log"

echo "── stage 2 · six classes (knife, pistol, person, fire, smoke, lying) ──"
if [ -f "$RUN/results.csv" ]; then
    epochs=$(( $(wc -l < "$RUN/results.csv") - 1 ))
    echo "epochs finished: $epochs / 20"
    tail -2 "$RUN/results.csv" | awk -F, 'NF>5 {printf "  epoch %s: mAP50 %s  mAP50-95 %s\n", $1, $8, $9}'
else
    echo "not started yet, or no epoch has finished"
fi
tail -c 300 "$LOG" 2>/dev/null | tr '\r' '\n' | grep -E '^ +[0-9]+/20' | tail -1
echo "weights so far:"
ls -1 "$RUN/weights" 2>/dev/null | tail -4 || true

echo
echo "── stage 1 · five classes (finished) ──"
echo "  stage1-5class-40ep.pt → mAP50 0.901 / mAP50-95 0.668 on the 385-image test"
echo "  (the previously shipped model was 0.589 / 0.403)"

echo
echo "── jobs ──"
launchctl list | grep cypher || echo "  none running"
pgrep -f 'ai/training/train.py' > /dev/null && echo "  python trainer IS running" || echo "  python trainer is not running"

echo
echo "stop training:   pkill -f 'ai/training/train.py'"
