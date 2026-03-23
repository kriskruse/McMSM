onEvent('recipes', event => {
    event.getRecipeFunction("ars_nouveau:enchanting_apparatus")(Item.of('oneblock:nihilo_catalyst', {oblevel:2000}), Item.of('oneblock:nihilo_catalyst', {empty:true}).toJson(), [
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem'),
        Item.of('ars_nouveau:mana_gem')
    ]);
})