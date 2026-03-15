#!/bin/bash

USER="stevenda"
CONFIG_FILE="config.json"
LOG_DIR="$HOME/logs"

# 1. PRE-FLIGHT CHECKS
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: $CONFIG_FILE not found! Run generate_config.sh first."
    exit 1
fi

if [ ! -x "./runme.sh" ]; then
    echo "Error: ./runme.sh not found or not executable! Run 'chmod +x runme.sh'."
    exit 1
fi

echo "Reading cluster map from $CONFIG_FILE..."

# 2. EXTRACT IPS FROM JSON
ORDER_IPS=($(python3 -c "import json,sys; print(' '.join(json.load(sys.stdin)['OrderService']['ips']))" < "$CONFIG_FILE"))
USER_IPS=($(python3 -c "import json,sys; print(' '.join(json.load(sys.stdin)['UserService']['ips']))" < "$CONFIG_FILE"))
PRODUCT_IPS=($(python3 -c "import json,sys; print(' '.join(json.load(sys.stdin)['ProductService']['ips']))" < "$CONFIG_FILE"))

echo "Deploying: 1 Order, ${#USER_IPS[@]} User, ${#PRODUCT_IPS[@]} Product nodes."
echo "-------------------------------------------------------"

# 3. THE DEPLOYMENT FUNCTION
launch_service() {
    local node=$1
    local flag=$2
    local service_name=$3
    
    echo "  -> Starting $service_name on $node..."
    
    # cd $(pwd) ensures the remote machine is in the exact folder where runme.sh lives
    # nohup ... & ensures the script stays alive after SSH disconnects
    ssh -n -f -o BatchMode=yes -o StrictHostKeyChecking=no "$USER@$node" \
        "cd $(pwd); nohup ./runme.sh $flag > $LOG_DIR/${node}_${service_name}.log 2>&1 &"
}

# 4. IGNITION
# Launch OrderService (-o)
for node in "${ORDER_IPS[@]}"; do
    launch_service "$node" "-o" "Order"
done

# Launch UserService Cluster (-u)
for node in "${USER_IPS[@]}"; do
    launch_service "$node" "-u" "User"
done

# Launch ProductService Cluster (-p)
for node in "${PRODUCT_IPS[@]}"; do
    launch_service "$node" "-p" "Product"
done

echo "-------------------------------------------------------"
echo "Swarm deployed."
echo "[Entry point] http://${ORDER_IPS[0]}:14133"
