#!/bin/bash
# title: Catalog Client Test Harness
# author: Dominic Evans
# date: February 4, 2026
# version: 1.0
# copyright: 2026 Dominic Evans

# Enable job management
set -m

# This script must be invoked from the project root directory
project_root_dir=$PWD
num_clients=${1:-2}  # The desired number of clients
base_port=2050

bin_dir="${PWD}/bin"
lib_dir="${PWD}/lib/*"
sandbox="${PWD}/test/sandbox"

# Array to store all created client PIDs
pids=()

# Kill all tracked PIDs on exit
cleanup() {
    echo -e "\n[TEST] Cleaning up background clients..."
    for pid in "${pids[@]}"; do
        # If the process is running, kill it
        if ps -p "${pid}" > /dev/null; then
            kill -9 -"${pid}"
            echo "Stopped process ${pid}"
        fi
    done
    # orbd always remains after the server is killed, so we dispose of it manually
    # pkill orbd
    exit
}

# Trigger cleanup on SIGINT and SIGTERM
trap cleanup SIGINT SIGTERM

# Clean up and create the sandbox directories
rm -rf "${sandbox}"

# Start the Catalog Server
java -cp "${bin_dir}:${lib_dir}" catalog_application.server.CatalogServer &
# Capture the server PID
pids+=($!)

# Allow time for the server to come properly online before clients start
sleep 5

for i in $(seq $num_clients); do
    client_dir="${sandbox}/client_${i}"
    client_port=$((base_port + i))

    echo "[TEST] Starting Client ${i} on port ${client_port}"

    mkdir -p "${client_dir}/share" "${client_dir}/download"

    # Create a unique file in each sandbox
    echo "Data from client ${i}" > "${client_dir}/share/client_${i}_file.txt"

    # Ensure that each client has access to the requisite configuration file
    cp "${project_root_dir}/client.properties" "${client_dir}/"

    # Run each client in their own sandbox
    cd "${client_dir}"
    java -cp "${project_root_dir}/bin:${project_root_dir}/lib/*" \
            catalog_application.client.CatalogClient \
            "${client_port}" \
            "${client_dir}/share" \
            "${client_dir}/download" &
    # Capture the PID of the subshell
    pids+=($!)
    # Return the working directory to the root directory between client invokations
    cd "${project_root_dir}"
done

echo "[TEST] ${num_clients} running. PIDs: ${pids[@]}"
echo "[TEST] Press Ctrl-C to stop all processes."

# Keep the scipt alive listening for the trap
wait
