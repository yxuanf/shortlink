package org.yxuanf.shortlink.admin.test;

public class UserTableShardingTest {
    public static final String SQL = "create table t_user_%d\n" +
            "(\n" +
            "    id            bigint auto_increment comment 'ID'\n" +
            "        primary key,\n" +
            "    username      varchar(256) null comment '用户名',\n" +
            "    password      varchar(512) null comment '密码',\n" +
            "    real_name     varchar(256) null comment '真实姓名',\n" +
            "    phone         varchar(128) null comment '手机号',\n" +
            "    mail          varchar(512) null comment '邮箱',\n" +
            "    deletion_time bigint       null comment '注销时间戳',\n" +
            "    create_time   datetime     null comment '创建时间',\n" +
            "    update_time   datetime     null comment '修改时间',\n" +
            "    del_flag      tinyint(1)   null comment '删除标识 0：未删除 1：已删除',\n" +
            "    constraint idx_unique_username\n" +
            "        unique (username) comment '用户名缩影'\n" +
            ");";
    private static final String SQL_SHORTLINK = "create table t_link_%d\n" +
            "(\n" +
            "    id              bigint auto_increment comment 'ID'\n" +
            "        primary key,\n" +
            "    domain          varchar(128)                   null comment '域名',\n" +
            "    short_uri       varchar(8) collate utf8mb4_bin null comment '短链接',\n" +
            "    full_short_url  varchar(128)                   null comment '完整短链接',\n" +
            "    origin_url      varchar(1024)                  null comment '原始链接',\n" +
            "    click_num       int default 0                  null comment '点击量',\n" +
            "    gid             varchar(32)                    null comment '分组标识',\n" +
            "    favicon         varchar(256)                   null comment '网站图标',\n" +
            "    enable_status   tinyint(1)                     null comment '启用标识 （0：启用）（1：未启用）',\n" +
            "    created_type    tinyint(1)                     null comment '创建类型 0：控制台 1：接口',\n" +
            "    valid_date_type tinyint(1)                     null comment '有效期类型 0：永久有效 1：用户自定义',\n" +
            "    valid_date      datetime                       null comment '有效期',\n" +
            "    `describe`      varchar(1024)                  null comment '描述',\n" +
            "    create_time     datetime                       null comment '创建时间',\n" +
            "    update_time     datetime                       null comment '修改时间',\n" +
            "    del_flag        tinyint(1)                     null comment '删除标识 0：未删除 1：已删除',\n" +
            "    constraint idx_unique_full_short_url\n" +
            "        unique (full_short_url)\n" +
            ");";
    private static final String SQL_GROUP = "create table t_group_%d\n" +
            "(\n" +
            "    id          bigint auto_increment comment 'ID'\n" +
            "        primary key,\n" +
            "    gid         varchar(32)  null comment '分组标识',\n" +
            "    name        varchar(64)  null comment '分组名称',\n" +
            "    username    varchar(256) null comment '创建分组用户名',\n" +
            "    create_time datetime     null comment '创建时间',\n" +
            "    update_time datetime     null comment '修改时间',\n" +
            "    del_flag    tinyint(1)   null comment '删除标识 0：未删除 1：已删除',\n" +
            "    sort_order  int          null comment '分组排序',\n" +
            "    constraint idx_unique_username_gid\n" +
            "        unique (gid, username)\n" +
            ");\n";
    private static final String SQL_GOTO_LINK = "create table t_link_goto_%d\n" +
            "(\n" +
            "    id             bigint auto_increment comment 'ID'\n" +
            "        primary key,\n" +
            "    gid            varchar(32) default 'default' null comment '分组标识',\n" +
            "    full_short_url varchar(128)                  null comment '完整短链接'\n" +
            ");";
    private static final String SQL_UPDATE_T_LINK = "alter table t_link_%d\n" +
            "    modify del_time datetime null;";
    private static final String SQL_UPDATE_T_LINK1 = "alter table t_link_%d\n" +
            "    add total_pv int default null comment '历史PV',\n" +
            "    add total_uv int default null comment '历史UV',\n" +
            "    add total_uip int default null comment '历史UIP',\n" +
            "    add today_pv int default null comment '今日PV',\n" +
            "    add today_uv int default null comment '今日UV',\n" +
            "    add today_uip int default null comment '今日UIP';";
    private static final String SQL_T_LINK_TODAY = "create table t_link_stats_today_%d\n" +
            "(\n" +
            "    id             bigint auto_increment comment 'ID'\n" +
            "        primary key,\n" +
            "    gid            varchar(32) default 'default' null comment '分组标识',\n" +
            "    full_short_url varchar(128)                  null comment '短链接',\n" +
            "    date           date                          null comment '日期',\n" +
            "    today_pv       int         default 0         null comment '今日PV',\n" +
            "    today_uv       int         default 0         null comment '今日UV',\n" +
            "    today_uip      int                           null comment '今日ip数',\n" +
            "    create_time    datetime                      null comment '创建时间',\n" +
            "    update_time    datetime                      null comment '修改时间',\n" +
            "    del_flag       tinyint(1)                    null comment '删除标识 0：未删除 1：已删除',\n" +
            "    constraint idx_unique_today_stats\n" +
            "        unique (full_short_url, gid, date)\n" +
            ");\n";
    private static final String SQL_T_LINK_DEFAULT = "alter table t_link_%d\n" +
            "    drop column today_pv,\n" +
            "    drop column today_uv,\n" +
            "    drop column today_uip;";
    private static final String SQL_DELETE_TODAY = "alter table t_link_goto_%d\n" +
            "    modify full_short_url varchar(128) collate utf8mb4_bin null comment '完整短链接';";

    public static void main(String[] args) {
        for (int i = 0; i < 16; i++) {
            System.out.printf((SQL_T_LINK_TODAY) + "%n", i);
        }
    }
}
