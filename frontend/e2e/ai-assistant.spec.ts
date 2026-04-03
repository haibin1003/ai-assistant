import { test, expect } from '@playwright/test';

test.describe('AI Assistant E2E', () => {
  test('main chat flow', async ({ page }) => {
    // Step 1: Open AI Assistant frontend
    await page.goto('http://localhost:3002/chat');
    await page.waitForLoadState('networkidle');

    // Step 2: Check page loaded
    const title = await page.title();
    console.log('Page title:', title);

    // Step 3: Find and check for chat input
    const chatInput = page.locator('input[type="text"], textarea').first();
    const isVisible = await chatInput.isVisible().catch(() => false);
    console.log('Chat input visible:', isVisible);

    // Step 4: Check for send button
    const sendButton = page.locator('button:has-text("发送"), button:has-text("Send")').first();
    const buttonVisible = await sendButton.isVisible().catch(() => false);
    console.log('Send button visible:', buttonVisible);

    // Assert page loaded
    await expect(page).toHaveURL(/chat/);
  });

  test('test full flow with OSRM', async ({ page }) => {
    // Step 1: Push user context to AI Assistant
    console.log('Pushing user context...');
    const pushContext = await fetch('http://localhost:8081/api/v1/context/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: 'e2e-test-session',
        systemId: 'osrm',
        user: { id: 1, username: 'admin', roles: ['ADMIN'] },
        credentials: { username: 'admin', password: 'admin123' }
      })
    });
    console.log('Push context status:', pushContext.status);
    const contextResult = await pushContext.json();
    console.log('Push context result:', JSON.stringify(contextResult));

    // Step 2: Open AI Assistant and try to send a message
    await page.goto('http://localhost:3002/chat');
    await page.waitForLoadState('networkidle');

    // Step 3: Find the chat input
    const chatInput = page.locator('input[type="text"], textarea').first();
    await expect(chatInput).toBeVisible();

    // Step 4: Type a message
    await chatInput.fill('你好');

    // Step 5: Click send button
    const sendButton = page.locator('button:has-text("发送"), button[type="submit"]').first();
    await sendButton.click();

    // Step 6: Wait for response (this will trigger the LLM call)
    await page.waitForTimeout(3000);

    // Step 7: Take screenshot of the result
    await page.screenshot({ path: 'ai-assistant-chat-result.png' });

    // Check if there's any response in the chat
    const messages = await page.locator('.message, .chat-message, [class*="message"]').count();
    console.log('Number of messages:', messages);
  });
});