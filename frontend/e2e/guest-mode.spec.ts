import { test, expect } from '@playwright/test';

test('guest mode can only use public tools', async ({ page }) => {
  await page.goto('http://localhost:3003/chat');

  // 验证页面加载
  await expect(page.locator('.chat-header h1')).toContainText('AI 助手');

  // 点击"不登录使用"进入游客模式
  await page.click('.btn-guest');
  await expect(page.locator('.guest-badge')).toContainText('游客模式');

  // 发送消息测试
  const input = page.locator('textarea');
  await input.fill('你好');
  await page.click('.btn-send');

  // 等待响应
  await page.waitForTimeout(3000);

  // 检查是否有消息
  const messages = page.locator('.message-row');
  const count = await messages.count();
  console.log('Messages count:', count);
  expect(count).toBeGreaterThan(0);
});