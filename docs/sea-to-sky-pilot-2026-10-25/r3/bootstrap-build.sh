#!/bin/bash
set -euo pipefail
exec > >(tee -a /var/log/pilot-r3-bootstrap.log) 2>&1
trap 'shutdown -h now' ERR
systemd-run --unit=pilot-time-limit --on-active=3h /sbin/shutdown -h now
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-21-jre-headless osmium-tool curl unzip time
mkdir -p /opt/pilot
curl -fSL --retry 3 https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip -o /opt/awscli.zip
unzip -q /opt/awscli.zip -d /opt
/opt/aws/install
cd /opt/pilot
aws s3 cp s3://outmap-planet-pilot-442073793062-20261025/r3/bundle/outmap.jar outmap.jar --only-show-errors
aws s3 cp s3://outmap-planet-pilot-442073793062-20261025/r3/bundle/jar-sha256.txt jar-sha256.txt --only-show-errors
sha256sum -c jar-sha256.txt
aws s3 cp s3://outmap-planet-pilot-442073793062-20261025/r3/bundle/build.sh build.sh --only-show-errors
bash build.sh
