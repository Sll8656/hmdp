package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class LoginInterceptor implements HandlerInterceptor {

    StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //目标：每次用户进行了操作都要刷新token的过期时间

        //获取请求头中的token
        String token = request.getHeader("authorization");

        if(StrUtil.isBlank(token)) {
            //用户不存在就拦截
            response.setStatus(401);
            return false;
        }
        String key = RedisConstants.LOGIN_USER_KEY;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(key+ token);
        //判断用户是否存在
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        //将查询到的 hash数据转化为user对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);

        //刷新token有效期
        stringRedisTemplate.expire(key,30, TimeUnit.MINUTES);
        //放行
        return true;
    }



}
