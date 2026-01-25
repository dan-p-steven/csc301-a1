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

# Parse flags
while getopts "cupiow" opt; do
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

fi

if $U_FLAG; then
  echo "[UserService]"
  java -cp $CLASSES:$LIB UserService.UserService
fi

if $P_FLAG; then
  echo "Running -p: placeholder for -p command"
  # Add your -p code here
fi

if $I_FLAG; then
  echo "Running -i: placeholder for -i command"
  # Add your -i code here
fi

if $O_FLAG; then
  echo "Running -o: placeholder for -o command"
  # Add your -o code here
fi

if $W_FLAG; then
  echo "Running -w: placeholder for -w command"
  # Add your -w code here
fi

