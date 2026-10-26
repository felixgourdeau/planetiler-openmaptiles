#!/bin/bash
set -euo pipefail
exec > >(tee -a /var/log/index-benchmark-bootstrap.log) 2>&1
trap 'shutdown -h now' ERR
systemd-run --unit=index-benchmark-time-limit --on-active=60m /sbin/shutdown -h now
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-21-jre-headless curl unzip time
mkdir -p /opt/index-benchmark
curl -fSL --retry 3 https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip -o /opt/awscli.zip
unzip -q /opt/awscli.zip -d /opt
/opt/aws/install
cd /opt/index-benchmark
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
aws s3 cp "$BUCKET/r3/bundle/outmap.jar" outmap.jar --only-show-errors
printf '8da74801cee001a588690c9d4fc99eba076abff930515a93d6314446d9cfe4c4  outmap.jar\n' | sha256sum -c -
aws s3 cp "$BUCKET/index-benchmark-20261026/bundle/Pass1Benchmark.class" Pass1Benchmark.class --only-show-errors
aws s3 cp "$BUCKET/index-benchmark-20261026/bundle/runner.sha256" runner.sha256 --only-show-errors
sha256sum -c runner.sha256
aws s3 cp "$BUCKET/index-benchmark-20261026/bundle/run.sh" run.sh --only-show-errors
bash run.sh
