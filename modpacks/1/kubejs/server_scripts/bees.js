// priority: 0

function isLiquid(name) {
  return name.substring(0,1) == 'L';
}


function liquid(name) {
    return Fluid.of(name.substring(1)).toJson();
}

onEvent('recipes', event => {

  //reduce outputs chance
  event.forEachRecipe({type: 'productivebees:centrifuge'}, recipe => {
    var outputs = JSON.parse(recipe.json.getAsJsonArray('outputs').toString());
    var firstOutput = outputs[0];
    if(firstOutput.hasOwnProperty('chance'))
      firstOutput['chance'] *= 0.85;
    else
      firstOutput['chance'] = 80;
    outputs[0] = firstOutput;
    recipe.merge({'outputs' : outputs})

  });


  const comb = (name, tagneeds) => {
    var data = {
        "type": "productivebees:advanced_beehive",
        "ingredient": "productivebees:"+name,
        "results": [
            {
                "item": Item.of("productivebees:configurable_honeycomb").toResultJson()
            },
            {
                "item": Item.of("the_bumblezone:pollen_puff").toResultJson(),
                "chance": 5
            }
        ],
        "conditions": []
    };
    for(tag of tagneeds)
      {
        data.conditions.push({
            "type": "forge:not",
            "value": {
                "type": "forge:tag_empty",
                "tag": tag
            }
        });
      }
  
  
      event.custom(data);
  };
  
  const centrifuge = (name, tagneeds, outputs) => {
    var data = {
      "type": "productivebees:centrifuge",
      "ingredient": Item.of("productivebees:configurable_honeycomb").toResultJson(),
      "comb_type" : 'productivebees:'+name, 
      "outputs": [],
      "conditions": []
  };
      for(tag of tagneeds)
      {
        data.conditions.push({
            "type": "forge:not",
            "value": {
                "type": "forge:tag_empty",
                "tag": tag
            }
        });
      }
  
      for(output of outputs)
      {
          var out = {}
          
          if(isLiquid(output[0])) {
            out['fluid'] = liquid(output[0]);
            out['amount'] = output[1];
          }
          else {
            out['item'] = Ingredient.of(output[0]).toJson();
            out['chance'] = output[1];
          }

          if(output.length >= 3)
            out['min'] = output[2];
          if(output.length > 3)
            out['max'] = output[3];
          data.outputs.push(out);
      }
  
      comb(name, tagneeds);
      event.custom(data);
  
  };


  const conv = (source, target, item) => {
      event.custom({
        "type": "productivebees:bee_conversion",
        "source": "productivebees:"+source,
        "result": "productivebees:"+target,
        "item": Ingredient.of(item).toJson()
      });
  };

  const breed = (parent1, parent2, offspring, items) => {
      var data = {
        "type": "productivebees:bee_breeding",
        "parent1": "productivebees:"+parent1,
        "parent2": "productivebees:"+parent2,
        "offspring": [
            "productivebees:"+offspring
        ],
        "breeding_items": [],
      };

      for(item of items)
        data.breeding_items.push(Ingredient.of(item).toJson());
      
      event.custom(data);


};

  //ars nouveau
  centrifuge('mana', [], [['ars_nouveau:mana_gem', 50]]);
  conv('crystalline', 'mana', 'ars_nouveau:mana_gem');
  //astral sorcery
  //centrifuge('aquamarine', [], [['astralsorcery:aquamarine', 50], ['Lastralsorcery:liquid_starlight', 40]]);
  breed('marble', 'mana', 'aquamarine', ['astralsorcery:aquamarine']);

  centrifuge('marble', [], [['astralsorcery:marble_raw', 80]]);
  conv('crystalline', 'marble', 'astralsorcery:marble_raw');

  //centrifuge('star_metal', [], [['astralsorcery:starmetal_ingot', 70], ['Lastralsorcery:liquid_starlight', 90]]);
  //breed('iron', 'aquamarine', 'star_metal', ['astralsorcery:starmetal']);
  
  //extreme reactors
  centrifuge('yellorium', [], [['#forge:ingots/yellorium', 70], ['Lproductivebees:honey', 150]]);
  conv('iron', 'yellorium', '#forge:storage_blocks/yellorium');
  conv('radioactive', 'yellorium', '#forge:ingots/yellorium');

  //aquaculture
  centrifuge('neptunium', [], [['#forge:ingots/neptunium', 10], ['Lminecraft:water', 100]])
  conv('blue_banded_bee', 'neptunium', '#forge:storage_blocks/neptunium');

  //eidolon
  centrifuge('pewter', [], [['#forge:ingots/pewter', 60]]);
  conv('lead', 'pewter', '#forge:storage_blocks/pewter');
  centrifuge('arcane_gold', [], [['#forge:ingots/arcane_gold', 50]]);
  conv('gold', 'arcane_gold', '#forge:storage_blocks/arcane_gold');
  
/*
  const silents = (name) => {
    centrifuge(name, [], [['#forge:gems/'+name, 45]]);
    conv('iron', name, '#forge:storage_blocks/'+name);
  }
  silents('ruby');
  silents('garnet');
  silents('topaz');
  silents('amber');
  silents('heliodor');
  silents('peridot');
  silents('green_sapphire');
  silents('phosphophyllite');
  //silents('aquamarine');
  silents('sapphire');
  silents('tanzanite');
  silents('amethyst');
  silents('agate');
  silents('morganite');
  silents('onyx');
  silents('opal');
  silents('carnelian');
  silents('spinel');
  silents('citrine');

  silents('jasper');
  silents('zircon');
  silents('moldavite');
  silents('malachite');
  silents('turquoise');
  silents('euclase');
  silents('benitoite');
  silents('iolite');
  silents('alexandrite');
  silents('lepidolite');
  silents('ametrine');
  silents('black_diamond');
  silents('moonstone');
  silents('pyrope');
  silents('coral');
  silents('sunstone');
  silents('cats_eye');
  silents('yellow_diamond');
  silents('jade');
  silents('chrysoprase');
  silents('apatite');
  silents('fluorite');
  silents('kyanite');
  silents('sodalite');
  silents('ammolite');
  silents('kunzite');
  silents('rose_quartz');
  silents('tektite');
  silents('pearl');*/

})
