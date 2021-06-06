# 秒杀系统

分布式的进阶[ 秒杀系统原理 - 搜索结果 - 知乎 (zhihu.com)](https://www.zhihu.com/search?type=content&q=秒杀系统原理)]

## 1. 秒杀系统

### 秒杀场景

1. 电商抢购限流商品
2. 买eason演唱会门票
3. 火车座抢票
4. ……

### 为什么要做一个系统

`如果系统不存在高并发，那么是不需要做秒杀的`，但如果并发量很大，`我们需要一套完整的流程保护措施`，保证系统在用户流量高峰期不会挂掉。

- 严格防止超卖：库存超卖，就会涉及到用户利益。
- 放置黑产：不坏好意的人把用户利益收入囊中
- 保证用户体验：高并发下，导出出现的页面崩溃，模块崩溃。

### 保护措施

- `乐观锁放置超卖`
- `令牌桶限流`
- `Redis缓存`
- `消息队列异步处理订单`
- ……

## 2. 防止超卖

王网页卡住，用户吐槽就完了，超卖可就严重多了，涉及到了用户的利益，人家是有权投诉你的。

### 2.1 数据库表

```mysql
-- --------------------------
-- Table structure for stock
-- --------------------------

Drop table if EXISTS `stock`;
create table `stock` (
	`id` int(11) unsigned not null AUTO_INCREMENT,
	`name` varchar(50) not null default "" comment '名称',
	`count` int(11) not null comment "库存",
	`sale` int(11) not null comment "已售",
	`version` int(11) not null comment "乐观锁 版本号",
	PRIMARY KEY(id)
) Engine = INNODB DEFAULT CHARSET=utf8;


-- --------------------------
-- Table structure for stock and order
-- --------------------------
drop table if EXISTS `stock_order`;
create table `stock_order`(
	`id` int(11) unsigned not null auto_increment,
	`sid` int(11) UNSIGNED not null comment "库存ID",
	`name` VARCHAR(20) not null comment "商品名称",
	`time` TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment "创建时间",-- 表示在数据库数据有更新的时候UPDATE_TIME的时间会自动更新
	PRIMARY KEY(`id`)
) Engine = INNODB DEFAULT CHARSET=utf8;
```

### 2.2 整合springboot 和 mybatis

创建springboot项目

整理`pom.xml`文件

```xml
 <!--web组件-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!--mybatis依赖-->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.1.2</version>
        </dependency>

        <!--mysql依赖-->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>5.1.38</version>
        </dependency>

        <!--lombok依赖-->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.8</version>
            <optional>true</optional>
        </dependency>

        <!--mybatis 整合springboot 需要 alibaba的数据源 druid-->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid</artifactId>
            <version>1.1.19</version>
        </dependency>

        <!--test组件-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```



配置 `appication.yml` 数据源

> 注意url格式 `jdbc:mysql://localhost:3306/seckill` 不是 `jdbc//:mysql://localhost:3306/seckill`

````yml
spring:
  datasource:
    #       使用阿里巴巴的数据源
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.jdbc.Driver
    url: jdbc:mysql://localhost:3306/seckill?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 1forlove
#    似乎是最快的连接池。。
    hikari:
#      自定义连接池名字
      pool-name: DateHikariCP
#      最小空闲连接数
      minimum-idle: 5
      #空闲连接存货最大时间，默认一小时。自定义半小时
      idle-timeout: 1800000
#      最大连接池数
      maximum-pool-size: 10
#      从连接池返回的连接自动提交
      auto-commit: true
#      最大存活时间
      max-lifetime: 1800000
#      连接超时时间 30秒
      connection-timeout: 30000
#      测试连接是否可用的查询语句（心跳机制）
      connection-test-query: SELECT 1


#    mybatis-plus配置
mybatis:
#  配置mapper.xml映射文件 classpath 包含resources包
  mapper-locations: classpath*:/mapper/*Mapper.xml
#  配置mybatis数据返回类型别名路径(默认是类名)
  type-aliases-package: com.anrolsp.seckill.pojo

#Mybatis SQL打印（方法接口所在的包，不是Mapper.xml所在的包）
logging:
  level:
    # 把对应包下面的class的日志文件设置为debug级别
    com.anrolsp.seckill.mapper: debug
    com.anrolsp.seckill.dao: debug
server:
  port: 8989
#
````



### 2.3 分析业务

![image-20210507224313156](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210507224313156.png)

#### 定义StockController 

> ### 接受参数，调用业务创建订单

```java
public class StockController {

    @Autowired
    private StockService stockService;

    /**
     * description: 通过秒杀商品的id得到对应的订单id
     *
     * @return String类型的订单id
     */
    @RestController
    @GetMapping("/kill")
    public String kill(Integer id){
        System.out.println("秒杀商品的id： " + id);
        int id = stockService.getId(id);
        return "秒杀成功，订单id: " + String.valueOf(id);
    }

}
```



#### OrderService

```java
package com.anrolsp.seckill.service;

