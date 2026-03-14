#!/bin/bash
NUM_TESTERS=10

N=100000
IP=142.1.46.95

#C=250
#OUT_DIR="product_testing_results/c250"
WORK_DIR="product_workloads"
#
#echo "Initiating Testing Swarm..."
#
#for i in {0..9}
#do
#   echo testing product_workloads/workload_part_$i...
#   python3 load_tester.py -w $WORK_DIR/workload_part_0$i -u http://$IP:14133 -c $C -n $N > "$OUT_DIR/result_$i.log" &
#done
#
#wait
#
##wipe 
#curl http://$IP:14133/product/wipe

C=400
OUT_DIR="product_testing_results/c400"

echo "Initiating Testing Swarm..."

for i in {0..9}
do
   echo testing product_workloads/workload_part_$i...
   python3 load_tester.py -w $WORK_DIR/workload_part_0$i -u http://$IP:14133 -c $C -n $N > "$OUT_DIR/result_$i.log" &
done

wait

echo "The dust has settled. Check the doomsday logs."
