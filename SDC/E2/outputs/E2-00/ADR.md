# E2-00 取消订单：多个入口共用一个取消 module

## 上下文

`mall-portal` 取消订单有四个入口、两份补偿。行号在基线 `dcaa93b3150352e5044708d7211b5bed0af4509f`（标签 `baseline-dcaa93b3`）上核对，并与 `origin/exp/E2` 的这些文件做了空 diff。

- `POST /order/cancelUserOrder`：`OmsPortalOrderController.java:92-98` 调用 `portalOrderService.cancelOrder(orderId)`。
- 延迟消息到期：`CancelOrderSender.java:25` 发到 TTL 队列；`RabbitMqConfig.java:51-53` 用 `x-dead-letter-exchange` / `x-dead-letter-routing-key` 到期后转到 `mall.order.cancel`（`QueueEnum.java:14-18`）；`CancelOrderReceiver.java:21-25` 再调用 `cancelOrder(orderId)`。
- `POST /order/cancelTimeOutOrder`：`OmsPortalOrderController.java:56-62` 调用 `cancelTimeOutOrder()`。`OrderTimeOutCancelTask.java:14-28` 也会调用它，但第 14 行 `@Component` 被注释，当前不会注册成 Bean。
- `POST /order/cancelOrder`：`OmsPortalOrderController.java:64-70` 实际只调用 `sendDelayMessageCancelOrder(orderId)`，并不取消订单。

补偿 A 在 `OmsPortalOrderServiceImpl.java:297-325`：先 `selectByExample`（`id`、`status=0`、`delete_status=0`），再 `updateByPrimaryKeySelective` 把 status 写成 4，然后释放锁定库存、退券、返还积分。查和改不是同一次条件更新，支付回调若在中间把 status 写成 1，取消仍可能按主键覆盖并重复补偿。

补偿 B 在 `OmsPortalOrderServiceImpl.java:267-294`：`getTimeOutOrders`（`PortalOrderDao.xml:29-48`，只要求 `status=0` 且创建时间早于超时分钟）之后 `updateOrderStatus` 把这些 id 全部写成 4，再逐单释放库存、退券、返积分。规则和补偿 A 各写一份。

退券在 `OmsPortalOrderServiceImpl.java:503-516`：`couponId` 为空则返回；否则按会员、券、相反的 `use_status` 取 `selectByExample` 的第一条，不按订单 id 关联。`paySuccess`（`:253-265`）只按主键把 status 写成 1，不检查前置状态。这两个方法的 `@Transactional` 在接口 `OmsPortalOrderService.java:32-45`。

## 决定

采用方案 B。新增 `OrderCancellation`（`@Component`，构造函数注入 Mapper、Dao 和会员服务），不另写 Java interface。

- `cancel(Long)` 调用私有 `cancelIf(id, true)`：条件是 `id`、`status=0` 和 `delete_status=0`（`OrderCancellation.java:54-56`、`:77-85`）。命中行数不是 1 就返回 false，不做补偿。命中 1 行才释放锁定库存、退券、返还积分。订单项列表为空时不调用 `releaseSkuStockLock`（`:93-95`）。退券方法从基线 `updateCouponStatus` 原样搬入（`:111-125`），仍取第一条相反使用状态的记录。
- `cancelOverdue()`：读 `oms_order_setting` id=1 的超时分钟，用原来的 `getTimeOutOrders`，对每笔调用 `cancelIf(id, false)`，条件只有 `id` 和 `status=0`，返回实际取消数（`:62-75`）。这样后台删除过的待付款超时单仍会被关掉并补偿。
- `OmsPortalOrderServiceImpl.cancelTimeOutOrder` 与 `cancelOrder` 各剩一行委托（当前文件 `:270-277`）。接口上的 `@Transactional` 没改。控制器、`CancelOrderReceiver`、`OrderTimeOutCancelTask` 没改。须经 `OmsPortalOrderService` 调用，事务在接口上。

下单时把券标成已使用的那份 `updateCouponStatus` 仍留在订单服务里（当前 `:455-468`），那不是取消补偿。

## 被否决的方案 A

在 `OmsPortalOrderServiceImpl` 里抽一个私有补偿方法，两处调用它。类内部文字不再重复，但两个入口仍各自决定“能不能取消”：单笔先查后改，批量先无条件改 status。幂等（这次调用有没有赢得 status=0 的更新）表达不出来。测试仍然要绕过整份订单服务，不能单独换掉取消规则。

## 比较

| | 可测试性 | 可替换性 | 改动成本 |
|---|---|---|---|
| 方案 A | 补偿仍是私有方法，特征测试只能走原服务 | 规则换不掉，除非再改这个服务类 | 少一个类，但新增一种补偿仍要找到两处调用是否都走到私有方法 |
| 方案 B | `SDCE200CancellationTest` 直接构造 `OrderCancellation`，用协作者的可观察调用断言；不断言私有方法名 | 换取消规则只换这一个组件，入口仍依赖 `OmsPortalOrderService` | 多一个类、多一层调用；新增补偿只改 `cancel` |

信息隐藏：库存、券、积分的补偿细节不再由控制器和消息接收者各自记住。条件更新把“只有赢家才补偿”收进 module，而不是调用方先读 status 再碰运气。

## 后果

多一个类，服务方法多一次跳转。依赖方向不变：仍由 portal 里的取消逻辑调用订单 Mapper/Dao、会员服务和优惠券记录，没有反向依赖。

批量路径不要求 `delete_status=0`，和基线 `getTimeOutOrders`（`PortalOrderDao.xml:45-47`）以及 `updateOrderStatus` 按 id 全部写成 4 一致。单笔 `cancel` 仍要求未删除。特征测试里有一笔 2010 年、status=0、delete_status=1 的超时单，改前改后都会变成 status=4，锁定库存减回，券回到 0，积分加回，并计入返回值。最近的已删除订单走 `cancelOrder` 时仍然不动。

