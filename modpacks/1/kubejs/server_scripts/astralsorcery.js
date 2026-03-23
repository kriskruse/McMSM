onEvent('recipes', event => {

    const infuser = (result, input, consumechance, duration) => {
        event.custom({
            "type": "astralsorcery:infuser",
            "fluidInput": "astralsorcery:liquid_starlight",
            "input": input,
            "output": result,
            "consumptionChance": consumechance,
            "duration": duration,
            "consumeMultipleFluids": false,
            "acceptChaliceInput": true,
            "copyNBTToOutputs": false
          });

    }

    infuser(Item.of('oneblock:nihilo_catalyst', {oblevel:1800}), Item.of('oneblock:nihilo_catalyst', {empty:true}).toJson(), 1.0, 300);
})