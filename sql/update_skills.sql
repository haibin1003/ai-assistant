-- ============================================
-- Skills 更新脚本
-- 数据库: ai_assistant
-- ============================================

-- 更新开源软件审查助手 (skill-4d9dac1a)
UPDATE t_skill_package
SET name = 'Open Source Reviewer',
    description = '专业的开源软件安全与合规审查助手。当用户请求审查软件包、或询问软件安全性和许可证合规性问题时自动激活。'
WHERE skill_id = 'skill-4d9dac1a';

-- 更新许可证合规检查器 (skill-b69dbfb1)
UPDATE t_skill_package
SET name = 'License Compliance Checker',
    description = '许可证合规验证助手。当用户询问许可证兼容性、商业使用权限、或检查某个软件是否可以用于商业项目时自动激活。'
WHERE skill_id = 'skill-b69dbfb1';

-- 更新软件运营周报生成器 (skill-ea1d5f36)
UPDATE t_skill_package
SET name = 'Software Operations Weekly Report',
    description = '软件运营周报生成助手。当用户请求生成周报、运营报告、或查看本周软件运营情况时自动激活。自动汇总软件包更新、订阅申请、系统使用统计等数据。'
WHERE skill_id = 'skill-ea1d5f36';

-- 插入演示用 Skill：软件依赖分析器
INSERT INTO t_skill_package (skill_id, name, description, storage_path, version, is_active, created_by, created_at, updated_at)
VALUES (
    'skill-demo1234',
    'Software Dependency Analyzer',
    '软件依赖关系分析与可视化助手。当用户需要分析软件包的依赖关系、查看依赖图谱、或了解软件技术栈时自动激活。提供依赖树展示、循环依赖检测、版本兼容性分析等功能。',
    'skills/skill-demo1234',
    '1.0.0',
    TRUE,
    'admin',
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    storage_path = VALUES(storage_path),
    updated_at = NOW();