基线批量路径遇到没有订单项的超时单时，`releaseSkuStockLock` 会拼出非法 SQL（`CASE` 没有 `WHEN`，`IN ()` 为空）导致整批回滚；改后对空列表跳过。这是按 MyBatis 对空 `foreach` 的语义推断的，没有运行验证。当前教学库没有 status=0 且没有订单项的订单（全库没有订单项的订单有 10 笔，但都不是待付款）。

## 明确不处理

1. `POST /order/cancelOrder` 名不副实，以及 `POST /order/cancelTimeOutOrder` 对外暴露。对外接口不改。
2. `POST /order/cancelUserOrder` 不校验订单属于当前会员。
3. `generateOrder` 在接口事务里发延迟消息（基线 `:245`，当前文件 `:247`，只因插入字段下移，调用没改）。
4. 优惠券退还未按订单关联，仍取该会员该券的第一条相反状态记录。
5. `paySuccess` 不检查前置状态（基线 `:253-265`，当前 `:255-267`，方法体没改）。

## 小型变化：写一条订单操作记录

已核对：`mall-portal` 依赖 `mall-mbg`，能用 `OmsOrderOperateHistoryMapper.insertSelective`。基线 portal 的取消路径没有 `OmsOrderOperateHistory`。表 `oms_order_operate_history` 的列与 mall-admin `OmsOrderServiceImpl.close` 第 67-76 行一致：`orderId`、`createTime`、`operateMan`、`orderStatus`、`note`。备注是「订单关闭:取消订单」。只经 Mapper 调用，没有把接口强转成实现类。`cancelOrder` 同时被用户取消与到期消息调用，演示里操作人一律写「系统」是简化。

- 补偿写入点：基线 2 处（`cancelTimeOutOrder` 与 `cancelOrder`），改后 1 处（`OrderCancellation.cancelIf`）。
- `git diff` hunk：`before.diff` 3 个（Mapper 字段 1 个，两处写入各 1 个）。`after.diff` 8 个：产品代码 3 个（import、final 字段与构造参数、一处写入），测试装配 5 个（两个测试类的 import、构造调用、反射依赖表、注册 Mapper）。改后不用 `@Autowired` 字段注入。基线文件已有 `import com.macro.mall.mapper.*` 和 `model.*`，所以 before.diff 没有 import hunk。

两份补丁都已 `git apply --check`。基线 `before.diff` 之后 `mvn -pl mall-portal -am test-compile` 通过。改后 `after.diff` 含测试装配，apply 后 `SDCE200Test` 与 `SDCE200CancellationTest` 共 8 个测试通过、跳过 0。基线补丁不能带上特征测试（该文件不在基线树上，写进去就无法 apply）；在基线树上另用 `ReflectionTestUtils.setField(impl, "orderOperateHistoryMapper", mapper)` 装配后，`SDCE200Test` 4 个测试通过、跳过 0。这些 setField 不在 `before.diff` 里。补丁没有提交进产品代码。

## 4+1

开发视图和物理视图用 `exp/E2` 的 `SDC/E2/说明.md`「公共 4+1 视图」：逻辑视图 `SDC/E2/diagrams/mall-logical.html`，进程视图 `SDC/E2/diagrams/mall-cancel.html`，开发视图 E1 的 `mall-dependencies.html`。说明里的部署图是本次开发部署；`mall-runtime.html` 不是单独的物理视图。本题没有新机器或新进程。

本题差异只在逻辑和进程：新增 `OrderCancellation` 这一个类。单笔取消进 `cancel`，超时批量进 `cancelOverdue` 再逐笔 `cancelIf`。`/cancelOrder` 和 `generateOrder` 仍只把延迟消息发进 TTL 队列。依赖方向不变。图稿在 `diagrams/`：`logical-before` / `logical-after`，`process-before` / `process-after`。改后图的 `meta.repository.revision` 是 refactor 提交 `47f4f735b26f75deb6e149e069cfe720869f46c6`。改前图引用基线 `dcaa93b3150352e5044708d7211b5bed0af4509f`，因为两份旧补偿在 refactor 提交里已经不在。改前逻辑视图控制器副标题是「两个取消入口」，对应图上从控制器连出的两条取消边；第三条 `/cancelOrder` 画在进程视图的虚线里。

## 证据与未验证

对照见 `evidence/compare.txt`：改前 4 个测试通过、跳过 0；改后 8 个通过、跳过 0；结论符合。`EXPECT_BASE=pass`，因为这是行为保持的结构改进。已删除的超时单这次也锁进了同一组特征测试，所以改前仍然通过。变异和库清理见 `evidence/mutations.txt`。

test 提交用反射按名字装配下一提交才出现的 `OrderCancellation`，是为了让同一组特征测试在改前改后都能运行。学生的特征测试不需要预知重构后的类名。

未验证：

- 没有走 HTTP 把四个 URL 打到运行中的 portal。
- 没有向真实 RabbitMQ 发 TTL 消息再等死信。
- 没有两个真实事务同时支付和取消，来复现先查后改的竞态。条件更新的 0 行行为只在 Mock 测试里模拟。
- 特征测试直接 new 服务实现，会员缓存用 Mock 吞掉 `delMember`。没有验证 Redis 里的会员缓存被删掉，也没有验证 Spring 代理上的 `@Transactional` 把委托调用包在同一个事务里。
- 逻辑视图改前图自动检查通过，但仍有 4 处连线交叉的布局建议；一次只改坐标的重排没有通过校验，已恢复原布局。没有做人工看图。
