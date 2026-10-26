/** Export the shared marine drawings for native image registration or sprite packing. */
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { deflateSync } from 'node:zlib';
import { createMarineImages } from '../styles/marine.ts';
import type { MarineImage } from '../styles/marine.ts';

const crc32 = (bytes: Uint8Array): number => {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ ((crc & 1) === 1 ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
};

const chunk = (type: string, payload: Uint8Array): Buffer => {
  const body = Buffer.concat([Buffer.from(type, 'ascii'), payload]);
  const length = Buffer.alloc(4);
  length.writeUInt32BE(payload.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, checksum]);
};

const encodePng = ({ width, height, data }: MarineImage): Buffer => {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6; // RGBA
  const stride = width * 4;
  const scanlines = Buffer.alloc((stride + 1) * height);
  for (let row = 0; row < height; row += 1) {
    scanlines.set(data.subarray(row * stride, (row + 1) * stride), row * (stride + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header), chunk('IDAT', deflateSync(scanlines)), chunk('IEND', Buffer.alloc(0)),
  ]);
};

const outputDirectory = process.argv[2];
if (!outputDirectory) throw new Error('Usage: node scripts/export-marine-images.mts <output-directory>');
await mkdir(resolve(outputDirectory), { recursive: true });
const manifest: Record<string, { file: string; width: number; height: number; pixelRatio: number }> = {};
for (const [id, image] of Object.entries(createMarineImages())) {
  const file = `${id}.png`;
  await writeFile(resolve(outputDirectory, file), encodePng(image));
  manifest[id] = { file, width: image.width, height: image.height, pixelRatio: 2 };
}
await writeFile(resolve(outputDirectory, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Exported ${Object.keys(manifest).length} marine images to ${resolve(outputDirectory)}`);
