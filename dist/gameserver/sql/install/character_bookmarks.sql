CREATE TABLE IF NOT EXISTS `character_bookmarks` (
	`char_Id` INT NOT NULL,
	`idx` TINYINT UNSIGNED NOT NULL,
	`name` VARCHAR(32) CHARACTER SET UTF8 NOT NULL,
	`acronym` VARCHAR(4) CHARACTER SET UTF8 NOT NULL,
	`icon` TINYINT UNSIGNED NOT NULL,
	`x` INT NOT NULL,
	`y` INT NOT NULL,
	`z` INT NOT NULL,
	PRIMARY KEY  (`char_Id`,`idx`)
) ENGINE=MyISAM;