#!/bin/bash

USER="stevenda"
ROSTER_FILE="live_nodes.txt"

# Clear out any old roster
> "$ROSTER_FILE"

echo "Scanning dh2010 lab for reachable computers..."
echo "-------------------------------------------------------"

for id in {03..50}; do
    NODE="dh2010pc${id}.utm.utoronto.ca"
    
    # 1. QUERY: Attempt a fast, silent SSH connection (2-second timeout)
    if ssh -o ConnectTimeout=2 -o BatchMode=yes -o StrictHostKeyChecking=no "$USER@$NODE" exit &>/dev/null; then
        
        # 2. PRIME: We got in! Now, wipe the slate clean.
        # Kill any zombie Java processes belonging to you so the ports are free.
        ssh -o BatchMode=yes -o StrictHostKeyChecking=no "$USER@$NODE" "pkill -u $USER java" &>/dev/null
        
        # 3. RECORD: Add this healthy, primed node to our official roster.
        echo "$NODE" >> "$ROSTER_FILE"
        echo "  [READY] $NODE has been primed."
        
    else
        echo "  [OFFLINE] $NODE is unreachable or locked."
    fi
done

echo "-------------------------------------------------------"
LIVE_COUNT=$(wc -l < "$ROSTER_FILE")
echo "$LIVE_COUNT primed machines ready in '$ROSTER_FILE'."