import org.springframework.stereotype.Service;

@Service
public interface OrderService {
    /**
     * description: 处理秒杀的下单方法，并返回订单的id
     *
     * @Param: 商品id
     * @return 订单的id
     */
    int kill(Integer id);
}

```

#### OrderServiceImpl

> ### 校验库存、扣除库存、创建订单

```java
package com.anrolsp.seckill.service;


import com.anrolsp.seckill.dao.OrderDao;
import com.anrolsp.seckill.dao.StockDao;
import com.anrolsp.seckill.pojo.Order;
import com.anrolsp.seckill.pojo.Stock;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

public class OrderServiceImpl implements OrderService {

    @Autowired
    private StockDao stockDao;

    @Autowired
    private OrderDao orderDao;
    /**
     * description: 校验库存、扣除库存、创建订单
     * 
      * @Param: null
     * @return 
     */
    @Override
    public int kill(Integer id) {
        //校验库存
        Stock stock = stockDao.checkStock(id);
        if (stock.getSale().equals(stock.getCount())) {
            throw new RuntimeException("库存不足！！");
        }else {
            //扣除库存
            stock.setSale(stock.getSale() + 1);
            stockDao.updateSale(stock);
            //创建订单
            Order order = new Order();
            //mybatis会自动生成主键，我们不需要设置主键值
            order.setSid(stock.getId()).setName(stock.getName()).setCreateDate(new Date());
            orderDao.createOrder(order);
            return order.getId();
        }
    }
}

```

> #### 我们需要定义 StockDao，来连接数据库，校验库存
>
> OrderDao，来向数据库中创建订单



#### StockDao

```java
package com.anrolsp.seckill.dao;

import com.anrolsp.seckill.pojo.Stock;

public interface StockDao {
    /**
     * description: 根据商品id返回库存数量
     *
     * @Param: 商品id
     * @return Stock对象
     */
    Stock checkStock(Integer id);
}
```

#### Order.dao

```java
package com.anrolsp.seckill.dao;

import com.anrolsp.seckill.pojo.Order;

public interface OrderDao {
    /**
     * description: 通过
     * 
      * @Param: null
     * @return 
     */
    void createOrder(Order order);
}
```



#### 配置StockMapper.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.anrolsp.seckill.dao.StockDao">
    <!--由于在application.yml中配置了别名属性 type-alias-package,因此pojo包下不需要导入路径-->
    <select id="checkStock" parameterType="int" resultType="Stock">
        select * from stock where id = #{id}
    </select>

    <update id="updateSale" parameterType="Stock">
        update stock set sale = #{sale} where id = #{id}
    </update>
</mapper>
```

#### OrderMapper.xml

