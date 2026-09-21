package com.example.util

import com.example.model.VideoItem
import java.util.Locale

/**
 * High-precision recognition and alias mapping engine for adult performers,
 * JAV actresses (Romaji & Japanese Kanji/Kana), and Western models.
 */
object AdultModelMatcher {

    data class ModelEntry(
        val primaryName: String,
        val aliases: List<String>,
        val isJav: Boolean,
        val imageUrl: String? = null
    )

    fun getAllKnownModels(): List<ModelEntry> = MODEL_DATABASE

    private val MODEL_DATABASE = listOf(
        // JAV Top Actresses with Romaji, Kana, Kanji variants
        ModelEntry("Yua Mikami", listOf("yua mikami", "mikami yua", "三上悠亜", "みかみゆあ"), true),
        ModelEntry("Eimi Fukada", listOf("eimi fukada", "fukada eimi", "深田えいみ", "ふかだえいみ"), true),
        ModelEntry("Saika Kawakita", listOf("saika kawakita", "kawakita saika", "河北彩花", "河北彩伽"), true),
        ModelEntry("Karen Kaede", listOf("karen kaede", "kaede karen", "楓カレン", "かえでかれん"), true),
        ModelEntry("Yui Hatano", listOf("yui hatano", "hatano yui", "波多野結衣", "はたのゆい"), true),
        ModelEntry("Ria Yamate", listOf("ria yamate", "yamate ria", "山手梨愛"), true),
        ModelEntry("Minami Aizawa", listOf("minami aizawa", "aizawa minami", "相沢みなみ"), true),
        ModelEntry("Arina Hashimoto", listOf("arina hashimoto", "hashimoto arina", "橋本ありな"), true),
        ModelEntry("Tsukasa Aoi", listOf("tsukasa aoi", "aoi tsukasa", "葵つかさ"), true),
        ModelEntry("Moe Amatsuka", listOf("moe amatsuka", "amatsuka moe", "天使もえ"), true),
        ModelEntry("Remu Suzumori", listOf("remu suzumori", "suzumori remu", "涼森れむ"), true),
        ModelEntry("Miru", listOf("sakamichi miru", "坂道みる"), true),
        ModelEntry("Kana Momonogi", listOf("kana momonogi", "momonogi kana", "桃乃木かな"), true),
        ModelEntry("Julia", listOf("julia", "ジュリア", "京香julia"), true),
        ModelEntry("Ai Uehara", listOf("ai uehara", "uehara ai", "上原亜衣"), true),
        ModelEntry("Sora Aoi", listOf("sora aoi", "aoi sora", "蒼井そら"), true),
        ModelEntry("Asuka Kirara", listOf("asuka kirara", "kirara asuka", "明日花キララ"), true),
        ModelEntry("Rara Anzai", listOf("rara anzai", "anzai rara", "rion", "安齋らら", "宇都宮しおん"), true),
        ModelEntry("Mana Sakura", listOf("mana sakura", "sakura mana", "佐倉まな"), true),
        ModelEntry("Kaho Shibuya", listOf("kaho shibuya", "shibuya kaho", "澁谷果歩"), true),
        ModelEntry("Maria Ozawa", listOf("maria ozawa", "ozawa maria", "小澤マリア"), true),
        ModelEntry("Hitomi Tanaka", listOf("hitomi tanaka", "tanaka hitomi", "田中瞳"), true),
        ModelEntry("Tina Yuzuki", listOf("tina yuzuki", "yuzuki tina", "rio", "柚木ティナ"), true),
        ModelEntry("Akari Mitani", listOf("akari mitani", "mitani akari", "三谷あかり"), true),
        ModelEntry("Riri Nanatsumori", listOf("riri nanatsumori", "nanatsumori riri", "七ツ森りり"), true),
        ModelEntry("Yume Nikaido", listOf("yume nikaido", "nikaido yume", "二階堂夢"), true),
        ModelEntry("Nozomi Ishihara", listOf("nozomi ishihara", "ishihara nozomi", "石原希望"), true),
        ModelEntry("Suzu Honjo", listOf("suzu honjo", "honjo suzu", "本庄鈴"), true),
        ModelEntry("Mio Ishikawa", listOf("mio ishikawa", "ishikawa mio", "石川澪"), true),
        ModelEntry("Aika Yamagishi", listOf("aika yamagishi", "yamagishi aika", "山岸逢花"), true),
        ModelEntry("Hibiki Otsuki", listOf("hibiki otsuki", "otsuki hibiki", "大槻ひびき"), true),
        ModelEntry("Tsubasa Amami", listOf("tsubasa amami", "amami tsubasa", "天海つばさ"), true),
        ModelEntry("Rei Kamiki", listOf("rei kamiki", "kamiki rei", "神木麗"), true),
        ModelEntry("Waka Misono", listOf("waka misono", "misono waka", "美園和花"), true),
        ModelEntry("Ena Satsuki", listOf("ena satsuki", "satsuki ena", "五月恵奈"), true),
        ModelEntry("Nanami Kawakami", listOf("nanami kawakami", "kawakami nanami", "河合あすな"), true),
        ModelEntry("Shizuku Natsume", listOf("shizuku natsume", "natsume shizuku", "夏目響"), true),
        ModelEntry("Aoi Kururugi", listOf("aoi kururugi", "kururugi aoi", "枢木あおい"), true),
        ModelEntry("Mei Satsuki", listOf("mei satsuki", "satsuki mei", "紗月芽衣"), true),
        ModelEntry("Yura Kano", listOf("yura kano", "kano yura", "架乃ゆら"), true),
        ModelEntry("Rion Sakamoto", listOf("rion sakamoto", "坂本リオン"), true),
        ModelEntry("Hiyori Ogawa", listOf("hiyori ogawa", "小川ひより"), true),
        ModelEntry("Riri Koyama", listOf("riri koyama", "小山りり"), true),
        ModelEntry("Yui Nagase", listOf("yui nagase", "長瀬唯"), true),
        ModelEntry("Rin Natsume", listOf("rin natsume", "夏目りん"), true),
        ModelEntry("Akiho Yoshizawa", listOf("akiho yoshizawa", "吉沢明歩"), true),
        ModelEntry("Kurea Hasumi", listOf("kurea hasumi", "蓮実クレア"), true),
        ModelEntry("Mao Hamasaki", listOf("mao hamasaki", "浜崎真緒"), true),
        ModelEntry("Ruka Kanae", listOf("ruka kanae", "叶愛るか"), true),
        ModelEntry("Nanami Matsumoto", listOf("nanami matsumoto", "松本菜奈実"), true),
        ModelEntry("Shoko Takahashi", listOf("shoko takahashi", "高橋しょう子"), true),
        ModelEntry("Meguri", listOf("meguri", "藤浦めぐ", "めぐり"), true),
        ModelEntry("Ayami Shunka", listOf("ayami shunka", "shunka ayami", "あやみ旬果"), true),

        // Western Popular Adult Models
        ModelEntry("Angela White", listOf("angela white"), false),
        ModelEntry("Eva Elfie", listOf("eva elfie"), false),
        ModelEntry("Mia Malkova", listOf("mia malkova"), false),
        ModelEntry("Lana Rhoades", listOf("lana rhoades"), false),
        ModelEntry("Riley Reid", listOf("riley reid"), false),
        ModelEntry("Abella Danger", listOf("abella danger"), false),
        ModelEntry("Sasha Grey", listOf("sasha grey"), false),
        ModelEntry("Kendra Lust", listOf("kendra lust"), false),
        ModelEntry("Gabbie Carter", listOf("gabbie carter"), false),
        ModelEntry("Sweetie Fox", listOf("sweetie fox"), false),
        ModelEntry("Alina Lopez", listOf("alina lopez"), false),
        ModelEntry("Liya Silver", listOf("liya silver"), false),
        ModelEntry("Brandi Love", listOf("brandi love"), false),
        ModelEntry("Autumn Falls", listOf("autumn falls"), false),
        ModelEntry("Nicole Aniston", listOf("nicole aniston"), false),
        ModelEntry("Emily Willis", listOf("emily willis"), false),
        ModelEntry("Lena Paul", listOf("lena paul"), false),
        ModelEntry("Mia Khalifa", listOf("mia khalifa"), false),
        ModelEntry("Cory Chase", listOf("cory chase"), false),
        ModelEntry("Alexis Texas", listOf("alexis texas"), false),
        ModelEntry("Lisa Ann", listOf("lisa ann"), false),
        ModelEntry("Johnny Sins", listOf("johnny sins"), false),
        ModelEntry("Rae Lil Black", listOf("rae lil black"), false),
        ModelEntry("Blake Blossom", listOf("blake blossom"), false),
        ModelEntry("Maitland Ward", listOf("maitland ward"), false),
        ModelEntry("Dillion Harper", listOf("dillion harper"), false),
        ModelEntry("Gianna Michaels", listOf("gianna michaels"), false),
        ModelEntry("Tori Black", listOf("tori black"), false),
        ModelEntry("Gia Paige", listOf("gia paige"), false),
        ModelEntry("Adriana Chechik", listOf("adriana chechik"), false),
        ModelEntry("Kenzie Reeves", listOf("kenzie reeves"), false),
        ModelEntry("Violet Myers", listOf("violet myers"), false),
        ModelEntry("Elsa Jean", listOf("elsa jean"), false),
        ModelEntry("Skylar Vox", listOf("skylar vox"), false),
        ModelEntry("Leah Gotti", listOf("leah gotti"), false),
        ModelEntry("Kelsi Monroe", listOf("kelsi monroe"), false),
        ModelEntry("Alura Jenson", listOf("alura jenson"), false),
        ModelEntry("Vicki Chase", listOf("vicki chase"), false),
        ModelEntry("Cherie DeVille", listOf("cherie deville"), false),
        ModelEntry("Peta Jensen", listOf("peta jensen"), false),
        ModelEntry("Janice Griffith", listOf("janice griffith"), false),
        ModelEntry("Natasha Nice", listOf("natasha nice"), false),
        ModelEntry("Sara Jay", listOf("sara jay"), false),
        ModelEntry("Phoenix Marie", listOf("phoenix marie"), false),
        ModelEntry("Kagney Linn Karter", listOf("kagney linn karter"), false),
        ModelEntry("Dani Daniels", listOf("dani daniels"), false),
        ModelEntry("Karma Rx", listOf("karma rx"), false),
        ModelEntry("Kira Noir", listOf("kira noir"), false),
        ModelEntry("Lacy Lennon", listOf("lacy lennon"), false),
        ModelEntry("Scarlit Scandal", listOf("scarlit scandal"), false),
        ModelEntry("Little Caprice", listOf("little caprice"), false),
        ModelEntry("Gina Valentina", listOf("gina valentina"), false),
        ModelEntry("Kenna James", listOf("kenna james"), false),
        ModelEntry("Anya Olsen", listOf("anya olsen"), false)
    )

