#!/usr/bin/env python3
"""
软件依赖深度分析脚本
用于分析软件包的依赖关系并生成详细报告
"""

import json
import sys
from typing import Dict, List, Set, Optional


class DependencyAnalyzer:
    def __init__(self, package_name: str, version: str):
        self.package_name = package_name
        self.version = version
        self.dependencies: Dict[str, List[str]] = {}
        self.visited: Set[str] = set()
        self.cycles: List[List[str]] = []

    def add_dependency(self, pkg: str, deps: List[str]):
        """添加依赖关系"""
        self.dependencies[pkg] = deps

    def analyze(self) -> Dict:
        """执行依赖分析"""
        result = {
            "package": self.package_name,
            "version": self.version,
            "direct_deps": self.dependencies.get(self.package_name, []),
            "all_deps": self._get_all_dependencies(self.package_name),
            "dependency_tree": self._build_tree(self.package_name, 0),
            "cycles": self.cycles,
            "stats": self._calculate_stats()
        }
        return result

    def _get_all_dependencies(self, pkg: str, visited: Optional[Set] = None) -> List[str]:
        """递归获取所有依赖"""
        if visited is None:
            visited = set()

        if pkg in visited:
            return []

        visited.add(pkg)
        deps = []

        for dep in self.dependencies.get(pkg, []):
            deps.append(dep)
            deps.extend(self._get_all_dependencies(dep, visited))

        return list(set(deps))

    def _build_tree(self, pkg: str, depth: int, visited: Optional[Set] = None) -> Dict:
        """构建依赖树"""
        if visited is None:
            visited = set()

        if depth > 5 or pkg in visited:
            return {"name": pkg, "truncated": True}

        visited.add(pkg)

        deps = self.dependencies.get(pkg, [])
        children = []

        for dep in deps[:10]:  # 限制每个节点最多显示10个子依赖
            children.append(self._build_tree(dep, depth + 1, visited.copy()))

        return {
            "name": pkg,
            "children": children,
            "depth": depth
        }

    def _calculate_stats(self) -> Dict:
        """计算依赖统计信息"""
        all_deps = self._get_all_dependencies(self.package_name)
        return {
            "total_dependencies": len(all_deps),
            "direct_dependencies": len(self.dependencies.get(self.package_name, [])),
            "max_depth": self._calculate_max_depth(),
            "unique_packages": len(set(all_deps))
        }

    def _calculate_max_depth(self, pkg: str = None, visited: Set = None) -> int:
        """计算最大依赖深度"""
        if pkg is None:
            pkg = self.package_name
        if visited is None:
            visited = set()

        if pkg in visited or pkg not in self.dependencies:
            return 0

        visited.add(pkg)

        max_depth = 0
        for dep in self.dependencies.get(pkg, []):
            depth = self._calculate_max_depth(dep, visited.copy())
            max_depth = max(max_depth, depth)

        return max_depth + 1


def main():
    """主函数"""
    # 示例数据
    analyzer = DependencyAnalyzer("example-package", "1.0.0")

    # 添加依赖关系
    analyzer.add_dependency("example-package", ["dep-a", "dep-b", "dep-c"])
    analyzer.add_dependency("dep-a", ["dep-d", "dep-e"])
    analyzer.add_dependency("dep-b", ["dep-d"])
    analyzer.add_dependency("dep-d", ["dep-f"])

    # 执行分析
    result = analyzer.analyze()

    # 输出结果
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
