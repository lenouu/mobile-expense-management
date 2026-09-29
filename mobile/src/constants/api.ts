/**
 * Base URL of the backend REST API (port from backend application.properties: server.port).
 *
 * Override with EXPO_PUBLIC_API_URL, e.g. on a phone where "localhost" is the phone itself:
 *   EXPO_PUBLIC_API_URL=http://192.168.1.25:8083/api
 */
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8083/api';
