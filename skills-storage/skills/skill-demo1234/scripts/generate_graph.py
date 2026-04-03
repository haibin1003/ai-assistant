#!/usr/bin/env python3
"""
依赖关系图生成器
将软件依赖关系转换为可视化的图形表示
"""

import json
import sys
from typing import Dict, List, Optional


class DependencyGraphGenerator:
    """依赖图生成器"""

    def __init__(self):
        self.nodes: Dict[str, Dict] = {}
        self.edges: List[tuple] = []
        self.max_depth = 10

    def add_package(self, name: str, version: str = "unknown", metadata: Optional[Dict] = None):
        """添加包节点"""
        self.nodes[name] = {
            "name": name,
            "version": version,
            "metadata": metadata or {}
        }

    def add_dependency(self, from_pkg: str, to_pkg: str, dep_type: str = "runtime"):
        """添加依赖边"""
        if from_pkg in self.nodes and to_pkg in self.nodes:
            self.edges.append((from_pkg, to_pkg, dep_type))

    def generate_text_graph(self, root_package: str) -> str:
        """生成文本形式的依赖图"""
        lines = []
        visited = set()

        def render_node(pkg: str, prefix: str, is_last: bool, depth: int):
            if depth > self.max_depth or pkg in visited:
                return

            visited.add(pkg)

            # 绘制节点
            connector = "└── " if is_last else "├── "
            version = self.nodes.get(pkg, {}).get("version", "?")
            lines.append(f"{prefix}{connector}{pkg} ({version})")

            # 获取子依赖
            children = [(to_pkg, dep_type) for from_pkg, to_pkg, dep_type in self.edges if from_pkg == pkg]

            for i, (child, dep_type) in enumerate(children):
                is_child_last = (i == len(children) - 1)
                new_prefix = prefix + ("    " if is_last else "│   ")
                render_node(child, new_prefix, is_child_last, depth + 1)

        # 渲染根节点
        root_version = self.nodes.get(root_package, {}).get("version", "?")
        lines.append(f"{root_package} ({root_version})")
        visited.add(root_package)

        # 渲染子节点
        children = [(to_pkg, dep_type) for from_pkg, to_pkg, dep_type in self.edges if from_pkg == root_package]
        for i, (child, dep_type) in enumerate(children):
            is_last = (i == len(children) - 1)
            connector = "└── " if is_last else "├── "
            dep_label = f"[{dep_type}]" if dep_type != "runtime" else ""
            lines.append(f"{connector}{child} {dep_label}")
            visited.add(child)

            # 渲染更深层节点
            sub_children = [(to_pkg, dep_type) for from_pkg, to_pkg, dep_type in self.edges if from_pkg == child]
            for j, (sub_child, sub_dep_type) in enumerate(sub_children):
                is_child_last = (j == len(sub_children) - 1)
                sub_prefix = "    " if is_last else "│   "
                sub_connector = "└── " if is_child_last else "├── "
                sub_dep_label = f"[{sub_dep_type}]" if sub_dep_type != "runtime" else ""
                lines.append(f"{sub_prefix}{sub_connector}{sub_child} {sub_dep_label}")

        return "\n".join(lines)

    def generate_mermaid(self, root_package: str) -> str:
        """生成 Mermaid 格式的依赖图"""
        lines = ["```mermaid", "graph TD"]

        for pkg in self.nodes:
            label = f"{pkg}<br/>v{self.nodes[pkg].get('version', '?')}"
            lines.append(f'    {pkg.replace("-", "_").replace(".", "_")}["{label}"]')

        for from_pkg, to_pkg, dep_type in self.edges:
            from_id = from_pkg.replace("-", "_").replace(".", "_")
            to_id = to_pkg.replace("-", "_").replace(".", "_")
            lines.append(f"    {from_id} --> {to_id}")

        lines.append("```")
        return "\n".join(lines)

    def generate_dot(self, root_package: str) -> str:
        """生成 Graphviz DOT 格式的依赖图"""
        lines = [
            "digraph dependencies {",
            '    rankdir=LR;',
            '    node [shape=box, fontname="Helvetica"];',
            '    edge [fontname="Helvetica"];',
            ""
        ]

        for pkg, info in self.nodes.items():
            node_id = pkg.replace("-", "_").replace(".", "_")
            label = f"{pkg}\\nv{info.get('version', '?')}"
            lines.append(f'    {node_id} [label="{label}"];')

        for from_pkg, to_pkg, dep_type in self.edges:
            from_id = from_pkg.replace("-", "_").replace(".", "_")
            to_id = to_pkg.replace("-", "_").replace(".", "_")
            lines.append(f'    {from_id} -> {to_id} [label="{dep_type}"];')

        lines.append("}")
        return "\n".join(lines)

    def export_json(self) -> str:
        """导出为 JSON 格式"""
        data = {
            "nodes": self.nodes,
            "edges": [{"from": f, "to": t, "type": d} for f, t, d in self.edges]
        }
        return json.dumps(data, indent=2, ensure_ascii=False)


def main():
    """主函数 - 示例用法"""
    generator = DependencyGraphGenerator()

    # 添加包节点
    generator.add_package("my-app", "1.0.0", {"type": "application"})
    generator.add_package("express", "4.18.0", {"type": "framework"})
    generator.add_package("lodash", "4.17.21", {"type": "utility"})
    generator.add_package("axios", "1.4.0", {"type": "http-client"})
    generator.add_package("react", "18.2.0", {"type": "framework"})
    generator.add_package("qs", "6.11.0", {"type": "library"})

    # 添加依赖关系
    generator.add_dependency("my-app", "express", "runtime")
    generator.add_dependency("my-app", "lodash", "runtime")
    generator.add_dependency("my-app", "axios", "runtime")
    generator.add_dependency("my-app", "react", "runtime")
    generator.add_dependency("express", "qs", "runtime")

    print("=" * 60)
    print("文本依赖图:")
    print("=" * 60)
    print(generator.generate_text_graph("my-app"))
    print()

    print("=" * 60)
    print("Mermaid 格式:")
    print("=" * 60)
    print(generator.generate_mermaid("my-app"))
    print()

    print("=" * 60)
    print("Graphviz DOT 格式:")
    print("=" * 60)
    print(generator.generate_dot("my-app"))


if __name__ == "__main__":
    main()
