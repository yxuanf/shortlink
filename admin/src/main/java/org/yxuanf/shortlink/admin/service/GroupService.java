package org.yxuanf.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.yxuanf.shortlink.admin.dao.entity.GroupDO;
import org.yxuanf.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import org.yxuanf.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * 短链接分组接口层
 */
public interface GroupService extends IService<GroupDO> {
    /**
     * 短链接分组新增功能
     *
     * @param groupName 请求参数
     */
    void saveGroup(String groupName);

    /**
     * 短链接分组新增功能
     *
     * @param groupName 请求参数
     * @param username  用户名
     */
    void saveGroup(String username, String groupName);

    /**
     * 查询短链接分组集合
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 根据id,修改短链接分组名
     *
     * @param requestParam 修改请求参数
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);

    /**
     * 根据gid,删除短链接
     *
     * @param gid 软删除gid (deleteFlag = 1)
     */
    void deleteGroup(String gid);

    /**
     * 短链接分组排序
     *
     * @param requestParam 短链接分组排序参数
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);
}
