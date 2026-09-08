import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const csp = [];
page.on('console', (m) => { if (/content security policy/i.test(m.text())) csp.push(m.text().slice(0, 120)); });

await page.goto('https://dailyforgekr.netlify.app', { waitUntil: 'networkidle' });
await page.waitForTimeout(2000);
console.log('data-theme:', await page.evaluate(() => document.documentElement.getAttribute('data-theme')));
console.log('capabilities from API:', await page.evaluate(async () => {
  const r = await fetch('https://dailyforge-api.onrender.com/api/v1/auth/capabilities');
  return await r.text();
}));

const skip = page.getByRole('button', { name: /skip/i });
if (await skip.count()) { await skip.click().catch(() => {}); await page.waitForTimeout(400); }
await page.locator('button.shell__signin').click();
await page.waitForTimeout(4000);

console.log('sheet HTML contains google slot:', await page.evaluate(() =>
  !!document.querySelector('[class*="google"], [id*="google"], [class*="auth__google"]')));
console.log('gsi script in DOM:', await page.locator('script[src*="gsi/client"]').count());
console.log('CSP violations:', csp.length ? csp : 'none');
await page.screenshot({ path: 'C:/Projects/DailyForge/frontend/.pw-scratch/g2.png' });
await browser.close();