    /**
     * Finds if a query references any known adult model.
     */
    fun findModel(query: String): ModelEntry? {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return null

        return MODEL_DATABASE.firstOrNull { entry ->
            entry.aliases.any { alias ->
                q == alias || q.contains(alias) || alias.contains(q)
            }
        }
    }

    /**
     * Checks if text contains any recognized model name.
     */
    fun isModelInText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return MODEL_DATABASE.any { entry ->
            entry.aliases.any { alias ->
                if (alias.length >= 3) lower.contains(alias) else false
            }
        }
    }

    /**
     * Checks if a query is adult-oriented based on model names, studios, or general adult terms.
     */
    fun isAdultQuery(query: String): Boolean {
        if (findModel(query) != null) return true
        val q = query.lowercase(Locale.ROOT)
        val keywords = listOf(
            "jav", "hentai", "milf", "porn", "xxx", "nsfw", "erotic", "creampie", "blowjob",
            "cumshot", "deepthroat", "gangbang", "threesome", "anal", "masturbation",
            "uncensored", "censored", "fc2", "heyzo", "caribbeancom", "1pondo", "sod",
            "s1", "moodyz", "ideapocket", "attackers", "prestige", "soft on demand",
            "tokyo-hot", "spankbang", "eporner", "pornhub", "xvideos", "xhamster",
            "subtitles", "chinese sub", "english sub", "jav sub"
        )
        return keywords.any { q.contains(it) }
    }

    /**
     * Determines whether a VideoItem matches the detected model.
     */
    fun matchesModel(item: VideoItem, model: ModelEntry): Boolean {
        val title = item.title.lowercase(Locale.ROOT)
        val channel = (item.uploaderName ?: "").lowercase(Locale.ROOT)
        val desc = (item.description ?: "").lowercase(Locale.ROOT)
        val combined = "$title $channel $desc"

        return model.aliases.any { alias ->
            combined.contains(alias)
        }
    }
}
