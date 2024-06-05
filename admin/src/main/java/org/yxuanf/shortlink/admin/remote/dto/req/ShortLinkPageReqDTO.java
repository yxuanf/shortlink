package org.yxuanf.shortlink.admin.remote.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import org.yxuanf.shortlink.admin.dao.entity.ShortLinkDO;

/**
 * 短链接分页返回参数
 */
@Data
public class ShortLinkPageReqDTO extends Page<ShortLinkDO> {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 排序标识
     */
    private String orderTag;
}