> `useGeneratedKeys="true" keyProperty="key"`  keyProperty 表示将生成的主键设置为Order对象的哪个属性

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.anrolsp.seckill.dao.OrderDao">
    <!--useGeneratedKeys 使用数据库的主键生成策略创建id-->
    <!--keyProperty 表示将生成的主键设置为Order对象的哪个属性-->
    <insert id="createOrder" parameterType="Order" useGeneratedKeys="true" keyProperty="id">
        insert into stock_order values(#{id}, #{sid}, #{name}, #{createDate})
    </insert>
</mapper>
```



> ### 能看出来，我们还需要Stock ,order的pojo类



#### Stock

```java
package com.anrolsp.seckill.pojo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * description: 和数据表stock对应，表示秒杀商品的信息
 *
  * @Param: null
 * @return
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Accessors(chain = true)
public class Stock {
    private Integer id;
    private String name;
    private Integer count;
    private Integer sale;
    private Integer version;
}
```



#### Order

```java
package com.anrolsp.seckill.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Accessors(chain = true)
public class Order {
    private Integer id;
    private Integer sid;
    private String name;
    private Date createDate;
}
```





### 2.4 问题分析和注意事项

1. 出现 `java.sql.SQLException: Unknown system variable 'query_cache_size'` 异常，是mysql版本不对应

   

   ```mysql
   mysql> select version()
       -> ;
   +----------+
   | version() |
   +----------+
   | 8.0.18    |
   +----------+
   1 row in set (0.03 sec)
   ```

   修改 `pom.xml`依赖

   ```xml
   <!--mysql依赖-->
           <dependency>
               <groupId>mysql</groupId>
               <artifactId>mysql-connector-java</artifactId>
               <version>8.0.18</version>
           </dependency>
   ```

   

2. 出现 `Invalid bound statement (not found): com.anrolsp.seckill.dao.StockDao.checkStock`

   发现是mapper映射问题 

   ```
   <mapper namespace="com.anrolsp.seckill.dao>改成如下
   <mapper namespace="com.anrolsp.seckill.dao.StockDao">
   ```

   

3. 输入参数格式错误`http://127.0.0.1:8989/stock/kill?1`

   ```java
   @RequestMapping("stock")
   @GetMapping("kill")
   public String kill(Integer id)
   ```

   > ### [阅读指南： springmvc的5种传值的方法_灰太狼-CSDN博客_springmvc传参](https://blog.csdn.net/weixin_39220472/article/details/80293888)

   改成 `http://127.0.0.1:8989/stock/kill?id=1`

### 2.5 防止超卖结果

![image-20210509130428730](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210509130428730.png)

能看到，业务结果实现了





## 3 使用Jmeter进行压力测试

### 3.1 安装jmeter

```markdown
# 1.下载Jmeter
[https://jmeter.apache.org/](https://jmeter.apache.org/)
# 2.配置环境变量
	path变量中加入 D:\Learning_material\other_materials\Java_materials\apache-jmeter-5.4.1\bin
# 3.测试Jmeter是否生效
C:\Users\anrol>jmeter -v

```



### 3.2 jmeter使用

使用命令测试

```cmd
jmeter -n -t [jmx file] -l [results file] -e -o [Path to web report folder]
```

使用GUI测试

![image-20210510220315365](C:\Users\anrol\AppData\Roaming\Typora\typora-user-images\image-20210510220315365.png)



### 3.3 jmeter测试

发现出现了超卖的现象。



### 3.4 使用悲观锁防止超卖

使用 `synchronised` 

```
public synchronised int kill()
```

**不能在业务方法中加悲观锁！**

> 事务的范围比线程的范围大，可能会出现 **线程结束了，但是事务没有提交**，仍然使用的原来的库存进行库存判断，此时下一个线程执行的时候，事务提交，会导致一个线程购买了两次商品。

#### 3.4.1 解决方法

不要在业务方法中加入同步代码块

1. 注释掉 `@Transactional`

   ```java
   //@Transactional
   ```

   

2. 或者 在业务方法的调用处加入锁，这样线程的范围比事务的范围大

   ```java
   synchronized (this){
       int killId = orderService.kill(id);
       return "秒杀成功，订单id: " + String.valueOf(killId);
   }
   ```

### 3.5 使用乐观锁解决商品的超卖问题

​	利用数据库的 `version` 字段和事务操作的原子性，来解决并发情况下超卖问题

1. ​	使用 `乐观锁` 改造 `updateSale()`防止 `超卖`问题

   ```java
    private void updateSale(Stock stock){
   
           //stock.setSale(stock.getSale() - 1);
           //使用数据库层面的version字段来实现并发修改问题，需要通过版本号和销量来更新销量
           //有的线程会无法更新sale（版本号不对），因此要处理无法更新的异常情况，失败的事务返回0
           int result = stockDao.updateSale(stock);
           if (result == 0){
               throw new RuntimeException("商品购买失败！");
           }
       }
   ```

   对应mybatis数据库语句改造

   ```xml
   <!--事务+version实现乐观锁,失败的事务返回0-->
       <update id="updateSale" parameterType="Stock">
           update stock set sale = sale + 1 and version = version + 1
           where id = #{id} and version = #{version}
       </update>
   ```

   其他方法无需改造

**结果**

库存100时

当有2000条并发的时候，确实卖出去了100个库存，并且只生成了100个订单。

但是只有110条并发的时候，只卖出去了96个商品

![image-20210511092153044](C:\Users\anrol\AppData\Roaming\Typora\typora-user-images\image-20210511092153044.png)

尽管`购买人数 > 商品库存`，仍然有**商品剩余**。



### 3.6 Redis的Multi 和 exec实现乐观锁

## 4. 接口限流

`限流：对某一时间窗口内的请求书进行限制，保持系统的可用性和稳定性，防止因流量暴增而导致系统的运行缓慢或者宕机`

### 4.1 接口限流

高并发的请求中，不进行接口限流，可能对后台系统造成巨大的压力，大量的请求抢购成功时需要调用下单的接口，过多的数据库请求会对系统造成影响。

因此一般的后台中，秒杀需要独立开发

### 4.2 如何解决接口限流

开发该并发的时候有三种方法来保护系统： `缓存` `降级`和 `接口限流`

常用的限流算法有 `令牌桶` 和 `漏桶（漏斗算法）` ， Google中的Guava中的RateLimiter就是令牌桶控制算法。

![image-20210511094916133](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210511094916133.png)

### 4.3 令牌桶和漏斗算法

<img src="https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210511094916133.png" alt="image-20210511095021315" style="zoom: 150%;" />

令牌桶算法更像一个自旋，当拿不到令牌的时候，就会等待，直到拿到令牌

### 4.4 令牌桶简单实现

1. **导入依赖**

   ```xml
   <!--拥有令牌桶算法的组件-->
   <dependency>
       <groupId>com.google.guava</groupId>
       <artifactId>guava</artifactId>
       <version>28.2-jre</version>
   </dependency>
   ```

   

2. **令牌桶算法的基本操作**

   结合 `lombok` 的 `@log4j`

   ```java
   private RateLimiter r = RateLimiter.create(10);//创建了容量为10的令牌桶
   r.acquire();//1.第一种：请求获取token令牌，没有拿到自旋等待
   r.tryAquire(5, timeUnit.SECONDS); //第二种情况，超时等待，在5秒内等待获取令牌，如果超过5秒，请求被抛弃，无法处理后续逻辑。
   ```

   ```java
   //令牌桶实例
       private RateLimiter rateLimiter = RateLimiter.create(40);
       /**
        * description: 令牌桶的测试方法
        *
        * @Param: 商品id
        * @return
        */
       @RequestMapping("sale")
       public String sale(Integer id){
           //log.info("等待的时间" + rateLimiter.acquire());
           if (!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
               System.out.println("当前请求被限流，无法处理后续秒杀逻辑..");
               return "抢购失败";
           }
           System.out.println("处理业务....");
           return "抢购成功";
       }
   ```

### 4.5 使用令牌桶实现乐观锁+限流

开发新的秒杀方法，使用`乐观锁+令牌桶`防止`超卖+限流`问题

1. 使用令牌桶改造 `controller`对接口进行限流



加入限流之后，有效放置了对系统的负载，但是可能会存在库存剩余。



## 5. 加入秒杀时间验证

实现了防止超卖和接口限流之后，我们还需要考虑以下三点：

1. `我们应该能限制秒杀的时间期限，如何加入时间验证？`
2. `对于某些懂一些技术的用户，如何防止他们进行抓包，用脚本来秒杀呢？`
3. `如何限定单用户的访问次数呢？`

因此我们执行三个模块：

1. 限时抢购
2. 防抓包处理（抢购接口隐藏，防爬）
3. 限定单用户访问频率



### 5.1 在redis中设置`key：kill `

`set kill1 1 ex 180`

这样每次请求的时候，kill存在才能调用kill接口

### 5.2 `pom.xml`设置redis依赖

```xml
spring-boot-starter-data-redis
```



### 5.3 `application.yml`配置redis

设置`redis接口`、`默认数据库索引`和`host远程数据库`

### 5.4 redis限时代码

`StockServiceImpl`

```java
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * description: 校验库存、扣除库存、创建订单
     * 
      * @Param: 商品id
     * @return 订单id
     */
    @Override
    public int kill(Integer id) {
        if (!stringRedisTemplate.hasKey("kill"+ id)){
            throw new RuntimeException("秒杀时间已过！");
        }
        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        return createOrder(stock);
    }
```

1. 使用jmeter测试

   ![image-20210512195635412](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210512195635412.png)

2. 结果

   ![image-20210512200941104](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210512200941104.png)

## 6. 用户接口隐藏

防止有些人直接通过`用户id`发起大量下单请求，**避开点击下单按钮**。这肯定比人点击要快。

因此我们要将**接口隐藏**。

**具体做法**：

![image-20210606153048857](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210606153048857.png)

1. 点击秒杀按钮，通过`用户id和商品id+随机salt`生成`md5验证值`，`用户id和商品id`作为限时key存入Redis，value是生成的md5
2. 用户点击下单按钮，通过生成的 `md5`和Redis中存入的 `md5`匹配。不匹配抛弃请求。

**脚本可以直接发起秒杀请求，但是得不到md5,不能通过下单请求**



### 6.1 新增User表

`set foreign_key_checks=1`

`StringRedisTemplate.opsForValue()`

`set names utf8mb4` 设置变量（各种字符集）为 `utf8mb4`格式

![image-20210606153130781](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210606153130781.png)

```mysql
-- --------------------------
-- Table structure for users
-- --------------------------
set names utf8mb4; #支持表情符号
set FOREIGN_KEY_CHECKS = 0;
drop table if exists `user`;
create table `user`(
`id` int unsigned not null auto_increment,
`name` varchar(20) not null default "" comment "用户名",
`password` varchar(40) not null default "" comment "用户密码",
primary key(id)
 ) engine = INNODB DEFAULT CHARSET=utf8;
 set FOREIGN_KEY_CHECKS = 1;#开启外键约束
```



### 6.2 生成md5验证值

1. `controller` 调用`service`的`md5`

   ```java
    /**
        * description: 用户点击秒杀按钮，根据用户id和商品id生成md5
        *
        * @Param: 商品id，用户sid
        * @return md5加密
        */
       @GetMapping("md5")
       public String md5(Integer id, Integer sid){
           String md5;
           try{
               //getMd5生成失败抛出异常
               md5 = orderService.getMd5(id, sid);
           }catch(Exception e){
               e.printStackTrace();
               return "md5获取失败!" + e.getMessage();
           }
           return "获取md5信息："+md5;
       }
   ```

   

2. `stockImpl`定义 `md5()`生成md5方法 

   ```java
    /**
        * description: 用户点击秒杀按钮，根据用户id和商品id生成md5
        *
        * @Param: 商品id，用户sid
        * @return md5加密
        */
       public String getMd5(Integer id, Integer sid){
           //验证用户的合法性
           User user = userDao.getUserById(sid);
           if (user == null)
               throw new RuntimeException("用户不存在！");
           log.info("用户信息:[{}]", user.toString());
           Stock stock = stockDao.checkStock(id);
           if (stock == null)
               throw new RuntimeException("商品不存在!");
           log.info("商品信息:[{}]", stock.toString());
           //设置md5Key作为redis的key
           String key = "KEY_"+ id + "_" + sid;
           //生成md5,!@#*&是salt，防止被轻易破解
           String md5Value = DigestUtils.md5DigestAsHex((key+"!@#*&").getBytes());
           //存入Redis中
           stringRedisTemplate.opsForValue().set(key, md5Value, 120, TimeUnit.SECONDS);
           log.info("md5写入[{}] [{}]", key, md5Value);
           return "md5:" + key;
       }
   ```

3. 定义`user`

   需要user 来存放从数据库中查询的返回的user对象

   ```java
   @Slf4j
   @Data
   public class User {
       int id;
       String name;
       String password;
   }
   ```

4. `定义userDao`

   ```java
   @Repository
   public interface UserDao {
   
       User getUserById(Integer id);
   }
   ```

5. `userMapper.xml`定义findByid方法实现

   ```xml
   <mapper namespace="com.anrolsp.seckill.dao.UserDao">
       <select id="getUserById" parameterType="int" resultType="User">
           select * from user where id = #{id}
       </select>
   </mapper>
   ```

### 6.3 校验md5

1. `controller`调用`service`秒杀方法

   ```java
   /**
    * description: 通过 乐观锁+接口限流+限时+隐藏接口 实现秒杀
    * @param 商品id 用户id md5
    * @return String类型的商品id
    */
   @GetMapping("killtokenmd5")
   public String killtokenmd5(Integer id, Integer sid, String md5){
       if (!rateLimiter.tryAcquire(3, TimeUnit.SECONDS)){
           throw new RuntimeException("秒杀被限流！请重试！");
       }
       System.out.println("秒杀商品的id： " + id);
       try{
           int killId = orderService.kill(id,sid, md5);
           return "秒杀成功，订单id: " + String.valueOf(killId);
       }catch(Exception e){
           e.printStackTrace();
           return e.getMessage();
       }
   }
   ```

2. 秒杀实现前校验md5

   ```java
     /**
        * description: 判断是否超时校验md5、校验库存、扣除库存、创建订单
        *
        * @Param: 商品id
        * @return 订单id
        */
       @Override
       public int kill(Integer id, Integer sid, String md5) {
           //校验md5
           String md5Key = "KEY_"+ id + "_" + sid;
           if (md5 == null || !md5.equals(stringRedisTemplate.opsForValue().get(md5Key)))
               throw new RuntimeException("不存在对应的md5！");
           //校验库存
           Stock stock = checkStock(id);
           //扣除库存
           updateSale(stock);
           //创建订单
           return createOrder(stock);
       }
   ```

加入其他人同样进行md5加密，能获取到对应的md5吗？

> 不行，外部人员没看源码，拿不到salt，并且生成的md5有时间限制.



结果

![image-20210514090712633](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210514090712633.png)

![image-20210514090701573](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210514090701573.png)

## 7.  单用户请求访问限制频率

防止有人加密md5，仍然能用脚本进行请求访问，对请求进行限制。

每次用户发送请求时记录访问次数(*存入Redis*)，如果访问次数>1，拒绝请求

**这个和限制用户只能秒杀一次没有关系**



1. 在 `controller`的接口限流后，秒杀方法前加入判断逻辑

   ```java
   @Autowired
   private UserService userService;
   /**
    * description: 通过 乐观锁+接口限流+限时+隐藏接口+频率限制 实现秒杀
    * @param 商品id 用户id md5
    * @return String类型的商品id
    */
   @GetMapping("killtokenmd5limit")
   public String killtokenmd5limit(Integer id, Integer sid, String md5){
       if (!rateLimiter.tryAcquire(3, TimeUnit.SECONDS)){
           throw new RuntimeException("秒杀被限流！请重试！");
       }
       System.out.println("秒杀商品的id： " + id);
       //进行单用户访问限制
       int userCount = userService.getUserCount(sid);
       log.info("用户到目前位置的访问次数[{}]", userCount);
       if (userCount > 10){
           return "购买失败，超过访问频率限制！";
       }
       try{
           int killId = orderService.kill(id,sid, md5);
           return "秒杀成功，订单id: " + String.valueOf(killId);
       }catch(Exception e){
           e.printStackTrace();
           return e.getMessage();
       }
   }
   ```

2. `userService、userServiceImpl`实现访问次数逻辑

   1. 根据不同用户id生成不同访问次数的key
   2. 根据key得到value
   3. 如果不存在
      1. 存入redis，默认值`"0"`
   4. 存在：调用redis自增函数

   ```java
   @Autowired
   private StringRedisTemplate stringRedisTemplate;
   
   @Override
   public int getUserCount(Integer id) {
       //生成rediskey
       String limitKey = "KEY_" + id+"COUNT";
       String userCount = stringRedisTemplate.opsForValue().get(limitKey);
       if (userCount == null){
           stringRedisTemplate.opsForValue().set(limitKey, "1", 3600, TimeUnit.SECONDS);
       }else
           stringRedisTemplate.boundValueOps(limitKey).increment();
       return Integer.parseInt(userCount);
   }
   ```

   > 需要用到 `getUserCount`吗？不需要

   

3. 结果：限制了用户的访问请求次数

   ![image-20210514095753208](https://raw.githubusercontent.com/OOOuya/cloudImg/master/img2/image-20210514095753208.png)
