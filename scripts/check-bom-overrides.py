#!/usr/bin/env python3
"""
检查 import 的 BOM 有没有静默改掉 Spring Boot 管理的版本。

背景（Maven 3.10.0 实测）：dependencyManagement 里直接写版本的条目优先；import 的 BOM 之间先到先得，
本 POM 的 import 排在从父 POM 继承来的 import 前面。Boot 用 import 管理的家族（Jackson、Netty、Log4j……）
因此会被 Spring Cloud / Spring Cloud Alibaba 的 BOM 覆盖，而且构建不会有任何提示。

做法：用和根 POM 相同的 parent 生成一个"只有 Boot"的 POM，两边各取一次 effective-pom，
对比 dependencyManagement 里同一坐标的版本。有差异就列出来并以非零状态退出。

用法（项目根目录）：python3 -I scripts/check-bom-overrides.py
"""
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MVNW = os.path.join(ROOT, "mvnw")


def effective_pom(pom: str, out: str, non_recursive: bool) -> None:
    cmd = [MVNW, "-B", "-q", "-f", pom, "help:effective-pom", f"-Doutput={out}"]
    if non_recursive:
        cmd.insert(2, "-N")
    subprocess.run(cmd, check=True, cwd=ROOT, stdout=subprocess.DEVNULL)


def managed_versions(path: str) -> dict:
    root = ET.parse(path).getroot()
    result = {}
    for d in root.findall("./m:dependencyManagement/m:dependencies/m:dependency", NS):
        key = tuple(
            d.findtext(f"m:{name}", default=default, namespaces=NS)
            for name, default in (("groupId", ""), ("artifactId", ""), ("classifier", ""), ("type", "jar"))
        )
        result[key] = d.findtext("m:version", namespaces=NS)
    return result


def main() -> int:
    root_pom = open(os.path.join(ROOT, "pom.xml"), encoding="utf-8").read()
    parent = re.search(r"<parent>.*?</parent>", root_pom, re.S).group(0)
    with tempfile.TemporaryDirectory() as tmp:
        boot_only = os.path.join(tmp, "pom.xml")
        with open(boot_only, "w", encoding="utf-8") as f:
            f.write(
                '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
                f"{parent}<groupId>check</groupId><artifactId>boot-only</artifactId><version>1</version>"
                "<packaging>pom</packaging></project>"
            )
        effective_pom(boot_only, os.path.join(tmp, "boot.xml"), non_recursive=False)
        effective_pom(os.path.join(ROOT, "pom.xml"), os.path.join(tmp, "project.xml"), non_recursive=True)
        boot = managed_versions(os.path.join(tmp, "boot.xml"))
        project = managed_versions(os.path.join(tmp, "project.xml"))

    overridden = sorted((k, boot[k], project[k]) for k in boot if k in project and boot[k] != project[k])
    print(f"Boot 管理 {len(boot)} 个坐标，本项目有效管理 {len(project)} 个，被 import 的 BOM 改掉版本的 {len(overridden)} 个")
    for (group, artifact, classifier, _), boot_version, project_version in overridden:
        suffix = f":{classifier}" if classifier else ""
        print(f"  ✗ {group}:{artifact}{suffix}  Boot={boot_version}  本项目={project_version}")
    if overridden:
        print("处理办法：在根 POM 里把对应家族的 BOM（用 Boot 的版本属性）import 到前面，或确认这个覆盖是有意的")
    return 1 if overridden else 0


if __name__ == "__main__":
    sys.exit(main())
