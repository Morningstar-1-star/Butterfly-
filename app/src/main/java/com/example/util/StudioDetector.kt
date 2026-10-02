package com.example.util

object StudioDetector {

    fun detectStudio(title: String, isTv: Boolean): String {
        val lower = title.lowercase().trim()
        val hash = kotlin.math.abs(title.hashCode())

        return when {
            // --- MARVEL STUDIOS / MCU ---
            lower.contains("iron man") || lower.contains("avengers") || lower.contains("marvel") ||
            lower.contains("captain america") || lower.contains("thor") || lower.contains("black panther") ||
            lower.contains("doctor strange") || lower.contains("ant-man") || lower.contains("guardians of the galaxy") ||
            lower.contains("deadpool") || lower.contains("wolverine") || lower.contains("x-men") ||
            lower.contains("fantastic four") || lower.contains("loki") || lower.contains("wandavision") ||
            lower.contains("moon knight") || lower.contains("shang-chi") || lower.contains("eternals") ||
            lower.contains("secret invasion") || lower.contains("the marvels") || lower.contains("she-hulk") ||
            lower.contains("blade") || lower.contains("thunderbolts") || lower.contains("agatha") ||
            lower.contains("what if") || lower.contains("daredevil") || lower.contains("punisher") ||
            lower.contains("hawkeye") || lower.contains("mcu") || lower.contains("stan lee") -> "Marvel Studios"

            // --- DC STUDIOS / DC COMICS ---
            lower.contains("superman") || lower.contains("batman") || lower.contains("the dark knight") ||
            lower.contains("dark knight") || lower.contains("joker") || lower.contains("dc studios") ||
            lower.contains("dc comics") || lower.contains("wonder woman") || lower.contains("aquaman") ||
            lower.contains("the flash") || lower.contains("flash ") || lower.contains("justice league") ||
            lower.contains("suicide squad") || lower.contains("peacemaker") || lower.contains("shazam") ||
            lower.contains("black adam") || lower.contains("harley quinn") || lower.contains("blue beetle") ||
            lower.contains("catwoman") || lower.contains("green lantern") || lower.contains("supergirl") ||
            lower.contains("watchmen") || lower.contains("penguin") || lower.contains("gotham") ||
            lower.contains("swamp thing") || lower.contains("sandman") || lower.contains("titans") -> "DC Studios"

            // --- WARNER BROS. PICTURES ---
            lower.contains("harry potter") || lower.contains("fantastic beasts") || lower.contains("matrix") ||
            lower.contains("wonka") || lower.contains("barbie") || lower.contains("beetlejuice") ||
            lower.contains("mad max") || lower.contains("furiosa") || lower.contains("tenet") ||
            lower.contains("dune") || lower.contains("godzilla") || lower.contains("kong") ||
            lower.contains("monsterverse") || lower.contains("the conjuring") || lower.contains("annabelle") ||
            lower.contains("the nun") || lower.contains("casablanca") || lower.contains("twister") ||
            lower.contains("warner") || lower.contains("wb ") || lower.contains("blade runner") -> "Warner Bros. Pictures"

            // --- PIXAR ANIMATION STUDIOS ---
            lower.contains("toy story") || lower.contains("inside out") || lower.contains("finding nemo") ||
            lower.contains("finding dory") || lower.contains("cars ") || lower.contains("cars 2") ||
            lower.contains("cars 3") || lower.contains("incredibles") || lower.contains("the incredibles") ||
            lower.contains("monsters inc") || lower.contains("monsters university") || lower.contains("up ") ||
            lower.contains("wall-e") || lower.contains("ratatouille") || lower.contains("coco") ||
            lower.contains("soul ") || lower.contains("turning red") || lower.contains("elemental") ||
            lower.contains("lightyear") || lower.contains("luca") || lower.contains("onward") ||
            lower.contains("brave") || lower.contains("a bug's life") || lower.contains("good dinosaur") ||
            lower.contains("elio") || lower.contains("pixar") -> "Pixar Animation Studios"

            // --- WALT DISNEY PICTURES / DISNEY+ ---
            lower.contains("frozen") || lower.contains("moana") || lower.contains("lion king") ||
            lower.contains("the lion king") || lower.contains("aladdin") || lower.contains("beauty and the beast") ||
            lower.contains("cinderella") || lower.contains("mulan") || lower.contains("tangled") ||
            lower.contains("zootopia") || lower.contains("encanto") || lower.contains("wish") ||
            lower.contains("little mermaid") || lower.contains("the little mermaid") || lower.contains("pinocchio") ||
            lower.contains("peter pan") || lower.contains("snow white") || lower.contains("bambi") ||
            lower.contains("dumbo") || lower.contains("jungle book") || lower.contains("the jungle book") ||
            lower.contains("pirates of the caribbean") || lower.contains("national treasure") || lower.contains("tron") ||
            lower.contains("hocus pocus") || lower.contains("nightmare before christmas") || lower.contains("cruella") ||
            lower.contains("maleficent") || lower.contains("wreck-it ralph") || lower.contains("big hero 6") ||
            lower.contains("raya") || lower.contains("princess and the frog") || lower.contains("mickey mouse") ||
            lower.contains("disney") || lower.contains("walt disney") -> "Walt Disney Pictures"

            // --- LUCASFILM ---
            lower.contains("star wars") || lower.contains("mandalorian") || lower.contains("the mandalorian") ||
            lower.contains("ahsoka") || lower.contains("andor") || lower.contains("obi-wan") ||
            lower.contains("boba fett") || lower.contains("bad batch") || lower.contains("clone wars") ||
            lower.contains("skywalker") || lower.contains("indiana jones") || lower.contains("willow") ||
            lower.contains("rogue one") || lower.contains("solo: a star wars") || lower.contains("lucasfilm") -> "Lucasfilm"

            // --- 20TH CENTURY STUDIOS / SEARCHLIGHT ---
            lower.contains("avatar") || lower.contains("planet of the apes") || lower.contains("kingdom of the planet") ||
            lower.contains("alien") || lower.contains("alien: romulus") || lower.contains("predator") ||
            lower.contains("prey") || lower.contains("die hard") || lower.contains("titanic") ||
            lower.contains("home alone") || lower.contains("ice age") || lower.contains("night at the museum") ||
            lower.contains("kingsman") || lower.contains("bohemian rhapsody") || lower.contains("the martian") ||
            lower.contains("grand budapest hotel") || lower.contains("poor things") || lower.contains("shape of water") ||
            lower.contains("nomadland") || lower.contains("jojo rabbit") || lower.contains("birdman") ||
            lower.contains("the menu") || lower.contains("free guy") || lower.contains("maze runner") ||
            lower.contains("logan") || lower.contains("20th century") || lower.contains("fox") ||
            lower.contains("searchlight") -> "20th Century Studios"

            // --- UNIVERSAL PICTURES / ILLUMINATION / DREAMWORKS ---
            lower.contains("jurassic") || lower.contains("fast & furious") || lower.contains("fast and furious") ||
            lower.contains("furious 7") || lower.contains("fate of the furious") || lower.contains("hobbs & shaw") ||
            lower.contains("despicable me") || lower.contains("minions") || lower.contains("super mario") ||
            lower.contains("shrek") || lower.contains("kung fu panda") || lower.contains("how to train your dragon") ||
            lower.contains("madagascar") || lower.contains("puss in boots") || lower.contains("trolls") ||
            lower.contains("sing ") || lower.contains("sing 2") || lower.contains("secret life of pets") ||
            lower.contains("wicked") || lower.contains("the grinch") || lower.contains("lorax") ||
            lower.contains("oppenheimer") || lower.contains("back to the future") || lower.contains("jaws") ||
            lower.contains("e.t.") || lower.contains("gladiator") || lower.contains("mamma mia") ||
            lower.contains("get out") || lower.contains("us ") || lower.contains("nope") ||
            lower.contains("twisters") || lower.contains("five nights at freddy") || lower.contains("fnaf") ||
            lower.contains("m3gan") || lower.contains("the purge") || lower.contains("bourne") ||
            lower.contains("the fall guy") || lower.contains("speak no evil") || lower.contains("nosferatu") ||
            lower.contains("wolf man") || lower.contains("universal") || lower.contains("dreamworks") ||
            lower.contains("illumination") -> "Universal Pictures"

            // --- PARAMOUNT PICTURES / NICKELODEON ---
            lower.contains("top gun") || lower.contains("mission: impossible") || lower.contains("mission impossible") ||
            lower.contains("transformers") || lower.contains("sonic the hedgehog") || lower.contains("sonic") ||
            lower.contains("gladiator ii") || lower.contains("a quiet place") || lower.contains("quiet place") ||
            lower.contains("scream") || lower.contains("star trek") || lower.contains("the godfather") ||
            lower.contains("godfather") || lower.contains("forrest gump") || lower.contains("saving private ryan") ||
            lower.contains("spongebob") || lower.contains("sponge bob") || lower.contains("paw patrol") ||
            lower.contains("ninja turtles") || lower.contains("tmnt") || lower.contains("mean girls") ||
            lower.contains("smile") || lower.contains("dungeons & dragons") || lower.contains("babylon") ||
            lower.contains("wolf of wall street") || lower.contains("shutter island") || lower.contains("yellowstone") ||
            lower.contains("south park") || lower.contains("paramount") || lower.contains("nickelodeon") -> "Paramount Pictures"

            // --- SONY PICTURES / COLUMBIA PICTURES ---
            lower.contains("spider-verse") || lower.contains("into the spider-verse") || lower.contains("across the spider-verse") ||
            lower.contains("venom") || lower.contains("jumanji") || lower.contains("ghostbusters") ||
            lower.contains("bad boys") || lower.contains("men in black") || lower.contains("uncharted") ||
            lower.contains("gran turismo") || lower.contains("karate kid") || lower.contains("cobra kai") ||
            lower.contains("equalizer") || lower.contains("hotel transylvania") || lower.contains("smurfs") ||
            lower.contains("resident evil") || lower.contains("underworld") || lower.contains("zombieland") ||
            lower.contains("21 jump street") || lower.contains("22 jump street") || lower.contains("social network") ||
            lower.contains("once upon a time in hollywood") || lower.contains("little women") || lower.contains("baby driver") ||
            lower.contains("da vinci code") || lower.contains("casino royale") || lower.contains("skyfall") ||
            lower.contains("spectre") || lower.contains("no time to die") || lower.contains("anyone but you") ||
            lower.contains("it ends with us") || lower.contains("madame web") || lower.contains("kraven") ||
            lower.contains("sony pictures") || lower.contains("columbia pictures") || lower.contains("tristar") -> "Sony Pictures"

            // --- LIONSGATE FILMS ---
            lower.contains("hunger games") || lower.contains("the hunger games") || lower.contains("songbirds and snakes") ||
            lower.contains("john wick") || lower.contains("ballerina") || lower.contains("twilight") ||
            lower.contains("saw ") || lower.contains("saw x") || lower.contains("spiral") ||
            lower.contains("now you see me") || lower.contains("la la land") || lower.contains("knives out") ||
            lower.contains("expendables") || lower.contains("the expendables") || lower.contains("rambo") ||
            lower.contains("hacksaw ridge") || lower.contains("american psycho") || lower.contains("borderlands") ||
            lower.contains("the crow") || lower.contains("boy kills world") || lower.contains("ungentlemanly warfare") ||
            lower.contains("lionsgate") || lower.contains("summit") -> "Lionsgate Films"

            // --- A24 ---
            lower.contains("everything everywhere") || lower.contains("civil war") || lower.contains("the whale") ||
            lower.contains("talk to me") || lower.contains("hereditary") || lower.contains("midsommar") ||
            lower.contains("uncut gems") || lower.contains("the witch") || lower.contains("lady bird") ||
            lower.contains("moonlight") || lower.contains("beef") || lower.contains("past lives") ||
            lower.contains("the iron claw") || lower.contains("iron claw") || lower.contains("priscilla") ||
            lower.contains("zone of interest") || lower.contains("beau is afraid") || lower.contains("pearl") ||
            lower.contains("maxxxine") || lower.contains("heretic") || lower.contains("we live in time") ||
            lower.contains("sing sing") || lower.contains("ex machina") || lower.contains("the lobster") ||
            lower.contains("the lighthouse") || lower.contains("the green knight") || lower.contains("green knight") ||
            lower.contains("a24") -> "A24"

            // --- LEGENDARY ENTERTAINMENT ---
            lower.contains("pacific rim") || lower.contains("detective pikachu") || lower.contains("enola holmes") ||
            lower.contains("interstellar") || lower.contains("inception") || lower.contains("300") ||
            lower.contains("warcraft") || lower.contains("legendary") -> "Legendary Entertainment"

            // --- BLUMHOUSE PRODUCTIONS ---
            lower.contains("the black phone") || lower.contains("paranormal activity") || lower.contains("insidious") ||
            lower.contains("sinister") || lower.contains("halloween ends") || lower.contains("halloween kills") ||
            lower.contains("invisible man") || lower.contains("split") || lower.contains("glass") ||
            lower.contains("whiplash") || lower.contains("happy death day") || lower.contains("imaginary") ||
            lower.contains("night swim") || lower.contains("blumhouse") -> "Blumhouse Productions"

            // --- METRO-GOLDWYN-MAYER / AMAZON MGM ---
            lower.contains("james bond") || lower.contains("007") || lower.contains("creed") ||
            lower.contains("rocky") || lower.contains("road house") || lower.contains("red one") ||
            lower.contains("the beekeeper") || lower.contains("beekeeper") || lower.contains("challengers") ||
            lower.contains("saltburn") || lower.contains("air ") || lower.contains("robocop") ||
            lower.contains("silence of the lambs") || lower.contains("the silence of the lambs") ||
            lower.contains("fargo") || lower.contains("vikings") || lower.contains("handmaid's tale") ||
            lower.contains("mgm") || lower.contains("metro-goldwyn") -> "Metro-Goldwyn-Mayer"

            // --- NEW LINE CINEMA ---
            lower.contains("lord of the rings") || lower.contains("the hobbit") || lower.contains("hobbit") ||
            lower.contains("war of the rohirrim") || lower.contains("nightmare on elm street") || lower.contains("rush hour") ||
            lower.contains("elf") || lower.contains("the mask") || lower.contains("dumb and dumber") ||
            lower.contains("final destination") || lower.contains("mortal kombat") || lower.contains("san andreas") ||
            lower.contains("rampage") || lower.contains("new line") -> "New Line Cinema"

            // --- HBO / MAX ---
            lower.contains("game of thrones") || lower.contains("house of the dragon") || lower.contains("last of us") ||
            lower.contains("the last of us") || lower.contains("succession") || lower.contains("white lotus") ||
            lower.contains("the white lotus") || lower.contains("euphoria") || lower.contains("the penguin") ||
            lower.contains("chernobyl") || lower.contains("sopranos") || lower.contains("the sopranos") ||
            lower.contains("the wire") || lower.contains("true detective") || lower.contains("westworld") ||
            lower.contains("barry") || lower.contains("silicon valley") || lower.contains("curb your enthusiasm") ||
            lower.contains("dune: prophecy") || lower.contains("hbo") || lower.contains("max") -> "HBO"

            // --- NETFLIX ---
            lower.contains("stranger things") || lower.contains("squid game") || lower.contains("wednesday") ||
            lower.contains("bridgerton") || lower.contains("one piece live action") || lower.contains("the witcher") ||
            lower.contains("money heist") || lower.contains("la casa de papel") || lower.contains("lupin") ||
            lower.contains("the crown") || lower.contains("ozark") || lower.contains("mindhunter") ||
            lower.contains("black mirror") || lower.contains("dark ") || lower.contains("outer banks") ||
            lower.contains("queen's gambit") || lower.contains("dahmer") || lower.contains("baby reindeer") ||
            lower.contains("rebel ridge") || lower.contains("red notice") || lower.contains("extraction") ||
            lower.contains("glass onion") || lower.contains("the gray man") || lower.contains("bird box") ||
            lower.contains("klaus") || lower.contains("damsel") || lower.contains("atlas") ||
            lower.contains("netflix") -> "Netflix"

            // --- APPLE TV+ ---
            lower.contains("ted lasso") || lower.contains("severance") || lower.contains("morning show") ||
            lower.contains("the morning show") || lower.contains("for all mankind") || lower.contains("foundation") ||
            lower.contains("slow horses") || lower.contains("silo") || lower.contains("pachinko") ||
            lower.contains("black bird") || lower.contains("shrinking") || lower.contains("hijack") ||
            lower.contains("masters of the air") || lower.contains("presumed innocent") || lower.contains("killers of the flower moon") ||
            lower.contains("napoleon") || lower.contains("coda") || lower.contains("tetris") ||
            lower.contains("wolfs") || lower.contains("apple tv") -> "Apple TV+"

            // --- AMAZON PRIME VIDEO ---
            lower.contains("the boys") || lower.contains("gen v") || lower.contains("rings of power") ||
            lower.contains("reacher") || lower.contains("invincible") || lower.contains("fallout") ||
            lower.contains("wheel of time") || lower.contains("the wheel of time") || lower.contains("jack ryan") ||
            lower.contains("terminal list") || lower.contains("good omens") || lower.contains("mrs. maisel") ||
            lower.contains("fleabag") || lower.contains("hazbin hotel") || lower.contains("idea of you") ||
            lower.contains("prime video") || lower.contains("amazon") -> "Prime Video"

            // --- ANIME STUDIOS ---
            // Studio Ghibli
            lower.contains("spirited away") || lower.contains("totoro") || lower.contains("princess mononoke") ||
            lower.contains("howl's moving castle") || lower.contains("howl") || lower.contains("ponyo") ||
            lower.contains("kiki's delivery") || lower.contains("castle in the sky") || lower.contains("boy and the heron") ||
            lower.contains("grave of the fireflies") || lower.contains("ghibli") -> "Studio Ghibli"

            // Toei Animation
            lower.contains("one piece") || lower.contains("dragon ball") || lower.contains("dragon ball z") ||
            lower.contains("dragon ball super") || lower.contains("dragon ball daima") || lower.contains("sailor moon") ||
            lower.contains("digimon") || lower.contains("saint seiya") || lower.contains("slam dunk") ||
            lower.contains("toei") -> "Toei Animation"

            // ufotable
            lower.contains("demon slayer") || lower.contains("kimetsu no yaiba") || lower.contains("mugen train") ||
            lower.contains("swordsmith village") || lower.contains("hashira training") || lower.contains("fate/stay night") ||
            lower.contains("fate/zero") || lower.contains("kara no kyoukai") || lower.contains("garden of sinners") ||
            lower.contains("ufotable") -> "ufotable"

            // MAPPA
            lower.contains("jujutsu kaisen") || lower.contains("chainsaw man") || lower.contains("final season") ||
            lower.contains("hell's paradise") || lower.contains("vinland saga season 2") || lower.contains("yuri on ice") ||
            lower.contains("dororo") || lower.contains("banana fish") || lower.contains("kakegurui") ||
            lower.contains("mappa") -> "MAPPA"

            // Studio Pierrot
            lower.contains("naruto") || lower.contains("shippuden") || lower.contains("boruto") ||
            lower.contains("bleach") || lower.contains("thousand-year blood war") || lower.contains("tokyo ghoul") ||
            lower.contains("black clover") || lower.contains("yu yu hakusho") || lower.contains("pierrot") -> "Studio Pierrot"

            // Bones
            lower.contains("my hero academia") || lower.contains("fullmetal alchemist") || lower.contains("fullmetal") ||
            lower.contains("mob psycho") || lower.contains("bungo stray dogs") || lower.contains("soul eater") ||
            lower.contains("noragami") || lower.contains("bones") -> "Bones"

            // Kyoto Animation
            lower.contains("violet evergarden") || lower.contains("silent voice") || lower.contains("a silent voice") ||
            lower.contains("k-on") || lower.contains("clannad") || lower.contains("sound! euphonium") ||
            lower.contains("miss kobayashi") || lower.contains("dragon maid") || lower.contains("hyouka") ||
            lower.contains("kyoani") || lower.contains("kyoto animation") -> "Kyoto Animation"

            // Madhouse
            lower.contains("death note") || lower.contains("hunter x hunter") || lower.contains("one punch man") ||
            lower.contains("frieren") || lower.contains("beyond journey's end") || lower.contains("no game no life") ||
            lower.contains("overlord") || lower.contains("monster") || lower.contains("perfect blue") ||
            lower.contains("paprika") || lower.contains("madhouse") -> "Madhouse"

            // Wit Studio / CloverWorks
            lower.contains("spy x family") || lower.contains("attack on titan") || lower.contains("vinland saga") ||
            lower.contains("ranking of kings") || lower.contains("bocchi the rock") || lower.contains("dress-up darling") ||
            lower.contains("promised neverland") || lower.contains("wit studio") || lower.contains("cloverworks") -> "Wit Studio"

            // Production I.G
            lower.contains("ginga eiyuu") || lower.contains("galactic heroes") || lower.contains("legend of the galactic") ||
            lower.contains("psycho-pass") || lower.contains("ghost in the shell") || lower.contains("production i.g") -> "Production I.G"

            // If no verified studio matched in title, return empty string so real creator/uploader is used!
            else -> ""
        }
    }

