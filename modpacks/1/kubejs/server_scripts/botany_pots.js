
onEvent('recipes', event => {

    const applyChanges = (base) => {
        var abs = base;
        if(base < 0) {
            abs = -base;
        }

        var var1 = abs*0.05;
        var var2 = base*base*0.25;
        var var3 = var1+var2+0.12;
        return base-var3;
    }
    event.forEachRecipe({type: 'botanypots:soil'}, recipe => {
        recipe.merge({'growthModifier': applyChanges(recipe.json.get('growthModifier'))});
    });

    event.forEachRecipe({type: 'botanypots:crop', output : 'mysticalagriculture:yellorium_seeds'}, recipe => {
        var categories = recipe.json.getAsJsonArray('categories');
        categories.remove(0);
        categories.add('prudentium');
    })

    //fix netherite
    event.forEachRecipe({type: 'botanypots:crop', output : 'mysticalagriculture:netherite_seeds'}, recipe => {
        var categories = recipe.json.getAsJsonArray('categories');
        categories.remove(0);
        categories.add('supremium');
    })

    //every seeds 1%
    var seeds = Ingredient.of(/seeds$/);
    event.forEachRecipe({type: 'botanypots:crop', output: seeds }, recipe => {
        recipe.outputItems.get(recipe.getOutputIndex(seeds, false)).setChance(0.01)
    });


});