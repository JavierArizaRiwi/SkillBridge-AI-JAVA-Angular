import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';

export interface RecordedRequest {
  method: string;
  url: string;
  body: string;
  authorization?: string;
}

export interface FakeServer {
  url: string;
  requests: RecordedRequest[];
  close(): Promise<void>;
}

type Handler = (
  req: IncomingMessage,
  res: ServerResponse,
  body: string,
) => void | Promise<void>;

/** Servidor HTTP efímero para simular Zoom/Google/Discord (FR-021). */
export async function startFakeServer(handler: Handler): Promise<FakeServer> {
  const requests: RecordedRequest[] = [];
  const server = createServer((req, res) => {
    let body = '';
    req.on('data', (chunk: Buffer) => {
      body += chunk.toString();
    });
    req.on('end', () => {
      requests.push({
        method: req.method ?? 'GET',
        url: req.url ?? '',
        body,
        authorization: req.headers.authorization,
      });
      Promise.resolve(handler(req, res, body)).catch(() => {
        res.statusCode = 500;
        res.end('{}');
      });
    });
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address() as AddressInfo;

  return {
    url: `http://127.0.0.1:${address.port}`,
    requests,
    close: () =>
      new Promise<void>((resolve) => {
        server.close(() => resolve());
      }),
  };
}

export function json(res: ServerResponse, status: number, payload: unknown): void {
  res.statusCode = status;
  res.setHeader('content-type', 'application/json');
  res.end(JSON.stringify(payload));
}
