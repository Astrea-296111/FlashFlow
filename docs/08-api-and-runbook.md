# API 与运行手册

`./mvnw -B spotless:check verify` 使用 Java 21，默认 Testcontainers 启动独立 MySQL 与 Redis。没有 Docker 的本次环境使用真实本地服务，显式 `FLASHFLOW_IT_EXTERNAL=true`，只连接 flashflow_it 和 Redis DB 1。不要把 external 模式指向已有业务数据：它会清理这些专用测试表。

## Docker Compose

```bash
cp .env.example .env
docker compose up --build -d
docker compose logs -f api worker
curl --fail http://localhost:8080/actuator/health
docker compose --profile monitoring up -d
```

首次可通过 `openssl rand -base64 48` 生成 JWT_SECRET 并写入 .env；默认文件中的密码仅供本地演示。Demo 模式创建管理员 admin，其密码来自 DEMO_ADMIN_PASSWORD。公开注册固定 USER，提交 role 字段也不能升级。

API 8080、worker Actuator 8081，数据库与 Redis 暴露端口仅绑定 loopback。Broker 的 advertised IP 是 Compose 内部 broker；faults.py 会在 worker 容器中运行镜像内的编译探针，DB HTTP/JWT fixture 在宿主机使用映射端口。native 模式使用本机 Broker 与显式参数。探针通过真实 JAR 的 PropertiesLauncher 加载已单独验证；Compose 配置解析通过，完整 Compose 及镜像构建**尚未在本环境执行**，不能据此声称 Docker 冒烟已经通过。

Swagger：[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)，OpenAPI：`/v3/api-docs`。管理 API 需要 ADMIN token，交易 API 需要 USER 或 ADMIN token。令牌 HS256、issuer=flashflow，TTL 2 小时。JWT_SECRET 少于 32 bytes 会拒绝启动。

| 方法与路径 | 权限 | 行为 |
|---|---|---|
| POST /api/v1/auth/register | 公开 | username、password，注册 USER |
| POST /api/v1/auth/login | 公开 | 返回 token |
| GET /api/v1/events、/{id} | 公开 | 活动列表、元数据详情 |
| POST /api/v1/admin/events | ADMIN | 创建售卖窗口 |
| PUT/DELETE /api/v1/admin/events/{id} | ADMIN | 修改/归档，冻结后拒绝 |
| POST /api/v1/admin/events/{id}/skus | ADMIN | tierName、price、stock |
| POST /api/v1/admin/skus/{id}/warm | ADMIN | 初次预热为 ASYNC |
| POST /api/v1/seckill/baseline/{skuId} | 登录 | 同步持久化下单 |
| POST /api/v1/seckill/{skuId} | 登录 | HTTP 202 资格预占 |
| GET /api/v1/seckill/result/{reservationId} | 所有者 | QUEUED/SUCCESS/FAILED |
| GET /api/v1/orders、/{orderNo} | 所有者 | 订单查询 |
| POST /api/v1/orders/{orderNo}/pay | 所有者 | 模拟支付；paymentKey 8..64 合法字符 |
| POST /api/v1/orders/{orderNo}/refund | 所有者 | 申请退款 |
| POST /api/v1/admin/orders/{orderNo}/issue | ADMIN | 模拟出票 |
| POST /api/v1/admin/orders/{orderNo}/refund-complete | ADMIN | 模拟退款完成并释放库存 |

## 最短业务验证

运行 `python benchmark/scripts/smoke.py`，设置 JWT_SECRET 与启动配置相同，且先 `python -m pip install -r benchmark/requirements.txt`。脚本创建隔离测试活动、预热、走真实 Broker 下单，等待结果，重复模拟支付，再审计库存。它直接写本地压测用户并用测试 JWT 登录，普通产品 HTTP 没有这些 fixture 入口。

手工登录：

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"local-admin-only"}'
```

用返回 token 作为 Authorization: Bearer header。创建活动的时间用 ISO-8601 UTC，随后创建 SKU。baseline 与 optimized 用两个 SKU；optimized 必须先 warm。拿到 QUEUED 后轮询 result 得到 SUCCESS 的 orderNo，才是订单已完成落库。SUCCESS 包括后来 CLOSED 的历史订单，需要再查询订单当前状态。

202 的 delivery=ACKNOWLEDGED 仅表示 Broker 的 SEND_OK；UNCERTAIN 表示发送结果不确定，不能立即重新购买或直接回补。409 包括 SOLD_OUT/NOT_ON_SALE/状态冲突；429 为限流；503 为未初始化/Redis 状态异常。订单和预占归属错误统一返回 404，避免泄露其他人的交易。

## 排查顺序

先检查 health、API/worker 角色和订阅组；再看订单/reservation/outbox 状态与 Broker consumerProgress；最后检查 Redis deadline 和库存守恒。禁止通过重新 warm 活跃 SKU“恢复”库存。TTL 到期后的坏消息可能进入 DLQ，需人工定位并验证业务身份后重放。

清理演示环境用 `docker compose down`；`down -v` 会删除演示数据库和消息，只有明确要重建全部演示数据时才使用。native 服务安装不是交付包的一部分，采用正式 Docker 或自己安装的同版本中间件复现。

测试构建显式加载 Mockito Java agent，并保留 JaCoCo agent；在受限运行环境中避免 Mockito 动态 self-attach 失败。
