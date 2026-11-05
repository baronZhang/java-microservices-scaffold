# Java 微服务脚手架 demo

[English](README.md)

网关 + 认证 + 用户 + 商品 + 库存 + 订单六个服务，Spring Boot 4.1.1 / Spring Cloud 2025.1.3 / JDK 25。
**默认不依赖任何中间件**就能跑通全部核心链路；Nacos、MySQL、Redis、Kafka 作为可叠加的 profile。

这是一个用来验证设计的练习项目，没有上过生产。项目用 Claude Code 开发，测试结果都来自真实运行。

## 快速开始

需要 JDK 25 或更高版本。

```bash
./mvnw verify            # 格式、静态检查、架构规则、单元 + 集成测试
scripts/start-all.sh     # 启动全部服务（默认 profile = demo）
scripts/smoke-test.sh    # 经网关跑一遍核心接口（37 项断言）
scripts/stop-all.sh
```

叠加中间件：`MS_PROFILES=demo,mysql,redis scripts/start-all.sh`（先 `docker compose -f deploy/docker-compose.infra.yml up -d mysql`）。

整套跑在容器里：构建镜像后 `docker compose -f deploy/docker-compose.yml up -d`，命令和换基础镜像源的参数见 compose 文件头。

## 端口

| 服务 | 端口 |
|---|---|
| gateway | 9080 |
| auth-service | 9081 |
| user-service | 9082 |
| product-service | 9083（灰度实例 9093） |
| inventory-service | 9084（第二实例 9094） |
| order-service | 9085（第二实例 9095） |

## 实验

`scripts/experiments/` 下九个可以直接运行的实验：负载均衡与宕机、故障注入（变慢 / 停掉）、可靠消息与重复消费、灰度与配置刷新、限流、分布式锁与雪花 ID、端到端防超卖、链路追踪、Nacos 推送刷新。

## 目录

```text
common/             公共模块：契约（common-core）、Web / 安全 starter、测试支撑
gateway/            API 网关
*-service/          五个业务服务（各自独立的库、Flyway 迁移）
config/             本地版配置中心（ms-common.yml、gateway.yml）
contracts/          服务间、前后端的契约样例
deploy/             docker-compose、MySQL 初始化、Kubernetes 清单
scripts/            启停、冒烟测试、实验、依赖检查
```

## 说明

- 仓库里的密码和令牌都是本地演示用的值。
- 接口和 [Vue 3 前端 demo](https://github.com/baronZhang/vue3-scaffold-demo) 一致，前端在单独的仓库里。
- 个别注释提到 `docs/`，那是我的私人学习笔记，不在这个仓库里。
