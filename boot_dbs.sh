#!/bin/bash

# Define your 3 dedicated database machines
USER_DB_NODE="dh2010pc00.utm.utoronto.ca"
PRODUCT_DB_NODE="dh2010pc01.utm.utoronto.ca"
ORDER_DB_NODE="dh2010pc02.utm.utoronto.ca"

USER="stevenda"
PORT=14441

# Helper function to remotely wipe, initialize, configure, and start a database
boot_db() {
    local node=$1
    local db_name=$2
    local dir_name=$3
    
    echo "Booting $db_name on $node..."
    
    ssh -q -T -o StrictHostKeyChecking=no "$USER@$node" << EOF
        # 1. Clear out any old data from previous runs
        ~/postgres/bin/pg_ctl -D /tmp/$dir_name stop -m immediate &>/dev/null
        rm -rf /tmp/$dir_name
        
        # 2. Initialize the database locally in the /tmp folder
        ~/postgres/bin/initdb -D /tmp/$dir_name &>/dev/null
        
        # 3. CRITICAL: Allow other lab machines to connect to it
        echo "listen_addresses = '*'" >> /tmp/$dir_name/postgresql.conf
        echo "max_connections = 300" >> /tmp/$dir_name/postgresql.conf
        echo "host all all 0.0.0.0/0 trust" >> /tmp/$dir_name/pg_hba.conf
        
        # 4. Start the database server
        ~/postgres/bin/pg_ctl -D /tmp/$dir_name -l /tmp/${dir_name}.log -o "-p $PORT" start &>/dev/null
        sleep 2
        
        # 5. Create the specific database your Java code expects
        ~/postgres/bin/createdb -p $PORT $db_name
EOF
    echo "$db_name is live on $node:$PORT"
}

# Boot all three in parallel
boot_db "$USER_DB_NODE" "users_db" "pg_users" &
boot_db "$PRODUCT_DB_NODE" "products_db" "pg_products" &
boot_db "$ORDER_DB_NODE" "orders_db" "pg_orders" &

wait
echo "-------------------------------------------------------"
echo "All 3 dedicated databases are running at maximum local speed!"
