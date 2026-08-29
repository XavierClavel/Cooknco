/**
 * Base URL for a backend call, chosen by where the code is running.
 *
 * On the server the public hostname is not resolvable from inside the cluster
 * network, and going out to it would mean a pointless DNS + TLS round trip back
 * into the same deployment, so server-side fetches address the backend service
 * directly. In the browser the relative path is same-origin and goes through
 * the Nitro proxy.
 */
export function apiBase(): string {
  const config = useRuntimeConfig()
  return import.meta.server ? `${config.backendUrl}/api/v1` : config.public.apiUrl
}
