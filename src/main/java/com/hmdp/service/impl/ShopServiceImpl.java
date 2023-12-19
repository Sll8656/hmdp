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
        //不存在: 根据id查询数据库
        Shop shop = shopMapper.selectById(id);

        //不存在：返回
        if(shop == null) {
            log.info("数据库中不存在!");
            return Result.fail("店铺不存在!");
        }
        log.info("数据库中存在!");
        //存在：写入redis，并返回
        stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(shop));
        log.info("写入Redis成功!");
        return Result.ok(shopJson);
    }

}
