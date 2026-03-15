#!/bin/bash

N=100000
IP="dh2010pc03.utm.utoronto.ca"

WORK_DIR="workloads/product/create/"
C=250
SERVICE="product"
CMD="create"
NUM_TESTERS=10
ARCH="hScale50"

OUT_DIR="results/${SERVICE}/${CMD}_${NUM_TESTERS}T_${C}C_${ARCH}"


echo "Initiating Testing Swarm"
echo "\t${SERVICE}_${CMD}_${NUM_TESTERS}T_${C}C_${ARCH}"

END_INDEX=$((NUM_TESTERS - 1))
mkdir -p $OUTDIR

for i in $(seq -f "%02g" 0 $END_INDEX)
do
   echo "\tFiring ${WORK_DIR}/workload_chunk_$i..."
   python3 load_tester.py -w $WORK_DIR/workload_chunk_$i -u http://$IP:14133 -c $C -n $N > "$OUT_DIR/result_$i.log" &
done

wait

#wipe 
curl http://$IP:14133/${SERVICE}/wipe

echo "Dust has settled. Check results at: ${OUT_DIR}"
