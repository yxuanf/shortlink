package org.yxuanf.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import org.yxuanf.shortlink.admin.common.database.BaseDO;

/**
 * 短链接分组实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_group")
public class GroupDO extends BaseDO {
    /**
     * id
     */
    private Long id;
    /**
     * 分组标识
     */
    private String gid;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 创建分组用户名
     */
    private String username;

    /**
     * 分组排序
     */
    private Integer sortOrder;

}
