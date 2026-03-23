const quests = [['ars_nouveau:arcane_ore', 157], ['astralsorcery:aquamarine_sand_ore', 160], ['astralsorcery:fountain_prime_ore', 163], ['astralsorcery:rock_crystal_ore', 166], ['astralsorcery:starmetal_ore', 169], ['bigreactors:anglesite_ore', 172], ['bigreactors:benitoite_ore', 175], ['bigreactors:yellorite_ore', 178], ['botania:floating_orechid', 181], ['botania:floating_orechid_ignem', 184], ['create:copper_ore', 187], ['create:zinc_ore', 190], ['minecraft:coal_ore', 193], ['minecraft:diamond_ore', 196], ['minecraft:emerald_ore', 199], ['minecraft:gold_ore', 202], ['minecraft:iron_ore', 205], ['minecraft:lapis_ore', 208], ['minecraft:nether_gold_ore', 211], ['minecraft:nether_quartz_ore', 214], ['minecraft:redstone_ore', 217], ['mysticalagriculture:inferium_ore', 220], ['mysticalagriculture:prosperity_ore', 223], ['mysticalagriculture:soulium_ore', 226], ['quark:biotite_ore', 229], ['silentgear:azure_silver_ore', 232], ['silentgear:bort_ore', 235], ['silentgear:crimson_iron_ore', 238], ['silentgems:agate_ore', 241], ['silentgems:alexandrite_ore', 244], ['silentgems:amber_ore', 247], ['silentgems:amethyst_ore', 250], ['silentgems:ametrine_ore', 253], ['silentgems:ammolite_ore', 256], ['silentgems:apatite_ore', 259], ['silentgems:aquamarine_ore', 262], ['silentgems:benitoite_ore', 265], ['silentgems:black_diamond_ore', 268], ['silentgems:carnelian_ore', 271], ['silentgems:cats_eye_ore', 274], ['silentgems:chaos_ore', 277], ['silentgems:chrysoprase_ore', 280], ['silentgems:citrine_ore', 283], ['silentgems:coral_ore', 286], ['silentgems:ender_ore', 289], 
['silentgems:euclase_ore', 292], ['silentgems:fluorite_ore', 295], ['silentgems:garnet_ore', 298], ['silentgems:green_sapphire_ore', 301], ['silentgems:heliodor_ore', 304], ['silentgems:iolite_ore', 307], ['silentgems:jade_ore', 310], ['silentgems:jasper_ore', 313], ['silentgems:kunzite_ore', 316], ['silentgems:kyanite_ore', 319], ['silentgems:lepidolite_ore', 322], ['silentgems:malachite_ore', 325], ['silentgems:moldavite_ore', 328], ['silentgems:moonstone_ore', 331], ['silentgems:morganite_ore', 334], ['silentgems:multi_ore_classic', 337], ['silentgems:multi_ore_dark', 340], ['silentgems:multi_ore_light', 343], ['silentgems:onyx_ore', 346], ['silentgems:opal_ore', 349], ['silentgems:pearl_ore', 352], ['silentgems:peridot_ore', 355], ['silentgems:phosphophyllite_ore', 358], ['silentgems:pyrope_ore', 361], ['silentgems:rose_quartz_ore', 364], ['silentgems:ruby_ore', 367], ['silentgems:sapphire_ore', 370], ['silentgems:silver_ore', 373], ['silentgems:sodalite_ore', 376], ['silentgems:spinel_ore', 379], ['silentgems:sunstone_ore', 382], ['silentgems:tanzanite_ore', 385], ['silentgems:tektite_ore', 388], ['silentgems:topaz_ore', 391], ['silentgems:turquoise_ore', 394], ['silentgems:yellow_diamond_ore', 397], ['silentgems:zircon_ore', 400]];

onEvent('block.break', event => {
    if (event.getWorld() != null && event.getEntity() != null) {
        for (index in quests) {
            var quest = quests[index];
            if (event.getBlock().getItem().getId() == quest[0]) {
                event.entity.data.ftbquests.addProgress(quest[1], 1);
                break;
            }
        }

    }
});


const stages = ['unlock_flatland', 'unlock_farmland', 'unlock_underground', 'unlock_overworld', 'unlock_astral', 'unlock_magicka', 'unlock_abyss', 'unlock_bumblezone', 'unlock_nether', 'unlock_end']

const forceReset = function(teamdata, quest) {
    teamdata.setStarted(quest.id, null);
	teamdata.setCompleted(quest.id, null);
    var children = quest.getChildren();
    for(index in children) {
        teamdata.setProgress(children[index], 0);
    }
    
}

onEvent('ftbquests.completed.boost', event => {
    var gamestages = event.player.data.gamestages;
    var quest = event.getObject();
    for(index in stages) {
        gamestages.remove(stages[index]);
    }
    gamestages.add(quest.getTags().toArray()[1]);
    forceReset(event.getData().getData(),quest);
})


