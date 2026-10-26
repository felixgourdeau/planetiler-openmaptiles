#!/bin/bash
set -euo pipefail
exec > >(tee -a /var/log/planet-elevations-bootstrap.log) 2>&1
trap 'shutdown -h now' ERR
systemd-run --unit=planet-time-limit --on-active=4h /sbin/shutdown -h now
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl unzip
mkdir -p /opt/planet /opt/node
curl -fSL --retry 3 https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip -o /opt/awscli.zip
unzip -q /opt/awscli.zip -d /opt
/opt/aws/install
cd /opt/node
curl -fSL --retry 3 https://nodejs.org/dist/v24.7.0/node-v24.7.0-linux-arm64.tar.xz -o node.tar.xz
curl -fSL --retry 3 https://nodejs.org/dist/v24.7.0/SHASUMS256.txt -o sums.txt
awk '$2 == "node-v24.7.0-linux-arm64.tar.xz" {print $1 "  node.tar.xz"}' sums.txt > selected.sha256
sha256sum -c selected.sha256
tar -xJf node.tar.xz --strip-components=1
ln -s /opt/node/bin/node /usr/local/bin/node
cd /opt/planet
aws s3 cp s3://outmap-planet-pilot-442073793062-20261025/planet-20261025-r1/bundle/elevations.sh elevations.sh --only-show-errors
bash elevations.sh
