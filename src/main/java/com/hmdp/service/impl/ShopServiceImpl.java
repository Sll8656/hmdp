package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;


@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopMapper shopMapper;
    @Override
    public Result queryById(Long id ) {
        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithPassMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop =  queryWithLogicalExpire(id);


        if(shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    public Shop queryWithPassMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在就返回
        if(StrUtil.isNotBlank(shopJson)) {

            log.info("Redis中存在!");
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //看命中的是否是空值，如果==null，说明不存在，！=null，说明存在，并且他的值是 ""。所以不等于的时候要排除
        if(shopJson != null) {
            return null;
        }

        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean gotLock = tryLock(lockKey);
            if(!gotLock) {
                Thread.sleep(50);
                return queryWithPassMutex(id);
            }
            //判断是否获取成功
            //失败，则休眠并重试
            //成功就根据id查询数据库


            //不存在: 根据id查询数据库
            shop = shopMapper.selectById(id);
            Thread.sleep(200); //模拟重建的延时
            //不存在
            if(shop == null) { //解决缓存穿透
                log.info("数据库中不存在!");
                //将redis和数据库中都不存在的存入Redis中
                stringRedisTemplate.opsForValue().set(key, "", 2L,TimeUnit.MINUTES);
                return null;
            }
            log.info("数据库中存在!");
            //存在：写入redis，并返回
            stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
            log.info("写入Redis成功!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }


        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    //封装的缓存穿透代码
    public Shop queryWithPassThrough(Long id) {
        String key = "cache:shop:" + id;
        //redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在就返回
        if(StrUtil.isNotBlank(shopJson)) {

            log.info("Redis中存在!");
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //shopJson三种情况：1.null 2.有值 3.""。上面的逻辑走下来，只剩下情况1和3。
        if(shopJson != null) {
            // != null 说明shopJson == ""。shopJson一开始肯定是null,所以才会执行数据库，数据库将无效key写入Redis，shopJson才!=null
            // shopJson == ""的前提是数据库将""写入了Redis。所以以后每次打入""，都会被Redis从这里拦截住。
            return null;
        }

        //根据id查询数据库
        Shop shop = shopMapper.selectById(id);

        //不存在
        if(shop == null) { //解决缓存穿透
            log.info("数据库中不存在!");
            //将redis和数据库中都不存在的存入Redis中
            stringRedisTemplate.opsForValue().set(key, "", 2L,TimeUnit.MINUTES);
            return null;
        }
        log.info("数据库中存在!");
        //存在：写入redis，并返回
        stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        log.info("写入Redis成功!");
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY +id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)) {
            return null;
        }
        //将shop和expireTime从redis中的json取出来
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            //没过期
            return shop;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        boolean gotLock = tryLock(lockKey);
        if(gotLock) {
            CACHE_REBUILD_EXECUTOR.submit(()-> {
                try {
                    this.saveShop2Redis(id,20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        return shop;
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);

        stringRedisTemplate.delete("cache:shop:" + shop.getId());
        return Result.ok();
    }

}
