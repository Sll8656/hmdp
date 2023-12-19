package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopTypeMapper shopTypeMapper;

    @Override
    public Result queryShopType() {
        String key = "cache:shopType";
        // 1.查询redis缓存
        List<String> typeJson = stringRedisTemplate.opsForList().range(key, 0, -1);
        // 2.判断是否命中
        if (CollectionUtil.isNotEmpty(typeJson)) {
            // 2.0 如果为空对象(防止缓存穿透时存入的空对象)
            if (StrUtil.isBlank(typeJson.get(0))) {
                return Result.fail("商品分类信息为空！");
            }
            // 2.1 命中则转换List<String> -> List<ShopType> 并返回、
            List<ShopType> typeList = new ArrayList<>();
            for (String jsonString : typeJson) {
                ShopType shopType = JSONUtil.toBean(jsonString, ShopType.class);
                typeList.add(shopType);
            }
            log.info("数据在Redis中！！");
            return Result.ok(typeList);
        }
        // 3. 未命中，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 3.1 数据库中不存在
        if (CollectionUtil.isEmpty(typeList)) {
            // 添加空对象到redis，解决缓存穿透
            stringRedisTemplate.opsForList().rightPushAll(key, CollectionUtil.newArrayList(""));
            stringRedisTemplate.expire(key,30, TimeUnit.MINUTES);
            // 返回错误
            return Result.fail("商品分类信息为空！");
        }
        // 3.2 数据库中存在,转换List<ShopType> -> List<String> 类型
        List<String> shopTypeList = new ArrayList<>();
        for (ShopType shopType : typeList) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(jsonStr);
        }
        // 4.写入redis缓存, 有顺序只能RPUSH
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);
        log.info("数据在MySQL中!!");
        // 5. 返回
        return Result.ok(typeList);
    }
}

