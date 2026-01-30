#!/usr/bin/env bash

# Initialize flags
C_FLAG=false
U_FLAG=false
P_FLAG=false
I_FLAG=false
O_FLAG=false
W_FLAG=false

LIB="lib/gson-2.13.1.jar"
CLASSES="compiled"
CONFIG="config.json"

# Parse flags
while getopts "cupiow:" opt; do
  case $opt in
    c)
      C_FLAG=true
      ;;
    u)
      U_FLAG=true
      ;;
    p)
      P_FLAG=true
      ;;
    i)
      I_FLAG=true
      ;;
    o)
      O_FLAG=true
      ;;
    w)
      W_FLAG=true
      W_ARG="$OPTARG"
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
  esac
done

# Execute commands based on flags
if $C_FLAG; then
  echo "Running -c: compiling Java files..."
  javac -cp .:$LIB -d $CLASSES \
  src/Shared/*.java \
  src/UserService/*.java \
  src/ProductService/*.java \
  src/ISCS/*.java \
  src/OrderService/*.java
fi

if $U_FLAG; then
  echo "[UserService]"
  java -cp $CLASSES:$LIB UserService.UserService $CONFIG
fi

if $P_FLAG; then
  echo "[ProductService]"
  java -cp $CLASSES:$LIB ProductService.ProductService $CONFIG
fi

if $I_FLAG; then
  echo "[ISCS]"
  java -cp $CLASSES:$LIB ISCS.ISCS $CONFIG
fi

if $O_FLAG; then
    echo "[OrderService]"
    java -cp $CLASSES:$LIB OrderService.OrderService $CONFIG
fi

if $W_FLAG; then
  echo "[Workload]"
  java -cp $CLASSES:$LIB ISCS.WorkloadParser $CONFIG "$W_ARG"
fi
