##other_discriminator_user表结构和数据



DROP TABLE IF EXISTS `other_discriminator_user`;
CREATE TABLE `other_discriminator_user`
(
    `id`          int(20) NOT NULL AUTO_INCREMENT,
    `user_name`   varchar(60)      DEFAULT NULL COMMENT '用户名称',
    `real_name`   varchar(60)      DEFAULT NULL COMMENT '真实名称',
    `sex`         char(1) NOT NULL DEFAULT '1' COMMENT '性别',
    `mobile`      varchar(20)      DEFAULT NULL COMMENT '电话',
    `email`       varchar(60)      DEFAULT NULL COMMENT '邮箱',
    `note`        varchar(200)     DEFAULT NULL COMMENT '备注',
    `position_id` int(20) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY           `fk_4` (`position_id`)
) ENGINE=InnoDB AUTO_INCREMENT=127 DEFAULT CHARSET=utf8;

INSERT INTO `other_discriminator_user`
VALUES ('1', 'lison', '李小宇', '1', '18232344223', 'lison@qq.com', 'lison的备注', '1');
INSERT INTO `other_discriminator_user`
VALUES ('2', 'james', '陈大雷', '1', '18454656125', 'james@qq.com', 'james的备注', '2');
INSERT INTO `other_discriminator_user`
VALUES ('3', 'cindy', '王美丽', '0', '14556656512', 'xxoo@163.com', 'cindys note', '1');
INSERT INTO `other_discriminator_user`
VALUES ('126', 'mark', '毛毛', '0', '18635457815', 'xxoo@163.com', 'marks note', '1');

################################################################################################

##other_discriminator_health_repormale表结构和数据(男性体检报告表)

DROP TABLE IF EXISTS `other_discriminator_health_repormale`;
CREATE TABLE `other_discriminator_health_repormale`
(
    `id`                          int(20) NOT NULL AUTO_INCREMENT,
    `check_project`               varchar(50)  DEFAULT NULL,
    `detail`                      varchar(100) DEFAULT NULL,
    `other_discriminator_user_id` int(20) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

INSERT INTO `other_discriminator_health_repormale`
VALUES ('1', '男人项目1', '达标', '1');
INSERT INTO `other_discriminator_health_repormale`
VALUES ('2', '男人项目2', '达标', '1');
INSERT INTO `other_discriminator_health_repormale`
VALUES ('3', '男人项目3', '达标', '1');

################################################################################################

##other_discriminator_health_reporfemale表结构和数据(女性体检报告表)
DROP TABLE IF EXISTS `other_discriminator_health_reporfemale`;
CREATE TABLE `other_discriminator_health_reporfemale`
(
    `id`                          int(20) NOT NULL AUTO_INCREMENT,
    `item`                        varchar(50)    DEFAULT NULL,
    `score`                       decimal(10, 2) DEFAULT NULL,
    `other_discriminator_user_id` int(20) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

INSERT INTO `other_discriminator_health_reporfemale`
VALUES ('1', '女生项目1', '80.00', '3');
INSERT INTO `other_discriminator_health_reporfemale`
VALUES ('2', '女生项目2', '60.00', '3');
INSERT INTO `other_discriminator_health_reporfemale`
VALUES ('3', '女生项目3', '90.00', '3');

