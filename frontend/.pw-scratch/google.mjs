import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const blocked = [];
page.on('console', (m) => { const t = m.text(); if (/content security policy|refused to load|blocked/i.test(t)) blocked.push(t); });
page.on('requestfailed', (r) => { if (/google/i.test(r.url())) blocked.push(`FAILED ${r.url()} :: ${r.failure()?.errorText}`); });

await page.goto('https://dailyforgekr.netlify.app', { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
const skip = page.getByRole('button', { name: /skip/i });
if (await skip.count()) { await skip.click().catch(() => {}); await page.waitForTimeout(400); }
await page.locator('button.shell__signin').click();
await page.waitForTimeout(3500);

console.log('Google script tags:', await page.locator('script[src*="accounts.google.com"]').count());
console.log('Google iframe/button present:', await page.locator('iframe[src*="accounts.google.com"], div[id*="g_id"], #credential_picker_container').count());
console.log('CSP / load failures:', blocked.length ? blocked : 'none');
await page.screenshot({ path: 'C:/Projects/DailyForge/frontend/.pw-scratch/google-signin.png' });
await browser.close();
