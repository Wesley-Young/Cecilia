// Messages of tRPC over a MessagePort, shared by the account process (server) and the renderer (client).

export type PortProcedureType = 'query' | 'mutation' | 'subscription';

export type PortClientMessage =
  | { kind: 'request'; id: number; type: PortProcedureType; path: string; input: unknown }
  /** Ends a subscription. */
  | { kind: 'stop'; id: number };

export type PortServerMessage =
  /** The result of a query or mutation. */
  | { kind: 'result'; id: number; data: unknown }
  /** One item of a subscription. */
  | { kind: 'data'; id: number; data: unknown }
  /** A subscription that ended by itself. */
  | { kind: 'stopped'; id: number }
  /** `error` is the router's error shape. */
  | { kind: 'error'; id: number; error: unknown };
