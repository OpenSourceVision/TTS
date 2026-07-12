package com.example.data

import android.content.Context
import kotlinx.coroutines.runBlocking

object PolyphonicTable {
    private var table: Map<Char, List<String>> = emptyMap()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        reload(context)
    }

    fun reload(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.appDao()
            
            runBlocking {
                val count = dao.getPresetPolyphonesCount()
                if (count == 0) {
                    val defaultList = listOf(
                        PresetPolyphoneEntity("重", "chóng,zhòng"),
                        PresetPolyphoneEntity("得", "dé,de,děi"),
                        PresetPolyphoneEntity("行", "xíng,háng"),
                        PresetPolyphoneEntity("地", "dì,de"),
                        PresetPolyphoneEntity("会", "huì,kuài"),
                        PresetPolyphoneEntity("和", "hé,hè,huó,huò"),
                        PresetPolyphoneEntity("着", "zhe,zhāo,zháo,zhuó"),
                        PresetPolyphoneEntity("看", "kàn,kān"),
                        PresetPolyphoneEntity("好", "hǎo,hào"),
                        PresetPolyphoneEntity("都", "dōu,dū"),
                        PresetPolyphoneEntity("还", "hái,huán"),
                        PresetPolyphoneEntity("发", "fā,fà"),
                        PresetPolyphoneEntity("倒", "dǎo,dào"),
                        PresetPolyphoneEntity("处", "chù,chǔ"),
                        PresetPolyphoneEntity("强", "qiáng,qiǎng,jiàng"),
                        PresetPolyphoneEntity("为", "wéi,wèi"),
                        PresetPolyphoneEntity("数", "shù,shǔ,shuò"),
                        PresetPolyphoneEntity("分", "fēn,fèn"),
                        PresetPolyphoneEntity("便", "biàn,pián"),
                        PresetPolyphoneEntity("种", "zhǒng,zhòng"),
                        PresetPolyphoneEntity("长", "cháng,zhǎng"),
                        PresetPolyphoneEntity("间", "jiān,jiàn"),
                        PresetPolyphoneEntity("假", "jiǎ,jià"),
                        PresetPolyphoneEntity("空", "kōng,kòng"),
                        PresetPolyphoneEntity("将", "jiāng,jiàng"),
                        PresetPolyphoneEntity("只", "zhǐ,zhī"),
                        PresetPolyphoneEntity("朝", "cháo,zhāo"),
                        PresetPolyphoneEntity("少", "shǎo,shào"),
                        PresetPolyphoneEntity("中", "zhōng,zhòng"),
                        PresetPolyphoneEntity("乐", "lè,yuè"),
                        PresetPolyphoneEntity("正", "zhèng,zhēng"),
                        PresetPolyphoneEntity("盛", "shèng,chéng"),
                        PresetPolyphoneEntity("差", "chà,chā,chāi,cī"),
                        PresetPolyphoneEntity("传", "chuán,zhuàn"),
                        PresetPolyphoneEntity("结", "jié,jiē"),
                        PresetPolyphoneEntity("难", "nán,nàn"),
                        PresetPolyphoneEntity("角", "jiǎo,jué"),
                        PresetPolyphoneEntity("落", "luò,là,lào"),
                        PresetPolyphoneEntity("缝", "fèng,féng"),
                        PresetPolyphoneEntity("调", "tiáo,diào"),
                        PresetPolyphoneEntity("藏", "cáng,zàng"),
                        PresetPolyphoneEntity("奇", "qí,jī"),
                        PresetPolyphoneEntity("参", "cān,shēn,cēn"),
                        PresetPolyphoneEntity("扇", "shàn,shān"),
                        PresetPolyphoneEntity("扫", "sǎo,sào"),
                        PresetPolyphoneEntity("塞", "sāi,sài,sè"),
                        PresetPolyphoneEntity("脏", "zāng,zàng"),
                        PresetPolyphoneEntity("冠", "guān,guàn"),
                        PresetPolyphoneEntity("蒙", "méng,mēng,měng"),
                        PresetPolyphoneEntity("薄", "báo,bó,bò"),
                        PresetPolyphoneEntity("模", "mó,mú"),
                        PresetPolyphoneEntity("圈", "quān,juàn,juān"),
                        PresetPolyphoneEntity("禁", "jìn,jīn"),
                        PresetPolyphoneEntity("似", "sì,shì"),
                        PresetPolyphoneEntity("剥", "bō,bāo"),
                        PresetPolyphoneEntity("担", "dān,dàn"),
                        PresetPolyphoneEntity("弹", "tán,dàn"),
                        PresetPolyphoneEntity("铺", "pū,pù"),
                        PresetPolyphoneEntity("削", "xuē,xiāo"),
                        PresetPolyphoneEntity("喝", "hē,hè"),
                        PresetPolyphoneEntity("粘", "zhān,nián"),
                        PresetPolyphoneEntity("吓", "xià,hè"),
                        PresetPolyphoneEntity("骨", "gǔ,gū"),
                        PresetPolyphoneEntity("扒", "bā,pá"),
                        PresetPolyphoneEntity("折", "zhé,shé,zhē"),
                        PresetPolyphoneEntity("宿", "sù,xiǔ,xiù"),
                        PresetPolyphoneEntity("散", "sàn,sǎn"),
                        PresetPolyphoneEntity("卡", "kǎ,qiǎ"),
                        PresetPolyphoneEntity("钻", "zuān,zuàn"),
                        PresetPolyphoneEntity("晃", "huǎng,huàng"),
                        PresetPolyphoneEntity("载", "zǎi,zài"),
                        PresetPolyphoneEntity("降", "jiàng,xiáng"),
                        PresetPolyphoneEntity("闷", "mèn,mēn"),
                        PresetPolyphoneEntity("鲜", "xiān,xiǎn"),
                        PresetPolyphoneEntity("属", "shǔ,zhǔ"),
                        PresetPolyphoneEntity("溜", "liū,liù"),
                        PresetPolyphoneEntity("奔", "bēn,bèn"),
                        PresetPolyphoneEntity("称", "chēng,chèn"),
                        PresetPolyphoneEntity("划", "huá,huà"),
                        PresetPolyphoneEntity("兴", "xīng,xìng"),
                        PresetPolyphoneEntity("宁", "níng,nìng"),
                        PresetPolyphoneEntity("舍", "shě,shè"),
                        PresetPolyphoneEntity("卷", "juǎn,juàn"),
                        PresetPolyphoneEntity("挨", "āi,ái"),
                        PresetPolyphoneEntity("哄", "hōng,hǒng,hòng"),
                        PresetPolyphoneEntity("累", "lèi,léi,lěi"),
                        PresetPolyphoneEntity("血", "xuè,xiě"),
                        PresetPolyphoneEntity("丧", "sāng,sàng"),
                        PresetPolyphoneEntity("尽", "jìn,jǐn"),
                        PresetPolyphoneEntity("校", "xiào,jiào"),
                        PresetPolyphoneEntity("没", "méi,mò"),
                        PresetPolyphoneEntity("觉", "jué,jiào"),
                        PresetPolyphoneEntity("要", "yào,yāo"),
                        PresetPolyphoneEntity("解", "jiě,xiè,jiè"),
                        PresetPolyphoneEntity("应", "yīng,yìng"),
                        PresetPolyphoneEntity("切", "qiē,qiè"),
                        PresetPolyphoneEntity("咽", "yān,yàn,yè"),
                        PresetPolyphoneEntity("斗", "dòu,dǒu"),
                        PresetPolyphoneEntity("挑", "tiāo,tiǎo"),
                        PresetPolyphoneEntity("泊", "bó,pō"),
                        PresetPolyphoneEntity("旋", "xuán,xuàn"),
                        PresetPolyphoneEntity("涨", "zhǎng,zhàng"),
                        PresetPolyphoneEntity("弄", "nòng,lòng"),
                        PresetPolyphoneEntity("菲", "fēi,fěi"),
                        PresetPolyphoneEntity("艾", "ài,yì"),
                        PresetPolyphoneEntity("尾", "wěi,yǐ"),
                        PresetPolyphoneEntity("靓", "jìng,liàng"),
                        PresetPolyphoneEntity("囤", "dùn,tún"),
                        PresetPolyphoneEntity("曾", "céng,zēng"),
                        PresetPolyphoneEntity("的", "de,dí,dì"),
                        PresetPolyphoneEntity("别", "bié,biè"),
                        PresetPolyphoneEntity("畜", "chù,xù"),
                        PresetPolyphoneEntity("合", "hé,gě"),
                        PresetPolyphoneEntity("杠", "gàng,gāng"),
                        PresetPolyphoneEntity("嚼", "jiáo,jué,jiào"),
                        PresetPolyphoneEntity("匙", "chí,shi"),
                        PresetPolyphoneEntity("辟", "bì,pì"),
                        PresetPolyphoneEntity("巷", "xiàng,hàng"),
                        PresetPolyphoneEntity("轴", "zhóu,zhòu"),
                        PresetPolyphoneEntity("坷", "kē,kě"),
                        PresetPolyphoneEntity("颤", "chàn,zhàn"),
                        PresetPolyphoneEntity("夹", "jiā,jiá,xiá")
                    )
                    dao.insertPresetPolyphones(defaultList)
                }
                
                val entities = dao.getAllPresetPolyphones()
                val newTable = mutableMapOf<Char, List<String>>()
                entities.forEach { entity ->
                    val ch = entity.char.trim().firstOrNull()
                    if (ch != null) {
                        val readings = entity.readings.split(",").map { it.trim() }
                        newTable[ch] = readings
                    }
                }
                table = newTable
                isLoaded = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun candidatesOf(ch: Char): List<String>? = table[ch]
    fun isPolyphonic(ch: Char): Boolean = table.containsKey(ch)
    fun getAllPolyphonicChars(): Set<Char> = table.keys
}
