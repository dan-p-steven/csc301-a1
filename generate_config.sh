#!/bin/bash

# ==========================================
# CRITICAL: Put the IP or DNS of your 
# dedicated PostgreSQL machine here!
# ==========================================
# ==========================================

USER_DB_HOST="dh2010pc00.utm.utoronto.ca"
PRODUCT_DB_HOST="dh2010pc01.utm.utoronto.ca"
ORDER_DB_HOST="dh2010pc02.utm.utoronto.ca"
# ==========================================
ROSTER_FILE="live_nodes.txt"
CONFIG_FILE="config.json"

# Read all live nodes into a bash array
mapfile -t ALL_NODES < "$ROSTER_FILE"
TOTAL=${#ALL_NODES[@]}

if [ "$TOTAL" -lt 3 ]; then
    echo "❌ Not enough nodes to build a cluster! We need at least 3. Found: $TOTAL"
    exit 1
fi

# 1. DIVVY UP THE COMPUTERS
# Take the very first one for the Orchestrator
ORDER_NODE="${ALL_NODES[0]}"

# Put the rest into a new array
REMAINING_NODES=("${ALL_NODES[@]:1}")

# Split the remainder 50/50
HALF=$((${#REMAINING_NODES[@]} / 2))
USER_NODES=("${REMAINING_NODES[@]:0:$HALF}")
PRODUCT_NODES=("${REMAINING_NODES[@]:$HALF}")

echo "DISTRIBUTION PLAN ($TOTAL total computers):"
echo "  - OrderService:   1 node  ($ORDER_NODE)"
echo "  - UserService:    ${#USER_NODES[@]} nodes"
echo "  - ProductService: ${#PRODUCT_NODES[@]} nodes"

# 2. HELPER FUNCTION: Convert Bash arrays to JSON arrays
function to_json_array() {
    local arr=("$@")
    local json="["
    for i in "${!arr[@]}"; do
        json+="\"${arr[$i]}\""
        if [ $i -lt $((${#arr[@]} - 1)) ]; then json+=", "; fi
    done
    json+="]"
    echo "$json"
}

ORDER_JSON=$(to_json_array "$ORDER_NODE")
USER_JSON=$(to_json_array "${USER_NODES[@]}")
PRODUCT_JSON=$(to_json_array "${PRODUCT_NODES[@]}")

# 3. WRITE THE JSON TO DISK
# We use 'cat <<EOF' to dump this directly into config.json
cat <<EOF > "$CONFIG_FILE"
{
    "UserService": {
        "port": 14833,
        "ips": $USER_JSON,
        "db": {
            "url": "jdbc:postgresql://$USER_DB_HOST:14441/users_db",
            "user": "stevenda",
            "password": "chezborgor"
        }
    },
    "ProductService": {
        "port": 14002,
        "ips": $PRODUCT_JSON,
        "db": {
            "url": "jdbc:postgresql://$PRODUCT_DB_HOST:14441/products_db",
            "user": "stevenda",
            "password": "chezborgor"
        }
    },
    "OrderService": {
        "port": 14133,
        "ips": $ORDER_JSON,
        "db": {
            "url": "jdbc:postgresql://$ORDER_DB_HOST:14441/orders_db",
            "user": "stevenda",
            "password": "chezborgor"
        }
    }
}
EOF

echo "Successfully generated $CONFIG_FILE!"
