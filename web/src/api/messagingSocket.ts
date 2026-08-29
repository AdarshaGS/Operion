import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { MessageResponse } from "./messaging";
import { getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

/**
 * One shared STOMP connection for the whole app, matching the backend's
 * com.operion.messaging.ws.WebSocketConfig: auth happens on the CONNECT frame (a bearer
 * token as a STOMP header, not an HTTP one - see StompAuthenticationInterceptor's doc for
 * why), and a client only ever subscribes to /topic/threads/{id} for a thread it's
 * already confirmed membership of via the REST API.
 *
 * listeners/subscriptions below track "what should be subscribed" independently of the
 * connection's own lifecycle, so a call to subscribeToThread before the handshake
 * finishes (or a later reconnect, given reconnectDelay) both resolve to the same
 * end state - every still-wanted thread subscribed exactly once - rather than a
 * one-shot onConnect callback that only the most recent caller would win.
 */
let client: Client | null = null;
const listeners = new Map<number, Set<(message: MessageResponse) => void>>();
const subscriptions = new Map<number, StompSubscription>();

function subscribeAllWanted(stompClient: Client) {
	for (const threadId of listeners.keys()) {
		if (!subscriptions.has(threadId)) {
			subscriptions.set(threadId, stompClient.subscribe(`/topic/threads/${threadId}`, (frame: IMessage) => {
				const message = JSON.parse(frame.body) as MessageResponse;
				listeners.get(threadId)?.forEach((listener) => listener(message));
			}));
		}
	}
}

function getClient(): Client {
	if (client) return client;
	client = new Client({
		webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
		connectHeaders: {
			get Authorization() {
				const session = getSession();
				return session ? `Bearer ${session.token}` : "";
			},
		} as Record<string, string>,
		reconnectDelay: 5000,
		onConnect: () => {
			subscriptions.clear();
			subscribeAllWanted(client!);
		},
	});
	client.activate();
	return client;
}

/** Subscribes to live messages for one thread; returns an unsubscribe function. */
export function subscribeToThread(threadId: number, onMessage: (message: MessageResponse) => void): () => void {
	const stompClient = getClient();
	if (!listeners.has(threadId)) {
		listeners.set(threadId, new Set());
	}
	listeners.get(threadId)!.add(onMessage);
	if (stompClient.connected) {
		subscribeAllWanted(stompClient);
	}

	return () => {
		const threadListeners = listeners.get(threadId);
		threadListeners?.delete(onMessage);
		if (threadListeners && threadListeners.size === 0) {
			listeners.delete(threadId);
			subscriptions.get(threadId)?.unsubscribe();
			subscriptions.delete(threadId);
		}
	};
}
