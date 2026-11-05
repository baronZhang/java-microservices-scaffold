# 契约样例

服务之间、前后端之间的"接口长什么样"，以 JSON 样例的形式放在这里，**提供方和消费方的测试都读同一份文件**：

| 目录 | 消费方 | 提供方 | 谁的测试在用 |
|---|---|---|---|
| `order-to-inventory/` | order-service（预占库存、投递订单事件） | inventory-service | 订单服务的 `InventoryContractIT`（检查自己发出的请求、能否解析响应）；库存服务的 `ReservationContractIT`（用样例请求打自己的接口，比对响应） |
| `frontend/` | Vue 前端（`src/types/*.ts`、`mock/`） | 网关 + user-service | 用户服务的 `UserApiIT`（比对响应字段） |

这是"消费者驱动契约测试"的轻量做法：没有引入 Pact / Spring Cloud Contract，但思想一样——
契约由消费方提出、双方各自用测试守住，任何一方改了字段，自己这边的测试先失败，而不是等联调或上线才发现。
