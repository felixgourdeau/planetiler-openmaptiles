import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, existsSync, openSync, closeSync, unlinkSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

// Operational monitor, not a scheduler: one approved build, no automatic rebuilds.
const report = resolve('docs/planet-build-2026-10-25-r1');
const temporary = '/private/tmp/outmap-planet-build-20261025';
const lockPath = `${temporary}/monitor.lock`;
const lock = openSync(lockPath, 'wx');
writeFileSync(lock, String(process.pid));
process.once('exit', () => { closeSync(lock); unlinkSync(lockPath); });
process.once('SIGTERM', () => process.exit(143));
const bucket = 'outmap-planet-pilot-442073793062-20261025';
const prefix = 'planet-20261025-r1';
const aws = (args: string[]): unknown => {
  const output = execFileSync('aws', [...args, '--region', 'us-east-1', '--output', 'json'], { encoding: 'utf8', stdio: 'pipe' });
  return output.trim() ? JSON.parse(output) as unknown : null;
};
type Completion = { exitCode: number; finishedAt: string; archive?: string };
type Instance = { InstanceId: string; State: { Name: string } };
type LaunchResult = { Instances: Instance[] };
const status = (stage: string): Completion | null => {
  const path = `${temporary}/${stage}-status.json`;
  try {
    aws(['s3api', 'get-object', '--bucket', bucket, '--key', `${prefix}/status/${stage}.json`, path]);
    const value = JSON.parse(readFileSync(path, 'utf8')) as Completion;
    if (!Number.isInteger(value.exitCode)) throw new Error(`Malformed ${stage} completion`);
    writeFileSync(`${report}/${stage}-completion.json`, JSON.stringify(value, null, 2));
    return value;
  } catch (error: unknown) {
    const failure = error as { stderr?: Buffer | string };
    if (/NoSuchKey|404|Not Found/.test(String(failure.stderr))) return null;
    throw error;
  }
};
const progress = (): void => {
  for (const name of ['elevations', 'prepare-trails']) {
    const path = `${temporary}/${name}-monitor.log`;
    try {
      execFileSync('aws', ['s3', 'cp', `s3://${bucket}/${prefix}/logs/${name}.log`, path,
        '--region', 'us-east-1', '--only-show-errors'], { stdio: 'pipe' });
      const lines = readFileSync(path, 'utf8').trim().split('\n');
      console.log(new Date().toISOString(), name, lines.at(-1));
    } catch (error: unknown) { console.log(name, 'log unavailable', String(error)); }
  }
};
const main = async (): Promise<void> => {
  const deadline = Date.now() + 4.5 * 60 * 60 * 1000;
  for (;;) {
    const elevations = status('elevations');
    const trails = status('trails-v2');
    if (elevations && elevations.exitCode !== 0 || trails && trails.exitCode !== 0)
      throw new Error('Preparation failed; inspect retained logs before any build launch');
    if (elevations && trails) break;
    if (Date.now() > deadline) throw new Error('Preparation monitoring deadline reached');
    progress();
    await delay(60_000);
  }
  const request = JSON.parse(readFileSync(`${temporary}/launch-trails.json`, 'utf8')) as Record<string, unknown>;
  request.InstanceType = 'm7gd.16xlarge';
  request.InstanceMarketOptions = { MarketType: 'spot', SpotOptions: { SpotInstanceType: 'one-time', InstanceInterruptionBehavior: 'terminate', MaxPrice: '1.65' } };
  request.NetworkInterfaces = [{ DeviceIndex: 0, SubnetId: 'subnet-a3fae5fe', Groups: ['sg-73b33a3a'], AssociatePublicIpAddress: true, DeleteOnTermination: true }];
  request.ClientToken = randomUUID();
  request.UserData = Buffer.from(readFileSync(`${report}/bootstrap-build.sh`)).toString('base64');
  request.TagSpecifications = [{ ResourceType: 'instance', Tags: [
    { Key: 'Name', Value: 'outmap-planet-20261025-r1-build' },
    { Key: 'Purpose', Value: 'temporary-planet-build' }, { Key: 'Run', Value: prefix }
  ] }];
  const requestPath = `${temporary}/launch-build.json`;
  // Preserve the request/client token across monitor restarts and uncertain API outcomes.
  if (!existsSync(requestPath)) writeFileSync(requestPath, JSON.stringify(request, null, 2));
  const launched = aws(['ec2', 'run-instances', '--cli-input-json', `file://${requestPath}`]) as LaunchResult;
  writeFileSync(`${report}/build-instance.json`, JSON.stringify(launched, null, 2));
  console.log('BUILD_LAUNCHED', launched.Instances[0].InstanceId);
  const buildDeadline = Date.now() + 5.5 * 60 * 60 * 1000;
  for (;;) {
    const completion = status('build');
    if (completion) {
      mkdirSync(`${report}/build-results`, { recursive: true });
      execFileSync('aws', ['s3', 'sync', `s3://${bucket}/${prefix}/logs/build`, `${report}/build-results`,
        '--region', 'us-east-1', '--only-show-errors'], { stdio: 'inherit' });
      console.log('BUILD_FINISHED', JSON.stringify(completion));
      if (completion.exitCode !== 0) process.exitCode = 1;
      return;
    }
    if (Date.now() > buildDeadline) throw new Error('Build monitoring deadline reached');
    try {
      execFileSync('aws', ['s3', 'cp', `s3://${bucket}/${prefix}/logs/build/build-world.log`,
        `${temporary}/build-monitor.log`, '--region', 'us-east-1', '--only-show-errors'], { stdio: 'pipe' });
      const lines = readFileSync(`${temporary}/build-monitor.log`, 'utf8').replace(/\u001b\[[0-9;]*m/g, '').trim().split('\n');
      console.log(new Date().toISOString(), lines.filter(line => /INF|complete|start/.test(line)).at(-1));
    } catch (error: unknown) { console.log('Build bootstrap/log pending', String(error)); }
    await delay(60_000);
  }
};
main().catch((error: unknown) => { console.error(error); process.exitCode = 1; });
