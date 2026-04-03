import { test, expect } from '@playwright/test';

/**
 * AI Assistant Service E2E Tests
 */

test.describe('AI Assistant Service', () => {
  test.describe('Health Check', () => {
    test('backend health check should return ok', async ({ request }) => {
      const response = await request.get('http://localhost:8081/health');
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.status).toBe('ok');
    });
  });

  test.describe('System Registration', () => {
    test('should list registered systems', async ({ request }) => {
      const response = await request.get('http://localhost:8081/api/v1/systems');
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
      expect(Array.isArray(data.data)).toBe(true);
    });

    test('should register a new system', async ({ request }) => {
      const response = await request.post('http://localhost:8081/api/v1/systems', {
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          systemId: 'test-system',
          systemName: 'Test System',
          mcpGatewayUrl: 'http://localhost:3000/api/mcp',
          authType: 'bearer',
          toolPrefix: 'test_',
          description: 'Test system for E2E tests'
        })
      });
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
    });

    test('should get system details', async ({ request }) => {
      const response = await request.get('http://localhost:8081/api/v1/systems/test-system');
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
      expect(data.data.systemId).toBe('test-system');
    });

    test('should update system', async ({ request }) => {
      const response = await request.put('http://localhost:8081/api/v1/systems/test-system', {
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          systemName: 'Updated Test System',
          description: 'Updated description'
        })
      });
      expect(response.status()).toBe(200);
    });

    test('should unregister system', async ({ request }) => {
      const response = await request.delete('http://localhost:8081/api/v1/systems/test-system');
      expect(response.status()).toBe(200);
    });
  });

  test.describe('Context Management', () => {
    const testSessionId = 'e2e-test-session-' + Date.now();

    test('should push user context', async ({ request }) => {
      const response = await request.post('http://localhost:8081/api/v1/context/push', {
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          sessionId: testSessionId,
          systemId: 'osrm',
          user: {
            id: 'user-001',
            username: 'testuser',
            roles: ['USER'],
            permissions: ['read']
          },
          credentials: {
            accessToken: 'test-token',
            expiresIn: 7200
          }
        })
      });
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
      expect(data.data.sessionId).toBe(testSessionId);
    });

    test('should validate session', async ({ request }) => {
      const response = await request.get(`http://localhost:8081/api/v1/context/${testSessionId}/valid`);
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
      expect(data.data.valid).toBe(true);
    });

    test('should clear context', async ({ request }) => {
      const response = await request.delete(`http://localhost:8081/api/v1/context/${testSessionId}`);
      expect(response.status()).toBe(200);
    });

    test('should return invalid for cleared session', async ({ request }) => {
      const response = await request.get(`http://localhost:8081/api/v1/context/${testSessionId}/valid`);
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.data.valid).toBe(false);
    });
  });

  test.describe('API Key Configuration', () => {
    test('should get API key configs', async ({ request }) => {
      const response = await request.get('http://localhost:8081/api/v1/config/api-key');
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
      expect(Array.isArray(data.data)).toBe(true);
    });

    test('should set API key', async ({ request }) => {
      const response = await request.put('http://localhost:8081/api/v1/config/api-key/test-provider', {
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          providerType: 'llm',
          apiKey: 'test-api-key-123'
        })
      });
      expect(response.status()).toBe(200);
    });

    test('should delete API key', async ({ request }) => {
      const response = await request.delete('http://localhost:8081/api/v1/config/api-key/test-provider');
      expect(response.status()).toBe(200);
    });
  });

  test.describe('Skills Management', () => {
    const testSkillId = 'test-skill-' + Date.now();

    test('should create a skill', async ({ request }) => {
      const response = await request.post('http://localhost:8081/api/v1/skills', {
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          skillId: testSkillId,
          name: 'Test Skill',
          description: 'A test skill for E2E tests',
          promptTemplate: 'This is a test prompt template: {{input}}',
          requiredTools: [],
          isGlobal: true
        })
      });
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
    });

    test('should list skills', async ({ request }) => {
      const response = await request.get('http://localhost:8081/api/v1/skills');
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data.code).toBe(200);
      expect(Array.isArray(data.data)).toBe(true);
    });

    test('should delete skill', async ({ request }) => {
      const response = await request.delete(`http://localhost:8081/api/v1/skills/${testSkillId}`);
      expect(response.status()).toBe(200);
    });
  });
});

test.describe('Frontend', () => {
  test('should load home page', async ({ page }) => {
    await page.goto('http://localhost:3000');
    await expect(page.locator('body')).toBeVisible();
  });

  test('should show chat interface', async ({ page }) => {
    await page.goto('http://localhost:3000');
    // Wait for the page to load
    await page.waitForTimeout(1000);
    // Check if chat input or some UI element is visible
    const body = await page.locator('body').innerHTML();
    expect(body.length).toBeGreaterThan(0);
  });
});
