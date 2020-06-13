#!/usr/bin/env bash

while true; do
  echo "response" | nc -vl localhost 9001
done
