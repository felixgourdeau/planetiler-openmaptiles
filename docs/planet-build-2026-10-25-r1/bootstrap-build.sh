#!/bin/bash
set -euo pipefail
exec > >(tee -a /var/log/planet-world-bootstrap.log) 2>&1
trap 'shutdown -h now' ERR
systemd-run --unit=planet-time-limit --on-active=5h /sbin/shutdown -h now
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-21-jre-headless curl unzip time
mkdir -p /opt/planet
curl -fSL --retry 3 https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip -o /opt/awscli.zip
unzip -q /opt/awscli.zip -d /opt
/opt/aws/install
cd /opt/planet
BUCKET=s3://outmap-planet-pilot-442073793062-20261025/planet-20261025-r1
for FILE in outmap.jar jar.sha256 ValidateWorld.class validator.sha256 build-world.sh auxiliary-inputs.sha256; do
 aws s3 cp "$BUCKET/bundle/v2/$FILE" "$FILE" --only-show-errors
done
sha256sum -c jar.sha256
sha256sum -c validator.sha256
bash build-world.sh
