#!/bin/bash
set -euo pipefail
exec > >(tee -a /var/log/planet-trails-bootstrap.log) 2>&1
trap 'shutdown -h now' ERR
systemd-run --unit=planet-time-limit --on-active=2h /sbin/shutdown -h now
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-21-jre-headless osmium-tool curl unzip time
mkdir -p /opt/planet
curl -fSL --retry 3 https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip -o /opt/awscli.zip
unzip -q /opt/awscli.zip -d /opt
/opt/aws/install
cd /opt/planet
BUCKET=s3://outmap-planet-pilot-442073793062-20261025/planet-20261025-r1
aws s3 cp "$BUCKET/bundle/outmap.jar" outmap.jar --only-show-errors
aws s3 cp "$BUCKET/bundle/jar.sha256" jar.sha256 --only-show-errors
sha256sum -c jar.sha256
aws s3 cp "$BUCKET/bundle/prepare-trails.sh" prepare-trails.sh --only-show-errors
bash prepare-trails.sh
