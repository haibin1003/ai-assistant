#!/usr/bin/env python3
"""
软件运营周报生成器 - Markdown 格式
生成包含 ASCII 图表的 Markdown 格式周报
"""

import json
import sys
import os
import argparse
import re
from datetime import datetime

# 如果设置了 SCRIPT_PARAMS 环境变量，解析并覆盖 sys.argv
_script_params = os.environ.get('SCRIPT_PARAMS', '')
if _script_params:
    try:
        # 使用简单的分割解析 --key value 对
        _parsed_args = [sys.argv[0]]
        # 先按 -- 分割（保留分隔符在每个子串开头）
        parts = _script_params.split('--')
        for part in parts:
            part = part.strip()
            if not part:
                continue
            # 每个 part 格式: "key value..."
            # 用第一个空格分割 key 和 value
            idx = part.find(' ')
            if idx > 0:
                key = part[:idx]
                value = part[idx+1:].strip()
                _parsed_args.append('--' + key)
                _parsed_args.append(value)

        sys.argv = _parsed_args
    except Exception as e:
        pass


def parse_data(json_str, expected_type='list'):
    """通用数据解析函数"""
    try:
        data = json.loads(json_str) if isinstance(json_str, str) else json_str
        if isinstance(data, dict):
            items = data.get('data', data)
        else:
            items = data

        if expected_type == 'list':
            return items if isinstance(items, list) else []
        elif expected_type == 'count':
            return len(items) if isinstance(items, list) else int(json_str) if str(json_str).isdigit() else 0
        elif expected_type == 'dict':
            return items if isinstance(items, dict) else {}
        return items
    except Exception as e:
        return [] if expected_type == 'list' else ({} if expected_type == 'dict' else 0)


def count_by_field(items, *fields):
    """按字段统计数量，支持多个字段名（依次尝试）"""
    counts = {}
    for item in items:
        value = 'UNKNOWN'
        for field in fields:
            if field in item:
                value = item[field]
                break
        counts[value] = counts.get(value, 0) + 1
    return counts


def draw_bar_chart(data_dict, max_width=20):
    """绘制 ASCII 横向柱状图"""
    if not data_dict:
        return "暂无数据"

    total = sum(data_dict.values())
    max_value = max(data_dict.values())

    result = []
    for key, value in sorted(data_dict.items(), key=lambda x: x[1], reverse=True):
        percent = (value / total * 100) if total > 0 else 0
        bar_len = int(value / max_value * max_width) if max_value > 0 else 0
        bar = '█' * bar_len + '░' * (max_width - bar_len)
        result.append(f"{key:<15} {bar} {value:>3} ({percent:>5.1f}%)")
    return '\n'.join(result)


