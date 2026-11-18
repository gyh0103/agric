package com.example.ty.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首次启动时写入河西走廊常用作物与病害样例，之后全部由后台维护。
 */
@Component
@RequiredArgsConstructor
public class CatalogSeeder implements ApplicationRunner {

    private final CropProfileRepository crops;
    private final DiseaseProfileRepository diseases;

    @Override
    public void run(ApplicationArguments args) {
        if (crops.count() == 0) crops.saveAll(cropSeeds());
        if (diseases.count() == 0) diseases.saveAll(diseaseSeeds());
    }

    private static CropProfile crop(String id, String name, String aliases, String category,
                                    String stages, String visionTasks, String notes, int order) {
        return CropProfile.builder().id(id).name(name).aliases(aliases).category(category)
                .stages(stages).visionTasks(visionTasks).notes(notes).enabled(true).sortOrder(order).build();
    }

    private static List<CropProfile> cropSeeds() {
        return List.of(
                crop("seed-corn", "制种玉米", "玉米,制种玉米,郑单958,先玉", "大田", "苗期,拔节期,抽雄期,灌浆期",
                        "leaf:叶片症状,canopy:冠层长势,head:穗部检测", "张掖国家级玉米制种基地主栽，注意去雄与隔离带管理", 10),
                crop("potato", "马铃薯", "土豆,洋芋,陇薯", "大田", "苗期,现蕾期,块茎膨大期",
                        "leaf:叶片症状", "山丹、民乐冷凉区主栽，重点防晚疫病", 20),
                crop("grape", "酿酒葡萄", "葡萄,敦煌无核白,赤霞珠", "果园", "萌芽期,开花期,果实膨大期,转色期",
                        "leaf:叶片症状,fruit_bruise:果实损伤", "武威、张掖酿酒葡萄产区，注意霜霉病与日灼", 30),
                crop("goji", "枸杞", "宁夏枸杞,枸杞子", "果园", "萌芽期,花果期,夏果采收期",
                        "leaf:叶片症状,fruit_freshness:果实新鲜度", "景泰、靖远枸杞产区，重点防瘿螨与白粉病", 40),
                crop("melon", "蜜瓜", "金红宝蜜瓜,民勤蜜瓜,甜瓜,白兰瓜", "大田", "苗期,伸蔓期,膨大期,成熟期",
                        "leaf:叶片症状,fruit_ripeness:果实成熟度", "民勤、瓜州蜜瓜产区，注意白粉病与裂瓜", 50),
                crop("jujube", "红枣", "临泽小枣,金丝小枣,枣", "果园", "萌芽期,开花期,果实膨大期,成熟期",
                        "leaf:叶片症状,fruit_bruise:果实损伤", "临泽、高台红枣产区，注意枣瘿蚊与炭疽病", 60),
                crop("vegetable", "高原夏菜", "甘蓝,西兰花,娃娃菜,莴笋,芹菜", "大田", "苗期,莲座期,结球期,采收期",
                        "leaf:叶片症状", "张掖、兰州高原夏菜，重点防菜青虫与软腐病", 70),
                crop("wheat", "小麦", "旱碱麦,春小麦,冬小麦", "大田", "分蘖期,拔节期,抽穗期,灌浆期",
                        "leaf:叶片症状,canopy:冠层水分表型,head:麦穗检测", "河西灌区春小麦，注意条锈病与干热风", 80),
                crop("rapeseed", "油菜", "陇油16油菜,制种油菜,油菜籽", "大田", "苗期,蕾薹期,花期,角果期",
                        "leaf:叶片症状", "山丹、民乐油菜制种基地", 90),
                crop("tomato", "番茄", "硬粉番茄,西红柿", "设施", "苗期,开花坐果期,结果盛期",
                        "leaf:叶片症状,fruit_ripeness:果实成熟度", "日光温室主栽，注意灰霉病与白粉虱", 100),
                crop("cucumber", "黄瓜", "津优黄瓜,水果黄瓜", "设施", "苗期,结瓜期,盛瓜期",
                        "leaf:叶片症状", "日光温室主栽，注意霜霉病与靶斑病", 110),
                crop("pepper", "辣椒", "陇椒五号,陇椒,甜椒", "设施", "苗期,开花坐果期,结果期",
                        "leaf:叶片症状", "日光温室主栽，注意疫病与蓟马", 120),
                crop("strawberry", "草莓", "红颜草莓,草莓", "设施", "缓苗期,开花期,膨果期,采收期",
                        "leaf:叶片症状,fruit_ripeness:果实成熟度", "日光温室采摘，注意白粉病与灰霉病", 130),
                crop("leafy", "叶菜", "水培油麦菜,油麦菜,生菜,菠菜", "设施", "幼苗期,快速生长期,采收期",
                        "leaf:叶片症状", "水培/基质栽培，注意根腐与缺素", 140)
        );
    }

    private static DiseaseProfile disease(String crop, String name, String aliases, String symptoms,
                                          String advice, String severity, int order) {
        return DiseaseProfile.builder().crop(crop).name(name).aliases(aliases).symptoms(symptoms)
                .advice(advice).severity(severity).enabled(true).sortOrder(order).build();
    }

