
/*
* See new mod update
*/
/*
onEvent('recipes', event => {

    const remove_recipes = (target) => {
        event.remove({"type" : "productivebees:bee_conversion", "id" : "productivebees:bee_conversion/mysticalagriculture/"+target+"_bee"});
        //event.remove({"offspring": ["productivebees:"+ target]});
    }

    const mystical = (result, conditions, input, primary, secondary) => {
        event.custom({
            "type": "mysticalagriculture:infusion",
            "conditions": conditions,
            "input": input,
            "ingredients": [primary, secondary, primary, secondary, primary, secondary, primary, secondary],
            "result": result
          });

    }

    const neeNbt = (name) => {
        return {EntityTag:{type:"productivebees:"+name}};
    }

    //prudentium bee
    remove_recipes('prudentium')
    mystical(Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('prudentium')).toJson(), [], Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('inferium')).toJson(), 
    Item.of("mysticalagriculture:prudentium_block"), Item.of("mysticalagriculture:inferium_essence"));

    //Tertium bee
    remove_recipes('tertium')
    mystical(Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('tertium')).toJson(), [], Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('prudentium')).toJson(), 
    Item.of("mysticalagriculture:tertium_block"), Item.of("mysticalagriculture:prudentium_essence"));

    //Imperium bee
    remove_recipes('imperium')
    mystical(Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('imperium')).toJson(), [], Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('tertium')).toJson(), 
    Item.of("mysticalagriculture:imperium_block"), Item.of("mysticalagriculture:tertium_essence"));

    //Supremium bee
    remove_recipes('supremium')
    mystical(Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('supremium')).toJson(), [], Item.of('productivebees:spawn_egg_configurable_bee', 1, neeNbt('imperium')).toJson(), 
    Item.of("mysticalagriculture:supremium_block"), Item.of("mysticalagriculture:imperium_essence"));

});
*/