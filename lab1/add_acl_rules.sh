#!/usr/bin/env bash
set -eu

file=$1

readarray -t aclRules < <(jq -c ".[]" "$file")

onos localhost app activate acl

echo "Wait the firewall up for 3 seconds"
sleep 3

for aclRule in "${aclRules[@]}"
do
    tmpFile=$(mktemp)
    echo "$aclRule" > "$tmpFile"

    bash onos-acl localhost --json "$tmpFile"

    rm "$tmpFile"
done
