import apiClient from '@/plugins/axios.js';

export {
  downloadPdf,
  downloadFile,
}

/**
 * Fetches a PDF export and hands it to the browser as a download.
 *
 * Rejects on failure rather than logging, so the caller can tell the user the download did
 * not happen — the buttons are only shown to an account the export is open to, so a
 * refusal is something the user needs to see rather than a state to render around.
 *
 * @param path the export endpoint, below the API root
 * @param fallbackName what to save as when the response names nothing
 */
async function downloadPdf(path: string, fallbackName: string) {
  return await downloadFile(path, fallbackName, 'application/pdf')
}

/**
 * The same, for any export.
 *
 * The blob is always built with the type asked for rather than with whatever the response
 * carried: a `.cook` file goes out as `text/x-cooklang`, which no browser knows, and one
 * given to a blob unqualified is offered as something to open rather than to save.
 *
 * @param path the export endpoint, below the API root
 * @param fallbackName what to save as when the response names nothing
 * @param mime what to ask for, and what to hand the browser
 */
async function downloadFile(path: string, fallbackName: string, mime: string) {
  const response = await apiClient.get(path, {
    responseType: 'blob',
    headers: {
      'Accept': mime
    }
  }).catch(async (error) => { throw await readBlobBody(error) })
  const url = window.URL.createObjectURL(new Blob([response.data], {type: mime}));
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
 * Asking for a file means axios hands a failed response back as a Blob, so the cause key
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
