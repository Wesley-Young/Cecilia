import { type inferRouterOutputs, initTRPC, TRPCError } from '@trpc/server';
import { ClipboardItem, clipboard, nativeImage, net } from 'electron';
import { z } from 'zod';

const t = initTRPC.create({ isServer: true });

export interface ShellInfo {
  name: string;
  version: string;
  platform: NodeJS.Platform;
  /** Running against the mock client instead of a real account. */
  mock: boolean;
}

/** Puts a picture the window shows on the clipboard: a data URL as it is, an https one downloaded first. */
async function copyImage(src: string) {
  let image: Electron.NativeImage;
  if (src.startsWith('data:')) {
    image = nativeImage.createFromDataURL(src);
  } else {
    const response = await net.fetch(src);
    if (!response.ok) throw new TRPCError({ code: 'BAD_GATEWAY', message: '图片下载失败' });
    image = nativeImage.createFromBuffer(Buffer.from(await response.arrayBuffer()));
  }
  // Electron decodes PNG and JPEG everywhere; other formats depend on the platform.
  if (image.isEmpty()) throw new TRPCError({ code: 'BAD_REQUEST', message: '无法复制这种格式的图片' });
  // As PNG, which every app reads.
  await clipboard.write([
    new ClipboardItem({ 'image/png': new Blob([new Uint8Array(image.toPNG())], { type: 'image/png' }) }),
  ]);
}

/** The main process's own RPC surface, served over app://. Account features live in the core router instead. */
export function createShellRouter(info: ShellInfo) {
  return t.router({
    system: {
      info: t.procedure.query(() => info),
    },
    clipboard: {
      // Only what the window can show: images from the server and ones it holds itself.
      copyImage: t.procedure
        .input(z.object({ src: z.string().regex(/^(https:|data:image\/)/) }))
        .mutation(({ input }) => copyImage(input.src)),
    },
  });
}

export type ShellRouter = ReturnType<typeof createShellRouter>;
export type ShellOutputs = inferRouterOutputs<ShellRouter>;
