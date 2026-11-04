import { readFile } from 'node:fs/promises';
import { extname, resolve, sep } from 'node:path';

const contentTypes: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

/**
 * Production CSP. Scripts are limited to bundled files; inline styles stay allowed because popover positioning
 * writes style attributes at runtime. Images may come from https, for avatars and message images, and from blob URLs,
 * for images pasted into the composer.
 */
const contentSecurityPolicy = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
].join('; ');

const notFound = () => new Response('Not Found', { status: 404 });

export interface AssetOptions {
  /** Built renderer files, served in production. */
  rendererDir: string;
  /** Vite dev server URL; set in development only. */
  rendererDevUrl?: string | undefined;
}

/** Serves the renderer: proxies to the Vite dev server in development, reads built files otherwise. */
export function createAssetHandler(options: AssetOptions) {
  const { rendererDir, rendererDevUrl } = options;

  if (rendererDevUrl) {
    return (request: Request, url: URL) =>
      fetch(new URL(url.pathname + url.search, rendererDevUrl), {
        method: request.method,
        headers: { accept: request.headers.get('accept') ?? '*/*' },
      });
  }

  return async (request: Request, url: URL) => {
    if (request.method !== 'GET' && request.method !== 'HEAD') return new Response(null, { status: 405 });
    let pathname: string;
    try {
      pathname = decodeURIComponent(url.pathname);
    } catch {
      return notFound();
    }
    // Paths without an extension are client-side routes and fall back to index.html.
    const file = extname(pathname) ? resolve(rendererDir, `.${pathname}`) : resolve(rendererDir, 'index.html');
    if (!file.startsWith(rendererDir + sep)) return notFound();
    let body: Buffer;
    try {
      body = await readFile(file);
    } catch {
      return notFound();
    }
    const type = contentTypes[extname(file)] ?? 'application/octet-stream';
    const headers = new Headers({ 'content-type': type, 'x-content-type-options': 'nosniff' });
    if (type.startsWith('text/html')) headers.set('content-security-policy', contentSecurityPolicy);
    return new Response(request.method === 'HEAD' ? null : new Uint8Array(body), { headers });
  };
}
