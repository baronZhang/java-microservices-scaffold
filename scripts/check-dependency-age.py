#!/usr/bin/env python3
"""检查 pom.xml 里显式写死的版本是否"发布满 N 天"（默认 14 天）。

为什么需要它：Maven 没有 npm 的 `--before=<日期>`，只能靠"版本写死 + 定期检查"。
被 BOM 管理的几百个传递依赖不用逐个查：BOM（Spring Boot / Spring Cloud）本身满两周，它里面的版本只会更早。
所以只查我们自己写死的版本：父 POM、BOM、BOM 之外的库和插件、Maven 本身。

用法：python3 -I scripts/check-dependency-age.py [天数]
"""
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIN_DAYS = int(sys.argv[1]) if len(sys.argv) > 1 else 14
CENTRAL = "https://repo1.maven.org/maven2"

pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
props = dict(re.findall(r"<([a-zA-Z0-9.-]+\.version)>([^<]+)</", pom))
# 父 POM 的 artifactId 和 version 之间有一行注释，所以用非贪婪匹配跨过去
parent_version = re.search(
    r"<artifactId>spring-boot-starter-parent</artifactId>.*?<version>([^<]+)</version>", pom, re.S
).group(1)
wrapper = (ROOT / ".mvn/wrapper/maven-wrapper.properties").read_text()

# (显示名, groupId, artifactId, 版本)
checks = [
    ("Spring Boot（父 POM）", "org.springframework.boot", "spring-boot-starter-parent", parent_version),
    ("Spring Cloud BOM", "org.springframework.cloud", "spring-cloud-dependencies", props["spring-cloud.version"]),
    ("Spring Cloud Alibaba BOM", "com.alibaba.cloud", "spring-cloud-alibaba-dependencies", props["spring-cloud-alibaba.version"]),
    ("ArchUnit", "com.tngtech.archunit", "archunit-junit5", props["archunit.version"]),
    ("Spotless 插件", "com.diffplug.spotless", "spotless-maven-plugin", props["spotless-maven-plugin.version"]),
    ("palantir-java-format", "com.palantir.javaformat", "palantir-java-format", props["palantir-java-format.version"]),
    ("SpotBugs 插件", "com.github.spotbugs", "spotbugs-maven-plugin", props["spotbugs-maven-plugin.version"]),
    ("JaCoCo 插件", "org.jacoco", "jacoco-maven-plugin", props["jacoco-maven-plugin.version"]),
    ("Maven", "org.apache.maven", "apache-maven", re.search(r"apache-maven-([0-9.]+)-bin", wrapper).group(1)),
    ("Maven Wrapper", "org.apache.maven.wrapper", "maven-wrapper", re.search(r"wrapperVersion=([0-9.]+)", wrapper).group(1)),
]


def published(group, artifact, version, attempts=3):
    """取 POM 文件的 Last-Modified 作为发布时间；网络偶尔抖动，失败重试几次"""
    url = f"{CENTRAL}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=20) as response:
                return parsedate_to_datetime(response.headers["Last-Modified"])
        except OSError:
            if attempt == attempts:
                raise
            time.sleep(attempt)


now = datetime.now(timezone.utc)
failed = 0
for name, group, artifact, version in checks:
    try:
        days = (now - published(group, artifact, version)).days
    except Exception as e:  # noqa: BLE001 - 网络问题如实报告
        print(f"  ?  {name} {version}：查询失败（{e}）")
        failed += 1
        continue
    ok = days >= MIN_DAYS
    failed += 0 if ok else 1
    print(f"  {'✓' if ok else '✗'}  {name} {version}：发布 {days} 天")
print(f"\n要求发布满 {MIN_DAYS} 天：{'全部满足' if failed == 0 else f'{failed} 项不满足'}")
sys.exit(1 if failed else 0)