def create_markdown_report(packages, pending_count, subscriptions, systems_count, output_path):
    """生成 Markdown 格式周报"""

    # 计算统计数据
    total_packages = len(packages) if packages else 0
    # OSRM API 返回 status 字段 (可以是 PUBLISHED, DRAFT, PENDING, OFFLINE)
    published_count = sum(1 for p in (packages or [])
                         if str(p.get('status', p.get('publishStatus', ''))).upper() in ['PUBLISHED', '已发布'])
    draft_count = sum(1 for p in (packages or [])
                      if str(p.get('status', p.get('publishStatus', ''))).upper() in ['DRAFT', '草稿'])

    # 按类型统计 (OSRM API 返回 type 字段)
    types_data = count_by_field(packages or [], 'type', 'softwareType')
    type_names = {
        'DOCKER_IMAGE': 'Docker 镜像',
        'HELM_CHART': 'Helm Charts',
        'MAVEN': 'Maven 依赖',
        'NPM': 'NPM 包',
        'PYPI': 'PyPI 包',
        'GENERIC': '通用文件',
    }

    # 按状态统计 (OSRM API 返回 status 字段)
    status_data = count_by_field(packages or [], 'status', 'publishStatus')

    # 订阅统计
    # OSRM API 返回格式: {"total": N, "items": [{"status": "APPROVED"}, ...]}
    # 也支持简化格式: {"approved": N, "pending": N, "rejected": N, "total": N}
    if isinstance(subscriptions, dict):
        if 'items' in subscriptions:
            # OSRM 格式：从 items 中统计各状态数量
            items = subscriptions.get('items', [])
            status_counts = count_by_field(items, 'status')
            approved = status_counts.get('APPROVED', 0)
            pending_sub = status_counts.get('PENDING', 0)
            rejected = status_counts.get('REJECTED', 0)
            total_subs = subscriptions.get('total', len(items))
        else:
            # 简化格式
            approved = subscriptions.get('approved', 0)
            pending_sub = subscriptions.get('pending', 0)
            rejected = subscriptions.get('rejected', 0)
            total_subs = subscriptions.get('total', approved + pending_sub + rejected)
    else:
        approved = pending_sub = rejected = 0
        total_subs = 0

    # 构建 Markdown
    md = f"""# 📊 软件运营周报

**报表周期**：{datetime.now().strftime('%Y-%m-%d')}（本周）
**生成时间**：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

---

## 📈 一、关键指标总览

| 指标 | 数值 |
|------|------|
| 📦 总软件包数 | **{total_packages}** |
| ✅ 已发布 | {published_count} |
| ⏳ 待审批 | {pending_count if pending_count else 0} |
| 📝 草稿 | {draft_count} |
| 🏢 业务系统数 | {systems_count if systems_count else 0} |

---

## 📦 二、软件包类型分布

| 类型 | 数量 | 占比 |
|------|------|------|
"""

    total_types = sum(types_data.values()) or 1
    for sw_type, count in sorted(types_data.items(), key=lambda x: x[1], reverse=True):
        percent = count / total_types * 100
        md += f"| {type_names.get(sw_type, sw_type)} | {count} | {percent:.1f}% |\n"

    if types_data:
        md += f"\n**类型分布图：**\n```\n{draw_bar_chart(types_data)}\n```\n"

    # 计算订阅百分比
    approved_pct = (approved / total_subs * 100) if total_subs > 0 else 0
    pending_pct = (pending_sub / total_subs * 100) if total_subs > 0 else 0
    rejected_pct = (rejected / total_subs * 100) if total_subs > 0 else 0

    md += f"""

---

## 📋 三、订阅申请统计

| 状态 | 数量 | 占比 |
|------|------|------|
| ✅ 已通过 | {approved} | {approved_pct:.1f}% |
| ⏳ 待审批 | {pending_sub} | {pending_pct:.1f}% |
| ❌ 已拒绝 | {rejected} | {rejected_pct:.1f}% |
"""

    if total_subs > 0:
        md += f"""
**申请状态分布：**
```
[{'█' * int(approved_pct/5)}{'░' * (20 - int(approved_pct/5))}] 已通过  {approved_pct:.0f}%
[{'█' * int(pending_pct/5)}{'░' * (20 - int(pending_pct/5))}] 待审批  {pending_pct:.0f}%
[{'█' * int(rejected_pct/5)}{'░' * (20 - int(rejected_pct/5))}] 已拒绝  {rejected_pct:.0f}%
```
"""

    md += """

---

## 📝 四、软件包明细

| 序号 | 名称 | 类型 | 版本 | 状态 |
|------|------|------|------|------|
"""

    pkg_list = (packages or [])[:20]  # 限制显示20条
    for idx, item in enumerate(pkg_list, 1):
        # OSRM API 返回字段: packageName, type, status, currentVersion
        # 使用 or '-' 处理 None 值，避免 subscript error
        name = (item.get('packageName') or item.get('name') or '-')[:20]
        sw_type = (item.get('softwareType') or item.get('type') or 'UNKNOWN')[:10]
        version = (item.get('versionNo') or item.get('currentVersion') or item.get('version') or '-')[:10]
        status = (item.get('publishStatus') or item.get('status') or '-')[:8]
        md += f"| {idx} | {name} | {sw_type} | {version} | {status} |\n"

    if not pkg_list:
        md += "| - | 暂无软件包数据 | - | - | - |\n"

    md += f"""

---

## 💡 五、数据说明

- 本报告数据来源于 **OSRM 系统**
- 数据更新时间为实时数据
- 报告生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

---

**📌 提示**：如需导出完整报告，请联系系统管理员。
"""

    # 写入文件
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(md)

    return True, len(md), md


def main():
    parser = argparse.ArgumentParser(description='生成软件运营周报 (Markdown)')
    parser.add_argument('--packages', '-p', default='[]', help='软件包数据 (JSON)')
    parser.add_argument('--pending', '-pe', default='0', help='待审批数量')
    parser.add_argument('--subscriptions', '-s', default='{}', help='订阅数据 (JSON)')
    parser.add_argument('--systems', '-sy', default='0', help='业务系统数量')
    parser.add_argument('--output', '-o', default='weekly_report.md', help='输出文件路径')

    args = parser.parse_args()

    # 解析数据
    packages = parse_data(args.packages, 'list')
    pending_count = parse_data(args.pending, 'count')
    subscriptions = parse_data(args.subscriptions, 'dict')
    systems_count = parse_data(args.systems, 'count')


    # 确定输出文件
    output_path = args.output
    if not output_path.endswith('.md'):
        output_path += '.md'

    # 生成报告
    success, size, markdown_content = create_markdown_report(packages, pending_count, subscriptions, systems_count, output_path)

    if success:
        # 输出结果 JSON 到 stdout，供 Java 解析
        result_json = json.dumps({
            "filePath": output_path,
            "fileSize": size,
            "status": "success"
        }, ensure_ascii=False)
        print(result_json)
        return 0
    else:
        return 1


if __name__ == "__main__":
    sys.exit(main())
