package com.ai.assistance.operit.data.model

/**
 * 内置预设主题包。
 *
 * 设计原则：低饱和、靠形状而非高对比区分层级；浅色 6 套 + 深色 4 套。
 * 每套只声明「主色 / 辅色 / 明暗」和少量界面微调，其余沿用用户现有设置
 * （字体、气泡细节、显示开关都不动），这样换主题不会把个人偏好冲掉。
 */
object ThemePackPresets {

    /**
     * 跟随壁纸：交给系统莫奈取色（Android 12+）。零体积，颜色随手机壁纸走。
     * 排在第一位，作为「不想挑颜色」时的默认选择。
     */
    val wallpaper =
            ThemePack(
                    id = "wallpaper",
                    name = "跟随壁纸",
                    description = "按手机壁纸自动取色（Android 12+）",
                    dark = false,
                    primaryColor = -11629454,
                    secondaryColor = -9459569,
                    followWallpaper = true
            )

    /** 青竹：柔和青绿，RevoKit 的主形象色。 */
    val qingzhu =
            ThemePack(
                    id = "qingzhu",
                    name = "青竹",
                    description = "柔和青绿，日常主力",
                    dark = false,
                    primaryColor = -11629454,
                    secondaryColor = -9459569
            )

    /** 苔原：灰绿偏暗，长时间看不累。 */
    val taiyuan =
            ThemePack(
                    id = "taiyuan",
                    name = "苔原",
                    description = "灰绿偏暗，久看不累",
                    dark = false,
                    primaryColor = -9535652,
                    secondaryColor = -7693704
            )

    /** 雾蓝：冷调克制，专注感强。 */
    val wulan =
            ThemePack(
                    id = "wulan",
                    name = "雾蓝",
                    description = "冷调克制，专注感强",
                    dark = false,
                    primaryColor = -10781543,
                    secondaryColor = -8741963
            )

    /** 藕荷：灰紫带一点粉，柔和偏暖。 */
    val ouhe =
            ThemePack(
                    id = "ouhe",
                    name = "藕荷",
                    description = "灰紫带粉，柔和偏暖",
                    dark = false,
                    primaryColor = -7704962,
                    secondaryColor = -5731172
            )

    /** 纸白：近乎无彩色，只靠形状分层。 */
    val zhibai =
            ThemePack(
                    id = "zhibai",
                    name = "纸白",
                    description = "近乎无彩色，只靠形状分层",
                    dark = false,
                    primaryColor = -9737365,
                    secondaryColor = -7566196
            )

    /** 石墨：深灰底 + 柔白字，夜里不刺眼。 */
    val shimo =
            ThemePack(
                    id = "shimo",
                    name = "石墨",
                    description = "深灰底柔白字，夜里不刺眼",
                    dark = true,
                    primaryColor = -8749177,
                    secondaryColor = -6643544
            )

    /** 夜航：深蓝底，安静。 */
    val yehang =
            ThemePack(
                    id = "yehang",
                    name = "夜航",
                    description = "深蓝底，安静",
                    dark = true,
                    primaryColor = -11900022,
                    secondaryColor = -9728853
            )

    /** 墨竹：青竹的暗色版。 */
    val mozhu =
            ThemePack(
                    id = "mozhu",
                    name = "墨竹",
                    description = "青竹的暗色版",
                    dark = true,
                    primaryColor = -10712458,
                    secondaryColor = -8673132
            )

    /** 琥珀：暖调，带纸感。 */
    val hupo =
            ThemePack(
                    id = "hupo",
                    name = "琥珀",
                    description = "暖调纸感",
                    dark = false,
                    primaryColor = -5209532,
                    secondaryColor = -3563677
            )

    /** 赤陶：砖红，个性强一点。 */
    val chitao =
            ThemePack(
                    id = "chitao",
                    name = "赤陶",
                    description = "砖红，个性强",
                    dark = false,
                    primaryColor = -6005942,
                    secondaryColor = -4227474
            )

    /**
     * 展示顺序。
     *
     * RevoKit：纯色预设已从列表里移除——应用默认外观要与上游 Operit 一致，
     * 卡片位只留「跟随壁纸」，其余位置由用户导入的主题包填充（见 ThemePackLibrary）。
     * 下面那些纯色定义的代码保留着，需要时可以一行加回来。
     */
    val all: List<ThemePack> = listOf(wallpaper)
}
