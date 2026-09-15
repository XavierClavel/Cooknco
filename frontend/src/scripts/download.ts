import apiClient from '@/plugins/axios.js';

export {
  downloadPdf,
}

/**
 * Fetches a PDF export and hands it to the browser as a download.
 *
 * Rejects on failure rather than logging, so the caller can tell the user the download did
 * not happen — every export here is admin-only and the backend enforces that, so a refusal
 * is something the operator needs to see rather than a state to render around.
 *
 * @param path the export endpoint, below the API root
 * @param fallbackName what to save as when the response names nothing
 */
async function downloadPdf(path: string, fallbackName: string) {
  const response = await apiClient.get(path, {
    responseType: 'blob',
    headers: {
      'Accept': 'application/pdf'
    }
  }).catch(async (error) => { throw await readBlobBody(error) })
  const url = window.URL.createObjectURL(new Blob([response.data], {type: 'application/pdf'}));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filenameOf(response) ?? fallbackName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Deferred, not immediate: some browsers only start reading the blob after the click
  // returns, and revoking it first cancels the download. Without revoking at all it would
  // sit in memory for as long as the page lives.
  setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

/** The name the backend chose for the file, from `Content-Disposition`. */
function filenameOf(response) {
  const disposition = response.headers['content-disposition']
  if (!disposition) return null
  return disposition.match(/filename="([^"]+)"/)?.[1] ?? null
}

/**
 * Puts a refusal's body back into readable form before it is rethrown.
 *
 * Asking for a PDF means axios hands a failed response back as a Blob, so the cause key
 * the backend answers with — `cookbook_too_large_to_export`, say — reaches the caller as an
 * unreadable object and every refusal reports the same generic failure. Read as text it is
 * that key again, which is what `toErrorMessage` expects to find.
 */
async function readBlobBody(error) {
  const body = error?.response?.data
  if (body instanceof Blob) {
    // The generic message the caller falls back to is still better than losing the error.
    try { error.response.data = await body.text() } catch { /* empty */ }
  }
  return error
}
