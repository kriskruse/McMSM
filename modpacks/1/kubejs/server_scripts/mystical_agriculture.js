onEvent('recipes', event => {

    const seed = (result, conditions, input, ingredients) => {
        event.custom({
            "type": "mysticalagriculture:infusion",
            "conditions": conditions,
            "input": input,
            "ingredients": ingredients,
            "result": result
          });

    }

    event.remove({output: 'mysticalagriculture:elementium_seeds'});
    event.remove({output: 'mysticalagriculture:manasteel_seeds'});
    event.remove({output: 'mysticalagriculture:terrasteel_seeds'});

    event.remove({output: 'mysticalagriculture:yellorium_seeds', type: 'mysticalagriculture:infusion'});
    seed(Item.of('mysticalagriculture:yellorium_seeds').toJson(), [Item.of('bigreactors:yellorium_ingot').toJson()], Item.of('mysticalagriculture:prosperity_seed_base'), 
    [
        Item.of('bigreactors:yellorium_ingot').toJson(),
        Item.of('mysticalagriculture:prudentium_essence').toJson(),
        Item.of('bigreactors:yellorium_ingot').toJson(),
        Item.of('mysticalagriculture:prudentium_essence').toJson(),
        Item.of('bigreactors:yellorium_ingot').toJson(),
        Item.of('mysticalagriculture:prudentium_essence').toJson(),
        Item.of('bigreactors:yellorium_ingot').toJson(),
        Item.of('mysticalagriculture:prudentium_essence').toJson()

    ]);

    //Oneblock 
    //underground
    seed(Item.of('oneblock:nihilo_catalyst', {oblevel:300}), [], Item.of('oneblock:nihilo_catalyst', {empty:true}).toJson(),
    [
        Ingredient.of('#forge:ores/silver'),
        Ingredient.of('#forge:ores/zinc'),
        Ingredient.of('#forge:ores/copper'),
        Ingredient.of('#forge:ores/iron'),
        Ingredient.of('#forge:ores/tin'),
        Ingredient.of('#forge:ores/gold'),
        Item.of('bigreactors:yellorite_ore'),
        Ingredient.of('#forge:ores/osmium')

    ]);

    //overworld
    seed(Item.of('oneblock:nihilo_catalyst', {oblevel:900}), [], Item.of('oneblock:nihilo_catalyst', {oblevel:300}).toJson(),
    [
        Ingredient.of('#forge:sand'),
        Item.of('minecraft:terracotta'),
        Item.of('minecraft:ice'),
        Ingredient.of('#forge:gravel'),
        Item.of('minecraft:dirt'),
        Ingredient.of('#minecraft:logs'),
        Ingredient.of('#minecraft:wool'),
        Item.of('minecraft:snow_block')

    ]);

});