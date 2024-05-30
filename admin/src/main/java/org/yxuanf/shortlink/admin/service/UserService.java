package org.yxuanf.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.yxuanf.shortlink.admin.dto.resp.UserLoginRespDTO;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dto.req.UserLoginReqDTO;
import org.yxuanf.shortlink.admin.dto.req.UserRegisterReqDTO;
import org.yxuanf.shortlink.admin.dto.req.UserUpdateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户接口
 */
public interface UserService extends IService<UserDO> {
    /**
     * 根据用户名查询用户信息
     *
     * @param username 用户名
     * @return 返回用户信息
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 查询用户是否存在
     *
     * @param username 用户名
     * @return True 用户名存在 False用户名不存在
     */
    Boolean hasUsername(String username);

    /**
     * 注册用户
     *
     * @param requestParam 用户请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 根据用户名更改用户
     *
     * @param requestParam 用户请求参数
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登入
     *
     * @param requestParam 登录请求参数
     * @return 登录返回参数
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 检查用户是否登录
     *
     * @param token 登录Token
     * @return 登录标识
     */
    Boolean checkLogin(String username, String token);

    /**
     * 用户登出
     *
     * @param username 用户名
     * @param token    用户Token
     */
    void logout(String username, String token);
}