    fun isStudioName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.trim().lowercase()
        return lower.contains("marvel") || lower.contains("dc studios") || lower.contains("dc comics") ||
               lower.contains("warner") || lower.contains("wb") || lower.contains("disney") ||
               lower.contains("pixar") || lower.contains("universal") || lower.contains("paramount") ||
               lower.contains("sony") || lower.contains("columbia") || lower.contains("20th century") ||
               lower.contains("fox") || lower.contains("lionsgate") || lower.contains("new line") ||
               lower.contains("legendary") || lower.contains("blumhouse") || lower.contains("a24") ||
               lower.contains("metro-goldwyn") || lower.contains("mgm") || lower.contains("ghibli") ||
               lower.contains("mappa") || lower.contains("toei") || lower.contains("pierrot") ||
               lower.contains("bones") || lower.contains("madhouse") || lower.contains("production i.g") ||
               lower.contains("kyoani") || lower.contains("netflix") || lower.contains("hbo") ||
               lower.contains("amc") || lower.contains("amazon") || lower.contains("apple tv") ||
               lower.contains("lucasfilm") || lower.contains("ufotable") || lower.contains("wit studio")
    }

    fun getStudioSearchQuery(studioName: String): String {
        val lower = studioName.lowercase()
        return when {
            lower.contains("marvel") -> "Marvel"
            lower.contains("dc") -> "DC"
            lower.contains("warner") -> "Warner Bros"
            lower.contains("disney") -> "Disney"
            lower.contains("pixar") -> "Pixar"
            lower.contains("universal") -> "Universal"
            lower.contains("paramount") -> "Paramount"
            lower.contains("sony") -> "Sony"
            lower.contains("20th") || lower.contains("fox") -> "Avatar"
            lower.contains("a24") -> "A24"
            lower.contains("lionsgate") -> "John Wick"
            lower.contains("legendary") -> "Dune"
            lower.contains("netflix") -> "Netflix"
            lower.contains("hbo") -> "HBO"
            lower.contains("ghibli") -> "Studio Ghibli"
            lower.contains("mappa") -> "MAPPA"
            else -> studioName.replace("Pictures", "").replace("Studios", "").replace("Films", "").trim()
        }
    }
}

