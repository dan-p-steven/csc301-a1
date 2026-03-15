#!/bin/bash

USER="stevenda"
ROSTER_FILE="live_nodes.txt"

# Your 3 dedicated database machines
USER_DB_NODE="dh2010pc00.utm.utoronto.ca"
PRODUCT_DB_NODE="dh2010pc01.utm.utoronto.ca"
ORDER_DB_NODE="dh2010pc02.utm.utoronto.ca"

echo "Initiating Cluster Shutdown Sequence..."
echo "-------------------------------------------------------"

# 1. TERMINATE THE COMPUTE SWARM
if [ -f "$ROSTER_FILE" ]; then
    echo "🔪 Phase 1: Assassinating Java nodes in parallel..."
    
    for node in $(cat "$ROSTER_FILE"); do
        (
            # We kill both the Java process and your runme.sh wrapper just to be safe
            ssh -q -o BatchMode=yes -o StrictHostKeyChecking=no "$USER@$node" \
                "pkill -u $USER java; pkill -u $USER -f runme.sh" &>/dev/null
            echo "  [OFFLINE] Compute cleared on $node"
        ) &
    done
    
    # Wait for all 50 kill commands to finish
    wait
else
    echo "⚠️ No $ROSTER_FILE found, skipping compute teardown."
fi

echo "-------------------------------------------------------"

# 2. TERMINATE THE DATABASES
echo "🗄️ Phase 2: Shutting down in-memory databases..."

stop_db() {
    local node=$1
    local dir_name=$2
    
    # Fast shutdown and wipe the /tmp directory to free up the RAM
    ssh -q -o BatchMode=yes -o StrictHostKeyChecking=no "$USER@$node" \
        "pg_ctl -D /tmp/$dir_name stop -m immediate &>/dev/null; rm -rf /tmp/$dir_name" &>/dev/null
    
    echo "  [WIPED] Database memory cleared on $node"
}

# Run the DB teardowns in parallel
stop_db "$USER_DB_NODE" "pg_users" &
stop_db "$PRODUCT_DB_NODE" "pg_products" &
stop_db "$ORDER_DB_NODE" "pg_orders" &

wait

echo "-------------------------------------------------------"
echo "Cluster successfully destroyed. The lab hardware is clean."
