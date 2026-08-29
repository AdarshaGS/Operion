// Desktop-responsive / full-page-scroll checks for tests/flows/. AppLayout's <main> is
// the actual scroll container (content sits under a fixed AppBar/Drawer, see AppLayout.tsx),
// not the window/body - so "scrolling" here scrolls that element, not window.scrollTo.
import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

export const DESKTOP_VIEWPORT = { width: 1440, height: 900 };

/** Scrolls the page's main content area from top to bottom and back, asserting there is
 * no horizontal overflow (a real desktop-responsive-layout bug) at this viewport size. */
export async function verifyFullPageScroll(page: Page) {
	const overflowsHorizontally = await page.evaluate(
		() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
	);
	expect(overflowsHorizontally, "Page has unexpected horizontal overflow at desktop viewport").toBe(false);

	const main = page.locator("main");
	await main.evaluate((el) => el.scrollTo({ top: el.scrollHeight, behavior: "instant" }));
	await page.waitForTimeout(150);
	const scrolledToBottom = await main.evaluate((el) => el.scrollTop > 0 || el.scrollHeight <= el.clientHeight);
	expect(scrolledToBottom, "Main content area did not scroll despite overflowing content").toBe(true);
	await main.evaluate((el) => el.scrollTo({ top: 0, behavior: "instant" }));
}
