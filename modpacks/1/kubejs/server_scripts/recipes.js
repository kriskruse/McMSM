onEvent('recipes', event => {

    event.remove({output: 'create:mechanical_piston'});
    event.shaped(Item.of('create:mechanical_piston'), [' P ', 'WCW', 'AEA'], {P : '#minecraft:planks', W : 'create:cogwheel', C : 'create:andesite_casing', A : 'botania:ender_air_bottle', E : 'create:piston_extension_pole'});

    event.remove({output: 'create:mechanical_drill'});
    event.shaped(Item.of('create:mechanical_drill'), [' L ', 'LIL', 'ACA'], {L : 'create:andesite_alloy', I : 'minecraft:iron_ingot', A : 'botania:ender_air_bottle', C : 'create:andesite_casing'});

    event.remove({output: 'refinedstorage:destructor'});
    event.shaped(Item.of('refinedstorage:destructor'), ['QDQ', 'ACA', 'QIQ'], {Q : 'refinedstorage:quartz_enriched_iron', D : 'refinedstorage:destruction_core', A : 'botania:ender_air_bottle', C : 'refinedstorage:cable', I : 'refinedstorage:improved_processor'});

    event.remove({output: 'botania:lens_weight'});
    event.shaped(Item.of('botania:lens_weight'), ['PEP', 'PNP', 'PRP'], {P : 'minecraft:prismarine_crystals', N : 'botania:lens_normal', E : 'botania:ender_air_bottle', R : 'botania:rune_water'});

    event.remove({output: 'botania:lens_mine'});
    event.shaped(Item.of('botania:lens_mine'), [' P ', 'LNL', 'ARA'], {P : 'minecraft:piston', L : 'minecraft:lapis_lazuli', N : 'botania:lens_normal', A : 'botania:ender_air_bottle', R : 'minecraft:redstone'});

    event.shapeless('farmersdelight:cabbage_seeds', 'farmersdelight:cabbage')

    event.shapeless('silentgems:fluffy_puff', 'minecraft:white_wool')

    event.shapeless('farmersdelight:wild_cabbages', ['farmersdelight:cabbage', 'mysticalagriculture:nature_essence'])
    event.shapeless('farmersdelight:wild_onions', ['farmersdelight:onion', 'mysticalagriculture:nature_essence'])
    event.shapeless('farmersdelight:wild_carrots', ['minecraft:carrot', 'mysticalagriculture:nature_essence'])
    event.shapeless('farmersdelight:wild_potatoes', ['minecraft:potato', 'mysticalagriculture:nature_essence', 'mysticalagriculture:nature_essence'])
    event.shapeless('farmersdelight:wild_rice', ['farmersdelight:rice_panicle', 'mysticalagriculture:nature_essence'])
    event.shapeless('farmersdelight:wild_tomatoes', ['farmersdelight:tomato', 'mysticalagriculture:nature_essence'])

    //Botania
    const alchemy_infusion = (input, output, mana) => {    
        event.custom({
            "type": "botania:mana_infusion",
            "input": input,
            "output": output,
            "mana": mana,
            "catalyst": {
              "name": "botania:alchemy_catalyst"
            }
          });
      };
    alchemy_infusion(Item.of('farmersdelight:wild_cabbages'), Item.of('farmersdelight:wild_onions'), 500);
    alchemy_infusion(Item.of('farmersdelight:wild_onions'), Item.of('farmersdelight:wild_carrots'), 500);
    alchemy_infusion(Item.of('farmersdelight:wild_carrots'), Item.of('farmersdelight:wild_potatoes'), 500);
    alchemy_infusion(Item.of('farmersdelight:wild_potatoes'), Item.of('farmersdelight:wild_rice'), 500);
    alchemy_infusion(Item.of('farmersdelight:wild_rice'), Item.of('farmersdelight:wild_tomatoes'), 500);
    alchemy_infusion(Item.of('farmersdelight:wild_tomatoes'), Item.of('farmersdelight:wild_cabbages'), 500);

    //Blossom
    event.shapeless('quark:red_blossom_sapling', ['minecraft:oak_sapling', 'mysticalagriculture:dye_essence'])
    alchemy_infusion(Item.of('quark:blue_blossom_sapling'), Item.of('quark:lavender_blossom_sapling'), 500);
    alchemy_infusion(Item.of('quark:lavender_blossom_sapling'), Item.of('quark:orange_blossom_sapling'), 500);
    alchemy_infusion(Item.of('quark:orange_blossom_sapling'), Item.of('quark:pink_blossom_sapling'), 500);
    alchemy_infusion(Item.of('quark:pink_blossom_sapling'), Item.of('quark:yellow_blossom_sapling'), 500);
    alchemy_infusion(Item.of('quark:yellow_blossom_sapling'), Item.of('quark:red_blossom_sapling'), 500);
    alchemy_infusion(Item.of('quark:red_blossom_sapling'), Item.of('quark:blue_blossom_sapling'), 500);

    //Archwood
    event.shapeless('ars_nouveau:red_archwood_sapling', ['minecraft:dark_oak_sapling', 'mysticalagriculture:dye_essence', 'ars_nouveau:mana_gem'])
    alchemy_infusion(Item.of('ars_nouveau:blue_archwood_sapling'), Item.of('ars_nouveau:purple_archwood_sapling'), 500);
    alchemy_infusion(Item.of('ars_nouveau:purple_archwood_sapling'), Item.of('ars_nouveau:green_archwood_sapling'), 500);
    alchemy_infusion(Item.of('ars_nouveau:green_archwood_sapling'), Item.of('ars_nouveau:red_archwood_sapling'), 500);
    alchemy_infusion(Item.of('ars_nouveau:red_archwood_sapling'), Item.of('ars_nouveau:blue_archwood_sapling'), 500);


    alchemy_infusion(Item.of('silentgems:fluffy_puff_seeds'), Item.of('silentgear:flax_seeds'), 200);

    //RS infinity booster
    event.remove({id: 'rsinfinitybooster:infinity_card'});
    event.shaped(Item.of('rsinfinitybooster:infinity_card'), ['SRS', 'RHR', 'ADA'], {H : 'botania:gaia_head', S : 'minecraft:nether_star', R : 'refinedstorage:range_upgrade', D : 'minecraft:dragon_egg', A : 'astralsorcery:resonating_gem'});

    event.remove({id: 'rsinfinitybooster:dimension_card'});
    event.shaped(Item.of('rsinfinitybooster:dimension_card'), ['AMR', 'IDI', 'EMV'], {I : 'rsinfinitybooster:infinity_card', A : 'astralsorcery:shifting_star_aevitas', R : 'astralsorcery:shifting_star_armara', D : 'astralsorcery:shifting_star_discidia', E : 'astralsorcery:shifting_star_evorsio', V : 'astralsorcery:shifting_star_vicio', M : 'ars_nouveau:arcane_relay'});



    //Time is up
    event.replaceInput({output: 'timeisup:timer_anchor'}, 'minecraft:gold_ingot', 'immersiveengineering:ingot_electrum');
    event.replaceInput({output: 'timeisup:timer_anchor'}, 'minecraft:gold_block', 'immersiveengineering:storage_electrum');
    event.replaceInput({output: 'timeisup:timer_ward'}, 'minecraft:stick', 'immersiveengineering:stick_treated');
    event.replaceInput({output: 'timeisup:timer_ward'}, 'minecraft:gold_block', 'mysticalagriculture:supremium_block');

    //Mekanism
    event.remove({id: 'mekanism:cardboard_box'});

    event.remove({id: 'mekanism:digital_miner'});
    event.custom({
      "type": "mekanism:mek_data",
      "result": {
        "item": "mekanism:digital_miner"
      },
      "pattern": [
        "ACA",
        "SRS",
        "TXT"
      ],
      "key": {
        "C": {
          "item": "botania:ender_air_bottle"
        },
        "A": {
          "tag": "mekanism:alloys/atomic"
        },
        "X": {
          "item": "mysticalagriculture:supremium_block"
        },
        "S": {
          "item": "mekanism:logistical_sorter"
        },
        "T": {
          "item": "mekanism:teleportation_core"
        },
        "R": {
          "item": "mekanism:robit"
        }
      }
    });

    //rftools builder
    event.remove({id: 'rftoolsbuilder:shape_card_quarry'});
    event.custom({
      "type": "mcjtylib:copy_nbt",
      "pattern": [
        "rPr",
        "iMi",
        "rSr"
      ],
      "key": {
        "P": {
          "item": "minecraft:diamond_pickaxe"
        },
        "S": {
          "item": "minecraft:diamond_shovel"
        },
        "M": {
          "item": "rftoolsbuilder:shape_card_def"
        },
        "r": {
          "item": "minecraft:redstone"
        },
        "i": {
          "item": "botania:lens_mine"
        }
      },
      "result": {
        "item": "rftoolsbuilder:shape_card_quarry"
      }
    });
    event.remove({id: 'rftoolsbuilder:shape_card_void'});
    event.custom({
      "type": "mcjtylib:copy_nbt",
      "pattern": [
        "xOx",
        "OMO",
        "xOx"
      ],
      "key": {
        "x": {
          "item": "botania:ender_air_bottle"
        },
        "M": {
          "item": "rftoolsbuilder:shape_card_def"
        },
        "O": {
          "item": "minecraft:obsidian"
        }
      },
      "result": {
        "item": "rftoolsbuilder:shape_card_void"
      }
    });

    //astral
    event.remove({id: 'astralsorcery:altar/colored_lens_break'});
    event.custom({
      "type": "astralsorcery:altar",
      "altar_type": 2,
      "duration": 400,
      "starlight": 2000,
      "pattern": [
        "A___A",
        "__D__",
        "__E__",
        "C_B_C",
        "_____"
      ],
      "key": {
        "A": {
          "item": "astralsorcery:aquamarine"
        },
        "B": {
          "item": "minecraft:diamond_pickaxe"
        },
        "C": {
          "tag": "forge:ingots/gold"
        },
        "D": {
          "item": "botania:ender_air_bottle"
        },
        "E": {
          "item": "astralsorcery:glass_lens"
        }
      },
      "output": [
        {
          "item": "astralsorcery:colored_lens_break",
          "count": 1
        }
      ],
      "effects": [
        "astralsorcery:built_in_effect_constellation_finish",
        "astralsorcery:built_in_effect_discovery_central_beam",
        "astralsorcery:altar_default_sparkle",
        "astralsorcery:built_in_effect_constellation_lines",
        "astralsorcery:built_in_effect_attunement_sparkle"
      ]
    });

    //end_portals
    event.remove({id: 'gardenofglass:end_portal_frame'});
    event.custom({
      "type": "astralsorcery:altar",
      "altar_type": 2,
      "duration": 400,
      "starlight": 2000,
      "pattern": [
        "AM_MA",
        "MSESM",
        "_OGO_",
        "MOOOM",
        "AM_MA"
      ],
      "key": {
        "M": {
          "item": "mysticalagriculture:supremium_essence"
        },
        "S": {
          "item": "eidolon:shadow_gem"
        },
        "E": {
          "item": "botania:ender_eye_block"
        },
        "O": {
          "item": "minecraft:obsidian"
        },
        "G": {
          "item": "botania:life_essence"
        },
        "A": {
          "item": "astralsorcery:resonating_gem"
        }
      },
      "output": [
        {
          "item": "minecraft:end_portal_frame",
          "count": 2
        }
      ],
      "effects": [
        "astralsorcery:built_in_effect_constellation_finish",
        "astralsorcery:built_in_effect_discovery_central_beam",
        "astralsorcery:altar_default_sparkle",
        "astralsorcery:built_in_effect_constellation_lines",
        "astralsorcery:built_in_effect_attunement_sparkle"
      ]
    });

    //obanalyzer
    event.shapeless(Item.of('oneblock:obanalyzer'), ['minecraft:dirt', 'minecraft:paper']);

    //obremover
    event.shapeless(Item.of('oneblock:obremover'), ['minecraft:dirt', 'minecraft:ender_eye']);

    //oneblock
    event.shaped(Item.of('oneblock:ex_nihilo'), ['IPI', 'ILI', 'BDB'], {B : '#forge:storage_blocks/iron', L : 'botania:life_essence', D : 'botania:lens_damage', P : 'minecraft:iron_pickaxe', I : '#forge:ingots/iron'});
    event.shaped(Item.of('oneblock:nihilo_catalyst', {empty:true}), [' P ', 'PGP', ' P '], {P : 'botania:mana_pearl', G : 'botania:mana_glass'});
    
    //flatland
    event.shaped(Item.of('oneblock:nihilo_catalyst', {oblevel:0}), [' L ', 'DCS', ' I '], {L : '#minecraft:leaves', D : 'minecraft:dirt', S : '#forge:sand', I : '#forge:storage_blocks/clay', C : Item.of('oneblock:nihilo_catalyst', {empty:true})});

    //farmland
    event.shaped(Item.of('oneblock:nihilo_catalyst', {oblevel:200}), [' H ', 'MCS', ' P '], {H : 'minecraft:hay_block', M : 'minecraft:melon', S : 'minecraft:pumpkin', P : 'farmersdelight:potato_crate', C : Item.of('oneblock:nihilo_catalyst', {empty:true})});

    //magicbundle
    event.shapeless(Item.of('oneblock:nihilo_catalyst', {oblevel:2200}), [Item.of('oneblock:nihilo_catalyst', {oblevel:2000}), Item.of('oneblock:nihilo_catalyst', {oblevel:1800}), 'botania:mana_diamond']);

    //abyss
    event.shaped(Item.of('oneblock:nihilo_catalyst', {oblevel:3000}), [' P ', 'BCS', ' D '], {B : 'minecraft:prismarine_bricks', D : 'minecraft:dark_prismarine', S : 'minecraft:sea_lantern', P : 'minecraft:sponge', C : Item.of('oneblock:nihilo_catalyst', {empty:true})});

    //bumblezone
    event.shaped(Item.of('oneblock:nihilo_catalyst', {oblevel:3800}), [' S ', 'PCH', ' N '], {S : 'the_bumblezone:honey_crystal_shards', P : 'the_bumblezone:filled_porous_honeycomb_block', H : 'minecraft:honeycomb_block', N : 'minecraft:bee_nest', C : Item.of('oneblock:nihilo_catalyst', {empty:true})});

    //nether
    event.shaped(Item.of('oneblock:nihilo_catalyst', {oblevel:4200}), [' S ', 'OCQ', ' B '], {S : 'minecraft:soul_sand', O : 'minecraft:obsidian', Q : 'minecraft:quartz_block', B : 'minecraft:nether_bricks', C : Item.of('oneblock:nihilo_catalyst', {empty:true})});

    //end
    event.shaped(Item.of('oneblock:nihilo_catalyst', {oblevel:5000}), [' E ', 'PCQ', ' B '], {E : 'endergetic:poismoss', P : 'minecraft:purpur_block', Q : 'endergetic:poise_cluster', B : 'minecraft:end_stone_bricks', C : Item.of('oneblock:nihilo_catalyst', {empty:true})});

    //all in one
    const catalyst = (oblevel) => {return Item.of('oneblock:nihilo_catalyst', {oblevel:oblevel});};
    event.shapeless(catalyst(5800), [catalyst(0), catalyst(200), catalyst(300), catalyst(900), catalyst(2200), catalyst(3000), catalyst(3800), catalyst(4200), catalyst(5000)]);

    //statue
    event.shaped(Item.of('oneblock:statue'), [' H ', 'BEB', ' T '], {H : 'minecraft:skeleton_skull', B : 'create:brass_hand', E : 'create:electron_tube', T : '#forge:treated_wood'});
});