    private static List<DiseaseProfile> diseaseSeeds() {
        return List.of(
                disease("制种玉米", "玉米大斑病", "leaf_blight,大斑",
                        "叶片出现长梭形灰褐色病斑，边缘暗褐，潮湿时病部有黑色霉层",
                        "选用抗病品种并清除病残体；发病初期用吡唑醚菌酯或苯醚甲环唑喷雾，7~10 天一次，连喷 2 次", "中度", 10),
                disease("制种玉米", "玉米锈病", "rust,条锈",
                        "叶片散生黄褐色至红褐色夏孢子堆，破裂后散出铁锈色粉末",
                        "避免偏施氮肥；发病初期用三唑酮或戊唑醇喷雾，重点喷施上部叶片", "中度", 20),
                disease("制种玉米", "玉米螟", "bore,钻心虫",
                        "心叶出现排孔，茎秆或穗轴有蛀孔和虫粪",
                        "心叶末期施用Bt颗粒剂或氯虫苯甲酰胺，注意去雄期安全间隔", "中度", 30),
                disease("马铃薯", "马铃薯晚疫病", "late_blight,晚疫",
                        "叶尖叶缘出现水浸状暗绿斑，扩展后变褐，背面有白色霉轮",
                        "雨后及时通风降湿；发病前预防性喷施代森锰锌，发病初期用烯酰吗啉或氟菌·霜霉威", "重度", 10),
                disease("番茄", "番茄灰霉病", "grey_mold,灰霉",
                        "果实和叶片出现水渍状软腐，表面覆盖灰色绒毛状霉层",
                        "控制棚内湿度，及时摘除病果病叶；用腐霉利或嘧霉胺烟剂，避免阴天喷水", "中度", 10),
                disease("番茄", "番茄白粉虱", "whitefly,粉虱",
                        "叶背聚集白色小虫，叶片褪绿发黄，有蜜露和煤污",
                        "悬挂黄色粘虫板；用噻虫嗪或螺虫乙酯灌根/喷雾，注意轮换用药", "中度", 20),
                disease("番茄", "番茄细菌性斑点病", "bacterial_spot,斑点",
                        "叶片出现褐色小斑点，周围有黄晕，果实表面形成疮痂状斑",
                        "清除病残体，避免叶面结露；发病初期用噻菌铜或春雷霉素喷雾", "中度", 30),
                disease("黄瓜", "黄瓜霜霉病", "downy_mildew,霜霉",
                        "叶正面出现受叶脉限制的多角形黄斑，背面有灰黑色霉层",
                        "加强通风、降低湿度；用烯酰吗啉或霜脲·锰锌交替喷雾，重点喷叶背", "中度", 10),
                disease("黄瓜", "黄瓜靶斑病", "target_spot,靶斑",
                        "叶片出现圆形或不规则病斑，中央灰白、边缘褐色，形似靶心",
                        "及时摘除下部病叶；用苯甲·嘧菌酯或氟吡菌酰胺喷雾", "中度", 20),
                disease("辣椒", "辣椒疫病", "phytophthora_blight,疫病",
                        "茎基部出现暗褐色水渍状病斑，整株萎蔫，果实软腐",
                        "避免大水漫灌，起垄栽培；发病初期用甲霜·锰锌灌根并结合喷雾", "重度", 10),
                disease("草莓", "草莓白粉病", "powdery_mildew,白粉",
                        "叶片、果面覆盖白色粉状物，叶片向上卷曲，果实畸形",
                        "棚内湿度控制在 70% 以下；用醚菌酯或氟硅唑喷雾，采收期注意安全间隔", "中度", 10),
                disease("草莓", "草莓灰霉病", "grey_mold,灰霉",
                        "果实软化并长出灰色霉层，多从残花部位开始",
                        "及时清除病果残花，加强通风；用腐霉利烟剂熏蒸", "中度", 20),
                disease("酿酒葡萄", "葡萄霜霉病", "downy_mildew,霜霉",
                        "叶片出现油渍状黄斑，背面有白色霜状霉层，后期叶片干枯",
                        "雨后及时喷药，用波尔多液预防，发病期用烯酰吗啉或氟吡菌胺", "重度", 10),
                disease("酿酒葡萄", "葡萄白粉病", "powdery_mildew,白粉",
                        "叶片和果面出现白色粉状霉层，果实停止生长甚至开裂",
                        "控制副梢郁闭；用硫制剂或戊唑醇喷雾，避开高温时段", "中度", 20),
                disease("枸杞", "枸杞白粉病", "powdery_mildew,白粉",
                        "叶片和嫩梢覆盖白色粉层，叶片皱缩变黄",
                        "合理修剪增加通风；用三唑酮或醚菌酯喷雾，采收前注意安全间隔", "中度", 10),
                disease("蜜瓜", "蜜瓜白粉病", "powdery_mildew,白粉",
                        "叶片出现白色粉斑，扩展后整叶覆白粉，叶片枯黄",
                        "避免密植，滴灌控湿；用醚菌酯或苯醚甲环唑喷雾", "中度", 10),
                disease("红枣", "枣炭疽病", "anthracnose,炭疽",
                        "果实出现红褐色凹陷病斑，病部有轮纹状小黑点",
                        "清除病果病枝；萌芽前喷石硫合剂，生长期用咪鲜胺或苯醚甲环唑", "中度", 10),
                disease("小麦", "小麦条锈病", "stripe_rust,条锈",
                        "叶片出现鲜黄色狭长条状孢子堆，沿叶脉排列",
                        "发现中心病株立即防治；用三唑酮或戊唑醇喷雾，7~10 天一次", "重度", 10),
                disease("油菜", "油菜菌核病", "sclerotinia,菌核",
                        "茎秆出现水渍状病斑并腐烂，后期可见黑色鼠粪状菌核",
                        "合理轮作、开沟排水；花期用菌核净或咪鲜胺喷雾", "中度", 10)
        );
    }
}
