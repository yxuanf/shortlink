package org.yxuanf.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dto.req.UserRegisterReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户接口
 */
public interface UserService extends IService<UserDO> {
    /**
     * 根据用户名查询用户信息
     *
     * @param username
     * @return
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
     * @param requestParam 用户请求参数
     */
    void register(UserRegisterReqDTO requestParam);
}
