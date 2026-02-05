#!/bin/bash
# title: Catalog Client Test Harness
# author: Dominic Evans
# date: February 4, 2026
# version: 1.0
# copyright: 2026 Dominic Evans

bin_dir="bin"
lib_dir="lib/*"
sandbox="test/sandbox"
share1="${sandbox}/share1"
down1="${sandbox}/down1"
share2="${sandbox}/share2"
down2="${sandbox}/down2"

# Clean up and create the sandbox directories
rm -rf "${sandbox}"
mkdir -p "${share1}" "${share2}" "${down1}" "${down2}"

# Start the Catalog Server
java -cp "${bin_dir}:${lib_dir}" catalog_application.server.CatalogServer &
server_pid=$!
sleep 3

# Start Client 1 (seeder) on port 2051
java -cp "${bin_dir}:${lib_dir}" catalog_application.client.CatalogClient \
    2051 "${share1}" \
    "${down1}" &
c1_pid=$!

# Start Client 2 (leecher) on port 2052
java -cp "${bin_dir}:${lib_dir}" catalog_application.client.CatalogClient \
    2052 \
    "${share2}" \
    "${down2}" &
c2_pid=$!

echo "--------------------------------------------------"
echo "[TEST] Server and clients are live."
echo "[TEST] Client 1 share directory: ${share1}"
echo "[TEST] Client 1 download directory: ${down1}"
echo "[TEST] Client 2 download directory: ${share2}"
echo "[TEST] Client 2 download directory: ${down2}"
echo "[TEST] Press <RET> to kill all test processes."
echo "--------------------------------------------------"
read

# Cleanup
kill "${server_pid}" "${c1_pid}" "${c2_pid}"
echo "[TEST] Cleanup complete."
