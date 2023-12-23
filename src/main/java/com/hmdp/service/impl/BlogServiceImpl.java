package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    private void  queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

//    private void isBlogLiked(Blog blog) {
//        UserDTO user = UserHolder.getUser();
//        if(user == null) {
//            return;
//        }
//        Long userId = user.getId();
//        String key = "blog:liked" + blog.getId();
//        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
//        blog.setIsLike(score != null);
//    }
    private void isBlogLiked(Blog blog) {
        Long userId = UserHolder.getUser().getId(); //从ThreadLocal中拿到用户id
        String key = "blog:liked:" + blog.getId(); //组成Redis中的key
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString()); //看看在不在Redis中
        blog.setIsLike(BooleanUtil.isTrue(isMember)); //如果在就设置为True，不在就设置为false。
    }
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }
    @Override
    public Result queryBlogById(Long id) {
        // 查询Blog
        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("笔记不存在！");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }
//    @Override
//    public Result likeBlog(Long id) {
//        Long userId = UserHolder.getUser().getId();
//        String key = BLOG_LIKED_KEY + id;
//        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
//        if(score == null) {
//            boolean isSuccess = update().setSql("liked = liled + 1").eq("id", id).update();
//            if(isSuccess) {
//                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
//            }
//        } else {
//            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
//            if(isSuccess) {
//                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
//            }
//        }
//        return Result.ok();
//    }
    @Override
    public Result likeBlog(Long id){
        Long userId = UserHolder.getUser().getId();       // 1.获取登录用户
        String key = BLOG_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());    // 2.判断当前登录用户是否已经点赞
        if(BooleanUtil.isFalse(isMember)){    //3.如果未点赞
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //保存用户到Redis的set集合
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }else{    //4.如果已点赞，取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                //把用户从Redis的set集合移除
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }
}
