onEvent('block.tags', event => {
    const blacklist = (id) => {event.add('the_bumblezone:blacklisted_productive_bees_combs','productivebees:comb_'+id)};

    const blacklist_array = ['tin','tungsten','tea','signalum','spacial','refined_obsidian','refined_glowstone','titanium','uncommon_salvage','unobtainium','platinum','rare_salvage','plastic','pink_slimy','osmium','niter','menril','lumium','uraninite','legendary_salvage','insanium','invar','epic_salvage','enderium','cinnabar','common_salvage','chocolate','vibranium','alfsteel','basalz','allthemodium','bismuth','blizz','blitz','bloody','bronze','slimesteel','rose_gold','queens_slime','cobalt','hepatizon','manyullyn','knightslime','pig_iron','spectrum','cosmic_dust','ender_slimy','sky_slimy', 'tinkers_bronze','soulsteel','inferium','prudentium','tertium','imperium','supremium','draconic'];

    for(var comb of blacklist_array)
        blacklist(comb);

    event.removeAll('itemfilters:check_nbt');
})

onEvent('item.tags', event => {event.removeAll('itemfilters:check_nbt');})