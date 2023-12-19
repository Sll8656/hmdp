package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopMapper shopMapper;
    @Override
    public Result queryById(Long id ) {
        String key = "cache:shop:" + id;
        //redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在就返回
        if(StrUtil.isNotBlank(shopJson)) {

            log.info("Redis中存在!");
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        //看命中的是否是空值，如果==null，说明不存在，！=null，说明存在，并且他的值是 ""。所以不等于的时候要排除
        if(shopJson != null) {
            return Result.fail("店铺信息不存在！");
        }

        //不存在: 根据id查询数据库
        Shop shop = shopMapper.selectById(id);

        //不存在
        if(shop == null) { //解决缓存穿透
            log.info("数据库中不存在!");
            //将redis和数据库中都不存在的存入Redis中
            stringRedisTemplate.opsForValue().set(key, "", 2L,TimeUnit.MINUTES);
            return Result.fail("店铺不存在!");
        }
        log.info("数据库中存在!");
        //存在：写入redis，并返回
        stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        log.info("写入Redis成功!");
        return Result.ok(shop);
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
