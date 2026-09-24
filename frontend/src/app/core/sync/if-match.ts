import { HttpHeaders } from '@angular/common/http';

/**
 * The header that makes an edit conditional on the copy the client actually read.
 *
 * Every editable entity carries a `version` from the server; echoing it here is what
 * lets the server refuse a write from a device whose copy has since been overtaken,
 * instead of letting it overwrite the newer data (see StaleWrite on the backend).
 *
 * A missing version sends no header at all, which the server reads as "no opinion" and
 * lets through — so a caller that genuinely has no prior read is not forced to invent
 * one, and adding the guard to a new endpoint is opt-in rather than a breaking change.
 */
export function ifMatch(version: number | null | undefined): { headers?: HttpHeaders } {
  return version === null || version === undefined
    ? {}
    : { headers: new HttpHeaders({ 'If-Match': String(version) }) };
}
