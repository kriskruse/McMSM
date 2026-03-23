onEvent('recipes', event => {

    const mechanical = (result, pattern, keys) => {
        event.custom({
            "type": "create:mechanical_crafting",
            "pattern": pattern,
            "key": keys,
            "result": result,
            "acceptMirrored": false
          });

    }

    //end_portals
    event.remove({id: 'endportalrecipe:craftable_end_portal'});
    const superchargedstone = Item.of('minecraft:end_stone').enchant('silentgems:supercharged', 3)
    mechanical({'item':'minecraft:end_portal_frame', 'count':6}, ['FFF', 'FDF', 'EEE'], {'F' : {'item':'portality:module_interdimensional'}, 'D' : {'item':'minecraft:dragon_egg'}, 'E' : superchargedstone.toJson()});


});