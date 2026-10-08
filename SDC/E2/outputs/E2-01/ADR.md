# E2-01 订单状态流转

## 上下文

订单状态迁移规则散在两个可运行应用里，执行方式也不一样。基线 `dcaa93b3150352e5044708d7211b5bed0af4509f`：

支付有两个入口。

- `POST /order/paySuccess`：`OmsPortalOrderController.java` 49–53 行直接调用 `paySuccess`。服务实现 253–264 行按主键把状态写成 1，不看当前状态，然后 `updateSkuStock`。重复调用会再扣库存。
- 支付宝回调 `AlipayServiceImpl.java` 97 行，以及查询 132 行，都走 `paySuccessByOrderSn`（基线 423–427 行，当前 428–432 行）。它先经 `getOrderByOrderSn`，基线 435 行只查出 status=0 的订单（当前这句在 440 行）。这是先读后写：顺序上的重复调用会被挡住，并发重复没有验证。

其余三条：

- 确认收货 2→3：`OmsPortalOrderController.java` 101–105 行进入 `confirmReceiveOrder`。基线 337–349 行先读订单，再判断 `status != 2`，然后整行写回。
- 发货 1→2：后台 `OmsOrderController.java` 38–41 行。`OmsOrderDao.xml` 64 行在 SQL 里写死 `status = 1`。
- 后台关闭：`OmsOrderController.java` 49–52 行。`OmsOrderServiceImpl.java` 61–66 行只要求未删除，65 行的条件里没有出发状态。
- 状态码 0–5 只出现在 `OmsOrder.java` 52–53 行的 Schema 注释里。库表同文在 `document/sql/mall.sql` 460 行。

重复走 `/order/paySuccess` 会多扣库存，已付款订单也能被后台关掉。这是错误行为，所以 `EXPECT_BASE=fail`。

## 决定

采用方案 a：新建模块 `mall-order-rule`，源码只依赖 JDK（继承根 pom 的依赖仍在类路径上）。里面只有 `OrderStatus`：五个状态常量和 `from(target)`。支付←{0}，发货←{1}，确认收货←{2}，后台关闭←{0}。没有枚举体系，也没有状态机框架。

四个写入点改为条件更新，`status in (出发状态)`，用命中行数决定后续：

- `paySuccess`（254–267 行）命中 1 行才扣库存，否则返回 0。
- `confirmReceiveOrder`（340–354 行）仍先核对订单归属；状态判断改成条件更新，命中 0 行时仍抛「该订单还未发货！」。
- `close`（62–66 行）在原有未删除条件上加上出发状态，返回值是实际关闭行数。
- 发货把 XML 里的 `status = 1` 改成调用方传入的出发集合（`OmsOrderDao.java` 多一个参数，XML 64–67 行 `<foreach>`）。目标状态 2 仍写在 CASE 的 `THEN 2`（54–57 行）。

## 被否决的方案

方案 b：把常量放进 `mall-common`。改动文件更少，但 `mall-common` 已经堆着 Web、Redis、校验和文档注解（`mall-common/pom.xml` 21–53 行）。订单出发状态会再加重这个杂物箱，portal 和 admin 以外的模块也会看见它。

方案 c：portal 和 admin 各写一份集合，再用契约测试锁住两边相同。不增加模块，但规则仍有两处，改一处时测试才能发现另一处没改。

## 信息隐藏

「允许的出发状态」是会变的设计决定。改前它散在 3 个文件里，而且有的路径根本没写：

- `OmsPortalOrderServiceImpl.java`：确认收货在基线 343 行判断 `status != 2`；支付宝查单在基线 435 行过滤 `status = 0`。`paySuccess` 本身（253–264 行）没有出发状态。
- `OmsOrderDao.xml` 64 行：发货写死 `status = 1`。
- `OmsOrderServiceImpl.java` 65 行：关闭的条件里没有出发状态。

改后这组决定集中在 `OrderStatus.java` 19–26 行。支付宝查单的 `status = 0` 仍留在 `getOrderByOrderSn`（当前 440 行），不在这处规则里。

| 方案 | 可测试性 | 可替换性 | 代价 |
|---|---|---|---|
| a 新模块 | 直接调用 `from`，不启动 Spring | 改这一处，四个写入点跟着变 | 多一个模块，两条依赖 |
| b 放进 mall-common | 也能单测，但和公共库搅在一起 | 改一处，可是所有模块都看得见 | 公共库更杂 |
| c 两边各写一份加契约测试 | 不一致要等测试才发现 | 仍要改两处 | 不增加模块 |

变异②把 `from(CLOSED)` 从只含 0 改成同时含 1，只动 `OrderStatus.java` 这一行。后台关闭已付款订单的测试随即变红。见 `evidence/mutations.txt`。

## 后果

根 `pom.xml` 增加模块（20 行）和依赖管理（112–116 行）。`mall-order-rule/pom.xml` 自己不声明 Spring。根 pom 62–75 行的 `dependencies` 仍会继承到每个子模块，其中包括 `spring-boot-starter-test`（test）、actuator 和 aop。`OrderStatus.java` 不引用这些类型。mall-admin、mall-portal 各一条依赖（24–27 行，25–28 行）。

两处可以看见的行为差别：

- 再调 `POST /order/paySuccess`（控制器 49–53 行）仍返回文案「支付成功」，data 是服务的返回值。现在重复调用得到 0，不再扣库存。
- 订单不存在时，原来 `getDetail` 之后空指针；现在条件更新命中 0 行，`paySuccess` 返回 0，控制器同样给出「支付成功」和 data 0。

用户取消不调用 `OrderStatus`。`cancelTimeOutOrder` 仍在 271–296 行，`cancelOrder` 仍在 300–328 行。

## 明确不处理

- 用户取消和超时取消（0→4）及其补偿。那是 E2-00。
- `getOrderByOrderSn` 440 行（基线 435 行）仍把 `status = 0` 写在读路径上，不是条件更新。
- 后台关闭待付款订单不释放锁定库存、优惠券和积分。`close` 只改状态并写操作记录。
- 关闭和发货仍给每个传入 id 写操作记录，即使条件更新没有命中。发货见 47–57 行，关闭见 68–77 行。
- 后台仍可删除待付款订单。`delete`（82–87 行）只判断未删除，不看状态。
