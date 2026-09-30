#!/bin/sh
set -eu
attempt=0
while [ "$attempt" -lt 60 ]; do
  if sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t flashflow-orders &&
     sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t flashflow-timeouts; then
    exit 0
  fi
  attempt=$((attempt+1))
  sleep 2
done
echo 'Broker/topic initialization timed out' >&2
exit 1
