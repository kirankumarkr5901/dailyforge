import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage({ colorScheme: 'light' }); // OS light, app should still be dark
await page.goto('https://dailyforgekr.netlify.app', { waitUntil: 'networkidle' });
await page.waitForTimeout(2000);
console.log(JSON.stringify(await page.evaluate(() => ({
  dataTheme: document.documentElement.getAttribute('data-theme'),
  surface: getComputedStyle(document.documentElement).getPropertyValue('--surface').trim(),
  bodyBg: getComputedStyle(document.body).backgroundColor,
})), null, 2));
await page.screenshot({ path: 'C:/Projects/DailyForge/frontend/.pw-scratch/dark-fixed.png' });
await browser.close();
